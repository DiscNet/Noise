# Noise! 1.11

Um editor de imagem Android offline com interface de vidro, processamento de filtros via OpenGL ES 2.0 e exportação PNG para **Pictures/Noise!**.

## Novo layout 1.11

O editor foi reorganizado para facilitar a edição em telas pequenas, mantendo o processamento GPU e todos os efeitos:
- **Topo simplificado:** marca Noise! e um único menu para ações secundárias.
- **Ações ao alcance do dedo:** Abrir, Recortar e Salvar PNG agrupados logo abaixo da prévia.
- **Abas horizontais roláveis:** Básico, Ruído, Dither, Efeitos, Arte e ASCII com alvos de toque confortáveis.
- **Controles com espaço:** nome e valor acima de um slider de largura total.
- **Vidro discreto:** fundo grafite, superfícies translúcidas e realces sutis em vez de fitas coloridas.

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

A nova interface prioriza a imagem editada: tons grafite, vidro translúcido sem exagero, navegação de categorias rolável e botões de edição agrupados. Os efeitos são processados em tempo real; as telas são Views Android desenhadas por código, não capturas estáticas.

## Demais ferramentas

Galeria com fotos recentes e álbuns completos, recorte de imagem por arraste dos cantos, original/comparação, neon dither ajustável, ruído orgânico, pó e vinheta, brilho difuso sem arrasto direcional, desfoque radial rotativo e salvamento automático em PNG na pasta pública **Imagens/Noise!**, que persiste após desinstalar o aplicativo.

## Build

JDK 17, Gradle 8.9 e Android SDK 35. Execute `gradle --no-daemon assembleDebug assembleDebugAndroidTest`. O CI testa os shaders no emulador e só publica a release quando os testes instrumentados passam.

[Instalar a versão mais recente](https://github.com/DiscNet/Noise/releases/latest)
