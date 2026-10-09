# Noise! 1.6

Editor de fotos Android offline, com processamento GLSL em tempo real (OpenGL ES 2.0), interface dark glass e exportação PNG.

## Galeria ao abrir

- A tela inicial apresenta uma **grade de fotografias recentes** indexadas pelo MediaStore (máximo de 500 por carregamento) e uma seleção de **álbuns/pastas**. Miniaturas são carregadas em threads separadas e guardadas num cache de memória.
- A primeira execução pode pedir permissão para ler as fotos; se não for concedida, é possível usar o **seletor de fotos do próprio Android** sem liberar acesso à galeria inteira.
- O botão de galeria no editor reabre os últimos arquivos/pastas; o ícone no cabeçalho da galeria abre o **Photo Picker** no Android 13+ ou a atividade de galeria do celular em versões anteriores.
- Não usamos mais `ACTION_OPEN_DOCUMENT` (gerenciador de arquivos) para escolher imagens.

## Redimensionar com gestos

O botão Redimensionar abre **duas barras de arrastar**, com preview ilustrativo da proporção, trava de proporção original e atalhos **25%, 50%, 100%, 200%**. Não há caixas numéricas para preencher. As dimensões são aplicadas à **exportação** sem destruir a foto original. O tamanho máximo permanece 4096×4096 px.

## Imagem e atmosfera

- A área atrás da foto é renderizada com shader leve: gradiente grafite/violeta, pequenos grãos, linhas sutis e iluminação ambiente suave.
- Prévia da imagem no formato original, encaixada sem cortes nem distorções.
- **Glow nas linhas do Dither:** haloes coloridos mais definidos ao redor dos traços brilhantes, preservando o fundo escuro.
- **Desfoque rotativo:** a vinheta é aplicada por último para que o blur não apague nem sobreponha as bordas escurecidas.
- **Ícones verdadeiros:** desenhos vetoriais antialiasing consistentes para controles de edição, ações e atalhos; não dependem de emojis/fontes.
- Efeitos anteriores (Fade, Tom de pele, Poeira, Vinheta, Aberrações, Névoa, Nitidez e demais controles Dither) continuam disponíveis.

## Desenvolvimento

JDK 17, Android SDK 35, Gradle 8.9. Execute `gradle --no-daemon assembleDebug assembleDebugAndroidTest`. O GitHub Actions verifica o editor e publica APK apenas quando todos os testes no emulador passam.

**Privacidade:** fotos continuam no aparelho; nenhum upload ou serviço de rede é necessário. A permissão de galeria é opcional para quem usa o seletor nativo do Android.

[Baixar último APK](https://github.com/DiscNet/Noise/releases/latest)
