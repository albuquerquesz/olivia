# DAT / MockDeviceKit — execucao local

O adapter DAT esta isolado no flavor `glasses`; o flavor `mock` continua sendo a demonstracao
offline deterministica e nao carrega o SDK. O adapter usa `mwdat-core`, `mwdat-camera` e
`mwdat-mockdevice` apenas — Olivia nao usa `mwdat-display`.

## Habilitar o simulador DAT

Em `apps/android/local.properties`, mantenha suas credenciais DAT locais e acrescente:

```properties
mwdat_mock_device_kit=true
```

Em seguida, no PowerShell em `apps/android`:

```powershell
& .\gradlew.bat ':app:installGlassesDebug'
```

Abra o app `Olivia` (sem o sufixo `.mock`) no emulador ou celular e inicie um take. O bootstrap
local cria Ray-Ban Meta simulados, liga/desdobra/veste o dispositivo e usa a camera traseira do
telefone como feed. Remova a flag e reinstale para voltar ao caminho de oculos pareados reais.

## O que observar

- A sessao deve sair de `STARTING` para `STARTED` quando o primeiro frame chegar.
- Pausar nao deve encerrar a sessao DAT; retomar abre outra geracao de captura.
- Quatro segundos sem frame devem levar a `DEGRADED`.
- A analise recebe somente frame efemero: nenhum frame ou video vai para timeline, DataStore,
  Firestore ou armazenamento em nuvem.

O stream pede HIGH (720×1280) e 2 fps; o `FrameSampler` mantem a analise em 0,5 fps. Buffers
comprimidos/de configuracao, ou que nao tenham RGBA completo, sao descartados em vez de rotulados
como imagem invalida. Portanto, o MockDeviceKit valida a integracao e a troca de adapter, mas nao
valida Bluetooth, hardware, consumo, latencia, qualidade de camera ou gravacao nativa.
