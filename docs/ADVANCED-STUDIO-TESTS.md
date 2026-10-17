# Advanced Studio — testes Fase 1

Instrumentação `AdvancedStudioTest.run` chamada em `NoiseSmokeTest`, inclusive modo CI targeted.

Cobertura automatizada escrita:
1. Canvas 128x128 com camadas Vermelho e Azul, verifica composição RGBA por pixel.
2. Visibilidade altera pixels, Undo restaura e Redo reaplica.
3. Duplicar cria entidade independente, reordenar troca Z order.
4. Layer locked não pode ser apagada.
5. Salvar ZIP .noise com PNG real, fechar e carregar. Confere IDs, tamanho, ordem, camada raster e posições.
6. Rejeita dimensões inválidas e excessivas.
7. Instancia e libera uma tela AdvancedStudioScreen real na Activity Android.

Estado inicial: **TESTES ESCRITOS, NÃO AINDA APROVADOS**, até a execução do GitHub Actions terminar. Resultados, SHA da execução e limitação de performance devem ser atualizados após CI.

Falta cobertura visual em dispositivo físico, tablets, teclado, stylus, casos de memória exaurida, exportação MediaStore automatizada e comparação com Photopea. Não indicar paridade até testar.

## Cenários adicionados para 1.41.0
Gesto raster gera pixels reais; undo/redo recupera bitmaps de versões anteriores; borracha remove alfa e desfaz; seleção restringe pintura; balde realiza flood fill; gradiente altera pixels; ColorMatrix faz ajuste reversível; texto não é rasterizado; retângulo e elipse são retidos no manifesto; escala/rotação salvas; crop altera documento e é reversível. Não classificar como TESTADO até CI concluir.

## Fase 3A
Testes instrumentados adicionados: máscara branca deixa fonte visível, traço preto apaga alfa, raster original permanece intacto, undo/redo da máscara altera composição, inversão revela, desativação restaura o original, pilha de brilho e Gaussian modifica render sem modificar o bitmap, round-trip .noise v3 conserva máscara/ordem de filtros/sombra e mantém os pixels fonte intactos. Não declarar aprovação antes de verificar o run do GitHub Actions.
