# Noise! V1.30 — Biblioteca de câmeras analógicas e projetos preservados

Base: V1.29.1 na branch de desenvolvimento `feature/noise-v1-28-refinement`.

## Painel de edição
- Removidos os interruptores de ativação/desativação dos efeitos, Dither e ajustes. A intensidade fica diretamente no slider e zero corresponde ao efeito neutro.
- Removido o gesto de pressionar o nome de um parâmetro para pausá-lo; permanecem edição numérica, sliders e restauração.
- Para não perder ferramentas sem equivalente numérico, Inversão de cores e Grão colorido usam escolhas de modo (chips), não Switches. O ASCII usa modo `Imagem original`, `Monocromático`, `Foto` e `Nonqual`; ajustar seu parâmetro ativa o resultado. Restauração e histórico permanecem.
- Projetos legados com parâmetros desativados continuam compatíveis; o slider indica o valor efetivamente aplicado, e editar o valor reativa o ajuste.

## Projetos que sobrevivem à desinstalação
- Introduzido `ProjectVault`, armazenamento compartilhado via **Storage Access Framework** (SAF), sem permissões amplas de arquivos.
- Após importar a primeira foto, o Noise! solicita **uma vez** uma pasta do telefone para manter cópias permanentes (pode ser cancelado). A biblioteca de projetos oferece `Proteger projetos após desinstalar`, disponível a qualquer momento.
- Na pasta selecionada, o Noise! cria `Noise! Projects/`, contendo subpastas com UUID e cópias independentes de `original` + `project.json` (incluindo parâmetros e histórico).
- Salvamentos, renomeações e exclusões também sincronizam a pasta compartilhada, em segundo plano, quando autorizada; no primeiro vínculo os projetos privados existentes são copiados.
- **Após reinstalar:** a autorização de acesso às pastas é removida pelo Android. Abra `Meus projetos → Proteger projetos` e selecione **a mesma pasta escolhida anteriormente**. O Noise! detecta os projetos compartilhados e os restaura ao aplicativo.
- Sem escolher a pasta (ou após revogar o acesso), os arquivos permanecem só no armazenamento privado e **não** sobrevivem à desinstalação. O app não pode contornar essa política do Android.
- Não excluir manualmente a pasta compartilhada. Se o telefone apagar os arquivos ou o sistema/aplicativo de arquivos os remover, a recuperação não será possível.

## Presets fotográficos
- Adicionados **12 filtros originais com estética de câmeras analógicas**, usando somente o pipeline GPU existente, com ajustes editáveis e prévia real:
  `Pocket 35`, `Instantânea`, `Verão 98`, `Filme 400`,
  `Flash Noturno`, `Filme Desbotado`, `Prata 1976`,
  `Negativo Âmbar`, `Hora Azul`, `Câmera de Brinquedo`,
  `Cidade 2002` e `Papel Fosco`.
- A inspiração estética é de aplicativos de câmera retrô como OldRoll, sem reutilizar imagens, pacotes de filtros, ícones ou nomes proprietários.
- Os presets antigos foram preservados, e os novos aparecem agrupados como `CÂMERAS & FILMES` dentro da galeria personalizada de presets.

## Validação
- Testes adicionados para ausência de Switches, quantidade/unicidade dos novos presets, compatibilidade JSON, presença do controle de backup, e validação de IDs dos projetos.
- Fluxo SAF real requer interação do usuário com o seletor do Android; **não** marcar como validado por teste automatizado sem executar restauração após reinstalação.
- Compilação e testes de emulador serão executados no GitHub Actions. Instalação e verificação física ainda pendentes.
- APK de prévia: `Noise.V1.30.apk`.
