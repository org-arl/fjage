#Powershell script to install fjage for Windows

$ver = "1.4"

Write-Host ""
Write-Host "Creating directories"

$op = new-item $pwd\build -itemtype directory 2>$null
$op = new-item $pwd\build\libs -itemtype directory 2>$null
$op = new-item $pwd\etc -itemtype directory 2>$null
$op = new-item $pwd\logs -itemtype directory 2>$null
$op = new-item $pwd\samples -itemtype directory 2>$null

Write-Host "Downloading files: "

$webclient = New-Object System.Net.WebClient
Write-Host "  1. fjage-$ver.jar"
$webclient.DownloadFile("https://repo1.maven.org/maven2/com/github/org-arl/fjage/$ver/fjage-$ver.jar", "$pwd\build\libs\fjage-$ver.jar")
Write-Host "  2. groovy-all-2.4.4.jar"
$webclient.DownloadFile("https://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/2.4.4/groovy-all-2.4.4.jar", "$pwd\build\libs\groovy-all-2.4.4.jar")
Write-Host "  3. commons-lang3-3.1.jar"
$webclient.DownloadFile("https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.1/commons-lang3-3.1.jar", "$pwd\build\libs\commons-lang3-3.1.jar")
Write-Host "  4. jline-3.9.0.jar"
$webclient.DownloadFile("https://repo1.maven.org/maven2/jline/jline/3.9.0/jline-3.9.0.jar", "$pwd\build\libs\jline-3.9.0.jar")
Write-Host "  5. gson-2.8.2.jar"
$webclient.DownloadFile("https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.2/gson-2.8.2.jar", "$pwd\build\libs\gson-2.8.2.jar")

Write-Host "  6. initrc.groovy"
$webclient.DownloadFile("https://raw.githubusercontent.com/org-arl/fjage/master/etc/initrc.groovy", "$pwd\etc\initrc.groovy")

Write-Host "  7. samples\01_hello.groovy"
$webclient.DownloadFile("https://raw.githubusercontent.com/org-arl/fjage/master/samples/01_hello.groovy", "$pwd\samples\01_hello.groovy")
Write-Host "  8. samples\02_ticker.groovy"
$webclient.DownloadFile("https://raw.githubusercontent.com/org-arl/fjage/master/samples/02_ticker.groovy", "$pwd\samples\02_ticker.groovy")
Write-Host "  9. samples\03_weatherStation.groovy"
$webclient.DownloadFile("https://raw.githubusercontent.com/org-arl/fjage/master/samples/03_weatherStation.groovy", "$pwd\samples\03_weatherStation.groovy")
Write-Host "  10. samples\03_weatherRequest.groovy"
$webclient.DownloadFile("https://raw.githubusercontent.com/org-arl/fjage/master/samples/03_weatherRequest.groovy", "$pwd\samples\03_weatherRequest.groovy")
Write-Host "  11. samples\WeatherForecastReqMsg.groovy"
$webclient.DownloadFile("https://raw.githubusercontent.com/org-arl/fjage/master/samples/WeatherForecastReqMsg.groovy", "$pwd\samples\WeatherForecastReqMsg.groovy")
Write-Host "  12. samples\04_weatherStation.groovy"
$webclient.DownloadFile("https://raw.githubusercontent.com/org-arl/fjage/master/samples/04_weatherStation.groovy", "$pwd\samples\04_weatherStation.groovy")
Write-Host "  13. samples\04_weatherRequest.groovy"
$webclient.DownloadFile("https://raw.githubusercontent.com/org-arl/fjage/master/samples/04_weatherRequest.groovy", "$pwd\samples\04_weatherRequest.groovy")

Write-Host "Creating fjage.bat"
$op = New-Item $pwd\fjage.bat -type file -force -value "@echo off
set CLASSPATH=build\libs\commons-lang3-3.1.jar;build\libs\fjage-$ver.jar;build\libs\groovy-all-2.4.4.jar;build\libs\jline-3.9.0.jar;build\libs\gson-2.8.2.jar;samples
set GUI=false
java -Dfjage.gui= org.arl.fjage.shell.GroovyBoot etc/initrc.groovy" 2>$null

Write-Host "done."
Write-Host "Run fjage.bat. Type help for help topics"