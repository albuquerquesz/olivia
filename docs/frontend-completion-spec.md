# Spec de conclusão do frontend — Olivia

**Escopo:** FE-02 (`OLI-10`), FE-03 (`OLI-11`), FE-04 (`OLI-12`), FE-08 (`OLI-16`) e FE-09 (`OLI-17`).

**Objetivo:** fechar o frontend do protótipo com implementação, testes e evidência de demo suficientes para mover as issues para `Done`, sem ampliar o produto.

**Fontes de verdade:** `docs/rules/architecture-diagram.json`, `docs/rules/olivia-contracts.json`, `docs/rules/meta_wearables_dat_android.json`, `docs/rules/olivia_backlog.json`, `PRODUCT.md`, `DESIGN.md` e `olivia-escopo-final`.

**Estado no Linear em 16/09/2026:** as cinco issues estão `In Progress`.

| Issue | Dependências | Situação para fechamento |
| --- | --- | --- |
| FE-02 / `OLI-10` | FE-00 concluída | Implementação mock presente; faltam cobertura e evidência de ensaio. |
| FE-03 / `OLI-11` | FE-02, FE-00 | Implementação presente; faltam `.ppn`, aparelho configurado e validação offline. |
| FE-04 / `OLI-12` | SH-04, FE-00 | Implementação presente; falta adequar cue local ao DoD e validar headset. |
| FE-08 / `OLI-16` | FE-07 concluída, BE-12 em andamento | Bloqueada pelo vídeo nativo e ainda sem exportação no app. |
| FE-09 / `OLI-17` | FE-02, FE-04 | Mock presente; faltam testes de limiar e evidência de recuperação. |

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

**Já existe:** `SessionRuntime`, `ScenarioGlassesGateway`, watchdog no `OliviaSessionViewModel`, timeline de eventos, cenário determinístico e testes básicos em `apps/android/app/src/test`.

**Falta para fechar:**

- cobrir em teste as transições inválidas de `pause`, `resume`, `degrade`, `recover` e `stop`, além do `start` já coberto;
- adicionar um teste de orquestração que prove `STARTED → PAUSED → STARTED → STOPPED` e `STARTED → DEGRADED → STARTED` sem perder eventos da timeline;
- provar que o mock emite frames a cada 2 s, cria o gap de 4 s e recupera sem reiniciar a sessão;
- impedir que um frame atrasado seja analisado depois de a sessão ter sido pausada ou encerrada;
- conferir no Android que `STARTED`, `PAUSED`, `DEGRADED` e `STOPPED` aparecem com texto e contraste corretos.

**Aceite:** testes verdes + ensaio mock com início, pausa, retomada, queda, recuperação e término; a timeline precisa manter os eventos anteriores à queda.

### FE-03 — comandos de voz locais (`OLI-11`)

**Já existe:** contrato fechado para `gravar`, `pausar`, `retomar` e `terminar`; Porcupine; reconhecimento on-device; permissão de microfone; ciclo de vida; fallback de debug no mock; estados de disponibilidade e erro.

**Falta para fechar:**

- colocar o modelo customizado Android `.ppn` de “Oi, Olivia” em `apps/android/app/src/main/assets/olivia_android.ppn` e manter a chave somente em `local.properties`/ambiente;
- validar em aparelho Android API 31+ com reconhecimento on-device disponível e permissão `RECORD_AUDIO` concedida;
- confirmar em modo avião que wake word e comandos continuam funcionando sem rede;
- mostrar disponibilidade do comando de voz também na tela `Gravar`; hoje o aviso está concentrado no fluxo da Home;
- testar os quatro comandos, comando fora da lista, comando em estado inválido e retorno ao wake word após cada tentativa;
- testar o celular distante na bancada e confirmar retorno audível para sucesso, erro e entrada indisponível;
- garantir que o fallback de debug só apareça no backend mock e não seja apresentado como fluxo principal.

**Aceite:** os quatro comandos controlam a sessão sem toque, cada comando produz feedback curto, nenhuma fala é enviada à nuvem e o app informa honestamente quando o recurso não está configurado.

### FE-04 — feedback de áudio (`OLI-12`)

**Já existe:** API de core sem conhecimento de hardware; modo Voz com TTS do Android; modo Sinal com distinção estéreo esquerda/direita; sinais de início, pausa, retomada, término, queda e recuperação.

**Falta para fechar:**

