#!/bin/sh

#use '-windows' option for creating a windows version

# fjage version
VERSION=1.3.2

# create the folder structure
mkdir -p build/libs etc logs samples

# download necessary JARs
cd build/libs
curl -O http://search.maven.org/remotecontent?filepath=com/github/org-arl/fjage/$VERSION/fjage-$VERSION.jar
curl -O http://search.maven.org/remotecontent?filepath=org/codehaus/groovy/groovy-all/2.1.3/groovy-all-2.1.3.jar
curl -O http://search.maven.org/remotecontent?filepath=org/apache/commons/commons-lang3/3.1/commons-lang3-3.1.jar
curl -O http://search.maven.org/remotecontent?filepath=jline/jline/2.10/jline-2.10.jar
#curl -O http://search.maven.org/remotecontent?filepath=uk/com/robust-it/cloning/1.9.0/cloning-1.9.0.jar
#curl -O http://search.maven.org/remotecontent?filepath=org/objenesis/objenesis/1.2/objenesis-1.2.jar
cd ../..

# download init scripts and logging configuration
cd etc
curl -O https://raw.github.com/org-arl/fjage/master/etc/initrc.groovy
cd ..

#TODO: Move from dev dir to master once done
# download sample agents
cd samples
curl -O https://raw.github.com/org-arl/fjage/master/samples/01_hello.groovy
curl -O https://raw.github.com/org-arl/fjage/master/samples/02_ticker.groovy
curl -O https://raw.github.com/org-arl/fjage/master/samples/03_weatherStation.groovy
curl -O https://raw.github.com/org-arl/fjage/master/samples/03_weatherRequest.groovy
curl -O https://raw.github.com/org-arl/fjage/master/samples/WeatherForecastReqMsg.groovy
curl -O https://raw.github.com/org-arl/fjage/master/samples/04_weatherStation.groovy
curl -O https://raw.github.com/org-arl/fjage/master/samples/04_weatherRequest.groovy
cd ..

# download startup script
curl -O https://raw.github.com/org-arl/fjage/master/fjage.sh
chmod a+x fjage.sh

# for creating zip file for windows
OPT1=
while [[ $1 == -* ]]
do
  OPT=$1
  if [ $OPT = "-windows" ]
  then
    shift
    
    mkdir -p fjage_windows
    cp -r build fjage_windows
    cp -r etc fjage_windows
    cp -r logs fjage_windows
    cp -r samples fjage_windows

    # fjage.bat
    echo "@echo off
set CLASSPATH=build\libs\commons-lang3-3.1.jar;build\libs\fjage-1.3.2.jar;build\libs\groovy-all-2.1.3.jar;build\libs\jline-2.10.jar;samples
set GUI=false
java -Dfjage.gui= org.arl.fjage.shell.GroovyBoot etc/initrc.groovy" > fjage.bat
    mv fjage.bat fjage_windows

    # create zip file
    zip -q fjage_windows.zip -r fjage_windows

    rm -rf fjage_windows
  fi
  shift
done

