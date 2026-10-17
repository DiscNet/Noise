# .noise — formato nativo Advanced Studio v1

Contêiner ZIP com `manifest.json` e entradas raster `layers/<UUID>.png`. Somente IDs UUID válidos. `manifest.json`: version int 1, id, name, width, height, dpi, background ARGB, active layer id, zoom, pan, layers bottom-to-top. Cada elemento inclui `id,name,x,y,opacity,visible,locked,blend,image`. Todos os originais raster permanecem camadas independentes; o projeto não é screenshot.

Persistência em armazenamento privado `files/advanced_studio/<UUID>.noise` com `android.util.AtomicFile`. O leitor compara exatamente o caminho esperado, rejeita caminhos arbitrários (zip slip), valida UUIDs únicos, limites de número de camadas, imagens e tamanhos antes de decodificar. Limites Fase 1: arquivo até 96 MiB; manifesto até 2 MiB; 40 camadas; imagem até 16 Mpx; 36 megapixels decodificados acumulados. Versões não suportadas são rejeitadas com erro descritivo; preservar arquivos antigos do Noise!.

Ainda não inclui texto, vetores, filtros, máscaras, ICC, histórico serializado ou referências externas. Futuras versões devem evoluir `format` e implementar migrações versionadas sem redefinir propriedades.
