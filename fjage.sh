#!/bin/bash
#
# Usage:
#   ./fjage.sh [-gui] [-port port] [-baud baud] [-rs232 devname]

CLASSPATH=.`find build/libs -name *.jar -exec /bin/echo -n :'{}' \;`
export CLASSPATH=$CLASSPATH:samples

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
  if [ $OPT = "-gui" ]; then
    GUI=true
  elif [ $OPT = "-port" ]; then
    shift
    OPT1="$OPT1 -Dfjage.port=$1"
  elif [ $OPT = "-baud" ]; then
    shift
    OPT1="$OPT1 -Dfjage.baud=$1"
  elif [ $OPT = "-rs232" ]; then
    shift
    OPT1="$OPT1 -Dfjage.devname=$1"
  else
    shift
    OPT1="$OPT1 $OPT"
  fi
  shift
done

mkdir -p logs
java -cp "$CLASSPATH" -Dfjage.gui=$GUI $OPT1 org.arl.fjage.shell.GroovyBoot $@ etc/initrc.groovy
