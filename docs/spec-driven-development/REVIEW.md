# Review do backlog de arquitetura, infraestrutura, segurança e backend

Data: 12/09/2026. Base: 36 tasks originais, contratos Kotlin existentes, servidor Elysia, Compose de Postgres, JSONs de regras e o clipboard Excalidraw anexado. Os trechos funcionais A3–A7 do anexo correspondem à narrativa preservada em `docs/rules/excalidraw-report.json`. O caminho encontrado é `docs/rules/olivia_backlog.json`.

Conclusão: o backlog cobre o fluxo submetido, mas ainda não é um plano de execução suficientemente preciso para agentic coding. Faltavam decisões de fronteira, integração obrigatória, segurança do provedor e resolução antecipada de riscos de hardware. A revisão corrige o planejamento; não demonstra aprovação da banca ou atendimento já medido dos 100 pontos.

## Achados e tratamento

| Prioridade | Evidência / lacuna | Consequência | Encaminhamento |
|---|---|---|---|
| P0 | A3.1, A3.7 e A4 pressupõem gravação nativa, transferência e sobrevivência à queda; BE-12 estava em M3 | Pode existir direção ao vivo sem existir o take 3K prometido | BE-12 passa a M0 para spike; provar acionamento mãos-livres, simultaneidade, continuidade na queda e importação separadamente |
| P0 | Arquitetura coloca sessão como implementação DAT; contrato diz que o domínio decide DEGRADED | Agente pode confundir sessão de streaming com gravação nativa | Estado Olivia pertence ao SessionCoordinator; DAT é adapter. `startCapture` não comprova gravação nativa |
| P0 | INT-01 pede timeline e operação sem debug, mas não dependia de BE-05, FE-03 e FE-06 | Primeiro gate podia ser declarado pronto sem o fluxo descrito | Dependências corrigidas; distinguir aceite offline e ensaio com IA real |
| P0 | BE-02 dependia de BE-00/INT-00 e BE-04 de BE-03 | SDK privado ou credencial IA bloqueava domínio que pode funcionar offline | Sampler e direção dependem de contratos/fixtures/mock; provider real tem gate separado |
| P0 | SH-04 omitia STOPPED; BE-03 usava `issueType/action`, diferentes de `VisualDiagnosis`; `SessionSignal` não tem pausa/retomada | Implementações podem divergir antes da integração | SH-04 lista dez estados; BE-03 usa o vocabulário vigente; evolução dos sinais é subtask explícita |
| P0 | Comentário de `GlassesGateway.frames` diz frames amostrados, enquanto JSON diz crus; dois knobs de sampling independentes | Dupla amostragem ou falsa perda de conexão ao reduzir fps | Resolver contrato em 00 antes do adapter; distinguir saúde da captura e agenda de inferência |
| P0 | BE-08 não define local de execução; arquitetura coloca Whisper no celular | Um endpoint de upload acrescentaria tratamento de áudio não descrito | Baseline local no Android; Docker é laboratório de integração, não backend de mídia em produção |
| P0 | BE-10 cobre arquivos e logs do app, sem configurar segurança/retencão do provedor | Descarte local não prova ausência de retenção em serviços externos | BE-13 adiciona configuração protegida e BE-10 exige evidência do provedor antes de frames reais |
| P1 | `.all('/api/auth/*')`, Better Auth e migração de usuários existem, mas não há endpoints de produto | Confundir scaffold com backend necessário leva a contas, CRUD e banco no caminho crítico | Manter legado fora do grafo de execução do MVP; não remover ou publicar serviços nesta revisão |
| P1 | BE-07 termina na emissão da instrução | Latência medida pode excluir TTS e transporte Bluetooth | Medir callbacks de áudio e explicitar limite; verificação acústica em hardware separada |
| P1 | `offsetMs` é desde início da sessão; arquivo nativo pode iniciar em outro instante e conter pausas/cortes | Cortes sugeridos podem apontar para trecho errado | BE-12 define alinhamento e incerteza; BE-09 só usa offsets de mídia validados |
| P1 | BE-01 exige positivos/negativos, sem rotulagem de ROI, ambiguidade ou qualidade mínima | JSON válido pode sugerir virar para o lado errado | Fixtures rotuladas e avaliação por classe, incluindo inversões, sombra versus baixa luz e abstenção |
| P1 | Testes aparecem pontualmente e não há harness de integração | Unidades podem passar enquanto componentes reais não se conectam | BE-14 e testes obrigatórios em cada módulo, com integração Docker, Android e provider real |
| P1 | Impacto estava concentrado no pitch INT-08 | Demonstração técnica não evidencia valor para Mariana | Comparação consentida com/sem orientação, aproveitamento do take, correções úteis e falsos avisos, sem analytics de produto |
| P1 | Cronograma já contém datas passadas, sem evidências anexadas | Aparência de progresso maior que a realidade | Preservar datas históricas e ordenar execução por gates; não marcar tasks concluídas |

## Revisão task a task — Backend/IA

