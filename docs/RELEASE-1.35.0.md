# Noise! V1.35.0 — Home Studio

## Identidade visual mais expressiva, sem excesso
A nova tela inicial segue a mesma linguagem da câmera e do editor: grafite profundo, luz lilás, linhas de órbita discretas e tipografia clara. O círculo permanece como símbolo central da identidade do Noise! e como entrada real para importar imagens.

- **Hero com profundidade:** o círculo manteve a silhueta, a borda fina contínua e a animação suave, agora com dois arcos externos delicados e um ponto de luz alinhado ao contorno. Os elementos pertencem à mesma composição; não são decorações espalhadas aleatoriamente.
- **Luz ambiente:** preservadas as manchas fotográficas suaves, em movimento de baixa velocidade, sem vídeos, imagens externas ou texturas que sobrecarreguem a GPU.
- **Informação integrada:** rótulos pequenos identificam o estúdio e as ferramentas existentes (Dither, ASCII e cor), sem simular funcionalidades novas.
- **Hierarquia melhor:** logotipo, contexto do produto, chamada principal e caminhos de criação ficam em posições claras, preservando espaço para a imagem.

## Mais funcionalidades à vista
- **Editar foto:** cartão principal que abre a importação/galeria existente.
- **Câmera:** segundo cartão que abre diretamente a câmera integrada do Noise!.
- **Continue criando:** retoma o projeto real mais recente; aparece apenas quando existe um projeto salvo. A Home de primeira execução não é preenchida com um botão inoperante.
- **Navegação inferior:** Início, Biblioteca e Projetos, todos conectados aos fluxos existentes.
- Ícones vetoriais nativos desenhados no app, incluindo um novo pictograma de câmera; nada depende de um serviço online.

## Transições e responsividade
- Um único sistema de brilho violeta, gradientes opacos discretos, feedback de toque rápido e dois traços orbitais animados. Não há glassmorphism excessivo, efeitos aleatórios ou overlays que impeçam a interação.
- Ajuste responsivo do tamanho dos títulos e ocultação das legendas ornamentais da órbita em telas compactas, mantendo os botões acessíveis.
- Animações da esfera e do fundo são pausadas quando a tela não é visível e respeitam a configuração de animação do Android.

## Validação
- Compilação Java/Android e testes instrumentados no emulador para layout, árvore de Views, esfera, fundo, ação real dos botões, projeto recente e dimensões compactas.
- A suíte existente de renderização GPU, editor, câmera, exportação e assinatura de APK continua como critério obrigatório de release.
- Não houve adição de efeitos de edição fotográfica: as melhorias desta versão são **efeitos e interações visuais da tela inicial**, mantendo o editor existente sem regressões.

Versão `1.35.0` · `versionCode 44` · mesmo `applicationId` e assinatura permanente.
