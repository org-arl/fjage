fjåge Build Instructions
========================

Prerequisites
-------------

* JDK 8 - the build compiles and tests with a JDK 8 toolchain. Gradle is fetched by `./gradlew` and can run on JDK 8 to 24.
* [Quarto](https://quarto.org/) - required for building documentation

Generating JARs
---------------

To generate project JARs:

    ./gradlew jar

The project and dependency JARs are located in `build/libs/`.

Testing the build
-----------------

To test the build:

    ./gradlew test

Test report is available at `build/reports/tests/test/index.html`.

Publishing JARs to GitHub Packages
----------------------------------
**To be done by project administrator only**

From version 2.1.0 onwards, fjåge is published to the [GitHub Maven repository](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry) (`https://maven.pkg.github.com/org-arl/fjage`) only. Older versions are still available in Maven Central.

Publishing needs a GitHub personal access token (classic) with the `write:packages` scope. Add your credentials to `~/.gradle/gradle.properties`, or to the git-ignored `gradle.properties` in the project root:

    gpr.user=<github-username>
    gpr.token=<personal-access-token>

If these are not set, the `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables are used instead.

To build and upload the jar, sources jar and javadoc jar:

    ./gradlew publish

GitHub Packages does not allow a released version to be overwritten, so bump `VERSION` before publishing again.

Generating Javadoc
------------------

To generate API documentation:

    ./gradlew javadoc

The javadoc is available at `build/docs/javadoc/index.html`.

Generating Developer's Guide
----------------------------

To generate the developer's guide in HTML format:

    quarto render docs

The guide is available at `docs/_site/index.html`. The guide, together with the Java, JavaScript and Python API documentation, is automatically published to [GitHub Pages](https://org-arl.github.io/fjage/) by the `docs` GitHub Actions workflow on every push to `master` that touches the documentation or sources.

Publishing fjagepy package to PyPI
----------------------------------

Follow the steps below to publish the fjagepy python package to [PyPI](https://pypi.python.org/pypi)

1. Install "twine":

    `pip install twine`

2. Install "wheel":

    `pip install wheel`

3. Create a source distribution:

    `python gateways/python/setup.py sdist`

4. Create a wheel for the project:

    `python gateways/python/setup.py bdist_wheel`

5. Create an account on [PyPI](https://pypi.python.org/pypi)

6. Once you have an account, you can upload your distribution to PyPI using twine:

    `twine upload gateways/python/dist/*`
