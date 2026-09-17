# Tasks — Contratos e fronteiras do domínio

Origem: [backlog canônico](../../rules/olivia_backlog.json). SH-01, SH-02, SH-04. IDs com sufixo são passos da issue original, não novas issues.

- [ ] **SH-01.1** — Registrar resposta oficial sobre pré-build quando disponível; não assumir que can_do_before_hackathon é uma aprovação.
- [x] **SH-02.1** — Fixar baseline de runtime local, IA visual via Firebase e ausência de contas/CRUD; manter riscos nativos explícitos. (Baseline registrada em `spec.md`, `DECISIONS.md` e `IMPLEMENTATION_BLOCKERS.md`; riscos DAT/mídia seguem BE-12.)
- [x] **SH-04.1** — Documentar transições, causas de DEGRADED, clock e limite de correlação sessão/mídia em implementation.md; mapa físico permanece BE-12.
- [x] **SH-04.2** — Implementar contratos 2.0.0 de captura/sampling, sinais e codec; sincronizar Kotlin, JSON e fixtures versionadas.
- [x] **SH-04.3a** — Escrever unitários e integração de contratos com runner Docker parcial BE-14.
- [x] **SH-04.3b** — Executar compilação, unitários e integração quando solicitados; registrar resultados e fechar aceite. Execução JVM registrada em `evidence/VALIDATION_RUN_2026-09-17.md`; Docker/Bun do harness permanecem separados em BE-14.

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.
