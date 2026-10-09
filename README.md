# Noise! 1.1

Editor de fotos Android offline.

[**Baixar APK**](https://github.com/DiscNet/Noise/releases/latest) · Android 8 ou superior

## Edição

- Nove ajustes de −100 a +100: saturação, vibração, exposição (±3 EV), contraste, highlights, branco, preto, ruído e matiz (±180°).
- Prévia em OpenGL ES: cada evento da barra atualiza os uniformes do shader. O próximo quadro usa o valor mais recente, sem fila de processamento de bitmaps ou espera para soltar a barra. A fluidez final depende da GPU e da resolução.
- Inversão de cores por interruptor, combinável com os ajustes e incluída na exportação.
- Comparação com o original ao segurar o botão; redefinição geral ou de um controle ao tocar no valor.
- Ruído negativo aplica suavização bilateral 3×3; positivo adiciona granulação. Não é restauração por IA.
- Layout com painéis translúcidos sobre um fundo de luz difusa, cantos arredondados, bordas finas e acentos ciano, violeta e lima. Controles em Cor, Luz e Textura.

## Imagens e exportação

Seletor de documentos Android, sem permissão ampla de armazenamento ou acesso à internet. Imagens em sRGB, orientação EXIF corrigida. Limite de 4096 pixels no maior lado (ou o limite da GPU, se menor). O PNG usa o mesmo shader e o mesmo tamanho de textura da prévia, incluindo transparência. Leitura em blocos pequenos para reduzir o pico de memória. A exportação tem seu próprio contexto GL, fora da interface.

## Desenvolvimento

JDK 17, Gradle 8.9 e SDK Android 35. `gradle assembleDebug assembleDebugAndroidTest`.

O GitHub Actions compila o app e roda testes instrumentados no emulador Android 10: shader neutro, orientação, transparência no PNG, nove efeitos, redução de ruído, comparação, interruptor de inversão e quadros renderizados durante mudanças na barra. O artefato Noise-smoke-tests contém o resultado e uma captura real da interface. A release só é publicada após os testes passarem.

APK de desenvolvimento assinado com chave de depuração. A chave é reutilizada via cache do CI, enquanto o cache estiver disponível. Para substituir a versão 1.0 (ou após expirar o cache), pode ser necessário desinstalar o APK anterior. Uma versão de loja deve usar uma chave de produção armazenada como segredo.
