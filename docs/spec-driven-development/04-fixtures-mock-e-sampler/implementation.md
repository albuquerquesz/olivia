# Primeira entrega — fixtures, mock e sampler

Status: código e testes escritos; nenhuma verificação foi executada nesta entrega.

Esta fatia implementa BE-01.1, BE-02.1 e BE-06.1 dentro de `apps/android/core`, sem SDK
Android, DAT, Gemini ou mídia real. `MockVisualFixtures` contém somente rótulos sintéticos
720×1280 e diagnósticos esperados; nenhum frame de pessoa, tatuagem, URI ou áudio é
versionado. `MockFrameAnalyzer` seleciona respostas por sequência de frame e nunca lê ou
persiste os bytes do frame.

`FrameSampler` limita o loop a uma análise em voo e um frame pendente. O chamador recebe cada
descarte para devolver buffers no adapter de câmera: frame de sessão/generation inválida,
vencido, ou substituído por frame mais novo. A cadência e a idade usam apenas `OliviaClock`;
o watchdog de captura continua responsabilidade do coordenador, portanto reduzir a taxa de
análise não provoca `DEGRADED` artificialmente.

Os testes unitários escritos cobrem substituição, expiração, fence inativa, cadência e respostas
mock de sucesso/falha. A composição fonte→sampler→analisador no Docker permanece BE-02.2 e
não foi alegada como entregue.
