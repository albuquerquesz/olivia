# Primeira entrega — coordenador de sessão e watchdog

Status: código e testes escritos; nenhuma validação automatizada ou de hardware foi executada.

`SessionCoordinator` vive em `:core` e recebe apenas as fronteiras `GlassesGateway`,
`AudioFeedback`, `Timeline` e `OliviaClock`. Ele não importa Android/DAT, não inicia gravação
nativa e não manipula bytes de frame. O adapter deve registrar `CaptureHeartbeat` antes de
passar um frame ao sampler; a primeira evidência de captura faz `STARTING → STARTED`.

O watchdog usa relógio monotônico e somente opera em `STARTED`: aos 4 s sem heartbeat válido,
invalida a geração de captura, entra em `DEGRADED`, preserva a timeline e emite o sinal de
conexão perdida. `PAUSED` não degrada por ausência intencional de frame. Conexão recuperada só
sai de `DEGRADED` depois de um novo heartbeat, não por callback de conexão isolado. Retomada
abre uma nova geração e também aguarda heartbeat.

`InMemoryTimeline` é o repositório local provisório para replay e testes; persistência atômica
privada é trabalho de BE-05. Os testes escritos cobrem a fronteira de 3999/4000 ms, pausa,
retomada e preservação de eventos. DAT/MockDeviceKit, permissões e wake word continuam em
BE-00/FE-03 e não foram alegados como implementados.

O companion agora instancia um `SessionRuntime` por flavor. No `mock`, uma fonte sintética de
frames 720×1280, marcada `FrameOrigin.MOCK`, atualiza o heartbeat antes de emitir cada marcador.
Ela alimenta `SessionCoordinator`, `FileTimeline` e `AndroidAudioFeedback` locais; não gera
diagnóstico visual, não usa DAT e não controla gravação nativa. No flavor `glasses`, os comandos
retornam indisponibilidade controlada até INT-00 fornecer um adapter DAT verificado.

AtualizaÃ§Ã£o do runtime mock: a fonte agora percorre, de ponta a ponta, `FrameSampler` (0,5
fps), `MockFrameAnalyzer`, `DefaultGuidanceEngine`, Ã¡udio, `CorrectionTracker` e
`FileTimeline`. O runtime serializa esses componentes; as respostas por sequÃªncia simulam
enquadramento fora do centro, confirmaÃ§Ã£o, sombra e confirmaÃ§Ã£o. Somente saÃ­das estruturadas
chegam Ã  timeline: bytes de frame nÃ£o sÃ£o persistidos nem registrados em log. Esta Ã© uma
demonstraÃ§Ã£o offline marcada como `MOCK`, sem DAT, Gemini ou gravaÃ§Ã£o nativa.

Command boundary update: `LocalCommandRecognizer` emits only parsed session commands and never
raw microphone audio or transcripts. `LocalCommandController` owns Android microphone permission
and lifecycle, then forwards recognized commands to `SessionRuntime`. The current Porcupine
implementation is explicitly unavailable: it does not request the microphone, record audio, or
call a network service until an access key and approved pt-BR models are configured outside the
repository. Therefore FE-03.1 remains open and no local recognition is claimed yet.
