# Noise! 1.13

Um editor de imagem Android offline com interface simples, processamento de filtros via OpenGL ES 2.0 e exportação PNG para **Pictures/Noise!**.

## Interface 1.13 — painel de edição de verdade

O editor agora prioriza a **usabilidade para ajustes**, inspirado na organização do Lightroom Mobile sem copiar seus ícones nem sua identidade gráfica:

- **Prévia equilibrada:** aproximadamente 40% da área flexível.
- **Painel de ajustes amplo:** aproximadamente 60% da área flexível, com rolagem vertical e sliders em tempo real.
- **Categorias no rodapé:** Básico, Ruído, Dither, Efeitos, Arte, ASCII e ação Recortar. Podem ser percorridas horizontalmente.
- **Cabeçalho compacto:** Noise!, Abrir, Salvar e Mais. Recorte permanece acessível na faixa de ferramentas inferior e no menu.
- **Sem glassmorphism:** cores sólidas cinza-escuro, sem transparência de vidro, brilhos, chanfros, orbes, fitas coloridas e fundos luminosos.
- **Menos ícones:** pequenos vetores no cabeçalho; valores e nomes dos parâmetros ficam claros, sem ícones ao lado de cada slider.

Os controles de intensidade até **200%** da versão 1.12, todos os efeitos GPU, a galeria, o recorte por gestos e o salvamento público de PNG foram preservados. O APK continua versionado, como `Noise.V1.13.apk` no GitHub Releases.

## Cores do Dither no ASCII

A aba **ASCII** transforma fotografias em **arte formada por caracteres reais**, como `;`, `:`, `%` e `@`, mantendo a saída como uma imagem PNG. O fundo preto com caracteres brancos reproduz a proposta da referência; cores originais e um segundo conjunto de caracteres são opcionais.

Ative **ASCII → Cores do Dither** para colorir os caracteres com a mesma paleta neon do Dither: **azul, laranja e vermelho**, conforme os tons da foto. O botão ativa o ASCII automaticamente e atualiza a prévia na hora, sem precisar ligar o efeito Dither. Desligue para voltar ao branco sobre preto ou selecione **Usar cores da foto**; os dois modos de cor são alternativos. A escolha é preservada ao recriar a tela e também no PNG salvo.

O efeito possui botão independente para ligar/desligar e **seis ajustes em tempo real**: densidade, contraste, brilho, limiar, espaçamento e tamanho dos caracteres. O botão **Aplicar estilo da referência** configura rapidamente o visual preto e branco. A prévia e o PNG exportado usam o mesmo shader OpenGL ES 2.0 e o mesmo atlas de caracteres, sem converter a imagem em texto ou aplicar uma imagem fixa.

## Efeitos de ARTE

Três efeitos exclusivos, criados proceduralmente a partir das três primeiras imagens de referência. **Não são imagens aplicadas como overlay estático.** Todos os 18 controles podem ser ajustados com barras, em tempo real, em qualquer combinação e no PNG exportado.

| Efeito | Proposta visual | Ajustes independentes |
|---|---|---|
| **Anéis** | círculos concêntricos brancos sobre preto, com desgaste irregular (primeira referência) | Intensidade, Espaçamento, Espessura, Centro X, Centro Y, Desgaste |
| **CRT** | tubo analógico em tons cinza-azulados, curvatura, scanlines e bordas escuras (segunda referência) | Intensidade, Frequência, Suavidade, Curvatura, Vinheta CRT, Tom do fósforo |
| **Glitch** | fotografia distorcida, interferência horizontal, faixas quebradas e alto contraste monocromático (terceira referência) | Intensidade, Faixas, Deslocamento, Falhas, Estática, Monocromia |

A intensidade inicial dos três modos é **zero**, para que nenhuma foto seja alterada ao abrir. Selecione **Arte → Anéis / CRT / Glitch → Aplicar efeito** para ativar o modo a 100%, e personalize qualquer valor de 0 a 100. Os demais efeitos já existentes (Básico, Ruído, Dither e Efeitos) continuam disponíveis; os estilos também podem ser sobrepostos entre si.

## Interface e referências

A interface atual utiliza fundos opacos e neutros, a foto acima, o painel de ajuste amplo abaixo e as ferramentas no rodapé. Os efeitos são processados em tempo real por OpenGL ES 2.0. Não há recursos de vidro ou sobreposições decorativas.

## Demais ferramentas

Galeria com fotos recentes e álbuns completos, recorte de imagem por arraste dos cantos, original/comparação, neon dither ajustável, ruído orgânico, pó e vinheta, brilho difuso sem arrasto direcional, realce de bordas sem deslocamento e salvamento automático em PNG na pasta pública **Imagens/Noise!**, que persiste após desinstalar o aplicativo.

## Build

JDK 17, Gradle 8.9 e Android SDK 35. Execute `gradle --no-daemon assembleDebug assembleDebugAndroidTest`. O CI testa os shaders no emulador e só publica a release quando os testes instrumentados passam.

[Instalar a versão mais recente](https://github.com/DiscNet/Noise/releases/latest)
