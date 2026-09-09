param([string]$JdkHome = $env:JAVA_HOME)
# Dot-source this file with -JdkHome pointing to your JDK directory.
# Changes apply to the current terminal only.
$projectRoot = Split-Path $PSScriptRoot -Parent
if ($JdkHome) {
    if (-not (Test-Path (Join-Path $JdkHome 'bin\java.exe'))) { throw 'Invalid JDK directory' }
    $env:JAVA_HOME = $JdkHome
    $env:PATH = "$JdkHome\bin;$env:PATH"
}
$jdkRoot = Join-Path $projectRoot '.tools\jdk'
if (-not $JdkHome -and (Test-Path $jdkRoot)) {
    $localJdk = Get-ChildItem $jdkRoot -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if ($localJdk) {
        $env:JAVA_HOME = $localJdk.FullName
        $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    }
}
$env:MAVEN_USER_HOME = Join-Path $projectRoot '.m2'
# Prevent an unrelated system DEBUG variable from enabling Spring debug logs.
$env:DEBUG = 'false'
