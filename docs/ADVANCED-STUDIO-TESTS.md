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
