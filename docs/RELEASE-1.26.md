# Noise! V1.26 — As duas navegações continuam em RODA circular

## Roda restaurada em Ferramentas e Efeitos
- **Ferramentas (direita)** e **Efeitos (esquerda)** possuem trajetórias realmente circulares, espelhadas em relação ao centro da tela.
- Removido o `clamp` das coordenadas horizontais dos ícones que achatava o arco em uma lista vertical, prejudicando a identidade orbital.
- Corrigido o sinal do deslocamento circular: itens vizinhos agora se curvam **para dentro da área da foto** a partir de cada borda, e não são posicionados fora da tela.
- O contentor orbital pode transbordar a estreita região de ancoragem, mas os itens e a linha circular **desaparecem gradualmente nas extremidades**, evitando bordas quadradas e cortes abruptos.
- Mantidos os giros/rolagem vertical com mola criticamente amortecida, snapping suave, gesto restrito às bordas e transições de abertura/fechamento independentes do dedo.
- Mantidos o escurecimento com fade in/out por toda a tela e o gradiente lateral.

## Regressão de Glow observada na validação anterior
- Corrigido o teto da luminosidade que saturava o Glow já em 100%, impedindo diferenças visuais em 200%. A composição agora usa compressão exponencial, preservando o aumento progressivo.

## Testes
- Asserções verificam que o ícone situado a duas posições do selecionado está deslocado para o centro, com **diferença horizontal real** (não apenas orientação ou posição lateral do painel) nas duas rodas.
- Mantidos os testes dos shaders, incluindo Glow 100% e 200%, Brancos, e da navegação exclusivamente por bordas.

APK de prévia `Noise.V1.26.apk`; aguardar validação em emulador e no aparelho para publicação definitiva.
