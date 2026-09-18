# Tasks - Transcricao local, cortes sugeridos e handoff

Origem: backlog canonico. BE-08, BE-09; FE-07/FE-08 como interfaces de revisao/exportacao.
IDs com sufixo sao passos da issue original, nao novas issues.

- [x] **BE-08.1** - Fazer spike do Whisper local e fixar modelo/runtime; documentar instalacao offline. Modelo `ggml-base.bin`, runtime `whisper.cpp v1.7.6`, hashes e runner estao em `docs/spec-driven-development/evidence/BE-08_WHISPER_DOCKER_2026-09-17.md`.
- [ ] **BE-08.2** - Implementar extracao e transcricao somente pos-STOPPED no Android com cancelamento e validacao de segmentos.
- [x] **BE-09.1** - Implementar CutPlanner deterministico em tempo de midia, com semantica de exclusao e evidencia da causa.
- [ ] **BE-09.2** - Entregar unitarios e integracao Docker completa com Whisper real; preservar timeline em falhas.
- [x] **FE-08.1** - Definir artefatos e handoff suportado pelo editor; implementar permissoes temporarias de compartilhamento.
- [x] **BE-08.3** - Implementar integracao Android de pos-processamento e preparar benchmark no aparelho alvo. O estado percorre `STOPPED -> PROCESSING -> READY`; benchmark e Whisper real continuam pendentes do runtime Android.

Aceite: entregar implementacao e testes unitarios/de integracao juntos, usando os cenarios da
spec e de TESTING. Registrar artefatos e resultado por modo. Execucao nao solicitada ou ambiente
ausente deixa validacao pendente; nunca marcar integracao real concluida por teste mock.
