# Photopea — auditoria funcional: primeira passagem

Data: 2026-10-17. Referência pública: https://www.photopea.com/ ; documentação: https://www.photopea.com/learn/

## Método e limites
O site público e capítulos da documentação oficial foram consultados. **Não foi possível operar nesta sessão o canvas interativo do editor web** com mouse/teclado, abrir cada submenu ou variar cada parâmetro. Portanto, itens documentados abaixo estão no estado IDENTIFICADO ou ANALISADO DOCUMENTALMENTE, nunca VALIDADO POR INTERAÇÃO. Não são capturas de código ou de elementos proprietários. O inventário de menus completos, ranges/defaults de todos os filtros e comparações visuais necessita de auditoria guiada em navegador interativo.

## Organização observável na documentação
- **Workspace**: toolbar à esquerda; área de trabalho central com abas de documentos; sidebar direita com painéis; menu principal superior e barra de opções contextuais imediatamente abaixo. Grupos de ferramentas abrem por clique prolongado. Referência https://www.photopea.com/learn/workspace.
- **Layers**: camadas são entidades independentes, selecionadas no painel à direita; arrastar reordena; ícone do olho alterna visibilidade; opacidade e mesclagem situam-se acima da lista; rodapé agrupa nova camada, pasta, máscara, exclusão. Ctrl+click adiciona à seleção. Referência https://www.photopea.com/learn/layers.
- **Layer Manipulation**: Move move camada ativa; Ctrl temporário e setas movimentam 1 px, Shift+seta 10 px; Auto Select opcional. https://www.photopea.com/learn/layer-manipulation.
- **Masks**: preto oculta, branco revela, cinza medeia alfa; raster/vector masks ligáveis ou não, density e feather não alteram os pixels originais da máscara. https://www.photopea.com/learn/masks.
- **Selection**: mapa de pesos por pixel no nível do documento, independente da camada. https://www.photopea.com/learn/index.php?page=selections.
- **Text**: point text, paragraph text, texto sobre curva; camada de texto permanece editável. https://www.photopea.com/learn/text.
- **Vector**: paths, vector masks e shape layers com fills. https://www.photopea.com/learn/vector-graphics.
- **Brush**: brush track de múltiplas pontas, size/hardness e spacing no Brush panel; suporta pressão e ABR na documentação. https://www.photopea.com/learn/brush-tools.
- **Adjustments / Filters**: Image → Adjustments; Filter; Smart Filters de smart objects. https://www.photopea.com/learn/adjustments-filters.
- **Navigation / Crop**: Zoom e Hand, crop manipulável nos cantos, confirmar/cancelar na barra superior. https://www.photopea.com/learn/index.php?page=navigation ; https://www.photopea.com/learn/index.php?page=image-size.
- **Canais e composições**: Window → Channels, RGB/Alpha e canais de máscaras; Layer Comps guardam configurações de aparência/posição/visibilidade. https://www.photopea.com/learn/channels ; https://www.photopea.com/learn/index.php?page=layer-comps.

## Mapeamento proposto à UI Noise! Android
| Photopea | Noise! Advanced Studio | Evidência disponível | Lacunas |
|---|---|---|---|
| Toolbar | Ferramentas vertical | Documentação oficial | Variações, atalhos |
| Menu File | Arquivo | Hierarquia documental | Testar cada comando |
| Menu Edit | Editar | Hierarquia documental | Clipboard |
| Image | Imagem | Documentação de ajustes | Matriz de parâmetros |
| Layer | Camadas + painel | Documentação oficial | Máscaras / grupos |
| Select | Selecionar | Documentação oficial | Seleções complexas |
| Filter | Filtros | Documentação oficial | Categorias exatas |
| View | Visualizar | Documentação oficial | Grade, snapping |
| Window | Painéis | Documentação oficial | Lista de painéis |
| Sidebar/History | Gavetas/painel | Documentação oficial | Histórico persistente |

## Roteiro de inspeção interativa pendente
Abrir documento raster, texto e forma em Photopea real. Para cada ferramenta capturar: localização, variantes, ícone, popover contextual, unidade/range/valor inicial, alteração por drag/clique/teclado, prévia, Undo/Redo, máscaras e alfa, resultado exportado e notas Android. Repetir por categoria de filtro. **Não preencher defaults que não foram medidos.**
