# Achados na documentação oficial da Meta (DAT 0.9) sobre o delay de vídeo

Pesquisa feita em 18/09/2026 em `wearables.developer.meta.com/docs/reference` e pelo endpoint de
busca oficial (`mcp.developer.meta.com/wearables`, `search_dat_docs`), depois que subir o
`frameRate` de 2 até 30 não resolveu o travamento da preview.

## O achado decisivo

> "Resolution and frame rate are constrained by the **Bluetooth Classic** connection between the
> user's phone and their AI glasses. To manage limited bandwidth, an **automatic ladder** reduces
> quality as needed. It first lowers the resolution by one step (HIGH → MEDIUM). If bandwidth
> remains constrained, it then reduces the frame rate (30 → 24), **but never below 15 fps**."
>
> "The image delivered to your app may appear lower quality than expected, even when the
> resolution reports HIGH or MEDIUM. This is due to **per-frame compression that adapts to
> available Bluetooth Classic bandwidth**."

Isso muda a investigação de hoje em três pontos:

1. **O transporte é Bluetooth Classic, não BLE.** Eu vinha assumindo BLE (Bluetooth Low Energy) nas
   hipóteses anteriores — é um erro meu que essa documentação corrige. Bluetooth Classic tem banda
   maior que BLE, mas ainda é ordens de magnitude menor que Wi-Fi/USB.
2. **Existe uma escada automática de qualidade dentro do próprio SDK/firmware**, que já reage à
   banda disponível baixando resolução primeiro, depois fps (nunca abaixo de 15). Ou seja, o valor
   de `frameRate` que a gente pede em `StreamConfiguration` é só um **teto desejado**, não o valor
   entregue — o sistema pode (e provavelmente está) entregando menos do que pedimos, de forma
   automática, sem avisar o app.
3. **A compressão por frame também é adaptativa** — frames podem variar de tamanho conforme a banda
   disponível no instante, o que por si só já explica parte do padrão irregular ("rajada de 2,
   depois um gap grande") que vimos nos logs: nem todo frame custa o mesmo pra transmitir.

## Por que subir pra 30 não ajudou — explicado

Isso bate exatamente com o que observamos: pedimos 2 → 6 (inválido, fora da lista oficial) → 7 →
30, e nada mudou. Segundo a doc, os valores válidos são só `2, 7, 15, 24, 30`. Mas mesmo pedindo
30 (valor válido), se a banda Bluetooth Classic não sustenta isso, **a própria escada automática da
Meta reduz de volta** — provavelmente para os mesmos ~24fps que já vínhamos observando no
`pts_gap_ms≈41ms` dos logs, independente do que configuramos. Não é um bug nosso: é o sistema
protegendo a conexão, exatamente como documentado.

## Outros achados relevantes

- `StreamConfiguration`: `videoQuality` padrão é `MEDIUM`, não `HIGH` — nós sempre usamos `HIGH`
  (720×1280) explicitamente. `HIGH` empurra a demanda de banda pro topo da faixa antes mesmo de
  qualquer ladder entrar em ação.
- Resoluções confirmadas: `HIGH`=720×1280, `MEDIUM`=504×896, `LOW`=360×640.
- `Stream.videoStream`: a documentação diz que o overflow de buffer é tratado descartando os
  frames **mais antigos** ("dropping the oldest frames to ensure smooth streaming") — ou não deveria
  existir um backlog crescente sem limite no nível do SDK. Isso **é uma tensão real** com o que o
  meu diagnóstico (`capture_lag_ms`) mostrou hoje (~39 segundos de descompasso, ver
  `bateria.md`/conversa) — ver ressalva abaixo.
- `VideoFrame.presentationTimeUs`: documentado só como "presentation timestamp in microseconds" —
  **a doc não diz a qual relógio/época esse timestamp é relativo**. Isso é uma lacuna importante.
- A busca também retornou um trecho de telemetria interna da Meta que rastreia eventos
  "prepare started/completed" pro streaming de câmera — sugerindo que existe uma fase de
  "preparação" reconhecida oficialmente antes do stream ficar pronto, possivelmente relacionada ao
  gap de 12-16s que medimos entre `STREAMING` e o primeiro frame decodificado.

## Ressalva importante sobre o meu próprio diagnóstico

Como a Meta não documenta a época de `presentationTimeUs`, o `capture_lag_ms` de ~39s que medi
antes **pode não ser um backlog real** — pode ser só o offset arbitrário entre o instante em que o
contador interno do encoder come duas a contar e o instante em que eu capturei minha referência.
Não dá pra confirmar isso sem mais teste; estou sinalizando pra não superestimar essa métrica.

## Hipóteses revisadas, em ordem de confiança após esses achados

1. **(Reforçada) A banda Bluetooth Classic é o teto real**, e a escada automática da Meta já está
   decidindo a taxa entregue — pedir mais fps do nosso lado não tem efeito porque o sistema deles já
   está no limite e se auto-ajusta.
2. **(Nova) `VideoQuality.HIGH` pode estar empurrando a escada pra baixo o tempo todo** — se
   pedíssemos `MEDIUM` (o padrão recomendado pela própria Meta) ou `LOW`, a escada teria mais margem
   e talvez entregasse fps mais estável, ao custo de resolução menor.
3. **(Enfraquecida, não descartada) Backlog real de ~39s** — ainda possível, mas agora concorre com
   a explicação mais simples de offset de época não documentado no `presentationTimeUs`.
4. **(Mantida) O gap de 12-16s entre `STREAMING` e o primeiro frame** parece ser uma fase de
   "preparação" reconhecida pela própria Meta (telemetria interna cita "prepare started/completed"),
   não um bug nosso.

## Próximo passo recomendado

Testar explicitamente `VideoQuality.MEDIUM` (o padrão da própria Meta) em vez de `HIGH`, mantendo
`frameRate` num valor válido (ex.: 15 ou 24), e comparar `wall_gap_ms`/`pts_gap_ms` de novo. Se a
escada automática for mesmo o problema, isso deve estabilizar a entrega sem precisar mexer em mais
nada do nosso lado — é a mudança mais alinhada com o que a própria documentação recomenda.
