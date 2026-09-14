# Primeira entrega — direção e confirmação

Status: código e testes unitários escritos; nenhuma verificação foi executada.

`DefaultGuidanceEngine` aceita exclusivamente os diagnósticos fechados do contrato. Ele aplica
confidence ≥0,80, severidade `MAJOR`, cooldown global de 4 s, prioridade por severidade,
confiança e então `FRAMING`, `LIGHT`, `HORIZON`. As frases vêm de `GuidanceCatalogue` local;
texto ou magnitude da IA nunca alcança a fala. Há no máximo uma correção pendente.

`CorrectionTracker` só abre a pendência após `AudioReceipt`, isto é, após o callback de início
de reprodução. Um frame posterior, da mesma geração de captura, com `OK` e confiança suficiente
fecha `OBEYED`. Timeout não confirma: desvio confiável persistente vira `IGNORED`; ausência ou
baixa confiança vira `EXPIRED`; pausa, degradação e STOP usam `expire()`.

O `InMemoryTimeline` já registra IDs de evento de modo idempotente para o replay local. A
persistência atômica em armazenamento privado, TTS/WAV Android e a integração Docker ainda
permanecem nas subtasks BE-05.2/FE-04.1.

`AndroidAudioFeedback` implementa a fronteira de áudio no companion: TTS pt-BR para Modo Voz
e tom PCM estéreo local de 80 ms para Modo Sinal. O recibo de voz só retorna em `onStart` do
TTS; erro de inicialização, voz indisponível ou recusa do engine vira falha controlada. O tom é
gerado e liberado em memória, sem WAV/arquivo persistido, rede ou conteúdo de frame. A rota
Bluetooth real e audibilidade nos óculos seguem pendentes de INT-05; esse adapter não é prova
de hardware.
