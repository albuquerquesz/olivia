# DAT / MockDeviceKit — evidência controlada

O adapter vive exclusivamente no flavor `glasses`. Ele inicializa `Wearables`, inicia a sessão e
entrega ao `:core` um frame NV21 efêmero. No MockDeviceKit, a câmera do telefone fornece I420 raw
e o gateway faz a conversão; no caminho físico, a câmera DAT comprimida em HEVC é decodificada
localmente. Bytes HEVC, frames e mídia não são gravados nem enviados.

O teste Android `DatMockDeviceKitIntegrationTest` cobre o contrato de frame portrait (`480×640`
no feed do telefone), estados `STARTED → PAUSED → STARTED`, watchdog de quatro segundos e
`DEGRADED → STARTED`. No processo de instrumentação o foreground service é isolado: Android 16
encerra um FGS iniciado por esse processo antes do callback; o APK de produção continua
iniciando `DatStreamingService`.

```powershell
& .\gradlew.bat ':app:connectedGlassesDebugAndroidTest' `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.olivia.companion.session.DatMockDeviceKitIntegrationTest' `
  '-Pandroid.testInstrumentationRunnerArguments.datMockVideoUri=file:///sdcard/Download/olivia-dat-mock.mp4'
```

Resultado em 16/09/2026: teste executado no Moto G60 (`moto g(60) - 12`, ADB `0072882177`),
`1 test, 1 passed` em 6,712 s. O ciclo confirmou frame raw convertido para NV21, queda após 4 s,
`DEGRADED`, recuperação, pausa/retomada e `STOPPED`. Isso ainda não prova Bluetooth, hardware,
bateria, latência, qualidade da câmera dos óculos ou gravação nativa.
