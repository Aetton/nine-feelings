param([switch]$Release,[switch]$DeviceTests,[string]$SdkPath=$env:ANDROID_HOME,[string]$JavaPath=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(!$JavaPath){$candidate=Join-Path $PSScriptRoot '..\.tools\java\jdk-17.0.17+10';if(Test-Path -LiteralPath $candidate){$JavaPath=(Resolve-Path -LiteralPath $candidate).Path}}
if($JavaPath){$env:JAVA_HOME=$JavaPath}
if(!$SdkPath){$SdkPath=Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
$env:ANDROID_HOME=$SdkPath
$arguments=@('-p',$PSScriptRoot,'--no-daemon','assembleDebug','lint')
$compatKey=Join-Path $PSScriptRoot 'build\debug.jks'
if(Test-Path -LiteralPath $compatKey){$arguments+=('-PcompatDebugKeystore='+$compatKey)}
if($Release){$arguments+=@('assembleRelease','bundleRelease')}
if($DeviceTests){$arguments+='connectedDebugAndroidTest'}
& (Join-Path $PSScriptRoot 'gradlew.bat') @arguments
if($LASTEXITCODE -ne 0){throw 'Android build failed; see the error above.'}
Write-Output (Join-Path $PSScriptRoot 'app\build\outputs')
