# Noise! V1.52.0 — Centered Stroke and Two-Sided Emboss/Pillow

## PhotoCraft source reference
- https://github.com/storytold/photocraft/blob/main/crates/compose/src/effects.rs
- https://github.com/storytold/photocraft/blob/main/crates/doc/src/effects.rs
- https://github.com/storytold/photocraft/blob/main/crates/gpu/src/plan.rs

## Fixes
- **Contorno → Centro** now paints half the band outside and half inside the silhouette, instead of drawing both portions behind an opaque layer.
- **Contorno → Dentro** paints over the source. **Fora** paints behind it. Split raster passes have independent cache keys, preserving their individual placement when styles change.
- **Relevo (dentro + fora)** applies independently computed interior light and exterior highlight/shade alpha, spanning the layer boundary, including partly covered edge pixels.
- **Almofada (dentro + fora)** also extends across both sides of the border but reverses the outer-facing illumination normal. The inside remains correctly lit.
- **Bisel interno** remains constrained to the original layer alpha; **Bisel externo** remains exterior.
- The style menu labels explain which modes cross the edge.
- The document format stays .noise v7, compatible with existing saved style values, non-destructive edits and undo/redo.

## Reference and scope
PhotoCraft is open-source and independently developed, not the proprietary Photopea source code. The Java implementation takes its principles (BevelPaint::Both and StrokePosition::Center width split) as reference, but does not guarantee identical results to Photopea on all images or blend combinations.

## Validation
Android instrumentation checks center stroke in/out placement, pure inside and outside strokes, two-sided emboss and pillow shadows, and existing app regressions.
