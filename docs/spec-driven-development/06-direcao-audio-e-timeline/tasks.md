# Tasks — Motor de direção, áudio e confirmação na timeline

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-04, BE-05, INT-01; FE-04/FE-06 como fronteiras de áudio e consumo. IDs com sufixo são passos da issue original, não novas issues.

- [x] **BE-04.1** — Implementar política determinística de threshold/cooldown/prioridade, histerese de duas análises concordantes e catálogo de instruções.
- [x] **BE-05.1** — Implementar correlação, pendência, confirmação, expiração e gravação idempotente local.
- [x] **FE-04.1** — Integrar TTS/WAV e sinais de sessão atualizados com resultado de emissão. (TTS pt-BR e tom PCM estéreo efêmero de 80 ms estão ligados ao runtime; o teste Android exercita o AudioTrack. Audibilidade/rota Bluetooth nos óculos permanece em INT-05.)
- [x] **BE-05.2** — Entregar unitários e integração Docker de direção/tracker/repositório, com falha de gravação e reinício. (JUnit cobre reinício, arquivo temporário interrompido e corrupção fail-closed; o perfil Docker `core-contracts` executa a mesma task de integração sem rede.)
- [ ] **INT-01.1** — Conectar FE-03, FE-06 e áudio e preparar roteiro offline de cinco takes sem toque. (Roteiro determinístico de cinco ciclos passou em `OfflineHandsFreeGateTest`; falta executar o `LiveSessionRuntime` com a rota de áudio/microfone real.)
- [ ] **INT-01.2** — Registrar separadamente ensaio com headset, integração IA real e hardware, sem misturar evidências. (A separação está documentada; ensaio físico continua pendente.)

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.
