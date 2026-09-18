# Primeira entrega — SH-04 e runner parcial BE-14

Status: código, testes e execução JVM/Docker registrados em 17/09/2026. Nenhum resultado de hardware,
qualidade de IA ou desempenho foi promovido por esta entrega. As regras de escopo mantiveram o trabalho
no domínio local; SH-02 fixa Firebase para visão e mantém auth/contas/CRUD fora do MVP.

## O que existe agora

O módulo `:core` publica contratos **2.0.0**. `OliviaClock`/`ClockReading` separam tempo monotônico de relógio civil. `SystemOliviaClock` usa origem única por instância; compartilhar uma instância no processo (Android pode fornecer adapter de relógio de boot posteriormente). Epoch pode voltar sem reduzir duração/cooldown. Não reutilizar valores monotônicos após reinício.

`GlassesGateway.frames` fornece captura antes do sampler, com um único consumidor e buffers limitados por contrato. `heartbeat` contém apenas metadata atualizada no callback de captura independentemente da análise. `CaptureFence` abre/invalida gerações para filtrar resultados tardios; pausa, degradação e STOP devem invalidar e retomada deve abrir geração nova. A integração desses efeitos é responsabilidade do futuro coordenador.

`SessionConfig.analysisIntervalMs` é a fonte única e `samplingFps` é derivado. Reduzir sampling não exige aumentar os 4 s do watchdog. `SessionSnapshot` admite múltiplas causas de degradação e guarda intenção STARTED/PAUSED. `SessionSignal` distingue pausa, retomada, conexão, análise e áudio. `AudioReceipt` é o recibo de início por callback, correlacionado por correctionId e CaptureId; não confirma que o som chegou fisicamente aos óculos.

`ContractJson` serializa timeline/review e rejeita versão ausente, diferente ou de eventos aninhados incompatíveis, além de referências a outra sessão. JSON usa `kotlinx.serialization` e os mesmos tipos públicos. Frame/bytes/credenciais não têm serializer. Transcrição na revisão continua privada e nunca deve ser logada. Casos de parsing com exceção devem ser convertidos em erro sanitizado pelo chamador, sem imprimir a exceção inteira com entrada.

## Matriz de transições implementada

| Origem | Destinos permitidos |
|---|---|
| IDLE | STARTING |
| STARTING | STARTED, STOPPING, ERROR |
| STARTED | PAUSED, DEGRADED, STOPPING |
| PAUSED | STARTED, DEGRADED, STOPPING |
| DEGRADED | STARTED, PAUSED, STOPPING |
| STOPPING | STOPPED, ERROR |
| STOPPED | PROCESSING, STARTING |
| PROCESSING | READY, ERROR, STOPPED |
| READY | STARTING |
| ERROR | IDLE |

`SessionTransitions` define apenas arestas, não executa comandos, relógios ou callbacks. Comando repetido deve ser no-op idempotente no coordenador; self-transition não é um novo evento. Não entrar em STARTED sem captura pronta, sair de DEGRADED sem resolver causas ou processar antes de STOPPED. Falha de captura/IA/áudio durante sessão ativa usa causa de DEGRADED; erro fatal/startup/encerramento exige caminho controlado. Em PAUSED o watchdog de frames intencionalmente suspensos fica desarmado, mas conexão é observada. Nova sessão enquanto PROCESSING cancela o pós-processamento, passa por STOPPED e abre uma nova geração. Esses efeitos são FE-02/FE-09, não comportamento já executado por esta entrega.

## Migração da API 1.0.0

| Antes | Agora |
|---|---|
| `SessionConfig(samplingFps = x, analysisIntervalMs = y)` | Configurar somente `analysisIntervalMs`; fps é propriedade derivada |
| `startCapture(sessionId, config)` | `startCapture(CaptureId)`; sampling pertence ao domínio |
| `resumeCapture()` | `resumeCapture(CaptureId)` com geração nova |
| `GlassesGateway.frames` amostrados | Frames anteriores ao sampler e `heartbeat: StateFlow<CaptureHeartbeat?>` separado |
| `Frame(sessionId, capturedAtEpochMs, ...)` | `Frame(FrameStamp(capture, sequence, receivedAt), ...)`; tempo é chegada no app, não exposição do sensor |
| `FrameAnalysis` com epoch de frame/sessão | `frameStamp`, `analyzedAt: ClockReading` e latência não negativa |
| `Correction(sessionId, issuedAtEpochMs, ...)` | `Correction(capture, issuedAt: ClockReading, ...)` |
| `onAnalysis(analysis, nowEpochMs)` | `onAnalysis(analysis, ClockReading)` |
| `announce/signal` sem retorno | `Result<AudioReceipt>` / `Result<ClockReading>`; cancelar propaga CancellationException |
| Arquivo sem versão ou `1.x` | Rejeitado pelo codec atual; nenhuma migração silenciosa ou sobrescrita |

A busca de consumidores no repositório encontrou apenas declarações dos contratos antes desta entrega; as telas usam `AudioMode`, preservado. Test doubles desta entrega usam API 2.0.0. Isso é inspeção de código, não confirmação por compilação. Não foi encontrado codec/arquivo persistido de produção 1.x que demandasse migração agora; se existir fora do repositório, preservar o original e implementar leitor/migração explícitos antes de usá-lo.

## Testes entregues e limitações

`ContractPolicyTest`: sampling/watchdog independentes, intervalos inválidos, salto de relógio civil, PROCESSING após STOPPED, causas de degradação e valores visuais inválidos. `CaptureFenceTest`: STOP, nova sessão, retomada na mesma sessão e estados sem análise. `ContractIntegrationTest`: composição fonte de fixture→analisador de fixture→fence real→codec real, exemplos dourados timeline/review e rejeição de versões/sessões incompatíveis. Os bytes dos doubles são marcadores sintéticos, não imagens válidas; decodificação é integração posterior de BE-00/03.

As fixtures JSON estão em `apps/android/core/src/test/resources/contracts`. Não são resultados de um take real. O [runner Docker](../../../infra/testing/README.md) usa o módulo concreto em build JVM isolado, sem Android SDK, DB ou secrets. Unitários e integração têm tasks separadas. A criação da imagem compila/prepara dependências; sua execução fica offline e sem rede.

SH-04 teve a execução JVM concluída em 17/09/2026; o relatório está em `../evidence/VALIDATION_RUN_2026-09-17.md`. O aceite do harness Docker/Bun continua em BE-14, e a validação de hardware permanece separada. O mapa sessão→mídia é BE-12 e permanece aberto.
