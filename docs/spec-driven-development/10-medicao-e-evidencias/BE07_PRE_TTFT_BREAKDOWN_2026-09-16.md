# BE-07 — decomposicao pre-TTFT no Moto G60 (16/09/2026)

Probe diagnostico separado do loop de producao, mantendo exclusivamente o
`gemini-3.5-flash-lite`, o mesmo projeto Firebase, schema, prompt, App Check e
`ThinkingLevel.MINIMAL`. Nenhum frame, resposta, token de acesso ou identificador
do aparelho e persistido; o teste imprime somente este agregado allowlisted.

## Desenho da campanha

Foram feitas 12 chamadas para respeitar o orcamento local:

- uma chamada `countTokens` somente com texto; ela e a primeira requisicao da sessao e fica marcada como fria;
- uma chamada `countTokens` por JPEG de 256, 512 e 1024 px;
- duas requisicoes `generateContentStream` somente com texto;
- duas requisicoes `generateContentStream` por cada uma das tres resolucoes de imagem.

As chamadas seguintes reutilizam a mesma instancia do modelo e sao marcadas como quentes. Isso separa o custo inicial do processo (App Check/TLS/DNS) apenas no nivel da sessao; nao e uma afirmacao sobre cold start no servidor. Percentis de uma ou duas amostras sao descritivos, nao uma cauda estatistica robusta.

## Resultado observado

O teste `FirebasePreTtftBreakdownTest` terminou com codigo 0 no Moto G60, com 12/12 chamadas bem-sucedidas:

```text
model=gemini-3.5-flash-lite requests=12 successes=12 failures=0
first_request_cold_ms=2765
count_text_ms=n=1,p50=2765,p95=2765 count_text_tokens=n=1,p50=10,p95=10
count_image_256_ms=n=1,p50=728,p95=728 count_image_256_bytes=n=1,p50=2081,p95=2081 count_image_256_tokens=n=1,p50=1277,p95=1277
count_image_512_ms=n=1,p50=668,p95=668 count_image_512_bytes=n=1,p50=3635,p95=3635 count_image_512_tokens=n=1,p50=1277,p95=1277
count_image_1024_ms=n=1,p50=686,p95=686 count_image_1024_bytes=n=1,p50=7713,p95=7713 count_image_1024_tokens=n=1,p50=1277,p95=1277
stream_text_ttft_ms=n=2,p50=1203,p95=1270 stream_text_completion_ms=n=2,p50=1204,p95=1271
stream_image_256_bytes=n=2,p50=2081,p95=2081 stream_image_256_ttft_ms=n=2,p50=1689,p95=2528 stream_image_256_completion_ms=n=2,p50=1691,p95=2630
stream_image_512_bytes=n=2,p50=3635,p95=3635 stream_image_512_ttft_ms=n=2,p50=2068,p95=2669 stream_image_512_completion_ms=n=2,p50=2228,p95=2719
stream_image_1024_bytes=n=2,p50=7713,p95=7713 stream_image_1024_ttft_ms=n=2,p50=1966,p95=3103 stream_image_1024_completion_ms=n=2,p50=2128,p95=3298
direct_api=SKIPPED_NO_EPHEMERAL_KEY agent_platform=SKIPPED_PROVIDER_NOT_ENABLED
```

## Interpretacao

1. O `countTokens` de texto frio levou 2.765 ms. Ele inclui o primeiro custo de cliente/provedor e nao deve ser tratado como RTT puro nem subtraido dos outros tempos.
2. O request somente de texto teve TTFT mediano de 1.203 s. Com imagem, o TTFT mediano ficou entre 1.689 s (256 px) e 2.068 s (512 px); 1024 px ficou em 1.966 s. Isso confirma custo visual antes do primeiro chunk, mas nao separa upload de fila/prefill do servico.
3. O JPEG cresceu de 2.081 para 7.713 bytes, enquanto `countTokens` retornou 1.277 tokens nas tres resolucoes. Bytes e tokens nao escalaram proporcionalmente nesta amostra; o TTFT tambem nao foi monotonicamente crescente. Nao ha base para promover 256 px sem campanha maior de qualidade e latencia.
4. A conclusao do stream ficou proxima do TTFT: o sufixo de geracao foi de aproximadamente 1 ms no texto e 0,1–0,2 s nas imagens. A cauda continua concentrada antes do primeiro chunk; reduzir nomes do JSON ou tocar resposta parcial nao e a alavanca principal.

## Limites e proximos probes

As comparacoes **Firebase versus Gemini Developer API direto** e **Agent Platform em `southamerica-east1`** nao foram executadas. Nao existe chave Developer descartavel configurada e o provider Agent Platform esta desabilitado no projeto. O teste registra esses estados explicitamente em vez de aceitar segredo em codigo, APK, log ou argumento do runner.

Para executar essas duas hipoteses depois, criar uma campanha separada com quota e segredo temporarios, sem alterar o caminho de producao (Firebase AI Logic). Repetir pelo menos 10–20 amostras por condicao e manter o mesmo frame/prompt/modelo; so entao comparar com esta rodada.
