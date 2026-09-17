# First delivery - deterministic cut planner

Status: code and unit tests written; JVM and Android flavor unit tests passed on 17/09/2026.
Whisper runtime/model, real media import from DAT, alignment evidence and hardware validation
remain open.

`CutPlanner` accepts only an already-aligned native-media timeline, validated transcript segments,
and a known recording duration. It suggests exclusion intervals for uncorrected deviations,
degraded capture, and explicit spoken retakes; it never treats missing transcription as silence.
Overlaps merge deterministically, intervals are bounded, and an entire-session cut is rejected.
No media bytes, transcript persistence, rendering, or editor integration was added.

O importador Android agora cria um rascunho local versionado da revisão com as marcas disponíveis,
sem inventar transcrição, silêncio ou cortes. `ReviewExporter` produz JSON e SRT apenas quando há
segmentos locais; `AndroidReviewExporter` compartilha o vídeo privado e os artefatos por URI de
leitura temporária no Android Sharesheet, onde CapCut pode ser escolhido se estiver instalado.

O ciclo `STOPPED → PROCESSING → READY` foi implementado no coordenador e só chega a `READY` após
o provider local devolver uma revisão válida. Falha entra em `POST_PROCESSING_FAILED`; cancelamento
volta a `STOPPED` e preserva o take. O adapter Whisper, media alignment real, Docker e execução
Android ainda dependem de runtime/modelo provisionado.

`LocalPostProcessor` now composes an injected on-device transcript provider with an already
imported recording, the session timeline, verified media alignment, and `CutPlanner`. Missing
alignment returns a partial review with no automatic cuts rather than an invented offset. The
actual Whisper provider and media alignment real remain unimplemented; o rascunho de importação
não é uma alegação de transcrição concluída.
