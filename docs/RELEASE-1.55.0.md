# Noise! V1.55.0 — Workspace contextual e degradês editáveis

## Organização do Advanced Studio
- Barra de **opções da ferramenta** movida para cima da tela de trabalho. Cor, pincel/tamanho/opacidade e opções da ferramenta de degradê deixam o rodapé.
- Painel inferior reservado a camada, texto, efeitos/filtros, transformação, mesclagem, opacidade, máscara e estrutura.
- Controles compactados: mesclagem e opacidade compartilham uma linha; botões de camada em uma faixa horizontal com tamanho regular e rolagem quando não couber.
- Ferramentas laterais com menores margens e ícones centralizados, evitando corte lateral.
- Painel de texto sem três controles RGB redundantes: cor escolhida visualmente.
- Altura do painel de camadas ajustada à altura da tela, dentro de limites para evitar engolir o canvas.

## Novo modelo de degradê
- Um só modelo `StudioGradient` para sobreposição, contorno e brilhos interno/externo de camada, além de pintura raster.
- Geometrias linear, radial, angular, refletido e diamante, seguindo conceitos documentados pelo Photopea e arquitetura funcional do projeto público PhotoCraft. Implementação Java/Android independente.
- Até 12 pontos de cor **RGBA** com ajuste de posição, pontos médios entre cores, escala, ângulo, origem X/Y, inversão e dither.
- Editor visual com faixas de cor, seletores por toque e prévia em tempo real; sem obrigatoriedade de códigos HEX.
- Camadas: edição não destrutiva e independente de gradiente em sobreposições, contornos e brilhos; opções de mesclagem por estilo continuam configuráveis.
- Contorno admite preenchimento de cor sólida ou degradê preservando a posição interno/centro/externo; gradiente e ajustes sobrevivem a salvar/abrir o projeto.
- Raster: gesto de arrastar pinta gradiente com a mesma rampa e geometrias, inclusive transparência, com desfazer/refazer.
- Formato `.noise` v9, leitura retrocompatível de projetos anteriores. Estilos existentes de duas cores migram para duas paradas ao serem lidos.

## Paridade e limites
- O Photopea também dispõe de tipos de preenchimento por padrão e camadas de preenchimento editáveis próprias. A V1.55.0 implementa os degradês na ferramenta e em **sobreposição/contorno de camada**, mas **não** cria ainda uma categoria completa de camada de preenchimento de gradiente, nem importe/exporte gradientes PSD/.GRD.
- O alinhamento da ferramenta é baseado na direção do arrasto; gradientes de efeito usam sua própria caixa de camada. Colorimetria não é garantida pixel-a-pixel idêntica ao Photoshop.
- A experiência final precisa de inspeção em aparelhos com diferentes resoluções de tela, além da suíte automatizada.

## Referências técnicas
- https://www.photopea.com/learn/layer-styles
- https://www.photopea.com/learn/vg-structure
- https://github.com/storytold/photocraft (especialmente `crates/compose/src/gradient_fill.rs`, `crates/compose/src/effects.rs` e `crates/ui-egui/src/gradient_ui.rs`)

## Testes novos
- Gradientes de múltiplas paradas, transparência, pontos médios, inversão, geometria radial/diamante, pintura raster e persistência de estilo de contorno e sobreposição em `.noise` v9.
