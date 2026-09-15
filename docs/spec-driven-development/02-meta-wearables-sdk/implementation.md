# Entrega parcial — configuração do Device Access Toolkit

Status: repositório privado e opt-outs por flavor escritos; adapter MockDeviceKit/DAT e execução de
testes pendentes.

Os artefatos DAT e Firebase ficam no flavor `glasses`. O repositório privado do DAT só é adicionado
quando `GITHUB_TOKEN` ou `github_token` em `local.properties` existe, portanto o build `mock` não
recebe nem depende da credencial. `src/glasses/AndroidManifest.xml` desabilita analytics e crash
reporting do DAT sem alterar o APK `mock`.

Ainda faltam o application ID do Wearables Developer Center, a resolução dos artefatos no ambiente
autorizado, a implementação MockDeviceKit/DAT atrás de `GlassesGateway` e evidência Android. Esta
configuração não comprova SDK, Bluetooth ou hardware.
