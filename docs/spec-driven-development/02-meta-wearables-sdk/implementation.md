# Entrega — configuração e adapter do Device Access Toolkit

Status: resolução Maven Central, placeholders locais por flavor, opt-outs e adapter
MockDeviceKit/DAT implementados; integração Android MockDeviceKit validada no Moto G60.

Os artefatos DAT e Firebase ficam no flavor `glasses`. O DAT é resolvido pelo Maven Central; o
build `mock` não recebe nem depende do SDK. `src/glasses/AndroidManifest.xml` injeta Application
ID e client token somente de `local.properties`/CI (ou `0/0` em Developer Mode), além de desabilitar
analytics e crash reporting sem alterar o APK `mock`.

`DatGlassesGateway` normaliza frames raw I420 para NV21 e frames HEVC para o mesmo contrato de
domínio, sem armazenar bytes depois da emissão. O ciclo de pausa mantém o stream aberto (o DAT
fecha streams parados), cancela apenas o collector e reanexa na retomada; encerramento terminal
fecha stream, câmera, sessão e serviço.

`DatMockDeviceKitIntegrationTest` comprova inicialização, frame portrait, queda por 4 s,
`DEGRADED`, recuperação, pausa/retomada e `STOPPED` no dispositivo conectado. Esta evidência
fecha o modo toolkit, mas não comprova Bluetooth, firmware, alto-falantes ou vídeo 3K físico.
