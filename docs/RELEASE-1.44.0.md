# Noise! V1.44.0 — Painéis de propriedades e rotação centralizada

## Advanced Studio · controles integrados
- Campos de parâmetros no próprio painel, sem abrir o editor numérico modal do Android. Cada campo oferece **arraste horizontal pelo rótulo**, slider, botões **− / +** e edição numérica precisa.
- Painel **Transformação** dedicado: posição X/Y, largura/altura, proporção vinculada, rotação, inclinação X/Y e atalhos de espelhamento/rotação.
- Aba **Texto**: controles com slider e valor exato para tamanho, espaçamento, entrelinhas, posição e dimensões; cores via paleta rápida e canais RGB ajustáveis.
- Histórico do projeto gera um checkpoint por operação de arraste, em vez de registrar dezenas de passos intermediários.

## Correção geométrica
- Transformações de texto, raster, vetor e objetos inteligentes são aplicadas sobre o **centro visual geométrico** do objeto. O pivô central permanece estável durante a rotação.
- A moldura e as alças de transformação utilizam o mesmo modelo matemático que o compositor/exportador.
- Testes cobrem invariância do centro geométrico para texto, vetor e transformação combinada com inclinação.

## Painel inferior · camadas e efeitos
- **Modo de mesclagem** e **opacidade (%)** no topo do painel Camadas, com controle ajustável e campo exato.
- **Faixa de efeitos na base**: inserir filtros, editar a pilha, sombra projetada e densidade de máscara.
- Editor de sombra embutido com valores independentes para raio, deslocamento X, deslocamento Y e opacidade.
- Filtros inteligentes e máscara podem ter parâmetros alterados pelo controle deslizante, sem teclado obrigatório.
- A identidade visual continua com paleta escura e acentos lilás do Noise!.

## Compatibilidade e limites
- Formato .noise v4 preservado; documentos anteriores continuam compatíveis.
- Máscaras, filtros e sombras mantêm o limite técnico de 2 megapixels por elemento nesta etapa.
- Não inclui, ainda, distorção em malha, perspectiva por quatro vértices, nem equivalência integral aos objetos inteligentes do Photopea.
