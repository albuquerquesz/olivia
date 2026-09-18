# SDD 02 — Dia do hackathon

Data: 18/09/2026  
Objetivo: usar os Ray-Ban Meta sem reescrever o produto, terminar toda a integração dos óculos até
16:00 e preservar pelo menos três horas até o encerramento das 19:00 para pitch e contingência.

O cronograma abaixo segue o edital em `docs/rules/hackaton-rules.json`: credenciamento às 08:00,
onboarding DAT às 09:30, checkpoints às 10:30, 15:00 e 16:00, fechamento técnico às 17:30,
pitch às 18:00 e encerramento às 19:00.

## Regra principal do dia

O produto já estará pronto. Amanhã só podem mudar:

- credenciais/Application ID/permissões;
- seleção do adapter real (`MWDAT_USE_MOCK_DEVICE_KIT=false`);
- configuração de modelo/limites;
- integração do arquivo nativo no `RecordingSource`;
- correções pequenas de compatibilidade comprovadas pelo dispositivo.

Não reescrever `:core`, `SessionRuntime`, preview, sampler, timeline, Porcupine/Rhino ou o contrato
de áudio durante o hackathon.

## Critério de sucesso

Até 15:00: câmera/stream DAT, preview, voz local, output por áudio e loop de direção funcionam.  
Até 16:00: privacidade, bateria, vídeo nativo/limitações e fallback estão registrados.  
Depois de 16:00: somente pitch, backup e correções de configuração; nenhum refactor.

Runbook minuto a minuto: [RUNBOOK_2026-09-18.md](RUNBOOK_2026-09-18.md).  
Matriz de evidência: [HARDWARE_EVIDENCE.md](HARDWARE_EVIDENCE.md).
