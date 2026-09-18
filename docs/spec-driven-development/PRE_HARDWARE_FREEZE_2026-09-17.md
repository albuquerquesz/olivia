# Freeze pré-hardware e plano do hackathon — 17/09/2026

## Objetivo de hoje

Fechar 100% do que é implementável, testável e demonstrável sem os Ray-Ban Meta. Isso significa
chegar amanhã com o app, contratos, máquina de estados, preview, voz local, análise, áudio local,
importação, pós-processamento, privacidade, fallback mock e evidências já prontos.

Isso não transforma teste mock em prova de hardware. A integração física começa somente quando os
óculos forem entregues e pareados.

## Regras que congelam o desenho

- O celular é a ponte obrigatória do DAT; não criar caminho direto óculos→nuvem.
- `GlassesGateway` continua sendo a única fronteira de frames. O domínio não importa tipos DAT.
- O stream DAT 720×1280 é sensor do loop, amostrado a 0,5 fps; o preview é efêmero e não vira vídeo final.
- Gemini via Firebase localiza somente a ROI. Enquadramento, luz/sombra e horizonte continuam locais.
- Porcupine/Rhino rodam no microfone do celular. Não depender do microfone dos óculos.
- Voz e tons locais são o output; não adicionar display nem controles de toque ao fluxo principal.
- `STARTED`, `PAUSED`, `DEGRADED` e `STOPPED` permanecem; quatro segundos sem heartbeat gera `DEGRADED`.
- O vídeo nativo 3K e o áudio entram somente depois de `STOPPED`; nunca bloquear o loop ao vivo.
- Frames, áudio de comando, tokens, resposta bruta e mídia intermediária permanecem efêmeros.

## Definition of done pré-hardware

1. Contratos 2.0.0 congelados e sem mudança de schema durante o hackathon.
2. APK `mock` demonstrável e APK `glasses` compilável com configuração local, sem credenciais no Git.
3. `DatGlassesGateway` pronto para trocar `MWDAT_USE_MOCK_DEVICE_KIT=true` por `false` sem reescrever o runtime.
4. Preview, sampler, watchdog, degradação/recuperação, timeline, correção e áudio local cobertos.
5. Firebase live configurado no flavor `glasses`; smoke de acesso separado da avaliação de qualidade.
6. Avaliação visual preparada com manifesto versionado e relatório agregado, sem persistir frames.
7. `RecordingSource`, importação privada, alinhamento, `CutPlanner` e exportação prontos para receber um
   arquivo nativo ou o fallback manual após `STOPPED`.
8. Entrada do Whisper local preparada com modelo/fixture fora do Git; se o modelo não estiver disponível,
   o app não inventa transcrição nem bloqueia a demo principal.
9. Checklist de privacidade, cleanup, App Check, opt-outs DAT e relatório sanitizado pronto para o pitch.
10. SH-01 enviada ao canal oficial; até haver resposta, registrar `PENDING_OFFICIAL_RESPONSE` e não
    alegar autorização administrativa.

## O que fica necessariamente para amanhã

| Gate | Ação no hardware | Critério mínimo |
|---|---|---|
| A1 / INT-04 | Parear, iniciar/encerrar sessão e receber frame | Frame DAT real chega no mesmo `GlassesGateway`; preview mostra origem DAT |
| A2 | Gravar vídeo nativo enquanto o stream DAT continua | Take 3K reproduzível com áudio e frames de direção durante o take |
| A3 | Interromper Bluetooth de forma segura | Sessão fica `DEGRADED`, marcas sobrevivem e vídeo nativo não é perdido |
| A4 / INT-06 | Encerrar, transferir/importar e abrir revisão | Arquivo chega somente após `STOPPED`, metadata/áudio válidos |
| A5 | Alinhar marca conhecida à mídia | Erro de alinhamento ≤250 ms, ou limitação registrada |
| INT-05 | Ouvir voz e tons | Início, pausa, queda, retomada e direção audíveis nos óculos |
| INT-07 | Medir | Pelo menos 10 inferências, bateria e latência com condições anotadas |

## Ordem operacional das seis horas

### 0:00–0:30 — Setup e decisão de caminho

Instalar o APK `glasses`, conferir Application ID/client token, permissões, App Check e pareamento.
Executar o smoke DAT antes de mexer em IA ou pós-processamento. Se o pareamento falhar, congelar o
happy path mock e registrar o bloqueio; não reescrever contratos.

### 0:30–1:30 — Câmera, preview e voz

Confirmar `STARTED`, frame 720×1280, preview, origem DAT, 0,5 fps e áudio local. Exercitar “Oi,
Olivia, Gravar”, “pausar”, “retomar” e “terminar”. Porcupine/Rhino continuam no celular.

### 1:30–2:30 — Loop de direção

Executar duas correções confirmadas, verificar timeline, queda após quatro segundos e recuperação.
Usar Gemini real somente no número de chamadas previamente orçado. Não alterar scheduler, preview,
OpenCV ou timeout para perseguir 900 ms nessa janela.

### 2:30–3:45 — Vídeo nativo e Bluetooth

Testar A1/A2. Se a API nativa não estiver exposta pelo toolkit entregue, registrar o resultado como
`NOT_SUPPORTED_OR_NOT_EXPOSED` e seguir com a direção ao vivo; não reconstruir 3K a partir do stream.
Depois testar A3 e confirmar que o take não depende do heartbeat DAT.

### 3:45–4:45 — STOPPED, importação e mídia

Executar A4/A5. Primeiro usar o caminho nativo; se falhar, usar o fallback de URI `content://` já
validado. Nunca chamar Whisper antes de `STOPPED`, nunca alterar o vídeo original e nunca gerar
“cortes” sem transcrição/metadata reais.

### 4:45–5:30 — Checkpoints e evidência

Registrar IA, câmera/microfone, áudio, privacidade e bateria. Salvar somente contagens, estados,
tempos, origem, MIME/duração/tamanho e códigos sanitizados. Não anexar frame, áudio bruto, token ou
resposta textual do provider.

### 5:30–6:00 — Freeze e pitch

Escolher um único caminho demonstrável, gerar APK/backup e parar mudanças estruturais. A narrativa
deve separar claramente: loop ao vivo comprovado, pós-take comprovado, e capacidades físicas ainda
não disponíveis ou limitadas.

## Prioridade de implementação até o freeze

1. Plugabilidade DAT/voz/preview e estados.
2. Segurança, cleanup e evidência sanitizada.
3. Pós-take local e fallback de importação.
4. Avaliação visual rotulada e smoke live.
5. CI/offline e APK reproduzível.
6. Latência de 900 ms somente depois do fluxo e das medições físicas.

Qualquer mudança que introduza login, CRUD, feed, editor de vídeo, upload de mídia, áudio remoto,
microfone dos óculos ou novo diagnóstico está fora do freeze.
