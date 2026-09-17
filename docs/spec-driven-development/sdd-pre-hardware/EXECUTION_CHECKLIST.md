# Checklist de execução — SDD 01

Marcar somente após executar e registrar evidência. `PENDING_HARDWARE` é resultado válido e não
deve ser convertido em `PASS` por causa do MockDeviceKit.

## 1. Baseline e SH-01

- [x] Enviar [pedido oficial de SH-01](../evidence/SH-01_PREBUILD_OFFICIAL_REQUEST.md) para `aiglassesbrasil@ceia.ufg.br`.
- [ ] Registrar horário, remetente e resposta sanitizada; sem resposta, manter `PENDING_OFFICIAL_RESPONSE`.
- [ ] Congelar `b39b6f1`/baseline atual e não alterar o contrato de preview.
- [ ] Confirmar que `STARTED`, `PAUSED`, `DEGRADED`, `STOPPED` e `PROCESSING` continuam iguais.

## 2. Ambiente e artefatos

- [x] Core JVM e integração Docker offline.
- [x] Harness Bun com reports sanitizados.
- [x] Workflow CI offline criado.
- [x] Unitários Android `mock` e `glasses`.
- [x] APK `assembleMockDebug`.
- [ ] Executar o workflow no GitHub e arquivar o link do run.
- [ ] Separar `google-services.json`/App Check/olivia env de qualquer artefato versionado.

## 3. Fronteira DAT e preview

- [x] MockDeviceKit abre sessão e entrega frame portrait.
- [x] Origem DAT/mock fica identificável no contrato.
- [x] Heartbeat é independente do sampler.
- [x] Quatro segundos sem frame produzem `DEGRADED`.
- [x] Recuperação abre geração válida e retorna ao estado correto.
- [x] Preview JPEG aparece somente durante a gravação.
- [x] Preview é limpo em pausa, stop e fechamento.
- [ ] Configurar `MWDAT_USE_MOCK_DEVICE_KIT=false` para a build de amanhã sem mudar o domínio.
- [ ] Documentar arquivos locais que serão conferidos no onboarding: Application ID, client token,
  permissões e `google-services.json`.

## 4. Voz e áudio

- [x] Wake word/intents locais têm lifecycle e cancelamento testados.
- [x] Comandos de gravar, pausar, retomar e terminar chegam à máquina de estados.
- [x] TTS/WAV e tons de início, pausa, queda, retomada e stop são locais.
- [x] Nenhum PCM de comando chega ao Firebase, timeline, filesystem ou logs.
- [ ] Deixar o teste manual de microfone do celular pronto para amanhã; não usar microfone dos óculos.

## 5. Loop offline e IA

- [x] Cinco takes determinísticos sem toque no fluxo de runtime fake.
- [x] Duas correções confirmadas por take.
- [x] Degradação após 4 s e recuperação após retorno do frame.
- [x] Timeline/cortes e evidência sanitizada.
- [x] Firebase/App Check smoke no flavor `glasses`.
- [ ] Executar campanha rotulada autorizada, se houver imagens/labels disponíveis sem PII.
- [ ] Registrar separadamente schema/access smoke, qualidade visual e latência.

## 6. Privacidade e segurança

- [x] Frame não é serializável nem aparece em JSON de timeline/review.
- [x] Falhas do provider são content-free.
- [x] Imports usam `.part`, cache privado e cleanup em cancelamento/falha.
- [x] Opt-outs DAT e App Check estão documentados.
- [ ] Fazer revisão final de backup/sandbox Android e dos logs da build que irá ao evento.
- [ ] Não copiar tokens, frames, áudio bruto, respostas do modelo ou URIs privadas para evidência.

## 7. Pós-take plugável

- [x] `RecordingSource` e importação manual `content://` validada.
- [x] `STOPPED → PROCESSING → READY` e cancelamento controlado.
- [x] `CutPlanner`/`ReviewExporter` não inventam transcrição ou cortes.
- [ ] Provisionar modelo/fixture Whisper local fora do Git e executar o perfil Docker, se possível.
- [ ] Conferir que Whisper só é chamado depois de `STOPPED`.
- [ ] Deixar o caminho “marks-only” como fallback se o vídeo nativo ou o modelo não estiverem disponíveis.

## 8. Freeze final

- [ ] Gerar APK mock e APK glasses final.
- [ ] Salvar hash dos APKs e versão do commit.
- [ ] Copiar backup do demo mock para dois notebooks/locais.
- [ ] Preencher [EVIDENCE_TEMPLATE.md](EVIDENCE_TEMPLATE.md).
- [ ] Imprimir/abrir o [runbook de amanhã](../sdd-hackathon-day/RUNBOOK_2026-09-18.md).
- [ ] Após o freeze, não fazer refactor; somente configuração ou correção bloqueadora.
