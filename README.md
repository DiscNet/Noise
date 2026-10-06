# Fox Clicker

Jogo incremental em pixel art desenvolvido para navegador, inspirado na proposta de jogos de clique simples como **Click the Button**.

A mecânica principal é clicar na raposa para gerar moedas, aumentar o poder do clique, evoluir o nível e desbloquear recompensas.

## Recursos

- Interface em pixel art.
- Raposa interativa como elemento principal.
- Sistema de moedas.
- Poder por clique.
- Sistema de níveis.
- Upgrades com custo progressivo.
- Sistema de combo.
- Baús e recompensas por quantidade de cliques.
- Partículas e moedas animadas.
- Ambiente aberto em tela cheia.
- Layout responsivo para diferentes tamanhos de tela.
- Salvamento automático usando `localStorage`.
- Reset completo do progresso.
- Sem dependências externas ou frameworks.

## Estrutura

```text
Noise/
├── index.html
├── style.css
├── game.js
└── README.md
```

### index.html

Define a estrutura principal do jogo, HUD, controles, indicadores de progresso e carregamento dos arquivos do projeto.

### style.css

Controla o layout da interface, responsividade, HUD, botões, indicadores e aparência geral.

### game.js

Contém a lógica do jogo, incluindo:

- Renderização do mundo.
- Renderização da raposa.
- Sistema de cliques.
- Moedas e partículas.
- Combos.
- Upgrades.
- Progressão de nível.
- Baús.
- Salvamento do progresso.
- Animações.

## Como executar

Não é necessário instalar dependências.

Basta abrir o arquivo:

```text
index.html
```

Também pode ser hospedado diretamente em serviços de hospedagem estática compatíveis com HTML, CSS e JavaScript.

## Tecnologias

- HTML5
- CSS3
- JavaScript
- HTML5 Canvas
- LocalStorage

## Gameplay

O ciclo principal é:

```text
Clicar na raposa
       ↓
Receber moedas
       ↓
Aumentar o poder
       ↓
Comprar upgrades
       ↓
Subir de nível
       ↓
Completar a barra de cliques
       ↓
Abrir o baú
       ↓
Receber recompensa
       ↓
Continuar evoluindo
```

O custo dos upgrades aumenta progressivamente, fazendo com que o jogador precise continuar acumulando moedas para avançar.

## Armazenamento

O progresso é salvo localmente no navegador através de `localStorage`.

A chave utilizada pelo jogo é:

```text
fox-clicker-v2
```

O progresso inclui moedas, nível, poder, custo do próximo upgrade e quantidade de cliques.

## Desenvolvimento

O projeto foi desenvolvido sem frameworks para manter a execução simples, leve e fácil de modificar.

A renderização utiliza uma resolução lógica de **960×540**, mantendo o aspecto 16:9 e o estilo pixelado através do Canvas.

## Licença

Este projeto não possui uma licença de código aberta definida no momento.

Os elementos e código presentes neste repositório devem ser considerados parte deste projeto até que uma licença seja adicionada.
