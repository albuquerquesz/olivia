# SDD 01 — Freeze pré-hardware

Data: 17/09/2026  
Objetivo: fechar hoje 100% do que pode ser implementado, executado e demonstrado sem os Ray-Ban Meta.

Este SDD é operacional. Os módulos `00`–`10` desta pasta continuam sendo a fonte técnica de
contratos, código, tasks e evidências. Aqui a equipe executa o que já está disponível e deixa o
hardware de amanhã como troca de adapter/configuração, não como reescrita do produto.

## Resultado esperado no fim do dia

- APK `mock` demonstrável e APK `glasses` pronto para receber o dispositivo.
- Contrato 2.0.0 congelado; nenhuma mudança estrutural amanhã.
- DAT atrás de `GlassesGateway`, com MockDeviceKit e adapter físico usando o mesmo runtime.
- Preview realtime preservado, efêmero e limpo em pausa, `STOPPED` e fechamento.
- Porcupine/Rhino locais no celular; comandos de gravar, pausar, retomar e terminar cobertos.
- Loop de direção com sampler 0,5 fps, watchdog de 4 s, duas correções e recuperação.
- Firebase/App Check configurado no flavor `glasses`, com qualidade e acesso reportados separadamente.
- Privacidade, cleanup, logs sanitizados, opt-outs DAT e fallback de importação prontos.
- `RecordingSource`, alinhamento, `CutPlanner`, exportação e entrada do Whisper preparados para o
  arquivo nativo pós-`STOPPED`.
- Roteiro, evidências e backup do pitch prontos antes de encerrar o dia.

## Ordem de execução de hoje

| Ordem | Frente | Tasks | Saída de hoje |
|---:|---|---|---|
| 1 | SH-01 e baseline | SH-01, SH-02, SH-04 | Pedido oficial pronto para envio/registro; contratos e escopo congelados |
| 2 | Ambiente reproduzível | BE-13, BE-14, SH-03 | CI offline, Docker, Bun, APK e configuração sanitizada |
| 3 | DAT plugável | INT-00, BE-00, FE-02, FE-09 | `GlassesGateway`/MockDeviceKit, frame, heartbeat, pausa, degradação e recuperação |
| 4 | Voz e preview | FE-03, FE-04, FE-06 | Wake word/intents locais, áudio local e preview sem persistência |
| 5 | Pipeline offline | BE-01, BE-02, BE-06, BE-04, BE-05, INT-01 | Cinco takes determinísticos, duas correções, timeline e evidência |
| 6 | IA e qualidade | BE-03, INT-03 | Smoke Firebase, avaliação rotulada possível e relatório sem frames |
| 7 | Privacidade | BE-10 | Retenção/cleanup/opt-outs/logs revisados e testados |
| 8 | Pós-take | BE-08, BE-09, BE-12 | Whisper/fixture se disponível; importação manual, alinhamento e cortes sem inventar resultado |
| 9 | Freeze | INT-02, INT-08 | APK, backup, roteiro de amanhã e pitch congelados |

## O que não deve consumir tempo hoje

- Pareamento ou tentativa de validar Bluetooth real sem o dispositivo.
- Alegar A1–A5, gravação nativa 3K, áudio nos óculos ou bateria real.
- Alterar contrato, scheduler, preview, Porcupine/Rhino ou caminho híbrido para perseguir 900 ms.
- Criar login, CRUD, feed, editor de vídeo, upload de mídia ou áudio remoto.

## Critério de saída

O freeze está concluído quando o time consegue instalar uma build, executar o fluxo mock completo,
abrir o flavor `glasses`, apontar as credenciais locais e saber exatamente quais arquivos/configurações
serão trocados amanhã. Depois do freeze, só são permitidas correções de bloqueio de hardware e
ajustes de configuração.

Checklist detalhado: [EXECUTION_CHECKLIST.md](EXECUTION_CHECKLIST.md).  
Template de evidência: [EVIDENCE_TEMPLATE.md](EVIDENCE_TEMPLATE.md).
