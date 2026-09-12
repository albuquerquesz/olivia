# Integração offline do core — primeira fatia de BE-14

O runner reutiliza `apps/android/core` e o version catalog real, com um settings Gradle separado para não resolver AGP, SDK Android ou pacotes DAT. Inclui políticas de contratos, clocks, fence de captura e codec JSON com fixtures douradas. Os adapters de borda do ensaio são doubles identificados; não há teste de decoder de imagens ou SDK real nesta fatia.

Implementado, ainda não executado. Seguindo AGENTS.md, os comandos abaixo só devem ser executados quando solicitados. Não requer `.env`, credenciais, banco ou óculos.

## Docker

Na raiz do repositório, preparar a imagem (requer internet para dependências):

```sh
docker compose -f infra/testing/compose.yaml build core-contracts
```

Executar ambas as camadas com rede desabilitada:

```sh
docker compose -f infra/testing/compose.yaml run --rm core-contracts
```

Somente integração:

```sh
docker compose -f infra/testing/compose.yaml run --rm core-contracts :core:integrationTest --rerun-tasks
```

O código de saída é o resultado do Gradle, sem mascarar falhas. `--rm` remove só o container dessa execução; nenhum volume externo é criado ou apagado. Relatórios JUnit/HTML ficam no filesystem descartável em `apps/android/core/build/test-results` e `build/reports/tests`. Para preservá-los em uma execução de diagnóstico, usar `run --name <nome-unico>` sem `--rm`, copiar os relatórios desse container e remover apenas esse container depois. Sanitizar relatórios antes de anexar evidência. Não há resultados reais para arquivar nesta entrega.

## Sem Docker

JDK 17+ e wrapper existente, sem Android SDK. Na raiz, PowerShell:

```powershell
.\apps\android\gradlew.bat -p infra/testing :core:test :core:integrationTest
```

Testes unitários e integração têm tasks/relatórios separados. `check` depende de ambas. `prepareOfflineTests` resolve classpath e compila testes, sem executá-los. A imagem usa esse passo antes de desabilitar a rede na execução; não há downgrade para mock de um teste live.

Ainda pendentes em BE-14: stub HTTP para BE-03, perfil Whisper, relatórios persistentes de CI, Android/MockDeviceKit e Firebase real. Docker deste módulo não comprova essas integrações. A imagem usa tag de versão explícita; pin por digest e auditoria de dependências ficam pendentes da primeira preparação autorizada.
