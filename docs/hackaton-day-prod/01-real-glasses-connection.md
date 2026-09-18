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

## Evidência executada — 18/09/2026

- O Android/Meta AI reconhece o Ray-Ban físico como conectado e pronto para captura.
- O comando local chegou ao gateway físico como `START_RECORDING`.
- Preflight DAT sanitizado: `registration=AVAILABLE`, `dev_mode=false`, `devices=0`, `metadata=none`.
- A ação oficial `Wearables.startRegistration` abriu o Meta AI, que retornou:
  `Application is not authorized to perform this operation. Attestation failed.`
- O APK instalado é `com.olivia.companion`, variante `glassesDebug`, assinado com a chave debug
  local. SHA-256: `68:34:EC:A2:60:F3:94:1F:A8:83:CE:EF:B4:5E:9E:08:80:99:5A:C0:A8:25:DF:13:54:A8:D0:43:78:79:8E:CF`.

Conclusão naquela tentativa inicial: o bloqueio era autorização/attestation da integração no Wearables Developer Center
(package, certificado e release channel/conta), não pareamento Bluetooth, ADB ou seleção manual de
dispositivo. `PASS_HARDWARE` permanece pendente até o portal autorizar o APK e o DAT entregar um
device elegível.

Para o ensaio físico local, a configuração foi corrigida depois dessa evidência: `glassesDebug` agora
empacota `APPLICATION_ID=0` e `CLIENT_TOKEN=0`, mantendo `MWDAT_USE_MOCK_DEVICE_KIT=false`. Esse é
o caminho DAT Developer Mode; exige Developer Mode ativo no Meta AI. As credenciais registradas ficam
reservadas para `glassesRelease` com package/certificado e release channel autorizados.

## Retomada física — 18/09, 12:14–12:18

Registro e Developer Mode confirmados nesta retomada: `REGISTERED`, `dev_mode=true`,
`devices=1`, `COMPATIBLE`. O diagnóstico inicial de attestation acima é histórico.
A câmera autorizada e o stream iniciado constam no histórico anterior, mas o preview ainda
não tem aceite: o crash nativo motivou a troca do decoder para saída direta MediaCodec.
A correção adicional remove o envio duplicado de CSD; compilação/instalação concluídas.
Nesta retomada, o transporte voltou a expirar na leitura GATT antes de abrir a câmera.
Ver [evidência da retomada](checkpoint-1/retomada-dat.md). `PASS_HARDWARE` segue pendente.
