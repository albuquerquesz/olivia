# APKs oficiais do CI

O workflow principal e `.github/workflows/android-wearables-apks.yml`. Ele gera somente os dois
artefatos que correspondem ao produto demonstravel:

| Artefato | Gradle | DAT | App Check | Uso |
|---|---|---|---|---|
| `olivia-developer-wearables-debug` | `assembleGlassesDebug` | `MWDAT_MOCK_DEVICE_KIT=true` | Play Integrity | Wearables Developer/MockDeviceKit com camera do telefone |
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

Temporariamente, o artefato developer do CI tambem usa esse mesmo certificado de release, embora
continue sendo `DEBUG`, use MockDeviceKit e selecione o provider Play Integrity. Isso permite que
novas builds atualizem a instalacao existente sem trocar a assinatura e sem depender de debug token.
O APK developer continua privado e nao deve ser tratado como uma build de producao.

## Download facil do APK developer

Quando o workflow principal termina com sucesso, o APK developer fica imediatamente disponivel
como artifact do Actions. Um workflow assincrono publica ou atualiza o pre-release privado
[`developer-latest`](https://github.com/albuquerquesz/olivia/releases/tag/developer-latest) em
segundo plano com:

- `olivia-developer-wearables-debug.apk`;
- `SHA256SUMS`.

Esse é o APK para instalar no celular durante o hackathon. Ele usa `glassesDebug` com
`MWDAT_MOCK_DEVICE_KIT=true`, Wearables Developer e MockDeviceKit. A aba Actions continua
mantendo o mesmo artefato para auditoria do workflow.

O APK developer publicado pelo CI é empacotado somente para `arm64-v8a`, que é a ABI dos
celulares Android modernos usados no hackathon. Builds locais continuam universais por padrão;
o APK production do CI também continua universal para preservar compatibilidade no ensaio físico.

## App Check e assinatura

O APK developer do CI e `DEBUG` para manter logs e MockDeviceKit, mas o provider e selecionado
explicitamente como Play Integrity e a assinatura e a mesma da release. Portanto, ele nao depende
de debug token. Builds locais `glassesDebug` continuam usando Debug Provider por padrao; para
reproduzir o APK do CI, use `-Polivia.debugAppCheckProvider=play-integrity` e a mesma assinatura.

Depois da migracao inicial para a assinatura fixa, instale novas builds com `adb install -r -d` e
nao limpe os dados do app. Outro aparelho ainda precisa cumprir a atestacao Play Integrity; nao
ha token de debug para cadastrar.

O APK production e `RELEASE`, portanto o codigo seleciona Play Integrity. Compilar o APK e
assina-lo nao prova enforcement: o app Android, certificado, projeto Firebase, quota e canal de
distribuicao precisam estar registrados no Console. O app nao cai silenciosamente para MockDeviceKit
quando essa configuracao falha.

## Validacao esperada

1. Instalar `olivia-developer-wearables-debug` para o ensaio sem oculos.
2. Confirmar o app developer no Firebase App Check com Play Integrity e o SHA-256 da keystore de release.
3. Confirmar `MWDAT_MOCK_DEVICE_KIT=true` e primeiro frame do MockDeviceKit.
4. Instalar `olivia-production-wearables-release` somente no ensaio com Ray-Ban Meta.
5. Confirmar `MWDAT_MOCK_DEVICE_KIT=false`, primeiro frame DAT e estado `CONNECTED`.

MockDeviceKit cobre o caminho de captura DAT e preview, mas nao prova gravacao nativa 3K, audio,
Bluetooth fisico, transferencia pos-`STOPPED` ou bateria.
