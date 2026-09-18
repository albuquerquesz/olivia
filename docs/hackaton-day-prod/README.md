# Olivia — plano de produção do dia do hackathon

Este é o único pacote operacional para o dia do hackathon. Ele é autocontido: descreve produto,
estado do código, hardware, fallback e execução sem depender de documentação histórica.

## Objetivo

Demonstrar Olivia como companion Android dos Ray-Ban Meta: comando local, stream dos óculos,
orientação por áudio e marcas/cortes pós-take. O celular é a ponte obrigatória do DAT.

## Ordem de operação

1. [Contrato do evento](00-event-contract.md)
2. [Óculos reais](01-real-glasses-connection.md)
3. [Bluetooth e DAT](02-bluetooth-and-dat.md)
4. [Mídia nativa](03-native-recording-and-storage.md)
5. [Loop vivo](04-live-loop-and-guidance.md)
6. [Privacidade e evidências](05-privacy-and-evidence.md)
7. [Runbook](06-runbook-and-checklists.md)

## Estado inicial

| Área | Existe | Ainda exige hardware |
| --- | --- | --- |
| `mockDebug` | demo offline determinístico | DAT/Bluetooth físico |
| `glassesDebug` + MockDeviceKit | lifecycle DAT e câmera do Moto simulando óculos | Ray-Ban, áudio e stream reais |
| adapter DAT físico | `DatGlassesGateway` e flag física | pareamento, elegibilidade e primeiro frame |
| loop de direção | 0,5 fps, cooldown, estados, timeline, áudio local | qualidade/latência/audibilidade física |
| mídia nativa | fronteira e fallback de importação | 3K, coexistência, transferência, alinhamento |

## Progresso registrado — 18/09/2026

- [x] Moto G60 conectado ao ADB; pacote físico `com.olivia.companion` instalado.
- [x] `mwdat_mock_device_kit=false` confirmado no `local.properties` usado no build.
- [x] `glassesDebug` compilado e instalado no Moto; a tela física usa preview DAT; controles manuais existem apenas no build debug para diagnóstico.
- [x] Tela de gravação aguarda o primeiro frame do DAT e mantém o comando local “Oi, Olivia. Depois: gravar”.
- [x] Wake word e comando `START_RECORDING` reconhecidos localmente no Moto.
- [x] Gateway físico inicializa o DAT e registra preflight sanitizado: `registration=AVAILABLE`, `devices=0`.
- [x] Ação oficial de autorização abre o Meta AI pelo DAT.
- [x] `glassesDebug` corrigido para DAT Developer Mode (`APPLICATION_ID=0`, `CLIENT_TOKEN=0`) e reinstalado.
- [x] Developer Mode e registro local confirmados: `REGISTERED`, `dev_mode=true`, um device compatível.
- [ ] Observar `STARTED` + primeiro frame real no APK físico.
- [ ] Marcar `PASS_HARDWARE` somente após esse primeiro frame e preview observáveis.
- [ ] Gravação nativa 3K/transferência permanece `BLOCKED_BY_DAT_0_9_0`, conforme o gate de 18/09.

### Retomada às 12:14–12:18

- [x] `APP_CHECK_READY` observado no APK instalado, sem novo cadastro de token.
- [x] Mantido DAT `0.9.0`; decoder usa saída direta `MediaCodec.getOutputImage` para evitar o caminho `ImageReader` que sofreu crash.
- [x] Removido envio duplicado da configuração HEVC; `installGlassesDebug` compilou e instalou a correção no Moto G60.
- [ ] Primeiro frame/preview da correção: tentativas desta retomada ficaram em `CONNECTING`/`DISCONNECTED`, com timeout GATT antes da câmera.

O histórico anterior registra câmera autorizada, sessão DAT `STARTED` e stream `STREAMING`.
Esses estados do SDK não comprovam `STARTED` da Olivia, que exige heartbeat de frame decodificado.
Registro/autorização inicial não é mais o bloqueio observado. Evidência detalhada em
[retomada física](checkpoint-1/retomada-dat.md).

## Modos

| Artefato | Uso | Regra |
| --- | --- | --- |
| `mockDebug` | backup offline | não usa DAT; não prova hardware |
| `glassesDebug` com mock | ensaio do SDK | rotular como simulado |
| `glasses` sem mock | ensaio físico | exige fluxo Meta AI e credenciais locais |

## Fallback

Falha física: registrar estado/código sanitizado, preservar APK mock e voltar ao caminho
demonstrável. Não reescrever o domínio, aumentar sampling ou fabricar vídeo 3K a partir do stream.

Segredos, frames, áudio bruto, vídeo, transcrição e URIs privadas nunca entram em Git, logs ou
evidências.
