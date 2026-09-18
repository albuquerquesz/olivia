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
| DAT físico / primeiro frame | `PASS_HARDWARE` | Frames `DAT NV21 720×1280` às 12:22 e 12:24; preview acompanhando movimentos confirmado pelo operador | Atraso visual não medido; não prova vídeo nativo, ROI ou áudio acústico |
| voz/tons nos óculos | `PENDING` |  |  |
| A1 início/fim nativo | `PENDING` |  |  |
| A2 3K + DAT | `PENDING` |  |  |
| A3 queda Bluetooth | `PENDING` |  |  |
| A4 importação pós-stop | `PENDING` |  |  |
| A5 alinhamento | `PENDING` |  |  |
| IA/qualidade | `PENDING` |  |  |
| bateria/latência | `PENDING` |  |  |

## Atualização operacional — 18/09/2026

O APK `glassesDebug` foi recompilado com `MWDAT_MOCK_DEVICE_KIT=false` e instalado no Moto G60.
O wake word e `START_RECORDING` foram reconhecidos localmente, e o gateway físico chegou ao DAT.
O preflight observou `registration=AVAILABLE` e `devices=0`; a tentativa de autorização abriu o Meta AI,
mas a integração falhou por `Application is not authorized to perform this operation. Attestation failed.`
Ainda não há evidência suficiente para marcar `PASS_HARDWARE` sem observar `STARTED` e o primeiro frame.
O adapter registra apenas estado agregado do preflight e `origin`, `format`, `width` e `height` do primeiro
frame, sem pixels ou mídia.

Após a falha de attestation, o `glassesDebug` foi separado corretamente para Developer Mode (`0/0`),
sem MockDeviceKit. A próxima evidência necessária é `registration=REGISTERED`, device elegível,
`STARTED` e `first_frame origin=DAT` com Developer Mode ativado no Meta AI.

Antes do checkpoint final, salvar APK físico, APK mock, commit/hash e log sanitizado em dois locais.

Retomada às 12:14–12:18: o bloqueio inicial de registro foi superado. `APP_CHECK_READY` observado.
Evidência atual e separação do histórico em [retomada DAT](checkpoint-1/retomada-dat.md).
