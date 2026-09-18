# Rollup das issues em andamento

Data: `2026-09-17`  
Escopo: issues mostradas no quadro antes do hackathon. O status abaixo cruza o backlog canonico,
as tasks SDD e as evidencias executadas nesta preparacao.

## Leitura executiva

As oito issues continuarem `In Progress` esta correto. Todas ja possuem implementacao, testes ou
preparacao relevante, mas nenhuma deve ser fechada apenas por MockDeviceKit, fixture ou smoke de
schema quando o aceite exige hardware, dataset rotulado, provider ou medicao real.

| Issue | Estado real hoje | Pode fechar antes do oculos? | Proximo passo |
|---|---|---:|---|
| BE-00 | `READY_FOR_HARDWARE` | Parcialmente | validar A1-A5/DAT fisico; mock, preview, watchdog e adapter estao cobertos |
| BE-12 | `PENDING_HARDWARE` | Nao | validar gravacao nativa 3K, Bluetooth, audio, transferencia e alinhamento |
| BE-11 | `IMPLEMENTED_PENDING_MEASUREMENT` | Parcialmente | executar medicao de bateria/impacto no aparelho e registrar amostra |
| BE-10 | `PARTIAL_PRIVACY_GATE` | Parcialmente | fechar provider/App Check, limpeza Android e ausencia de vazamento com evidencia real |
| BE-09 | `PARTIAL` | Parcialmente | integrar Whisper real ao pipeline e validar video -> transcricao -> cortes |
| BE-08 | `PARTIAL` | Parcialmente | portar o runtime Whisper para Android; o spike Docker ja passou |
| BE-07 | `MEASUREMENT_PENDING` | Nao | medir latencia/energia em hardware; a otimizacao de 900 ms fica depois |
| BE-03 | `SCHEMA_PASS_QUALITY_PENDING` | Parcialmente | executar campanha visual rotulada sem PII; schema/access nao mede qualidade |

## Detalhamento por issue

### BE-00 - adapter DAT

O caminho de dominio e adapter ja esta isolado, o MockDeviceKit entrega frame portrait, o preview
realtime e efemero, o heartbeat e independente do sampler e o watchdog de quatro segundos produz
`DEGRADED`. Os testes Android executados passaram. Ainda nao existe evidencia do DAT/Ray-Ban fisico,
portanto a issue deve permanecer aberta para o ensaio A1-A5.

### BE-12 - video nativo 3K

O contrato de `RecordingSource`, a importacao `content://`, o cleanup `.part` e o alinhamento
preparado existem. Isso nao prova a gravacao nativa dos oculos. Bluetooth, audio, continuidade
apos perda e transferencia pos-`STOPPED` continuam exclusivamente no bloco de hardware de amanha.

### BE-11 - bateria

Os defaults e contadores de 0,5 fps/4 s estao implementados e o metodo de medicao esta documentado.
Nao ha medicao fisica de bateria nesta maquina. O resultado deve ser reportado como pendente, nao
estimado a partir do mock.

### BE-10 - privacidade

Inventario, logs por allowlist, descarte de frames, cleanup privado e opt-outs estao em codigo e
documentacao. Faltam a evidencia do tratamento/retencao do provider e os ensaios finais Android
de vazamento. O freeze nao deve afirmar retencao zero no provider.

### BE-09 e BE-08 - cortes e Whisper

`CutPlanner` e o fallback marks-only estao prontos. O runner Docker com `whisper.cpp v1.7.6`,
modelo multilíngue e fixture externo passou validando hash, schema e timestamps. O APK ainda usa
`UnavailableLocalTranscriptProvider`; por isso a integracao Android real continua aberta e o fluxo
nao depende dela para demonstrar o loop offline.

### BE-07 - latencia

As metricas, spans e harness estao implementados. A medicao remota registrada permanece em torno
de p50 2 s e p95 4,3 s, acima da meta de 900 ms. Isso e um resultado honesto e nao deve ser
mascarado por fallback. A otimimizacao foi corretamente adiada para depois do freeze/hardware.

### BE-03 - qualidade visual

O contrato estruturado, adapter Firebase, validacao, timeout, visao local e smoke de acesso/schema
estao cobertos. BE-03.5 ainda exige imagens e labels autorizados sem PII para medir qualidade de
direcao. Nao fechar a issue usando apenas respostas validas do Gemini.

## Ordem para o hackathon

1. BE-00: configurar o flavor `glasses`, trocar somente o adapter/config e provar primeiro frame.
2. BE-12: validar gravacao 3K, audio e transferencia apos `STOPPED`.
3. BE-10: confirmar privacidade e logs sanitizados antes de dados reais.
4. BE-08/BE-09: usar Whisper Android se estiver disponivel; caso contrario, preservar timeline e
   exportar marks-only.
5. BE-11/BE-07: medir bateria e latencia no hardware, sem bloquear o happy path por perseguir 900 ms.
6. BE-03: executar a avaliacao rotulada somente se houver dataset autorizado; registrar schema e
   qualidade separadamente.

Referencias: [checklist pre-hardware](EXECUTION_CHECKLIST.md), [evidencia do freeze](EVIDENCE_TEMPLATE.md)
e [evidencia Whisper](../evidence/BE-08_WHISPER_DOCKER_2026-09-17.md).
