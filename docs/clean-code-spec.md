# Spec de limpeza — `else` desnecessários

**Status:** proposta para execução incremental  
**Escopo:** refatoração de legibilidade no código Kotlin do app Android.  
**Objetivo:** remover `else` que apenas aumenta o aninhamento quando um guard
clause, retorno antecipado ou extração de função deixa o fluxo mais direto,
sem alterar contratos, estados, áudio, hardware, persistência ou comportamento
visual da Olivia.

Esta é uma tarefa de manutenção interna. Ela não amplia o produto e não exige
alteração em `docs/rules/`, porque não muda contrato, dados, privacidade ou
integração de hardware.

## Regra de remoção

Remover um `else` somente quando:

1. o ramo anterior termina o fluxo (`return`, `throw`, `continue` ou equivalente);
2. o resultado continua idêntico para todos os caminhos;
3. a mudança não transforma um fallback necessário em comportamento implícito;
4. testes existentes continuam cobrindo sucesso, erro e fallback.

Não substituir todo `if/else` por outra construção apenas para reduzir a
contagem textual de `else`. A intenção do fluxo é mais importante que a
quantidade de palavras-chave.

## Inventário encontrado

A busca foi feita no repositório excluindo artefatos de build. Foram encontradas
**37 ocorrências em Kotlin** e nenhuma ocorrência em TypeScript, JavaScript ou
Java.

### Candidatos para remoção

| Prioridade | Arquivo e linhas | Ocorrências | Tratamento planejado |
| --- | --- | ---: | --- |
| P1 | `apps/android/app/src/main/kotlin/com/olivia/companion/session/AndroidAudioFeedback.kt:70-84` | 2 | Extrair a inicialização/validação do TTS para uma função com guard clauses. Falha de TTS e idioma ausente devem marcar erro e sair; o caminho de sucesso configura `READY` sem `else` aninhado. |
| P1 | `apps/android/app/src/main/kotlin/com/olivia/companion/session/AndroidAudioFeedback.kt:98-118` | 2 | Usar guard clause para o caminho sem TTS e deixar o fallback de WAV no fluxo principal. Preservar `QUEUE_ADD` para correções e `QUEUE_FLUSH` para sinais críticos. |

Essas quatro ocorrências são as únicas classificadas inicialmente como
`else` estruturalmente desnecessário. A implementação deve ser feita em um
commit separado e validada com os testes de áudio existentes.

### Ocorrências que devem ser preservadas

Estas ocorrências foram revisadas e não entram na remoção automática:

| Arquivo e linhas | Motivo para preservar |
| --- | --- |
| `session/OliviaMarkers.kt:66-71` | O `else` interno serializa caracteres de controle e caracteres comuns; ambos são saídas válidas. O `else` do `when` também é o fallback necessário. |
| `session/AndroidAudioFeedback.kt:167-170` | Mono e estéreo exigem máscaras de canal diferentes. |
| `session/AndroidAudioFeedback.kt:209-213` | `panFor` precisa de retorno neutro para direções sem pan lateral. |
| `session/AndroidAudioFeedback.kt:253-263` | O parser WAV diferencia chunks `fmt ` e `data`; o `else if` é parte do protocolo. |
| `session/OliviaSessionViewModel.kt:467` | O título precisa distinguir o take ao vivo dos demais takes. |
| `ui/OliviaApp.kt:173-177`, `231-235` | A cor de sistema e as cores do `Scaffold` usam o background claro compartilhado pelo app. |
| `ui/OliviaApp.kt:208-227` | O `DisposableEffect` tem dois ciclos de vida distintos: tela sem permissão/onboarding e tela ativa com observer. Não remover sem preservar o `onDispose` correto. |
| `ui/OliviaApp.kt:213-217` | O `else -> Unit` torna o `when` de eventos de ciclo de vida exaustivo; remover exige tratar explicitamente todos os eventos. |
| `ui/OliviaApp.kt:333-339` | O primeiro clique navega; o clique seguinte inicia a sessão, conforme o comportamento aprovado. |
| `ui/OliviaApp.kt:462`, `471`, `542-544`, `593`, `602` | São escolhas de ação, rótulo, ícone e papel semântico dependentes do estado. |
| `ui/OliviaComponents.kt:91-100` | Cores e gradientes têm fallback explícito para o botão primário. |
| `ui/OliviaComponents.kt:101-110` | O `Modifier` de sombra depende da combinação de variante e habilitação. |
| `ui/OliviaComponents.kt:224-228` | `Constraints.Infinity` não pode receber soma de largura; o ramo finito precisa expandir o limite. |
| `ui/screens/TakeScreen.kt:46-67` | A tela precisa mostrar exportação quando há vídeo e mensagem honesta quando não há. O texto de carregamento também é um estado necessário. |
| `ui/screens/HomeScreen.kt:68` | A tela possui estados distintos para lista recente vazia e preenchida. |
| `ui/screens/SessionsScreen.kt:220` | Singular e plural são textos diferentes. |
| `ui/screens/WelcomeScreen.kt:58`, `134`, `224`, `261-267` | São fallback de permissão, ação, imagem e rótulo final do onboarding; remover algum deles mudaria a experiência ou deixaria o `when` incompleto. |

## Plano de execução

### Parte 1 — guard clauses do áudio

- [ ] Refatorar a inicialização do TTS sem alterar mensagens de erro ou estados.
- [ ] Refatorar `announce` e `signal` mantendo os fallbacks locais em WAV.
- [ ] Executar `:app:testMockDebugUnitTest` e `:app:assembleMockDebug`.
- [ ] Conferir no emulador o Modo Voz, o Modo Sinal e o comportamento com TTS indisponível.
- [ ] Criar um commit exclusivo para essa parte.

### Parte 2 — revisão dos não-candidatos

- [ ] Repassar o inventário depois da Parte 1.
- [ ] Só alterar ocorrências preservadas se surgir uma simplificação que mantenha
  a mesma semântica e tenha cobertura específica.
- [ ] Criar um commit separado apenas se houver uma alteração independente.

## Critérios de aceite

- Nenhum `else` removido altera estado de sessão, fallback de áudio, navegação,
  permissões ou acessibilidade.
- O comportamento `STARTED`, `PAUSED`, `DEGRADED` e `STOPPED` permanece intacto.
- TTS disponível continua falando; TTS indisponível continua usando o sinal WAV.
- Correções continuam usando fila aditiva e sinais críticos continuam podendo
  limpar a fila antiga.
- A spec e os commits identificam claramente o que foi removido e o que foi
  deliberadamente preservado.
