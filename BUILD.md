fjåge Build Instructions
========================

Prerequisites
-------------

* [Gradle](http://www.gradle.org/)
* [Sphinx](http://sphinx-doc.org/) - required for building documentation
* [GnuPG](http://www.gnupg.org/) - required for signing artifacts for MavenCentral

Generating JARs
---------------

To generate project JARs:

    gradle jars

The project and dependency JARs are located in `build/libs/`.

Testing the build
-----------------

To test the build:

    gradle test

Test report is available at `build/reports/tests/index.html`.

Uploading JARs to Maven Central
-------------------------------
**To be done by project administator only**

Create `~/.gradle/gradle.properties` with the following keys:

  * signing.keyId
  * signing.password
  * signing.secretKeyRingFile
  * sonatypeUsername
  * sonatypePassword

To generate and upload the archives to Sonatype repository:

    gradle uploadArchives

Finally, go to [Sonatype](http://oss.sonatype.org/) and close, check and publish the archives.  Once published, the archive is synchronized with Maven Central in about 2 hours.

Generating Javadoc
------------------

To generate API documentation:

    gradle javadoc

The javadoc is available at `docs/javadoc/index.html`.

Generating Developer's Guide
----------------------------

To generate the developer's guide and related documentation in HTML format:

    gradle doc

The guide is available at `htdocs/doc/html/index.html`.

The Gradle build script simply executes a `make html` command in turn calls Sphinx build script to generate the documentation. The Gradle build script suppresses the output of Sphinx. In case of any problems, it may be useful to look at the output by directly invoking `make html`.

Publishing fjagepy package to PyPI
----------------------------------

Follow the steps below to publish the fjagepy python package to [PyPI](https://pypi.python.org/pypi)

1. Install "twine":

    `pip install twine`

2. Install "wheel":

    `pip install wheel`

3. Create a source distribution:

    `python src/main/python/setup.py sdist`

4. Create a wheel for the project:

    `python src/main/python/setup.py bdist_wheel`

5. Create an account on [PyPI](https://pypi.python.org/pypi)

6. Once you have an account, you can upload your distribution to PyPI using twine:

    `twine upload src/main/python/dist/*`
