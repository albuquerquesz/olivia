# Primeira entrega — transporte Firebase para classificação visual

Status: adapter Android e bootstrap do Firebase no flavor glasses escritos; integração DAT/runtime e validação ao vivo pendentes.

## Núcleo já entregue

`VisualAnalysisSchema` aceita somente a versão `1.0` e até um diagnóstico por classe fechada:
`FRAMING`, `LIGHT` e `HORIZON`. Ele rejeita campos ausentes ou desconhecidos, texto livre,
duplicatas, confiança inválida, direção incompatível e qualquer magnitude, pois ainda não há
calibração verificada dos óculos. `GeminiFrameAnalyzer` acrescenta timeout de 2 s, sem retry do
mesmo frame, erros sem conteúdo e propagação correta de cancelamento.

## Adapter Android

`FirebaseGeminiVisualTransport` fica exclusivamente em `app/src/glasses`. Ele recebe um
`FirebaseApp` já configurado pelo ambiente local/CI, cria o cliente Firebase AI Logic e implementa
`GeminiVisualTransport` sem fazer o domínio `:core` importar Android ou Firebase.

O adapter decodifica JPEG, NV21 ou RGBA apenas em memória, reduz o maior lado para 512 px e envia
um único bitmap com um prompt limitado a enquadramento, luz/sombra e horizonte. A resposta pede
JSON; `GeminiFrameAnalyzer` e `VisualAnalysisSchema` ainda são a barreira definitiva para o
contrato fechado, timeout de 2 s e rejeição de texto/propriedades inesperadas.

Não há cache, arquivo, log ou retry do frame. Cancelamento de coroutine propaga. Demais falhas se
reduzem a um erro sanitizado, que o núcleo converte em indisponibilidade de análise. O bitmap é
liberado depois da chamada.

Ainda não há uma instância Firebase configurada no runtime `glasses`, nem frame DAT ligado a esse
adapter. Isso depende de `google-services.json` local, App Check, quotas/modelo aprovados e
INT-00. O mock continua deliberadamente sem Firebase. Nenhum teste Android/Firebase foi executado
nesta entrega.
