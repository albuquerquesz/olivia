# Tasks — Gravação nativa, importação e alinhamento de mídia

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-12; INT-06 consome o conector real. IDs com sufixo são passos da issue original, não novas issues.

- [ ] **BE-12.1** — Concluir matriz iniciada no módulo 02 e explicitar limites de start/stop, simultaneidade, queda e importação.
- [ ] **BE-12.2** — Implementar RecordingSource de fixture e adapter de importação local/real com validação e cancelamento.
- [ ] **BE-12.3** — Definir mapa sessão→mídia e tolerância; versionar contrato se necessário.
- [ ] **BE-12.4** — Entregar unitários e integração Docker/Android com vídeos, offsets e falhas reais de leitura.
- [ ] **INT-06.1** — Executar futuramente a mesma composição com take nativo e registrar fallback sem alegar equivalência.

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.

