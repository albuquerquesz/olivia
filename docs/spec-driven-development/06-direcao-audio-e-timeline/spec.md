# Motor de direção, áudio e confirmação na timeline

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: BE-04, BE-05, INT-01; FE-04/FE-06 como fronteiras de áudio e consumo.

Dependências: SH-04 e BE-06 para direção; FE-02/FE-03 e BE-02 para ensaio; BE-03 para gate com IA real.

Escopo: Dentro. Fecha orientação→ajuste→confirmação. Não adiciona analytics ou editor.

## Política e eventos

GuidanceEngine recebe diagnósticos, aplica thresholds configuráveis e produz Correction com texto de catálogo local. Valores iniciais de engenharia: confiança ≥0,80, severity MAJOR, cooldown global 4000 ms e no máximo uma correção pendente. Em empate, maior severity/confiança; empate restante FRAMING, LIGHT, HORIZON. Calibrar com fixtures antes de afirmar qualidade física.

CorrectionTracker mantém a pendência até frame posterior da mesma sessão confirmar severity OK e confidence ≥0,80 para a mesma classe. Janela inicial de confirmação 8000 ms; desvio persistente confiável após janela vira IGNORED; ausência de evidência, pausa, degradação ou STOP vira EXPIRED; nunca confirmar por timeout. Instabilidade e baixa confiança não confirmam. SUPERSEDED só quando uma nova prioridade substitui a anterior por política explícita.

Criar evento PENDING quando intenção de áudio é entregue e distinguir falha de emissão de orientação efetivamente ouvida. AudioFeedback confirma início de reprodução ou sinaliza AUDIO_FAILED. Após sucesso, frame posterior fecha OBEYED e marca CORRECTION_RESOLVED com correctionId. Não emitir pós-STOP.

TimelineRepository guarda eventos locais sem bytes e garante ID idempotente e isolamento por sessionId. Para sobreviver a reinício, planejar arquivo JSON versionado por sessão com escrita atômica no armazenamento privado (ou adapter local equivalente documentado); interrupção não pode corromper as marcas já confirmadas. Não inserir eventos de produto em tabelas de autenticação.

TTS é local/offline quando disponível; WAVs locais ~80 ms para Sinal. Queda, pausa e retomadas são distintas. Seleção de voz indisponível e falha BT têm tratamento controlado e não levam conteúdo livre de modelo ao usuário.

## Testes e aceite

Unitários: thresholds exatos, cooldown 3999/4000 ms, desempate, frame anterior, sessão diferente, confirmação, expiração e duplicata. Integração Docker: fonte→análise mock→direção→tracker→timeline concreta; ler após reinício e garantir mesmos eventos. Integração Android: AudioFeedback real e callback/erro; headset BT e depois óculos.

INT-01 deve incluir voz local, ao menos duas correções confirmadas, STOP e timeline, cinco execuções consecutivas de 30–60 s sem toque/debug. Rodada offline com mock não depende de Gemini; rodada real tem aceite separado em INT-03. Não requer polimento da tela.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.

