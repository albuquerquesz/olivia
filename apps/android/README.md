# Olivia — companion app Android

App nativo Kotlin/Compose que hospeda o loop ao vivo da Olivia. O celular é a ponte
obrigatória do Device Access Toolkit; este módulo é a superfície onde a sessão, os
frames e o áudio vão viver. A interface usa exclusivamente o modo claro.

## Pré-requisitos

- JDK 17 ou superior.
- Android SDK com a plataforma API 36 instalada.
- Nenhum óculos físico e nenhum SDK do DAT são necessários para o flavor `mock`.

## Configuração

```sh
cp local.properties.example local.properties
```

Preencha `sdk.dir`. Se `ANDROID_HOME` já estiver definido no ambiente, o Gradle
encontra o SDK sozinho e `local.properties` fica opcional.

## Build

```sh
./gradlew assembleMockDebug
```

O APK sai em `app/build/outputs/apk/mock/debug/`. Os demais flavors seguem o mesmo
padrão (`assembleGlassesDebug`).

## Flavors

| Flavor    | applicationId              | Fonte da sessão                    |
| --------- | -------------------------- | ---------------------------------- |
| `mock`    | `com.olivia.companion.mock` | Óculos simulados, sem SDK          |
| `glasses` | `com.olivia.companion`      | Ray-Ban Meta via DAT               |

Os dois flavors instalam lado a lado. O `mock` é o caminho de demo offline garantido; o
`glasses` pode usar o MockDeviceKit com `mwdat_mock_device_kit=true` para simular a câmera dos
óculos no telefone.

## Versões

Tudo — Kotlin, Compose, AGP, SDKs e os artefatos do DAT — fica em
[`gradle/libs.versions.toml`](gradle/libs.versions.toml). Os artefatos `mwdat` estão
resolvidos do Maven Central e exclusivos do flavor `glasses`. O registro no Wearables
Developer Center está configurado localmente; o smoke test do MockDeviceKit passou no Moto G60.
Ele usa a câmera do telefone como feed simulado e não substitui a validação dos óculos físicos.

## Contratos e testes do domínio

O `:core` usa contratos 2.0.0; veja a [migração](../../docs/hackaton-day-prod/00-event-contract.md).
O [runner de integração](../../infra/testing/README.md) permite testar o mesmo módulo em Docker/JVM sem Android SDK ou DAT, com tarefas separadas de unitários e integração. Os testes desta primeira entrega foram escritos, mas ainda não executados.

## Artefatos oficiais

- `olivia-developer-wearables-debug`: flavor `glassesDebug` com `MWDAT_MOCK_DEVICE_KIT=true`, usa o Wearables SDK e MockDeviceKit.
- `olivia-production-wearables-release`: flavor `glassesRelease` com `MWDAT_MOCK_DEVICE_KIT=false`, usa DAT fisico e Play Integrity.
- `mockDebug`: fallback offline legado; nao usa DAT nem MockDeviceKit.

O workflow oficial e [android-wearables-apks.yml](../../.github/workflows/android-wearables-apks.yml). Os requisitos de secrets e assinatura estao em [ANDROID_CI_ARTIFACTS.md](../../docs/ANDROID_CI_ARTIFACTS.md).

## Segredos

`local.properties`, tokens, keystores e APKs são ignorados pelo Git. Para uma integração
registrada, `mwdat_application_id` e `mwdat_client_token` entram por `local.properties`
ou pelas variáveis de CI `MWDAT_APPLICATION_ID` e `MWDAT_CLIENT_TOKEN`. Em Developer Mode,
os placeholders explícitos `0/0` são usados.
