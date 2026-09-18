# 02 — Bluetooth, DAT e áudio

## Responsabilidades

| Camada | Faz | Não faz |
| --- | --- | --- |
| Meta AI / DAT | pareamento oficial, elegibilidade, transporte | ser substituída por pareamento Android genérico |
| `DatGlassesGateway` | sessão, câmera, stream, frame, heartbeat, cleanup | persistir bytes ou vazar tipos DAT ao domínio |
| `SessionCoordinator` | estados e gerações de captura | medir saúde pelo fim da IA |
| Android audio | TTS/tom e rota de mídia selecionada | garantir audibilidade sem teste físico |

## Caminho real

Com mock desligado, o gateway inicializa `Wearables`, cria sessão com `AutoDeviceSelector`, inicia
`DeviceSession`, adiciona câmera e inicia stream. Cada frame atualiza o heartbeat antes do sampler.
O caminho físico usa HEVC/decoder local; MockDeviceKit usa I420 raw no Moto. Êxito do mock não prova
decoder, frame 720×1280, rádio Bluetooth nem latência física.

## Áudio

Modo Voz usa TTS pt-BR; Modo Sinal usa PCM local de 80 ms. A rota é a saída Android selecionada.
Ouvir voz e todos os tons nos Ray-Ban é a única prova de áudio físico.

## Queda/reconexão

1. Sem heartbeat por 4 s quando captura é esperada: `DEGRADED/CAPTURE_UNAVAILABLE`.
2. Parar análise, preservar timeline e emitir tom de queda.
3. Com frame válido, restaurar captura/áudio em geração válida e emitir tom de retorno.
4. Não concluir que a gravação nativa sobreviveu; isso é A3 e exige prova de mídia.

Provocar queda somente quando for recuperável e seguro. Se não houver forma segura, marcar
`INCONCLUSIVE`; não improvisar.
