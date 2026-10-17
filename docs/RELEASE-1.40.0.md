# Noise! V1.40.0 — Advanced Studio, Fase 1

Primeira entrega funcional de um segundo ambiente de edição dentro do Noise!. O Advanced Studio é uma implementação Android independente e **não é uma cópia do Photopea nem sua equivalência integral**.

## Implementado nesta versão
- Entrada pela Home (ADVANCED STUDIO) e, a partir do editor fotográfico, opção de enviar a **foto original** ao novo ambiente. O projeto de origem permanece intacto.
- Interface dedicada com menu superior Arquivo/Editar/Camadas/Visualizar, barra contextual, duas ferramentas reais (Mover e Mão), canvas central, painel de camadas e status de zoom. Layout Android retrato e tablets com painel lateral.
- Documento raster próprio com nome, UUID, largura, altura, DPI, fundo branco ou transparente e camadas reais separadas por ID.
- Camadas raster: criar, importar pelo seletor do Android, duplicar, excluir, selecionar, renomear, ocultar, bloquear, ajustar opacidade e reordenar. Cada camada mantém bitmap, deslocamento X/Y e metadados independentes.
- Compositor nativo único para prévia e exportação com os modos Normal, Multiply e Screen. Checkerboard de fundo é apenas indicação de transparência, não entra na exportação.
- Um dedo move a camada selecionada ou navega na ferramenta Mão; gesto com dois dedos para pan e zoom. Desfazer e refazer de metadados com histórico limitado.
- Persistência de projetos nativos `.noise` em armazenamento privado do app, com arquivo ZIP versionado, manifesto JSON, cada raster separado em PNG, gravação atômica, autosave com debounce e restauração de projetos.
- Exportação PNG pública em `Pictures/Noise!` pelo MediaStore em Android 10+.
- Teste instrumental Android para composição de pixels, ordem, visibilidade, undo/redo, duplicação/bloqueio e salvamento/reabertura do projeto com camadas intactas.

## Limitações declaradas
- Esta é **somente a Fase 1 raster**. Máscaras, textos editáveis, vetores, seleções, estilos, filtros, Liquify, brushes, Smart Objects e PSD estruturado permanecem pendentes.
- A exportação pública da nova página é PNG no Android 10+; Android 8/9 requer um fluxo legado de gravação que ainda não foi implementado nesta fase.
- Documentos limitados a 16 megapixels e até 36 megapixels acumulados de camadas; histórico não é persistido após reabrir o arquivo; um documento visível de cada vez.
- A auditoria do Photopea partiu de páginas/documentação oficiais, sem sessão interativa de validação de todos os comandos ou parâmetros.

## Documentação
- `docs/PHOTOPEA-FEATURE-AUDIT.md`
- `docs/ADVANCED-STUDIO-FEATURE-MATRIX.md`
- `docs/ADVANCED-STUDIO-ARCHITECTURE.md`
- `docs/ADVANCED-STUDIO-RENDERING.md`
- `docs/ADVANCED-STUDIO-PROJECT-FORMAT.md`
- `docs/ADVANCED-STUDIO-TESTS.md`

**Aplicativo**: `com.discnet.noise`, `versionName 1.40.0`, `versionCode 58`. A assinatura permanente permanece idêntica à das releases oficiais anteriores.