- trocar o tom PCM sintetizado em runtime por cues WAV locais de aproximadamente 80 ms, conforme o DoD e o contrato do modo Sinal;
- cobrir a tradução de cada `Direction` e `SessionSignal` para cue/texto em testes determinísticos;
- tratar falha ou indisponibilidade do TTS sem silêncio enganoso: emitir erro observável e manter o modo Sinal utilizável;
- confirmar que uma fala nova não corta indevidamente um aviso crítico de queda/retomada;
- validar saída em headset Bluetooth comum nos modos Voz e Sinal antes de testar os alto-falantes dos óculos.

**Aceite:** correções e sinais chegam ao headset, o sinal tem duração curta e distingue esquerda/direita, e a API continua recebendo apenas `Correction`/`SessionSignal`, nunca um tipo de óculos.

### FE-08 — handoff para CapCut (`OLI-16`)

**Estado:** não implementado no frontend atual. A revisão mostra timeline e cortes, mas não há exportação, `ACTION_SEND`, artefato de marcação ou adapter de compartilhamento. O ViewModel ainda chama o pós-processador com `recording = null`.

**Falta para fechar:**

- concluir com BE-12 o recebimento do `RecordingAsset` e seu caminho local depois de `STOPPED`;
- definir um artefato canônico `olivia-markers.json` contendo versão do contrato, `sessionId`, duração e marcas com `offsetMs`, tipo, rótulo, direção, status e cortes sugeridos;
- gerar o artefato sem incluir frames, áudio bruto, credenciais ou dados de terceiros;
- encapsular o handoff em um adapter de exportação, usando URI `content://`/chooser do Android e sem criar editor próprio;
- compartilhar vídeo e JSON juntos sem bloquear o loop ao vivo;
- fazer o spike do caminho suportado no sistema alvo; se CapCut não aceitar integração direta, documentar o fluxo manual demonstrável;
- exibir estados de exportação, falha e sucesso sem afirmar que o CapCut importou algo quando isso não tiver sido comprovado;
- testar conteúdo do JSON, URI compartilhada, ausência de dados privados e comportamento sem vídeo disponível.

**Aceite:** após o take, a pessoa consegue compartilhar o vídeo nativo e o artefato de marcação; a integração direta não é requisito, mas o handoff manual precisa ser reproduzível e documentado.

### FE-09 — queda e retorno (`OLI-17`)

**Já existe:** timeout configurável de 4 s, transição para `DEGRADED`, overlay na UI, preservação da timeline, tom de queda/retomada e recuperação quando chega um frame posterior no cenário mock.

**Falta para fechar:**

- testar que nunca degrada antes de 4 s e que entra em `DEGRADED` na primeira janela do watchdog depois do limiar (o polling atual é de 250 ms);
- testar que pausa não dispara watchdog, que a queda não apaga marcas e que a recuperação não cria nova timeline;
- testar que os sinais de queda e retomada são distintos do sinal de pausa;
- testar um frame atrasado/fora de sessão para não analisar dados depois de `STOPPED`;
- explicitar no adapter real a tentativa de reconexão e o retorno a `STARTED`; o mock não substitui a validação DAT;
- fazer dois ensaios manuais: caminho saudável sem falso alerta e caminho com gap de frames.

**Aceite:** a ausência de frame nunca fica silenciosa; o estado, o áudio e a timeline contam a mesma história antes, durante e depois da queda.

## Correções de conformidade visual e de produto

Estas correções precisam entrar antes do aceite visual, mesmo não sendo uma issue funcional isolada:

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

## Ordem objetiva de execução

1. Corrigir as não conformidades visuais e os claims não medidos.
2. Fechar testes de FE-02 e FE-09 no mock.
3. Adicionar o `.ppn`, validar FE-03 offline e testar alcance/retorno.
4. Fechar cues, falhas e ensaio de headset de FE-04.
5. Integrar BE-12, gerar JSON e concluir o handoff de FE-08.
6. Registrar evidências por issue no Linear; só então alterar o status para `Done`.

## Checklist final de evidência

- [ ] `:app:testMockDebugUnitTest` executado e verde.
- [ ] `assembleMockDebug` executado e APK instalado.
- [ ] Mock ensaiado com os quatro estados e queda/recuperação.
- [ ] Wake word e quatro comandos testados sem rede.
- [ ] Headset Bluetooth testado em Voz e Sinal.
- [ ] Vídeo + `olivia-markers.json` compartilhados após um take.
- [ ] Handoff manual para CapCut reproduzido ou integração direta comprovada.
- [ ] Nenhuma medição de hardware, latência ou bateria alegada sem medição real.
- [ ] Comentários/evidências adicionados às cinco issues no Linear.
- [ ] Só depois: FE-02, FE-03, FE-04, FE-08 e FE-09 movidas para `Done`.

Build, testes e validações de hardware permanecem pendentes até autorização explícita e disponibilidade dos dispositivos; esta spec não afirma que foram executados.
