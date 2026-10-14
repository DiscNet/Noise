# Noise! V1.19 — Gestos opostos, scroll com mola crítica e fade do fundo

- Arrastar **da direita para a esquerda** no editor abre **Ferramentas**.
- Arrastar **da esquerda para a direita** abre **Efeitos**, de forma independente. Não exige passar por Ferramentas.
- Enquanto uma roda está aberta, outro gesto não troca de menu; há possibilidade de fechamento por arrasto horizontal e pelo botão Voltar do Android.
- As duas rodas entram a partir de seus respectivos lados, com animações amortecidas e sem ligação rígida ao dedo.
- O escurecimento é uma camada separada, estática espacialmente, que faz **fade in** e **fade out** graduais (420/360ms), sem acompanhar lateralmente a roda.
- A **rolagem vertical orbital** agora utiliza integração de mola criticamente amortecida a cada frame, conservando velocidade entre o arrasto, a desaceleração e o snapping. Resposta adaptada à taxa de atualização da tela.
- Permanecem o shader corrigido de **Brancos**, os controles de edição reais, o preview OpenGL e a exportação.
- A suíte Android verifica gestos opostos, brilho crescente do scrim e interação de rotação.

Instalador de prévia: `Noise.V1.19.apk`. Necessário concluir os testes de instrumentação antes de publicar como release definitiva.
