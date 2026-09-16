# First delivery - live loop metrics

Status: métricas agregadas e testes unitários implementados; execução de medição física e bateria
permanecem pendentes.

O ensaio Android de 16/09/2026 no Moto G60 executou dez ciclos Gemini + TTS e está registrado em
[BE07_MOTO_G60_2026-09-16.md](BE07_MOTO_G60_2026-09-16.md). A amostra não atingiu a meta de 900 ms
(p50 frame→áudio 2257 ms; p95 5957 ms); portanto BE-07 permanece aberto para otimização e para
validação posterior com frame DAT e saída nos óculos.

`LiveLoopMetrics` stores a bounded, in-memory collection of monotonic timing pairs plus sampling
counters. It computes nearest-rank p50/p95 de inferência e do par frame→callback Android de áudio.
Audio request is deliberately different from callback, and neither is evidence of the
acoustic instant at the Ray-Ban speaker. No frame bytes, raw model output, audio, text, or
credential is collected.

The mock runtime records receive/select/discard counts and pairs each selected frame with analysis
and, when present, the matching audio callback. The artifact has no analytics upload or
persistence. O `SessionRuntime` expõe somente esse snapshot agregado em memória para a tela ou um
teste autorizado; ele não cria analytics nem arquivo. Medições no dispositivo, método de bateria e
todos os gates físicos permanecem pendentes.

`LiveTrialReporter` transforma o `SessionConfig` e o snapshot em um relatório agregado de ensaio.
Ele torna visíveis os defaults 0,5 fps/2 s e cooldown de 4 s e aceita níveis inicial/final de
bateria somente quando alguém os observou no aparelho ou nos óculos. Delta ausente permanece
`null`: o app não inventa consumo, não lê serial, nem persiste o relatório.
