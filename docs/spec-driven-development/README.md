# Desenvolvimento por especificações — Olivia

Revisão de planejamento em 12/09/2026. O resumo executivo do freeze pré-hardware de 17/09 e do hackathon de 18/09 está em [PRE_HARDWARE_FREEZE_2026-09-17](PRE_HARDWARE_FREEZE_2026-09-17.md); a execução operacional foi separada nos dois SDDs abaixo. A execução de validação está registrada em [17/09/2026](evidence/VALIDATION_RUN_2026-09-17.md). A meta de 900 ms continua por último; hoje o objetivo é fechar tudo que é plugável e verificável sem os óculos.

## Dois SDDs operacionais

- [SDD 01 — Freeze pré-hardware](sdd-pre-hardware/README.md): execução de hoje, APK, adapters,
  voz, preview, IA, privacidade, pós-take, evidências e backup.
- [SDD 02 — Dia do hackathon](sdd-hackathon-day/README.md): execução cronológica de 08:00 a 19:00,
  com integração dos óculos encerrada às 16:00 e três horas de folga para pitch/contingência.

Os módulos técnicos abaixo continuam compartilhados pelos dois SDDs; não duplicar contratos nem
criar um segundo domínio.

Comece pela [review](REVIEW.md), pelas [decisões](DECISIONS.md) e pela [política de testes](TESTING.md). O [backlog canônico](../rules/olivia_backlog.json) preserva os IDs existentes; cada task aponta para sua especificação. O código de contratos em `apps/android/core` continua sendo a fonte da API vigente. Mudanças propostas de contrato estão identificadas como pendentes, não como implementadas.

## Ordem de execução

O plano tem dois horizontes: **freeze pré-hardware hoje** e **integração H0 amanhã**. Não bloquear o
freeze por A1–A5, áudio físico ou bateria; deixar esses testes como troca de adapter/configuração no
primeiro bloco do hackathon.

Os números ordenam a entrega, não impõem espera quando uma task já tem suas dependências atendidas. Em especial, o spike BE-12 começa junto ao setup DAT, embora a implementação completa da importação seja o módulo 07. Segurança começa no módulo 01 e acompanha todos os demais.

| Ordem | Feature / especificação | Tasks principais | Saída necessária |
|---|---|---|---|
| 00 | [Contratos e fronteiras](00-contratos-e-fronteiras/spec.md) | SH-01, SH-02, SH-04 | Contratos congelados; SH-01 em resposta oficial |
| 01 | [Setup backend, IA e integração](01-setup-backend-e-infra/spec.md) | BE-13, BE-14; SH-03 como interface | Ambiente reproduzível, Docker de testes, Firebase protegido |
| 02 | [Setup Meta Wearables SDK](02-meta-wearables-sdk/spec.md) | INT-00; FE-00 como pré-requisito | SDK e MockDeviceKit; spike de capacidade nativa iniciado |
| 03 | [Captura e controle de sessão](03-captura-e-sessao/spec.md) | BE-00, FE-02, FE-03, FE-09 | Frames, comandos locais, pausa, degradação e retomada |
| 04 | [Fixtures, mock e sampler](04-fixtures-mock-e-sampler/spec.md) | BE-01, BE-02, BE-06 | Pipeline offline determinístico a 0,5 fps |
| 05 | [Classificação visual](05-classificacao-visual/spec.md) | BE-03 | Diagnóstico fechado, validado e testado com IA real |
| 06 | [Direção, áudio e timeline](06-direcao-audio-e-timeline/spec.md) | BE-04, BE-05, INT-01; FE-04, FE-06 | Correção, confirmação e primeiro ensaio mãos-livres |
| 07 | [Gravação nativa e importação](07-gravacao-nativa-e-importacao/spec.md) | BE-12 | Capacidade comprovada ou limitação registrada; tempo do vídeo alinhado |
| 08 | [Transcrição, cortes e handoff](08-pos-processamento-e-exportacao/spec.md) | BE-08, BE-09; FE-07, FE-08 | Pós-take local, marcas úteis e exportação verificável |
| 09 | [Privacidade e segurança](09-privacidade-e-seguranca/spec.md) | BE-10 | Provas de descarte e controles de acesso/abuso |
| 10 | [Medição e evidências](10-medicao-e-evidencias/spec.md) | BE-07, BE-11, INT-02 a INT-08 | Freeze sanitizado hoje; A1–A5/áudio/bateria amanhã |

Tasks FE-01 e FE-05 continuam sendo trabalho de UI, fora desta revisão; aparecem na matriz somente como consumidoras dos estados, modos e avisos. Nenhuma revisão de layout é necessária para executar este plano.

## Como um agente executa uma feature

1. Leia as regras relevantes em `../rules/`, a task canônica, `spec.md`, `tasks.md` e `diagram.mmd` da feature, além de `TESTING.md`.
2. Confira as dependências pelos artefatos entregues. Existência de interface, tela, data passada ou check box não prova comportamento funcionando.
3. Implemente a menor fatia vertical e seus testes unitários e de integração no mesmo trabalho. Não deixe integração para uma task futura de hardening.
4. Preserve interfaces públicas ou versione a mudança com fixtures de compatibilidade. Atualize o JSON de contrato na mesma entrega.
5. Registre arquivos alterados, cenários, resultado observado e limitações. Diferencie teste implementado, executado, falhou e não executado. Um teste requerido não executado deixa o aceite pendente.
6. Respeite AGENTS.md: executar build, lint, typecheck ou testes exige solicitação explícita do usuário. Este planejamento exige implementá-los; não é autorização permanente para executá-los.

Cada `tasks.md` traz subtasks locais no formato `BE-XX.n`. Elas refinam a task canônica; não são novas issues nem declarações de progresso. Checkboxes começam abertas mesmo quando já existe scaffold, porque seu aceite ainda precisa ser demonstrado.

## Cronologia e gates

As datas originais M0–M4 permanecem como referência histórica. O backlog também separa H0 como
integração presencial: INT-04/05/06/07 não são bloqueios para o freeze de hoje. O pedido de SH-01
está preparado em [evidence/SH-01_PREBUILD_OFFICIAL_REQUEST](evidence/SH-01_PREBUILD_OFFICIAL_REQUEST.md),
mas ainda precisa ser enviado e respondido pela organização.

O primeiro gate de hoje é o caminho offline de 30–60 segundos com voz, duas correções confirmadas e
recuperação. O segundo é deixar o flavor `glasses`, a fronteira DAT, o preview, o áudio e o pós-take
prontos para receber o hardware. Amanhã a prioridade é câmera/preview, voz/áudio, vídeo nativo,
queda Bluetooth, importação e checkpoints. A latência de 900 ms só será reavaliada depois desses
gates, sem alterar agora a cadência de 0,5 fps, o preview, Porcupine/Rhino ou o caminho híbrido.

Diagramas gerais: [arquitetura](architecture.mmd) e [dependências](execution-order.mmd). Cada feature também possui um diagrama simples de seus componentes.
