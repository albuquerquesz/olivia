# Integração das orientações recebidas — 15/09/2026

Este registro converte os e-mails da organização em ações verificáveis sem alterar a proposta
submetida: creator de mãos ocupadas, câmera dos Ray-Ban como sensor do loop e feedback por áudio.
Nada abaixo prova hardware antes da medição física.

| Tema | Decisão aplicada | Evidência / plano B |
| --- | --- | --- |
| Instrução atrasada | O motor exige duas análises consecutivas do mesmo desvio, além do cooldown de 4 s. As frases continuam locais e qualitativas; nunca pedem graus ao modelo. | Timeline mostra os dois diagnósticos e a correção. Se a rede variar, aumentar o intervalo/cooldown só após medir. |
| IA e imagem | Gemini devolve somente ROI normalizada/confiança da peça. O Android calcula determinísticamente offset de enquadramento, luminância na ROI e horizonte; thresholds locais escolhem frases qualitativas, nunca graus. Antes do envio, reduzir JPEG para lado maior próximo de 512 px e avaliar blur de rosto on-device. | Refactor ROI+visão local e testes JVM implementados; validação Firebase/DAT e campanha de qualidade continuam pendentes. Sem pré-processador validado, não declarar blur. Sem Gemini disponível, a demo mock é explicitamente mock. |
| Microfone / áudio | Executar A/B: mic do celular + A2DP versus mic dos óculos por HFP + áudio mono. HFP não é API DAT e não muda o contrato submetido sem aprovação explícita. | Registrar taxa de reconhecimento em ruído, rota e audibilidade. O fallback é comando local no celular e saída A2DP. |
| Sinal em HFP | Caso HFP seja adotado por decisão posterior, substituir lateralidade estéreo por padrão mono (grave/agudo; um/dois bipes). | Não habilitar no produto antes de confirmação de rota e revisão de contrato. |
| Gravação 3K | É o primeiro gate de hardware: comando de início, simultaneidade com DAT, preservação em queda, importação e sincronização com palma/tom. | Se falhar, demonstrar somente o loop de direção no stream e declarar 3K como roadmap. |
| Privacidade / LGPD | Manter frame efêmero e sem áudio ao vivo na IA; adicionar ao checklist a retenção/treinamento do provedor, transferência internacional, consentimento de imagem e teste de blur local. | A página de pitch deve separar descarte local de política do provedor; não alegar retenção zero sem contrato/configuração comprovada. |
| Bateria e latência | Solicitar stream DAT mínimo capaz de orientar e 2 fps, mantendo análise a 0,5 fps. Medir pelo menos 10 inferências: mediana, pior caso, rede, formato, bateria do celular e dos óculos a cada 10 min. | Nenhum número mock é apresentado como medição física. |

## Ordem de execução no Android real

1. Instalar APK mock, Meta AI, configuração DAT e todos os modelos/chaves antes de sair para o evento.
2. No primeiro acesso ao óculos, executar o experimento de rota de áudio e o gate de 3K acima.
3. Confirmar frame físico DAT, então configurar formato/fps e rodar o loop câmera → IA → áudio → confirmação.
4. Registrar um vídeo curto assim que o loop funcionar e medir os números do pitch.
5. Mostrar uma falha controlada: ausência de dois frames concordantes mantém silêncio; quatro segundos sem frame entra em `DEGRADED` e preserva a timeline.

## Itens que ainda exigem decisão/evidência

- Trocar o wake word para HFP muda uma invariante atual do produto e requer aprovação explícita
  após o A/B; não foi implementado.
- Pré-processamento de rosto exige adapter Android e avaliação de qualidade antes de ser declarado como
  proteção ativa. O refactor ROI+visão local tem contrato, fixtures e integração JVM; ainda exige
  campanha Android com Firebase/DAT antes de ser declarado preciso no cenário real.
- O acesso do app ao arquivo nativo importado pelo Meta AI, a gravação simultânea e a sincronização
  não foram medidos e permanecem o gate BE-12/INT-06.
