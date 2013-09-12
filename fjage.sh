#!/bin/bash
#
# Usage:
#   ./fjage.sh [-gui] [-nocolor]

CLASSPATH=.`find build/libs -name *.jar -exec /bin/echo -n :'{}' \;`

# Cygwin/Windows uses a ";" classpath separator
if [ $(expr "$(uname -s)" : 'CYGWIN.*') -gt 0 ];then
  CLASSPATH=`echo "$CLASSPATH" | sed 's/:/;/g'`
fi

# process command line options
GUI=false
OPT1=
while [[ $1 == -* ]]
do
  OPT=$1
  if [ $OPT = "-gui" ]
  then
    shift
    GUI=true
  else
    shift
    OPT1="$OPT1 $OPT"
  fi
  shift
done

mkdir -p logs
java -cp "$CLASSPATH" -Dfjage.gui=$GUI org.arl.fjage.shell.GroovyBoot $@ etc/initrc.groovy
