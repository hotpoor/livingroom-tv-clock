param([Parameter(Mandatory=$true)][string]$TvAddress)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$tools=Join-Path (Split-Path $project -Parent) '.tools'
$compiler=Get-ChildItem -LiteralPath $tools -Filter javac.exe -Recurse | Select-Object -First 1
$javaDir=Split-Path $compiler.FullName -Parent
$env:JAVA_HOME=Split-Path $javaDir -Parent
$sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{"$tools\android-sdk"}
$bt=(Get-ChildItem "$sdk\build-tools" -Filter aapt.exe -Recurse | Select-Object -First 1).DirectoryName
$jar=(Get-ChildItem "$sdk\platforms" -Filter android.jar -Recurse | Select-Object -First 1).FullName
$dest="$project\build\device-checks"
$adb="$tools\platform-tools\adb.exe"
function Check { if($LASTEXITCODE -ne 0){throw "Device check command failed: $LASTEXITCODE"} }
New-Item -ItemType Directory -Force "$dest\classes","$dest\dex" | Out-Null
& "$javaDir\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$project\build\classes;$jar" -d "$dest\classes" "$PSScriptRoot\VoiceFlowChecks.java"; Check
& "$javaDir\jar.exe" cf "$dest\checks.jar" -C "$dest\classes" .; Check
& "$bt\d8.bat" --lib $jar --min-api 24 --output "$dest\dex" "$dest\checks.jar"; Check
& "$bt\aapt.exe" package -f -M "$PSScriptRoot\AndroidManifest.xml" -I $jar -F "$dest\base.apk"; Check
Push-Location "$dest\dex"
try { & "$bt\aapt.exe" add "$dest\base.apk" classes.dex; Check } finally { Pop-Location }
& "$bt\zipalign.exe" -f 4 "$dest\base.apk" "$dest\aligned.apk"; Check
& "$bt\apksigner.bat" sign --ks "$project\dev.keystore" --ks-key-alias tvclock --ks-pass pass:android --key-pass pass:android --out "$dest\checks.apk" "$dest\aligned.apk"; Check
& $adb -s $TvAddress install -r "$dest\checks.apk"; Check
try {
    $result=& $adb -s $TvAddress shell am instrument -w cn.livingroom.flipclock.checks/cn.livingroom.flipclock.VoiceFlowChecks
    $result | Write-Output
    if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'Voice flow passed'){throw 'Voice flow integration checks failed'}
} finally { & $adb -s $TvAddress uninstall cn.livingroom.flipclock.checks }
