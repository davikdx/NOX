#!/usr/bin/env bash
# First distribution only. The private recovery key is held outside the repository.
set -euo pipefail
umask 077
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
export NOX_PASSWORD="$(openssl rand -hex 32)"
echo "::add-mask::$NOX_PASSWORD"
printf '%s\n' "$NOX_PASSWORD" > "$tmp/senha.txt"
keytool -genkeypair -storetype PKCS12 -keystore "$tmp/nox.p12" -storepass:env NOX_PASSWORD -keypass:env NOX_PASSWORD -alias nox -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Nox Android'
signer="$ANDROID_HOME/build-tools/35.0.0/apksigner"
"$signer" sign --ks "$tmp/nox.p12" --ks-pass env:NOX_PASSWORD --ks-key-alias nox --out build/pacotes/Nox-1.0.apk app/build/outputs/apk/release/app-release-unsigned.apk
"$signer" verify --verbose --print-certs build/pacotes/Nox-1.0.apk > build/pacotes/certificado.txt
tar -C "$tmp" -czf "$tmp/key.tgz" nox.p12 senha.txt
openssl cms -encrypt -binary -aes-256-cbc -in "$tmp/key.tgz" -outform DER -out build/pacotes/assinatura-nox.cms qa/recovery-public.pem

