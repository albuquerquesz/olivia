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

Para habilitar o comando de voz no dispositivo, preencha `picovoice_access_key` e
coloque o modelo `.ppn` customizado para “Oi, Olivia” em `app/src/main/assets/`
(ou informe um caminho absoluto em `picovoice_keyword_path`). O reconhecimento do
comando usa o serviço on-device do Android; sem chave, modelo ou serviço local o
adapter fica indisponível e o fallback de debug continua disponível.

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

Os dois flavors instalam lado a lado. O `mock` é o caminho de demo garantido e deve
continuar subindo sem hardware.

## Versões

Tudo — Kotlin, Compose, AGP, SDKs e os artefatos do DAT — fica em
[`gradle/libs.versions.toml`](gradle/libs.versions.toml). Os artefatos `mwdat` estão
declarados mas ainda não aplicados: a autenticação no GitHub Packages, o
`APPLICATION_ID` e o smoke test do MockDeviceKit são da task INT-00.

## Segredos

`local.properties`, tokens, keystores e APKs são ignorados pelo Git. O token do DAT
entra por `GITHUB_TOKEN` no ambiente ou por `github_token` em `local.properties`.
O `picovoice_access_key` também fica somente em `local.properties` ou no ambiente
`PICOVOICE_ACCESS_KEY`; áudio do microfone não é enviado à nuvem pela integração.
