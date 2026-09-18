# Latência, bateria, integração e evidências de avaliação

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: BE-07, BE-11, INT-02, INT-03, INT-04, INT-05, INT-06, INT-07, INT-08.

Dependências: Módulos 03–09 conforme gate; INT-03 tem versões pré-hardware e hardware. INT-08 não apaga limitações em aberto.

Escopo: Suporte necessário. Medir o fluxo, incluindo utilidade para Mariana, sem plataforma de analytics.

## Instrumentação

BE-07 registra captura, seleção, início/fim de inferência, validação/direção, pedido de áudio e callback de início de reprodução com clock monotônico. p50/p95 de frame→áudio usam pares da mesma chamada. Callback Android não é prova do instante acústico nos óculos; evidência física separada documenta método. Sampling acrescenta espera até próximo frame e não pode ser escondido na latência de inferência.

Meta ~900 ms do submit: registrar amostra, quantis, warm/cold, modelo, aparelho, rede e falhas. Dez inferências reais satisfazem a amostra mínima INT-07 mas não sustentam percentil de cauda robusto. Contar frames recebidos, selecionados, descartados e chamadas; timestamps e IDs bastam, sem mídia.

BE-11 mede duração, taxa e tempo ativo e registra energia separada em celular/óculos com método disponível. Informar resolução da medida, nível inicial/final, condições e duração; 0% em take curto pode significar resolução insuficiente. Redução de 60× em inferências não é redução de 60× em consumo. Tuning mantém defaults visíveis e não mascara ausência de frames.

## Gates e avaliação

INT-02: queda após uma correção confirmada; DEGRADED em 4 s, tom distinto, marca preservada, retomada e processamento final. Repetir com timeout IA e pausa para distinguir causas.

INT-03: evidência por checkpoint — saída IA real/rotulada; origem DAT; áudio nos óculos; fluxo/descarte; estratégia e medida de bateria. Pré-hardware deixa campos físicos pendentes. INT-04/05 provam sensores/saída no hardware; INT-06 prova take e pós-processamento; INT-07 mede.

INT-08 inclui comparação de cenário representativo, consentido, com e sem direção: proporção de trecho aproveitável segundo rubrica prévia (peça visível, luz adequada, horizonte), correções úteis/erradas, necessidade de refação e capacidade de operar sem toque. Não inferir causalidade ou generalizar população a partir de um ensaio. Armazenar apenas relatório agregado autorizado. Impacto 30 pontos precisa desta evidência de valor, além da arquitetura.

Freeze prepara artefato estável, roteiro e backup mock claramente identificado. Se faltar integração nativa, informar o trecho não comprovado; não usar vídeo de fixture como captura real.

## Testes e aceite

Unitários: cálculo de quantis/contadores, dados faltantes e clock. Integração Docker: captura→análise→áudio instrumentado e persistência de relatório com falhas; garantir que métricas não disparam chamadas extra. Integração física depende de equipamentos, não pode ser substituída por Docker.

Pronto quando cada linha da matriz REVIEW tem artefato observado ou limitação explicitamente aberta. Checklist de cinco checkpoints não é promessa de 100 pontos.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.

