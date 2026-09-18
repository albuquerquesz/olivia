# 01 — Conectar óculos reais ao app

## Resultado esperado

No flavor `glasses` com `MWDAT_MOCK_DEVICE_KIT=false`, o DAT encontra um Ray-Ban elegível,
inicia a sessão e entrega o primeiro frame real. Isso não prova 3K, áudio físico ou transferência.

## Pré-condições

- Moto com Bluetooth ativo e permissões `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` e câmera.
- Meta AI instalado, conta aprovada, Developer Mode ativo e Ray-Ban pareado pelo fluxo Meta AI.
- Óculos carregados, ligados e próximos ao Moto.
- `MWDAT_APPLICATION_ID` e, se exigido, `MWDAT_CLIENT_TOKEN` em ambiente/local.properties protegido.
- Credenciais/release channel configurados para a conta ativa no Meta AI.
- APK físico `glasses`; flag `mwdat_mock_device_kit=false`.

## Procedimento idempotente

1. Registrar modelo/firmware Ray-Ban, Moto, Meta AI, DAT e bateria inicial.
2. Conferir a flag física e permissões antes de abrir Olivia.
3. Completar o registro suportado pelo DAT/Meta AI. Não criar bond Bluetooth manual nem usar
   `BluetoothAdapter` para listar/selecionar os óculos.
4. Observar registro DAT e lista de dispositivos elegíveis.
5. Iniciar sessão curta. Sucesso exige `STARTED` e primeiro frame; “conectado” no Meta AI não basta.
6. Parar e repetir uma vez. Registrar qualquer divergência.

## Diagnóstico

| Sintoma | Registro sanitizado | Ação segura |
| --- | --- | --- |
| dispositivo ausente | registro, Meta AI, firmware | revisar pareamento, Developer Mode e conta |
| sem dispositivo elegível | código DAT e estado | revisar autorização/release channel; não trocar domínio |
| sessão não inicia | etapa e timeout | limpar recursos, repetir uma vez, usar mock se persistir |
| sem frame | tempo e estado stream | falha de captura; não aumentar fps |
| crash nativo | stack sanitizada e versões | congelar mudança estrutural e voltar ao fallback |

`PASS_HARDWARE` requer Ray-Ban físico, `STARTED`, primeiro frame DAT e preview efêmero. Nunca
anexar o frame à evidência.
