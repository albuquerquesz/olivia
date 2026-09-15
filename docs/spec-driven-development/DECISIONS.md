# Decisões e pendências de arquitetura

Estas decisões orientam a implementação. A API Kotlin evoluiu para 2.0.0 na primeira entrega SH-04; compilação/testes ainda não executados. Ver [migração e limites](00-contratos-e-fronteiras/implementation.md). As seções de resolução abaixo registram o desenho original, implementado nos contratos; efeitos de runtime e mídia permanecem nas tasks responsáveis.

| ID | Decisão | Estado / responsável |
|---|---|---|
| ADR-01 | Domínio, sessão, sampler, direção, timeline e pós-processamento no Android. `Backend/IA` é responsabilidade da equipe, não sinônimo de servidor HTTP | Baseline preservada; SH-04 |
| ADR-02 | Firebase AI Logic é o caminho de inferência visual. Elysia/Better Auth/Postgres existentes não são dependências de runtime do MVP; não criar `/classify`, contas ou CRUD de sessões por conveniência | Planejado; BE-13 |
| ADR-03 | Docker serve ao desenvolvimento e integração. Runner JVM e stub HTTP exercitam domínio/transportes reais; SDK DAT, Firebase Android e áudio exigem camada Android adicional | Planejado; BE-14 |
| ADR-04 | Whisper executa localmente no celular após STOPPED. Baseline: `whisper.cpp` + `ggml-base.bin` multilíngue; `ggml-tiny.bin` multilíngue é fallback condicionado a benchmark. Serviço remoto de áudio exigiria decisão explícita de dados e revisão do submit | Decidido, provisionamento e benchmark pendentes; BE-08 |
| ADR-05 | Sessão DAT de captura não equivale a gravação nativa. Comandos, simultaneidade, preservação na queda e importação são capacidades independentes a comprovar | Em aberto; BE-12/INT-04/06 |
| ADR-06 | Um relógio monotônico para duração, cooldown, timeout e offsets; epoch somente para correlação externa. Definir mapa sessão→mídia antes de gerar cortes | Clock implementado em SH-04, sem execução; mapa de mídia pendente BE-12 |
| ADR-07 | Sampler recebe captura antes de reduzir a frequência. Disponibilidade usa chegada de frames de captura, nunca término de inferência. Buffer limitado: um em análise e no máximo um mais recente aguardando | Contrato 2.0.0 implementado, sem execução; pipeline pendente BE-00/02 |
| ADR-08 | Falha de IA não significa frame saudável nem queda Bluetooth. Suspender orientação e sinalizar indisponibilidade; manter causa tipada e recuperação sem reiniciar timeline | Planejado; SH-04/BE-03/FE-09 |
| ADR-09 | Vídeo original e transcrição permanecem locais; somente frame amostrado segue ao provedor visual. Nada de bytes de mídia, texto de transcrição ou tokens em logs/test reports | Planejado; BE-10 |
| ADR-10 | Sem usuários/contas no MVP. App Check e quotas protegem uso da IA; CORS, package name ou API key isoladamente não autenticam chamadores | Planejado; BE-13 |

## Resolução obrigatória em SH-04

Escolher um contrato único para captura crua versus amostrada: a preferência é `GlassesGateway.frames` como fonte anterior ao sampler, com disponibilidade/heartbeat de captura independente, e sampler no domínio. Isso altera a semântica documentada em Kotlin 1.0.0 e exige versão compatível com o impacto nos consumidores. Não aplicar dupla amostragem para conciliar os documentos.

Adicionar intenção audível de pausa e retomada de sessão, ausentes de `SessionSignal`. Retomada da conexão e retomada após pausa são eventos distintos. Definir sinal de falha de análise/áudio e causas em `SessionSnapshot`, sem renomear os dez estados. `DEGRADED` por falta de frame ocorre em 4 s; IA indisponível deve ter causa distinta e aviso, podendo usar DEGRADED com motivo tipado. Recuperar para PAUSED quando havia pausa, e não iniciar análise automaticamente.

Não usar falta de frames provocada por `pauseCapture()` como queda. Definir como o adapter mantém a sessão DAT viva e observa conexão durante pausa; se a API não permitir a mesma operação, registrar a tradução e sua limitação.

Estabelecer regra entre `samplingFps` e `analysisIntervalMs` (um valor derivado do outro) e mover o watchdog para saúde da captura. Assim reduzir amostragem não desliga a garantia dos 4 s. Documentar a evolução da restrição atual `degradedAfterMs > analysisIntervalMs`.

## Gate de hardware e contingência

BE-12 começa no módulo 02: guardar versão SDK, modelo/firmware, documentação e resultado para cada capacidade. Antes do hardware, o resultado pode ser `não verificado`, nunca `suportado` por inferência. Arquivo selecionado pelo usuário após STOPPED é fallback de importação demonstrável, mas não prova acionamento nativo por voz, transferência automática ou gravação simultânea.

Se captura DAT impedir gravação nativa, isso compromete a promessa submetida. Continuar desenvolvimento desacoplado com fixtures, registrar o bloqueio e buscar decisão de produto/organização; não substituir o vídeo nativo por frames de streaming nem declarar o fluxo completo entregue.

## Fronteira de controllers/classes

Planejar `SessionCoordinator`, `FrameSampler`, `GeminiFrameAnalyzer`, `DiagnosisValidator`, `GuidanceEngine`, `CorrectionTracker`, `TimelineRepository`, `NativeRecordingSource`, `LocalWhisperTranscriber`, `CutPlanner` e `ReviewExporter` como componentes internos/adapters. Estes nomes são destinos propostos, não arquivos que já existem. Não replicar regras em controllers HTTP. DTO do provedor termina no `GeminiFrameAnalyzer`; o restante consome o contrato Kotlin.
