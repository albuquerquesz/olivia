# BE-10.2 — recuperação de artefatos privados Android

- Data: 2026-09-16
- Ambiente: Android Emulator `Olivia_API_36`, API 36, variante `mockDebug`.
- Cenário: `LocalArtifactRecoveryInstrumentationTest` cria cópias privadas sintéticas sem owner,
  reinicializa os adaptadores e verifica o descarte.
- Resultado: aprovado.
- Limite: a evidência cobre somente `cacheDir/olivia-recordings` e
  `filesDir/olivia-review-exports`; não apaga URIs `content://` originais, não mede retenção de
  provedor e não é validação de hardware.
