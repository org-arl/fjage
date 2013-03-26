#!/bin/sh

CLASSPATH=`find build/libs -name *.jar -exec /bin/echo -n :'{}' \;`
mkdir -p logs
java -cp $CLASSPATH -Djava.util.logging.config.file=etc/logging.properties org.arl.fjage.shell.GroovyBoot etc/initrc.groovy
