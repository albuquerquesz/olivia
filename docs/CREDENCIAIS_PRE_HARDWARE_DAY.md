# Credenciais e configuração pré-hardware day

Objetivo: deixar o máximo possível do Olivia executável e verificável localmente. No
hardware day, o trabalho deve se limitar a trocar os adaptadores mock pelos adaptadores
DAT e a colher evidência real de dispositivo — não a criar o produto do zero.

> Nunca cole segredos no chat, em código-fonte, em `gradle.properties` versionado ou em
> qualquer arquivo que entre no Git. Use `apps/android/local.properties` (ignorado) e
> variáveis de ambiente/segredos do CI.

## Situação atual

- `apps/android/google-services.json` existe, está ignorado pelo Git e contém uma
  configuração Android para `com.olivia.companion`.
- Ele ainda **não ativa Firebase no runtime**: a integração de Firebase AI Logic e o
  plugin Google Services ainda precisam ser conectados ao módulo `app`.
- O APK mock, o loop sintético de frames, a timeline e o áudio local já permitem a
  demonstração offline. Eles não comprovam DAT, Bluetooth, Gemini, Porcupine, áudio
  nos óculos, vídeo nativo ou bateria.

## 1. Firebase AI Logic / Gemini

### Providenciar

- Acesso administrativo ao projeto Firebase já associado ao `google-services.json`.
- Firebase AI Logic configurado, com o provider e modelo Gemini que serão usados no
  loop visual.
- Firebase App Check habilitado e um token de debug para desenvolvimento local e
  emulador.
- Registro Firebase para ambos os application IDs, caso o flavor mock também vá usar
  IA real:
  - `com.olivia.companion` (glasses/base);
  - `com.olivia.companion.mock` (mock).
- Restrições da API key para o package Android e o certificado SHA-1 usados no teste.

### Valores locais necessários

Definir em ambiente local ou no cofre de segredos do CI:

```text
OLIVIA_LIVE_AI_ENABLED=true
OLIVIA_FIREBASE_PROJECT_ID=<project-id>
OLIVIA_FIREBASE_APP_ID=<android-app-id>
OLIVIA_FIREBASE_API_KEY=<api-key-restrita>
OLIVIA_AI_MODEL=<modelo-aprovado>
FIREBASE_APP_CHECK_DEBUG_TOKEN=<debug-token>
```

Os valores de projeto, app ID e API key podem ser lidos do arquivo de configuração
Firebase; não devem ser duplicados no código. Não é necessário fornecer service account
nem uma chave Gemini avulsa ao aplicativo.

### Arquivo local

Ao conectar o plugin Google Services, disponibilizar também o arquivo ignorado em:

```text
apps/android/app/google-services.json
```

O arquivo existente em `apps/android/google-services.json` não deve ser movido ou
versionado sem ajustar a integração Gradle correspondente.

## 2. Meta Wearables Device Access Toolkit (DAT)

### Providenciar

- GitHub Personal Access Token **classic** com escopo `read:packages`, para baixar os
  artefatos privados do DAT.
- `APPLICATION_ID` emitido no Wearables Developer Center.
- Acesso da equipe à organização, projeto e release channel liberados pela Meta.
- Confirmar no onboarding se a versão atual do DAT exige também um `CLIENT_TOKEN` e,
  se exigir, recebê-lo pelo canal oficial.

### Valores locais necessários

Em `apps/android/local.properties` (arquivo ignorado):

```properties
github_token=<github-classic-pat-read-packages>
mwdat_application_id=<wearables-developer-center-application-id>
# Preencher somente se a documentação/SDK atual exigir:
mwdat_client_token=<client-token>
```

Alternativamente, `GITHUB_TOKEN` pode ser fornecido por variável de ambiente/segredo de
CI. O MVP usa somente `mwdat-core`, `mwdat-camera` e `mwdat-mockdevice`; não usar
`mwdat-display`, pois os Ray-Ban não oferecem UI visual ao usuário.

## 3. Wake word local (Porcupine)

### Providenciar

- Conta Picovoice e `AccessKey`.
- Modelo Android do wake word personalizado “Oi Olivia” (`.ppn`).
- Modelo de linguagem Porcupine PT-BR (`.pv`) compatível com a versão escolhida do SDK.

### Valores/arquivos locais necessários

```text
PICOVOICE_ACCESS_KEY=<access-key>
<assets locais>/oi_olivia_android.ppn
<assets locais>/porcupine_pt_br.pv
```

O wake word e os comandos são processados no microfone do **celular**. Nenhum áudio
bruto do microfone alimenta a IA durante o take. Os modelos devem ficar fora do Git
até que a licença e a forma de distribuição estejam definidas.

## 4. Pós-processamento local

Não há credencial obrigatória para o fluxo offline. A baseline aprovada é `whisper.cpp` com o
modelo multilíngue `ggml-base.bin`; `ggml-tiny.bin` multilíngue fica como fallback caso o benchmark
no Android não feche o tempo aceitável. Variantes `.en` não servem à demo pt-BR.

Baixar o modelo para diretório local fora do Git, registrar origem, SHA-256 e licença e configurar:

```text
OLIVIA_WHISPER_MODEL_PATH=<caminho-local>/ggml-base.bin
OLIVIA_WHISPER_FALLBACK_MODEL_PATH=<caminho-local>/ggml-tiny.bin
OLIVIA_WHISPER_LANGUAGE=pt
```

Isso não entra no loop ao vivo e não deve bloquear o frame → IA → áudio.

## O que fica para o hardware day

Não são credenciais, mas dependem do evento:

- Ray-Ban Meta em Developer Mode, pareado ao Android de teste e autorizado na conta/
  projeto liberados pela organização.
- Firmware e capacidades reais confirmados no dispositivo.
- Verificação independente de frame 720×1280, áudio audível nos óculos, gravação nativa,
  preservação após queda Bluetooth e importação pós-`STOPPED`.

MockDeviceKit e o APK mock servem de fallback demonstrável, claramente identificado
como mock; não devem ser apresentados como prova de hardware.

## Entrega segura amanhã

1. Mantenha `google-services.json` local; não envie seu conteúdo por mensagem.
2. Crie/preencha `apps/android/local.properties` com DAT, sem adicioná-lo ao Git.
3. Guarde Firebase/App Check e Picovoice como variáveis de ambiente ou segredos do CI.
4. Compartilhe apenas a confirmação de que cada item foi provisionado; depois conectamos
   os adaptadores reais e executamos os testes de emulador apropriados.

## Referências

- `docs/rules/meta_wearables_dat_android.json`
- `docs/rules/hackaton-rules.json`
- `docs/HARDWARE_DAY_RUNBOOK.md`
- [Firebase AI Logic para Android](https://firebase.google.com/docs/ai-logic/get-started?platform=android)
- [Firebase App Check](https://firebase.google.com/docs/ai-logic/app-check)
- [Meta Wearables DAT Android](https://github.com/facebook/meta-wearables-dat-android)
- [Porcupine Android](https://picovoice.ai/docs/quick-start/porcupine-android/)
