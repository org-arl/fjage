#!/bin/sh

# fjage version
VERSION=1.12.3

# create the folder structure
mkdir -p build/libs etc logs samples

# download necessary JARs
cd build/libs
curl -O https://repo1.maven.org/maven2/com/github/org-arl/fjage/$VERSION/fjage-$VERSION.jar
curl -O https://repo1.maven.org/maven2/org/codehaus/groovy/groovy/2.5.9/groovy-2.5.9.jar
curl -O https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.9/commons-lang3-3.9.jar
curl -O https://repo1.maven.org/maven2/org/jline/jline/3.12.1/jline-3.12.1.jar
curl -O https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.5/gson-2.8.5.jar
curl -O https://repo1.maven.org/maven2/org/eclipse/jetty/websocket/websocket-servlet/9.4.26.v20200117/websocket-servlet-9.4.26.v20200117.jar
cd ../..

# download init scripts and logging configuration
cd etc
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/etc/initrc.groovy
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/etc/initrc-rconsole.groovy
cd ..

# download sample agents
cd samples
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/samples/01_hello.groovy
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/samples/02_ticker.groovy
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/samples/03_weatherStation.groovy
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/samples/03_weatherRequest.groovy
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/samples/WeatherForecastReqMsg.groovy
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/samples/04_weatherStation.groovy
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/samples/04_weatherRequest.groovy
cd ..

# download startup script
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/fjage.sh
curl -O https://raw.githubusercontent.com/org-arl/fjage/master/rconsole.sh
chmod a+x fjage.sh rconsole.sh
