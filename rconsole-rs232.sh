#!/bin/sh
#
# Usage:
#   ./rconsole-rs232.sh devname [baud]

CLASSPATH=`find build/libs -name *.jar -exec /bin/echo -n :'{}' \;`
java -cp "$CLASSPATH" -Ddevname="$1" -Dbaud="$2" org.arl.fjage.shell.GroovyBoot etc/initrc-rconsole.groovy
