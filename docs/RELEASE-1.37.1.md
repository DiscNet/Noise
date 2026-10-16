# Noise! V1.37.1 — bordas de tinta com granulação fina

Atualização visual incremental da Home, a partir da V1.37.0. O fundo procedural mantém seu movimento contínuo, fusões, separações, toques, qualidade adaptativa, redução de movimento e integração com os controles.

## O que mudou

- Adicionada uma faixa de *overspray* com partículas de tinta no contorno das massas, inspirada na referência de tinta preta com bordas pulverizadas sobre papel claro.
- A densidade das partículas diminui gradualmente para fora da silhueta; uma erosão sutil quebra a regularidade do lado interno do contorno.
- O interior das massas continua opaco, enquanto o papel e o espaço negativo permanecem limpos fora da faixa de granulação.
- A textura reutiliza os canais do ruído estacionário já gerado na GPU. Dois padrões em escalas diferentes reduzem repetições visíveis, sem bitmap extra, processamento de pixels pela CPU ou nova passagem de desenho.
- A proteção de contraste dos textos no topo continua ativa; a câmera, o editor, os menus rotativos, a assinatura permanente e as preferências não foram modificados.

## Compatibilidade e validação

- Fragment shader compatível com o pipeline OpenGL ES 2.0 existente.
- Mantém as metas de resolução e de cadência dos perfis Alto, Equilibrado e Econômico; o custo de dois acessos adicionais à textura precisa ser medido em dispositivo real.
- Executar `gradle --no-daemon assembleDebug assembleDebugAndroidTest` e os testes instrumentados da Home no workflow Android.
- Conferir as capturas e a gravação dos artefatos `Noise-smoke-tests` antes de considerar o acabamento visual definitivo.
- O vídeo no emulador Android usa SwiftShader e não comprova 60 FPS em aparelho físico.

Versão `1.37.1` · `versionCode 50` · mesmo package e mesma assinatura permanente, permitindo atualização da instalação oficial anterior.
