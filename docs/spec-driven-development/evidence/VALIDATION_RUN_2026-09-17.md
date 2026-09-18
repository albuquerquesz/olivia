# Execução de validação do SDD — 17/09/2026

Esta execução fecha somente gates que podem ser comprovados neste ambiente. O modo MockDeviceKit
é evidência do adapter/mock, não de Ray-Ban Meta físico, áudio acústico ou gravação nativa 3K.

## Baseline

- Commit: `b39b6f1` — merge da UI de conexão/recording.
- Worktree: limpo no início da execução.
- Preview realtime: preservado; `livePreview` permanece efêmero e é limpo em pause/stop/close.
- Otimização de 900 ms: deliberadamente fora deste ciclo; os números remotos continuam sendo
  baseline, sem alteração de scheduler/modelo/OpenCV.

## Comandos executados

| Validação | Resultado | Escopo comprovado |
| --- | --- | --- |
| `:core:test` + `:core:integrationTest` no projeto `infra/testing` | PASS | Contratos, sampler, timeline, análise local, pós-processamento e fixtures JVM |
| `:core:test` + `:core:integrationTest` no projeto Android | PASS | Reuso do módulo core sem dependência Android/DAT |
| `:app:testMockDebugUnitTest` | PASS | Lifecycle/UI mock e pós-take |
| `:app:testGlassesDebugUnitTest` | PASS | Runtime glasses compilável e regras de sessão |
| `:app:connectedMockDebugAndroidTest` | PASS | APK mock no Moto G60 Android 12 |
| `:app:connectedGlassesDebugAndroidTest` com `MWDAT_USE_MOCK_DEVICE_KIT=true` | PASS | DAT MockDeviceKit, frame portrait, preview e recuperação do runtime |
| `:app:testMockDebugUnitTest --tests com.olivia.companion.session.OfflineHandsFreeGateTest` | PASS | Cinco ciclos determinísticos com comandos locais de gravar/pausar/retomar/terminar, duas correções, degradação/recuperação e pós-take |
| `assembleMockDebug` | PASS | Artefato APK mock compilável |
| `bun test infra/testing/tests` via Bun 1.3.10 | PASS | 13 testes do harness, 43 expectativas, sem conteúdo sensível |
| `bun infra/testing/run.ts unit` via Bun 1.3.10 | PASS | Entry point sanitizado retornou `suite=unit,status=passed` |
| `bun infra/testing/run.ts integration-offline` via Bun 1.3.10 + Docker Compose | PASS | Contratos JVM, harness Bun, provider stub, rede interna e probe de 401/403/429/5xx/timeout |
| `bun infra/testing/run.ts integration-live-ai` via Bun 1.3.10 + Android `glasses` | PASS | 1 teste Firebase/App Check autorizado; smoke de acesso, não avaliação de qualidade visual |

## Bloqueios de execução nesta máquina

- Docker Desktop/daemon Linux foi iniciado durante a rodada; o perfil Compose offline completo
  passou depois de corrigir a resolução Windows do Compose e o formato `tmpfs` portátil.
- Bun não está instalado globalmente no host; o runner foi executado via `npx bun@1.3.10` e o
  workflow `sdd-offline-validation.yml` fixa a mesma versão.
- Não havia Ray-Ban Meta pareado neste ADB; nenhuma capacidade A1–A5 foi promovida por esta rodada.
- Não havia modelo Whisper local provisionado; BE-08/BE-09 continuam pendentes e não há transcrição
  inventada no Android.

## Gates que permanecem abertos

SH-01 (resposta administrativa oficial), BE-00.1/BE-00.2 e FE-03.2 (DAT e comandos físicos), BE-03.5
(qualidade rotulada), INT-03 (roteiro completo dos cinco checkpoints), BE-10.3/BE-10.4 (provider e
vazamento completo), BE-12.1–BE-12.4/INT-04/INT-05/INT-06 (hardware nativo), BE-14.1 e
BE-08.1/08.2/BE-09.2 (Whisper real), INT-01 (ensaio do `LiveSessionRuntime` com áudio/microfone sem
toque; o roteiro determinístico offline passou), INT-07 (bateria/acústica) e INT-08 (impacto consentido).
