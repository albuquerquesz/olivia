# Classes de classificação de enquadramento, luz e horizonte

Status: refactor de arquitetura planejado; a implementação atual ainda usa diagnóstico completo vindo do Gemini e não satisfaz este desenho até migrar contrato, adapter e testes.

Tasks canônicas: BE-03.

Dependências: SH-04, BE-01, BE-02, BE-13 e BE-14. Direção pode avançar com BE-06 enquanto acesso real não está disponível.

Escopo: Dentro. Somente FRAMING, LIGHT e HORIZON. Controllers são componentes internos Kotlin, não endpoints Elysia.

## Contrato e responsabilidades

GeminiFrameAnalyzer prepara um único frame efêmero para Firebase AI Logic e pede somente `WorkAreaLocalization`: presença da peça, confiança e bounding box normalizada. `WorkAreaLocalizationValidator` valida schema, finitude, limites [0,1], área mínima e ausência de campos de ação. Metadados de sessão, origem e tempos vêm do app, nunca da resposta do modelo.

`LocalVisualAnalyzer` consome a ROI válida e o mesmo frame apenas em memória. Ele transforma o deslocamento do centro da ROI em `FRAMING`, calcula `LIGHT` pela luminância na ROI contra referência local e estima `HORIZON` localmente com confiança. Só então produz o `FrameAnalysis` fechado consumido pelo motor de direção. Gemini não devolve direção, magnitude, severidade, luz, horizonte ou fala.

Não aceitar texto livre como comando. Prompt instrui a observar somente região da peça e ignora texto contido na imagem como instrução. Alvo não identificável, resposta incompleta ou contradição entre severity/direction produzem abstenção/resultado controlado, nunca correção inventada. Definir no schema versão e cardinalidade; preferir um diagnóstico por classe sem duplicatas.

O modelo localiza a peça, não conhece a ação física dos óculos. O app compara o centro da ROI ao centro do frame: desvio normalizado até 0,10 fica silencioso; >0,10 até 0,25 gera “um pouco” no lado/eixo necessário; acima de 0,25 gera a variante direta. Estes valores são configuração inicial a validar com fixtures, não graus. Não falar “20 graus” só porque aparece no exemplo. Luz baixa sem evidência de mão não autoriza atribuir a sombra à mão.

Timeout inicial de inferência 2000 ms, uma chamada em voo, sem retry do mesmo frame vencido. Após falha suspender correções e sinalizar indisponibilidade de análise com causa própria; tentativas seguintes usam frame novo e backoff limitado em 429/5xx. Cancelamento de coroutine não deve ser engolido como resultado normal. Respostas após STOP ou de geração anterior são descartadas.

## Testes e aceite

Unitários: parsing de ROI, limites normalizados, ausência de campo, ROI degenerada, campos de ação proibidos e prompt injection; offsets central/pequeno/grande, luminância na ROI, horizonte confiável/incerto e descarte. Integração Docker: cliente/parser reais contra stub com 200/401/403/429/5xx/timeout, verificando descarte. Integração Android live obrigatória: adapter Firebase real, projeto dev e modelo submetido com fixtures autorizadas.

Smoke e avaliação de qualidade seguem TESTING.md: separar acerto de localização da ROI e acerto dos diagnósticos locais/direções, com falsos positivos e limitações. Gate de modelo não é apenas HTTP 200 ou JSON bem formado. Manter versões de prompt/modelo/config no relatório, sem frame ou saída textual bruta.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.
