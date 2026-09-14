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
