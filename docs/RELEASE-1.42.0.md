# Noise! V1.42.0 — Advanced Studio Fase 3A

Entrega incremental de recursos profissionais **reais**, preservando o editor clássico e os arquivos .noise existentes.

## Funcionalidades entregues
- Máscaras de camada raster, armazenadas independentemente da imagem de origem: criar toda branca ou toda preta; pintar preto para ocultar, branco para revelar; inverter; alternar ativação; densidade de 0 a 100; excluir; desfazer/refazer.
- Filtros não destrutivos em pilha editável por camada: Desfoque gaussiano (convolução separável, raio 1–8), Brilho (-100..100) e Saturação (-100..100). É possível acrescentar, reordenar, ativar/desativar, alterar parâmetro e remover. Compositor da exportação é o mesmo da prévia.
- Estilo de camada Sombra projetada com raio 1–16, deslocamento, cor padrão preta e opacidade configurável. Persistente e independente da origem.
- Cache LRU limitado a 16 MB para os resultados dos filtros e invalidação durante pintura raster ou da máscara.
- Arquivo nativo .noise v3: manifesto mantém filtros, máscaras, sombras, parâmetros de edição; máscara é um PNG próprio em masks/ID.png. Leitor suporta formatos v1 e v2.

## Limitações explicitadas
- Filtros, máscaras e estilos aceitos apenas em camadas de até 2 megapixels por operação, como proteção temporária para memória e CPU de aparelhos intermediários. Imagens maiores continuam abrindo e editando normalmente no fluxo anterior, mas os novos efeitos não são expostos.
- Gaussian é processado na CPU, inicialmente em raios pequenos e com prévia calculada na alteração confirmada. Não há ainda um pipeline de tiles para filtros espaciais grandes.
- Edição de máscara via pincel binário de pontas redondas. Sem feather da máscara, máscaras vetoriais, clipping mask ou modo de mostrar só a máscara nesta versão.
- Sombra: controles básicos de raio, deslocamento e opacidade (sem Spread, contour, luz global ou estilos adicionais).
- Pen/Bézier, Liquify, Healing Brush, Curves completas, LUTs, ajuste não destrutivo como layer autônoma, tipografia rica e smart objects continuam pendentes da Fase 3B e 4.
- O preview ao alterar parâmetros do filtro não é contínuo durante o arraste do slider nesta versão: ele é aplicado quando o usuário confirma.

## Verificação
Teste instrumental Android: alpha da máscara ao pintar preto/branco, Undo/Redo independente de pixels originais, inversão, toggle, efeitos encadeados, consistência de exportação e round-trip de ZIP v3. Regressões dos modos anteriores e assinatura verificadas via workflow.
Versão: 1.42.0, versionCode 60, applicationId com.discnet.noise, assinatura permanente inalterada.
