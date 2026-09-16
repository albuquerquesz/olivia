# Bloqueios para concluir a implementação executável

O código que independe de provedores e hardware avançou com mock offline, loop de sessão,
classificação Firebase isolada por flavor, importação local pós-`STOPPED`, timeline e contratos.
As entregas abaixo não podem ser concluídas de modo honesto sem configuração externa.

| Frente | Pré-condição necessária | Tasks bloqueadas | O que não será assumido |
| --- | --- | --- | --- |
| DAT e MockDeviceKit | artefatos Maven Central, app registrado no Wearables Developer Center e, fora do Developer Mode, Application ID/client token locais | INT-00.2–00.4, BE-00.1–00.2, INT-04–06 | API, formato de callback, streaming físico, gravação nativa ou 3K |
| Firebase AI Logic | `google-services.json` local, App Check, projeto/modelo autorizado e quota | BE-03.4–03.5, INT-03 | acesso real, retenção do provedor, latência ou qualidade do modelo |
| Wake word Porcupine | access key e modelos pt-BR aprovados/localmente provisionados | FE-03.1–03.2 | reconhecimento por microfone dos óculos ou áudio enviado à nuvem |
| Whisper local | runtime Android definido, modelo multilíngue local com hash/licença e caminho provisionado | BE-08, BE-09, integração Whisper | transcrição remota, desempenho de 8 s ou cortes reais |
| Validação | autorização explícita para executar Gradle/testes e, para hardware, dispositivo pareado | critérios de aceite pendentes | sucesso de build/teste, bateria, rota de áudio ou comportamento de firmware |

Quando os itens forem provisionados, a ordem é: resolver/rodar `mock` e MockDeviceKit, conectar
o adapter DAT a `GlassesGateway`, habilitar a factory Firebase no runtime `glasses`, rodar o teste
Android live controlado, validar importação de take e só então executar a matriz física BE-12.
