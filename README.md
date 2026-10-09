# Noise! 1.5

Editor de fotografias Android offline (Android 8+, OpenGL ES 2.0).

## Interface
Visual dark glass inspirado na referência de UI, com superfícies translúcidas, reflexos leves, cantos arredondados, brilho violeta/rosa e controles compactos. Esta interface usa transparência e gradientes; a desfocagem real do conteúdo da prévia por trás dos painéis não é implementada porque o conteúdo é um `GLSurfaceView` e a captura a cada quadro afetaria a latência.

Abas **Básico**, **Ruído**, **Dither** e **Efeitos**. Ajuste enquanto move os sliders, compare com o original, redefina e salve em PNG.

## Novidades 1.5
- **Brilho difuso:** glow de realces com amostras simétricas em oito direções. Não desfoca nem arrasta a imagem base, e funciona independentemente do Dither.
- **Desfoque rotativo:** soma de amostras em arco tangente às circunferências concêntricas, aparecendo progressivamente nas bordas.
- **Dither profissional:** intensidade, profundidade/níveis tonais, posição X/Y do padrão, escala, densidade de pontos e ondulação, além do desvio RGB e preset da referência.
- **Poeira analógica:** flocos aleatórios, fibras diagonais, microarranhões, anéis de emulsão e granulação fina; distribuição determinística não repetitiva.
- **Prévia sem corte:** imagem inteira preservada na sua proporção original (fit-center). O espaço excedente usa o fundo escuro do editor em vez de recortar a fotografia.
- **Redimensionar:** botão ⤢ na barra superior, largura/altura entre 1 e 4096 px, manutenção opcional da proporção e botão Original. Redimensiona o **PNG exportado** sem alterar os pixels da foto carregada.
- **Ícone adaptativo:** novo ícone com fundo escuro de ponta a ponta e símbolo dentro da área segura, evitando bordas brancas do launcher.

As opções 1.4 continuam disponíveis: Fade, Tom de pele (seletivo por cor, sem detecção de rosto), Vinheta, Aberrações, Névoa e Nitidez. O Dither é uma implementação visual autoral inspirada na referência e não reproduz o código de Dither Boy.

## Compilar e testar
`gradle --no-daemon assembleDebug assembleDebugAndroidTest` (JDK 17, Gradle 8.9, SDK 35). O GitHub Actions valida um emulador Android e publica APK apenas após passar o smoke test. O build usa assinatura de debug; atualizar uma instalação assinada com chave diferente pode exigir desinstalar a versão anterior.

[**Baixar o APK mais recente**](https://github.com/DiscNet/Noise/releases/latest)
