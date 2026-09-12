# Classes de classificação de enquadramento, luz e horizonte

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: BE-03.

Dependências: SH-04, BE-01, BE-02, BE-13 e BE-14. Direção pode avançar com BE-06 enquanto acesso real não está disponível.

Escopo: Dentro. Somente FRAMING, LIGHT e HORIZON. Controllers são componentes internos Kotlin, não endpoints Elysia.

## Contrato e responsabilidades

GeminiFrameAnalyzer prepara um único frame efêmero para Firebase AI Logic e converte a resposta no contrato FrameAnalysis. DiagnosisValidator valida lista de diagnósticos com tipo fechado, severity, confidence finita entre 0 e 1, direção compatível com a classe e magnitude finita/limitada quando existir. Metadados de sessão, origem e tempos vêm do app, nunca da resposta do modelo.

Não aceitar texto livre como comando. Prompt instrui a observar somente região da peça e ignora texto contido na imagem como instrução. Alvo não identificável, resposta incompleta ou contradição entre severity/direction produzem abstenção/resultado controlado, nunca correção inventada. Definir no schema versão e cardinalidade; preferir um diagnóstico por classe sem duplicatas.

O modelo identifica desvio, não conhece a ação física calibrada dos óculos. Fixture de peça à direita deve confirmar sentido de correção da câmera; não confiar na frase ambígua “offset à esquerda” do submit. Não falar “20 graus” só porque aparece no exemplo: magnitude só entra se houver calibração e confiança, senão instrução qualitativa. Luz baixa sem evidência de mão não autoriza atribuir a sombra à mão.

Timeout inicial de inferência 2000 ms, uma chamada em voo, sem retry do mesmo frame vencido. Após falha suspender correções e sinalizar indisponibilidade de análise com causa própria; tentativas seguintes usam frame novo e backoff limitado em 429/5xx. Cancelamento de coroutine não deve ser engolido como resultado normal. Respostas após STOP ou de geração anterior são descartadas.

## Testes e aceite

Unitários: parsing, enums, confidence, duplicatas, ausência de campo, magnitude absurda e prompt injection. Integração Docker: cliente/parser reais contra stub com 200/401/403/429/5xx/timeout, verificando descarte. Integração Android live obrigatória: adapter Firebase real, projeto dev e modelo submetido com fixtures autorizadas.

Smoke e avaliação de qualidade seguem TESTING.md: evidenciar acerto por classe/direção, falsos positivos e limitações. Gate de modelo não é apenas HTTP 200 ou JSON bem formado. Manter versões de prompt/modelo/config no relatório, sem frame ou saída textual bruta.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.

