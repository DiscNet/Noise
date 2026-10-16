# Noise! V1.37.5 — degradês, biblioteca de projetos e salvamento visível

A atualização refina a identidade visual solicitada sem alterar o shader, o movimento procedural, o círculo ou o restante do fundo animado da tela inicial.

## Menu rotativo

- O **painel quadrado geral da V1.37.4 foi removido**. O dock voltou a ter fundo transparente, com degradês laterais em preto, roxo e roxo-escuro e entrada/saída suaves.
- Removidos os cartões quadrados dos ícones/legendas. As linhas circulares, o foco, o crossfade e a transição líquida Bézier clássica foram preservados.
- O botão **Selecionar** é o único bloco preenchido e quase quadrado do menu: branco, texto preto, halo branco, 3 dp de raio, margens simétricas e texto centralizado.
- Contraste dos títulos e do arco refinado sem adicionar fundo sólido sobre a foto.

## Meus projetos

- Superfícies em preto, roxo e roxo-escuro, com gradientes de verdade nos cartões, fundo e diálogos.
- Botões principais brancos com texto preto, secundários roxos com texto branco; raios reduzidos para 4–6 dp.
- Mantidos a prévia das imagens, os projetos, favoritos, renomear, abrir, excluir e o armazenamento protegido.

## Home — somente botões

- Os botões de projetos e configurações, os atalhos Editar foto/Câmera, o último projeto e a navegação inferior seguem a mesma paleta branco/preto/roxo/roxo-escuro.
- Contraste entre símbolos e fundo foi ampliado; componentes clicáveis têm cantos discretos.
- **O fundo procedural, a Sphere, suas animações e a organização da tela inicial não foram alterados.**

## Exportar imagem

- A confirmação de salvar foto agora abre uma **tela modal de carregamento** com spinner indeterminado, texto de status e fundo de alto contraste.
- A tela aparece quando a exportação realmente começa, aguarda o processamento GPU e o salvamento do arquivo, e some tanto em sucesso quanto em falha.
- Não exibe porcentagens inventadas; os gestos do menu ficam temporariamente bloqueados, evitando interação sob a tela de progresso.
- A exportação permanece no executor de fundo, com limpeza de recursos e publicação do arquivo em `Pictures/Noise!`.

## Verificação

O workflow compila APK debug + AndroidTest, valida gestos, cores, telas e o fluxo de exportação, e só publica a release oficial na `main` depois de passar. Recomenda-se a inspeção no celular do alinhamento e da suavidade das animações.

Versão `1.37.5` · `versionCode 54` · package e assinatura permanente inalterados.
