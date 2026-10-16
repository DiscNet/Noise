# Noise! V1.36.2 — Fundo orgânico e seletor sem saltos

## Material visual: vidro removido

O efeito de vidro texturizado foi **removido**, conforme o feedback da versão 1.36.1.

- O arquivo `TexturedGlass.java` foi excluído do projeto.
- Os cards da Home, o painel de criação, os botões superiores e a retomada de projeto utilizam superfícies foscas grafite, sem amostragem do fundo, blur, ruído de vidro ou simulação de refração.
- O botão de confirmação do menu rotativo também utiliza uma superfície fosca.
- Os elementos retangulares permanecem quase quadrados, com **raios entre 6 e 7 dp**; o círculo central continua perfeitamente circular.
- Os recursos de edição de imagem e captura permanecem independentes dessa estética.

## Fundo de tinta orgânica, conforme referência enviada

- A paleta passa a ser predominantemente monocromática: **papel claro, grafite e preto**.
- Silhuetas curvas e assimétricas compõem faixas de tinta com áreas vazias expressivas, em vez de massas chapadas ou manchas translúcidas arbitrárias.
- As bordas dos traços recebem granulação procedural determinística, difusão gradual de opacidade e microirregularidades de contorno.
- O resultado é gerado com um bitmap de tinta armazenado em cache e gradiente de base. Não é um wallpaper externo nem uma fotografia gerada.
- Um deslocamento mínimo e lento da arte preserva a sensação de movimento da Home sem criar pulsações exageradas.
- Títulos da Home são escuros sobre a área clara; o botão circular, os cartões e a navegação inferior continuam com texto claro sobre fundo escuro para garantir legibilidade.

## Seletor rotativo: transição contínua e responsiva

- Antes, a borda e a cor do ícone eram trocadas abruptamente quando o item arredondado mudava. Essa troca visual foi substituída por **interpolação da intensidade de seleção com o deslocamento real da roda**.
- A borda de origem perde opacidade enquanto a borda do ícone seguinte recebe opacidade progressivamente. Apenas o ícone encaixado possui contorno totalmente opaco em repouso.
- O brilho dos símbolos e a cor dos títulos também acompanham o deslocamento fracionário, sem alternar instantaneamente o peso tipográfico.
- O metaball em Bézier continua sincronizado à posição da roda, inclusive durante deslocamento lento, movimento rápido e inversão de direção.
- A mola do deslocamento foi recalibrada para um encaixe mais viscoso e suave, sem disparar um novo animator a cada troca de ícone.
- A deformação de contorno continua sendo uma geometria real, não uma mudança isolada de opacidade.

## Testes e limitações

- Testes instrumentados verificam: ausência do vidro na Home, cartões foscos de raio reduzido, regiões claras e escuras do fundo com granulação, evolução contínua da borda ao passar pelo meio de dois itens, metaballs e fluxos existentes da câmera/editor.
- A publicação requer compilação Android, testes instrumentados e assinatura com a mesma keystore permanente.
- As características visuais e o desempenho final ainda devem ser conferidos em aparelho físico, inclusive na velocidade da animação em telas de 60 Hz.

Versão `1.36.2` · `versionCode 48` · mesmo package e assinatura das versões oficiais anteriores.
