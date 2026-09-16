# Setup do Meta Wearables Device Access Toolkit

Status: planejado; implementação e execução de testes pendentes.

Tasks canônicas: INT-00; FE-00 e SH-03 como fundação; início antecipado do spike BE-12.

Dependências: FE-00, SH-01 para INT-00; SH-04 para spike/contratos. Mock local não depende do SDK; os artefatos DAT são públicos no Maven Central, mas registro e hardware continuam gates separados.

Escopo: Dentro do escopo para DAT e suporte necessário para setup. Nenhum uso de display ou novo canal de entrada.

## Setup e fronteira

Reutilizar flavors e version catalog existentes; registrar versão fixa disponível e APIs consultadas. Resolver mwdat-core, mwdat-camera e mwdat-mockdevice pelo Maven Central. Application ID e client token da integração registrada entram apenas por configuração local/CI e não devem ser confundidos com applicationId Android; não imprimir nem versionar esses valores.

Configurar permissões e opt-outs de analytics/crash reporting conforme regras DAT. Tratar rejeição de registro, permissão negada/revogada e ausência de dispositivo como resultados controlados. Não importar tipos DAT em :core.

Distinguir mock local offline de MockDeviceKit: o primeiro não exige instalação do SDK; o segundo comprova integração com o toolkit, mas não prova comportamento físico. Usar a mesma interface de domínio nos dois.

Iniciar BE-12 agora com a matriz de capacidades: iniciar/encerrar gravação nativa por fluxo mãos-livres, coexistir com streaming, manter take na perda BT e importar arquivo com áudio original. Conferir documentação e registrar desconhecidos. Conector completo e relógios de mídia são módulo 07.

## Testes e aceite

Unitários: tradução de status/erros e configuração incompleta. Integração Android/MockDeviceKit: registrar/inicializar, emitir frame, indisponibilidade e recuperação sem reescrever consumidor. Runner Docker cobre somente o contrato normalizado, sem alegar Bluetooth simulado.

Pronto em modo toolkit quando a evidência inclui versão, variante, fixture e frame origin MOCK do mock; hardware real recebe DAT apenas quando efetivamente vem dos óculos. Gate físico fica INT-04/05/06. Não considerar pacote Gradle declarado como SDK integrado.

Veja [subtasks](tasks.md), [diagrama](diagram.mmd), [decisões](../DECISIONS.md) e [contrato de testes](../TESTING.md). Os critérios aqui são obrigatórios para fechar as tasks associadas; nenhum resultado foi medido nesta revisão.
