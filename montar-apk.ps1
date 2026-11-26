param([ValidateSet('debug','release')][string]$Variant = 'debug')
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    if ($Variant -eq 'release') {
        foreach ($name in 'NOX_KEYSTORE','NOX_KEY_ALIAS','NOX_STORE_PASSWORD','NOX_KEY_PASSWORD') {
            if (-not [Environment]::GetEnvironmentVariable($name)) {
                throw "Defina $name no ambiente desta sessão para assinar a versão de uso diário."
            }
        }
        if (-not (Test-Path -LiteralPath $env:NOX_KEYSTORE -PathType Leaf)) {
            throw 'O arquivo NOX_KEYSTORE não foi encontrado.'
        }
    }

    $capitalized = (Get-Culture).TextInfo.ToTitleCase($Variant)
    & .\gradlew.bat ":app:assemble$capitalized" ":app:lint$capitalized"
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar ou verificar o aplicativo.' }

    $sdkRoots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")
    $verifier = $sdkRoots | Where-Object { $_ } |
        ForEach-Object { Join-Path $_ 'build-tools\35.0.0\apksigner.bat' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $verifier) {
        throw 'Instale Android SDK Build-Tools 35.0.0 e defina ANDROID_HOME para verificar a assinatura.'
    }
    $apk = Join-Path $PSScriptRoot "app\build\outputs\apk\$Variant\app-$Variant.apk"
    & $verifier verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'A assinatura do APK não passou na verificação.' }
    Write-Output (Get-FileHash -LiteralPath $apk -Algorithm SHA256)
    Write-Output $apk
}
finally {
    Pop-Location
}
