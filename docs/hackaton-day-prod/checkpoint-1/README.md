# Checkpoint técnico 1 — material de apoio

Contexto para uma IA (ou pessoa) que vai ler esta pasta e produzir uma captura/resumo do estado do
checkpoint das 15:00–15:30 do [06-runbook-and-checklists.md](../06-runbook-and-checklists.md#L63-L66):

> Mostrar IA, frame, áudio, privacidade e bateria com limites declarados.
> Usar o caminho real mais confiável ou mock claramente identificado.

## Por que esta pasta existe

Enquanto a integração física com o Ray-Ban (DAT + Firebase App Check) ainda estava em teste no
dispositivo, revisamos o código já implementado para separar, por assunto, o que já está pronto
(comprovável por código) do que só pode ser comprovado com o hardware físico rodando. Cada arquivo
aqui cobre **um** dos cinco assuntos do checkpoint.

## Arquivos

- [privacidade.md](privacidade.md) — pronto; App Check confirmado funcionando no físico hoje.
- [bateria.md](bateria.md) — estratégia e instrumentação prontas; existem 2 amostras reais de
  latência (1,85 s / 3,68 s), mas isso ainda é amostra insuficiente e acima da meta de 900 ms.
- [audio.md](audio.md) — `PASS_HARDWARE`: operador ouviu a correção saindo dos Ray-Ban hoje.
- `ia.md` e `gravacao-video.md` — **ainda não criados**; o operador vai adicionar. Já existe
  evidência bruta pra isso em [05-privacy-and-evidence.md](../05-privacy-and-evidence.md) (linha
  "DAT físico / primeiro frame" já em `PASS_HARDWARE`) e em
  [retomada-dat.md](retomada-dat.md), com horários e detalhes da sessão física de hoje.
- [meta-docs-findings.md](meta-docs-findings.md) — achados na documentação oficial da Meta sobre
  por que subir o `frameRate` não resolveu o travamento da preview (banda Bluetooth Classic +
  escada automática de qualidade do próprio SDK). Relevante pro assunto "frame/gravação-vídeo".

## Atualização — 18/09/2026, sessão física

Rodou ponta a ponta pela primeira vez: sessão iniciada, óculos conectados, Gemini analisando,
ROI rodando, correção de enquadramento ouvida nos Ray-Ban. Os três assuntos abaixo (privacidade,
bateria, áudio) foram atualizados para refletir isso. Falta só afinar detalhes técnicos: amostra
de latência maior, meta de 900 ms ainda não atingida, e sincronizar o status "voz/tons nos óculos"
na matriz principal (`05-privacy-and-evidence.md`), que ainda está `PENDING` lá.

## Convenção de cada arquivo de assunto

Cada arquivo segue a mesma estrutura:

1. **Status** — pronto / pronto parcialmente / pendente, com o motivo.
2. **O que já está pronto** — achados com referência `arquivo:linha` do código-fonte real, não
   suposição.
3. **O que falta** — o que só pode ser resolvido com hardware físico ou medição real.
4. **Como demonstrar na banca** — o gesto/comando concreto a executar durante o checkpoint.

## Regras que a IA que for gerar a captura final deve respeitar

- Não afirmar medição de bateria, latência ou hardware que não foi de fato executada — ver
  [00-event-contract.md](../00-event-contract.md#L20) e
  [05-privacy-and-evidence.md](../05-privacy-and-evidence.md#L16-L20).
  "Pronto" aqui significa **implementado e auditado por código**, não **medido no físico**.
- Frame, áudio bruto, transcrição, token ou credencial nunca entram em nenhum arquivo desta pasta
  nem na captura final — só estado sanitizado, exatamente como em
  [05-privacy-and-evidence.md](../05-privacy-and-evidence.md#L3-L14).
- Quando `ia.md` e `gravacao-video.md` existirem, a captura final deve juntar os cinco assuntos na
  mesma matriz de evidência do runbook, não substituí-la.
