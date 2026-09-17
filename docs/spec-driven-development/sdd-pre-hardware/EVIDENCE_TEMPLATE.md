# Evidencia do freeze pre-hardware

Commit/build: `eb06989` (baseline de codigo `b39b6f1`)<br>
APK mock SHA-256: `44b5ebde1c903f95299d4e4dd4e89397292fe1f9783309f56a0faf747bbef1e1`<br>
APK glasses SHA-256: `55434983f89ef1df04e8c9e08f3946f703581aaf5a176887e088f65ce242d422`<br>
Data do freeze: `2026-09-17`

## Resultado executado

| Area | Resultado | Evidencia | Limitacao |
|---|---|---|---|
| Contratos/estados | `PASS_OFFLINE` | testes core/Android; cadeia de pos-take exige `STOPPED` | hardware nao envolvido |
| DAT MockDeviceKit | `PASS_MOCK` | `testMockDebugUnitTest`, `testGlassesDebugUnitTest`, `connectedMockDebugAndroidTest` | nao prova Bluetooth fisico |
| Preview | `PASS_CODE_PATH` | `SessionRuntime`/StateFlow e testes existentes; limpeza em pausa/stop/fechamento | nao e video final 3K |
| Voz local | `PASS_CODE_PATH` | Porcupine/Rhino e comandos locais; checklist de microfone pronto | microfone dos oculos nao e usado |
| IA Firebase | `PASS_SCHEMA_ACCESS` | smoke existente no flavor `glasses` | qualidade visual e latencia continuam separadas |
| Privacidade | `PASS_STATIC` | frame efemero, logs sem midia, arquivos sensiveis nao rastreados | retencao do provider externo nao foi medida |
| Whisper | `PASS_DOCKER` | modelo/fixture externos ao Git; runner offline passou com hash/schema/timestamps | runtime Whisper dentro do APK e desempenho Android pendentes |
| Pos-take | `PASS_FALLBACK` | importacao `content://`, cleanup `.part` e export marks-only | A1-A5, 3K, Bluetooth e audio fisicos pendentes |
| Backup/pitch | `PARTIAL` | APKs em dois slots locais; runbook de 18/09 aberto | segundo notebook/dispositivo ainda pendente |

## Comandos executados

- `assembleMockDebug assembleGlassesDebug`
- `testMockDebugUnitTest testGlassesDebugUnitTest connectedMockDebugAndroidTest`
- `docker compose --profile whisper ... run whisper`

O runner Docker registrou somente `suite/status`; nenhum transcript, frame, audio bruto, URI
privada ou credencial foi copiado para esta evidencia. O modelo `ggml-base.bin` e o fixture
sintetico ficam em `G:\olivia-local\whisper`, fora do repositorio.

## Gates ainda abertos

- resposta oficial de SH-01 e link de execucao do workflow GitHub;
- campanha de qualidade visual rotulada, se houver dataset autorizado e sem PII;
- validacao fisica A1-A5: DAT/Ray-Ban, Bluetooth, gravacao nativa 3K, audio e bateria;
- Whisper real dentro do APK Android e medicao de pos-processamento no dispositivo;
- copia para o segundo notebook/dispositivo.
