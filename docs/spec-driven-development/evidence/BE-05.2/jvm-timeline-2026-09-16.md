# BE-05.2 — timeline e integração offline JVM

- Data: 2026-09-16
- Comando: `:core:test :core:integrationTest` sem daemon e sem configuration cache.
- Resultado: aprovado.
- Cobertura: eventos idempotentes entre instâncias, leitura após reinício, correção emitida e
  confirmada, arquivo temporário interrompido ignorado e arquivo confirmado corrompido falha
  fechado.
- Limite: a execução foi no JVM local. O compose offline entrega o mesmo perfil para Docker;
  execução do container não prova SDK Android, DAT, Bluetooth ou hardware.
