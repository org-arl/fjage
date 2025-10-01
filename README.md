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

Maven dependency (Github Maven repository)
------------------------

    <dependency>
      <groupId>com.github.org-arl</groupId>
      <artifactId>fjage</artifactId>
      <version>2.2.0</version>
    </dependency>

From version 2.1.0 onwards, fjåge will be published in the [Github Maven repository](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry) only. The previous versions are still available in Maven Central.

Versions
-------------

fjåge follows [Semantic Versioning](http://semver.org/). The current major version is 2.x.y.

The main changes in the version 2.0.0 compared to the previous 1.x series are:

* The `WebSocketConnector` class was renamed to `WebSocketHubConnector` to accurately reflect it being an aggregator of multiple WebSocket connections, mainly to be used for Shells.
* A new `WebSocketConnector` class was added to provide a direct WebSocket connection to a fjåge agent for use with `fjage.js` Gateways.
* `Shell` agent messages `PutFileReq` and `GetFileReq` messages [had their parameter names changed](https://github.com/org-arl/fjage/commit/a3314d557109c1a77b4cd95f8514a3f0c6cf0950) to be more consistent with the rest of the API.
* `WebServer` methods for adding handlers have been cleaned-up and renamed. Now `WebServer.addStatic` adds a handler to serve static files, `WebServer.addHandler` adds a generic handler.

Since these changes are not backward compatible, the version number was incremented to 2.0.0.

The last stable release of the 1.x series is 1.14. The patch branch [patch-1.14.x](https://github.com/org-arl/fjage/commits/patch-v1.14.x/) is maintained for bug fixes and minor improvements.

If you want to keep using fjåge 1.x, you can use the latest 1.14.x release.

## fjåge.js 2.0.0

With the update of `WebSocketConnector` to be a direct (non-aggregated) connection to a fjåge agent, the `fjage.js` library was also updated to support watching for specific topics as per the fjåge Gateway specification. This allows for a more efficient use of the WebSocket connection and reduces the amount of data sent over the network.

## Releases 1.15.x

The 1.15.x releases 1.15.0, 1.15.1, and 1.15.2 have some of the breaking changes (specifically the changed parameter names) but didn't increment the major version number. This was a oversight and the 1.15.x releases are not backward compatible with the 1.14.x releases. The 1.15.x releases are not maintained anymore and **should not be used**.

Contributing
------------

Contributions are always welcome! Clone, develop and do a pull request!

Try to stick to the coding style already in use in the repository. Additionally, some guidelines:

* [Commit message style](https://github.com/angular/angular.js/blob/master/DEVELOPERS.md#commits)

Building:

* `gradle` to build the jars including resources (webshell, fjage.js, etc.)
* `gradle test` to run all regression tests (automated through Github actions CI)
* `gradle publish` to upload jars to Maven staging (requires credentials)
* `make html` to build developer's documentation (automated through ReadTheDocs)
* `gradle javadoc` to build the Java API documentation
* `gradle jsdoc` to build the Javascript API documentation

License
-------

fjåge is licensed under the Simplified (3-clause) BSD license.
See [LICENSE.txt](http://github.com/org-arl/fjage/blob/master/LICENSE.txt) for more details.
