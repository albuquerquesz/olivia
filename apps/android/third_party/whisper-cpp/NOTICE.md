# whisper.cpp — origem e versão fixada

Vendorizado a partir de https://github.com/ggerganov/whisper.cpp

- Tag: `v1.9.4`
- Commit: `927cfce34f31707e17f2bff35c349632fb9e2c3a`
- Licença: MIT (ver `LICENSE` neste diretório)
- Data da vendorização: 2026-09-18

## Escopo copiado

Somente `CMakeLists.txt`, `LICENSE`, `src/`, `include/` e `ggml/` da árvore upstream.
Nenhum arquivo foi modificado. Backends de GPU (CUDA, Metal, Vulkan, SYCL, OpenCL,
HIP etc.) não são compilados; o JNI em `app/src/main/jni/whisper/CMakeLists.txt`
define `GGML_USE_CPU` e não habilita nenhuma opção de backend acelerado.

## Por que vendorizado em vez de dependência Maven/AAR

Nenhuma AAR pública oferece o modelo quantizado multilíngue sem tier pago (ver
decisão registrada com o time em 2026-09-18). Vendorizar o código-fonte oficial
com hash fixado dá controle total sobre quantização e idioma sem depender de
licenciamento de terceiro, ao custo de manter a compilação NDK/CMake no projeto.

## Como atualizar a versão

1. Conferir o changelog da nova tag em upstream antes de trocar.
2. Repetir a cópia dos quatro caminhos acima a partir da nova tag.
3. Atualizar `Tag`/`Commit` acima e revalidar `app/src/main/jni/whisper/jni.c`
   contra a assinatura de `whisper_init_from_file_with_params_no_state` etc.,
   já que esse arquivo é uma cópia adaptada (ver seu próprio comentário de
   proveniência no topo).
