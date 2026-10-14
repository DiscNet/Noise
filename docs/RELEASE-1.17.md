# Noise! V1.17 — Navegação orbital por arrasto

## Interface e interação
- Removidos os botões de abertura da roda de ferramentas e do painel de efeitos; ambos são alcançados exclusivamente com swipes horizontais.
- Um arrasto para a esquerda a partir do editor revela a roda de ferramentas. A própria roda acompanha o dedo, com translúcido dinâmico; soltá-la confirma ou cancela conforme a distância e velocidade.
- Na roda de ferramentas, arrastar para a esquerda abre a de efeitos. Na de efeitos, arrastar para a direita retorna a ferramentas. Na de ferramentas, arrastar para a direita fecha a navegação.
- A zona sensível cobre toda a largura do preview, o cabeçalho, a faixa de orientação inferior e as bordas laterais do editor; a área de sliders mantém seus próprios gestos.
- Transições coordenadas de deslocamento e opacidade com curvas de desaceleração, sem saltos de layout; mantém seleção orbital vertical e snapping.
- Dicas de navegação são texto, sem botões clicáveis.
- Mantida correção dos Brancos (V1.16), processamento GLSL, histórico e exportação.

## Validação
Build e testes de instrumentação Android via GitHub Actions. A suíte testa a abertura *durante* o arrasto, transparência intermediária e navegação horizontal em ambos os sentidos.

Arquivo previsto: `Noise.V1.17.apk` (prévia debug); a distribuição final depende do resultado da instrumentação.
