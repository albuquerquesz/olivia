# Frame / vídeo — checkpoint técnico 1

## Status: o Wearables Toolkit (pareamento/conexão) funciona bem; a entrega de vídeo em si ainda tem fila e engasgos

Separando dois níveis que ficaram misturados na investigação de hoje: **conectar o DAT** (registro,
sessão, câmera, stream) é uma coisa; **entregar o vídeo em cadência estável** é outra, bem mais
difícil, e é onde ainda estamos.

## O que já está pronto — o Toolkit em si funciona

- **Pareamento e registro do DAT**: `registration=REGISTERED`, `dev_mode=true`, device
  `CONNECTED/COMPATIBLE` — estável nas últimas sessões, sem precisar recadastrar App Check nem
  credencial de novo.
- **Ciclo de vida da sessão**: `session=IDLE→STARTING→STARTED`, `stream=STARTING→STREAMING`,
  reconecta corretamente depois de uma queda (`JobCancellationException` seguida de nova tentativa
  bem-sucedida em ~2s numa das sessões de hoje).
- **Primeiro frame real decodificado**: confirmado várias vezes, com preview acompanhando o
  movimento da câmera.
- **`PASS_HARDWARE` para câmera/preview** já registrado em
  [05-privacy-and-evidence.md](../05-privacy-and-evidence.md#L22-L34) e detalhado em
  [retomada-dat.md](retomada-dat.md).

Ou seja: o Wearables Device Access Toolkit da Meta, como produto de pareamento/streaming, funcionou
como devia. O problema não está em "conectar aos óculos".

## O que ainda não está bom — entrega do vídeo em cadência

- **Fila/engasgo observado ao longo do dia**: frames chegando em rajadas (dois frames com poucos
  ms de diferença, seguidos de um gap grande) em vez de espaçados uniformemente — sensação de
  preview "travando e depois pulando".
- **Atraso variável medido manualmente**: teste da mão na cara mostrou 3-5s entre o gesto real e o
  gesto aparecer na preview, instável entre repetições.
- **Causa raiz identificada, documentada em [meta-docs-findings.md](meta-docs-findings.md)**: o
  transporte é Bluetooth Classic (não Wi-Fi/USB), e a própria Meta documenta uma **escada
  automática de qualidade** que reduz resolução e depois fps conforme a banda disponível — o
  `frameRate`/`videoQuality` que pedimos é só um teto desejado, não o valor entregue.
- **Instrumentação evoluiu bastante hoje**: de um `frame_arrival` simples, para comparar
  `wall_gap_ms` (chegada real) vs `pts_gap_ms` (espaçamento no relógio do encoder), até um
  `backlog_ms` (via `SourceBacklog` no `:core`) que não depende de os dois relógios compartilharem
  época — essa é a métrica mais confiável que temos hoje.
- **Melhor resultado até agora**: com `VideoQuality.LOW`, vídeo não comprimido (`compressVideo`
  ligado por flag de diagnóstico) e `frameRate` variável (a escada da Meta decidiu ~7fps numa
  sessão), o `backlog_ms` caiu de ~1,4s pra **praticamente 0** em vários frames consecutivos — mas
  ainda não confirmamos que isso se mantém estável numa sessão longa, nem qual o trade-off real de
  qualidade de imagem pra ROI/Gemini nessa resolução mais baixa.
- **Hipótese ainda não testada**: contenção do próprio rádio Bluetooth do celular — vídeo entrando
  e áudio (TTS) saindo ao mesmo tempo, pelo mesmo rádio.

## O que falta

- Confirmar se o `backlog_ms` baixo se mantém numa sessão de vários minutos, não só nos primeiros
  segundos depois de uma mudança de config.
- Decidir o trade-off: `VideoQuality.LOW` (360×640) entrega estabilidade, mas é resolução bem menor
  — precisa confirmar que o Gemini/ROI ainda funcionam bem com frames nessa resolução.
- Testar a hipótese de contenção de Bluetooth (áudio + vídeo simultâneos).
- Gravação nativa 3K continua **fora do escopo desta investigação** — é bloqueio separado e
  documentado (`BLOCKED_BY_DAT_0_9_0`), não relacionado ao streaming de baixa resolução usado pra
  ROI/preview.

## Como demonstrar na banca

1. Mostrar o pareamento/conexão funcionando de forma confiável (isso está sólido).
2. Ser honesto sobre a preview: explicar que existe um atraso real, com causa raiz identificada
   (limite de banda Bluetooth Classic + escada automática da própria Meta), não um bug misterioso.
3. Se o `backlog_ms` baixo se confirmar estável até a hora do checkpoint, mostrar a preview em
   `LOW` como a versão mais fluida disponível hoje; se não, mostrar a versão mais alta qualidade e
   explicar a limitação com números reais, não estimativa.
