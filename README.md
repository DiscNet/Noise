# Noise!

Editor de imagens Android, offline e sem anúncios.

Controles de −100 a +100: saturação, vibração, exposição (±3 EV), contraste, highlights, branco, preto, ruído (negativo: suavização bilateral; positivo: granulação determinística) e matiz (±180°). Prévia atualizada durante os ajustes, comparação ao segurar o botão, reset e exportação PNG com transparência.

## Instalar

Baixe **Noise.apk** na [última release](https://github.com/DiscNet/Noise/releases/latest). É um APK de teste assinado com chave de depuração, não uma versão de loja. Android 8+. O aplicativo usa o seletor de arquivos do sistema, sem permissões de armazenamento ou internet.

## Compilar

JDK 17, Gradle 8.9 e Android SDK 35. Execute `gradle assembleDebug`. O GitHub Actions compila cada push em main e publica o APK em Releases e no artefato Noise-APK.

## Processamento

Edição não destrutiva em sRGB. Prévia limitada a 900 pixels no maior lado, processada fora da interface; tarefas antigas são descartadas. Exportação usa a imagem carregada, limitada a 4096 pixels no maior lado por memória. Android 9+ corrige orientação EXIF pelo ImageDecoder; Android 8 usa BitmapFactory e pode exigir imagem previamente orientada. Redução de ruído é uma aproximação local 3×3, não restauração por IA. Os efeitos podem variar um pouco entre prévia e exportação por resolução.

O histórico anterior permanece no Git.
