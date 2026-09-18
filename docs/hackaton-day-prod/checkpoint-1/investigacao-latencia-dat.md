# Investigação de latência DAT — 18/09/2026

Tasks: BE-00, BE-02 e INT-04; suporte à integração física e ao loop existente, sem nova funcionalidade de produto. Gemini continua amostrado a 0,5 fps. Taxas maiores de câmera foram autorizadas pelo usuário para diagnóstico de preview/tracking.

## Resultado atual

**Atraso cena → tela ainda não resolvido.** O operador confirmou cores normais no último teste a 2 fps, mas atraso visual de aproximadamente 3 segundos, às vezes maior. Não declarar PASS de latência ou ROI em tempo real.

A versão instalada solicita HIGH/2 fps, usa HEVC customizado no físico e I420 do SDK no mock. HIGH é uma solicitação: o codec mudou de formato 736×1280 para 512×896 durante a sessão; esses números incluem alinhamento, não necessariamente a área visível. O primeiro frame visível foi 720×1280. Não afirmar que toda a sessão permaneceu em 720×1280.

## Evidência física coletada antes desta revisão documental

Moto G60 / Android 12 / DAT Android 0.9.0. Apenas tempos, contagens e formatos; nenhum frame ou áudio persistido.

| Janela | Configuração | Resultado |
| --- | --- | --- |
| 13:21:09–13:22:07, 120 frames | HIGH, pedido anterior de 6 fps; PTS geralmente ~499 ms | Espera enqueue→output: p50 100,5 ms, p95 1129 ms, máximo 1540 ms. A saída só era drenada quando entrava outro pacote. |
| 13:26:13–13:26:57, 49 frames | HIGH/7, HEVC com drenagem independente | Espera p50 14 ms, p95 21 ms; conversão p50 37 ms. Defasagem relativa aumentou ~37,4 s. Pacotes já chegavam devagar antes de nosso codec. |
| 13:31:41–13:32:45, 127 frames | HIGH/7, `compressVideo=false`, decoder interno DAT | Cores confirmadas normais; operador confirmou atraso grande. Defasagem relativa cresceu ~47 s; timestamps iniciais repetidos limitam a precisão desta comparação. |
| Primeiro take a partir de 13:34:10 | HIGH/30, HEVC customizado corrigido | PTS ~33 ms inicialmente; primeiros 119 frames entregues em ~28 s, com ~23,5 s de defasagem adicional. Operador relatou muito atraso e depois tela verde. |
| 13:37:50–13:38:53, 129 frames | HIGH/2, HEVC corrigido | Espera p50 9 ms/p95 18 ms; conversão p50 20 ms/p95 32 ms; decode→publicação p50 13 ms/p95 79 ms. Defasagem relativa terminou ~5,8 s abaixo da inicial, mas operador ainda percebeu ~3 s cena→tela. |

As janelas têm FPS e condições diferentes; não são benchmark controlado do efeito isolado de cada mudança. Publicação do preview não mede composição/apresentação efetiva da tela. Diferenças entre durações de PTS e relógio do celular indicam acúmulo/escoamento relativo; não medem atraso absoluto com relógios de origens diferentes.

Correções já compiladas/instaladas: saída do MediaCodec drenada independentemente da entrada; acesso aos planos YUV reduzido; CSD inicial não reenviado após `start`; configurações avulsas posteriores encaminhadas ao codec ativo. A última correção é defensiva: não foi comprovada como causa da tela verde. Nenhuma suíte automatizada executada nesta investigação. Nesta revisão documental não houve novo build nem alteração do pipeline instalado.

## O que a documentação e o código oficial dizem

