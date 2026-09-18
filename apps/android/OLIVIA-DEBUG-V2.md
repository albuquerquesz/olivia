# Olivia Debug V2

`glassesDebugV2` é uma variante física separada da Olivia atual para comparar o vídeo no mesmo
hardware sem substituir a instalação da demo.

- application ID: `com.olivia.companion.debugv2`
- DAT: `MWDAT_USE_MOCK_DEVICE_KIT=false`
- mesmo código e configuração do flavor `glasses`
- arquivo: `app-glasses-debugV2.apk`

Build:

```sh
cd apps/android
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
  ./with-jdk.sh ./gradlew :app:assembleGlassesDebugV2 --console=plain
```

Este APK é gerado, mas não é instalado automaticamente em nenhum aparelho. Para instalar lado a
lado com a demo, use o Samsung ou outro aparelho de teste:

```sh
adb -s <device> install -r app/build/outputs/apk/glasses/debugV2/app-glasses-debugV2.apk
adb -s <device> shell am start -n com.olivia.companion.debugv2/.MainActivity
```
