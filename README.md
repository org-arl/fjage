![](https://github.com/org-arl/fjage/workflows/CI/badge.svg)

fjåge
=====
**Framework for Java and Groovy Agents**

Introduction
------------

fjåge provides a **lightweight** and **easy-to-learn** framework for [agent-oriented software development](http://en.wikipedia.org/wiki/Agent-oriented_programming) in Java and Groovy. Although most of the functionality of the framework can be used in pure-Java projects, the adoption of Groovy in the project simplifies development immensely. Typically, initialization scripts, shell interaction and command scripts are written in Groovy. Agents and support classes may be written in Java or Groovy.

Key Features
------------

* Lightweight and fast
* Easy to learn, and rapid agent development cycle
* Agent development in Java or Groovy
* Interactive Groovy shell and scripting
* Easy switching between realtime operation and discrete event simulation
* APIs for access from Java, Groovy, Python, C, Julia, and Javascript
* JSON-based protocol to interface with external applications

Documentation
-------------

* [Release Notes](ReleaseNotes.md)
* [Getting Started](https://fjage.readthedocs.io/en/latest/quickstart.html)
* [Developer's Guide](https://fjage.readthedocs.io/en/latest/)
* [API documentation](http://org-arl.github.io/fjage/javadoc/)

Support
-------

* [Project Home](http://github.com/org-arl/fjage)
* [Issue Tracking](http://github.com/org-arl/fjage/issues)

Maven Central dependency
------------------------

    <dependency>
      <groupId>com.github.org-arl</groupId>
      <artifactId>fjage</artifactId>
      <version>1.14.2</version>
    </dependency>

Contributing
------------

Contributions are always welcome! Clone, develop and do a pull request!

Try to stick to the coding style already in use in the repository. Additionally, some guidelines:

* [Commit message style](https://github.com/angular/angular.js/blob/master/DEVELOPERS.md#commits)

Building:

* `gradle` to build the jars including resources (webshell, fjage.js, etc.)
* `gradle lite` to build only the jars
* `gradle test` to run all regression tests (automated through Github actions CI)
* `gradle publish` to upload jars to Maven staging (requires credentials)
* `make html` to build developer's documentation (automated through ReadTheDocs)
* `gradle javadoc` to build the Java API documentation
* `npm run docs` to build the Javascript API documentation

License
-------

fjåge is licensed under the Simplified (3-clause) BSD license.
See [LICENSE.txt](http://github.com/org-arl/fjage/blob/master/LICENSE.txt) for more details.
