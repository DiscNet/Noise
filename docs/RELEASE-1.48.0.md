# Noise! V1.48.0 — Hotfix de Bisel e Relevo para tipografia

Esta versão corrige o serrilhado e os artefatos de luz/sombra relatados ao aplicar **Bisel e relevo** à palavra "Luka" e a outros textos com curvas e diagonais.

## Correção do renderizador
- Substituída **integralmente** a antiga implementação do bisel com distância aproximada por um pipeline dedicado, baseado em *Euclidean Signed Distance Field* (EDT exata em tempo linear por dimensão).
- A tipografia e as formas paramétricas são rasterizadas novamente em resolução adaptativa (até 4× em elementos pequenos), **não apenas ampliadas** a partir de pixels serrilhados.
- O gradiente do mapa de distância utiliza convolução suavizada e normais contínuas, preservando transições limpas nos cantos das letras.
- **Suave, Cinzel duro e Cinzel suave** utilizam perfis separados, mantendo a borda externa com antialiasing independentemente do perfil geométrico.
- A saída reduzida emprega média de área com alfa pré-multiplicado, para evitar o serrilhado causado pela redução de resolução com amostragem insuficiente.
- Bisel interno não pode pintar pixels completamente externos à forma do texto; isso elimina o rastro branco/escuro produzido pelo efeito fora da silhueta.
- A geometria do texto reserva espaço real para itálicos, ascendentes e descendentes, evitando cortar bordas já no bitmap de entrada.
- Preservadas as configurações existentes de estilo e o formato de arquivo nativo **.noise v7**, sem migração de documentos.

## Desempenho e limitações
- Orçamento máximo de cerca de 1,1 milhão de pixels durante a computação do mapa do bisel, com resolução dinâmica para documentos grandes, reduz risco de falta de memória em celulares.
- Os algoritmos de outros estilos de camada continuam inalterados.
- A aparência exata depende de fonte, tamanho, dispositivo, escala e parâmetros. A remoção absoluta de artefatos em todas as combinações não deve ser presumida sem testes no aparelho.

## Testes
- Teste instrumentado específico usando **Luka** com fonte grande, contornos antialiased e bisel nos três modos, garantindo ausência de pixels fantasmas fora do alfa da fonte em **Bisel interno**.
- Testes anteriores para comparação entre modos, persistência .noise e tela principal continuam ativos.
