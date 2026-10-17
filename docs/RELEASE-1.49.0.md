# Noise! V1.49.0 — Bisel e Relevo limpo, com composição de luz separada

## Diagnóstico e comparação com Photopea

A versão anterior ainda desenhava a cor-base do texto e uma segunda imagem de bisel produzida em resolução diferente. Ao sobrepor as duas silhuetas, surgiam linhas cinzas, bordas pretas, franjas brancas e a aparência de uma extrusão desalinhada. A luz e a sombra ainda eram misturadas em uma única camada de cor, diferentemente do padrão de estilos de camada usado no Photopea.

Photopea é um software de código fechado. Esta implementação foi desenvolvida de forma independente, reproduzindo opções e princípios observáveis, sem copiar seu código proprietário.

## Correção

- Um único raster de alta qualidade é usado **simultaneamente** para formar o texto e calcular seu bisel. Os pixels de origem e os de relevo não são mais desenhados em silhuetas independentes.
- A imagem com relevo é composta **antes** de ser reduzida à resolução final, evitando deslocamentos entre contornos e brilho.
- O bisel interno preserva **exatamente** o alfa da camada de texto de origem. Não são gerados pixels extras no fundo transparente.
- Luz e sombra são computadas em passagens RGB independentes, com modos de mesclagem e controles próprios: **Tela / Screen** para o realce e **Multiplicar / Multiply** para a sombra, como padrão.
- Foram adicionadas opções de **Mesclagem da luz** e **Mesclagem da sombra** ao painel de Bisel e Relevo; as opções são reeditáveis, salvam no mesmo documento .noise v7 e são mantidas em desfazer/refazer.
- Inclinação, técnica e contorno do bisel continuam ajustáveis: Suave, Cinzel duro e Cinzel suave usam geometria diferente e antialias nas bordas.
- Normal do relevo calculada de um mapa de distância euclidiana com filtragem de 3×3 e saída antialiased, preservando caracteres curvos e diagonais.
- Sem precisar rasterizar a camada de texto: conteúdo continua totalmente editável.

## Validação

- Testes instrumentados com a palavra `Luka` e caracteres curvos/diagonais para verificar ausência de pixels fantasmas.
- Testes de neutralidade: desativar a opacidade da luz e da sombra não pode alterar o RGB das letras.
- Teste de diferença real entre Screen e Normal na iluminação do relevo.
- Testes anteriores de máscara de corte, seleção, exportação, história e projetos permanecem ativos.

## Limites

Não há garantia de correspondência pixel a pixel com o Photopea: o código de renderização original é proprietário e a precisão depende de fonte, parâmetros, sistema de renderização e resolução. O alvo desta versão é corrigir de forma verificável os artefatos mostrados pelo usuário, não emular exatamente todos os algoritmos privados do Photopea.

A geração em alta resolução é adaptativa e limitada a 900 mil pixels internos para reduzir risco de consumo excessivo de memória nos aparelhos Android.
