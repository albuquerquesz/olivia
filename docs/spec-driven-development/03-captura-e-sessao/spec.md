# Captura de câmera e controle local por microfone

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: BE-00, FE-02, FE-03, FE-09; FE-01 somente consumidor de estado.

Dependências: SH-04 e FE-00 para sessão; INT-00 para adapter real. Unidade e mock local avançam antes do SDK. Áudio usa FE-04.

Escopo: Dentro. Câmera dos óculos para frames; microfone do celular exclusivamente para comandos locais. Nenhum áudio de microfone alimenta IA durante o take.

## Comportamento

SessionCoordinator coordena start/pause/resume/stop idempotentes. Porcupine reconhece ativação local; o adapter de comandos deve documentar modelos/frases pt-BR para gravar, pausar, retomar e terminar. Não assumir que detectar wake word implementa interpretação de todos os comandos.

BE-00 traduz frames e disponibilidade DAT para GlassesGateway, com ownership e liberação de buffers explícitos. Validar formato, dimensões, orientação, espelhamento e timestamp; alvo submetido 720×1280. Configuração entregue diferente fica diagnosticada, não mascarada por metadata fixa.

Frames de captura alimentam saúde antes do sampler. Em STARTED, quatro segundos sem frame levam a DEGRADED com causa de captura, cancelam orientação pendente e emitem sinal distinto de pausa. Em PAUSED, ausência intencional de frames não conta como queda; conexão ainda é observada. Retomada restaura intenção anterior e mantém sessionId/timeline.

STARTING só confirma disponibilidade após evidência de captura; timeout de startup gera falha controlada. STOP invalida a geração de inferências, libera recursos e passa por STOPPING→STOPPED. Toda transição atualiza timeline sem guardar mídia. Não afirmar que isso controla o vídeo nativo: capacidade é BE-12.

## Testes e aceite

Unitários com relógio virtual: start duplicado, STOP durante STARTING, pausa no timeout, retorno durante PAUSED, fronteira 3999/4000 ms, callback após STOP e permissão revogada. Integração JVM/Docker: SessionCoordinator + fonte concreta de fixture + sampler + timeline, interrompendo a fonte. Integração Android: DAT/MockDeviceKit + microfone local + lifecycle/foreground e permissões, sem upload de áudio.

Aceite funcional exige comandos no telefone da bancada, quatro eventos audíveis distintos e recuperação sem perder marcas. Teste com voz gravada sintética é fixture; prova de reconhecimento no ruído do estúdio exige ensaio físico.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.

