# Noise! V1.37.4 — menus orbitais com alto contraste e identidade em quatro cores

A pedido do usuário, removemos o campo escalar de tinta aplicado aos menus na V1.37.3 e **restauramos o efeito metaball Bézier suave existente antes dessa versão**. A rolagem, gestos, encaixe, seleção, favoritos e crossfade não foram alterados.

## Nova identidade visual exclusiva dos menus rotativos

- Quatro cores com funções claras: **preto** para cabeçalho e scrim, **roxo-escuro** para dock/painel, **roxo** para ações secundárias e cartões alternados, **branco** para caixas de ícones, botão Selecionar e foco.
- Caixas de ícones **brancas e preenchidas**, com glifos pretos. Contorno claro com brilho branco em múltiplas linhas, mais intenso na seleção; sem simulação de vidro.
- Os cartões das legendas alternam roxo e roxo-escuro e interpolam suavemente até branco ao ganhar foco. A cor do texto interpola de branco a preto para manter legibilidade durante a roda.
- Botão Selecionar branco com texto preto, borda e halo branco; favorito branco/preto; cabeçalho preto com título claro; área de instruções roxa.
- Superfícies mais opacas, camadas separadas por contraste e bordas quase quadradas (raios de 4–6 dp).
- Granulação estável: textura pontilhada determinística de 64 × 64 pixels, criada uma única vez e compartilhada por materiais da roda. É recortada nas formas reais de cada box, sem animação aleatória, blur ou recomputação de bitmap por frame.
- A membrana clássica voltou, agora com cor violeta mais firme e linhas brancas mais intensas, mas **sem a simulação procedural da V1.37.3**.

## Preservação funcional e escopo

A Home e o fundo fluido da V1.37.2 não foram tocados. Nenhum arquivo do renderer, shader, controles ou assets da Home foi alterado. Câmera, editor, filtros, exportação, assinatura permanente e arquitetura do app seguem iguais. Novas alterações estão restritas a `OrbitalSelector`, `LiquidSelectorLayer`, ao novo `OrbitalMaterial`, ao teste orbital e aos metadados/documentação.

## Testes

CI deve compilar, executar os testes instrumentados com a ponte Bézier e verificar caixas brancas, cartões opacos e cantos reduzidos. Inspecionar capturas no artefato `Noise-smoke-tests` para validar contraste e granulação visual. Perfil de performance/fps em celular físico continua recomendado.

Versão `1.37.4` · `versionCode 53` · APK oficial assinado pela keystore permanente após CI na main.
