$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$toolsDir = Join-Path (Split-Path $projectDir -Parent) '.tools'
$javaCompiler = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { Get-Item "$env:JAVA_HOME\bin\javac.exe" } else { Get-Command javac -ErrorAction SilentlyContinue }
if (!$javaCompiler -and (Test-Path -LiteralPath $toolsDir)) { $javaCompiler=Get-ChildItem -LiteralPath $toolsDir -Filter javac.exe -Recurse | Select-Object -First 1 }
if (!$javaCompiler) { throw 'Install JDK 17 and set JAVA_HOME, or put it under ../.tools' }
$compilerPath = if ($javaCompiler.FullName) { $javaCompiler.FullName } else { $javaCompiler.Source }
$env:JAVA_HOME = Split-Path (Split-Path $compilerPath -Parent) -Parent
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$toolsDir\android-sdk" }
if (!(Test-Path -LiteralPath $sdkRoot)) { throw 'Set ANDROID_HOME to an Android SDK with platform 28 and build-tools 35.0.0' }
$buildTools = if (Test-Path "$sdkRoot\build-tools\35.0.0\aapt.exe") { "$sdkRoot\build-tools\35.0.0" } else { (Get-ChildItem -LiteralPath "$sdkRoot\build-tools" -Filter aapt.exe -Recurse | Select-Object -First 1).DirectoryName }
$androidJar = if (Test-Path "$sdkRoot\platforms\android-28\android.jar") { "$sdkRoot\platforms\android-28\android.jar" } else { (Get-ChildItem -LiteralPath "$sdkRoot\platforms" -Filter android.jar -Recurse | Select-Object -First 1).FullName }
if (!$buildTools -or !$androidJar) { throw 'Android platform or build-tools missing' }
function Check { if ($LASTEXITCODE -ne 0) { throw "Build command failed ($LASTEXITCODE)" } }
New-Item -ItemType Directory -Force -Path "$projectDir\build\classes", "$projectDir\build\dex", "$projectDir\build\assets" | Out-Null
Copy-Item -Path "$projectDir\assets\*.json" -Destination "$projectDir\build\assets" -Force
if (Test-Path "$projectDir\assets\fonts") { Copy-Item -LiteralPath "$projectDir\assets\fonts" -Destination "$projectDir\build\assets" -Recurse -Force }
if (Test-Path -LiteralPath "$projectDir\local-assets") { Get-ChildItem -LiteralPath "$projectDir\local-assets" -Filter *.json | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination "$projectDir\build\assets" -Force } }
$privateFontTarget = Join-Path $projectDir 'build\assets\fonts\Fliqlo.otf'
if (Test-Path -LiteralPath $privateFontTarget) { Remove-Item -LiteralPath $privateFontTarget -Force }
if (Test-Path "$projectDir\local-assets\fonts") { Copy-Item -LiteralPath "$projectDir\local-assets\fonts" -Destination "$projectDir\build\assets" -Recurse -Force }
& "$buildTools\aapt.exe" package -f -M "$projectDir\AndroidManifest.xml" -S "$projectDir\res" -A "$projectDir\build\assets" -I $androidJar -F "$projectDir\build\base.apk"
Check
$sources = Get-ChildItem -LiteralPath "$projectDir\src" -Filter *.java -Recurse | ForEach-Object FullName
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d "$projectDir\build\classes" $sources
Check
& "$env:JAVA_HOME\bin\jar.exe" cf "$projectDir\build\classes.jar" -C "$projectDir\build\classes" .
Check
& "$buildTools\d8.bat" --lib $androidJar --min-api 24 --output "$projectDir\build\dex" "$projectDir\build\classes.jar"
Check
Copy-Item -LiteralPath "$projectDir\build\base.apk" -Destination "$projectDir\build\unsigned.apk" -Force
Push-Location "$projectDir\build\dex"
try { & "$buildTools\aapt.exe" add "$projectDir\build\unsigned.apk" classes.dex; Check } finally { Pop-Location }
& "$buildTools\zipalign.exe" -f 4 "$projectDir\build\unsigned.apk" "$projectDir\build\aligned.apk"
Check
if (!(Test-Path -LiteralPath "$projectDir\dev.keystore")) {
  & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore "$projectDir\dev.keystore" -storepass android -keypass android -alias tvclock -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Living Room Clock Development'
  Check
}
& "$buildTools\apksigner.bat" sign --ks "$projectDir\dev.keystore" --ks-key-alias tvclock --ks-pass pass:android --key-pass pass:android --out "$projectDir\build\livingroom-flipclock.apk" "$projectDir\build\aligned.apk"
Check
& "$buildTools\apksigner.bat" verify "$projectDir\build\livingroom-flipclock.apk"
Check
Write-Output "$projectDir\build\livingroom-flipclock.apk"
