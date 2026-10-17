# Advanced Studio — pipeline Fase 1

Compositor `StudioDocument.render(Canvas)`: o mesmo código renderiza canvas de interação e `composite()` de PNG, evitando diferenças de ordem/opacidade entre prévia e exportação. Plano inicial transparente ou branco, em ordem bottom-to-top. Cada camada raster aplica bitmap no (x,y), alpha clamped 0..255 e modo real `SRC_OVER`, `MULTIPLY` ou `SCREEN`. Android >=29: `Paint.setBlendMode`; Android 26-28: `PorterDuffXfermode`. Esses modos devem ser verificados em testes de pixel (incluindo canais alfa) antes de sinalizar compatibilidade profissional.

O checkerboard é decoração do editor e não é exportado. A dimensão da saída é exatamente a do documento.

Restrições: renderização CPU/UI na prévia; fase inicial não implementa tiles, máscaras, modelos ICC, HDR nem GPU compositor multicamadas. O pipeline existente OpenGL ES 2.0 de edição fotográfica não foi modificado. Refatorar composição em passes GPU, cache de layers e tiles somente com testes de borda e alfa. Não apresentar demais mesclagens como disponíveis.

## Renderizador 1.41.0
Translação, rotação e escala independentes por camada dentro de clip da dimensão do documento, seguidas de blending/opacity. Camadas raster: source bitmap; camadas TEXT: Canvas drawText parametrizado; RECTANGLE/ELLIPSE: primitivas vetoriais Canvas. Mesmos comandos para preview e export. Escopo restrito a raster 8 bits/sRGB, sem paths complexos ou glyph shapers profissionais. Alteração de pixels via copy-on-write/Canvas, com clip em seleção retangular. API 29+ suporta Overlay/Darken/Lighten além de Multiply/Screen; fallback Android 26-28 será expandido em versão posterior.

## Fase 3A: filtros, máscaras e sombra
Ordem da camada com esses efeitos: gerar conteúdo (raster/texto/forma) -> executar stack de filtros ordenada (brilho/saturação com ColorMatrix; Gaussian com kernel separável e pré-multiplicação de canais) -> máscara raster com densidade e alfa -> sombra de camada como representação borrada do alfa da imagem mascarada -> opacity/blending no compositor -> outros layers em ordem z. Gaussian radius 1..8 e <=2 MP. O cache LRU de bitmaps renderizados usa chave com identidade da origem, máscara e parâmetros. Preview e export chamam StudioDocument.render(Canvas), mantendo ordem de passes idêntica.
