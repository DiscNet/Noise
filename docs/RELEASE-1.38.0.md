# Noise! V1.38.0 — navegação do estúdio, galeria e exportação

## Navegação principal
- A navegação inferior é única, compartilhada por **Início, Galeria e Projetos**; ícones e legendas mantêm contraste claro, com seleção branca e texto preto.
- Deslizar horizontalmente navega entre as três abas. Movimentos verticais permanecem disponíveis para listas e galerias; modais, câmera, Presets e exportação não recebem o gesto.
- O acesso à edição pela Home fica restrito à área circular da esfera. Espaço ao redor não abre mais acidentalmente o seletor.

## Galeria
- A antiga aba “Biblioteca” passa a chamar-se **Galeria**.
- A consulta MediaStore exibe as fotos editadas e as fotos da câmera do Noise! na pasta `Pictures/Noise!`, em ordem decrescente. Mantém filtragem por pasta, thumbnails assíncronos e solicita permissão quando necessário. A consulta é compatível com Android anterior ao 10.
- Tocar na foto abre ações próprias da interface: **Editar imagem** ou **Compartilhar**, sem entrada direta no editor.
- Compartilhar usa a folha de compartilhamento Android apenas quando o usuário explicitamente pede essa ação.

## Projetos
- A aba mantém projetos privados, miniaturas, abrir, renomear, excluir e armazenamento protegido.
- Cartões com maior contraste/descrições, melhor hierarquia e espaço inferior para a navegação persistente.

## Menu rotativo
- A membrana líquida é retirada do renderizador da roda. Permanecem a trajetória orbital, molas, fades, foco e seleção.
- O botão Selecionar é centrado em relação à **tela inteira**, inclusive quando a roda abre por qualquer lado.

## Exportação
- O fluxo de tamanho/formato é uma **tela própria em tela cheia**, com parâmetros roláveis e ações fixas, em vez do Dialog inferior.
- Controles de proporção em texto, sem emojis.
- O salvamento mostra progresso indeterminado e, depois do sucesso, uma confirmação **personalizada Noise!**, em vez de AlertDialog nativo Android. Erros encerram o progresso e mostram o status.
- Permanece salvando diretamente em `Pictures/Noise!`, preservando o original e usando o processamento GPU no executor de fundo.

## Presets
- Melhor adaptação para telas Android baixas: prévia e cartões responsivos, além de modais roláveis compatíveis com teclado e visores compactos.

## Escopo
O fundo procedural da Home, seus shaders, renderizador e animações não foram alterados. Mudaram apenas interações da Home e as demais telas solicitadas.

Versão `1.38.0`, `versionCode 55`, assinatura e package Android preservados. Validar a aparência em celular físico após os testes automatizados.
