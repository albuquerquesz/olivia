# Cartão de onboarding — build `glasses`

Este cartão é para conferir a configuração no celular/notebook no início do hackathon. Ele lista
somente nomes de arquivos e chaves; nunca copiar valores para a evidência, pitch ou chat.

## Arquivos locais

| Item | Local esperado | Uso | Pode ir para Git/evidência? |
|---|---|---|---|
| DAT local | `apps/android/local.properties` | Application ID, client token e flag do MockDeviceKit | Não |
| Firebase Android | `apps/android/app/src/glasses/google-services.json` | projeto/app público do flavor `glasses` | Não |
| Firebase mock | `apps/android/app/src/mock/google-services.json` | projeto/app público do flavor `mock` | Não |
| Wake word/Rhino | `apps/android/app/src/main/assets/` | modelos licenciados locais | Não |
| Whisper | caminho definido fora do Git | modelo e fixture pós-take | Não |

## Chaves esperadas

Em `apps/android/local.properties`:

- `mwdat_application_id`
- `mwdat_client_token` quando exigido pelo SDK/organização
- `mwdat_mock_device_kit=false` para a build física de amanhã
- `picovoice_access_key`
- caminhos locais de Porcupine/Rhino

No ambiente/CI protegido:

- `MWDAT_APPLICATION_ID`
- `MWDAT_CLIENT_TOKEN`
- `PICOVOICE_ACCESS_KEY`
- `FIREBASE_APP_CHECK_DEBUG_TOKEN` somente para debug autorizado

## Conferência antes de instalar

1. Confirmar que `mwdat_mock_device_kit` não está `true`.
2. Confirmar que Application ID/client token não aparecem no diff, logs ou nome do artefato.
3. Instalar o APK `glasses`, não o APK `mock`.
4. Conceder Bluetooth `SCAN`/`CONNECT`, câmera e notificações quando o Android solicitar.
5. Confirmar que o wake word usa o microfone do celular; o DAT não fornece microfone em tempo real.
6. Se a configuração física falhar, voltar ao APK mock identificado como fallback e registrar
   `HARDWARE_SETUP`, sem alterar o domínio.

## Regra de segurança

O arquivo `google-services.json` identifica o projeto Android, mas tokens de App Check, DAT,
Picovoice e qualquer credencial administrativa continuam fora do Git e fora da evidência.

