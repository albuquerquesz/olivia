# BE-08 - evidencia Whisper local em Docker

Data: `2026-09-17`<br>
Resultado: `PASS_DOCKER`

## Escopo

Foi provisionado um modelo Whisper multilingue fora do repositorio e um fixture sintetico de
fala em portugues. O perfil Docker executou sem rede, com modelo e fixture montados somente para
leitura, e validou integridade SHA-256 antes de transcrever.

## Artefatos externos

- Modelo: `G:\olivia-local\whisper\ggml-base.bin`
- SHA-256 do modelo: `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe`
- Fixture: `G:\olivia-local\whisper\olivia-whisper-fixture.wav`
- SHA-256 do fixture: `ae67949bbc2def8a0db0121fab4b843efda0a74c1ace7370d208e985aa34239f`
- Imagem: `testing-whisper`, digest `sha256:6dcf5835ec74615cdaba7a86e6b9f3f87fa69abaad5f323fb130537263e14e8e`

## Resultado

O runner retornou apenas:

```json
{"suite":"whisper","status":"passed"}
```

Foram validados hash, transcricao nao vazia, timestamps monotonicos e schema. Nenhum texto
transcrito foi registrado.

## Limite honesto

Esta evidencia fecha o harness local e o contrato de seguranca do runner. Ela nao prova ainda
que o Whisper esta empacotado como runtime Android, nem sua latencia, consumo de bateria ou
qualidade no telefone. No APK, o caminho atual mantem `UnavailableLocalTranscriptProvider` e o
fallback marks-only ate essa integracao ser implementada/medida.
