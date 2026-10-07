#!/bin/sh

# fjage version
VERSION=2.6.0

# dependency versions (matching build.gradle for fjage $VERSION)
GROOVY_VERSION=2.5.23
JLINE_VERSION=3.29.0
COMMONS_LANG_VERSION=3.20.0
COMMONS_IO_VERSION=2.21.0
CLONING_VERSION=1.9.12
OBJENESIS_VERSION=2.1
JETTY_VERSION=9.4.58.v20250814
GSON_VERSION=2.13.2
JSERIALCOMM_VERSION=2.10.4
SERVLET_VERSION=3.1.0

MVN=https://repo1.maven.org/maven2

# create the folder structure
mkdir -p build/libs etc logs samples

# download necessary JARs
cd build/libs
curl -LO https://github.com/org-arl/fjage/releases/download/v$VERSION/fjage-$VERSION.jar
curl -O $MVN/org/codehaus/groovy/groovy/$GROOVY_VERSION/groovy-$GROOVY_VERSION.jar
curl -O $MVN/org/jline/jline/$JLINE_VERSION/jline-$JLINE_VERSION-jdk8.jar
curl -O $MVN/org/apache/commons/commons-lang3/$COMMONS_LANG_VERSION/commons-lang3-$COMMONS_LANG_VERSION.jar
curl -O $MVN/commons-io/commons-io/$COMMONS_IO_VERSION/commons-io-$COMMONS_IO_VERSION.jar
curl -O $MVN/uk/com/robust-it/cloning/$CLONING_VERSION/cloning-$CLONING_VERSION.jar
curl -O $MVN/org/objenesis/objenesis/$OBJENESIS_VERSION/objenesis-$OBJENESIS_VERSION.jar
curl -O $MVN/com/google/code/gson/gson/$GSON_VERSION/gson-$GSON_VERSION.jar
curl -O $MVN/com/fazecast/jSerialComm/$JSERIALCOMM_VERSION/jSerialComm-$JSERIALCOMM_VERSION.jar
curl -O $MVN/javax/servlet/javax.servlet-api/$SERVLET_VERSION/javax.servlet-api-$SERVLET_VERSION.jar
for jar in jetty-server jetty-servlet jetty-security jetty-rewrite jetty-http jetty-io jetty-util jetty-util-ajax jetty-client; do
  curl -O $MVN/org/eclipse/jetty/$jar/$JETTY_VERSION/$jar-$JETTY_VERSION.jar
done
for jar in websocket-server websocket-servlet websocket-client websocket-common websocket-api; do
  curl -O $MVN/org/eclipse/jetty/websocket/$jar/$JETTY_VERSION/$jar-$JETTY_VERSION.jar
done
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
