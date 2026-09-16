# Tasks — Classes de classificação de enquadramento, luz e horizonte

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-03. IDs com sufixo são passos da issue original, não novas issues.

- [x] **BE-03.1** — Definir schema externo fechado e mapeamento ao FrameAnalysis/VisualDiagnosis existente. **Substituído pelo refactor ROI abaixo; não fecha o novo aceite.**
- [x] **BE-03.2** — Implementar adapter Firebase, validador, timeout, cancelamento e tratamento de indisponibilidade. **O adapter atual é a base de transporte, mas ainda pede diagnósticos completos.**
- [x] **BE-03.3** — Escrever unitários e integração Docker com falhas de transporte e respostas malformadas. (O core JVM cobre schema, transporte e timeout; o harness offline executa as integrações sem rede.) **A cobertura de ROI/visão local permanece pendente.**
- [x] **BE-03.6** — Migrar o contrato para `WorkAreaLocalization` normalizado; rejeitar direção, magnitude, severidade e texto do provedor.
- [x] **BE-03.7** — Implementar visão local determinística: offsets de framing, luminância na ROI e horizonte com abstenção por baixa confiança.
- [x] **BE-03.8** — Cobrir fixtures e integração `frame → ROI → análise local → guidance`, incluindo limites 10%/25%, ROI inválida e descarte. (JVM; qualidade/stream real segue BE-03.4/03.5 e INT-00.)
- [ ] **BE-03.4** — Implementar suíte Android live com .env/config dev; falhar claramente sem credenciais quando selecionada.
- [ ] **BE-03.5** — Preparar avaliação rotulada de qualidade/direção; registrar gate pendente até execução real.

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.
