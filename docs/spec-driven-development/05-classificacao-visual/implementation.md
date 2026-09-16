# Entrega atual — localização da peça via Firebase e diagnóstico visual local

Status: contrato ROI, adapter Android, visão local e testes JVM implementados. O gate Firebase live de schema passou em AVD; integração DAT/runtime e avaliação rotulada de qualidade seguem pendentes.

## Núcleo já entregue

`WorkAreaLocalizationSchema` aceita somente a versão `2.0`: `targetPresent`, `confidence` e uma
ROI normalizada (`x`, `y`, `width`, `height`) ou `null` quando a peça não está identificável. Ele
rejeita texto livre, campos extras, direção, severidade, magnitude, diagnósticos, números inválidos
e ROIs fora do frame. `GeminiFrameAnalyzer` acrescenta timeout de 2 s, sem retry do mesmo frame,
erros sem conteúdo e propagação correta de cancelamento.

`DeterministicLocalVisualAnalyzer` é o único componente que produz `FRAMING`, `LIGHT` e
`HORIZON`: compara o centro da ROI com o frame, mede luminância da ROI contra a referência do
frame e estima a inclinação de linhas horizontais com confiança. Até 10% de offset fica silencioso;
10–25% vira correção `MINOR` (“um pouco”); acima de 25% vira `MAJOR`. Não há ângulo físico nem
atribuição de sombra à mão. Em baixa confiança o analisador se abstém.

## Adapter Android

`FirebaseGeminiVisualTransport` fica exclusivamente em `app/src/glasses`. Ele recebe um
`FirebaseApp` já configurado pelo ambiente local/CI, cria o cliente Firebase AI Logic e implementa
`GeminiVisualTransport` sem fazer o domínio `:core` importar Android ou Firebase.

O adapter decodifica JPEG, NV21 ou RGBA apenas em memória, reduz o maior lado para 512 px e envia
um único bitmap com prompt limitado a localizar a área de trabalho. Além do prompt, a geração usa
`responseSchema` nativo do Firebase AI Logic para o envelope `WorkAreaLocalization` 2.0; o core
continua validando a ROI e o Android produz uma cópia grayscale efêmera para o cálculo local. O frame, bitmap
e luma são liberados ao final da chamada; nenhuma métrica, ROI ou byte segue para timeline/log.

Não há cache, arquivo, log ou retry do frame. Cancelamento de coroutine propaga. Demais falhas se
reduzem a um erro sanitizado, que o núcleo converte em indisponibilidade de análise. O bitmap é
liberado depois da chamada.

O `FirebaseVisualAnalyzerFactory` injeta o decoder Android e o analisador local somente no flavor
`glasses`. O mock continua deliberadamente sem Firebase. Acesso Firebase, frame DAT e validação de
qualidade ao vivo continuam gates separados; testes JVM não são evidência de hardware ou acurácia.
