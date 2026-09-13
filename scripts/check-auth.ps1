param([ValidateSet('flow','readback')][string]$Mode='flow')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$statePath = Join-Path $root '.tools/auth-check.json'
Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.UseProxy = $false
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(15)
function Call([string]$method,[string]$path,[int]$expected,$body=$null,[string]$token='') {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($method), 'http://127.0.0.1:8080/api/v1'+$path)
    if ($token) { $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token) }
    if ($null -ne $body) { $request.Content = [System.Net.Http.StringContent]::new(($body|ConvertTo-Json),[Text.Encoding]::UTF8,'application/json') }
    try {
        $response=$client.SendAsync($request).GetAwaiter().GetResult()
        try {
            if ([int]$response.StatusCode -ne $expected) { throw "$method $path returned $([int]$response.StatusCode), expected $expected" }
            $text=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if ($text) { return ($text|ConvertFrom-Json) }
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}
$previousToken=$env:SEED_TEST_TOKEN
try {
    if ($Mode -eq 'flow') {
        $name='smoke_'+[guid]::NewGuid().ToString('N').Substring(0,20)
        $password=[guid]::NewGuid().ToString()+[guid]::NewGuid().ToString()
        $user=Call 'POST' '/auth/register' 201 @{username=$name;password=$password;displayName='SYNTHETIC acceptance'}
        $login=Call 'POST' '/auth/login' 200 @{username=$name;password=$password}
        $password=$null
        Call 'GET' '/users/me' 401 | Out-Null
        Call 'GET' '/articles' 403 $null $login.accessToken | Out-Null
        $driver=Get-ChildItem (Join-Path $root '.m2/repository/com/mysql/mysql-connector-j') -Recurse -Filter '*.jar' | Select-Object -First 1
        & java -cp $driver.FullName scripts/UserAdmin.java $name role MERCHANT
        if ($LASTEXITCODE -ne 0) { throw 'Synthetic merchant provisioning failed' }
        $env:SEED_TEST_TOKEN=$login.accessToken
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-business.ps1 -Mode flow
        if ($LASTEXITCODE -ne 0) { throw 'Business acceptance failed' }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-draft.ps1 -ExpectUnavailable
        if ($LASTEXITCODE -ne 0) { throw 'Legacy preview failed' }
        # Only a 15-minute test token is kept in the ignored snapshot. No raw password.
        @{userId=$user.id;username=$name;token=$login.accessToken} | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
        Write-Output 'PASS: real registration/login, customer denial, local merchant grant, authenticated CRUD and legacy preview'
    } else {
        $saved=Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
        $me=Call 'GET' '/users/me' 200 $null $saved.token
        if ($me.id -ne $saved.userId -or $me.role -ne 'MERCHANT') { throw 'User did not survive restart' }
        $env:SEED_TEST_TOKEN=$saved.token
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-business.ps1 -Mode readback
        if ($LASTEXITCODE -ne 0) { throw 'Business readback failed' }
        Write-Output 'PASS: account, signed token and business records survive Java restart'
    }
} finally { $env:SEED_TEST_TOKEN=$previousToken; $client.Dispose(); $handler.Dispose() }
