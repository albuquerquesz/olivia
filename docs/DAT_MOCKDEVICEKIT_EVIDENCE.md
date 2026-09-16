# DAT / MockDeviceKit — evidência controlada

O adapter vive exclusivamente no flavor `glasses`. Ele inicializa `Wearables`, inicia a sessão,
abre a câmera DAT comprimida em HEVC e só entrega ao `:core` um frame NV21 decodificado localmente.
Bytes HEVC, frames e mídia não são gravados nem enviados.

O teste Android `DatMockDeviceKitIntegrationTest` cobre o contrato de frame, estados
`STARTED → PAUSED → STARTED`, watchdog de quatro segundos e `DEGRADED → STARTED`. No processo de
instrumentação o foreground service é isolado: Android 16 encerra um FGS iniciado por esse processo
antes do callback; o APK de produção continua iniciando `DatStreamingService`.

```powershell
& .\gradlew.bat ':app:connectedGlassesDebugAndroidTest' `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.olivia.companion.session.DatMockDeviceKitIntegrationTest' `
  '-Pandroid.testInstrumentationRunnerArguments.datMockVideoUri=file:///sdcard/Download/olivia-dat-mock.mp4'
```

Resultado em 16/09/2026: compilação do flavor e do teste aprovada. No AVD `Olivia_API_36`, a sessão
MockDeviceKit ainda permanece em `STARTING` e expira antes de `STARTED`; portanto não existe, ainda,
evidência de frame, resolução/orientação real ou recuperação do stream. Isso também não prova
Bluetooth, hardware, bateria, latência, qualidade da câmera ou gravação nativa.
