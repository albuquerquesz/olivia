# Proveniência de fixtures sintéticas — 17/09/2026

Este registro cobre somente fixtures vetoriais/sintéticas usadas no replay offline. Não há
imagem, áudio, pessoa, tatuagem, URI, hash de mídia ou dado pessoal no código ou neste relatório.

| ID | Origem declarada | Dimensão | Cobertura | Resultado esperado |
| --- | --- | ---: | --- | --- |
| `synthetic-centered` | `synthetic-vector-label` | 720×1280 | alvo centrado | framing/luz/horizonte OK |
| `synthetic-subject-right` | `synthetic-vector-label` | 720×1280 | alvo deslocado | `TURN_RIGHT` |
| `synthetic-hand-shadow` | `synthetic-vector-label` | 720×1280 | sombra/oclusão rotulada | `LEAVE_THE_SHADOW` |
| `synthetic-low-light-no-hand` | `synthetic-vector-label` | 720×1280 | baixa luz sem mão | `FACE_THE_LIGHT` |
| `synthetic-horizon-left` | `synthetic-vector-label` | 720×1280 | horizonte inclinado | `LEVEL_HORIZON_RIGHT` |
| `synthetic-target-absent` | `synthetic-vector-label` | 720×1280 | alvo ausente | nenhuma orientação |

Evidência executada: `MockFrameAnalyzerTest`, `DeterministicLocalVisualAnalyzerTest`,
`SamplerPipelineIntegrationTest`, `RoiToGuidanceIntegrationTest` e o perfil Docker offline
completo. Isso comprova o oráculo determinístico e o descarte/retensão do sampler; não comprova
qualidade visual em captura real ou hardware.
