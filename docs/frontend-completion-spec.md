# Spec de conclusão do frontend — Olivia

**Escopo:** FE-02 (`OLI-10`), FE-03 (`OLI-11`), FE-04 (`OLI-12`), FE-08 (`OLI-16`) e FE-09 (`OLI-17`).

**Objetivo:** fechar o frontend do protótipo com implementação, testes e evidência de demo suficientes para mover as issues para `Done`, sem ampliar o produto.

**Fontes de verdade:** `docs/rules/architecture-diagram.json`, `docs/rules/olivia-contracts.json`, `docs/rules/meta_wearables_dat_android.json`, `docs/rules/olivia_backlog.json`, `PRODUCT.md`, `DESIGN.md` e `olivia-escopo-final`.

**Estado no Linear em 16/09/2026:** as cinco issues estão `In Progress`.

**Atualização após a implementação local:** os commits `0c561a9`, `3ebdeb2` e
`e558475` entregaram a maior parte do código do vertical slice. Esta versão da
spec separa o que já está no repositório do que ainda precisa ser executado,
medido ou desbloqueado antes de mover qualquer issue para `Done`.

| Issue | Dependências | Situação para fechamento |
| --- | --- | --- |
| FE-02 / `OLI-10` | FE-00 concluída | Máquina, mock e cobertura determinística ampliados; testes executados com sucesso, mas ainda faltam ensaio e evidência no aparelho. |
| FE-03 / `OLI-11` | FE-02, FE-00 | Fluxo local, estados e fallback presentes; faltam o `.ppn`, aparelho configurado, validação offline e ensaio de alcance. |
| FE-04 / `OLI-12` | SH-04, FE-00 | WAVs locais, mapeamentos, fallback de TTS e testes determinísticos presentes; falta validar headset e registrar evidência de áudio. |
| FE-08 / `OLI-16` | FE-07 concluída, BE-12 em andamento | JSON, `FileProvider`, chooser e fixture `RecordingSource` do mock presentes; falta o vídeo nativo e a evidência de handoff. |
| FE-09 / `OLI-17` | FE-02, FE-04 | Watchdog, proteção contra frames atrasados e cobertura executada do cenário mock presentes; faltam ensaio e reconexão DAT. |

## Decisão de conclusão

Uma issue só pode ser concluída quando houver:

1. código implementado no adapter correto;
2. testes automatizados para as regras determinísticas;
3. ensaio manual no mock, com headset Bluetooth quando houver áudio;
4. evidência registrada no Linear;
5. nenhuma afirmação de hardware, latência ou bateria sem medição real.

Há dois níveis de aceite:

- **Vertical slice mock:** FE-02, FE-03, FE-04 e o comportamento simulado de FE-09 podem ser validados offline, sem óculos físicos.
- **Produto completo:** FE-08 depende de BE-12 para receber o vídeo nativo; FE-09 e o roteamento final dependem do adapter DAT/real de BE-00 e INT-04. Essas dependências não podem ser escondidas marcando o frontend como validado em hardware.

## Estado atual e lacunas

### FE-02 — máquina de estados (`OLI-10`)

**Já existe:** `SessionRuntime`, `ScenarioGlassesGateway`, watchdog no
`OliviaSessionViewModel`, timeline de eventos, cenário determinístico e
testes de transições inválidas, limiar de quatro segundos, sessão/frame
estrangeiro e frame atrasado em `apps/android/app/src/test`. O teste de
orquestração do `OliviaSessionViewModel` também cobre pausa, retomada, queda,
recuperação, término, cadência de 2 s e o gap do mock.

**Falta para fechar:**

- provar no ensaio que o mock emite frames a cada 2 s, cria o gap de 4 s,
  recupera sem reiniciar a sessão e mantém as marcas anteriores;
- validar no Android que `STARTED`, `PAUSED`, `DEGRADED` e `STOPPED` aparecem
  com texto, contraste e ação disponível coerentes;
- instalar o APK e registrar o resultado do ensaio neste ciclo.

**Aceite:** testes verdes + ensaio mock com início, pausa, retomada, queda, recuperação e término; a timeline precisa manter os eventos anteriores à queda.

### FE-03 — comandos de voz locais (`OLI-11`)

**Já existe:** contrato fechado para `gravar`, `pausar`, `retomar` e
`terminar`; integração preparada para Porcupine e reconhecimento on-device;
permissão de microfone; ciclo de vida; fallback de debug no mock; estados de
disponibilidade e erro; aviso de disponibilidade também na tela `Gravar`.

**Falta para fechar:**

