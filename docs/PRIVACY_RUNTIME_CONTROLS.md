# Privacidade e limpeza verificável — Olivia

## Inventário mínimo

| Dado | Local | Retenção | Controle |
| --- | --- | --- | --- |
| Frame 720×1280 | memória do loop | somente durante análise | descartado após classificação; nunca timeline/log/cache |
| Diagnóstico e direção | timeline privada | duração da sessão/revisão local | somente enums, confiança e offsets; sem bytes de frame |
| Áudio do comando | microfone do celular | processamento local imediato | não enviado ao provedor e não persistido |
| Vídeo selecionado | URI do provider + cache privado | somente durante revisão | cópia privada descartável; original nunca é apagado |
| Review/SRT | `filesDir/olivia-review-exports` | até compartilhar ou descartar | URI temporária pelo Sharesheet; limpeza no descarte e após crash |

## Controles aplicados

- A timeline armazena direção estruturada, não frase falada, transcript, URI, frame ou credencial.
- Não há logger de produto que receba payload de frame, mídia, transcript, URL privada ou segredo.
  Diagnósticos do harness usam somente schema fixo, status, código e duração.
- Importações usam `.part`, sincronizam antes de renomear e limpam `.part`/`.mp4` órfãos no
  diretório privado `cacheDir/olivia-recordings` na próxima inicialização.
- Exports de revisão e SRT são transitórios: o app os remove explicitamente e também remove
  órfãos de `filesDir/olivia-review-exports` após crash/process death.
- A limpeza é limitada aos dois diretórios privados com nome fixo da Olivia; nenhum `content://`
  original selecionado pela pessoa é alterado ou apagado.

## Ensaio e consentimento

Antes de qualquer ensaio com pessoas: explicar a câmera, obter concordância de quem aparece,
usar apenas o projeto Firebase autorizado, não usar vídeo/áudio de terceiros como fixture e
descartar cópias temporárias ao terminar. Isso é um checklist operacional, não aconselhamento
jurídico nem autorização para retenção adicional.
