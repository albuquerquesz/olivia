# 05 — Privacidade, bateria e evidências

## Nunca registrar

- frame, screenshot da câmera, vídeo, áudio bruto, transcrição ou URI privada;
- Application ID, token DAT, chave Picovoice, token App Check, senha ou credencial;
- prompts/respostas além de código e estado sanitizados.

## Registrar

- data/hora, operador, hardware/firmware, Moto, Meta AI, DAT, APK/commit e baterias;
- modo mock/físico, estados de registro/sessão, primeiro frame, inferências, tempos e erros;
- `PASS_HARDWARE`, `PASS_MOCK`, `NOT_SUPPORTED`, `INCONCLUSIVE` ou `PENDING`;
- limitação e fallback usado.

## Métricas honestas

0,5 fps e tons locais são estratégia de eficiência, não medição de bateria. Medir bateria e ao
menos 10 inferências reais com condições anotadas. Reportar p50/p95, tamanho de amostra e se o
áudio foi ouvido. Callback TTS não é latência acústica.

## Matriz do evento

| Item | Resultado | Evidência sanitizada | Limitação/fallback |
| --- | --- | --- | --- |
| DAT físico / primeiro frame | `PENDING` |  |  |
| voz/tons nos óculos | `PENDING` |  |  |
| A1 início/fim nativo | `PENDING` |  |  |
| A2 3K + DAT | `PENDING` |  |  |
| A3 queda Bluetooth | `PENDING` |  |  |
| A4 importação pós-stop | `PENDING` |  |  |
| A5 alinhamento | `PENDING` |  |  |
| IA/qualidade | `PENDING` |  |  |
| bateria/latência | `PENDING` |  |  |

Antes do checkpoint final, salvar APK físico, APK mock, commit/hash e log sanitizado em dois locais.
