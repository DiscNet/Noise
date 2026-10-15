# Noise! V1.23 — Animação orbital independente do dedo e fade in/out

- Abertura dos menus continua restrita às bordas do editor. **Borda direita → esquerda:** Ferramentas, na direita. **Borda esquerda → direita:** Efeitos, na esquerda.
- Os gestos não controlam mais translação, escala ou opacidade do painel. Um arrasto reconhecido **apenas dispara a animação**, que continua suavemente mesmo que o dedo acelere, pare ou seja solto.
- Rodas utilizam a mesma dinâmica de **mola criticamente amortecida** usada no scroll orbital, com velocidade contínua e trajetória curta a partir da própria borda. Não ocorre salto visual nas mudanças de evento de toque.
- O escurecimento é um **gradiente translúcido independente**, com fade in/out baseado em tempo de quadro e resposta temporal suavizada (~200ms), sem acompanhar o dedo e sem contornos pretos abruptos.
- Conteúdo dos menus ganha **fade in/out** (~160ms de constante temporal) e leve transição de escala, tanto na abertura quanto no fechamento.
- Ao fechar ou cancelar, tanto o painel quanto o gradiente retornam progressivamente ao estado invisível sem troca brusca.
- Testes Android cobrem rejeição de swipes centrais/invertidos, independência do posicionamento do dedo, opacidades intermediárias durante abertura e fechamento, e lados corretos dos painéis.
- Renderização por GPU, efeitos, seleção, exportação e física de scroll anteriores preservados.

APK de teste `Noise.V1.23.apk`; não distribuir como release definitiva sem executar os testes no emulador.
