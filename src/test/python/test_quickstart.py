"""Checks release selection and download failure behavior without network access."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
INSTALLER = ROOT / "docs/fjage_quickstart.sh"
MANIFEST = ROOT / "docs/releases/fjage-2.6.0-dependencies.txt"


@unittest.skipIf(os.name == "nt", "The quickstart installer requires a POSIX shell")
class QuickstartTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.project = self.root / "project"
        self.project.mkdir()
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.log = self.root / "requests"
        curl = self.bin / "curl"
        curl.write_text(r"""#!/usr/bin/env python3
import os
from pathlib import Path
import sys
args = sys.argv[1:]
url = args[1]
output = Path(args[args.index('-o') + 1])
with open(os.environ['REQUEST_LOG'], 'a') as log:
    log.write(url + '\n')
if os.environ.get('FAIL_DOWNLOAD') in (url.rsplit('/', 1)[-1], 'all'):
    sys.exit(22)
if url.endswith('-dependencies.txt'):
    if '/releases/download/' in url and os.environ.get('HISTORICAL') == '1':
        sys.exit(22)
    output.write_bytes(Path(os.environ['DEPENDENCY_MANIFEST']).read_bytes())
else:
    output.write_text(url + '\n')
""")
        curl.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'],
                        REQUEST_LOG=str(self.log), DEPENDENCY_MANIFEST=str(MANIFEST), VERSION='2.6.0')

    def install(self, **env):
        return subprocess.run(['sh', str(INSTALLER)], cwd=self.project,
                              env=dict(self.env, **env), capture_output=True, text=True)

    def assert_install(self):
        libs = self.project / 'build/libs'
        expected = {line.rsplit('/', 1)[-1] for line in MANIFEST.read_text().splitlines()}
        expected.add('fjage-2.6.0.jar')
        self.assertEqual(expected, {p.name for p in libs.glob('*.jar')})
        self.assertEqual(MANIFEST.read_bytes(), (libs / MANIFEST.name).read_bytes())
        for path in ('fjage.sh', 'rconsole.sh', 'etc/initrc.groovy', 'samples/01_hello.groovy'):
            self.assertIn('/v2.6.0/', (self.project / path).read_text())
        self.assertTrue(os.access(self.project / 'fjage.sh', os.X_OK))
        self.assertEqual([], list(self.project.glob('.fjage-install.*')))

    def test_release_manifest_installs_exact_dependencies_and_tagged_scripts(self):
        result = self.install()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_install()
        self.assertNotIn('/master/', self.log.read_text())

    def test_historical_manifest_keeps_the_original_dependency_versions(self):
        result = self.install(HISTORICAL='1')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_install()
        self.assertIn('/master/docs/releases/fjage-2.6.0-dependencies.txt', self.log.read_text())
        self.assertTrue((self.project / 'build/libs/objenesis-3.0.1.jar').is_file())

    def test_failed_download_does_not_update_the_project(self):
        (self.project / 'etc').mkdir()
        config = self.project / 'etc/initrc.groovy'
        config.write_text('existing configuration')
        result = self.install(FAIL_DOWNLOAD='gson-2.13.2.jar')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual('existing configuration', config.read_text())
        self.assertFalse((self.project / 'build').exists())
        self.assertEqual([], list(self.project.glob('.fjage-install.*')))

    def test_conflicting_jars_are_preserved_and_reported(self):
        libs = self.project / 'build/libs'
        libs.mkdir(parents=True)
        old = libs / 'gson-2.10.jar'
        old.write_text('old dependency')
        result = self.install()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Conflicting JAR', result.stderr)
        self.assertEqual('old dependency', old.read_text())
        self.assertEqual([old], list(libs.iterdir()))

    def test_invalid_manifest_cannot_write_outside_the_staging_directory(self):
        manifest = self.root / 'invalid-manifest'
        manifest.write_text('../outside.jar\n')
        result = self.install(DEPENDENCY_MANIFEST=str(manifest))
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Invalid dependency path', result.stderr)
        self.assertFalse((self.project / 'build').exists())


if __name__ == '__main__':
    unittest.main()
