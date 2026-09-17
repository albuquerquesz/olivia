# APKs oficiais do CI

O workflow principal e `.github/workflows/android-wearables-apks.yml`. Ele gera somente os dois
artefatos que correspondem ao produto demonstravel:

| Artefato | Gradle | DAT | App Check | Uso |
|---|---|---|---|---|
| `olivia-developer-wearables-debug` | `assembleGlassesDebug` | `MWDAT_MOCK_DEVICE_KIT=true` | Debug provider | Wearables Developer/MockDeviceKit com camera do telefone |
| `olivia-production-wearables-release` | `assembleGlassesRelease` | `MWDAT_MOCK_DEVICE_KIT=false` | Play Integrity | Ray-Ban Meta fisico via DAT/Bluetooth |

O antigo `mockDebug` continua somente como fallback offline legado. Ele nao usa o SDK DAT nem o
MockDeviceKit e nao deve ser usado para validar Firebase, Bluetooth ou oculos.

## Secrets obrigatorios do GitHub

Os valores devem existir no GitHub Actions como secrets protegidos, nunca no YAML ou no Git:

- `MWDAT_APPLICATION_ID`
- `MWDAT_CLIENT_TOKEN`
- `PICOVOICE_ACCESS_KEY`
- `OLIVIA_GOOGLE_SERVICES_JSON_B64`: `google-services.json` do app `com.olivia.companion`, codificado em base64
- `ANDROID_RELEASE_KEYSTORE_B64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

Os modelos Picovoice não são secret do GitHub porque o ZIP ultrapassa o limite de 48 KB por
secret. Eles ficam no release privado `internal-assets-v1`, no asset `picovoice-assets.zip`, e o
workflow baixa esse asset com o `GITHUB_TOKEN` efêmero. O ZIP deve conter na raiz:
`olivia_android.ppn`, `porcupine_params_pt.pv`, `olivia-rhino_pt_android_v4_1_0.rhn` e
`rhino_params_pt.pv`.

O keystore de release deve ser o mesmo certificado registrado para o app Android no Firebase,
Google Cloud e canal de distribuicao escolhido. O workflow falha antes do Gradle se algum valor
obrigatorio estiver ausente.

## App Check e assinatura

O APK developer e `DEBUG`, portanto instala o Debug App Check Provider. O token de debug e gerado
no dispositivo na primeira execucao e precisa ser registrado em Firebase Console; ele nao e
embutido no APK nem substituido por um secret de build.

O APK production e `RELEASE`, portanto o codigo seleciona Play Integrity. Compilar o APK e
assina-lo nao prova enforcement: o app Android, certificado, projeto Firebase, quota e canal de
distribuicao precisam estar registrados no Console. O app nao cai silenciosamente para MockDeviceKit
quando essa configuracao falha.

## Validacao esperada

1. Instalar `olivia-developer-wearables-debug` para o ensaio sem oculos.
2. Confirmar o token Debug App Check no Firebase Console do projeto de desenvolvimento.
3. Confirmar `MWDAT_MOCK_DEVICE_KIT=true` e primeiro frame do MockDeviceKit.
4. Instalar `olivia-production-wearables-release` somente no ensaio com Ray-Ban Meta.
5. Confirmar `MWDAT_MOCK_DEVICE_KIT=false`, primeiro frame DAT e estado `CONNECTED`.

MockDeviceKit cobre o caminho de captura DAT e preview, mas nao prova gravacao nativa 3K, audio,
Bluetooth fisico, transferencia pos-`STOPPED` ou bateria.