- obter o modelo customizado Android `.ppn` de “Oi, Olivia” e colocá-lo em
  `apps/android/app/src/main/assets/olivia_android.ppn`; não criar placeholder
  nem commitar a chave, que deve permanecer em `local.properties`/ambiente;
- validar em aparelho Android API 31+ com reconhecimento on-device disponível
  e permissão `RECORD_AUDIO` concedida;
- confirmar em modo avião que wake word e comandos continuam funcionando sem
  rede;
- testar os quatro comandos, comando fora da lista, comando em estado inválido
  e retorno ao wake word após cada tentativa;
- testar o celular distante na bancada e confirmar retorno audível para
  sucesso, erro e entrada indisponível;
- garantir, por inspeção do APK/flavor e ensaio, que o fallback de debug só
  aparece no backend mock e não é apresentado como fluxo principal;
- registrar aparelho/API, estado da permissão, conectividade e resultado de
  cada comando no comentário da issue.

**Aceite:** os quatro comandos controlam a sessão sem toque, cada comando produz feedback curto, nenhuma fala é enviada à nuvem e o app informa honestamente quando o recurso não está configurado.

### FE-04 — feedback de áudio (`OLI-12`)

**Já existe:** API de core sem conhecimento de hardware; modo Voz com TTS do
Android; modo Sinal com WAVs locais de aproximadamente 80 ms e distinção
estéreo esquerda/direita; mapeamento determinístico de cada `Direction` e
`SessionSignal`; fallback observável quando TTS falha; fila que não corta um
aviso crítico de conexão; sinais de início, pausa, retomada, término, queda e
recuperação.

**Falta para fechar:**

- validar no aparelho que os WAVs versionados chegam como PCM, estéreo, 16 kHz
  e aproximadamente 80 ms;
- validar saída em headset Bluetooth comum nos modos Voz e Sinal;
- simular TTS indisponível e confirmar no aparelho que há erro observável,
  fallback audível e nenhum silêncio enganoso;
- confirmar no ensaio que uma fala nova não corta aviso crítico de queda ou
  retomada;
- registrar modelo do headset, modo de áudio, rota de saída e resultado; não
  declarar roteamento para os óculos antes do teste de hardware.

**Aceite:** correções e sinais chegam ao headset, o sinal tem duração curta e distingue esquerda/direita, e a API continua recebendo apenas `Correction`/`SessionSignal`, nunca um tipo de óculos.

### FE-08 — handoff para CapCut (`OLI-16`)

**Estado atual:** o frontend agora possui `OliviaMarkersJson`, contrato
`olivia-markers.json`, `SessionExportAdapter`, `AndroidSessionExportAdapter`,
`FileProvider`, chooser Android, estados de sucesso/falha/indisponibilidade e
um botão de exportação na revisão. O flavor `mock` entrega um `RecordingAsset`
por fixture local reproduzível e prepara vídeo + JSON depois de `STOPPED`; o
flavor `glasses` ainda retorna `RecordingSource = null` até BE-12 fornecer o
vídeo nativo.

**Falta para fechar:**

- concluir com BE-12 o recebimento de um `RecordingAsset` pelo caminho nativo
  dos óculos; o frontend deve apenas consumir o contrato, sem reescrever o loop
  ao vivo;
- demonstrar no ensaio mock o vídeo + `olivia-markers.json` compartilhados antes
  do hardware;
- validar no ensaio que vídeo e JSON são compartilhados juntos depois de
  `STOPPED`, sem bloquear análise, áudio ou watchdog durante o take;
- fazer o spike do caminho suportado no sistema alvo; se CapCut não aceitar
  integração direta, documentar o fluxo manual demonstrável;
- testar conteúdo do JSON, versão do contrato, URI `content://`, ausência de
  frames/áudio bruto/credenciais e comportamento sem vídeo disponível;
- confirmar que a UI não afirma que o CapCut importou algo: ela só deve
  informar que os arquivos foram preparados/compartilhados.

**Aceite:** após o take, a pessoa consegue compartilhar o vídeo nativo e o artefato de marcação; a integração direta não é requisito, mas o handoff manual precisa ser reproduzível e documentado.

### FE-09 — queda e retorno (`OLI-17`)

**Já existe:** timeout configurável de 4 s, transição para `DEGRADED`, overlay
na UI, preservação da timeline, tom de queda/retomada e recuperação quando
chega um frame posterior no cenário mock; há testes para o limiar, pausa,
frames atrasados e frames fora da sessão.

**Falta para fechar:**

- manter o teste de orquestração que confirma que o polling de 250 ms não
  degrada antes de 4 s e degrada na primeira janela após o limiar;
