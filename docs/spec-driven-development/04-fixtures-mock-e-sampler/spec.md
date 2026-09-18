# Fixtures, analisador mock e amostragem

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: BE-01, BE-02, BE-06.

Dependências: SH-04; BE-01 para sampler; BE-14 para integrar no Docker. Não depende de Gemini ou de BE-00 para aceite offline.

Escopo: Dentro e suporte necessário. Preserva a amostragem de 0,5 fps e a demonstração determinística offline.

## Fixtures e reprodução

Manifesto de fixtures inclui ID, proveniência, autorização/licença, formato/dimensões, orientação, ROI rotulada e sequência temporal. Para o novo pipeline, o oráculo separa localização esperada da peça, classe/direção determinística esperada e faixa de offset: central (≤10%), pequeno (>10–25%) ou grande (>25%) nos quatro eixos. Cobrir centralizado, fora do quadro, sombra versus baixa luz, horizonte, alvo ausente e sinais conflitantes. Não versionar frames de sessão real sob o nome fixture.

MockFrameAnalyzer produz sequência conhecida, inclusive latência, timeout, 429 equivalente, erro de schema e retorno atrasado. FrameOrigin e AnalyzerSource registram separadamente origem da câmera e origem da análise: câmera real com análise mock não é integração IA real.

Sampler seleciona no máximo um frame a cada 2000 ms. Uma análise em voo e no máximo um frame mais recente aguardando; substituir/liberar intermediários, não criar fila crescente. No momento de enviar, confirmar idade máxima do frame (inicial 2000 ms), sessão e estado. A cada ciclo usar o mais recente; nunca repetir requisição de um frame vencido.

Liberar buffers em sucesso, erro, descarte, timeout e cancelamento. O watchdog observa captura, não a agenda de inferência. Mudança de sampling deriva os dois knobs de uma configuração única resolvida em SH-04.

## Testes e aceite

Unitários: relógio virtual, cadência, taxa configurável, fonte rápida, analisador lento e liberação contabilizada. Integração Docker: fonte de bytes concreta→sampler→adapter stub/validador; sequência fixa reproduz dois desvios e confirmações. Falha de internet não altera mock offline.

Gate: não exceder 30 análises por janela de 60 s no padrão (definir janelas semiabertas para evitar erro de contagem na borda), sem acúmulo de buffers; cenários repetem os mesmos eventos com relógio virtual. Sampling menor não causa DEGRADED artificial.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.
