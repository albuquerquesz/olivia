# Matriz de evidência do hardware — 18/09/2026

Preencher durante o evento. `PASS` exige observação reproduzível no hardware; `NOT_SUPPORTED` é
resultado válido quando a API/documentação não expõe a capacidade; `PENDING` não entra no pitch como
capacidade entregue.

| ID | Resultado | Evidência mínima | Observação |
|---|---|---|---|
| A1 | `PENDING` | início/fim nativo hands-free, horários e método | sessão DAT não basta |
| A2 | `PENDING` | 3K + áudio enquanto frames DAT chegam | não reconstruir com stream |
| A3 | `PENDING` | queda BT, `DEGRADED`, marcas e take preservados | registrar forma da queda |
| A4 | `PENDING` | arquivo pós-`STOPPED`, MIME, duração, áudio, URI | manual é fallback |
| A5 | `PENDING` | marca conhecida e erro ≤250 ms | caso contrário, `ALIGNMENT_UNVERIFIED` |
| INT-05 | `PENDING` | voz e tons audíveis nos óculos | celular/headset não substitui |
| INT-07 | `PENDING` | ≥10 inferências, bateria e condições | não extrapolar |
| Privacidade | `PENDING` | ausência de frame/áudio/token em logs e arquivos | provider tratado separadamente |

## Registro do take

- Hardware/firmware: `PREENCHER`
- APK/commit: `PREENCHER`
- Bateria inicial/final: `PREENCHER`
- Horário de início/stop: `PREENCHER`
- Perda de Bluetooth: `PREENCHER`
- Arquivo importado: `PREENCHER` (somente metadata)
- Limitações: `PREENCHER`
