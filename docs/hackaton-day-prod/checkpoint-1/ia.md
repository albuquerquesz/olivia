# IA — checkpoint técnico 1

## Status: funcionando ponta a ponta; meta de latência ainda não atingida

Hoje, por várias sessões físicas, o pipeline completo rodou de verdade: frame real do Ray-Ban →
Gemini localiza a área de trabalho → rastreamento local (optical flow) acompanha entre respostas →
correção de enquadramento → áudio nos óculos.

## O que já está pronto

- **Gemini respondendo com frame real**: múltiplas chamadas completas medidas hoje —
  `SDK_ROUND_TRIP` de 1,85s a 4,24s em amostras diferentes (tag `OliviaVisualTiming`).
- **App Check confirmado funcionando no físico**: `APP_CHECK_READY` observado no log; o token de
  debug cadastrado no Console está válido para esta instalação.
- **Rastreamento local (optical flow) disparando correções de forma consistente**: em mais de uma
  sessão, várias `correction_dispatch source=TRACKER_LOCAL` com frame bem fresco (68-132ms de
  idade) — é esse caminho rápido que está efetivamente guiando a pessoa em tempo real entre uma
  resposta e outra do Gemini, não só a chamada lenta.
- **Confirmação positiva de enquadramento implementada e testada**: `SessionSignal.TARGET_FRAMED`,
  tom próprio de 740Hz, dispara só depois de um desvio real (com cooldown de 4s) — cobre a lacuna
  que não existia até hoje de manhã ("só corrige, nunca confirma que está certo").
- **Áudio confirmado nos Ray-Ban**: o operador ouviu a correção ("vire para a direita"/"para baixo")
  saindo dos óculos, não só do celular.
- **Fallback automático caminho lento → caminho rápido** funcionando: quando o Gemini demora ou dá
  timeout, o rastreamento local continua operando com a última ROI conhecida, sem ficar mudo.

## O que falta

- **Meta de ~900ms end-to-end (frame → áudio) não atingida**: as amostras reais medidas hoje
  (1,85s-4,24s de round-trip do Gemini) ficam bem acima disso. O caminho rápido em si já responde
  em ~100-500ms quando engatado, mas depende de haver uma ROI local ativa vinda de uma resposta
  recente do Gemini.
- **Amostra insuficiente para p50/p95 declarado**: menos de 10 inferências completas medidas até
  agora — ver `bateria.md`.
- **Timeouts intermitentes do Gemini**: pelo menos um `visual analysis unavailable: TIMEOUT` por
  sessão longa, geralmente contornado pelo caminho rápido continuando a falar.

## Como demonstrar na banca

1. Rodar uma sessão real, deixar a Olivia localizar a peça (Gemini) e então se desviar do
   enquadramento — mostrar a correção saindo pelos óculos.
2. Voltar ao enquadramento correto e deixar o tom `TARGET_FRAMED` confirmar.
3. Ser honesto sobre a latência: dizer que o pipeline funciona ponta a ponta, mas a meta de 900ms
   ainda está em ajuste — não arredondar os números medidos.
