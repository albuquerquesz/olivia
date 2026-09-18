# Transcrição local, cortes sugeridos e handoff

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: BE-08, BE-09; FE-07/FE-08 como interfaces de revisão/exportação.

Dependências: SH-04, BE-12 para mídia e relógios; BE-05 para marcas; BE-14 para integração. UI de revisão é consumidora, não redesenhada.

Escopo: Dentro. Somente pós-take, marcas/cortes/legendas e entrega ao editor existente.

## Pipeline

PostProcessor só inicia após STOPPED e recurso de mídia pronto: PROCESSING→READY, erro controlado ou cancelamento. Nova sessão de direção tem prioridade e suspende/cancela trabalho pesado segundo política explícita, sem perder o resultado anterior.

LocalWhisperTranscriber extrai áudio localmente e transcreve por Whisper no telefone. A decisão
inicial fixa `whisper.cpp` com `ggml-base.bin` multilíngue; `ggml-tiny.bin` multilíngue é o
fallback somente se benchmark no aparelho-alvo não fechar o tempo aceitável. Variantes `.en` não
são permitidas porque a demo é pt-BR. Antes de integrar, registrar versão do runtime, URL/origem,
SHA-256, licença, consumo e instalação offline. Docker usa o mesmo modelo para integração
funcional, mas não prova velocidade no Android. Se modelo faltar ou transcrição falhar, preservar
timeline e informar revisão parcial, sem texto inventado.

Validar TranscriptSegment: início ≥0, fim>início, fim≤duração, ordem, locale e limites de texto. Cortes são intervalos sugeridos para excluir, em tempo de mídia, coerentes com CutReason existente: UNCORRECTED_DEVIATION, DEGRADED_STRETCH, SPEECH_RETAKE e SILENCE. Não afirmar que uma instrução “recentrou” significa descartar tudo após ela.

CutPlanner aplica heurística versionada: trecho pendente sem correção utilizável, intervalo degradado, fala explícita de refação e silêncio aferido na faixa (ausência de transcrição sozinha não prova silêncio). Unir sobreposições, limitar à duração e conservar justificativa; não cortar sessão inteira sem explicitar ausência de material aproveitável. Pausa de direção pode coexistir com gravação contínua.

ReviewExporter produz JSON versionado de marcas/cortes e SRT quando houver fala, mais referência compartilhável ao vídeo local via permissão temporária. FE-08 faz spike de consumo real pelo editor: compartilhar JSON/SRT não prova que CapCut importa ambos ou aplica cortes. Documentar o caminho manual suportado sem construir editor/renderizador.

## Testes e aceite

Unitários: segmentos, limites, offset, sobreposição, texto SRT e heurísticas. Integração Docker: vídeo fixture→extração→Whisper real→CutPlanner→artefatos; execução offline com modelo provisionado. Integração Android: mesmo arquivo/modelo, ciclo STOPPED/PROCESSING/READY e cancelamento com limpeza.

Aceite exige artefatos consumíveis, cortes em posições corretas e resumo calculado do resultado (nunca “quatro cortes, 32 segundos” fixo). Medir separadamente importação, extração, transcrição e geração; ~8 s do submit é meta, não promessa validada.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.
