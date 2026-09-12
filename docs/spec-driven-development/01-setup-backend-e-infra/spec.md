# Setup do backend, nuvem e harness de integração

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: BE-13, BE-14; SH-03 como dependência compartilhada.

Dependências: SH-02 e SH-04; o setup Android existente FE-00 é reutilizado, sem revisão de UI.

Escopo: Suporte necessário. Preparar desenvolvimento e testes sem introduzir nova API de produto.

## Fronteiras e infraestrutura

Backend próprio não é requisito do caminho submetido: Elysia/Better Auth/Postgres atuais ficam fora da composição do MVP. BE-13 entrega configuração de ambiente, inventário de dependências e setup protegido de Firebase. Não publicar servidor, criar conta de usuário, banco de sessões, fila, bucket ou proxy de frames.

Configurar um projeto Firebase de desenvolvimento separado, modelo submetido, API habilitada e App Check; verificar mecanismo de attestation para distribuição controlada e dispositivo de demo. Debug token somente em ambiente dev. Aplicar restrições de chave e quotas de projeto. O limite do cliente reduz uso acidental, mas não é proteção suficiente contra abuso.

Seguir TESTING.md para `.env.example`, configuração Android e secrets de CI. Não copiar o .env inteiro ao APK; nenhum PAT DAT ou segredo administrativo embarcado. Modelo/config ausente falha cedo com mensagem sanitizada; modo mock continua independente de acesso à nuvem.

BE-14 entrega Docker Compose de testes, runner JVM e stub HTTP, cenários de falha, perfil Whisper local e entrypoints de CI. Se for necessário manter teste do scaffold Elysia, factory sem listen automático e banco descartável são ferramentas de teste, sem novos endpoints de negócio. Essa refatoração só entra quando a task de harness for implementada.

## Testes e aceite

Unitários: parser de ambiente, allowlist, enum de modo, limites e redaction. Integração Docker: runner↔stub, timeout, conexão recusada e teardown; perfil legado opcional executa migração em Postgres isolado e confirma health/rotas esperadas. Integração Android real: acesso autorizado ao Firebase dev e rejeição quando App Check/config inválidos, sem log de token.

Gate: offline executável sem secrets, live não seleciona mock silenciosamente, nenhum serviço novo exigido para o loop. Configurações externas feitas no futuro devem ser evidenciadas sem credenciais.

Referência de segurança: [Firebase](https://firebase.google.com/docs/ai-logic/security-checklist). A configuração efetiva do projeto ainda precisa de prova.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.

