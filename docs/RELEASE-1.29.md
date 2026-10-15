# Noise! V1.29 — fundo animado, estética orbital futurista, HSL profissional e redução de ruído

Base: branch `feature/noise-v1-28-refinement`, V1.28.2. A V1.27 permanece como release estável até validação.

## Home
- Corrigido o ciclo de vida das animações: `StockAtmosphere` e `Sphere` usam `ValueAnimator` que inicia ao surgir, pausa ao ocultar e retoma ao voltar à Home. Respeita a preferência do Android por menos movimento.
- Luzes abstratas radiais ganham deslocamento notável e independente em duas dimensões, sem imagens baixadas da internet.
- Círculo central redesenhado com aura, múltiplos anéis concêntricos, arcos luminosos rotativos e pontos orbitais animados; gradientes armazenados em cache após mudança de tamanho.

## Cor
- Três rodas de graduação cromática: **Sombras, Meios-tons e Realces**. Posição X/Y seleciona o viés cromático e intensidade, com manipulação direta, restauração neutra e histórico por gesto.
- Três curvas de seis pontos: **Hue × Hue**, **Saturação × Hue** e **Saturação × Saturação**, incluindo pontos editáveis, reset individual e interpolação suave. O processamento opera sobre a cor de cada pixel, não sobre a imagem como um todo.
- Modos de mesclagem da graduação: Normal, Multiplicar, Tela, Sobrepor e Luz suave, com opacidade independente.
- Séries de controles integradas ao documento, `EditState`, shader GLSL e serialização JSON compatível com projetos anteriores. Neutralidade preservada por padrão.

## Luz e detalhes
- **HDR** tonal ajustável (abertura moderada das sombras, compressão de realces) e **Detalhes HDR** locais (microcontraste de quatro amostras no passe final).
- **Redução de ruído** de fato: filtro bilateral espacial 3×3, com intensidade e preservação de bordas independentes. Intensidade zero não ativa as amostragens adicionais.
- Mantido o controle de Ruído existente para geração de grão (positivo) e compatibilidade com redução legada (negativo).

## Verificação
- Novos testes instrumentados para curvas, rodas, valores JSON, mesclagem, HDR, diferença de pixels em imagem ruidosa, bypass do original e movimento efetivo do fundo na Home.
- Padrão de validação: build Android, execução de testes no emulador, revisão de screenshots e confirmação física quando disponível. Não declarar aprovação antes dessas etapas.

Arte visual implementada inteiramente com Views/Canvas do Noise!, sem interfaces nativas externas. Prévia: `Noise.V1.29.apk`.
