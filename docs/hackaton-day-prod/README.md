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
