# Noise! 1.4

Editor de fotografias Android, offline, com prévia OpenGL ES 2.0 e exportação PNG.

[Baixar o APK mais recente](https://github.com/DiscNet/Noise/releases/latest) · Android 8 ou superior.

## Controles e tradução

A interface dark glass agora organiza os ajustes em quatro abas roláveis:

- **Básico:** Saturação, Contraste, Vibração, Exposição, Highlights, Branco, Preto, Matiz.
- **Ruído:** suavização bilateral para valores negativos e granulação orgânica para valores positivos.
- **Dither:** neon de linhas onduladas e pontos de luz, Desvio RGB e botão **Aplicar Neon da referência**.
- **Efeitos:** **Fade**, **Tom de pele (Skin Tone)**, **Poeira (Dust)**, **Vinheta (Vignette)**, **Aberrações (Aberrations)**, **Névoa (Mist)**, **Brilho difuso (Glow)** e **Nitidez (Sharpen)**.

Os novos efeitos ficam **desativados por padrão** e são ajustáveis de 0 a 100, com visualização ao deslizar e sem debouncing. Glow saiu da aba Dither e funciona sozinho ou combinado ao Dither. O preset de neon também configura Glow, mas não o habilita automaticamente ao abrir uma foto.

## Efeitos de imagem (versão 1.4)

- **Fade:** look fosco, sombras elevadas e altas luzes levemente reduzidas.
- **Tom de pele:** reforço seletivo de tons quentes por faixa de cor; não identifica pessoas nem usa reconhecimento facial.
- **Poeira:** partículas e microarranhões sintéticos pseudoaleatórios, determinísticos e não repetidos em um tile de textura.
- **Vinheta:** escurecimento gradual da periferia, preservando o centro.
- **Aberrações:** desvio radial das componentes vermelha/azul nas bordas da lente, distinto do Desvio RGB uniforme.
- **Névoa:** difusão espacial e redução sutil de contraste, para atmosfera enevoada.
- **Brilho difuso:** realce suave aproximado das regiões claras, com ou sem Dither.
- **Nitidez:** filtro unsharp 4 amostras, reforçando detalhes e bordas.

O processamento é por fragment shader na GPU, com os mesmos uniforms na prévia e na exportação. Não é um algoritmo de IA. Efeitos muito fortes podem aumentar a carga gráfica em dispositivos modestos. Opacidade/alpha original é mantida no PNG. A prévia preenche a tela por recorte, mas a exportação usa a imagem inteira.

## Projeto

O Dither neon é uma implementação própria inspirada na referência visual fornecida, não no código-fonte do Dither Boy. A aparência exata depende da imagem, exposição e intensidades configuradas.

Requisitos de build: JDK 17, Gradle 8.9, Android SDK 35.
Execute `gradle assembleDebug assembleDebugAndroidTest`. O workflow GitHub Actions compila, testa em emulador e publica uma nova release apenas quando tudo passa. O APK publicado é assinado com chave de desenvolvimento e pode exigir reinstalação após uma mudança de assinatura.
