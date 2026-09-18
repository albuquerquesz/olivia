# 00 — Contrato do evento

## Fluxo que não muda

1. “Oi, Olivia, gravar” roda localmente no microfone do celular.
2. DAT entrega frames; o app analisa um a cada 2 segundos (0,5 fps).
3. Gemini devolve somente ROI normalizada/confiança; Android calcula enquadramento, luz e horizonte.
4. Thresholds, duas análises compatíveis e cooldown de 4 s decidem a orientação.
5. TTS ou tom local é roteado pela saída de mídia Android.
6. `STARTED`, `PAUSED`, `STOPPED` e `DEGRADED` permanecem invariantes.
7. Quatro segundos sem heartbeat de captura levam a `DEGRADED`; marcas são preservadas.
8. Mídia nativa só entra após `STOPPED`; o stream não é o vídeo final.

## Limites fixos

- Usar `mwdat-core`, `mwdat-camera` e `mwdat-mockdevice`; nunca `mwdat-display`.
- Frames são efêmeros; não persistir frame, áudio bruto, URI privada ou credencial.
- DAT não alimenta IA com microfone ao vivo; wake word/comandos são locais no celular.
- Vídeo 3K e áudio nativos não bloqueiam o loop vivo.
- ~900 ms é meta a medir. Callback TTS não comprova som físico.

## Status de evidência

`PASS_MOCK` prova mock; `PASS_CODE_PATH` prova código; `PASS_HARDWARE` exige observação
reproduzível no Ray-Ban e Moto. `NOT_SUPPORTED`, `INCONCLUSIVE` e `PENDING` são resultados válidos.

## Freeze

No evento só mudar credencial, permissão, flag, configuração de adapter ou bug pequeno observado.
Não reescrever `:core`, sampler, estados, preview, timeline, Porcupine/Rhino ou áudio.
