# Roteiro manual — microfone do celular e comandos locais

Objetivo: deixar o ensaio de voz pronto para amanhã sem alegar validação do microfone dos óculos.
O áudio de comando é local, não entra no Firebase e não é escrito em arquivo.

## Preparação

1. Instalar o APK `glasses` no Android de teste.
2. Conceder somente a permissão de microfone do aplicativo quando solicitada.
3. Deixar o áudio de saída no Modo Voz; repetir no Modo Sinal se o áudio estiver indisponível.
4. Confirmar que o dispositivo DAT está pareado ou usar o APK mock explicitamente identificado.
5. Iniciar a captura de logs somente com filtros sanitizados; não usar `logcat` bruto como evidência.

## Roteiro de aceitação

| Passo | Fala no microfone do celular | Observação esperada |
|---:|---|---|
| 1 | `Oi, Olivia, gravar` | `STARTING → STARTED`, tom de início e primeiro frame/preview |
| 2 | `Oi, Olivia, pausar` | `PAUSED`, preview limpo e tom de pausa |
| 3 | `Oi, Olivia, retomar` | nova geração de captura, preview volta e tom de retomada |
| 4 | `Oi, Olivia, terminar` | `STOPPING → STOPPED`, tom de stop e nenhum processamento antes do stop |
| 5 | comando fora do estado, se aplicável | erro sanitizado e nenhum crash/transição inválida |

## Evidência permitida

Registrar somente: variante, aparelho, permissão concedida/negada, estado antes/depois, contagem
de comandos, sucesso/falha sanitizado e horário. Não registrar áudio PCM, transcrição do comando,
frases de conteúdo, frames, URIs privadas ou tokens.

## Falhas e fallback

- Sem permissão: registrar `VOICE_INPUT_UNAVAILABLE`; não enviar áudio para provider.
- Sem modelos Porcupine/Rhino: usar Modo Sinal ou controles de emergência previstos no APK mock;
  não declarar hands-free real concluído.
- Microfone dos óculos: não usar como substituto. O DAT não é a entrada de voz do produto.

