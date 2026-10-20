param([Parameter(Mandatory=$true)][string]$TvAddress,[ValidateSet('voice','fridge','sync','schedule')][string]$Mode='voice',[string]$ReplayText='',[string]$ExpectedFridge='',[string]$ExpectedFood='',[string]$FixtureId='')
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
& "$javaDir\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$project\build\classes;$jar" -d "$dest\classes" "$PSScriptRoot\VoiceFlowChecks.java" "$PSScriptRoot\FridgeFlowChecks.java" "$PSScriptRoot\InventoryWebChecks.java" "$PSScriptRoot\SchedulePanelChecks.java"; Check
& "$javaDir\jar.exe" cf "$dest\checks.jar" -C "$dest\classes" .; Check
& "$bt\d8.bat" --lib $jar --min-api 24 --output "$dest\dex" "$dest\checks.jar"; Check
& "$bt\aapt.exe" package -f -M "$PSScriptRoot\AndroidManifest.xml" -I $jar -F "$dest\base.apk"; Check
Push-Location "$dest\dex"
try { & "$bt\aapt.exe" add "$dest\base.apk" classes.dex; Check } finally { Pop-Location }
& "$bt\zipalign.exe" -f 4 "$dest\base.apk" "$dest\aligned.apk"; Check
& "$bt\apksigner.bat" sign --ks "$project\dev.keystore" --ks-key-alias tvclock --ks-pass pass:android --key-pass pass:android --out "$dest\checks.apk" "$dest\aligned.apk"; Check
& $adb -s $TvAddress install -r "$dest\checks.apk"; Check
try {
    $class=if($Mode -eq 'schedule'){'SchedulePanelChecks'}elseif($Mode -eq 'sync'){'InventoryWebChecks'}elseif($Mode -eq 'fridge'){'FridgeFlowChecks'}else{'VoiceFlowChecks'}
    $marker=if($Mode -eq 'schedule'){'Schedule panel passed'}elseif($Mode -eq 'sync'){'Inventory sync passed'}elseif($Mode -eq 'fridge'){'Fridge flow passed'}else{'Voice flow passed'}
    $extra=@()
    if($Mode -eq 'sync'){if($FixtureId -notmatch '^web-check-[a-zA-Z0-9-]+$'){throw 'Sync checks require an isolated web-check fixture'};$extra=@('-e','fixtureId',$FixtureId)}
    if($ReplayText){
        if($Mode -ne 'fridge' -or !$ExpectedFridge -or !$ExpectedFood){throw 'Replay requires fridge mode, expected fridge and food'}
        $extra=@('-e','replayTextB64',[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($ReplayText)),'-e','expectedFridgeB64',[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($ExpectedFridge)),'-e','expectedFoodB64',[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($ExpectedFood)))
        $marker='Fridge read-only replay passed'
    }
    $result=& $adb -s $TvAddress shell am instrument -w @extra "cn.livingroom.flipclock.checks/cn.livingroom.flipclock.$class"
    $result | Write-Output
    if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch $marker){throw "$Mode integration checks failed"}
} finally { & $adb -s $TvAddress uninstall cn.livingroom.flipclock.checks }
