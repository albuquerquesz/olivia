# Tasks — Fixtures, analisador mock e amostragem

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-01, BE-02, BE-06. IDs com sufixo são passos da issue original, não novas issues.

- [x] **BE-01.1** — Criar manifesto rotulado e fixtures sintéticas/consentidas positivas, negativas e ambíguas.
- [x] **BE-06.1** — Implementar replay determinístico com relógio virtual e falhas configuráveis.
- [x] **BE-02.1** — Implementar cadência, single-flight, substituição de frame pendente e descarte em todos os caminhos.
- [ ] **BE-02.2** — Integrar fonte→sampler→analisador no runner Docker, incluindo fonte rápida e inferência lenta.
- [ ] **BE-01.2** — Entregar testes unitários dos oráculos/fixtures e relatório de proveniência sem dados pessoais.

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.
