# Noise! V1.35.1 — Home editorial e seleção líquida de ferramentas

## Home — acabamento preciso
- Todas as **superfícies não circulares da tela inicial** passam a usar cantos de **12 dp**: painéis, cartões de edição/câmera, retomada de projetos e botões superiores. O círculo principal continua circular.
- Removidos os traçados orbitais externos, os arcos soltos, o ponto luminoso decorativo e legendas que competiam com o conteúdo.
- O círculo usa agora luz fotográfica interna mais contida, uma borda fina contínua e gradações de grafite e violeta sem exagerar em saturação ou glow.
- Mantidos os acessos funcionais à edição, à câmera e aos projetos, com as ações e a disposição responsiva introduzidas na V1.35.0.
- Os movimentos de fundo/círculo continuam discretos, pausando quando a Home não está visível e respeitando a configuração do Android.

## Seletor rotativo — foco e efeito líquido
- **Apenas o ícone efetivamente alinhado ao centro do seletor recebe borda branca.** Ícones fora do foco têm superfície grafite sem contorno, inclusive durante rolagem.
- A borda é removida enquanto dois itens passam entre pontos de encaixe e retorna somente ao centro quando um novo ícone se alinha.
- Ao encaixar em outro efeito ou ferramenta, o contorno selecionado **se transforma em uma membrana fluida**: o perímetro sofre uma deformação orgânica curta e suave (285 ms) e recupera o formato final.
- A animação utiliza um `Path` e um `Drawable` reutilizável no Android Canvas: não carrega bitmaps, não cria partículas e não altera os efeitos aplicados à imagem.
- Se as animações do Android estiverem desativadas, a borda se posiciona imediatamente sem transição. A animação é cancelada quando o seletor fecha.

## Qualidade e compatibilidade
- Testes instrumentados verificam: 12 dp em painéis/cartões, ausência de arcos decorativos antigos, borda somente no ícone central, ausência de contorno durante transição, diferença real de pixels entre estágios líquidos e funcionamento do seletor.
- As ferramentas de edição, presets, exportação, câmera, gestos e assinatura permanente permanecem inalterados.
- A publicação oficial depende dos testes completos no GitHub Actions e da geração do APK com a keystore permanente.

Versão `1.35.1` · `versionCode 45` · mesmo identificador `com.discnet.noise`.