A [referência 0.9 solicitada](https://wearables.developer.meta.com/docs/reference/android/dat/0.9/com_meta_wearable_dat_camera_types_streamconfiguration/) exigiu login na consulta automatizada. O conteúdo do guia foi recuperado pelo [MCP público de documentação da Meta](https://mcp.developer.meta.com/wearables); suas referências apontam para `latest`. As diferenças de implementação foram conferidas também no AAR 0.9.0 instalado localmente, sem presumir que documentação `latest` seja uma referência versionada.

- FPS válidos: 2, 7, 15, 24 e 30. O pedido anterior de 6 não era documentado.
- HIGH: 720×1280; MEDIUM: 504×896; LOW: 360×640. A documentação descreve adaptação à capacidade de Bluetooth Classic: resolução, FPS e compressão podem variar. Não há garantia de latência a partir desses parâmetros.
- `compressVideo=false` não transmite YUV sem compressão por Bluetooth. O SDK decodifica no celular e entrega frames raw ao app. `true` expõe HEVC para o consumidor fazer a decodificação. A opção foi introduzida no [changelog 0.6](https://github.com/facebook/meta-wearables-dat-android/blob/main/CHANGELOG.md).
- O binário 0.9 confirma `VideoDecoder`, `AsyncFrameHandler` e `CodecFactory` internos; não são uma nova API pública a importar diretamente. `StreamImpl` seleciona entrega encoded/decoded conforme `compressVideo`.

### Exemplo oficial CameraAccess

Revisão do repositório consultada: `974e05c569da8be01c0c9ce8bc5bc79b73ff8540`, de 14/09/2026. Os links abaixo apontam para `main`; usar esse hash para reproduzir a comparação.

O [CameraViewModel](https://github.com/facebook/meta-wearables-dat-android/blob/main/samples/CameraAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/camera/CameraViewModel.kt) pede MEDIUM/24 fps e `compressVideo=true`, pois compartilha HEVC com preview e gravação do stream. Serializa o trabalho de frames fora da main thread. A gravação do exemplo não é gravação nativa 3K e não deve ser copiada para o produto Olivia.

O [HevcDecoder oficial](https://github.com/facebook/meta-wearables-dat-android/blob/main/samples/CameraAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/stream/HevcDecoder.kt) usa callbacks assíncronos do MediaCodec e renderiza diretamente em `Surface`, evitando o round-trip YUV→JPEG→Bitmap do nosso preview. Prefere codec de software e lista `OMX.Exynos.hevc.dec` e `c2.mtk.hevc.decoder` como incompatíveis. Isso não prova incompatibilidade do codec padrão escolhido no Moto.

O exemplo tem fila própria de até 100 unidades e não promete atraso zero. Não copiar cegamente sua fila ou parser como solução de latência. Nossa medição já mostra tempo pequeno dentro do codec após a correção. É necessário comparar no mesmo hardware com o resto do app isolado.

### MockDeviceKit

Não é um decoder de baixa latência para os óculos reais; substitui a fonte/transporte para desenvolvimento. A inspeção de `mwdat-mockdevice:0.9.0` mostrou câmera local com encoder e fluxo próprio. `MediaStreamingWarpService` lê `preferredFps` e constrói timestamps via `FrameTimer`. No collector da câmera local consultado, não há chamada ao método de espera desse timer antes de enviar cada frame. Isso sustenta a possibilidade de cadência de entrega distinta da solicitada, mas não comprova que a sessão da madrugada entregou exatamente 30 fps. Nem o número configurado nem o PTS sintético devem substituir uma medição de chegada real do mock.

## Projetos públicos e relatos relevantes

- [MetaLens AI — StreamViewModel Android](https://github.com/przemek-nowicki/meta-lens-ai/blob/main/android/app/src/main/java/com/metalens/app/stream/StreamViewModel.kt): implementação pública com stream a 24 fps e conversão I420→NV21→JPEG/Bitmap. Usa APIs anteriores a 0.9; é referência de integração, não comprovação de baixa latência nem substituto pronto para nosso adapter.
- [iSee Labs — MetaSessionManager](https://github.com/iSee-Labs/meta-wearables-dat-flutter/blob/main/android/src/main/kotlin/com/iseelabs/meta_wearables_dat_flutter/MetaSessionManager.kt): preview raw via textura; usa `collectLatest`. O [changelog](https://pub.dev/packages/meta_wearables_dat_flutter/changelog) registra correções de cor, tamanho da textura e alocações. São falhas específicas de conversão/renderização; não demonstram a causa do nosso atraso upstream.
- [Rodcone — plugin Android](https://github.com/rodcone/flutter_meta_wearables_dat/blob/main/android/src/main/kotlin/io/rodcone/flutter_meta_wearables_dat/MetaWearablesDatPlugin.kt): separa contador de chegada do SDK de processamento/renderização e expõe PTS. Padrão útil para diagnosticar origem do travamento sem confundir frame recebido com frame exibido.
- [Discussão #143](https://github.com/facebook/meta-wearables-dat-android/discussions/143): autor relata suspeita de perda de referências/keyframes no caminho HEVC comprimido. Sem solução oficial confirmada na discussão consultada.
- [Issue #159](https://github.com/facebook/meta-wearables-dat-android/issues/159): reprodução de queda de FPS e backlog ao selecionar microfone Bluetooth SCO. Na nossa consulta anterior, SCO estava inativo; não atribuir esse diagnóstico automaticamente ao Olivia.

Tela verde não identifica sozinha corrupção de HEVC: também existem relatos de placeholders ligados ao estado dos óculos e problemas de conversão/renderização. A causa do episódio observado continua aberta. Não persistir imagem para diagnosticá-la.

## Diferenças e riscos no Olivia

1. `HevcFrameDecoder` escolhe decoder padrão da plataforma; o exemplo e o decoder interno DAT preferem software. Precisamos registrar o nome efetivamente escolhido e comparar, sem supor que software sempre será mais rápido.
2. O preview faz HEVC→NV21→JPEG→Bitmap. Uma saída direta à Surface elimina trabalho, mas os ~13 ms medianos até publicação medidos não explicam sozinhos os ~3 s percebidos. Uma Surface também não fornece automaticamente os pixels necessários ao ROI.
3. **Idade do frame:** `DatGlassesGateway.emitDecodedFrame` cria `FrameStamp.receivedAt` depois do decode. `FrameSampler` e tracking usam esse relógio; um frame atrasado antes da chegada pode passar como recente. Precisamos distinguir chegada, PTS de captura e validade temporal. Não subtrair PTS diretamente do relógio Android sem validar a base temporal.
4. `_frames.tryEmit` com capacidade 1 e overflow padrão pode recusar frames novos quando o consumidor está ocupado. O preview posterior já descarta o mais antigo, mas o collector compartilhado também executa tracking. Medir descartes e custo do fast path; política de manter o mais recente cabe a frames já decodificados.
5. Não aplicar `conflate`/`DROP_OLDEST` indiscriminadamente a pacotes HEVC: frames dependem de referências. Em perda, reiniciar numa unidade de acesso aleatório com configuração válida; pular bytes arbitrários pode piorar a imagem.

## Próxima comparação controlada

Executar preview isolado no mesmo Moto/óculos, com a configuração do exemplo oficial MEDIUM/24. A resolução menor é um experimento diagnóstico, não mudança silenciosa do contrato do produto.

1. Caminho oficial compressed→decoder de software→Surface, sem gravação, Gemini, tracker, wake word ou reprodução de áudio. Conferir atraso cena→tela com movimento/cronômetro ao vivo, sem capturar mídia.
2. Na mesma configuração, testar `compressVideo=false` e pixels raw do SDK. O A/B anterior foi HIGH/7 com o restante do Olivia ativo; não isola esses fatores.
3. Reintroduzir tracking, Gemini a 0,5 fps, wake word e saída de áudio separadamente, mantendo constantes resolução/FPS e medindo chegada, decode, publicação e apresentação.
4. Se o preview isolado já atrasar, comparar MEDIUM/LOW, rota Bluetooth, versões do Meta AI/firmware e outro telefone disponível. Preparar reprodução mínima para a Meta; não declarar culpa do rádio, SDK ou hardware sem esse isolamento.

Se o caminho raw ficar saudável, ele simplifica preview+ROI com um único decode no SDK. Se apenas a Surface direta ficar saudável, planejar a extração efêmera de pixels para o tracker/IA sem reintroduzir um segundo gargalo. O critério é frame atual e orientação útil, não apenas FPS alto ou fila local vazia.

## Retomada — câmera isolada, 18/09/2026

Suporte às tasks **BE-00 / INT-04**, dependências SH-04, FE-00, FE-02,
BE-02 e INT-00. `CameraDiagnosticActivity` existe somente em `glassesDebug`.
Ela instancia gateway e preview GPU sem construir o runtime, ROI, Gemini,
wake word ou TTS. O processo anterior é encerrado para evitar interferência
residual. Nenhuma imagem ou áudio é persistido.

O trabalho retomado já continha preview NV21 direto na GPU, worker de tracking
com descarte do frame antigo e `FrameStamp.sourceBacklogMs`. O limite de 750 ms
usa idade local mais backlog relativo; **não detecta atraso fixo inicial**.
O log da execução anterior de SourceBacklogTest, FrameSamplerTest,
MotionTrackingTest, HybridVisualAnalyzerTest e HybridReplayHarnessTest terminou
em `BUILD SUCCESSFUL`. As mudanças da tela de diagnóstico foram compiladas e
instaladas no Moto G60 nesta retomada.

### Comparação física

Horários abaixo são do logcat do aparelho. Estatísticas filtradas pelo PID de
cada execução para não misturar processos nas coletas contínuas. p95 usa o
percentil empírico inferior. Medem entrega local, não exposição→tela.

| Caminho | Janela | Frames decodificados | Gap de chegada p50 / p95 | Backlog relativo máximo | Submit→draw p50 / p95 |
| --- | --- | ---: | ---: | ---: | ---: |
| SDK raw, MEDIUM/24 | 14:05:52–14:06:46 | 727 | 8 / 511 ms | 15.853 ms | 2 / 9 ms |
| HEVC + decoder Olivia, MEDIUM/24 | 14:06:56–14:08:07 | 1.016 | 10 / 473 ms | 15.121 ms | 2,5 / 15 ms |

No segundo caminho, espera no codec p50/p95 **16/40 ms** e conversão NV21
**4/17 ms**. Os pacotes já chegam em rajadas antes do decoder Olivia. O backlog
sobe e depois diminui: medianas baixas dos gaps refletem essas rajadas e não
comprovam fluidez. O SDK adapta a saída: observado 504×896 inicialmente e
360×640 no caminho comprimido. MEDIUM/24 é configuração pedida, não garantia
de resolução nem FPS entregues. SCO estava inativo na consulta durante o raw.

Isso reproduz o atraso sem os componentes de análise/áudio da Olivia e em dois
caminhos de decode. Não identifica isoladamente rádio, firmware, encoder ou SDK.
O caminho oficial software→Surface ainda não foi executado; o decoder Olivia
continua usando saída YUV, embora os tempos medidos sejam muito menores que o
backlog observado.

### Reproduzir

Somente com APK `glassesDebug` instalado e permissões Android/DAT concedidas:

```sh
adb shell am force-stop com.olivia.companion
adb shell am start -n com.olivia.companion/.CameraDiagnosticActivity
```

Para HEVC + decoder Olivia, acrescente `--ez compressed true` ao `am start`.
Para o controle raw LOW/7, acrescente `--ez low_bandwidth true`.
Cada comparação deve começar com `force-stop`, mantendo condições físicas iguais.
A Activity encerra recursos ao sair da tela. Esses extras não mudam as
configurações do fluxo principal nem autorizam aceitar resolução abaixo do alvo.
