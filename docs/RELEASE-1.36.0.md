# Noise! V1.36.0 — Organic Backdrop, Textured Glass e Liquid Selector

## Linguagem visual comum
- Fundo **grafite, azul profundo e violeta contido**, com silhuetas de tinta assimétricas e interseções de formas em vez de blobs aleatórios.
- `OrganicBackdrop` desenha a composição vetorial, luz localizada e a atmosfera lenta. O círculo principal continua perfeitamente circular, sem novos anéis.
- `TexturedGlass` é um material reutilizável para superfícies escolhidas da Home: painel inicial, cartões de edição/câmera, retomada de projeto e botões superiores. O seletor também usa o material em seu botão principal.
- As superfícies mantêm cantos entre **8 e 12 dp**. A hierarquia foi mantida em vez de distribuir efeitos indiscriminadamente.

## Vidro com materialidade
- As superfícies da Home amostram o próprio fundo orgânico em resolução reduzida e usam um filtro de desfoque de duas passagens (kernel de tenda), próximo a um blur gaussiano de baixo custo.
- A microtextura procedural multiescala altera a amostragem e a transmissão de luz (deslocamento das coordenadas), simulando vidro irregular; não é uma imagem granulada colada por cima.
- O resultado é armazenado em um bitmap pequeno por material, atualizado com menor frequência. O desenho dos frames usa BitmapShader no Canvas e shader de reflexão suave, com realce de contorno discreto.
- Em superfícies que não têm acesso a um fundo amostrado, o componente usa uma superfície escura texturizada como fallback. **Não representa desfoque real de qualquer app subjacente nessas áreas.**
- Este material é usado *somente pela interface*. Nenhum filtro, ruído ou alteração de textura é aplicado às fotografias editadas.

## Liquid transition no seletor rotativo
- `LiquidSelectorLayer` desenha uma **ponte de metaball por curvas de Bézier** entre ícones adjacentes, com duas zonas de influência e perímetro contínuo.
- O movimento vem diretamente do `offset` fracionário do seletor; acompanha o dedo e o amortecimento já usado pela roda.
- Ao girar Vinheta → Glow, a massa se alonga, une os elementos, transfere intensidade e se desfaz ao encaixar. Reverter o gesto reverte a forma no mesmo frame, sem fila de animações.
- Ícones inativos continuam **sem contorno**; o novo selecionado recebe borda branca apenas após o alinhamento.
- Se as animações do sistema estiverem desativadas, a ponte não é desenhada e a seleção responde imediatamente.
- O botão de confirmação da roda utiliza material de vidro de baixa intensidade. Não foi criada uma caixa rígida nova envolvendo toda a roda.

## Desempenho e compatibilidade
- O fundo usa Paths e Gradients em Canvas; o vidro mantém cache por tamanho/posição e usa escala baixa no blur. A ponte usa um Path e três Paints reutilizados, sem partículas e sem bitmaps por quadro.
- Os elementos respondem à visibilidade e às preferências de animação do Android.
- Testes instrumentados verificam fontes reais de blur, variação da textura, geometria da transferência líquida, ícones e ações funcionais, além da suíte atual de editor, câmera e exportação.
- O acabamento visual, a suavidade real das transições e o FPS final dependem de verificações no dispositivo físico.

`versionCode 46` · `versionName 1.36.0` · assinatura permanente do Noise! preservada.
