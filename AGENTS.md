# Olivia — instruções de projeto

## Fonte de verdade

Antes de planejar ou mudar algo que afete produto, hardware, IA, dados ou demo, leia o JSON relevante em `rules/`:

- `architecture-diagram.json`: componentes, contratos, estados, fluxos e checkpoints.
- `olivia-contracts.json`: interfaces, estados de sessão e schema de timeline (código em `apps/android/core`).
- `meta_wearables_dat_android.json`: setup Android do DAT, artefatos, credenciais, limites e MockDeviceKit.
- `olivia_backlog.json`: escopo, dependências, critérios de aceite e marcos.
- `hackaton-rules.json`: regras do programa, cronograma e avaliação.
- `excalidraw-report.json`: narrativa e conteúdo submetido.

## Invariantes do produto

- Olivia é um companion app para Ray-Ban Meta; o celular é a ponte obrigatória do DAT.
- O loop ao vivo é: iniciar → analisar frame → orientar por áudio → confirmar correção → finalizar → mostrar timeline/cortes.
- Analise apenas enquadramento, luz/sombra e horizonte. A entrada é o frame 720×1280 por Bluetooth, amostrado a 0,5 fps.
- A resposta da IA é estruturada; o motor de direção usa thresholds e cooldown de 4 segundos.
- A saída para a pessoa é áudio (TTS ou WAV local); não presuma display nos óculos.
- Preserve `STARTED`, `PAUSED`, `STOPPED` e `DEGRADED`. Quatro segundos sem frame significa `DEGRADED`.
- Wake word/comandos rodam localmente no celular. O DAT não expõe microfone em tempo real.
- Use `mwdat-core`, `mwdat-camera` e `mwdat-mockdevice`; não use `mwdat-display`, pois Olivia não depende de display nos óculos.
- Vídeo 3K e áudio nativos só entram no pós-processamento depois que a sessão termina; não bloqueie o loop ao vivo.

## Privacidade, bateria e demo

- Frames são efêmeros: descarte-os após classificar. Não registre imagem, áudio bruto ou credenciais.
- Preserve 0,5 fps, tons locais e sampling/cooldown configuráveis.
- Mantenha o mock determinístico e offline; hardware real deve trocar adapters, não reescrever o produto.
- Toda mudança deve continuar demonstrável nos checkpoints de IA, câmera/microfone, áudio, privacidade e bateria.

## Escopo e trabalho

- Não adicione auth, contas, feed social, CRUD, editor de vídeo completo ou analytics sem pedido explícito.
- Relacione a mudança à task do backlog e às dependências antes de implementar. Se não houver task, confirme que não é expansão de escopo.
- Quando alterar contrato, dados, hardware ou privacidade, atualize o JSON relevante em `rules/`.
- Não alegue validação de hardware, latência ou bateria sem medição real.
- Use os padrões TypeScript/Bun/Turborepo já existentes. Não execute build, lint, typecheck ou testes sem solicitação explícita.
