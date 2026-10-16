# Noise! V1.34.1 — Câmera sem recorte artificial, orientação correta e régua suave

## Enquadramento em 1×
- A nova proporção padrão **TELA** utiliza **100% do visor visível** em 1×, sem os antigos recuos fixos de 9% na largura e 14% na altura.
- O limite da moldura coincide com o visor em 1×, sem uma borda branca residual. Em 2× ou mais, apenas a moldura diminui; o preview permanece imóvel.
- Se o usuário escolher **1:1** ou outro formato que não corresponda às dimensões do visor, o recorte continuará limitado à proporção solicitada. Isso é necessário para não distorcer a imagem.
- A captura continua vindo do JPEG de resolução plena oferecido pelo CameraX; não é uma captura de tela.

## Proporções automáticas, sem opções duplicadas
- Menu reduzido a: **TELA, 1:1, 4:3, 16:9, 3:2, NATIVO**.
- Os formatos são apresentados no padrão fotográfico (maior lado primeiro). Em retrato, 4:3 gera 3:4; em paisagem física, a fotografia rotacionada gera 4:3. O mesmo ocorre com 16:9 e 9:16.
- A interface de câmera permanece fixa em retrato, preservando a experiência anterior, e os indicadores de flash, temporizador e proporção acompanham suavemente a posição física do aparelho.

## Foto salva na orientação correta
- Corrigido o sinal da rotação aplicada depois da normalização de orientação feita pelo CameraX. Antes, a conversão do ângulo do sensor no sentido oposto podia produzir fotos deitadas de cabeça para baixo.
- O JPEG é orientado por pixels antes da exportação; seu EXIF de orientação é normalizado para impedir uma segunda rotação na galeria.
- Testes verificam os pontos de referência nos quatro cantos em fotos rotacionadas 90°/270°, além das dimensões e do enquadramento.

## Réguas de ajuste menos rígidas
- Nova sensibilidade adaptativa à extensão do ajuste e à largura real do celular. Um deslize alcança mais valores sem exigir vários movimentos longos.
- Arrasto mais responsivo, inércia calibrada, renderização simples por hardware, marcações legíveis e vibração menos frequente.
- Mantidos ajuste fino quantizado, limite dos controles, indicador central e retorno ao padrão por toque duplo.

## Validação e limitações
- Regressões automatizadas para enquadramento 1× sem margens, redução geométrica da moldura, proporções adaptativas, orientação por pixels, sensibilidade das réguas e JPEG de câmera virtual.
- A equivalência absoluta com todos os sensores e câmeras frontais ainda requer teste em um celular físico.
- Como antes, zoom por recorte conserva os pixels úteis, mas reduz naturalmente a resolução disponível em zoom extremo.

`versionCode 43` · `versionName 1.34.1` · assinatura permanente preservada.
