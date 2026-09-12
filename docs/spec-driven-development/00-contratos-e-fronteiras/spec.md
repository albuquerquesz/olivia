# Contratos e fronteiras do domínio

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: SH-01, SH-02, SH-04.

Dependências: Nenhuma dependência técnica; ler as regras e o código :core antes de alterar contratos.

Escopo: Suporte necessário. Consolidar uma API Kotlin que separe SDK, inferência, direção, áudio, mídia e persistência local. SH-01 permanece pendência administrativa, não autorização presumida de pré-build.

## Comportamento e contratos

O módulo `:core` permanece Kotlin puro. SDK Meta, Firebase e Android ficam nos adapters. SessionCoordinator controla IDLE, STARTING, STARTED, PAUSED, DEGRADED, STOPPING, STOPPED, PROCESSING, READY e ERROR; captura DAT não inicia implicitamente o vídeo nativo.

Resolver ADR-06/07: captura anterior ao sampler, heartbeat independente da inferência e um Clock injetável monotônico. Epoch ancora eventos; elapsed/offset não mudam quando o relógio civil salta. Um sessionId/generation identifica resultados válidos. STOP e nova sessão invalidam resultados pendentes.

O contrato visual usa FrameAnalysis e VisualDiagnosis atuais (FRAMING/LIGHT/HORIZON, Severity, Direction, Confidence). Não criar issueType/action paralelos no domínio. A API externa é convertida no adapter. Correções recebem IDs; o evento de confirmação referencia a correção e um frame posterior.

Evoluir SessionSignal para pausa/retomada e indisponibilidade da análise, discriminando eventos de conexão. Revisar a semântica de samplingFps/analysisIntervalMs e do watchdog antes de BE-00/02. Versionar alterações incompatíveis; atualizar JSON, comentários e consumidores juntos.

## Testes e aceite

Unitários: transições, consistência da configuração, confidence inválida, clocks e comparação de versões. Integração JVM/Docker: implementar doubles de borda que conectem as interfaces reais, serializar/reler exemplos dourados de timeline/review, trocar mock por adapter de teste sem mudar consumidor. Cobrir STOPPED antes de PROCESSING e rejeitar resultado de sessionId anterior.

Pronto quando a matriz de transição, exemplos de mensagens e decisão de buffers/heartbeat não se contradizem entre Kotlin, JSON e specs. Não marcar SH-01 concluída sem resposta oficial.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.

