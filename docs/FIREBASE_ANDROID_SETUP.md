# Firebase Android — configuração local

O Firebase atende somente a análise visual. Não há Firestore, login, upload de vídeo, backup ou
persistência de frames neste MVP.

## Arquivos e variantes

- `apps/android/app/src/glasses/google-services.json`: arquivo real do app Android
  `com.olivia.companion`; é ignorado pelo Git.
- `apps/android/app/src/mock/google-services.json`: configuração inerte para o package mock;
  não habilita Firebase AI nem faz chamadas de rede.
- O plugin `com.google.gms.google-services` processa o arquivo específico de cada flavor.

## Console Firebase / Google Cloud

1. No Firebase, registre o Android `com.olivia.companion` e baixe o arquivo para o caminho acima.
2. No Firebase AI Logic, habilite o provedor/modelo autorizado e defina quota de desenvolvimento.
3. Em App Check, registre o certificado SHA-256 usado pelo APK de teste. Para distribuição fora da
   Play Store, configure Play Integrity de acordo com o canal de teste e monitore antes de exigir
   enforcement.
4. Restrinja a API key ao package e certificado corretos no Google Cloud. Não transforme a key,
   App Check ou `google-services.json` em segredo de usuário; tokens administrativos e debug
   continuam fora do repositório.

O app instala Play Integrity antes de criar o adapter Firebase AI. Se configuração, App Check,
quota ou modelo faltarem, o adapter falha com erro sanitizado; nunca troca silenciosamente para
mock nem envia mídia para outro serviço.

## Limite de evidência

Compilar ou declarar dependências não prova acesso ao Gemini, retenção do provedor, qualidade,
latência ou enforcement. Rodar o teste Android live controlado continua pendente de autorização e
configuração externa.
