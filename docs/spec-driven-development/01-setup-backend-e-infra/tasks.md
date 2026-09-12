# Tasks — Setup do backend, nuvem e harness de integração

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-13, BE-14; SH-03 como dependência compartilhada. IDs com sufixo são passos da issue original, não novas issues.

- [ ] **BE-13.1** — Inventariar scaffold e publicar a decisão de que auth/DB/API legados não são runtime do MVP.
- [ ] **BE-13.2** — Criar exemplos de configuração, carregadores por allowlist e orientação de secrets dev/CI/Android.
- [ ] **BE-13.3** — Preparar setup Firebase/App Check/quotas e comprovar acesso no projeto dev quando configurado.
- [ ] **BE-14.1** — Implementar infra/testing/compose.yaml com perfis offline, Whisper e legado opcional; nenhum volume de desenvolvimento.
- [ ] **BE-14.2** — Implementar entrypoints, stubs, relógio de teste e reports sanitizados; documentar requisitos Android separados.
- [ ] **BE-14.3** — Entregar testes unitários e integração do próprio harness: startup, erro, indisponibilidade e limpeza.

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.

