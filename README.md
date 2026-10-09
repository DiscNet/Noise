# Noise! 1.3

Editor de fotos Android offline com prévia OpenGL ES 2.0 e visual dark glass inspirado no conceito de interface fornecido.

[Baixar APK da última release](https://github.com/DiscNet/Noise/releases/latest) · Android 8 ou superior

## Interface

- Barra superior **Noise!** com abrir foto e exportar PNG, abas **Basic**, **Noise** e **Dither**.
- Visual grafite escuro, cantos arredondados, bordas sutis e controles translúcidos. Prévia em destaque, sem barras pretas: imagens são **recortadas somente na prévia** para ocupar toda a área, mantendo a imagem completa no PNG salvo.
- Painel inferior com controles responsivos; pressione **Original** para comparar, use **Reset** para restaurar, e **Invert** para inverter as cores.
- Grupo Basic: saturação, contraste, vibração, exposição, highlights, branco, preto e matiz.
- Grupo Noise: granulação orgânica em intensidade positiva e suavização bilateral em intensidade negativa.
- Grupo Dither: intensidade do padrão, glow e RGB Shift. Botão **Aplicar Neon da referência** prepara as três barras (94/66/13) sem alterar o original.

## Dither neon

**Versão 1.3:** shader autoral baseado na imagem de referência (**não é o código do Dither Boy**): linhas horizontais finas, onduladas conforme luminância e contorno, pontilhismo irregular, dithering Bayer ordenado, separação de regiões frias (azul/lilás) e quentes (rosa/laranja), sombras muito escuras e brilho emissivo na silhueta. O padrão escala conforme a prévia para evitar aliasing em imagens grandes, e a exportação conserva detalhes na resolução original. Os controles funcionam em tempo real via uniforms da GPU e são reproduzidos na exportação PNG. A granulação foi corrigida para usar hash de coordenadas inteiras da imagem toda, sem ladrilhos de 256 pixels.

A aparência exata depende da foto e dos controles, portanto a prévia do conceito é uma referência estética, não uma promessa de resultado idêntico em todas as imagens.

## Compilação

JDK 17, Gradle 8.9, Android SDK 35: `gradle assembleDebug assembleDebugAndroidTest`. GitHub Actions compila, executa testes instrumentados no emulador e publica a release apenas se os testes terminarem com sucesso. APK de desenvolvimento assinado com chave de depuração; pode exigir desinstalar versões anteriores em caso de troca de assinatura.
