# Gravação nativa, importação e alinhamento de mídia

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: BE-12; INT-06 consome o conector real.

Dependências: SH-04; spike documental já inicia em 02/INT-00; teste físico depende de acesso aos óculos.

Escopo: Dentro. Arquivo final vem da gravação nativa; streaming é somente sensor de direção.

## Spike antecipado e implementação

BE-12 tem duas entregas: investigação antecipada em M0 e adapter completo neste módulo. Manter tabela com capacidade, versão/modelo/firmware, fonte, passo reproduzível, observado, limitação e estado não verificado/suportado/não suportado.

Provar separadamente: acionar/encerrar take nativo pelo fluxo mãos-livres; streaming DAT e gravação 3K simultâneos; take íntegro com áudio após desconexão Bluetooth; transferência após STOPPED e acesso pelo app. Sessão de câmera do SDK não prova qualquer uma dessas capacidades automaticamente.

RecordingSource lista e importa referência de mídia para uso local, sem upload ao backend. Fixture usa o mesmo contrato. Na ausência de importação automática documentada, seleção explícita de arquivo pelo usuário após o take é fallback de revisão; registrar que isso não satisfaz automaticamente todo A3/A4.

Validar URI/path local, permissão, MIME real, tamanho, duração e presença de áudio antes de decodificar. Não buscar URLs arbitrárias nem aceitar caminho de rede. TransferProgress é monotônico e total coerente; interrupção/cancelamento não deixa arquivo parcial parecer pronto. Não apagar o original do creator.

Mapear tempo da sessão para tempo da mídia: âncora de início e offset, trechos contínuos ou tabela por segmento quando gravação tiver pausas/reinícios. offsetMs da timeline não é automaticamente posição de arquivo. Tolerância inicial para teste de alinhamento: erro absoluto ≤250 ms em marcas conhecidas; incerteza maior bloqueia cortes automáticos e mantém revisão das marcas sem alinhamento enganoso.

## Testes e aceite

Unitários: validação de asset/progresso, limites, mapping com início deslocado e pausa. Integração Docker: fixture de vídeo real→adapter local→extração de metadados→mapeamento; cobrir corrupção, áudio ausente e cancelamento. Integração Android: URI selecionada/transferida, permissões e lifecycle. INT-06 deve usar take real, não apenas fixture com nome “DAT”.

Gate nativo não verificado não impede trabalho offline, mas impede declarar a promessa de preservação do take comprovada. Contradição de capacidade exige decisão explícita; não reconstruir vídeo do stream.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.

