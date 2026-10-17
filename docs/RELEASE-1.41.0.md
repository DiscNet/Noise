# Noise! V1.41.0 - Advanced Studio Phase 2

Implementação inicial de edição fundamental, mantendo o editor clássico independente.

Recursos: pincel raster e borracha transparente com histórico por traço; seleção retangular; recorte confirmável; balde contíguo, gradiente linear; brilho, contraste e saturação; texto editável; formas retângulo e elipse editáveis; transformações não destrutivas (mover, escalar, rotacionar); modos de mesclagem Normal, Multiply, Screen, Overlay, Darken e Lighten nas APIs compatíveis.

Formato de projeto .noise v2 preserva os elementos vetoriais/textuais, seleções e transformações; arquivos v1 permanecem legíveis.

Limitações: o balde está limitado a 4MP por operação; operações de pincel são CPU; sem Photoshop PSD estruturado, máscara avançada, Bezier, Liquify, Healing, estilização vetorial completa, gradientes radiais ou perspectiva. Pintar sobre camada raster transformada exige rasterização ainda pendente. Exportação pública requer Android 10+ nesta etapa.

Compatibilidade: com.discnet.noise, versionName 1.41.0, versionCode 59; assinatura oficial permanente preservada. Nenhum recurso do editor clássico foi substituído.

Os testes instrumentados devem ser aprovados antes do release.