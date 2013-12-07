rem fjage for windows, zip file generator
@echo off

set VERSION=1.3.2
set ROOTDIR=fjage-%VERSION%
set LIBPATH=build\lib
set ETC=etc
set LOGS=logs
set SAMPLES=samples

rem Create the folder structure
md %ROOTDIR%
md %ROOTDIR%\%LIBPATH%
md %ROOTDIR%\%ETC%
md %ROOTDIR%\%LOGS%
md %ROOTDIR%\%SAMPLES%

rem Need to have curl for windows with ssl installed for the following to work (Steps below)
rem 1. Download and unzip 64-bit cURL with SSL. - http://curl.download.nextag.com/download/curl-7.21.7-win64-ssl-sspi.zip (32 bit - http://curl.haxx.se/latest.cgi?curl=win32-ssl)
rem 2. Download the latest bundle of Certficate Authority Public Keys from mozilla.org. http://curl.haxx.se/ca/cacert.pem
rem 3. Rename this file from cacert.pem to curl-ca-bundle.crt
rem 4. Copy both of them to C:\Windows\System32 directory (to make both of them in PATH environment).

rem Download necessary JARs
curl -O http://search.maven.org/remotecontent?filepath=com/github/org-arl/fjage/%VERSION%/fjage-%VERSION%.jar
curl -O http://search.maven.org/remotecontent?filepath=org/codehaus/groovy/groovy-all/2.1.3/groovy-all-2.1.3.jar
curl -O http://search.maven.org/remotecontent?filepath=org/apache/commons/commons-lang3/3.1/commons-lang3-3.1.jar
curl -O http://search.maven.org/remotecontent?filepath=jline/jline/2.10/jline-2.10.jar
move *.jar %ROOTDIR%\%LIBPATH%

rem Download init scripts and logging configuration
curl -O https://raw.github.com/org-arl/fjage/master/etc/initrc.groovy
move initrc.groovy %ROOTDIR%\%ETC%

#TODO: Update the following path from dev to master
# download sample agents
curl -O https://raw.github.com/manuignatius/fjage/dev/samples/01_hello.groovy
curl -O https://raw.github.com/manuignatius/fjage/dev/samples/02_ticker.groovy
curl -O https://raw.github.com/manuignatius/fjage/dev/samples/03_weatherStation.groovy
curl -O https://raw.github.com/manuignatius/fjage/dev/samples/03_weatherRequest.groovy
curl -O https://raw.github.com/manuignatius/fjage/dev/samples/WeatherForecastReqMsg.groovy
curl -O https://raw.github.com/manuignatius/fjage/dev/samples/04_weatherStation.groovy
curl -O https://raw.github.com/manuignatius/fjage/dev/samples/04_weatherRequest.groovy
move *.groovy %ROOTDIR%\%SAMPLES%

rem Generate startup script
@echo off
echo @echo off> fjage.bat
echo.>> fjage.bat
echo set CLASSPATH=%LIBPATH%\commons-lang3-3.1.jar;%LIBPATH%\fjage-%VERSION%.jar;%LIBPATH%\groovy-all-2.1.3.jar;%LIBPATH%\jline-2.10.jar;%SAMPLES%\>> fjage.bat
echo.>> fjage.bat
echo set GUI=false>> fjage.bat
echo java -Dfjage.gui=%GUI% org.arl.fjage.shell.GroovyBoot etc/initrc.groovy>> fjage.bat
move fjage.bat %ROOTDIR%

rem Generating the zip folder, this is optional step, and is commented now.
rem Download the command line version of 7zip - 7za.exe - from http://www.7-zip.org/download.html
rem Make sure 7za can be accessed from this path (try copying it to C:\Windows\System32)
rem 7za a -tzip %ROOTDIR%.zip %ROOTDIR%

rem cls