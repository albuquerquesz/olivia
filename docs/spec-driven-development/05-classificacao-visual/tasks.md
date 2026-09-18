# Tasks — Classes de classificação de enquadramento, luz e horizonte

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-03. IDs com sufixo são passos da issue original, não novas issues.

- [x] **BE-03.1** — Definir schema externo fechado e mapeamento ao FrameAnalysis/VisualDiagnosis existente. **O contrato foi evoluído para `WorkAreaLocalization` 2.0; diagnósticos são locais.**
- [x] **BE-03.2** — Implementar adapter Firebase, validador, timeout, cancelamento e tratamento de indisponibilidade. **O adapter agora pede somente ROI/confiança; a visão local fica fora do provedor.**
- [x] **BE-03.3** — Escrever unitários e integração Docker com falhas de transporte e respostas malformadas. (O core JVM cobre schema, transporte e timeout; o harness offline executa as integrações sem rede.) **Cobertura de ROI/visão local foi adicionada em BE-03.8.**
- [x] **BE-03.6** — Migrar o contrato para `WorkAreaLocalization` normalizado; rejeitar direção, magnitude, severidade e texto do provedor.
- [x] **BE-03.7** — Implementar visão local determinística: offsets de framing, luminância na ROI e horizonte com abstenção por baixa confiança.
- [x] **BE-03.8** — Cobrir fixtures e integração `frame → ROI → análise local → guidance`, incluindo limites 10%/25%, ROI inválida e descarte. (JVM; qualidade/stream real segue BE-03.4/03.5 e INT-00.)
- [x] **BE-03.4** — Implementar suíte Android live com .env/config dev; falhar claramente sem credenciais quando selecionada. (`FirebaseRoiIntegrationTest` passou em 16/09/2026 no AVD Olivia_API_36 com Firebase/App Check, schema nativo e JPEG sintético efêmero; não é avaliação de qualidade.)
- [ ] **BE-03.5** — Preparar avaliação rotulada de qualidade/direção; registrar gate pendente até execução real.

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.
