# Firebase Android — configuração local

O Firebase atende somente a análise visual. Não há Firestore, login, upload de vídeo, backup ou
persistência de frames neste MVP.

## Arquivos e variantes

- `apps/android/app/src/glasses/google-services.json`: arquivo real do app Android
  `com.olivia.companion`; é ignorado pelo Git.
- `apps/android/app/src/mock/google-services.json`: configuração inerte para o package mock;
  não habilita Firebase AI nem faz chamadas de rede.
- O plugin `com.google.gms.google-services` processa o arquivo específico de cada flavor.

## Console Firebase / Google Cloud

1. No Firebase, registre o Android `com.olivia.companion` e baixe o arquivo para o caminho acima.
2. No Firebase AI Logic, habilite o provedor/modelo autorizado e defina quota de desenvolvimento.
3. Em App Check, registre o certificado SHA-256 usado pelo APK de teste. Para distribuição fora da
   Play Store, configure Play Integrity de acordo com o canal de teste e monitore antes de exigir
   enforcement.
4. Restrinja a API key ao package e certificado corretos no Google Cloud. Não transforme a key,
   App Check ou `google-services.json` em segredo de usuário; tokens administrativos e debug
   continuam fora do repositório.

O APK `release` instala Play Integrity antes de criar o adapter Firebase AI. O APK `debug` usa o
Debug provider exclusivamente para desenvolvimento local. Se configuração, App Check, quota ou
modelo faltarem, o adapter falha com erro sanitizado; nunca troca silenciosamente para mock nem
envia mídia para outro serviço.

## Probe no emulador

Para o probe de instrumentação, crie um token em **App Check → Apps → Olivia → Manage debug
tokens** e salve-o no `.env` ignorado pelo Git como `FIREBASE_APP_CHECK_DEBUG_TOKEN=...`. O
`DebugAppCheckTestHelper` recebe esse token pelo runner e não depende de um UUID dinâmico do
logcat. O token não entra no código, `local.properties`, BuildConfig ou APK de produção.

Salve as três configurações abaixo no `.env` da raiz e execute:

```env
OLIVIA_LIVE_AI_ENABLED=true
OLIVIA_AI_MODEL=gemini-3.5-flash-lite
FIREBASE_APP_CHECK_DEBUG_TOKEN=seu-token-de-debug
```

```powershell
& .\gradlew.bat ':app:connectedGlassesDebugAndroidTest' `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.olivia.companion.infra.FirebaseAccessIntegrationTest'
```

O token de debug é um segredo de acesso ao ambiente de desenvolvimento: revogue-o ao concluir o
teste ou imediatamente se ele for exposto.

### Token local no `.env`

Você pode salvar `OLIVIA_LIVE_AI_ENABLED`, `OLIVIA_AI_MODEL` e
`FIREBASE_APP_CHECK_DEBUG_TOKEN` no `.env` da raiz, que já é ignorado por Git. O Gradle lê somente
essas três chaves para o runner de instrumentação; elas não entram em BuildConfig, recursos, APK ou
release. Variáveis de ambiente ainda têm precedência se você quiser sobrescrever temporariamente um
valor.

## Gate ROI ao vivo

Após o probe de acesso ficar verde, repita o mesmo comando trocando a classe por
`com.olivia.companion.ai.FirebaseRoiIntegrationTest`. Ele gera um JPEG sintético somente em
memória, usa o mesmo `FirebaseGeminiVisualTransport` do APK e passa somente se a resposta obedecer
ao schema `WorkAreaLocalization` 2.0. O probe aceita até 30 s para uma conexão Firebase fria; o
loop de gravação permanece com timeout de 2 s. Enquadramento, luz e horizonte são calculados localmente;
direção, graus ou fala do provedor são rejeitados.

Esse gate comprova acesso e contrato, não qualidade visual. A avaliação rotulada com fixtures
autorizadas continua como BE-03.5 e deve registrar apenas métricas sanitizadas.

### Decomposição pré-TTFT

Para separar primeira chamada fria, texto sem imagem, custo visual por resolução e chamadas
quentes, execute o probe de doze requisições:

```powershell
adb logcat -c
& .\gradlew.bat ':app:connectedGlassesDebugAndroidTest' `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.olivia.companion.ai.FirebasePreTtftBreakdownTest' `
  --console=plain --no-daemon --no-configuration-cache
adb logcat -d -v brief 'OliviaBE07Breakdown:I' '*:S'
```

O teste exige `gemini-3.5-flash-lite` e imprime apenas durações agregadas, bytes dos JPEGs,
contagem de tokens e códigos de falha. A primeira chamada é marcada como `cold` no nível da
sessão; as demais reutilizam o mesmo modelo e são `hot`. A comparação direta com a Gemini
Developer API e a região Agent Platform ficam explicitamente como `SKIPPED` até haver provider
habilitado e credencial descartável fora do repositório. O resultado observado está em
[`BE07_PRE_TTFT_BREAKDOWN_2026-09-16.md`](spec-driven-development/10-medicao-e-evidencias/BE07_PRE_TTFT_BREAKDOWN_2026-09-16.md).

### Probe de streaming/TTFT

Para medir o tempo até o primeiro chunk e até a conclusão sem alterar o loop de produção,
execute no mesmo aparelho físico:

```powershell
$env:OLIVIA_AI_MODEL = 'gemini-3.5-flash-lite'
& .\gradlew.bat ':app:connectedGlassesDebugAndroidTest' `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.olivia.companion.ai.FirebaseStreamTimingMeasurementTest'
```

O log `OliviaBE07Stream` publica somente p50/p95 de TTFT, conclusão, quantidade de chunks e
falhas. Chunks parciais nunca são usados para falar ou orientar; a validação completa do JSON
continua obrigatória.

## Limite de evidência

Compilar ou declarar dependências não prova acesso ao Gemini, retenção do provedor, qualidade,
latência ou enforcement. Rodar o teste Android live controlado continua pendente de autorização e
configuração externa.
