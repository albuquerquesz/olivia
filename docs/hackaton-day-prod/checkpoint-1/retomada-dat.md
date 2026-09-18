# Retomada da integração física — 18/09/2026

Status: primeiro frame físico confirmado; aceite de preview e lifecycle ainda em validação.

## Escopo e artefato

- Tasks: `BE-00` e `INT-04`; dependências `SH-04`, `FE-00`, `FE-02`, `BE-02`, `INT-00`.
- Moto G60, Android 12, Ray-Ban Meta; DAT Android `0.9.0`.
- `glassesDebug`, `MWDAT_USE_MOCK_DEVICE_KIT=false`, Developer Mode `0/0`.
- Base Git `d87ec56`, com alterações locais ainda não commitadas.
- SHA-256 do APK instalado nesta retomada:
  `7d2377de71d07d6472c2e3208d5f359cff65d8129ac8539efbd12eaffa15aa9f`.

## Evidência direta nesta retomada

Horários do logcat do Moto, em 18/09:

| Horário | Evidência sanitizada | Conclusão limitada |
| --- | --- | --- |
| 12:14:34 | `APP_CHECK_READY` | App Check aceito; não comprova inferência Gemini |
| 12:15:10 | `REGISTERED`, `dev_mode=true`, `devices=1`, `CONNECTING/COMPATIBLE` | Registro e descoberta passaram |
| 12:15:13–12:15:22 | Leitura GATT expirou em 2000 ms após conexão, descoberta de serviços e negociação MTU | Falha de transporte antes da câmera |
| 12:15:25 | `DAT data connection timed out` | Tentativa encerrada em 15 s |
| 12:16:12–12:16:27 | `DISCONNECTED/COMPATIBLE`, seguido do mesmo timeout | Repetição após usuário confirmar óculos no rosto |
| 12:17:48–12:18:03 | `CONNECTING/COMPATIBLE`, seguido do mesmo timeout | Novo APK instalado; Meta AI reaberto; câmera ainda não iniciou |

`./gradlew installGlassesDebug` terminou com `BUILD SUCCESSFUL` e instalação em um aparelho.
Não foram executadas suítes de testes automatizados nesta retomada.

## Correção retomada

O histórico anexado registra `CAMERA=GRANTED`, sessão DAT `STARTED` e stream `STREAMING`,
seguidos de crash nativo ao ler planos do `ImageReader`. A troca para saída YUV flexível via
`MediaCodec.getOutputImage` já estava no código e no APK ao retomar.

Nesta retomada foi removido o reenvio explícito de configuração HEVC após `start()`:
o `csd-0` do `MediaFormat` já é enviado automaticamente pelo Android.
[Contrato oficial MediaCodec](https://developer.android.com/reference/android/media/MediaCodec#CSD).
O pacote que contém configuração e imagem continua sendo enfileirado como imagem.

O estado `STARTED` do SDK não substitui o `STARTED` da Olivia, que requer heartbeat.
A correção não pode receber aceite físico até o transporte voltar e chegar frame decodificado.

## Próximo passo

Reiniciar fisicamente os óculos pelo interruptor, reconectar pelo fluxo Meta AI e repetir uma
sessão curta. Exigir `first_frame origin=DAT` e preview observado; encerrar e confirmar limpeza.
Manter `PASS_HARDWARE` pendente até isso acontecer. Não repetir configuração de credenciais ou
cadastro do App Check sem nova evidência de falha nessas etapas.

Gravação nativa 3K/transferência continua no gate independente de `BE-12`. Não foi implementada
gravação do stream Bluetooth. Não foram persistidos frames, áudio ou credenciais nesta evidência;
latência acústica e consumo de bateria não foram medidos.

## Avanço após reinício — 12:21–12:24

Após reinício físico dos óculos informado pelo operador e reinício do transporte/apps no Moto:

- Meta AI mostrou óculos conectados e bateria 75% (leitura pontual, sem medição de consumo).
- 12:21:45: DAT `CONNECTED/COMPATIBLE`.
- 12:21:46: câmera `Granted`, sessão DAT `STARTED`.
- 12:21:49: stream `STREAMING`.
- 12:22:04: `first_frame origin=DAT format=NV21 width=720 height=1280`.

Não houve novo crash no decoder nessa tentativa. A tela já estava em `ERROR` porque o watchdog
contou o período de inicialização como falta de frame. Corrigido em `BE-00`/`FE-02`: o gateway
aguarda até 25 s pelo primeiro frame após o stream iniciar, e o coordenador consome o heartbeat
já publicado antes de liberar o lock. O watchdog de captura ativa continua em 4 s.

Segunda compilação/instalação concluída com sucesso. Incluído teste de regressão de início lento
com heartbeat e degradação após 4 s; teste automatizado ainda não executado.
