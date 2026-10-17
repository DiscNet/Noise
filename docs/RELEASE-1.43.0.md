# Noise! V1.43.0 — Advanced Studio: Camadas, Tipografia e Transformação

## Edição visual
- Barra esquerda compacta, com ícones vetoriais e agrupamento por toque prolongado.
- Cada grupo exibe as ferramentas relacionadas em menu contextual; manter pressionado o ícone para escolher a variante.
- Miniatura real do conteúdo de cada camada, desenhada diretamente do raster, texto ou forma.
- Marcador de tipo no canto da miniatura: R (raster), S (objeto inteligente incorporado), T (texto) e V (forma).
- Painel inferior/lateral alternável entre **Camadas** e **Texto**.

## Texto e tipografia
- Aba Caractere / Parágrafo: editar texto, fonte do sistema, tamanho, cor, negrito, itálico, espaçamento entre letras e entrelinhas, alinhamento, posição X/Y, largura e altura.
- O texto permanece como camada paramétrica. Preview, máscaras, filtros e exportação compartilham o mesmo desenho de glifos.
- As propriedades são salvas no projeto com histórico de desfazer/refazer.

## Transformações
- **Mover** está separado de **Transformação livre**.
- Transformação direta no canvas: oito alças de escala e uma de rotação; movimento do conteúdo dentro do retângulo.
- Caixa de parâmetros de transformação: X, Y, largura, altura, vínculo de proporção, rotação e inclinação horizontal/vertical.
- Presets: girar 90° para ambos os lados, girar 180°, espelhar horizontal e vertical.
- Estilo paramétrico: inclinação e geometria são preservadas no `.noise`.

## Objetos inteligentes incorporados
- Conversão entre uma camada raster e um objeto inteligente incorporado que preserva os pixels originais enquanto aplica transformações e efeitos.
- Marcador S identifica o objeto; a pintura direta é reservada a camadas raster.
- **Limitação:** não é um Smart Object compatível com PSD, nem possui edição aninhada de documentos.

## Compatibilidade e limites
- Formato nativo `.noise` **v4**, com leitor compatível com v1, v2 e v3.
- Continua válido o limite de até 2 megapixels por camada para máscaras, filtros CPU e sombras.
- Ainda pendentes: perspectiva por vértice, distorção livre/warp de malha, importação PSD estruturada, seleção múltipla e fontes externas TTF/OTF.
- As opções de transformação implementadas não representam paridade completa com o Photopea.

## Testes
- Testes instrumentados incluem round-trip de características de texto, inclinações e pixels de objeto inteligente.
- A aprovação dos testes e a assinatura oficial dependem de execução concluída do GitHub Actions.
