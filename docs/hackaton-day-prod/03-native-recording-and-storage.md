# 03 — Gravação nativa, armazenamento e pós-take

## Separação obrigatória

O stream DAT Bluetooth é sensor para direção. O take final é mídia 3K nativa dos óculos e só entra
depois de `STOPPED` pelo caminho oficialmente exposto. Nunca criar o vídeo final dos frames DAT.

## Matriz de capacidades

| ID | Prova | Estado inicial | Fallback |
| --- | --- | --- | --- |
| A1 | início/fim nativo mãos-livres com horários | `PENDING_HARDWARE` | demonstrar stream/loop |
| A2 | 3K + áudio coexistindo com DAT | `PENDING_HARDWARE` | não reconstruir vídeo |
| A3 | take íntegro após queda Bluetooth | `PENDING_HARDWARE` | preservar marcas |
| A4 | mídia pós-stop com MIME/duração/áudio válidos | `PENDING_HARDWARE` | importação manual `content://` |
| A5 | alinhamento timeline/mídia ≤250 ms | `PENDING_HARDWARE` | marcas sem corte automático |

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
