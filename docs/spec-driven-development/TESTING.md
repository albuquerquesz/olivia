# Contrato de testes e ambiente

Cada task funcional entrega implementação, testes unitários e pelo menos um teste de integração relevante no mesmo conjunto de alterações. Docker faz parte obrigatória do harness BE-14. Não é suficiente testar apenas mocks de todos os colaboradores. O [runner inicial de contratos](../../infra/testing/README.md) está implementado, sem execução. Os demais perfis abaixo são alvos futuros e não representam autorização para executar verificações.

## Camadas de evidência

| Camada | Ambiente planejado | O que realmente integra | Aceite |
|---|---|---|---|
| Unitária | Kotlin/JVM com relógio virtual; Bun se houver ferramenta TS | Uma política/transformação e suas bordas | Resultado determinístico, incluindo entradas inválidas |
| Integração offline | Docker Compose dedicado: runner JVM + stub HTTP local com cenários | Sampler, validação, direção, timeline e pós-processamento com implementações concretas | Roteiro 30–60 s, falhas, descarte e zero egress externo |
| Integração Android | Emulator/dispositivo; mock local e MockDeviceKit em variantes distintas | Lifecycle, permissões, bytes DAT, SDK Firebase, filesystem e áudio Android | Fixture chega ao componente real; exceções não derrubam sessão |
| Integração IA real | Android + projeto Firebase de desenvolvimento; configuração local/CI | Exato adapter Firebase usado pelo app e modelo selecionado | Schema, classe/direção, erro/timeout e sanitização de evidência |
| Integração Whisper real | Runner Docker CPU e Android usando versão/modelo equivalentes | Decodificação, transcrição local, alinhamento e CutPlanner | Segmentos e cortes válidos; Android é referência de desempenho |
| Hardware/E2E | Celular e Ray-Ban Meta | Captura real, saída audível, queda, take nativo, energia | INT-04 a INT-07 com ambiente e limites registrados |

Docker não emula Bluetooth, firmware, MockDeviceKit nem Firebase AI Logic Android. O stub exercita o parser e o comportamento de rede por uma porta de teste; não prova a integração SDK real. Teste REST com uma chave Gemini pode diagnosticar acesso, mas não substitui o teste do adapter Firebase Android.

## Harness a implementar em BE-14

- Criar `infra/testing/compose.yaml`, separado de `docker-compose.yml` legado. Fixar versões/imagens; runner sem privilégios, sem socket Docker montado, filesystem somente leitura quando viável e temporários em tmpfs. Containers não armazenam mídia real.
- Perfil offline: rede interna para runner/stub, dependências previamente instaladas na imagem, fixtures sintéticas/consentidas e zero secrets. Timeout externo deve falhar se o teste tentar internet. Preparação da imagem pode precisar de rede; execução offline não.
- Perfil Whisper: modelos com versão/hash e licença documentados, montados somente leitura; áudio de fixture temporário, apagado inclusive na falha. CPU Docker comprova integração e não a meta de 8 s no telefone.
- Postgres não é requisito do produto. Caso o harness cubra o scaffold Elysia existente, usar perfil `legacy-api` opcional com banco descartável, rede privada e migrações reais. Nunca usar o volume `postgres_data` de desenvolvimento ou uma DATABASE_URL externa para testes.
- Implementar entrypoints documentados para `unit`, `integration-offline`, `integration-android`, `integration-live-ai` e `integration-whisper`. CI offline roda sem secrets de nuvem; live depende de ambiente protegido e não recebe credenciais em PR de fork.
- Retornar erro e interromper seleção live sem configuração obrigatória. Quando a suíte live não foi selecionada, reportar “não executada”; nunca fallback automático para mock com selo verde de integração real.
- Todos os recursos temporários pertencem à execução, com identificação própria e teardown limitado a esses recursos. Coletar relatório sanitizado mesmo se houver timeout e limpar em `finally`.

## Configuração e segredos

BE-13/14 deverão criar exemplos sem valores reais e um carregador por allowlist. O `.env` de desenvolvimento permanece ignorado. Variáveis de teste propostas:

