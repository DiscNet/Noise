# Noise! V1.22 — Bordas para abrir, fundo em gradiente e transições suaves

- Somente arrastos que **começam na borda física esquerda ou direita** podem abrir as rodas. A área de ativação é responsiva (48–72 dp ou 16% da largura); arrastar no centro da imagem não abre menus.
- Um swipe **da borda direita para a esquerda** abre **Ferramentas**, ancorada na direita.
- Um swipe **da borda esquerda para a direita** abre **Efeitos**, ancorado na esquerda.
- Gestos no sentido contrário à entrada, zoom com dois dedos e controles do editor não abrem os menus.
- O fundo escurecido deixa de ser uma película opaca uniforme. A interface usa um **gradiente de preto translúcido que desaparece em direção ao lado oposto**, suavizando o limite entre a roda e a imagem.
- Removida a caixa retangular com bordas escuras do painel orbital; os controles ficam sobre o gradiente e a imagem permanece parcialmente visível.
- O gradiente escurece de maneira progressiva durante o arrasto, por filtro temporal independente (240 ms), e continua a clarear/escurecer em fades de 460–540 ms.
- A roda começa parcialmente deslocada e translúcida durante o toque, com translação, escala e opacidade amortecidas; após soltar o dedo, ela termina o deslocamento por transição de 640 ms, evitando abertura brusca.
- Física orbital de scroll com mola criticamente amortecida mantida.
- Novos testes instrumentados rejeitam swipes centrais e para fora das bordas e verificam fade inicial parcial, animação após soltar e lateralidade do painel.

Prévia: `Noise.V1.22.apk`; validar a execução em emulador antes de release definitiva.
