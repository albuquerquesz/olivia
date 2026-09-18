# 06 — Runbook idempotente e checklists

Cada etapa pode ser repetida sem apagar dados, alterar contrato ou criar promessa. Em falha: parar,
registrar estado sanitizado, usar fallback e seguir.

## Estado executado — 18/09/2026

- [x] ADB reconheceu o Moto G60 por USB.
- [x] `glassesDebug` físico instalado com `MWDAT_MOCK_DEVICE_KIT=false`.
- [x] Tela de gravação aberta; preview aguarda o stream DAT.
- [x] Comando “Oi, Olivia, gravar” executado; `START_RECORDING` reconhecido localmente.
- [x] Preflight DAT executado; `registration=AVAILABLE`, `devices=0`.
- [x] Fluxo oficial de autorização abriu o Meta AI.
- [x] `glassesDebug` ajustado para Developer Mode DAT (`APPLICATION_ID=0`, `CLIENT_TOKEN=0`) e reinstalado.
- [x] Developer Mode e registro local confirmados: `REGISTERED`, `dev_mode=true`, um device compatível.
- [ ] `STARTED` e primeiro frame real observados.
- [ ] Evidência `PASS_HARDWARE` salva sem anexar frame.

### Retomada às 12:14–12:18

- [x] `APP_CHECK_READY` observado no APK instalado, sem novo cadastro de token.
- [x] Mantido DAT `0.9.0`; decoder usa saída direta `MediaCodec.getOutputImage` para evitar o caminho `ImageReader` que sofreu crash.
- [x] Removido envio duplicado da configuração HEVC; `installGlassesDebug` compilou e instalou a correção no Moto G60.
- [ ] Primeiro frame/preview da correção: tentativas desta retomada ficaram em `CONNECTING`/`DISCONNECTED`, com timeout GATT antes da câmera.

O histórico anterior registra câmera autorizada, sessão DAT `STARTED` e stream `STREAMING`.
Esses estados do SDK não comprovam `STARTED` da Olivia, que exige heartbeat de frame decodificado.
Registro/autorização inicial não é mais o bloqueio observado. Evidência detalhada em
[retomada física](checkpoint-1/retomada-dat.md).

## 08:00–09:00 — credenciamento e recepção

- [ ] Registrar hardware, firmware, versões, baterias e responsável.
- [ ] Confirmar ADB por Wi-Fi, carregadores e backup em dois locais.
- [ ] Conferir configuração local física sem expor valores.
- [ ] Separar APK `glasses` físico e APK `mock` fallback.

## 09:00–09:30 — abertura oficial

- [ ] Confirmar agenda, jurados e instruções comunicadas pela organização.
- [ ] Registrar somente mudanças oficiais de regras ou cronograma.

## 09:30–10:30 — onboarding técnico

- [ ] Conferir Meta AI, conta, Developer Mode e pareamento oficial.
- [ ] Abrir `glasses` com MockDeviceKit desligado.
- [ ] Conceder Bluetooth, câmera, microfone e notificações solicitadas.
- [ ] Confirmar registro DAT e dispositivo elegível.
- [ ] Iniciar/parar sessão curta; observar `STARTED` e primeiro frame.
- [ ] Repetir uma vez; se falhar, registrar `HARDWARE_SETUP` e abrir mock.

## 10:30–11:00 — apresentação dos checkpoints obrigatórios

- [ ] Confirmar com a organização os cinco checkpoints: IA, câmera/microfone, áudio,
  privacidade e eficiência de bateria.
- [ ] Adaptar a ordem de demonstração apenas se houver instrução oficial.

## 11:00–12:00 — desenvolvimento e mentoria

- [ ] Confirmar preview e descarte em pausa/stop.
- [ ] Testar voz e tom nos óculos.
- [ ] Fazer take de 10–30 s com direção/marcas, se IA disponível.
- [ ] Executar A1/A2 somente via API/fluxo oficialmente exposto.

## 12:00–14:00 — almoço

- [ ] Preservar equipamentos, APKs e evidências; não alterar contrato durante a pausa.

## 14:00–15:00 — desenvolvimento e mentoria

- [ ] Executar A3 somente se a recuperação Bluetooth for segura.
- [ ] Encerrar em `STOPPED`; tentar A4 e A5 quando possível.
- [ ] Medir inferências/bateria sem alterar parâmetros sem dados.

## 15:00–15:30 — checkpoint técnico 1

- [ ] Mostrar IA, frame, áudio, privacidade e bateria com limites declarados.
- [ ] Usar o caminho real mais confiável ou mock claramente identificado.

## 15:30–16:00 — coffee break

- [ ] Recarregar dispositivos e salvar backup sanitizado.

## 16:00–16:45 — checkpoint técnico 2

- [ ] Demonstrar privacidade e estratégia de eficiência de bateria, sem alegar medição não feita.

## 16:45–17:30 — reta final e entrega do pitch

- [ ] Congelar integração; depois disso, somente correção bloqueadora.
- [ ] Salvar APK físico, APK mock, commit/hash e evidência sanitizada.
- [ ] Entregar materiais do pitch no formato solicitado pela organização.

## 18:00–18:30 — pitches finais

- [ ] Apresentar: dor da Mariana → comando local → frame/preview → áudio → marca → revisão →
  privacidade/0,5 fps → resultado físico medido ou mock identificado.
- [ ] Manter mock e limites físicos explícitos durante perguntas da banca.

## 18:30–18:45 — deliberação da banca

- [ ] Não realizar mudança técnica; manter evidências e APKs disponíveis para consulta.

## 18:45–19:00 — encerramento e premiação

- [ ] Registrar apenas resultados anunciados oficialmente.

## 19:00 — confraternização

Entre 17:30 e 18:00, não há atividade técnica planejada: usar o intervalo apenas conforme orientação
da organização.

Parar uma linha quando a API não expõe a capacidade, DAT falha repetidamente, pareamento não é
elegível ou recuperação é insegura. Não fazer refactor, pareamento Android manual ou mudança de
contrato para mascarar limite físico.
