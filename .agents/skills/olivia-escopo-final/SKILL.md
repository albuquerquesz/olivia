---
name: olivia-escopo-final
description: Guardrail de produto para o protótipo Olivia. Use antes de planejar, implementar ou revisar mudanças para manter o escopo final do fluxo mãos-livres de gravação com Ray-Ban Meta e impedir expansão não aprovada.
---

# Escopo final — Olivia (protótipo)

Esta skill é o contrato de produto do protótipo. Antes de aceitar uma mudança, classifique-a como:

- **Dentro:** implementa diretamente o fluxo definido abaixo.
- **Suporte necessário:** infraestrutura, estados, testes, mocks ou tratamento de erro indispensáveis para o fluxo, sem criar uma nova promessa de produto.
- **Fora:** muda usuário, hardware, canais, finalidade, latência, privacidade ou comportamento do protótipo.

Mudanças fora do escopo não devem ser implementadas silenciosamente. Pare, explique qual limite seria ultrapassado e peça aprovação explícita.

## Promessa do produto

Olivia ajuda creators que trabalham com as mãos ocupadas a gravar vídeos verticais curtos com os Ray-Ban Meta. Durante o take, ela observa o enquadramento, a luz na região da peça e a inclinação do horizonte, avisando por áudio quando uma correção precisa chegar a tempo. Ao terminar, entrega marcas de direção e cortes sugeridos para continuar a edição no CapCut.

O protótipo evita que o creator descubra somente depois da gravação que a peça saiu do quadro, a mão fez sombra ou a imagem ficou torta — situações que obrigariam a refazer o serviço.

## Usuária de referência

Mariana, 31 anos, tatuadora com estúdio próprio em Ipanema. Grava para Instagram e TikTok de duas a três vezes por semana. Durante a sessão está de luva, com a máquina na mão, não pode tocar no celular nem sair da posição e aproveita menos da metade das tentativas.

O cenário de validação é Mariana finalizando uma tatuagem no antebraço, querendo um Reels de 30 segundos, com o celular na bancada e pareado aos Ray-Ban Meta.

Não generalize o protótipo para “qualquer creator”, qualquer câmera ou qualquer indústria sem solicitação explícita.

## Fluxo principal obrigatório

1. Mariana diz **“Oi, Olivia, Gravar”**. O microfone do celular executa o wake word local via Porcupine. O app chama `startSession()` do Device Access Toolkit; a sessão passa para `STARTED` e toca um tom curto de confirmação.
2. Ela volta para a máquina. A câmera dos óculos fornece um stream de 720×1280 via Bluetooth. Olivia amostra um frame a cada dois segundos (0,5 fps) e envia o frame ao Gemini 3.5 Flash-Lite via Firebase AI Logic SDK para classificar enquadramento, luminância na região da peça e inclinação do horizonte. Se não há correção, não interrompe o trabalho.
3. Em 00:12, a peça vai para o canto direito do quadro. O VLM detecta o sujeito descentralizado e Olivia fala: **“Vire uns 20 graus à direita.”** Mariana corrige a cabeça; o frame de 00:14 confirma a correção, a obediência vira aceite e entra a marca `00:14 · recentrou` na timeline.
4. Em 00:31, a mão projeta sombra sobre a peça. Olivia detecta luminância abaixo do limiar na região de interesse e fala: **“A sua mão está fazendo sombra na peça.”** Em 00:34, a correção é confirmada e marcada.
5. Mariana diz **“Oi, Olivia, pausar”**. O microfone do celular registra o instante, mantém a sessão viva e toca um tom de pausa.
6. Mariana diz **“Oi, Olivia, terminar”**. Olivia encerra a sessão, que passa para `STOPPED`, e toca um tom de fechamento.
7. O vídeo final em 3K sai dos óculos para o celular por Wi-Fi. O app extrai a trilha de áudio, transcreve com Whisper ao fim da sessão e cruza a transcrição com as marcas feitas ao vivo. Olivia informa: **“Peguei quatro cortes, 32 segundos.”** O processamento esperado é de aproximadamente oito segundos.
8. Mariana abre o celular, vê a timeline com marcas de direção e pontos de corte sugeridos pela fala e exporta para o CapCut, que já usa.

