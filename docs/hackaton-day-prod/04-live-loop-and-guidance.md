# 04 — Loop vivo, IA, voz e timeline

## Configuração fixa

| Parâmetro | Valor |
| --- | --- |
| frame real esperado | 720×1280; registrar divergência |
| amostragem | 0,5 fps / 2 s |
| timeout de captura | 4 s |
| cooldown | 4 s |
| saída | TTS pt-BR ou tom local |

## Pipeline

`frame efêmero → sampler → ROI Gemini → análise local → guidance → áudio → confirmação → timeline`

Gemini devolve somente ROI/confiança. O Android calcula enquadramento, luminância e horizonte.
Não pedir/apresentar graus físicos, severidade da nuvem ou causa de sombra sem detector validado.

## Estados

| Estado | Regra |
| --- | --- |
| `STARTED` | captura é aceita e análise ocorre por intervalo |
| `PAUSED` | capacidade preservada; ausência de frame é intencional |
| `DEGRADED` | causa tipada; não confundir com silêncio saudável |
| `STOPPED` | encerra loop e habilita pós-take |

Falha de IA preserva captura/timeline. Falha de captura interrompe análise. Preview e bytes de frame
só vivem em memória.

## Ensaio

Confirmar comando local, aplicar uma correção observável, ouvir o feedback, verificar confirmação
posterior e encerrar. Se a IA expirar, registrar análise indisponível e continuar apenas os testes
de captura/áudio sem inventar correções.
