# Noise! V1.28 — Auditoria inicial da base V1.27

Escopo inspecionado antes de alterações: `EditorPanel.java`, `PrecisionControl.java`, `EditorTheme.java`, `EditorWorkspace.java`, `CropEditor.java`, `MainActivity.java`, `EditorDocument.java`, `OrbitalSelector.java`, `SwipeNavigationHost.java`, `EditorSurface.java`. Integrações adicionais inspecionadas: `AdjustmentSpec.java`, `GlPipeline.java`, `ExportManager.java`.

## Bugs reproduzidos
Na inspeção inicial **não foi executada ainda a reprodução instrumentada da V1.28**. Não classificar como reproduzidos os problemas visuais relatados antes da execução de testes.

## Limitações verificadas diretamente no código
- `PrecisionControl.numeric()`: `AlertDialog` com botões Android convencionais, em contraste com o tema do editor. Os valores também são inicialmente formatados em duas casas indiscriminadamente.
- `PrecisionControl`: botões de restauração individual apenas por gesto de duplo toque; controles visuais têm espaçamento amplo.
- `EditorPanel.rebuild()`: remove/reinsere todas as Views de controles em trocas contextuais, redefinindo o scroll na seleção de categoria. O layout de painel não possui cabeçalho e grupos avançados.
- `EditorTheme.Slider`: desenha trilho e marcador com paint reutilizado, mas o estado pressionado muda tamanho de forma abrupta.
- `EditorWorkspace`: dimensões do sheet só contemplam largura e altura; estados intermediários explícitos e acomodação dinâmica ainda precisam de trabalho.
- `CropEditor`: alças e manipulação por pixel já existentes; proporção **Original** não aparece, texto de tamanho baseado no bitmap de preview e animação da grade não existe.
- `MainActivity.showExport()`: `AlertDialog` antigo e um campo único de **lado maior**, sem largura/altura editáveis, com ligação à exportação e sem seção Transformar.
- `EditorDocument.outputSize()`: fator nunca ultrapassa 1 (política implícita contra upscaling), tamanho de saída serializado apenas como `outputLongEdge`. Projetos antigos precisam permanecer compatíveis.
- `OrbitalSelector.OrbitStage.onTouchEvent()`: hit test aproxima o toque por `arcX(theta)-75dp` e distância ponderada, ignorando as bounds transformadas das rows e icon Views. `OrbitalSelector` usa flags separadas `closing`/`dismissed` sem enum completo; riscos de repetição de eventos/callbacks durante remoção.
- `SwipeNavigationHost`: detecta abertura por bordas, cancela eventos do child e evita segundo dedo; toque aberto precisa de proteção contra propagação.
- `EditorSurface`: `ScaleGestureDetector` e `GestureDetector` compartilham o toque; prioridades entre zoom e navegação devem permanecer.

## Melhorias de design propostas (não são bugs comprovados)
- Painel de controle compacto com Manrope, cards #17161D e sliders neutro→atual, numeric sheet próprio.
- Seção Transformar com recorte e redimensionamento por largura/altura, presets e proporcionalidade.
- Menus orbitais espelhados, persistência da seleção, fundo full-screen com fade, realce radial leve, hitbox precisa e máquina de estados.
- Preservar pipeline GPU, undo/redo, formatos, fluxo MediaStore, limites de textura, e projetos antigos.
- Política explícita V1.28: **somente redução**, sem prometer super-resolução artificial.

## Critérios
Os status passam a “reproduzido” apenas após falha confirmada de testes instrumentados e screenshot de emulador. Build/compilação separados de validação visual e de aparelho físico.
