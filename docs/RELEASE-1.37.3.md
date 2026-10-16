# Noise! V1.37.3 — física de tinta procedural no menu rotativo

Esta atualização aplica à transferência líquida entre os ícones da roda orbital o **mesmo princípio de campo escalar, deformação multiescala e erosão geométrica** usado no fundo da Home. **A Home não foi alterada**: o shader `fluid.frag`, o renderizador, os controles e os assets da tela inicial permanecem idênticos à V1.37.2.

## O menu ganha

- Uma membrana gerada a partir das densidades de duas massas e de um pescoço transitório. O próprio campo cruza o limiar de tinta para unir e separar as regiões: o efeito não é mais uma cápsula Bézier com transparência animada.
- Advecção orgânica de duas escalas no espaço da massa. As ondulações seguem a posição fracionária da roda e o tempo, e não são reposicionadas aleatoriamente a cada frame.
- Lascas, reentrâncias e irregularidades no **limite real** das massas, incorporadas ao limiar do campo *antes* da extração do contorno. O material tem corpo e bordas desgastadas, não uma camada de grain.
- Campo convertido em geometria vetorial por amostragem local e interpolação dos cruzamentos de isovalor (marching squares). O preenchimento e o traço derivam do **mesmo campo**; as superfícies permanecem anti-aliased e limitadas à região entre ícones.
- Contorno do foco refeito como superelipse orgânica com desgaste derivado da mesma função de ruído. Sua deformação segue a força fracionária de seleção.
- Pigmentos grafite/violeta e uma borda clara discreta para manter contraste sobre o editor escurecido; nada é desenhado por cima da fotografia editada fora da roda.
- Permanecem rotação circular, molas contínuas, crossfade de ícones, toque, favoritos, navegação, menu bilateral e fechamento suave.

## Custo e compatibilidade

O fundo da Home continua renderizado **inteiramente pela GPU**. O menu usa o **mesmo modelo matemático**, porém constrói um pequeno caminho vetorial local na UI Android Canvas para manter transparência e compatibilidade desde Android 8 (GLES2 no editor). Ele não é uma segunda simulação GLES2 nem um wallpaper ou vídeo. A grade e os buffers são reaproveitados; não há bitmap completo, blur em tela inteira ou thread extra. A medição de FPS e gasto de CPU em aparelhos reais continua necessária.

## Validação

- O workflow gera APK debug e instrumentado e exercita as transições em emulador Android.
- Testes checam que o campo realmente produz separação → fusão → separação, mantém deformação temporal determinística e preserva animação de seleção/fade e gestos.
- Capturas e filmagem do teste podem ser inspecionadas nos artefatos de CI. Emulador SwiftShader não equivale a dispositivo real.
- APK release usa a mesma identidade `com.discnet.noise`, assinatura permanente e versão incrementada para atualizar sem desinstalar.

Versão `1.37.3` · `versionCode 52`.
