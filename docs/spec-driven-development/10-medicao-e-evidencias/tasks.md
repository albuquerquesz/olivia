# Tasks — Latência, bateria, integração e evidências de avaliação

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-07, BE-11, INT-02, INT-03, INT-04, INT-05, INT-06, INT-07, INT-08. IDs com sufixo são passos da issue original, não novas issues.

- [x] **BE-07.1** — Implementar spans monotônicos e métricas por chamada, diferenciando pedido de áudio de reprodução. (Agregado em memória: p50/p95 de inferência e frame→callback, contadores de sampling; exposto pelo runtime sem mídia, texto ou IDs.)
- [x] **BE-11.1** — Implementar contadores e tuning com defaults 0,5 fps/4 s; documentar método de energia. (`SessionConfig` é a fonte única dos defaults e `LiveTrialReporter` produz relatório agregado com baterias somente quando observadas; não estima consumo.)
- [ ] **BE-07.2** — Entregar unitários de métricas e integração Docker sem logs de conteúdo. (Unitários de quantis/ausência de áudio cobrem o núcleo; falta a integração Docker. A medição física permanece INT-07.)
- [ ] **INT-02.1** — Preparar/implementar roteiro automatizado de queda, retomada, pausa e timeout IA.
- [ ] **INT-03.1** — Montar registro de evidências para cinco checkpoints e quatro critérios; físico permanece pendente até medição.
- [ ] **INT-04.1** — Validar captura/sessão DAT real; confrontar matriz de capacidade BE-12.
- [ ] **INT-05.1** — Validar voz, tons e distinção de eventos/lados no hardware.
- [ ] **INT-06.1** — Validar vídeo nativo, transcrição, alinhamento e handoff real.
- [ ] **INT-07.1** — Medir latência e energia e registrar amostra/condições, sem extrapolar. (Latência Gemini+TTS no Moto foi medida; bateria, DAT e saída física nos óculos continuam pendentes.)
- [ ] **INT-08.1** — Preparar ensaio de impacto consentido, roteiro estável e backup identificado.

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.