- confirmar no ensaio que pausa não dispara watchdog, a queda não apaga marcas
  e a recuperação não cria uma nova timeline;
- adicionar uma asserção explícita de que os sinais de queda/retomada são
  distintos do sinal de pausa;
- explicitar no adapter real a tentativa de reconexão e o retorno a `STARTED`;
  o mock não substitui a validação DAT;
- fazer dois ensaios manuais: caminho saudável sem falso alerta e caminho com
  gap de frames, guardando os eventos observados;
- não marcar a integração de hardware como concluída antes de BE-00/INT-04.

**Aceite:** a ausência de frame nunca fica silenciosa; o estado, o áudio e a timeline contam a mesma história antes, durante e depois da queda.

## Correções de conformidade visual e de produto

**Status atual:** as correções de produto listadas abaixo foram aplicadas nos
commits desta implementação. Resta somente a inspeção manual no APK para
confirmar margens, rolagem, contraste, escala de fonte e `contentDescription`.

O aceite visual precisa confirmar que continuam verdadeiras estas decisões:

- remover da Home o CTA “Começar a gravar”; o `DESIGN.md` reserva a entrada de gravação para o botão circular da navegação e diz que a Home não deve ter “Gravar agora”;
- remover a waveform com `rememberInfiniteTransition`; o movimento aprovado não possui loops contínuos;
- não exibir na Home `SIMULADO`, “Bluetooth 5.3 · pareado” ou métricas inventadas de bateria, armazenamento e câmera como se fossem telemetria real; a indicação de simulação deve ficar na tela `Gravar`, e dados não medidos não podem ser apresentados como fato;
- colocar o estado/disponibilidade de voz no painel de `Gravar`, sem dizer que o app está escutando quando ele apenas está disponível;
- manter a entrada `Gravar` fora da cápsula das abas, com alvo acessível de pelo menos 48 dp;
- verificar margens, rolagem, contraste, escala de fonte e `contentDescription` nas quatro telas;
- manter a interface clara, sem display nos óculos, sem contas, feed, editor ou comandos fora do vocabulário fechado.

## Invariantes que não podem regredir

- telefone é a ponte obrigatória do DAT;
- estados de produto: `STARTED`, `PAUSED`, `STOPPED`, `DEGRADED`;
- frames 720×1280, amostragem de 0,5 fps/2 s e degradação após 4 s sem frame;
- análise limitada a enquadramento, luz/sombra na peça e horizonte;
- wake word e comandos rodam localmente no celular; somente os quatro comandos definidos;
- saída para a pessoa é áudio; não depender de display nos óculos;
- vídeo 3K e áudio nativos entram somente depois de `STOPPED`;
- frames de análise são efêmeros; timeline não contém frame, áudio bruto ou credencial;
- mock é determinístico e offline; adapter real substitui a fonte sem reescrever o fluxo;
- não usar `mwdat-display` nem criar editor de vídeo próprio.

## Plano de implementação para concluir

### Fase 0 — preparar dependências sem alterar o escopo

- [ ] Obter o `.ppn` oficial de “Oi, Olivia”; não fabricar um artefato e não
  commitar a chave do Porcupine.
- [ ] Confirmar disponibilidade do aparelho Android API 31+ e de um headset
  Bluetooth comum.
- [ ] Manter BE-12 responsável por `RecordingAsset`/fixture de vídeo e BE-00
  responsável pelo adapter de frames DAT; o frontend não pode contornar esses
  contratos com APIs do SDK espalhadas pela UI.
- [ ] Manter a integração real de hardware como dependência de INT-04. Não
  transformar uma validação mock em alegação de hardware.

### Fase 1 — fechar o vertical slice offline (FE-02 e FE-09)

- [x] Adicionar o teste de orquestração de sessão com pausa, retomada, queda,
  recuperação e término, incluindo a lista de eventos da timeline.
- [x] Exercitar o mock com frames de 2 s e gap maior que 4 s; confirmar que
  marcas anteriores permanecem e que a sessão não reinicia.
- [x] Cobrir explicitamente o polling de 250 ms, a pausa sem watchdog, o
  frame atrasado/fora de sessão e a distinção entre os sinais de pausa e
  conexão.
- [x] Executar a suíte unitária do flavor mock e guardar o resultado.
- [ ] Fazer o ensaio manual saudável e o ensaio com gap; capturar estado,
  áudio e timeline observados.

### Fase 2 — fechar voz local (FE-03)

- [ ] Adicionar o `.ppn` ao caminho de assets e configurar a chave somente em
  `local.properties` ou ambiente.
