# Auditoria anterior à implementação — Noise! 1.13

Base: b9dfacfca566172525c9a44be406219b363f6f91, versionCode 14, versionName 1.13.

## Arquitetura e inventário

Java 17, Android nativo (min 26 / target 35), AGP 8.7.3, Gradle 8.9 no CI, GLES 2.0. Sem dependências de runtime de terceiros. MainActivity constrói o layout, mantém 50 parâmetros, importa, recorta e exporta. EditState faz a conversão para uniforms; EditorSurface mantém a GL thread; GlPipeline compila shaders e envia uniforms; GpuExporter usa EGL separado. GalleryScreen consulta MediaStore e carrega thumbnails; CropEditor desenha alças; AsciiAtlas gera caracteres reais. Glass já usa superfícies opacas apesar do nome legado. O CI contém testes instrumentados de pixels e interface em API 30, antes da publicação automática.

## Achados

- Espaço vertical fixo 0.82 / 1.18, sem painel ajustável, zoom ou modo de tela cheia. Textos e vários componentes têm alturas fixas.
- Cabeçalho sem histórico. Valores clicáveis restauram silenciosamente o ajuste em vez de permitir digitação. Alterações contínuas não têm transações.
- Recorte cria outro bitmap na thread principal; original e geometria são perdidos. Recriação da Activity reabre a URI sem reaplicar recorte.
- Importação reduz a imagem para no máximo 4096; a exportação usa esse mesmo bitmap. Não há cópia durável do arquivo original nem projeto editável.
- A descrição afirma correspondência prévia/exportação, mas uPatternScale muda espaçamento de dither, CRT, anéis, glow e glitch conforme o tamanho da superfície.
- ASCII, CRT e Glitch amostram a imagem original, ignorando ajustes anteriores de luz/cor em áreas substituídas. Nitidez compara cor ajustada com vizinhos não ajustados, alterando regiões uniformes.
- Apenas PNG; sem qualidade JPEG, compartilhamento ou seletor de resolução acessível.
- Não existem projetos, presets persistentes, desfazer/refazer, sombras, brilho independente ou temperatura.
- Álbuns já consultam todas as fotos; a opção denominada “Todas as fotos” mostra apenas as 500 recentes. Thumbnails podem enfileirar trabalho repetido; erros de permissão são confundidos com álbuns vazios.
- A atualização dos sliders já usa snapshots voláteis e RENDERMODE_WHEN_DIRTY, sem debounce. Reutilizar essa estratégia.
- Recursos GL são recriados após perda de contexto, mas faltam liberação explícita na destruição e medições úteis de frame.
- README tem descrições desatualizadas (nomes de categorias/intervalos) e comentários no código ainda mencionam vidro/desfoque removidos.

## Decisões

Manter Java, Android e GLES2. Separar definições, documento, histórico, armazenamento, arquivos, painel, controles e exportação. Manter o arquivo original e geometria normalizada. Separar passagem tonal das amostragens artísticas. Preservar Neon, ASCII e efeitos atuais; oferecer Bayer e Threshold reais. Não apresentar difusão de erro, máscaras ou IA sem implementação. Registrar limites GPU, de memória e validações em VALIDATION.md.

Referências: https://www.photopea.com/learn/workspace ; https://helpx.adobe.com/lightroom/mobile/get-started/gesture-controls-in-lightroom-for-mobile.html ; https://developer.android.com/training/data-storage/shared/media . Princípios aplicados: controles contextuais, painéis recolhíveis, comparação por pressão e MediaStore para arquivos públicos.
