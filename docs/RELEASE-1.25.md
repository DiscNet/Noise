# Noise! V1.25 — Permissão inicial, Glow, Brancos e bordas dos menus

## Permissão na primeira abertura
- A primeira execução instala a UI e solicita automaticamente **acesso às imagens**, apenas uma vez (caso ainda não exista autorização).
- No Android 14+, considera acesso total ou parcial às fotos; em Android 13 pede READ_MEDIA_IMAGES e em versões anteriores READ_EXTERNAL_STORAGE.
- O aplicativo respeita uma negativa: não volta a pedir autorização automaticamente a cada abertura.
- A galeria do sistema continua disponível sem acesso completo; o usuário pode solicitar permissão novamente pelo comando explícito da galeria.
- Persistência local da informação `gallery_permission_first_launch_asked`.

## Correções de processamento GPU
- **Glow:** o filtro anterior dependia totalmente de um limiar de luminância rígido; imagens escuras ou pouco contrastadas podiam produzir zero emissão luminosa. O novo kernel emissivo adiciona energia também para pixels menos brilhantes sem deslocar a textura base. Limiar de 100% permanece matematicamente definido.
- **Brancos:** removida a dupla atenuação do controle. A nova curva de branco afeta altas luzes coloridas e meios-tons superiores com compressão suave, sem apagar sombras quase pretas e evitando clipping abrupto.
- Preview e exportação utilizam os mesmos shaders.

## Menu orbital
- Linhas do arco foram convertidas em pequenos segmentos com opacidade gradualmente reduzida perto das bordas externas e das extremidades da roda, removendo o traço que terminava abruptamente no recorte da tela.
- Rótulos e ícones respeitam os limites da área disponível e desaparecem progressivamente nas extremidades superior/inferior.
- Mantidas as transições de fade, o scroll amortecido e as laterais atuais: Ferramentas à direita, Efeitos à esquerda, somente por gestos iniciados nas bordas.

## Verificação
- Testes instrumentados atualizados para detectar mudança de Brancos em imagens coloridas pouco iluminadas e Glow com limiar alto/máximo, sem alterar o modo de comparação da imagem original.
- Checagem de solicitação inicial de permissão e testes existentes de abertura, seleção, GPU e exportação.

Instalador de prévia: `Noise.V1.25.apk`. Validar no emulador e, para aparência/gestos, em dispositivo físico antes de publicar release final.
