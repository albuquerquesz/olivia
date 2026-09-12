# Privacidade, segurança e descarte verificável

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: BE-10; BE-13 configura a nuvem; FE-05 somente comunicação ao usuário.

Dependências: SH-04 para política inicial; BE-00/02/03/08/12/13/14 e INT-00 para fechar evidências. Inicia junto de 01, não apenas após pós-processamento.

Escopo: Suporte necessário e requisito ético do fluxo submetido.

## Modelo de ameaças e tratamento de dados

Ativos: frame efêmero, vídeo local, voz de comandos, transcrição, timeline, tokens e quota de IA. Fronteiras: óculos→Android, memória→filesystem, Android→Firebase, arquivo externo→decoder e app→editor. Adversários/casos: app cliente alterado, conteúdo malicioso na imagem, arquivo inválido, token exposto, log acidental e perda do aparelho.

Frames vivem apenas no pipeline em memória; buffer liberado em todos os caminhos, sem cache, dump, snapshot de teste ou log de payload. Copias gerenciadas pelo SDK/GC precisam de inspeção e limitação de escopo, sem alegar zeroização física garantida. Só bytes do frame selecionado seguem para IA visual autorizada.

Comandos usam microfone local; PCM não persiste e não segue à nuvem. Áudio do take e Whisper permanecem locais. Vídeo original pertence ao creator e não é removido por teardown; temporários de extração/transferência parcial têm dono e são apagados em sucesso, falha, cancelamento e recuperação após crash.

Timeline/review são metadados locais, mas texto transcrito e identificadores ainda podem conter informação pessoal. Armazenamento privado, exclusão iniciada pelo usuário e proteção contra backup indevido devem ser definidos no adapter. Compartilhamento usa apenas URIs selecionadas e permissão temporária; não expor diretórios.

Antes de usar dados reais, registrar provedor Gemini selecionado, projeto/modalidade, região quando aplicável, retenção de prompts/respostas, uso para melhoria/treinamento, logs/monitoramento e controles disponíveis. Não presumir que “descartar no app” controla o provedor. Enquanto isso não estiver esclarecido e compatível, live usa apenas fixtures sintéticas/consentidas sem terceiros.

## Controles e aceite

App Check/quotas/restrições conforme BE-13; nenhum endpoint de upload ou conta adicionados. Opt-outs DAT presentes. Logs por allowlist: ID aleatório, enum, duração, tamanho e código de erro sanitizado. Não registrar transcript, spokenText derivado de conteúdo, URI completa privada ou credencial.

Unitários: redaction, allowlist e ownership de arquivos. Integração Docker: injetar conteúdo marcador em fixture e causar erro/cancelamento; garantir que não aparece em report/log/cache e temporários de execução são removidos. Integração Android: mesma inspeção de sandbox/backup e tráfego do ensaio; testar URI revogada e lifecycle.

Gate ético: política documentada + evidência dos caminhos normais e de falha + configuração real do provedor. Consentimento do ensaio não autoriza armazenamento arbitrário, e não é garantia de nota ou parecer jurídico.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.

