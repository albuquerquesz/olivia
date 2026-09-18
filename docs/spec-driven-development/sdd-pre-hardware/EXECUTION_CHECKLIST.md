# Checklist de execucao - SDD 01

Marcar somente apos executar e registrar evidencia. `PENDING_HARDWARE` e resultado valido e nao
deve ser convertido em `PASS` por causa do MockDeviceKit.

## 1. Baseline e SH-01

- [ ] Enviar [pedido oficial de SH-01](../evidence/SH-01_PREBUILD_OFFICIAL_REQUEST.md) para `aiglassesbrasil@ceia.ufg.br`.
- [ ] Registrar horario, remetente e resposta sanitizada; sem resposta, manter `PENDING_OFFICIAL_RESPONSE`.
- [x] Congelar `b39b6f1`/baseline atual e nao alterar o contrato de preview. O codigo do baseline foi recompilado no commit atual `eb06989`; nao houve alteracao estrutural no contrato.
- [x] Confirmar que `STARTED`, `PAUSED`, `DEGRADED`, `STOPPED` e `PROCESSING` continuam iguais. Ver [evidencia do freeze](EVIDENCE_TEMPLATE.md).

## 2. Ambiente e artefatos

- [x] Core JVM e integracao Docker offline.
- [x] Harness Bun com reports sanitizados.
- [x] Workflow CI offline criado.
- [x] Unitarios Android `mock` e `glasses`.
- [x] APK `assembleMockDebug`.
- [x] APK `assembleGlassesDebug`.
- [ ] Executar o workflow no GitHub e arquivar o link do run.
- [x] Separar `google-services.json`/App Check/olivia env de qualquer artefato versionado. Os arquivos locais estao ignorados; nenhum arquivo sensivel foi rastreado.

## 3. Fronteira DAT e preview

- [x] MockDeviceKit abre sessao e entrega frame portrait.
- [x] Origem DAT/mock fica identificavel no contrato.
- [x] Heartbeat e independente do sampler.
- [x] Quatro segundos sem frame produzem `DEGRADED`.
- [x] Recuperacao abre geracao valida e retorna ao estado correto.
- [x] Preview JPEG aparece somente durante a gravacao.
- [x] Preview e limpo em pausa, stop e fechamento.
- [x] Configurar `MWDAT_USE_MOCK_DEVICE_KIT=false` para a build de amanha sem mudar o dominio.
- [x] Documentar arquivos locais que serao conferidos no onboarding: Application ID, client token, permissoes e `google-services.json`. Ver [ONBOARDING_CONFIG.md](ONBOARDING_CONFIG.md).

## 4. Voz e audio

- [x] Wake word/intents locais tem lifecycle e cancelamento testados.
- [x] Comandos de gravar, pausar, retomar e terminar chegam a maquina de estados.
- [x] TTS/WAV e tons de inicio, pausa, queda, retomada e stop sao locais.
- [x] Nenhum PCM de comando chega ao Firebase, timeline, filesystem ou logs.
- [x] Deixar o teste manual de microfone do celular pronto para amanha; nao usar microfone dos oculos. Ver [MANUAL_MIC_CHECK.md](MANUAL_MIC_CHECK.md).

## 5. Loop offline e IA

- [x] Cinco takes deterministicos sem toque no fluxo de runtime fake.
- [x] Duas correcoes confirmadas por take.
- [x] Degradacao apos 4 s e recuperacao apos retorno do frame.
- [x] Timeline/cortes e evidencia sanitizada.
- [x] Firebase/App Check smoke no flavor `glasses`.
- [ ] Executar campanha rotulada autorizada, se houver imagens/labels disponiveis sem PII.
- [ ] Registrar separadamente schema/access smoke, qualidade visual e latencia.

## 6. Privacidade e seguranca

- [x] Frame nao e serializavel nem aparece em JSON de timeline/review.
- [x] Falhas do provider sao content-free.
- [x] Imports usam `.part`, cache privado e cleanup em cancelamento/falha.
- [x] Opt-outs DAT e App Check estao documentados.
- [x] Fazer revisao final de backup/sandbox Android e dos logs da build que ira ao evento.
- [x] Nao copiar tokens, frames, audio bruto, respostas do modelo ou URIs privadas para evidencia.

## 7. Pos-take plugavel

- [x] `RecordingSource` e importacao manual `content://` validada.
- [x] `STOPPED -> PROCESSING -> READY` e cancelamento controlado.
- [x] `CutPlanner`/`ReviewExporter` nao inventam transcricao ou cortes.
- [x] Provisionar modelo/fixture Whisper local fora do Git e executar o perfil Docker. Resultado sanitizado em [BE-08_WHISPER_DOCKER_2026-09-17.md](../evidence/BE-08_WHISPER_DOCKER_2026-09-17.md).
- [x] Conferir que Whisper so e chamado depois de `STOPPED`.
- [x] Deixar o caminho "marks-only" como fallback se o video nativo ou o modelo nao estiverem disponiveis.

## 8. Freeze final

- [x] Gerar APK mock e APK glasses final.
- [x] Salvar hash dos APKs e versao do commit.
- [ ] Copiar backup do demo mock para dois notebooks/locais. Dois slots locais foram preparados; falta copiar para o segundo notebook/dispositivo.
- [x] Preencher [EVIDENCE_TEMPLATE.md](EVIDENCE_TEMPLATE.md).
- [x] Imprimir/abrir o [runbook de amanha](../sdd-hackathon-day/RUNBOOK_2026-09-18.md).
- [ ] Apos o freeze, nao fazer refactor; somente configuracao ou correcao bloqueadora.
