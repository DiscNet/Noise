# Noise! V1.46.0 — Máscaras, seleção por pixels, raster e conteúdo inteligente

## Alterações funcionais
- **Máscaras**: miniatura ao lado do conteúdo no painel de camadas, edição por toque na miniatura, máscara inicial branca, pincel preto/branco/cinza, borracha sobre máscara, inverter, aplicar ao raster, excluir, densidade, ativar/desativar e indicador da máscara ativa.
- **Raster**: pincel e borracha modificam pixels diretamente com undo/redo. Pincel e máscara interpretam corretamente as coordenadas de camadas movidas, giradas, redimensionadas e inclinadas, mantendo o conteúdo original da camada sem alterações paramétricas no histórico.
- **Seleções**: seleção por opacidade dos pixels (não pelos limites retangulares), inversão, preenchimento por cor, comando Limpar e seleção removida ao tocar sem arrastar. Seleções de pixels recebem contorno tracejado e são salvas no projeto.
- **Objetos inteligentes incorporados**: conversão de camada raster em documento interno independente, comando para abrir e editar seu conteúdo, preservação de estilos e filtros do objeto externo e rasterização explícita. As alterações internas aparecem na instância pai ao fechar o documento interno; arquivos internos persistem no projeto.
- **Bisel e relevo**: renderização adaptativa em 2x para textos e formas de tamanho moderado, interpolação para reduzir serrilhamento de bordas e rampas suavizadas no modo cinzel duro.
- **Seletor de cores**: campo maior, matiz com gradiente, controles numéricos opcionais RGB/HSV, prévia antes/depois, amostras recentes e área de toque mais ampla.

## Arquivos e compatibilidade
- Formato `.noise` v6 com leitura retrocompatível v1–v5.
- Smart objects internos são incorporados ao arquivo; restrição de 12 camadas internas e sem aninhamento de outros objetos inteligentes nesta versão.
- Seleção por pixels limitada a 4 MP. A prévia de estilo com supersampling adaptativo usa 2x em camadas até 350 mil pixels; objetos maiores recebem o renderizador de resolução normal.
- Máscara é armazenada como bitmap em tons de cinza. A opção de vínculo já tem estado salvo e indicador, mas transformação independente da máscara ainda requer refinamento.
- Objetos inteligentes ainda não implementam arquivos PSB/PSD, instâncias ligadas a arquivos externos ou hierarquia aninhada ilimitada; o fluxo é independente e nativo do Noise!.

## Testes
- Testes instrumentados: seleção alfa, inversão, preenchimento destrutivo e desfazer, máscara branca e aplicação de máscara, ida e volta de documento com smart object editável e renderização do conteúdo.
