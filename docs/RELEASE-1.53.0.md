# Noise! V1.53.0 — Mesclagem, réguas, guias, ferramentas e transformação

## Modos de mesclagem
- Implementados 27 modos RGB selecionáveis em Camadas: Normal, Dissolver, Escurecer, Multiplicar, Subexposição/Superexposição de cor, linear, Cor mais escura/clara, Tela, Sobrepor, Luz suave/direta/vívida/linear/de pino, Mistura sólida, Diferença, Exclusão, Subtrair, Dividir, Matiz, Saturação, Cor e Luminosidade.
- A composição de alfa usa a equação W3C para *source over*, com fallback calculado em software para os modos indisponíveis no `Canvas` Android.
- Os controles de mesclagem individuais dos estilos de camada também incluem todos os modos, com composição local por software para os não suportados nativamente.
- Referência de arquitetura: `storytold/photocraft` — `crates/color/src/blend.rs` e `crates/compose/src/psblend.rs`. A implementação Java é independente.

## Alinhamento
- Menu **Visualizar**: Réguas, Mostrar guias, Alinhar automaticamente, Nova guia vertical/horizontal, Guias ao centro e Limpar guias.
- Guias verticais/horizontais em coordenadas do documento, persistentes no formato nativo `.noise` **v8**, que lê versões anteriores.
- Régua sobreposta ao canvas com marcadores de pixels, sem aparecer no PNG exportado.
- Snapping dos limites e centros da camada ativa às guias, às bordas/centro do documento e às bordas/centro de outras camadas visíveis; indicação temporária durante o deslocamento.

## Transformação
- Arrastar uma alça lateral preserva a borda oposta (anteriormente a escala era aplicada ao centro).
- Escala ancorada, inclusive em camadas giradas/inclinadas.
- Controle `↔ Livre` permite ativar/desativar vínculo de proporções para esticar uma dimensão independentemente.

## Organização da interface
- Abas de Texto e Camadas substituídas por controles compactos `T` e `▤`.
- Configuração do pincel separada do painel inferior: botão `✎ Pincel` abre tamanho/opacidade em painel próprio, com ação para escolher cor.
- O painel inferior mantém comandos compactos e mais espaço para o conteúdo.

## Estilos adicionais e bisel
- Efeitos **Acetinado** e **Sobreposição de padrão** (xadrez, listras e pontos), ambos persistentes, parametrizáveis, com cor, escala, rotação, opacidade e modos de mesclagem.
- Os modos de luz e sombra do **Bisel e Relevo** também utilizam o cálculo estendido de mesclagem; a superfície Suave recebeu uma média espacial suave na normal para reduzir granulação em textos menores.
- Referência comportamental: `storytold/photocraft` — `crates/compose/src/effects.rs`. Não é cópia literal do código e não pressupõe fidelidade pixel a pixel em todos os estilos.

## Limites
- O editor mantém seus limites anteriores de rasterização de camadas e de processamento por CPU. Misturas em software são mais custosas que os modos suportados pela GPU/Canvas.
- Ainda faltam alguns recursos para paridade total com o Photopea, como biblioteca de padrões importados, objetos inteligentes vinculados a arquivos externos e controle completo de suavização baseado em vetor.
- Testes instrumentados adicionados para todos os modos, transformações ancoradas, guias, serialização e novos efeitos.
