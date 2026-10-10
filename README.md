# Noise! 1.8

Um editor de imagem Android offline com interface de vidro, processamento de filtros via OpenGL ES 2.0 e exportação PNG para **Pictures/Noise!**.

## Novidade: ARTE

Três efeitos exclusivos, criados proceduralmente a partir das três primeiras imagens de referência. **Não são imagens aplicadas como overlay estático.** Todos os 18 controles podem ser ajustados com barras, em tempo real, em qualquer combinação e no PNG exportado.

| Efeito | Proposta visual | Ajustes independentes |
|---|---|---|
| **Anéis** | círculos concêntricos brancos sobre preto, com desgaste irregular (primeira referência) | Intensidade, Espaçamento, Espessura, Centro X, Centro Y, Desgaste |
| **CRT** | tubo analógico em tons cinza-azulados, curvatura, scanlines e bordas escuras (segunda referência) | Intensidade, Frequência, Suavidade, Curvatura, Vinheta CRT, Tom do fósforo |
| **Glitch** | fotografia distorcida, interferência horizontal, faixas quebradas e alto contraste monocromático (terceira referência) | Intensidade, Faixas, Deslocamento, Falhas, Estática, Monocromia |

A intensidade inicial dos três modos é **zero**, para que nenhuma foto seja alterada ao abrir. Selecione **Arte → Anéis / CRT / Glitch → Aplicar efeito** para ativar o modo a 100%, e personalize qualquer valor de 0 a 100. Os demais efeitos já existentes (Básico, Ruído, Dither e Efeitos) continuam disponíveis; os estilos também podem ser sobrepostos entre si.

## Interface e referências

A quarta imagem inspirou o **novo plano de fundo** com fitas abstratas ondulantes em gradiente violeta, pêssego e rosa sobre preto, além de uma textura de brilho diagonal nos painéis de vidro. A interface é desenhada por código Android e continua interativa, sem transformar uma captura de tela em UI. Os elementos são inspirações estilísticas, não cópias de arte ou código de terceiros.

## Demais ferramentas

Galeria com fotos recentes e álbuns completos, recorte de imagem por arraste dos cantos, original/comparação, neon dither ajustável, ruído orgânico, pó e vinheta, brilho difuso sem arrasto direcional, desfoque radial rotativo e salvamento automático em PNG na pasta pública **Imagens/Noise!**, que persiste após desinstalar o aplicativo.

## Build

JDK 17, Gradle 8.9 e Android SDK 35. Execute `gradle --no-daemon assembleDebug assembleDebugAndroidTest`. O CI testa os shaders no emulador e só publica a release quando os testes instrumentados passam.

[Instalar a versão mais recente](https://github.com/DiscNet/Noise/releases/latest)
