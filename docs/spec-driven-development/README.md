# Desenvolvimento por especificações — Olivia

Revisão de planejamento em 12/09/2026. Escopo: arquitetura, infraestrutura, segurança, domínio e integrações de backend/IA. As interfaces Android entram apenas quando são fronteiras necessárias do fluxo. Implementação iniciada: [SH-04 — contratos 2.0.0 e testes](00-contratos-e-fronteiras/implementation.md), com runner Docker parcial BE-14. Validação por execução pendente.

Comece pela [review](REVIEW.md), pelas [decisões](DECISIONS.md) e pela [política de testes](TESTING.md). O [backlog canônico](../rules/olivia_backlog.json) preserva os IDs existentes; cada task aponta para sua especificação. O código de contratos em `apps/android/core` continua sendo a fonte da API vigente. Mudanças propostas de contrato estão identificadas como pendentes, não como implementadas.

## Ordem de execução

Os números ordenam a entrega, não impõem espera quando uma task já tem suas dependências atendidas. Em especial, o spike BE-12 começa junto ao setup DAT, embora a implementação completa da importação seja o módulo 07. Segurança começa no módulo 01 e acompanha todos os demais.

| Ordem | Feature / especificação | Tasks principais | Saída necessária |
|---|---|---|---|
| 00 | [Contratos e fronteiras](00-contratos-e-fronteiras/spec.md) | SH-01, SH-02, SH-04 | Contratos, relógios e decisões sem ambiguidades |
| 01 | [Setup backend, IA e integração](01-setup-backend-e-infra/spec.md) | BE-13, BE-14; SH-03 como interface | Ambiente reproduzível, Docker de testes, Firebase protegido |
| 02 | [Setup Meta Wearables SDK](02-meta-wearables-sdk/spec.md) | INT-00; FE-00 como pré-requisito | SDK e MockDeviceKit; spike de capacidade nativa iniciado |
| 03 | [Captura e controle de sessão](03-captura-e-sessao/spec.md) | BE-00, FE-02, FE-03, FE-09 | Frames, comandos locais, pausa, degradação e retomada |
| 04 | [Fixtures, mock e sampler](04-fixtures-mock-e-sampler/spec.md) | BE-01, BE-02, BE-06 | Pipeline offline determinístico a 0,5 fps |
| 05 | [Classificação visual](05-classificacao-visual/spec.md) | BE-03 | Diagnóstico fechado, validado e testado com IA real |
| 06 | [Direção, áudio e timeline](06-direcao-audio-e-timeline/spec.md) | BE-04, BE-05, INT-01; FE-04, FE-06 | Correção, confirmação e primeiro ensaio mãos-livres |
| 07 | [Gravação nativa e importação](07-gravacao-nativa-e-importacao/spec.md) | BE-12 | Capacidade comprovada ou limitação registrada; tempo do vídeo alinhado |
| 08 | [Transcrição, cortes e handoff](08-pos-processamento-e-exportacao/spec.md) | BE-08, BE-09; FE-07, FE-08 | Pós-take local, marcas úteis e exportação verificável |
| 09 | [Privacidade e segurança](09-privacidade-e-seguranca/spec.md) | BE-10 | Provas de descarte e controles de acesso/abuso |
| 10 | [Medição e evidências](10-medicao-e-evidencias/spec.md) | BE-07, BE-11, INT-02 a INT-08 | Resultados rastreáveis aos critérios e checkpoints |

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

As datas originais M0–M4 permanecem como referência histórica. A sequência acima e as dependências revisadas orientam a execução a partir de agora; o plano não pressupõe que os marcos passados foram concluídos. As estimativas originais foram preservadas; BE-13/14 receberam estimativa inicial de 3 cada, na mesma unidade do backlog, a refinar antes de comprometer prazo. Capacidade disponível e SH-01 determinam o que pode ser implementado antes do evento. Não foi enviado contato externo à organização.

O primeiro gate é o caminho offline de 30–60 segundos com voz, duas correções confirmadas e recuperação. Em seguida, provar a mesma composição com Gemini real. A prova de hardware exige câmera DAT e áudio nos óculos, mais verificação independente da gravação nativa. Pós-processamento e exportação têm gate próprio. A demo mock permanece backup identificado; não substitui uso efetivo do toolkit/hardware.

Diagramas gerais: [arquitetura](architecture.mmd) e [dependências](execution-order.mmd). Cada feature também possui um diagrama simples de seus componentes.
