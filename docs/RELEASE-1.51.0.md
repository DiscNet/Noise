# Noise! V1.51.0 — Bisel e Relevo com arquitetura de mapa de altura

## Referência estudada
O projeto [PhotoCraft](https://github.com/storytold/photocraft), em especial
[`crates/compose/src/effects.rs`](https://github.com/storytold/photocraft/blob/main/crates/compose/src/effects.rs)
(funções `bevel_geom`, `bevel_height`, `bevel_chisel_h` e `bevel_maps`),
foi usado para estudar a organização do efeito. PhotoCraft é um projeto
independente do Photopea, licenciado sob MIT ou Apache-2.0.
Os princípios foram adaptados ao Android Java; não se trata de integração com
Photopea nem de cópia binária de seu renderizador.

## Implementação
- **Suave:** usa a cobertura alfa do texto ou forma como mapa de altura, filtrado por uma função tent (duas passagens box horizontais e duas verticais). O relevo é a inclinação dessa superfície arredondada, não apenas uma máscara alfa esfumada.
- **Cinzel duro:** usa rampas lineares derivadas de distância euclidiana assinada para criar facetas planas.
- **Cinzel suave:** compartilha as rampas de distância do cinzel, mas utiliza um deslocamento diferente junto à borda e um suavizador específico para relevos em ambos os lados.
- **Iluminação:** calcula a normal 3D a partir do gradiente do mapa de altura, inclui ângulo, altitude, profundidade e direção; avalia iluminação relativa à superfície plana.
- **Luz e sombra:** mapas separados com mesclagem independente, conforme os controles já existentes. A região plana fica sem tingimento artificial ou haze cinza.
- **Contorno:** aplicado às intensidades de luz/sombra, não ao alfa da fonte ou à geometria.
- Mantém o pipeline compartilhado de visualização e exportação, a rasterização ampliada de texto, cache de efeitos e preservação da transparência original no bisel interno.
- `.noise` mantém a versão 7; os projetos atuais continuam compatíveis.

## Segurança e desempenho
- O orçamento de renderização continua limitado a 900.000 pixels de trabalho com reescala dinâmica para documentos maiores.
- O modo Suave evita calcular campos EDT desnecessários; os desfoques box são implementados com soma incremental por linha/coluna e custo linear.
- O efeito não altera destrutivamente o texto nem outras camadas.

## Testes
- Regressão com `Luka`, antialias e ausência de silhueta fantasma fora do contorno.
- Comparação dos três modos de técnica.
- Teste adicional de placa retangular: centro neutro sem névoa cinza; iluminação direcional diferente em bordas opostas; exterior transparente.
- Testes instrumentados do Noise! para edição, câmera/GPU, undo, máscaras e projeto.

## Limitação de fidelidade
A arquitetura segue a metodologia observável do código PhotoCraft, mas isso não significa
igualdade pixel a pixel com Photopea ou Photoshop. Curvas, fonts, gestão de cor,
precisão numérica e características de cada dispositivo ainda podem introduzir diferenças.
