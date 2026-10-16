# Noise! V1.39.0 — Galeria e visualizador imersivo

## Galeria e seletor são fluxos separados

- **Galeria**: fotos de todo o MediaStore acessível à permissão total ou parcial, não somente arquivos Noise!. Ordenação por `DATE_ADDED DESC, _ID DESC`, sem repetir o mesmo ID. Seções “Hoje”, “Ontem” e datas completas. Grade reciclável com quatro colunas quando a largura permite e redução automática para três/duas em aparelhos menores. Fundo preto profundo, miniaturas recortadas apenas no grid, cantos discretos.
- **Seletor do círculo da Home**: instância exclusiva de galeria em modo de escolha, sem visualizador. Toque numa miniatura carrega a fotografia diretamente no editor. As rotas não se confundem.
- Abas Início, Galeria e Projetos com navegação horizontal, barra flutuante escura/translúcida, sem permitir que o gesto de abas concorra com o visualizador.
- MediaStore é consultado em lotes de 96 itens usando paginação por data e ID; busca de álbuns e miniaturas em background, LRU de 18 MiB e decodificação de thumbnail adequada à exibição. Item clicado nunca decodifica o original inteiro para preencher a grade.
- A posição da grade é mantida ao alternar abas.

## Visualizador

- Nova página em preto `#080808`, imagem sem corte, informação discreta e três ações circulares: **Baixar**, **Editar** e **Compartilhar**.
- Swipes horizontais percorrem a ordem carregada, solicitando páginas adicionais quando próximo do fim; com zoom ativo os movimentos passam a deslocar a imagem, sem trocar fotografias.
- Tap simples oculta/exibe chrome, duplo toque alterna ampliação e pinch amplia até 5×; limites de pan contêm a imagem ampliada.
- Decodificação assíncrona até 2048 px na aresta maior para o visualizador, com LRU de 42 MiB, pré-carregamento adjacente e transições de 160 ms.
- O **arquivo original nunca é modificado**. Baixar faz cópia binária direta (sem recompcompressão) para `Pictures/Noise!/Saved`, com publicação transacional no MediaStore. Em Android 8–9 pede permissão de escrita quando necessário.
- Editar passa o URI selecionado ao importador existente; Compartilhar usa URI e MIME originais com concessão temporária de leitura.

## Presets e exportação

- Intensidade dos Presets de 0–100%, com interpolação efetiva de ajustes, curvas e rodas cromáticas, prévia GPU debounced e aplicação como única operação undo/redo. Efeitos discretos mudam no limiar de 50%; geometria e resolução permanecem intactas. Incluídas categorias, substituição do preset personalizado pelos ajustes atuais e miniaturas menores.
- Tela de exportação em página própria com cabeçalho/Voltar, prévia compacta, dimensões, aspecto, tamanho estimado com indicação de incerteza, formatos efetivos **PNG, JPEG e WebP**. HEIC não aparece porque não há encoder funcional nesta arquitetura. Ações fixas, conteúdo rolável e insets do Android respeitados.
- Codificação WebP real com extensão/MIME corretos; compatibilidade com documentos JPEG legados preservada. Exportação para Pictures/Noise! sem seletor de pasta ou notificação nativa.

## Menu rotativo

- Cores individuais de ferramenta com saturação controlada e transição cromática ao ganhar foco, molduras de contraste e glow.
- Retorno do efeito de ligação viscosa Bézier solicitado nesta revisão; acompanha o deslocamento fracionário sem animação reiniciada, preservando a dinâmica da roda.

## Escopo e validação

- O shader do fundo procedural da Home não foi modificado.
- Acesso parcial do Android é respeitado: a consulta só exibe itens que o MediaStore permite ler.
- Necessários: build debug/androidTest, instrumento em emulador, salvar e compartilhar fotos reais, zoom/troca de fotos, presets e exportação. O emulador não substitui testes de fluidez em dispositivo físico.

Versão `1.39.0` · `versionCode 56` · identidade de pacote e assinatura permanente preservadas.