| Configuração | Uso / armazenamento |
|---|---|
| `OLIVIA_TEST_MODE=offline` | Padrão; live é opt-in explícito |
| `OLIVIA_AI_MODEL=gemini-3.5-flash-lite` | Modelo submetido; verificar disponibilidade no projeto |
| `OLIVIA_LIVE_AI_MAX_REQUESTS=12` | Limite local do smoke, sem retries ilimitados; campanha de qualidade exige orçamento próprio |
| `OLIVIA_LIVE_AI_MAX_ESTIMATED_COST_USD` | Obrigatório em live, definido pelo operador; limite estimado complementa quotas do provedor |
| `OLIVIA_FIREBASE_CONFIG_PATH` | Caminho local do arquivo de configuração do app/projeto de desenvolvimento; gerar/copiar só o necessário para Android |
| `FIREBASE_APP_CHECK_DEBUG_TOKEN` | Segredo de teste Android; registrar somente no projeto dev, nunca na release |
| `GITHUB_TOKEN` | Acesso aos pacotes DAT; somente etapa de resolução Gradle, não imagem/APK |
| `OLIVIA_DAT_APPLICATION_ID` | Configuração do app registrado, mapeada explicitamente ao mecanismo Gradle existente |
| `PORCUPINE_ACCESS_KEY` | Configuração restrita do SDK local; não tratar chave embutida no cliente como segredo inviolável |
| `OLIVIA_WHISPER_MODEL_PATH` | Modelo local versionado; sem API key de transcrição na baseline |
| `GEMINI_API_KEY` | Apenas diagnóstico direto opcional em ferramenta de host, se necessário; nunca embarcar no app nem usar no lugar do gate Firebase |

`.env` não é mecanismo de proteção de APK: não injetar o arquivo inteiro em BuildConfig, Docker build args ou bundle. Firebase config pública identifica o projeto; credenciais administrativas, tokens de debug e chaves de API de servidor exigem tratamento separado. Não ler ou imprimir arquivos reais de segredo em relatórios.

## Oráculos e cenários mínimos

- Relógio virtual: frame a cada 2 s, timeout aos 4 s, cooldown 4 s; testar exatamente antes, no limite e depois. Cobrir pausa, STOP, cancelamento e resposta tardia de outra sessão.
- Fixtures: ao menos positivos e negativos para cada uma das três classes, rotação, espelhamento, alvo ambíguo/ausente, baixa confiança, duas falhas simultâneas e texto malicioso na imagem. Manifesto com rótulo, origem e resultado esperado. Não usar pessoas/tatuagens reais sem autorização.
- Smoke live de até 12 chamadas: confirma acesso e integração, não estima acurácia. Campanha rotulada separada: ao menos 10 positivos e 10 negativos por classe; reportar matriz de confusão e direção correta. Meta inicial interna: ≥80% dos desvios rotulados com direção correta por classe e ≤10% de avisos nos negativos; tamanho limitado da amostra deve acompanhar o resultado. Estes são gates de engenharia propostos, não critérios oficiais da banca.
- Rede: 401/403, 429, 5xx, JSON inválido, enum desconhecido, confidence fora de 0..1, payload excessivo, timeout, DNS indisponível, cancelamento e retorno fora de ordem. Retenção de bytes deve acabar em todos os caminhos; nenhum texto livre do modelo vira fala.
- Integração de mídia: vídeo sem áudio/corrompido, URI revogada, offsets não alinhados, segmentos sobrepostos, faixa vazia, silêncio, frase de refação, cancelamento e modelo ausente. Nunca reconstruir vídeo do stream para fazer o teste passar.
- Privacidade: inspecionar saídas de log, cache, diretório temporário e tráfego do próprio ensaio. Testar limpeza após falha; não prometer apagamento físico garantido da RAM gerenciada ou retenção zero externa sem prova.

## Registro de evidência

Cada execução produz um registro com task/subtask, commit, data, ambiente, versões, fixture ID, configuração não secreta, modo (`mock-local`, `mock-dat`, `live-ai`, `hardware`), cenário, esperado, observado, status e limitações. Armazenar em `docs/spec-driven-development/evidence/<task>/` quando existir resultado; esta revisão não cria resultados fictícios.

Resultados de desempenho registram tamanho da amostra, warm/cold, dispositivo, rede e ponto exato de timestamp. Snapshot de texto completo gerado por IA, frame, transcrição ou tokens não são evidência sanitizada. Usar IDs, enums, contadores e tempos. A feature fica `pronta para validação`, não `concluída`, enquanto faltar executar uma camada obrigatória.
