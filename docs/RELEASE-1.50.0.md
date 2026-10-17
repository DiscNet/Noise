# Noise! V1.50.0 — Bisel e relevo sem a segunda silhueta

## Origem do problema

O Noise! renderizava o texto-base e o mapa de relevo em buffers independentes. As duas máscaras eram combinadas sobre o canvas, o que causava discrepâncias nas bordas de caracteres curvas e diagonais, áreas cinzas e reflexos brancos/pretos sujos — sobretudo na palavra "Luka" enviada como exemplo.

A V1.49.0 já separou a iluminação em SCREEN e MULTIPLY, mas ainda mantinha **duas imagens de efeito sobrepostas à silhueta rasterizada original**. Esta versão elimina a discrepância estrutural.

## Correção

- Novo pipeline: uma única rasterização de alta qualidade do texto, desenho do relevo e composição **no mesmo raster**; redução da imagem composta apenas uma vez.
- Bisel interno tem exatamente a mesma cobertura alfa da camada original: zero pixels de borda adicionais. Sem efeito de extrusão fantasma causado pela iluminação.
- Distância euclidiana assinada exata, normais filtradas e antialiasing adaptativo para contornos de glifos (até 3× de resolução nos elementos pequenos).
- **Suave** cria um perfil arredondado; **Cinzel duro**, uma rampa com aresta firme antialias; **Cinzel suave**, um perfil de facetas arredondadas.
- Luz e sombra RGB continuam **independentes**, com SCREEN e MULTIPLY como padrões Photopea, e controles próprios de cor, opacidade e modo de mesclagem.
- A iluminação com força zero mantém rigorosamente os pixels originais, sem introduzir lavagens cinzas ou mudanças de RGB.
- O efeito continua não destrutivo, editável e compatível com os projetos `.noise` v7.

## Confirmação e limitações

- Novos testes instrumentados: palavra `Luka`, preservação do alfa, neutralidade com iluminação zerada, modos de mesclagem diferentes, contorno com suavização e ausência de cinza em regiões iluminadas/sombreadas.
- Comparação funcional com os conceitos publicados pela documentação Photopea e as opções visíveis no editor. O código-fonte do Photopea é proprietário: **não se afirma cópia exata de seu código nem paridade pixel-perfect**.
- Processamento usa orçamento de memória adaptativo de aproximadamente 900 mil pixels intermediários por passada para evitar picos de memória em dispositivos Android.
