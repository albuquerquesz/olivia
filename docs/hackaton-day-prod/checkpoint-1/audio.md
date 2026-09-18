# Áudio — checkpoint técnico 1

## Status: pronto no código e no roteamento físico; falta confirmar que é a fala da Olivia (não áudio genérico)

Ver [02-bluetooth-and-dat.md](../02-bluetooth-and-dat.md#L19-L22): *"Ouvir voz e todos os tons nos
Ray-Ban é a única prova de áudio físico."* Isso é sobre a **saída** de áudio (orientação). O
microfone é assunto de outro checkpoint (câmera/microfone) — ver nota no final.

## O que já está pronto

- Dois modos implementados: `VOICE` (TTS pt-BR) e `SIGNAL` (tom PCM local de 80 ms, sem síntese,
  mais barato em bateria e imune a falha de TTS) —
  `apps/android/core/src/main/kotlin/com/olivia/core/GuidanceContract.kt:62-68`.
- Frases fixas em pt-BR por direção, nunca texto gerado por IA —
  `apps/android/core/src/main/kotlin/com/olivia/core/DefaultGuidanceEngine.kt:21-39` (`GuidanceCatalogue`).
- Cooldown de 4 s e exigência de 2 frames consecutivos concordando antes de falar, evitando spam de
  áudio — `apps/android/core/src/main/kotlin/com/olivia/core/DefaultGuidanceEngine.kt:5-9`.
- Roteamento é a saída de mídia Android padrão (o que já está pareado por Bluetooth), sem
  implementação própria de transporte — `AndroidAudioFeedback`
  (`apps/android/app/src/main/kotlin/com/olivia/companion/audio/AndroidAudioFeedback.kt`).
- **Confirmado hoje pelo operador**: áudio já é ouvido normalmente pelo Bluetooth no Ray-Ban a
  partir do Moto G60 — o pareamento/roteamento físico funciona.
- Instrumentação de latência do áudio (`TTS_INIT`, `TTS_REQUEST_TO_START`,
  `SIGNAL_REQUEST_TO_START`) ligada a log hoje (ver `bateria.md`) — tag `OliviaSessionRuntime`,
  evento `audio_timing`.

### Mecanismo (por que a prova de áudio aqui é forte)

O `AudioReceipt` que confirma que a correção "começou a ser dita" **não é o momento em que o app
pediu pra falar** — é o callback `UtteranceProgressListener.onStart(utteranceId)` do próprio motor
TTS do Android, que só dispara quando o Android efetivamente começa a reproduzir o áudio
(`AndroidAudioFeedback.kt:91-107`). Ou seja, `receipt.startedAt` é uma prova de reprodução real, não
de "enfileirado". Isso é o motivo do aviso no [02-bluetooth-and-dat.md](../02-bluetooth-and-dat.md#L20-L22):
mesmo essa prova de callback não substitui ouvir o som saindo do óculos — só reduz a distância entre
os dois.

Duas camadas de fallback, ambas automáticas:

1. Se o modo `VOICE` não iniciar a fala em 5 s (`speak()` tem `withTimeoutOrNull(5_000)`,
   `AndroidAudioFeedback.kt:134`), o próprio `announce()` cai pro tom `SIGNAL` sem intervenção —
   nunca fica em silêncio total por falha de TTS.
2. O tom `SIGNAL` não é gerado em tempo real: cada `SignalDirection` tem uma senoide de 80 ms a
   48 kHz pré-computada em memória e cacheada num `AudioTrack` reaproveitável
   (`AndroidAudioFeedback.kt:242-278`) — reproduzir é só `track.play()`, sem custo de síntese.

## O que falta
 
- Confirmar especificamente que o som ouvido foi uma **`Correction` da Olivia** (voz ou tom),
  não áudio genérico do sistema (música, notificação, etc.). É rápido de testar: não depende do
  DAT/câmera, só precisa de uma correção disparada (real ou de teste) enquanto o Bluetooth está
  conectado.

## Como demonstrar na banca

1. Com os Ray-Ban conectados por Bluetooth, disparar uma correção (durante uma sessão real ou um
   teste dirigido) e deixar a banca ouvir a frase em pt-BR saindo dos óculos.
2. Mostrar o modo Sinal como alternativa mais barata em bateria, se o tempo permitir.
3. Citar o cooldown de 4 s como parte do design de não sobrecarregar a pessoa com áudio.

## Nota — não confundir com o microfone

O microfone que a Olivia usa para "Oi, Olivia" e comandos é **sempre o do celular** (Porcupine +
Rhino, 100% local), nunca o microfone dos óculos — o DAT 0.9.0 não expõe microfone ao vivo, por
invariante de produto (`AGENTS.md`). Isso já foi confirmado funcionando hoje (wake word detectada
localmente). Esse ponto pertence ao checkpoint de câmera/microfone, não a este arquivo.
