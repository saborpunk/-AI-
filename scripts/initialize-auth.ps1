$ErrorActionPreference = 'Stop'
$path = Join-Path (Split-Path $PSScriptRoot -Parent) 'config/auth.local.properties'
if (Test-Path -LiteralPath $path) { Write-Output 'Existing auth configuration preserved.'; exit 0 }
$bytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$value = 'auth.jwt.secret-base64=' + [Convert]::ToBase64String($bytes) + "`n"
# Never print the key or replace an existing one: replacement invalidates issued tokens.
[IO.File]::WriteAllText($path, $value, [Text.UTF8Encoding]::new($false))
Write-Output 'Created gitignored project-local JWT key.'
