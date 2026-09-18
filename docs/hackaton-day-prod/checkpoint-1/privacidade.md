# Privacidade — checkpoint técnico 1

## Status: pronto para demonstrar

Auditoria feita por leitura de código (sem rodar build/lint/teste), cruzando com as regras de
["Nunca registrar"](../05-privacy-and-evidence.md#L3-L7). Não depende de hardware físico — é
arquitetura, não medição.

## O que já está pronto

| Regra | Onde está implementado | Evidência |
| --- | --- | --- |
| Frame/pixels/vídeo nunca logado | `DatGlassesGateway.logFirstFrame()` só loga `origin/format/width/height` do primeiro frame; comentário explícito "never logs frame bytes or image data" | `apps/android/app/src/glasses/kotlin/com/olivia/companion/session/DatGlassesGateway.kt:208-214` |
| Frame nunca persiste em disco | `GeminiVisualTransport`: "must keep Frame.bytes ephemeral: no file, log, cache, retry queue, or transcript is permitted"; bitmaps são `recycle()`d no `finally` | `apps/android/core/src/main/kotlin/com/olivia/core/GeminiFrameAnalyzer.kt:8-9`, `apps/android/app/src/glasses/kotlin/com/olivia/companion/ai/FirebaseGeminiVisualTransport.kt:102-105` |
| Resposta/erro do provedor de IA nunca logado | `classify()` só loga `error::class.simpleName` e só em `DEBUG`; nunca corpo da resposta nem dados do frame | `FirebaseGeminiVisualTransport.kt:88-101` |
| Áudio bruto/transcrição nunca logado | Whisper local só loga `decodeMs/modelLoadMs/inferMs` (tempos), nunca o texto transcrito | `apps/android/app/src/main/kotlin/com/olivia/companion/transcription/WhisperLocalTranscriptProvider.kt:41-44` |
| Transcrição de voz nunca cruza a fronteira do reconhecedor | Rhino (comando de voz) só entrega a intent estruturada; "never exposes a transcript to the session" | `apps/android/app/src/main/kotlin/com/olivia/companion/session/AndroidVoiceCommandSource.kt:223-234` |
| Credenciais nunca logadas em código próprio | `MWDAT_APPLICATION_ID`/`MWDAT_CLIENT_TOKEN`/chave Picovoice só passam por `BuildConfig`, nunca aparecem em `Log.*` | `apps/android/app/build.gradle.kts` |
| Resposta da IA é fechada por schema | Gemini só pode devolver `schemaVersion/targetPresent/confidence/roi` — não pode devolver texto livre, descrição de tatuagem, nem identificar pessoa (instrução explícita no prompt) | `FirebaseGeminiVisualTransport.kt:183-212` |

### Mecanismo: o schema não é "boa vontade do prompt", é restrição do modelo

A parte que dá força a essa linha é que `responseSchema = WORK_AREA_RESPONSE_SCHEMA` é passado na
`generationConfig` do `generativeModel` (`FirebaseGeminiVisualTransport.kt:45-56`), junto com
`responseMimeType = "application/json"`. Isso é **structured output** do Gemini: o SDK obriga o
modelo a gerar apenas essa forma de JSON (chaves e tipos fixos), não é uma instrução no texto do
prompt que o modelo poderia ignorar. Mesmo se o modelo "quisesse" descrever a cena em texto livre,
a chamada não teria como retornar isso — o parser (`WorkAreaLocalizationSchema.decode`) também
rejeita qualquer coisa fora do formato em `GeminiFrameAnalyzer.kt:77-78`, então há duas camadas de
contenção: a do provedor (schema) e a do app (decode estrito).

O ciclo de vida do frame também é curto de propósito: `FirebaseGeminiVisualTransport.classify()`
decodifica o `Bitmap`, reduz pra no máximo 512 px, codifica em JPEG **em memória**, manda pro
`generateContent()` e libera (`recycle()`) tanto o bitmap decodificado quanto o reduzido no `finally`
(`FirebaseGeminiVisualTransport.kt:63-106`) — o frame nunca toca o disco em nenhum ponto desse
caminho.

## O que falta

- Nada de código pendente. O token de debug do Firebase App Check foi cadastrado no Console hoje e
  **confirmado funcionando no físico** (`APP_CHECK_READY` observado no log durante a sessão real) —
  ponto operacional resolvido, não precisa de mais ação. Só reforçando: esse token nunca deve ficar
  em nenhum arquivo do repositório nem em evidência anexada, só no Console do Firebase.

## Como demonstrar na banca

Explicar a arquitetura, não precisa de hardware ligado:

1. Mostrar o prompt do Gemini ([FirebaseGeminiVisualTransport.kt:202-212](../../../apps/android/app/src/glasses/kotlin/com/olivia/companion/ai/FirebaseGeminiVisualTransport.kt#L202-L212)) e o schema fechado — a IA só pode devolver coordenadas, nunca descrição de conteúdo sensível.
2. Explicar que o frame é efêmero: decodifica, analisa, descarta — nunca grava.
3. Explicar que a transcrição (Whisper) só roda local, só depois de `STOPPED`, nunca ao vivo.
4. Citar a regra de wake word/comando 100% local no celular — o DAT nunca expõe microfone ao vivo.
