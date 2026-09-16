# Entrega parcial — configuração do Device Access Toolkit

Status: resolução Maven Central, placeholders locais por flavor e opt-outs escritos; adapter
MockDeviceKit/DAT e execução de testes pendentes.

Os artefatos DAT e Firebase ficam no flavor `glasses`. O DAT é resolvido pelo Maven Central; o
build `mock` não recebe nem depende do SDK. `src/glasses/AndroidManifest.xml` injeta Application
ID e client token somente de `local.properties`/CI (ou `0/0` em Developer Mode), além de desabilitar
analytics e crash reporting sem alterar o APK `mock`.

Ainda faltam credenciais válidas da integração registrada quando ela for usada, a implementação
MockDeviceKit/DAT atrás de `GlassesGateway` e evidência Android. Esta configuração não comprova SDK,
Bluetooth ou hardware.
