# Matriz de capacidades nativas — BE-12

Estado em 15/09/2026: nenhuma capacidade abaixo foi medida. Esta matriz separa intenção de
produto, documentação e observação no hardware; `não verificado` não pode ser apresentado como
suportado no pitch.

| ID | Capacidade necessária | Estado | Evidência aceitável | Roteiro físico | Fallback declarado |
| --- | --- | --- | --- | --- | --- |
| A1 | Iniciar e encerrar a gravação nativa por um fluxo mãos-livres | Não verificado | Vídeo nativo novo, com horários de início/fim e método de acionamento anotados | Abrir sessão DAT; acionar por `Hey Meta, grave um vídeo`; encerrar; confirmar se a sessão permanece saudável | Demonstrar apenas a sessão/stream de direção; 3K fica roadmap |
| A2 | Gravação 3K nativa coexistir com o stream DAT | Não verificado | Contador/origem de frame DAT durante o take e arquivo nativo importado depois | Com A1 em andamento, receber frames reais por pelo menos 10 s; encerrar e importar o take | Não reconstruir vídeo a partir dos frames Bluetooth |
| A3 | Take nativo e áudio permanecerem íntegros após queda Bluetooth | Não verificado | Arquivo importado reproduzível, com duração e áudio registrados antes/depois da queda | Durante A2, provocar a perda de vínculo de forma segura; aguardar `DEGRADED`; recuperar/encerrar e importar | Preservar somente as marcas já obtidas; não declarar continuidade do take |
| A4 | Arquivo chegar ao celular e poder ser usado pelo app após `STOPPED` | Não verificado | URI/local path autorizado, MIME, tamanho, duração e presença de áudio validados localmente | Importar pelo Meta AI app/galeria após `STOPPED`; selecionar o arquivo e validar metadados | Seleção manual é só fallback de revisão; não prova A1–A3 |
| A5 | Linha do tempo do stream alinhar ao take nativo | Não verificado | Marca conhecida com erro absoluto de no máximo 250 ms | Produzir tom ou palma no início do take; comparar o offset da timeline com a trilha nativa | Mostrar marcas sem corte automático e declarar alinhamento pendente |

## Contexto de execução

- Aplicativo: `com.olivia.companion`, flavor `glasses`.
- Toolkit alvo: DAT Android 0.9.0, configurado no version catalog; a versão realmente resolvida
  deve ser registrada no dia.
- Registrar modelo dos óculos, firmware, modelo do Android, versão Meta AI, rede, relógio usado,
  operador e resultado de cada etapa. Não anexar frames, áudio bruto, vídeo, transcrição, URI
  privada, token ou credencial à matriz.
- Um resultado `não suportado` ou `inconclusivo` encerra aquela linha e ativa seu fallback; não
  autoriza mudanças no domínio para mascarar o limite do hardware.

## Critério de fechamento

Uma linha só vira `suportado` quando há evidência física reproduzível. A1–A4 precisam estar
suportadas para afirmar o fluxo completo de take 3K; A5 também precisa estar suportada antes de
gerar cortes automaticamente sobre essa mídia.
