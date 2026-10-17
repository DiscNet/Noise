# Noise! V1.47.0 — Máscara de corte, painel fx e bisel com técnicas diferenciadas

## Máscara de corte (Clipping Mask)
- O painel de camada agora oferece um botão **↓ Máscara de corte**, independente da máscara raster branca/preta.
- Quando ativada, a camada superior é visível apenas onde a primeira camada **não recortada** abaixo possui alfa. A camada inferior continua visível.
- Várias camadas marcadas como recorte utilizam a mesma camada-base até a primeira não marcada. O comportamento usa coordenadas de documento, portanto respeita deslocamento, rotação, escala e máscara da camada-base.
- O comando pode ser desfeito/refeito, e o estado é salvo no arquivo nativo `.noise` **v7**, com leitura retrocompatível das versões anteriores.
- A camada recortada recebe um indicador de seta para baixo e recuo no painel de camadas.
- Se não existir camada abaixo, o recorte não pode ser ativado.

## Painel inferior
- Removidos o cabeçalho redundante "ESTILOS • EFEITOS DA CAMADA", o botão "fx Estilos" duplicado e o botão "＋ Filtro".
- Apenas um acionador compacto **fx** adiciona estilos à camada; o menu superior Filtros continua permitindo adicionar/gerenciar filtros.
- As miniaturas dos filtros existentes continuam acessíveis para ajustar ou remover o filtro.
- Botão para alternar a máscara de corte adicionado ao painel ao lado de fx.

## Bisel e relevo
- Normais calculadas por gradiente Sobel de mapa de distância com correção subpixel pela cobertura alfa, reduzindo os degraus do mapa de distância inteiro.
- Os modos **Suave**, **Cinzel duro** e **Cinzel suave** agora usam perfis de relevo e realce separados: superfície curva, facetas definidas e facetas com transições atenuadas, respectivamente.
- Mesmo no Cinzel duro, as bordas não passam por um corte binário; conservam transição anti-aliased.
- Mantido supersampling adaptativo 2× em objetos menores, limitado pelo orçamento de memória do Android.

## Compatibilidade e validação
- Formato nativo `.noise` v7 (lê v1–v6).
- Testes instrumentados verificam recorte pela transparência real e não pelo bounding box, múltiplas camadas recortadas, desfazer, salvar/reabrir e diferenças de pixels entre as três técnicas de bisel.
- Efeitos de alta resolução continuam sujeitos ao limite de desempenho estabelecido nas versões anteriores. Antisserrilhamento reduzido, mas qualidade idêntica em qualquer fator de zoom não é garantida.
