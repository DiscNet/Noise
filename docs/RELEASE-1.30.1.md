# Noise! V1.30.1 — Rich Film, contorno sutil e menus orbitais refinados

Versão-base: V1.30 (branch de desenvolvimento). Identidade, rodas circulares e projetos existentes preservados.

## Câmeras retrô mais marcantes
- Anteriormente os presets eram majoritariamente combinações de exposição, fade e granulação leve; agora o **pipeline GPU possui uma camada de filme analógico própria**.
- O tratamento de cor em `tone.frag` inclui resposta de película com toe nas sombras, shoulder nos realces, separação entre sombras frias e realces quentes e transições tonais suaves.
- `editor.frag` aplica granulação **real em múltiplas escalas** (fina, média e grossa) sem padrões de textura repetitivos, halation quente ao redor de fontes de luz e vazamento opcional de luz nas bordas.
- Quatro parâmetros independentes em **Detalhes → Ajustes avançados**: `Textura de filme`, `Cores de filme`, `Halation` e `Vazamento de luz`. Todos funcionam em tempo real, salvam no projeto e são exportados.
- Os 12 presets analógicos existentes foram reforçados com diferentes perfis de película. Novo **Cobre R · Rich Film**: riqueza tonal, aquecimento equilibrado, halation e textura granular visível. É um look próprio inspirado no objetivo estético de câmeras retrô como OldRoll; **não replica o preset proprietário Pro R**.
- Controles neutros desativam totalmente a camada nova. O modo comparar original ignora os efeitos. Projetos V1.30 e anteriores permanecem compatíveis porque `EditorDocument` já serializa parâmetros por índice opcional.

## Tela inicial
- A esfera passou a um painel luminoso com cantos **menos arredondados**, contorno branco/violeta delicado, glow por traços suaves e segundo traço discreto pontilhado.
- Mantida a iluminação volumétrica e os gradientes móveis, sem voltar a encher a composição de barras ou bolinhas.

## Menus orbitais
- Ícones com superfície escura ou violeta e **borda branca fina**, iluminada por dois traços translúcidos (sem blur caro).
- Indicador deslocado para **fora** do ícone, deixando livre o espaço entre texto e ícone. Foi reduzido em largura e opacidade.
- Preservada a rotação circular, a orientação das rodas e o fade in/out.

## Verificação
- Compilação `assembleDebug assembleDebugAndroidTest`, instrumentação em emulador e revisão das capturas pelo GitHub Actions.
- Testes ampliados para os 13 presets, intensidade do Rich Film, diferenciação pixel a pixel, compatibilidade do modo original e presença da borda luminosa dos ícones.
- Não declarar testes em aparelho físico concluídos sem execução. Publicar em Releases somente após validação.
