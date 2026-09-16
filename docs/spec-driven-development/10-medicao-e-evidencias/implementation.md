# First delivery - live loop metrics

Status: métricas agregadas e testes unitários implementados; execução de medição física e bateria
permanecem pendentes.

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
