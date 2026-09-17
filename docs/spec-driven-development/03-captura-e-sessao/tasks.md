# Tasks — Captura de câmera e controle local por microfone

Origem: [backlog canônico](../../rules/olivia_backlog.json). BE-00, FE-02, FE-03, FE-09; FE-01 somente consumidor de estado. IDs com sufixo são passos da issue original, não novas issues.

- [x] **FE-02.1** — Implementar coordenador e transições/cancelamento sobre :core atualizado.
- [ ] **BE-00.1** — Implementar adapter DAT, formato/orientação, buffers e disponibilidade independente da IA.
- [x] **FE-03.1** — Integrar wake word e comandos pt-BR locais com lifecycle/permissões e retorno de áudio.
- [x] **FE-09.1** — Implementar watchdog e sinais de perda/recuperação, preservando pausa e timeline.
- [ ] **BE-00.2** — Entregar unitários de borda e integração fonte→sessão→timeline no Docker e Android/MockDeviceKit.
- [ ] **FE-03.2** — Planejar e registrar ensaio físico de comandos e evidência de ausência de envio de áudio.

Aceite: entregar implementação e testes unitários/de integração juntos, usando os cenários da [spec](spec.md) e de [TESTING](../TESTING.md). Registrar artefatos e resultado por modo. Execução não solicitada ou ambiente ausente deixa validação pendente; nunca marcar integração real concluída por teste mock.
