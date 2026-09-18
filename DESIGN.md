# Olivia: sistema visual Android

## Direção aprovada

Composição B aprovada em 06/09/2026: título leve no branco, painel azul com margens,
resumo compacto sem cards e navegação inferior com botão circular elevado.
Referência visual é o estudo B da conversa, implementado em Compose, sem imagens
rasterizadas de texto ou controles. Usar cores sólidas e peso 300 nos títulos,
conforme o plano aprovado, em vez de reproduzir texturas ou traços ultrafinos do estudo.

## Paleta e superfícies

60/30/10 descreve o equilíbrio visual aproximado, não uma contagem de pixels.
O painel cresce com o conteúdo e a fonte; não impor 30% de altura às telas.

| Papel | sRGB para Compose |
| --- | --- |
| Fundo | #F1F3F7 |
| Superfície | #FAFBFD |
| Painel principal e ação/seleção | #A9D3F2 |
| Ação principal (topo) | #B8DCF4 |
| Ação principal (base) | #86BCE1 |
| Texto sobre ação principal | #FAFBFD |
| Texto principal e no azul | #151A21 |
| Texto secundário no branco | #526174 |
| Texto secundário no azul | #526174 |
| Superfície de seleção suave | #E8EEF5 |
| Contorno funcional | #718198 |
| Divisória decorativa | #DCE3EC |

Neutros têm matiz frio. As cores sRGB aprovadas são os valores canônicos no app.
Não usar cores dinâmicas do sistema que substituam essa paleta. A ação principal
em gradiente usa os tons azuis aprovados em CTAs do onboarding, com sombra azul
suave; o azul canônico #A9D3F2 continua sendo a base do tema.

## Tipografia e espaço

Fonte sans-serif nativa, sem download. Título 32/40sp peso 300, seção 24/32sp
peso 400, corpo 16/24sp peso 400, ação 14/20sp peso 500, navegação 12/16sp peso 500.
Margens laterais 24dp; intervalos 8, 16, 24 e 32dp. Painéis com raio 20dp e padding
24dp. Conteúdo rolável, com largura limitada em telas grandes.

## Telas e componentes

- Sem títulos de página (Gravar, Sessões, Ajustes). A orientação vem do
  painel e da barra inferior.
- Welcome/onboarding: tela inicial, com fundo claro e três etapas fixas no mesmo
  padrão visual. A etapa final solicita uma permissão por vez: microfone e, no flavor de
  óculos, Bluetooth. Sem conta. Pareamento e autorização dos óculos ficam no Meta AI.
- Início: painel com Dispositivo e Ray-Ban Meta; Últimos takes em cards (miniatura,
  título, badges de duração e data abaixo do título, seta) e atalho ver todos para Sessões.
  Sem inventar takes.
  Sem selo Modo demonstração ou texto Óculos simulados. A indicação do backend
  continua na tela Gravar. Sem botão Gravar agora ou frase Pronta para.
- Gravar: comando por voz e disponibilidade no painel; configurações abaixo.
  Não alegar que o app está escutando ou que os óculos estão conectados.
- Sessões: estado vazio no painel, sem dados inventados ou novas telas de revisão.
- Ajustes: aba do menu. Seleção Voz/Sinal no painel, perfil e privacidade em
  seções abertas. A seleção continua local à tela, sem nova integração com o
  motor de áudio. Sem botão Voltar.
- Menu: cápsula flutuante baixa com Início, Sessões e Ajustes. Gravar fica fora,
  à direita, na mesma altura, como anel azul com disco interno, sem rótulo.
  Superfície sólida 50% de raio, margem lateral 24dp, 8dp do fundo, contorno, sem
  sombra. Círculo e abas formam alvos acessíveis; abrir Gravar não inicia take.

## Movimento

Navegação: fade com deslocamento de 8dp em 220ms. Interações e seleção: 150–180ms.
Curva ease-out-quart, sem molas, loops ou entradas sequenciais. Usar animações
Compose que respeitam a escala de duração do Android, inclusive zero.

## Rastreabilidade e validação

Refinamento visual de FE-01 (SH-03, SH-04, FE-00) e FE-05 (FE-01, FE-04), com
onboarding apoiado por INT-00 para as permissões do DAT.
Não conclui essas tasks funcionais por si só. Contratos, hardware, IA e privacidade
permanecem definidos nos JSON de docs/rules/.

Compilar :app:assembleMockDebug com Java 21, instalar e reabrir no emulador.
Conferir as telas do onboarding, toque central, seleção de áudio, fonte ampliada,
animações desativadas e insets. Não executar lint ou testes extras sem solicitação.
