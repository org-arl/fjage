Frequently Asked Questions
==========================

Groovy syntax
-------------

:Q: Why does `add oneShotBehavior { ... }` or `addOneShotBehavior { ... }` (or other behaviors) not work even though I have `GroovyExtensions` enabled?

:A: These two synaxes supported by `GroovyExtensions` have been removed since fjåge v1.2. Instead use the new Groovy syntax `add new OneShotBehavior({ ... })`. The similarity with the Java syntax avoids confusions caused due to previous Groovy syntax.

Logging
-------

:Q: How do I temporarily enable debug logging for fjåge applications without writing my own `logging.properties`?

:A: Debug logging (log level `ALL`) can be enabled by simply passing a `-debug` flag on the command line to `GroovyBoot`. To enable debug logging for only certain loggers, you can use a flag of the form `-debug:loggername`. Startup scripts (such as `fjage.sh`) pass all arguments to `GroovyBoot`, allowing this flag to be simply included on the command line while starting the application.
