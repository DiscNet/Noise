# Noise! V1.37.0 — tinta procedural viva

O fundo da Home deixa de ser um bitmap deslocado por um animador. Agora um fragment shader GLES2 avalia um campo escalar de massas elípticas deformadas por domain warping. A soma das densidades cria e rompe conexões geométricas; a mistura de pigmentos usa essas mesmas contribuições.

## Implementação

- `OrganicBackdrop`: integração passiva com a Home, preferências, acessibilidade, bateria e estado térmico.
- `FluidBackgroundRenderer`: um desenho GLES2 por quadro em `TextureView`, EGL em thread própria e buffers reaproveitados. Nenhuma dependência nova.
- `FluidField` / `OrganicMotionController`: seis massas principais e até quatro fragmentos, ruído de interpolação quíntica, movimento em várias escalas e relógio acumulado sem loop curto.
- `FluidInteractionController`: observa os eventos antes dos controles, aplica perturbação localizada com intensidade limitada e amortecimento. Não intercepta gestos.
- `FluidQualityController`: orçamento de pixels e histerese para evitar oscilações de qualidade. A resolução pode mudar imediatamente; os detalhes geométricos entram e saem gradualmente.
- `FluidBackgroundLifecycle`: Choreographer, no máximo um quadro pendente e pausa quando a Home fica oculta/coberta ou a Activity pausa.

O círculo continua sendo desenhado com `drawCircle`. Os painéis e textos permanecem em views acima do fundo. Os menus orbitais conservam sua animação independente e a paleta violeta compartilhada visualmente com a tinta.

## Qualidade

| Perfil | Orçamento interno máximo | Massas | Deformação | Alvo de cadência |
| --- | ---: | ---: | --- | ---: |
| Alto | 520.000 pixels | 10 | Macro, meso e duas escalas de detalhe | 60 FPS |
| Equilibrado | 270.000 pixels | 8 | Macro, meso e detalhe | 60 FPS |
| Econômico | 120.000 pixels | 6 | Macro e meso, fusão preservada | 30 FPS |

As dimensões respeitam a proporção da tela e nunca ultrapassam a resolução da view. GPUs limitadas ao mínimo de uniforms GLES2 usam seis massas, mantendo fusão/separação. A economia de bateria ou temperatura a partir de MODERATE limita ao perfil econômico. Carga sustentada reduz a qualidade; recuperação exige tempo estável. Os valores são metas, não garantia de FPS em qualquer aparelho.

O modo “Reduzir movimento” e a escala de animação desligada no Android congelam a composição. Um gradiente de reserva só aparece enquanto a superfície inicia ou em falha real de EGL/shader, registrada em `NoiseFluid`. Não há substituição silenciosa por vídeo/GIF.

## Validação reproduzível

```sh
gradle --no-daemon assembleDebug assembleDebugAndroidTest
python -m pip install numpy scipy Pillow moderngl
python scripts/validate-fluid.py --video
```

O script usa o código Java de produção para gerar posições, raios, orientações e pesos. Apenas a sintaxe GLSL é adaptada para o contexto desktop. Em cada perfil, um par real de massas é isolado e centralizado para contar componentes conectados na imagem renderizada, sem interferência dos limites da tela.

Resultados locais em EGL/llvmpipe, 300 × 534, 60 segundos de evolução amostrados a 4 Hz:

| Perfil | Componentes observados | Mudanças entre fundido/separado | Mediana / p95, desenho + leitura |
| --- | --- | ---: | --- |
| Econômico | 1 e 2 | 4 | 1,22 / 3,59 ms |
| Equilibrado | 1 e 2 | 4 | 1,29 / 2,73 ms |
| Alto | 1 e 2 | 4 | 1,39 / 2,21 ms |

Também verificados: resultado determinístico no mesmo instante; pequena diferença entre quadros consecutivos; perturbação por toque; telas quadrada, horizontal e vertical longa; relógio sem salto após pausa; redução de qualidade sob carga. Sequência visual de 48 segundos produzida para inspeção. Essas medidas são de renderização em software no host e **não representam FPS de um celular**.

`FluidBackgroundTest` executa o shader GLES2 real na TextureView Android, captura duas composições, verifica pausa/cobertura/retorno, redução de movimento e perfil econômico. Mantém os testes existentes de círculo, controles, câmera, menus, edição e exportação. O workflow guarda as capturas, `noise-fluid-metrics.txt` e uma gravação da Home de 40 segundos em `Noise-smoke-tests`.

Para desempenho de aparelho físico: medir em modo release após aquecimento, em 720p e 1080p, observar frame pacing e temperatura durante pelo menos cinco minutos, e repetir com economia de bateria. A medição instrumentada inclui submissão CPU e backpressure EGL; não é uma consulta de tempo puro da GPU. Essa validação física não foi executada no ambiente de desenvolvimento.

A assinatura permanente e o fluxo de publicação do APK oficial permanecem os existentes.
