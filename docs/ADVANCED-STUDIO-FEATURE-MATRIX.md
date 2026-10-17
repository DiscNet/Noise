# Advanced Studio — matriz de cobertura (V1.40.0 Fase 1)

Estados: IDENTIFICADO (documentação), ANALISADO (requisitos e comportamento estudados), IMPLEMENTADO (código presente), TESTADO (CI instrumental aprovado), LIMITADO, PENDENTE. Um recurso só é TESTADO depois de a execução mais recente do CI confirmar os testes; antes disso permaneça IMPLEMENTADO/AGUARDANDO TESTE.

| Recurso | Estado no Noise! | Arquivo / limitação |
|---|---|---|
| Entrada Home | IMPLEMENTADO; aguardando CI | HomeScreen.java |
| Entrada editor convencional | IMPLEMENTADO; aguardando CI | MainActivity.java; importa foto original, não o preview filtrado |
| UI superior File/Edit/Layers/View | IMPLEMENTADO; aguardando CI | AdvancedStudioScreen.java |
| Menus Image/Select/Filter/Window completos | PENDENTE | Só comandos funcionais são expostos |
| Canvas central | IMPLEMENTADO; aguardando CI | StudioCanvas View |
| Camadas raster independentes | IMPLEMENTADO; aguardando CI | StudioDocument.Layer + bitmaps próprios |
| Adicionar / duplicar / excluir | IMPLEMENTADO; aguardando CI | StudioDocument, limita 40 |
| Renomear / visibilidade / bloqueio | IMPLEMENTADO; aguardando CI | AdvancedStudioScreen |
| Ordem / opacidade | IMPLEMENTADO; aguardando CI | painel layers e composição |
| Normal / Multiply / Screen | IMPLEMENTADO; aguardando CI, além Overlay/Darken/Lighten em API 29+ | Renderizador Canvas |
| Demais blend modes | PENDENTE | Sem nomes apontando para implementação falsa |
| Ferramenta Move, pan e pinch | IMPLEMENTADO; aguardando CI | StudioCanvas; ainda sem snap |
| Documentos múltiplos em abas | PENDENTE | Persistência permite documentos isolados em disco |
| Histórico undo/redo de propriedades | IMPLEMENTADO; aguardando CI | 24 snapshots de metadata; não persiste após reabrir |
| Projeto nativo .noise | V2 IMPLEMENTADO, aguardando CI; abre v1 | ZIP: PNG por raster; texto/vetor JSON paramétrico |
| Recuperação automática (debounce) | IMPLEMENTADO; aguardando CI | após operações; não há journal incremental |
| Salvar + reabrir camadas e posição | IMPLEMENTADO; aguardando CI | StudioProjectStore |
| Exportação PNG MediaStore | IMPLEMENTADO; aguardando CI | Android 10+; compositor inclui texto e formas |
| Importar imagem como camada | IMPLEMENTADO; aguardando CI | ACTION_OPEN_DOCUMENT; pixels originais; sem PSD |
| Brush / Eraser / Clone / Healing | Brush e Eraser IMPLEMENTADOS, aguardando CI; Clone e Healing PENDENTES | StudioRasterTools; gesto único e pixels com Undo |
| Seleções / Crop / Transform Livre | Seleção retangular, crop confirmável, mover/escalar/rotacionar IMPLEMENTADOS; demais modos PENDENTES | StudioCanvas e StudioDocument |
| Texto editável / vetores / shapes | Texto e formas retângulo/elipse IMPLEMENTADOS; Pen/Bezier e múltiplos estilos PENDENTES | Tipo sem bitmap, metadados reais no .noise |
| Máscaras raster / vetoriais | PENDENTE | Seleção geométrica não é máscara de camada |
| Adjustment layers / Filters | Brilho/Contraste/Saturação raster IMPLEMENTADOS; Adjustment layers e demais filtros PENDENTES | ColorMatrix raster, undo |
| Layer Styles / Smart Filters | PENDENTE | Pipeline por passes |
| Liquify | PENDENTE | Deslocamento persistente requer outro módulo |
| PSD/SVG/TIFF import estruturada | PENDENTE | Formatos não anunciados como completos |
| Tiles / documentos enormes | LIMITADO | até 8192 por lado e 16 MP por documento; 36 Mpx de camadas |
| ICC, 16/32 bit, CMYK | PENDENTE | Preview atual sRGB 8-bit |
| Atalhos completos / stylus pressure | PENDENTE | Fases posteriores |
| Teste visual comparativo Photopea | PENDENTE | Não houve sessão interativa do site |
| Documentação funcional Photopea | ANALISADO (parcial) | PHOTOPEA-FEATURE-AUDIT.md |

Status atualizado somente por evidência de ferramentas, jamais por desejo de paridade com Photopea.

## Fase 2 — 1.41.0 (CI pendente)
Pintura raster e borracha (pressão) com bitmap copy-on-write e um checkpoint por traço, preenchimento contíguo com tolerância, gradiente linear, seleção retangular, crop confirmável, transformação numérica, formas retângulo/elipse e texto paramétrico. Ajustes raster Brilho/Contraste/Saturação. Riscos/pendências: máscara, paths Bezier, suavização avançada de pincéis, seleção complexa, transformação perspectiva/warp, gradientes radial/angular, tipografia rica, filtros avançados, reamostragem de transformações. O status TESTADO requer CI aprovado.

## Fase 3A — 1.42.0
IMPLEMENTADO (aguardando CI): máscara raster por camada (PNG próprio, branco/preto, inversão, densidade, pintura, Undo/Redo); pilha não destrutiva ordenável de GAUSSIAN_BLUR/BRIGHTNESS/SATURATION; sombra projetada editável; formato v3 com leitura v1/v2. LIMITADO: máscaras e efeitos até 2 MP por camada; parâmetros do filtro sem preview live ao arrastar, apenas ao confirmar; Gaussian processado por CPU. PENDENTE: máscaras vetoriais, clipping, brush mask feather, Pen, Liquify, retouch, curvas, smart objects, ajustes completos e demais layer styles. Os itens somente mudam para TESTADO após CI aprovado.
