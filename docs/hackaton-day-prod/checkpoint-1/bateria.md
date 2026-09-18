# Bateria — checkpoint técnico 1

## Status: estratégia e instrumentação prontas; primeira medição real existe, mas é insuficiente

Como o [00-event-contract.md](../00-event-contract.md#L20) e o
[05-privacy-and-evidence.md](../05-privacy-and-evidence.md#L16-L20) exigem: **estratégia de
eficiência não é medição de bateria**. Esta nota separa as duas coisas com honestidade.

Atualização 18/09, sessão física: com o pipeline completo rodando (frame real → Gemini → ROI →
áudio), a instrumentação corrigida hoje já capturou os dois primeiros round-trips completos de
Gemini: **≈1,85 s e ≈3,68 s**. Isso é dado real, não estimativa — mas ainda é só **2 amostras**,
não as ≥10 exigidas, e as duas ficam bem acima da meta de ~900 ms. Ver seção "O que falta".

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

- **Amostra insuficiente**: só 2 respostas completas medidas (`≈1,85 s`, `≈3,68 s`), não as ≥10
  exigidas pelo `05-privacy-and-evidence.md`. Falta repetir a sessão física algumas vezes e deixar
  o log acumular mais amostras antes de calcular p50/p95 de verdade.
- **Ressalva de validade da amostra**: essas duas medições foram feitas com o timeout de rede em
  modo diagnóstico (`DIAGNOSTIC_TIMEOUT_MS = 8_000`, só em `DEBUG`/MockDeviceKit — ver
  `FirebaseVisualAnalyzerFactory.kt:42-47`), criado hoje porque o guard de produção de 2 s estava
  cancelando a chamada antes de qualquer resposta chegar. Isso não distorce o tempo de rede/inferência
  em si (o cronômetro mede o round-trip real, não o timeout), mas significa que essas amostras vêm
  de um build que tolera respostas lentas — não prova que o guard de 2 s de produção se comporta
  igual. Não usar essas duas amostras como prova do build de release.
- **Bateria em si**: ainda não há leitura de `dumpsys battery` antes/depois de uma sessão — só uma
  leitura pontual do Meta AI (75% num instante, sem consumo calculado).
- **Meta de ~900 ms**: as duas amostras reais (1,85 s e 3,68 s) estão bem acima dela. Isso não é um
  problema de instrumentação — é sinal real de que o caminho físico (Bluetooth + decode + upload +
  inferência) hoje é mais lento que a meta, e vale investigar antes de prometer o número na banca.

## Como demonstrar na banca

1. Repetir sessões físicas curtas pra acumular ≥10 amostras, capturar o log dos eventos de timing
   (`OliviaVisualTiming`/`OliviaSessionRuntime`), calcular p50/p95 de verdade e anotar
   `dumpsys battery` antes/depois.
2. Ser honesto sobre o número: mostrar as duas amostras reais de hoje (1,85 s / 3,68 s) como
   evidência de que o caminho funciona ponta a ponta, e declarar explicitamente que a meta de
   900 ms ainda não foi atingida — não arredondar isso pra "dentro da meta".
3. Mostrar a estratégia (0,5 fps, cooldown de 4 s, frame efêmero, sem WakeLock) como o motivo de a
   eficiência ser boa mesmo com a latência de rede ainda alta.
