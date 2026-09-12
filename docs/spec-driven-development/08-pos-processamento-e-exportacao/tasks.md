# Tasks — Transcrição local, cortes sugeridos e handoff

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-08, BE-09; FE-07/FE-08 como interfaces de revisão/exportação. IDs com sufixo são passos da issue original, não novas issues.

- [ ] **BE-08.1** — Fazer spike do Whisper local e fixar modelo/runtime; documentar instalação offline.
- [ ] **BE-08.2** — Implementar extração e transcrição somente pós-STOPPED com cancelamento e validação de segmentos.
- [ ] **BE-09.1** — Implementar CutPlanner determinístico em tempo de mídia, com semântica de exclusão e evidência da causa.
- [ ] **BE-09.2** — Entregar unitários e integração Docker completa com Whisper real; preservar timeline em falhas.
- [ ] **FE-08.1** — Definir artefatos e handoff suportado pelo editor; implementar permissões temporárias de compartilhamento.
- [ ] **BE-08.3** — Implementar integração Android de pós-processamento e preparar benchmark no aparelho alvo.

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.

