$ErrorActionPreference='Stop'
& "$PSScriptRoot\build.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
$androidRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path (Split-Path $PSScriptRoot -Parent) '.tools\android-sdk' }
$androidJar=(Get-ChildItem "$androidRoot\platforms" -Recurse -Filter android.jar | Select-Object -First 1).FullName
$testDir=Join-Path $PSScriptRoot 'build\checks'
New-Item -ItemType Directory -Force $testDir | Out-Null
$classpath="$PSScriptRoot\build\classes;$androidJar"
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp $classpath -d $testDir "$PSScriptRoot\checks\TimeChecks.java"
if ($LASTEXITCODE -ne 0) { throw 'Check compilation failed' }
& "$env:JAVA_HOME\bin\java.exe" -cp "$testDir;$classpath" cn.livingroom.flipclock.TimeChecks
if ($LASTEXITCODE -ne 0) { throw 'Time checks failed' }
foreach($name in @('inventory','schedule')) { Get-Content "$PSScriptRoot\assets\$name.json" -Raw | ConvertFrom-Json | Out-Null }
Write-Output 'Build, time checks and public seed JSON passed'
