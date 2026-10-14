# Noise! V1.21 — Correção direta no GitHub

- **Ferramentas**: painel realmente ancorado à **direita**, com a roda e seu ícone ativo à direita e rótulos à esquerda.
- **Efeitos**: painel realmente ancorado à **esquerda**, com a roda e seu ícone ativo à esquerda e rótulos à direita.
- Gestos permanecem independentes: direita → esquerda abre Ferramentas; esquerda → direita abre Efeitos.
- A abertura lateral agora combina deslocamento amortecido, fade e scale suave (98,5% → 100%) em até 550 ms. Um estado inicial invisível evita o flash do menu antes do primeiro quadro.
- Durante o arrasto, translação, opacidade e escala respondem progressivamente com interpolação amortecida por quadro. O escurecimento do editor mantém fade in/out independente.
- Scroll orbital mantém integração com mola criticamente amortecida e encaixe suave.
- Testes instrumentados verificam posições laterais **físicas** do painel e do ícone, direção correta da animação e fade independente.
- O shader de Brancos, editor GPU e exportação não foram alterados.

APK de prévia: `Noise.V1.21.apk`. Publicação definitiva depende dos testes completos em emulador.
