# Noise! V1.37.2 — desgaste geométrico real das bordas

Correção do fundo fluido da Home, mantendo as funções da V1.37.1 e sua paleta, com desgaste **na própria silhueta da tinta**, e não uma camada de ruído sobre uma borda lisa.

## Mudança técnica

- O limite tinta/papel agora depende de um `erodedIsovalue` variável no espaço da própria massa fluida. O `smoothstep` é aplicado somente **depois** de alterar esse limite: ele desenha a forma já irregular.
- Ruído multiescala cria reentrâncias largas, lascas menores, pequenas ilhas de tinta e microfalhas que removem material na fronteira. A tendência positiva do deslocamento favorece o desgaste para dentro da massa escura.
- As amostras são calculadas em `q`, a coordenada deformada pelo próprio campo fluido. As irregularidades acompanham a forma ao se expandir, fundir, separar e reagir ao toque, em vez de ficarem coladas à tela ou cintilarem quadro a quadro.
- Removido o `overspray` adicionado na V1.37.1: nenhuma pintura de partículas em cima da máscara limpa nem erosão simulada por redução arbitrária de opacidade após o desenho.
- Mantido o campo escalar geométrico, a mistura de pigmentos, o fundo, a proteção de contraste do cabeçalho, o ciclo de vida, o controle térmico, os perfis de qualidade, a redução de movimento, o editor, a câmera e a roda de menus.
- Reutiliza `uNoise` existente em três consultas de ruído procedural por pixel, sem alocação, textura nova, passagem extra nem processamento na CPU. O detalhamento fino escala com o perfil de qualidade.

## Validação

O workflow Android compila o APK e os testes, verifica o shader GLES2 em uma `TextureView` real no emulador, grava capturas do fundo e testa a assinatura com uma chave descartável. A saída de prévia não usa a assinatura permanente e não deve substituir uma instalação oficial. Após passar pela revisão e integração em `main`, a publicação oficial utiliza a keystore permanente já configurada.

A aparência e a cadência devem ser examinadas também em aparelho físico, pois SwiftShader não fornece um número de FPS representativo. Caso necessário, calibrar o tamanho das lascas e a densidade das falhas sem alterar a lógica de fusão das massas.

Versão `1.37.2` · `versionCode 51` · mesma identificação de pacote e assinatura permanente das releases anteriores.
