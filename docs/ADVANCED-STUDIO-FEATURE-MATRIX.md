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
| Normal / Multiply / Screen | IMPLEMENTADO; aguardando CI | renderizador CPU Android Canvas + Paint |
| Demais blend modes | PENDENTE | Sem nomes apontando para implementação falsa |
| Ferramenta Move, pan e pinch | IMPLEMENTADO; aguardando CI | StudioCanvas; ainda sem snap |
| Documentos múltiplos em abas | PENDENTE | Persistência permite documentos isolados em disco |
| Histórico undo/redo de propriedades | IMPLEMENTADO; aguardando CI | 24 snapshots de metadata; não persiste após reabrir |
| Projeto nativo .noise | IMPLEMENTADO; aguardando CI | ZIP manifesto + PNG por camada |
| Recuperação automática (debounce) | IMPLEMENTADO; aguardando CI | após operações; não há journal incremental |
| Salvar + reabrir camadas e posição | IMPLEMENTADO; aguardando CI | StudioProjectStore |
| Exportação PNG MediaStore | IMPLEMENTADO; aguardando CI | Android 10+ nesta fase |
| Importar imagem como camada | IMPLEMENTADO; aguardando CI | ACTION_OPEN_DOCUMENT; pixels originais; sem PSD |
| Brush / Eraser / Clone / Healing | PENDENTE | Núcleo de raster precisa brush engine |
| Seleções / Crop / Transform Livre | PENDENTE | Não expostos como funcionais |
| Texto editável / vetores / shapes | PENDENTE | Tipos documentais posteriores |
| Máscaras raster / vetoriais | PENDENTE | Pipeline v2 |
| Adjustment layers / Filters | PENDENTE | Não reutilizar sliders globais incorretamente |
| Layer Styles / Smart Filters | PENDENTE | Pipeline por passes |
| Liquify | PENDENTE | Deslocamento persistente requer outro módulo |
| PSD/SVG/TIFF import estruturada | PENDENTE | Formatos não anunciados como completos |
| Tiles / documentos enormes | LIMITADO | até 8192 por lado e 16 MP por documento; 36 Mpx de camadas |
| ICC, 16/32 bit, CMYK | PENDENTE | Preview atual sRGB 8-bit |
| Atalhos completos / stylus pressure | PENDENTE | Fases posteriores |
| Teste visual comparativo Photopea | PENDENTE | Não houve sessão interativa do site |
| Documentação funcional Photopea | ANALISADO (parcial) | PHOTOPEA-FEATURE-AUDIT.md |

Status atualizado somente por evidência de ferramentas, jamais por desejo de paridade com Photopea.
