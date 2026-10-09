fjåge Build Instructions
========================

Prerequisites
-------------

* JDK 8 - the build compiles and tests with a JDK 8 toolchain. Gradle is fetched by `./gradlew` and can run on JDK 8 to 24.
* [Quarto](https://quarto.org/) - required for building documentation
* Python 3 - required for building and testing the fjagepy gateway
* Node.js and pnpm/npm - required for building and testing the fjage.js gateway
* Make/C-compiler - required for building fjage.c gateway

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