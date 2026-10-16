# Noise! V1.34.1 — Visor realmente completo, orientação e régua suave

## Correções da câmera

- **Sem margem permanente em 1×:** o formato inicial **TELA** corresponde exatamente à área da prévia. A moldura não recebe mais os limites artificiais de 91% da largura e 86% da altura; em 1× não há escurecimento externo nem borda sobre a fotografia.
- **Zoom exclusivo por recorte:** só a moldura diminui (2× reduz suas dimensões à metade); a câmera e o visor permanecem fixos. A foto é obtida de um JPEG real em alta resolução e recortada conforme a moldura.
- **Formatos sem duplicações:** **TELA**, **1:1**, **4:3**, **16:9** e **3:2**. Não há 3:4 ou 9:16 separados: ao fotografar com o celular deitado, a fotografia passa para a orientação paisagem sem girar a interface da câmera.
- **Correção das fotos horizontais:** o giro adicional do bitmap agora respeita o sentido do sensor de orientação Android; foram adicionados testes com pixels assimétricos para os dois lados da rotação de 90°/270°. A UI permanece em retrato.
- **Régua e rodas mais leves:** aproximadamente metade do deslocamento por passo, menor limiar de arraste, inércia mais suave, menos vibração e desenho acelerado por hardware.
- Preservados foco por toque, flash, temporizador, controles ao vivo de exposição/cor, alinhamento, nitidez no JPEG, MediaStore e assinatura permanente.

## Observações de qualidade

O formato **TELA** mostra toda a área já visível no `PreviewView`. Quando a proporção do sensor difere da proporção da tela, o `PreviewView.ScaleType.FILL_CENTER` precisa cortar parte do campo de visão original para preencher a tela sem barras. Essa limitação geométrica não deve ser confundida com a moldura do zoom. Para fotografar em 1:1 ou 4:3, a moldura precisa respeitar a proporção solicitada e, portanto, pode não cobrir todo o visor.

A rotação com sensor físico precisa ser conferida nos dois sentidos em aparelhos reais. Os testes de bitmap no emulador validam a orientação lógica, mas não todas as variantes de câmera Android.

`versionName=1.34.1` · `versionCode=43` · `applicationId=com.discnet.noise`.