| Task | Avaliação | Refinamento incorporado |
|---|---|---|
| BE-00 | Alinhada, fronteira imprecisa | GlassesGateway concreto, ownership de buffers, adapter DAT separado do mock local |
| BE-01 | Alinhada, aceite fraco | Proveniência consentida/sintética, rótulos, ROI, ambiguidades, timestamps e oráculo |
| BE-02 | Alinhada, dependência bloqueante | Fonte abstrata, uma inferência por vez, descarte em falhas, saúde independente de sampling |
| BE-03 | Alinhada, schema divergente | FrameAnalysis/VisualDiagnosis, validação fechada, timeout/cancelamento, contrato e integração Firebase real |
| BE-04 | Alinhada, faltam números | Cooldown 4 s e thresholds iniciais explícitos, fala de catálogo, prioridade determinística |
| BE-05 | Alinhada, falta correlação temporal | Confirmação por frame posterior, idempotência, expiração, sessão correta e metadados locais |
| BE-06 | Alinhada | Relógio virtual, offline verificável e falhas parametrizadas |
| BE-07 | Parcial | p50/p95 com tamanho da amostra, início efetivo de áudio e limites da medida |
| BE-08 | Parcial | Whisper local, STOPPED obrigatório, mídia corrompida/sem áudio e cancelamento |
| BE-09 | Alinhada, sem semântica de corte | Intervalos a excluir, alinhamento ao vídeo, sobreposição e limites de duração |
| BE-10 | Parcial | Segurança desde o setup, retenção externa, redaction e limpeza em exceções |
| BE-11 | Alinhada, proxy de bateria insuficiente | Contadores não equivalem a energia; medição separada em celular/óculos |
| BE-12 | Crítica e tardia | Spike antecipado; controle nativo, simultaneidade, Wi-Fi e fallback identificados separadamente |
| BE-13 (nova) | Suporte necessário | Setup de nuvem/segredos/quotas e delimitação do backend Elysia existente |
| BE-14 (nova) | Suporte necessário | Harness Docker/Android/IA real e convenção de evidências |

## Cobertura da avaliação

Os pesos abaixo vêm da tabela fornecida pelo usuário. O JSON do edital tem a tabela da seção 11.2 fragmentada pela extração; não foi usado para inventar uma nota. Os cinco checkpoints da seção 8.1 são obrigações distintas dos quatro critérios de pontuação da Etapa 6. A referência a “IA local” no cronograma não elimina a permissão explícita de IA via API/nuvem na seção 8.1; eventual orientação posterior deve ser registrada em SH-01.

| Critério | Pontos possíveis | Evidência planejada | Tasks / módulos | Condição ainda pendente |
|---|---:|---|---|---|
| Viabilidade técnica | 30 | Contratos coerentes; offline repetível; integração real; recuperação; tempos medidos; pipeline pós-take | SH-04, BE-00 a BE-09, BE-12 a BE-14, INT-01/02/06/07; 00–08 e 10 | Implementação e execução dos gates; resolver capacidades de gravação |
| Aderência ao toolkit e hardware | 20 | Versão DAT, registro/permissões, frame com origem DAT, áudio nos óculos e vídeo real | INT-00/04/05/06, BE-00/12; 02/03/07/10 | Mock e headset comum não comprovam Ray-Ban Meta |
| Impacto da solução | 30 | Take demonstrável com mãos ocupadas, correção útil e confirmada, menos material inutilizável, handoff compreensível | BE-04/05/09, FE-03/04/08, INT-01/08; 03/06/08/10 | Ensaio com usuária/cenário representativo; arquitetura sozinha não garante impacto |
| Considerações éticas | 20 | Fluxo de dados; consentimento no ensaio; descarte, segurança do provedor, ausência de áudio ao vivo em IA e avaliação de falsos avisos | BE-10/13/14, INT-03; 01/05/09/10 | Evidência de configuração externa e inspeção dos caminhos de falha |
| Total | 100 | Cobertura planejada dos quatro critérios | Matriz acima | Não é nota estimada ou garantida |

## Fontes e limites

- Planejamento entregue: A3.1–8, A4, A5.1–5 e A7.1–5 do anexo, preservados em [excalidraw-report.json](../rules/excalidraw-report.json). O submit é histórico e não foi reescrito.
- Código inspecionado: [contratos](../../apps/android/core/src/main/kotlin/com/olivia/core), [servidor](../../apps/server/src/index.ts), [Compose](../../docker-compose.yml). Não há implementação de endpoints de classificação ou transcrição no servidor inspecionado.
- A documentação oficial lista `gemini-3.5-flash-lite`; mantém-se o modelo submetido, sujeito a acesso efetivo no projeto de desenvolvimento. [Modelos Firebase](https://firebase.google.com/docs/ai-logic/models), consulta em 12/09/2026.
- O setup de segurança usa App Check, restrições de chave e quotas; chave de configuração Firebase não é autorização. [Checklist Firebase](https://firebase.google.com/docs/ai-logic/security-checklist), consulta em 12/09/2026. As políticas propostas neste plano complementam essa referência; nenhuma configuração externa foi aplicada.
- O README oficial DAT documenta streaming e captura de foto. Isso não comprova o caminho nativo 3K simultâneo requerido pelo submit; BE-12 permanece gate de investigação e medição. [SDK Android Meta](https://github.com/facebook/meta-wearables-dat-android), consulta em 12/09/2026.
- `whisper.cpp` oferece exemplo Android e é candidato ao adapter local, ainda sujeito a benchmark e escolha de versão/modelo. [Projeto oficial](https://github.com/ggml-org/whisper.cpp). Não há resultado de desempenho local nesta revisão.

Não foram executados testes, builds, lint, typecheck, Docker ou chamadas de inferência. Esta revisão não usa segredos reais nem envia frames/áudio a provedores.
