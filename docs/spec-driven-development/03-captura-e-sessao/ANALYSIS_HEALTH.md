# Saúde da análise no loop ao vivo

Uma falha do classificador não é um frame sem desvio. A primeira falha por geração ativa adiciona
`ANALYSIS_UNAVAILABLE`, cria um evento seguro de degradação e pede o sinal de análise indisponível.
Não há resposta textual do modelo, bytes do frame ou detalhe de provedor na timeline.

Enquanto a captura continuar saudável, o sampler continua aceitando frames em `DEGRADED` causado
apenas por análise ou áudio. Uma análise posterior bem-sucedida é a única evidência que remove
`ANALYSIS_UNAVAILABLE` e emite `ANALYSIS_RESTORED`; repetição de falha não produz spam. Se a causa
for `CAPTURE_UNAVAILABLE`, a fence foi invalidada e nenhum frame é aceito até a recuperação da
captura abrir uma geração válida.

Status: lógica, testes JVM e validação MockDeviceKit executados em 17/09/2026. Firebase de qualidade,
microfone físico e hardware DAT continuam fora desta evidência.
