# Noise! V1.39.1 — Galeria editorial, presets responsivos e tinta tátil

Ajustes pontuais sobre 1.39.0, mantendo a edição GPU, o seletor próprio da Home, a assinatura estável e os projetos existentes.

- **Visualizador:** a foto anterior agora se desloca integralmente para fora enquanto a próxima entra pela lateral oposta, com easing por 280 ms e fotografia anterior preservada durante o movimento. Diferente do fade superficial anterior.
- **Grade da Galeria:** fotos por data real de captura (MediaStore.DATE_TAKEN, com fallback em DATE_ADDED). Cabeçalhos Hoje/Ontem/datas; miniaturas selecionadas ganham largura de duas colunas e altura maior, distribuídas em ritmo editorial; no seletor da Home as células permanecem uniformes. Corrigido avanço do cursor da paginação após cada lote de 96 fotos, evitando recargas repetidas da primeira página.
- **Presets:** cabeçalho e ação Aplicar fixos; prévia, instruções, intensidade e lista passaram à mesma ScrollView com altura flexível. Ajustadas as margens de status/navigation bar, cartões compactos e dimensões do modal à altura útil real da tela, inclusive em aparelhos pequenos.
- **Fundo líquido da Home:** ao tocar/arrastar, o campo de densidade é realmente erodido no ponto do dedo (cavidade irregular e grânulos/desprendimento nas margens); o campo se recompõe após soltar, sem o efeito anterior de simples deformação superficial. Fundo fora da região do toque mantém a física procedural.
- **Ícone oficial:** adotada a imagem enviada pelo usuário, preservando seu símbolo central branco e arte granulada preta em fundo branco. Recurso WebP compactado para 192×192 inserido em `drawable-nodpi`, referenciado nos ícones adaptativos Android e nos drawables tradicionais. A imagem original 1536×1536 não é guardada no APK; a representação otimizada mantém o desenho no tamanho de launcher.
- **Versão:** 1.39.1, versionCode 57, package e chave de assinatura inalterados.

## Validação
Os testes Android verificam o recurso real do ícone, deslocamento efetivo da imagem, células da Galeria, intensidade de dissolução ao toque e rolagem estrutural dos Presets. O emulador é uma validação funcional; conferir visual e performance final em telefone físico.
