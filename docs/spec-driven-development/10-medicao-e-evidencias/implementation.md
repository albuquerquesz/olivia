# First delivery - live loop metrics

Status: métricas agregadas, spans por estágio e testes unitários implementados; a rodada Gemini +
TTS no Moto foi executada. DAT, bateria e onset acústico nos óculos permanecem pendentes.

O ensaio Android de 16/09/2026 no Moto G60 executou dez ciclos Gemini + TTS e está registrado em
[BE07_MOTO_G60_2026-09-16.md](BE07_MOTO_G60_2026-09-16.md). A amostra não atingiu a meta de 900 ms
(p50 frame→áudio 2257 ms; p95 5957 ms); portanto BE-07 permanece aberto para otimização e para
validação posterior com frame DAT e saída nos óculos.

O mesmo ensaio agora inclui `FirebaseStreamTimingMeasurementTest`, somente diagnóstico, com
`gemini-3.5-flash-lite`: TTFT p50 1714 ms/p95 4586 ms e conclusão do stream p50 1852 ms/p95
4735 ms em dez amostras. A diferença curta entre primeiro e último chunk mostra que a espera
principal ocorre antes do primeiro chunk. O `FirebaseGeminiVisualTransport` fixa
`ThinkingLevel.MINIMAL`; o gate ROI pós-alteração passou, sem mudar o contrato de localização.
O caminho one-shot pós-alteração também completou 10/10 no Moto (round-trip p50 2008 ms/p95
3909 ms; frame→callback TTS p50 2037 ms/p95 4275 ms). Esses números continuam sendo ensaios
diagnósticos de rede/serviço, não evidência de latência acústica nos óculos.

`LiveLoopMetrics` stores a bounded, in-memory collection of monotonic timing pairs plus sampling
counters. It computes nearest-rank p50/p95 de inferência e do par frame→callback Android de áudio.
Audio request is deliberately different from callback, and neither is evidence of the
acoustic instant at the Ray-Ban speaker. No frame bytes, raw model output, audio, text, or
credential is collected.

The Android runtime keeps a single-flight analysis worker and a one-frame pending sampler buffer;
Gemini/TTS work is outside the state mutex, and PAUSE/STOP cancels the active job before the capture
generation is invalidated. Commits and timeline writes remain serialized and generation-checked.

The mock runtime records receive/select/discard counts and pairs each selected frame with analysis
and, when present, the matching audio callback. The artifact has no analytics upload or
persistence. O `SessionRuntime` expõe somente esse snapshot agregado em memória para a tela ou um
teste autorizado; ele não cria analytics nem arquivo. O teste BE-07 também registra, em log agregado,
decode/resize/JPEG/round-trip do SDK/parse/análise local e readiness/pedido→início do TTS. A rodada
no Moto está no relatório vinculado; o round-trip do SDK domina e uma de dez chamadas diagnósticas
atingiu timeout. Ingresso DAT, método de bateria e todos os gates físicos permanecem pendentes.

`LiveTrialReporter` transforma o `SessionConfig` e o snapshot em um relatório agregado de ensaio.
Ele torna visíveis os defaults 0,5 fps/2 s e cooldown de 4 s e aceita níveis inicial/final de
bateria somente quando alguém os observou no aparelho ou nos óculos. Delta ausente permanece
`null`: o app não inventa consumo, não lê serial, nem persiste o relatório.
