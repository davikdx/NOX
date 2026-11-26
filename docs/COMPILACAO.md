# Compilação, assinatura e atualização

O projeto usa a plataforma Android 35, Build-Tools 35.0.0, Android Gradle Plugin 8.9.2, Gradle 8.11.1 e JDK 17. A versão de produção usa o identificador `br.app.nox`, o código de versão 1 e o nome 1.0.0. A variante de desenvolvimento usa `br.app.nox.dev` e mantém seus dados separados. O APK suporta Android 10 (API 29) em diante. O primeiro build precisa acessar os repositórios das ferramentas; o aplicativo instalado funciona offline.

## Compilar no Windows

1. Instale Android Studio com o SDK da plataforma Android 15 (API 35), Android SDK Platform-Tools e Android SDK Build-Tools 35.0.0 pelo SDK Manager. Se necessário, instale o JDK 17 e configure `JAVA_HOME` para a pasta do JDK. Configure `ANDROID_HOME` para a pasta do SDK.
2. Extraia o ZIP e abra a pasta que contém `settings.gradle` no Android Studio. Aguarde a sincronização. Na linha de comando, abra o PowerShell nessa mesma pasta.
3. Para compilar a variante de desenvolvimento, executar o lint e verificar a assinatura, rode `.\montar-apk.ps1 debug`. O binário será `app/build/outputs/apk/debug/app-debug.apk`. O Gradle gera a chave de desenvolvimento automaticamente.
4. Em Linux/macOS, rode `sh gradlew :app:assembleDebug :app:lintDebug` e verifique o APK com `apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk`. O utilitário está em `$ANDROID_HOME/build-tools/35.0.0`.

## Criar a versão de uso diário

Crie uma chave de distribuição pessoal uma única vez. Pelo Android Studio, use **Build → Generate Signed App Bundle or APK → APK → Create new**. Guarde o arquivo em um local privado fora do projeto e faça uma cópia de segurança. Como alternativa, o comando abaixo pede as senhas no terminal e cria um keystore no caminho escolhido.

```powershell
keytool -genkeypair -keystore C:\Chaves\nox-release.p12 -storetype PKCS12 -alias nox -keyalg RSA -keysize 3072 -validity 10000
```

No assistente, selecione a variante **release** e use a mesma chave em todas as atualizações. Para assinar pela linha de comando, configure no ambiente da sessão `NOX_KEYSTORE` (caminho absoluto), `NOX_KEY_ALIAS`, `NOX_STORE_PASSWORD` e `NOX_KEY_PASSWORD`. Para keystores PKCS12 gerados pelo comando acima, use a mesma senha para o arquivo e a chave. A seguinte forma evita digitar as senhas como comandos no histórico do PowerShell.

```powershell
$env:NOX_KEYSTORE = 'C:\Chaves\nox-release.p12'
$env:NOX_KEY_ALIAS = 'nox'
$credential = Get-Credential -UserName 'nox' -Message 'Senha do keystore pessoal'
$env:NOX_STORE_PASSWORD = $credential.GetNetworkCredential().Password
$env:NOX_KEY_PASSWORD = $env:NOX_STORE_PASSWORD
try { .\montar-apk.ps1 release } finally {
    Remove-Item Env:\NOX_STORE_PASSWORD,Env:\NOX_KEY_PASSWORD
}
```

O script gera `app/build/outputs/apk/release/app-release.apk`, executa lint, verifica os esquemas de assinatura e mostra a impressão digital do certificado e o SHA-256 do arquivo. Guarde a impressão digital do certificado com a chave. Abrir e usar o aplicativo em um aparelho é uma etapa separada dessa verificação.

## Preservação dos dados

Para publicar uma atualização pessoal, mantenha `applicationId`, aumente `versionCode` e assine com a chave de produção original. No aparelho, instale a nova versão por cima da anterior. O Android compara as assinaturas. Se a assinatura for incompatível, interrompa e identifique a chave correta. Desinstalar o aplicativo apaga a base privada, portanto faça um backup local pelo Nox antes de qualquer desinstalação. A variante de desenvolvimento usa um pacote independente e não atualiza a produção.

O wrapper é o publicado no repositório do Gradle. Seu SHA-256 é `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`. O checksum da distribuição está fixado em `gradle-wrapper.properties`.

[Compatibilidade do plugin Android](https://developer.android.com/build/releases/agp-8-9-0-release-notes) · [Verificação das distribuições do Gradle](https://gradle.org/release-checksums/) · [Assinatura de aplicativos](https://developer.android.com/studio/publish/app-signing)
