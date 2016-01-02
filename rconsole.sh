#!/bin/sh

CLASSPATH=`find build/libs -name *.jar -exec /bin/echo -n :'{}' \;`
java -cp "$CLASSPATH" -Dhostname="$1" -Dport="$2" org.arl.fjage.shell.GroovyBoot etc/initrc-rconsole.groovy
