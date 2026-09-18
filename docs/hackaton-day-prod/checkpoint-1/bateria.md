# Bateria — checkpoint técnico 1

## Status: pronto na estratégia e na instrumentação; medição real ainda `PENDING`

Como o [00-event-contract.md](../00-event-contract.md#L20) e o
[05-privacy-and-evidence.md](../05-privacy-and-evidence.md#L16-L20) exigem: **estratégia de
eficiência não é medição de bateria**. Esta nota separa as duas coisas com honestidade.

## O que já está pronto

### Estratégia de eficiência (implementada, auditável por código)

- Amostragem de análise fixa em 0,5 fps (`analysisIntervalMs = 2_000`), não a cada frame da câmera —
  `apps/android/core/src/main/kotlin/com/olivia/core/Sampling.kt:35`.
- Cooldown de 4 s entre correções de áudio, evitando TTS repetido —
  `apps/android/core/src/main/kotlin/com/olivia/core/DefaultGuidanceEngine.kt:7`.
- Frame reduzido para no máximo 512 px antes do JPEG/upload ao Gemini, reduzindo custo de rede por
  chamada — `apps/android/app/src/glasses/kotlin/com/olivia/companion/ai/FirebaseGeminiVisualTransport.kt:174`.
- Sem `WakeLock` nem `keepScreenOn` em nenhum ponto do app — verificado por busca no código-fonte.
- `DatStreamingService` (foreground service do stream DAT) usa canal `IMPORTANCE_LOW` e só roda
  entre `startCapture`/`stopCapture`, não em background permanente —
  `apps/android/app/src/glasses/kotlin/com/olivia/companion/session/DatStreamingService.kt`.

### Mecanismo: por que o 0,5 fps é estrutural, não só um número de config

O `FrameSampler` (`Sampling.kt:29-60`) não é "chama a IA toda vez que dá 2 segundos" por
convenção — ele é **single-flight por construção**: só existe um slot de análise em voo
(`analysisInFlight`) e um único frame retido como "mais novo pendente" (`pending`). Um frame que
chega enquanto a análise anterior ainda não terminou, ou antes do `analysisIntervalMs` passar, é
descartado (`SamplingDecision.Discard`) e devolvido pro adapter de câmera imediatamente — nunca
enfileira. Isso significa que mesmo que a câmera do DAT entregasse frames mais rápido, o custo de
decodificar/analisar não escalaria com isso: o teto de 0,5 fps é imposto pela estrutura de dados,
não por disciplina de quem chama.

Vale notar que `DatGlassesGateway.kt:327` pede `DAT_FRAME_RATE = 2` (2 fps) **à câmera física** —
isso não contradiz o invariante de 0,5 fps: é a taxa de captura do sensor, não a taxa de análise. O
`FrameSampler` ainda descarta 3 de cada 4 frames capturados antes de qualquer decodificação pesada.

### Instrumentação de latência (corrigida hoje — antes não emitia nada)

A classe `LiveLoopMetrics` já calcula p50/p95 de inferência e de frame→áudio
(`apps/android/core/src/main/kotlin/com/olivia/core/LiveLoopMetrics.kt:33-39`), exatamente o que o
runbook pede para reportar. Mas os três pontos de instrumentação (`AnalysisTimingSink`,
`FirebaseTimingSink`, `AudioTimingSink`) estavam ligados ao callback padrão (no-op) nos três
lugares onde são construídos — ou seja, nada disso nunca chegava a um log. Corrigido:

- `apps/android/app/src/main/kotlin/com/olivia/companion/session/SessionRuntime.kt:214-220` — tag
  `OliviaSessionRuntime`, evento `audio_timing`.
- `apps/android/app/src/glasses/kotlin/com/olivia/companion/ai/FirebaseVisualAnalyzerFactory.kt:26-50`
  — tag `OliviaVisualTiming`, eventos `firebase_timing` e `analysis_timing`.

Com isso, assim que houver frame real + resposta do Gemini, os tempos aparecem automaticamente em:
```
adb logcat -s OliviaVisualTiming:I OliviaSessionRuntime:I
```

### Achado registrado, não corrigido (fora do escopo de hoje)

`SessionRuntime.kt:318-333` roda um watchdog em loop de 250 ms pela vida inteira do runtime,
inclusive quando a sessão está `IDLE`. É um padrão que custa bateria de forma real (impede o CPU de
descansar), mas mudar isso hoje tocaria na máquina de estados, que o freeze do evento proíbe
reescrever (`00-event-contract.md`, seção Freeze). Fica registrado para revisão pós-evento.

## O que falta

- **Medição real**: ≥10 inferências reais com bateria antes/depois anotada, p50/p95 lidos do log,
  amostra declarada. Isso só existe depois que o DAT físico entregar frame e o Gemini responder de
  verdade — é trabalho de operador no aparelho, não de leitura de código.

## Como demonstrar na banca

1. Se houver corrida física: rodar uma sessão curta, capturar o log dos dois eventos de timing
   acima, calcular/mostrar p50/p95 e a variação de bateria (`dumpsys battery` antes/depois).
2. Se não houver tempo/hardware a tempo: mostrar a estratégia (0,5 fps, cooldown de 4 s, frame
   efêmero) como design, e declarar explicitamente que a medição está pendente — nunca alegar um
   número que não foi medido.
