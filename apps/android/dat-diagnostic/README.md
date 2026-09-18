# DAT Camera Diagnostic

APK independente para testar somente a câmera do Meta Wearables DAT. O módulo não depende de
`:app`, `:core`, Firebase, App Check, Gemini, Porcupine, ROI, TTS ou timeline.

## Build e instalação

```sh
cd apps/android
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
  ./with-jdk.sh ./gradlew :dat-diagnostic:assembleDebug --console=plain
adb -s <device> install -r dat-diagnostic/build/outputs/apk/debug/dat-diagnostic-debug.apk
adb -s <device> shell am start -n com.olivia.datdiagnostic/.DiagnosticActivity
```

O app pede `MEDIUM/24`, `compressVideo=false`, coleta o raw I420 entregue pelo SDK, converte uma
vez para NV21 e renderiza o frame mais recente na GPU. A tela mostra estado, FPS, dimensão, gap de
chegada e gap de PTS. Logs contêm somente essas métricas agregadas; nenhum frame é persistido.

O registro e a permissão DAT são tratados pelo próprio app. Se o DAT não estiver registrado, o
botão abre o fluxo de registro no Meta AI; depois é necessário retornar ao diagnóstico e iniciar o
stream novamente.
