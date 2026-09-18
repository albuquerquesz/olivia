# Tasks — Privacidade, segurança e descarte verificável

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-10; BE-13 configura a nuvem; FE-05 somente comunicação ao usuário. IDs com sufixo são passos da issue original, não novas issues.

- [x] **BE-10.1** — Produzir inventário de dados e ameaças no início da infra; fixar owners e locais de armazenamento. (Ver `docs/PRIVACY_RUNTIME_CONTROLS.md`.)
- [x] **BE-10.2** — Implementar logs por allowlist, liberação/limpeza em finally e recuperação de temporários após crash. (Timeline sem texto livre; imports e exports órfãos são limpos apenas em diretórios privados.)
- [ ] **BE-10.3** — Documentar e evidenciar tratamento pelo provedor e opt-outs DAT antes de live com dados reais.
- [ ] **BE-10.4** — Entregar unitários e integração Docker/Android de ausência de vazamento e limpeza em falhas.
- [x] **BE-10.5** — Fechar checklist ético de consentimento do ensaio, retenção local e compartilhamento temporário. (Ver `docs/PRIVACY_RUNTIME_CONTROLS.md`.)

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.
