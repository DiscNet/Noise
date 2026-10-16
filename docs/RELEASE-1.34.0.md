# Noise! V1.34.0 — Controles de câmera e alinhamento fotográfico

## Refinamento da interface
- Barra superior com controles compactos, visor amplo de cantos suaves, ferramentas circulares menores e feedback tátil discreto.
- O seletor de proporção agora abre diretamente a lista **1:1, 4:3, 3:4, 16:9, 9:16, 3:2 e nativo**, evitando percorrer os formatos repetidamente.
- Régua horizontal de precisão mantida com indicador central fixo, deslizamento com inércia e retorno ao padrão por toque duplo.
- Os ajustes da câmera não abrem a galeria. A câmera mantém a interface fixa em retrato; somente a fotografia final recebe a orientação física do aparelho.

## Ferramentas com processamento real
- **Nitidez JPEG:** novo ajuste de detalhe do arquivo salvo, com intensidade limitada para minimizar halos. Não é apresentado como filtro ao vivo; por enquanto a nitidez só é aplicada na captura.
- **Alinhar (−15° a +15°):** régua graduada e referência de nivelamento sobre o visor, com rotação real da foto exportada. A operação mantém as dimensões/proporção e evita triângulos pretos por um recorte de preenchimento.
- Saturação, temperatura, exposição, matiz e contraste continuam com ajuste de prévia por GPU e processamento efetivo do JPEG capturado.
- Zoom por moldura (não amplia o preview), posicionamento da moldura e foco por toque mantidos.

## Qualidade e limitações
- CameraX continua capturando JPEG na maior resolução adequada disponibilizada. O pipeline decodifica apenas a região solicitada a partir do JPEG original antes dos ajustes, sem usar a tela de prévia como fotografia.
- O formato de exportação desta versão é **JPEG**. HEIC não está habilitado e não é apresentado como recurso disponível.
- **Foco manual nativo** permanece condicionado a implementar uma integração Camera2 segura e detectar suporte real do sensor.
- Uma régua de nitidez em tempo real exigiria um filtro espacial GPU de prévia; nesta versão o efeito atua apenas no JPEG final.
- A equivalência exata entre visor e sensor, o foco físico e a captura frontal precisam de testes adicionais em aparelhos reais. Testes instrumentados em emulador não substituem essa validação.

Versão `1.34.0` · `versionCode 42` · mesma assinatura permanente das versões oficiais anteriores.
