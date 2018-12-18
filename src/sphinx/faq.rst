Frequently Asked Questions
==========================

Groovy syntax
-------------

:Q: Why does `add oneShotBehavior { ... }` or `addOneShotBehavior { ... }` (or other behaviors) not work even though I have `GroovyExtensions` enabled?

:A: These two synaxes supported by `GroovyExtensions` have been removed since fjåge v1.2. Instead use the new Groovy syntax `add new OneShotBehavior({ ... })`. The similarity with the Java syntax avoids confusions caused due to previous Groovy syntax.

:Q: I just declared a variable in the shell using `def x = ...`, but I get a `No such property` error when I try accessing it!  Why?

:A: Variables declared with types or using `def` are available during execution of the command, but not exported to the variable binding of the shell. To declare a new variable in the binding, it should be declared without a type definition (e.g. `x = ...`).

:Q: What is the difference between `import` and `export`?

:A: In a Groovy script, `import` is used in the same sense as Java or Groovy, to import a package or class.  The imports are only active during the execution of the script.  `export` is used to add an import to the shell, so that import is in force in the shell even after the script has terminated.  At the shell prompt, `import` and `export` can be used interchangeably (with a slightly different syntax -- see `help export` for more information).

Logging
-------

:Q: How do I temporarily enable debug logging for fjåge applications without writing my own `logging.properties`?

:A: Debug logging (log level `ALL`) can be enabled by simply passing a `-debug` flag on the command line to `GroovyBoot`. To enable debug logging for only certain loggers, you can use a flag of the form `-debug:loggername`. Startup scripts (such as `fjage.sh`) pass all arguments to `GroovyBoot`, allowing this flag to be simply included on the command line while starting the application. An alternative solution is to use the command `logLevel` at the shell prompt to control the log level of a specific logger.  For more information, try `help logLevel`.

Precompiled scripts
-------------------

:Q: Why does my precompiled script not work correctly?

:A: Precompiled scripts should be derived from the `org.arl.fjage.shell.BaseGroovyScript` base class. To do this, ensure that you have the `@groovy.transform.BaseScript org.arl.fjage.shell.BaseGroovyScript fGroovyScript` annotation in the script.
