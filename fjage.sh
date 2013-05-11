#!/bin/sh

CLASSPATH=.`find build/libs -name *.jar -exec /bin/echo -n :'{}' \;`

# Cygwin/Windows uses a ";" classpath separator
if [ $(expr "$(uname -s)" : 'CYGWIN.*') -gt 0 ];then
  CLASSPATH=`echo "$CLASSPATH" | sed 's/:/;/g'`
fi

mkdir -p logs
java -cp "$CLASSPATH" $@ org.arl.fjage.shell.GroovyBoot etc/initrc.groovy
