# Noise! V1.28 — Refinamento dos painéis, Transformar e navegação orbital

Versão-base: **V1.27, release build-119**. Mudanças aplicadas em `feature/noise-v1-28-refinement`.

## Controles e painéis
- Cartões de ajuste compactos e arredondados em cinza escuro, Manrope, valores alinhados à direita, organização em grupos e cabeçalho contextual.
- Sliders com trilho fino, marcador de neutro, preenchimento violeta e indicador com microanimação durante o toque.
- Restauração individual com botão ↺; permanecem o duplo toque no rótulo e o pressionar prolongado para ativar/desativar.
- Entrada numérica em sheet escuro próprio com sinal/decimais, botões ±, validação, restaurar, cancelar e aplicar.
- Ajuste ao vivo preservado sem debounce; um gesto de slider continua sendo uma operação única no histórico.
- Scroll vertical sem scrollbar, com rolagem preservada no rebuild quando apropriado.
- Painel compacto, intermediário e expandido com reserva de área mínima para o preview.

## Transformar: recorte e resolução
- A categoria Ferramentas apresenta separadamente **Recortar** e **Redimensionar**.
- Crop original permanece totalmente não destrutivo com alças de canto/laterais e arraste do centro. A V1.28 adiciona proporção **Original**, chips seletivos, grades com fade durante o gesto, dimensões calculadas com a resolução real e Manrope.
- Nova interface compacta de redimensionamento: prévia, resolução original/nova, largura e altura editáveis, proporção bloqueada por padrão, presets 25/50/75/100% e Original, formato PNG/JPEG e qualidade JPEG contextual.
- Exportação continua automática em `Pictures/Noise!`.
- Nova serialização opcional `outputWidth`/`outputHeight`. Projetos antigos que armazenam só `outputLongEdge` são compatíveis.
- **Política:** apenas redução. Pedidos acima da resolução original e limites da GPU são limitados. Sem promessa de reconstrução de detalhes.

## Menus orbitais
- Rodas circulares reais e orientação V1.27 preservadas: Ferramentas à direita, Efeitos à esquerda, gesto exclusivamente pelas bordas.
- Hitboxes de ícones agora calculadas pelas posições e escalas reais das Views; removida a seleção por X/Y estimados.
- Máquina de estados explícita (`CLOSED`, `OPENING`, `OPEN`, `CLOSING`); impedidos overlays duplicados enquanto uma roda anterior faz fade out.
- Toque fora da região do painel fecha o menu sem ativar a imagem ao fundo.
- Favoritos por roda usando estrela no cabeçalho e acesso por toque prolongado; última seleção de ferramenta e efeito preservada entre sessões.
- Fade/gradiente escuro em tela inteira, scroll orbital com mola amortecida, snap e reação independente do dedo preservados.
- Respeito à preferência Android de desligar animações, e shader de halo radial armazenado em cache.

## Verificação
- Auditoria inicial: [AUDIT-1.28.md](AUDIT-1.28.md).
- Testes instrumentados estendidos para política de resolução, compatibilidade JSON, proporção automática do sheet, hitbox selecionada e capturas de três fases orbitais.
- Os requisitos de velocidade, dimensões pequenas e instalação física dependem de validação e não são presumidos aprovados por compilar.

## Observações
- Ainda não há editor de super-resolução: aumentar dimensões acima das nativas não recupera qualidade e está desabilitado.
- A estrutura base de projetos, histórico e pipeline GPU é mantida. O layout não foi substituído por listas no lugar das rodas.
- A aprovação para release definitiva requer instrumentação bem-sucedida e revisão das capturas, além de checagem física quando possível.

## Correção adicional — gestos e bibliotecas personalizadas
- O swipe orbital **só pode começar dentro do retângulo visível do preview**, próximo às bordas da imagem. A área do painel de ajustes, seus sliders, a alça do sheet, cabeçalho, galeria e telas de projetos/presets ficam excluídas da ativação.
- Após usar um slider ou ação, a roda continua acessível; uma proteção detecta fechamento visual antigo e um watchdog de descarte encerra overlays que poderiam permanecer invisíveis capturando toques.
- Substituída a lista de projetos baseada em `AlertDialog` por **ProjectLibraryScreen**, com cartões visuais Manrope, miniaturas reais e prévias GPU dos ajustes salvos quando suportado, geradas e cacheadas fora da UI thread. Abrir, renomear e excluir têm telas de confirmação próprias.
- Substituída a aba de Presets baseada em `AlertDialog`/`PopupMenu` por biblioteca com prévias GPU, aplicar, salvar com nome, renomear, favoritar e excluir, todos em componentes próprios.
- Mantidos originais e receitas de projetos antigos. Alterações de nome usam a cópia mais atual do documento no disco; exclusão solicita confirmação.
- Tests Android adicionais: rejeição de swipe sobre ajustes, reabertura da roda após editar, exibição de miniaturas reais da biblioteca de projetos, e abertura de Presets sem diálogos nativos.
- Capturas esperadas: `noise-projects.png` e `noise-presets.png`, além de painel, transformação e fases da roda.

## V1.28.1 — correção de navegação e retomada das transições após edição

- **Bug corrigido:** uma roda em estado de fechamento podia bloquear a abertura seguinte. O editor agora aceita imediatamente outro swipe pelas bordas válidas do preview, descarta a roda antiga antes de criar a próxima e reconcilia o estado `orbitalClosing` no descarte.
- **Prioridade de gestos:** o gesto só é iniciado na área visível do preview; sliders e painel inferior não acionam menus.
- **Abertura com atraso físico:** `SwipeNavigationHost` transmite o progresso horizontal ao seletor; o deslocamento do dedo modifica o alvo de posição, opacidade, escala, scrim e gradiente. A posição exibida se aproxima desse alvo usando mola criticamente amortecida (não fica colada ao dedo).
- **Fade restaurado:** o fundo escuro em tela inteira e o gradiente lateral reagem suavemente à entrada; ao soltar, atingem sua intensidade final com easing temporal. No fechamento, todos os componentes retornam à transparência antes de a View ser removida.
- **Compatibilidade:** geometria circular V1.27 preservada, com Ferramentas à direita e Efeitos à esquerda; nenhuma alteração em projetos ou filtros.
- **Testes de regressão:** após alterar um slider e trocar de categoria, abrir Efeitos pela borda; fechar e abrir Ferramentas imediatamente; comprovar que o alvo muda com o dedo, mas a View não teleporta no mesmo evento de toque.

Instalador de teste atualizado: `Noise.V1.28.1.apk`. Não é release estável até aprovação da instrumentação e revisão das capturas de emulador.
