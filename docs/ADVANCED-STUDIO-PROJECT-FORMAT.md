# .noise — formato nativo Advanced Studio v1

Contêiner ZIP com `manifest.json` e entradas raster `layers/<UUID>.png`. Somente IDs UUID válidos. `manifest.json`: version int 1, id, name, width, height, dpi, background ARGB, active layer id, zoom, pan, layers bottom-to-top. Cada elemento inclui `id,name,x,y,opacity,visible,locked,blend,image`. Todos os originais raster permanecem camadas independentes; o projeto não é screenshot.

Persistência em armazenamento privado `files/advanced_studio/<UUID>.noise` com `android.util.AtomicFile`. O leitor compara exatamente o caminho esperado, rejeita caminhos arbitrários (zip slip), valida UUIDs únicos, limites de número de camadas, imagens e tamanhos antes de decodificar. Limites Fase 1: arquivo até 96 MiB; manifesto até 2 MiB; 40 camadas; imagem até 16 Mpx; 36 megapixels decodificados acumulados. Versões não suportadas são rejeitadas com erro descritivo; preservar arquivos antigos do Noise!.

Ainda não inclui texto, vetores, filtros, máscaras, ICC, histórico serializado ou referências externas. Futuras versões devem evoluir `format` e implementar migrações versionadas sem redefinir propriedades.

## Formato v2 — compatibilidade retroativa
`format:2` acrescenta layer `type` RASTER/TEXT/RECTANGLE/ELLIPSE, campos paramétricos text/fontSize/color/objectWidth/objectHeight, scaleX/scaleY/rotation, seleção retangular opcional. Somente camadas raster possuem `image` e entrada PNG; textos e shapes são reconstruídos dos parâmetros do manifesto. `fromJson` aceita `format:1` para preservar os projetos V1.40.0, fornecendo defaults para tipo e transformações. O leitor continua verificando IDs/caminhos e limite de recursos.

## Formato v3
Compatibilidade de leitura v1/v2/v3. Camadas podem possuir `mask` = `masks/<UUID>.png`, `maskEnabled`, `maskDensity`, `shadowEnabled`, `shadowRadius`, `shadowX`, `shadowY`, `shadowColor`, `shadowOpacity`, `effects` = array de `{kind,value,enabled}` em ordem de aplicação. Máscaras grayscale têm dimensões idênticas ao conteúdo natural da camada, sem valores fora do orçamento 2 MP; editor de máscara não altera bitmap original. Leitor impede traversal e valida UUID, caminho de recurso, dimensão da máscara, tamanho/numero de efeitos, parâmetros e orçamento total decodificado. Escrita atômica persiste PNGs separados de raster e máscaras.
