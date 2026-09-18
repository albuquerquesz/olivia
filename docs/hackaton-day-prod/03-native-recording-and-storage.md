# 03 — Gravação nativa, armazenamento e pós-take

## Separação obrigatória

O stream DAT Bluetooth é sensor para direção. O take final é mídia 3K nativa dos óculos e só entra
depois de `STOPPED` pelo caminho oficialmente exposto. Nunca criar o vídeo final dos frames DAT.

## Matriz de capacidades

| ID | Prova | Estado inicial | Fallback |
| --- | --- | --- | --- |
| A1 | início/fim nativo mãos-livres com horários | `BLOCKED_BY_DAT_0_9_0` | demonstrar stream/loop |
| A2 | 3K + áudio coexistindo com DAT | `BLOCKED_BY_DAT_0_9_0` | não reconstruir vídeo |
| A3 | take íntegro após queda Bluetooth | `BLOCKED_BY_DAT_0_9_0` | preservar marcas |
| A4 | mídia pós-stop com MIME/duração/áudio válidos | `BLOCKED_BY_DAT_0_9_0` | importação manual `content://` |
| A5 | alinhamento timeline/mídia ≤250 ms | `BLOCKED_BY_A1_A4` | marcas sem corte automático |

## Gate verificado em 18/09/2026

O artefato Android DAT `0.9.0` efetivamente integrado ao app foi inspecionado. A interface pública
`com.meta.wearable.dat.camera.Stream` oferece somente `start`, `stop`, `videoStream`,
`errorStream` e `capturePhoto`. Ela não oferece iniciar/parar gravação nativa nos óculos, listar
assets, nem transferir um vídeo para o companion. Assim, `ActiveBackend.createRecordingSource()`
permanecer `null` no flavor `glasses` é o comportamento correto para esta versão: não existe
adapter possível sem uma API da Meta que ainda não foi exposta.

Não gravar ou muxar o stream DAT no celular como substituto: ele é o sensor Bluetooth de direção,
não o take 3K nativo prometido. Até a Meta disponibilizar essa capacidade, a única via suportada
no app é a importação explícita de um `content://` já presente no dispositivo, exclusivamente
para revisão pós-take; ela não prova A1–A3 nem A5.

## A1 e A2

Com o stream físico saudável, usar somente o acionamento nativo oficialmente exposto. Observar
frames DAT por 10 s, criar marca conhecida, encerrar e confirmar arquivo nativo novo. Sem arquivo
e coexistência observados, não marcar suporte.

## A3

Executar somente após A1/A2 e com recuperação conhecida. Depois da perda, importar a mídia e
confirmar duração/áudio antes/depois. `DEGRADED` no app não prova A3.

## A4 e A5

Depois de `STOPPED`, importar pelo fluxo oficial. Seleção manual é só fallback de revisão: copiar
para cache privado, validar `content://`, MIME/duração/áudio e registrar metadados. Comparar uma
marca conhecida à trilha para A5.

## Privacidade

Nunca anexar mídia, áudio, frame, transcrição, URI privada ou token à evidência. Whisper usa somente
áudio da mídia após `STOPPED`; nunca microfone ao vivo.