Meta do loop de direção: **frame → inferência → áudio em aproximadamente 900 ms no Modo Voz**.

## Exceção obrigatória: queda do Bluetooth

Se o app ficar quatro segundos sem receber frame, o Device Access Toolkit marca a sessão como interrompida:

- parar a análise;
- preservar as marcas já feitas;
- tentar reconectar;
- manter a gravação nativa dos óculos, para que o take não seja perdido;
- tocar um tom de queda diferente do tom de pausa;
- tocar um tom de retomada quando reconectar.

Silêncio não pode mascarar falha de conexão. No caminho saudável, ausência de correção não precisa interromper Mariana; no caminho degradado, a perda do sinal deve ser explicitamente comunicada.

## Decisões técnicas travadas no protótipo

- **Amostragem:** um frame a cada dois segundos, 0,5 fps, em vez de análise contínua. O creator leva de um a dois segundos para reagir; maior frequência aumenta custo e bateria sem ganho perceptível. A referência de eficiência é aproximadamente 60× menos inferências que 30 fps.
- **Modos de saída:** Modo Voz fala a instrução; Modo Sinal usa tons locais de 80 ms, em estéreo, indicando o lado da correção. O modo é escolhido pelo usuário nas configurações e declarado na abertura.
- **Áudio:** transcrição somente ao fim da sessão. Não escutar o microfone em tempo real para gerar legenda ou corte; essas funções não têm urgência.
- **Wake word:** roda localmente no microfone do celular, via Porcupine. Não transferir a ativação para o microfone dos óculos nem enviar o áudio de ativação para a nuvem.
- **Fontes de vídeo:** o stream 720×1280 via Bluetooth é sensor para direção. O vídeo final vem da gravação nativa 3K dos óculos por Wi-Fi. Não montar o vídeo final a partir do stream do SDK.
- **Duração:** sessão pontual de aproximadamente 30–60 segundos, alinhada a vídeo vertical; não é uso contínuo.
- **Privacidade:** frames de análise são efêmeros: enviados, classificados e descartados, sem persistência. Nenhum microfone alimenta a IA durante o take. O vídeo final permanece com o creator; terceiros em cena não viram dado persistido.

## Critérios de aceite do protótipo

Uma mudança só está alinhada quando preserva estes resultados:

- iniciar, pausar e terminar sem tocar no celular durante o trabalho;
- detectar apenas enquadramento, luminância/sombra na região da peça e inclinação do horizonte;
- emitir correções acionáveis pelo áudio e confirmar a correção com um frame posterior;
- registrar as correções e gerar marcas/cortes para o fluxo posterior no CapCut;
- manter o take e as marcas quando o Bluetooth cair;
- respeitar a meta aproximada de 900 ms no loop de direção;
- não persistir frames de análise nem usar áudio ao vivo para alimentar a IA.

## Fora do escopo

Não adicionar ao protótipo, salvo aprovação explícita:

- análise contínua, 30 fps ou reconstrução do vídeo a partir do stream Bluetooth;
- edição de vídeo completa, timeline como editor, renderização final ou substituto do CapCut;
- transcrição, legenda, escuta ou classificação de áudio em tempo real;
- wake word no microfone dos óculos, botão físico ou toque como fluxo principal;
- suporte a outros óculos, câmeras, celulares, wearables ou integrações de hardware;
- novos modos de interação além de Voz e Sinal, ou saída por texto/tela para substituir o áudio;
- armazenamento permanente de frames, áudio de terceiros ou vídeo bruto na plataforma;
- assistente conversacional genérico, comandos não relacionados à sessão ou automações fora da gravação;
- novas métricas de visão além de enquadramento, luminância na peça e horizonte;
- expansão para outros perfis de usuário, setores ou casos de uso como promessa do MVP;
- qualquer feature cujo benefício só exista depois do take quando ela puder ser resolvida pelas marcas e cortes já definidos.

Infraestrutura de API, autenticação, persistência de metadados de sessão, mocks e testes podem ser criados somente quando forem suporte direto ao fluxo acima. Eles não autorizam ampliar a experiência do produto.
