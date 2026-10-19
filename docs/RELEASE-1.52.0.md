# Noise! V1.52.0 — Relevo central, Almofada e Contorno central

## Correção do Bisel e Relevo

- **Bisel interno:** somente na silhueta interna.
- **Bisel externo:** somente na área exterior.
- **Relevo (Emboss):** une as metades interna e externa da borda, com largura proporcional ao tamanho do efeito.
- **Relevo de almofada (Pillow Emboss):** também ocupa os dois lados, mas calcula a iluminação exterior com a normal invertida em relação à metade interna.
- Pixels parcialmente cobertos pelo contorno são combinados em alfa pré-multiplicado, evitando que a renderização arredonde a borda por um limiar binário ou elimine sua porção externa.
- Mantém as três técnicas Suave, Cinzel duro e Cinzel suave e as opções de luz e sombra preexistentes.

## Correção do Contorno → Centro

- A espessura é dividida **50% para dentro e 50% para fora** da silhueta.
- A metade externa é composta atrás do conteúdo e a interna é composta acima dele; antes, as duas eram desenhadas embaixo da camada, ocultando a porção interna quando ela era opaca.
- Mantidos os modos Dentro e Fora com geometria independente, além da mesclagem e opacidade próprias do estilo.
- Bordas usam a cobertura alfa original e atenuação de subpixel.

## Referências técnicas

A implementação foi comparada com os códigos públicos de [PhotoCraft](https://github.com/storytold/photocraft), especialmente `crates/compose/src/effects.rs` (funções `bevel_geom`, `bevel_maps`, `stroke_widths`) e `crates/doc/src/effects.rs`, sob [licença Apache-2.0](https://github.com/storytold/photocraft/blob/main/LICENSE-APACHE). Os algoritmos continuam implementados independentemente em Java para Android; não foram incorporados arquivos do PhotoCraft.

[Photopea — Layer Styles](https://www.photopea.com/learn/layer-styles) foi usado como referência de terminologia e interação.

## Compatibilidade e testes

- VersionCode 71 / VersionName 1.52.0; formato nativo `.noise` permanece v7, sem migração.
- Testes instrumentados para contorno central dentro/fora, modos estritamente internos e externos, opacidade e mesclagem, Relevo com ambos os lados e Almofada com iluminação exterior invertida.
- Renderizações complexas dependem das limitações de pixels e memória já estabelecidas no Advanced Studio. Não se afirma equivalência pixel a pixel com o renderizador fechado do Photopea.
