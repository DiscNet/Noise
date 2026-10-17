# Noise! Advanced Studio — arquitetura Fase 1

## Escopo
Nova modalidade independente do editor fotográfico. Package Java `com.discnet.noise.advancedstudio`, sem substituir `EditorDocument`, `ProjectStore`, `GlPipeline` ou `EditorWorkspace`.

- `StudioDocument`: id UUID, tamanho, DPI, background, camadas com UUID, ordem, posições, bloqueio, visibilidade, opacidade e modo de mistura. Snapshots de metadados para undo/redo.
- `StudioProjectStore`: diretório privado `files/advanced_studio/<UUID>.noise`, ZIP versionado, escrita atômica, recuperação, exportação `Pictures/Noise!` via MediaStore.
- `AdvancedStudioScreen`: ambiente Android View próprio com menu superior, opções de ferramenta, barra vertical, canvas no centro, lista de camadas e status de zoom. Layout landscape/tablet com painel lateral; portrait com painel inferior.
- `MainActivity`: integração limitada à abertura/fechamento e `ACTION_OPEN_DOCUMENT` para inserir camadas. A Home tem um acesso destacado de baixa densidade.

## Modelo e limites
A versão 1 fornece somente `RasterLayer` funcional; o formato permite evolução, mas não afirma compatibilidade com PSD nem outras camadas. Compositor trabalha em sRGB 8-bit via Canvas/Bitmap e API Android (BlendMode 29+, PorterDuff fallback). O sistema antigo continua separado.

O importador de imagens aceita os formatos que o BitmapFactory Android reconhece e até 16 Mpx por fonte nesta fase. O histórico de propriedades não é salvo no ZIP na Fase 1; alterações de documento e camadas sim. Um único documento permanece visível por vez, e os demais são reabertos da lista de projetos.

## Layout/gestos
Um dedo em Mover altera a posição da camada selecionada e registra comando de histórico. Em Mão movimenta a área. Dois dedos fazem pan/pinch, sem abrir menu orbital do editor convencional. O painel Camadas permite selecionar, duplicar, excluir, alterar ordem e opacidade, ocultar e bloquear. Operações não desenvolvidas não aparecem como opções vazias.

## Fases seguintes
Fase 2: brush engine, eraser, formas/texto editáveis, seleções e transformações. Fase 3: máscaras, styles, filtros com semântica correta. Fase 4: smart objects, ferramentas profissionais, tiles e interoperabilidade.

## Evolução 1.41.0
Camadas tipadas (RASTER, TEXT, RECTANGLE, ELLIPSE). Texto e geometria serializados parametricamente e renderizados pelo mesmo compositor de exportação, sem bitmap de texto no `.noise`. Stroke/fill/gradient ColorMatrix implementados em StudioRasterTools, cada edição raster substitui referência do Bitmap (copy-on-write) após checkpoint, permitindo undo pixel a pixel. Seleção é RectF no espaço do documento e limita operações de raster. Crop altera dimensões e offsets com undo, sem eliminar bitmaps de origem. UI mantém rotas independentes do editor original.