- [ ] Instalar o APK mock em aparelho API 31+ com `RECORD_AUDIO` concedido.
- [ ] Testar `gravar`, `pausar`, `retomar` e `terminar` em modo avião.
- [ ] Testar comando desconhecido, comando em estado inválido, erro de
  reconhecimento e retorno ao wake word.
- [ ] Repetir com o celular distante na bancada e registrar os retornos
  audíveis de sucesso, erro e indisponibilidade.
- [ ] Confirmar que somente o mock expõe controles de debug.

### Fase 3 — fechar áudio (FE-04)

- [x] Executar os testes de `AudioCue` e validar os WAVs locais: PCM16,
  estéreo, 16 kHz e aproximadamente 80 ms.
- [ ] Validar Modo Voz e Modo Sinal em headset Bluetooth comum.
- [ ] Desabilitar/indisponibilizar TTS e confirmar fallback audível, erro
  observável e continuidade do Modo Sinal.
- [ ] Confirmar que os alertas de queda/retomada não são cortados por uma nova
  correção.
- [ ] Registrar modelo do headset e rota de áudio; não afirmar saída nos
  óculos sem o teste de hardware correspondente.

### Fase 4 — concluir exportação (FE-08)

- [x] Integrar o `RecordingSource` do mock a uma fixture local reproduzível.
- [x] Gerar `olivia-markers.json` a partir da revisão, sem frames, áudio bruto,
  credenciais ou dados de terceiros.
- [x] Compartilhar vídeo e JSON juntos via URI `content://` depois de
  `STOPPED`, sem bloquear o loop ao vivo.
- [ ] Testar sucesso, falha, ausência de vídeo, conteúdo do JSON e URI
  compartilhada.
- [ ] Fazer o spike de CapCut; se não houver integração direta, documentar e
  reproduzir o handoff manual usando os dois arquivos.
- [ ] Substituir a fixture pelo adapter nativo somente quando BE-12/INT-04
  estiverem disponíveis e medidos.

### Fase 5 — evidência e fechamento no Linear

- [x] Executar `:app:testMockDebugUnitTest` e `assembleMockDebug`.
- [ ] Instalar o APK e anexar/registrar o resultado dos ensaios nas cinco
  issues.
- [ ] Registrar dependências ainda abertas, especialmente BE-12, BE-00 e
  INT-04, sem marcar o frontend como validado em hardware.
- [ ] Só depois da revisão dos critérios abaixo, mover cada issue para
  `Done` individualmente.

## Critérios de fechamento por issue

| Issue | Código mínimo | Evidência obrigatória | Bloqueio atual |
| --- | --- | --- | --- |
| FE-02 / `OLI-10` | Orquestração completa e mock determinístico | Teste verde + ensaio dos quatro estados e timeline | Nenhum estrutural; falta executar e registrar |
| FE-03 / `OLI-11` | `.ppn`, comandos locais e fallback mock | Aparelho API 31+, modo avião, quatro comandos e erros | Modelo `.ppn` e aparelho |
| FE-04 / `OLI-12` | Cues WAV, TTS/fallback e fila de avisos | Headset Bluetooth em Voz e Sinal | Headset e execução |
| FE-08 / `OLI-16` | `RecordingAsset`, JSON e share conjunto | Vídeo + JSON compartilhados e handoff CapCut reproduzido | BE-12 / `OLI-29` |
| FE-09 / `OLI-17` | Watchdog, timeline e reconexão por adapter | Ensaio saudável + gap + recuperação | BE-00 / `OLI-40` e INT-04 / `OLI-33` para hardware |

## Checklist final de evidência

- [x] `:app:testMockDebugUnitTest` executado e verde.
- [x] `assembleMockDebug` executado e APK gerado; instalação ainda pendente.
- [ ] Mock ensaiado com os quatro estados e queda/recuperação.
- [ ] Wake word e quatro comandos testados sem rede.
- [ ] Headset Bluetooth testado em Voz e Sinal.
- [ ] Vídeo + `olivia-markers.json` compartilhados após um take.
- [ ] Handoff manual para CapCut reproduzido ou integração direta comprovada.
- [ ] Nenhuma medição de hardware, latência ou bateria alegada sem medição real.
- [ ] Comentários/evidências adicionados às cinco issues no Linear.
- [ ] Só depois: FE-02, FE-03, FE-04, FE-08 e FE-09 movidas para `Done`.

Build, testes e validações de hardware permanecem pendentes até autorização explícita e disponibilidade dos dispositivos; esta spec não afirma que foram executados.
