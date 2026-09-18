# Bloqueios para concluir a implementação executável

O código que independe de provedores e hardware avançou com mock offline, loop de sessão,
classificação Firebase isolada por flavor, importação local pós-`STOPPED`, timeline e contratos.
O objetivo do freeze de 17/09 é concluir tudo que pode ser preparado sem os óculos; as linhas H0
ficam como validação de troca de adapter no hackathon.

| Frente | Pré-condição necessária | Tasks bloqueadas | O que não será assumido |
| --- | --- | --- | --- |
| SH-01 administrativo | Resposta oficial da organização sobre pré-build | SH-01.1 | `can_do_before_hackathon` não é autorização por si só |
| DAT físico e capacidades nativas | pareamento dos óculos, Application ID/client token do canal físico e dispositivo compatível | BE-00.1–00.2, INT-04–06 | streaming Bluetooth físico, alto-falantes, gravação nativa ou 3K |
| Firebase AI Logic | `google-services.json` local, App Check, projeto/modelo autorizado e quota | BE-03.5, INT-03 | o smoke Android remoto passou; qualidade rotulada, retenção do provedor e limites de custo continuam sem evidência |
| Wake word e intents locais | access key e modelos Porcupine/Rhino pt-BR aprovados/localmente provisionados | FE-03.2 físico | reconhecimento por microfone dos óculos ou áudio enviado à nuvem |
| Whisper local | runtime Android definido, modelo multilíngue local com hash/licença e caminho provisionado | BE-08, BE-09, integração Whisper | transcrição remota, desempenho de 8 s ou cortes reais |
| Validação | autorização explícita para executar Gradle/testes e, para hardware, dispositivo pareado | critérios de aceite pendentes | sucesso de build/teste, bateria, rota de áudio ou comportamento de firmware |

Antes do hardware, a ordem é: congelar contratos, rodar mock/MockDeviceKit, habilitar a factory
Firebase no runtime `glasses`, fechar privacidade/cleanup, preparar importação/Whisper e deixar o
APK/roteiro/fallback prontos. Quando os óculos forem provisionados, a ordem H0 é: parear, conectar
o adapter DAT a `GlassesGateway`, validar frame/preview, áudio, vídeo nativo, queda Bluetooth,
importação e só então executar a matriz física BE-12.

O setup e o ciclo MockDeviceKit já foram executados no Moto G60 e repetidos na validação de
17/09/2026. O teste cobre frame raw portrait
(I420 convertido para NV21), queda de heartbeat, `DEGRADED`, recuperação, pausa/retomada e
`STOPPED`; portanto INT-00.1–00.4 não estão mais bloqueadas. O bloqueio restante é exclusivamente
a evidência física, a qualidade rotulada e o runtime/modelo Whisper listados acima. O harness
offline completo e o smoke Firebase Android de acesso/App Check passaram nesta rodada; isso não
fecha qualidade visual nem os cinco checkpoints. Os testes executados estão em
[VALIDATION_RUN_2026-09-17](evidence/VALIDATION_RUN_2026-09-17.md).
