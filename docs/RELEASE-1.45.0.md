# Noise! V1.45.0 — Layer Styles, Visual Colors & Custom Fonts

## Color picker
- Visual HSV selector with saturation/value plane, hue, alpha and color presets.
- Foreground, editable text and layer-style color controls use the picker instead of requiring HEX codes.

## Fonts
- Import local `.ttf` and `.otf` files from Android's document picker.
- Validate and copy font to private app storage (maximum 16 MB), with persistent content-hash identity.
- Imported font participates in text metrics, canvas rendering, effects and PNG exports.
- Project stores a stable font reference. Fonts are installed in app-private storage, not embedded in the project; if the app is uninstalled without backup, custom font files can be lost.

## Layer styles
- Eight independently configurable styles: Bevel/Emboss, Drop Shadow, Inner Shadow, Outer Glow, Inner Glow, Stroke, Color Overlay, Gradient Overlay.
- Blend and opacity per style. Sizes, angles, distances, spread, gradient end colors, scale, stroke position, inner glow center/border, bevel light/shade controls, elevation, depth, technique and direction.
- Contour presets (linear, round, sharp, cone, ring, half round) and an editable five-control-point contour curve for bevel and glows.
- Real alpha-driven layer-style rendering for raster, editable text, vector shapes and basic smart layers, shared by canvas and PNG output.
- Expandable `fx` entries under each layer, with individual effect visibility and tap-to-edit.
- Styles stored non-destructively in .noise format **v5**, with v1–v4 backward compatibility, history snapshots and undo support.

## Scope and limitations
- This is an independent Android implementation based on publicly documented Photopea workflows, **not an identical reproduction of every Photoshop/Photopea option or rendering algorithm**.
- Some advanced functions such as exact Bezier contour editing, jitter/noise, global-light linkage, multiple contour sub-styles, texture/pattern overlay and true Photoshop PSD interoperability remain unavailable.
- CPU renderer limits detailed effects to 2 MP per layer to protect mobile devices; large layers render without these new styles.
- Project-level font dependencies require the corresponding custom font file to remain installed.
