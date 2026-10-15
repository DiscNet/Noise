# Noise! V1.29.1 — gestos das curvas, configurações e acabamento minimalista da Home

Base: V1.29 em desenvolvimento. A versão estável do Noise! continua na release V1.27; **este documento descreve uma prévia**, não uma release validada.

## Menus versus controles de cor
- Corrigida a classificação de toques iniciados nas rodas cromáticas e nas curvas Hue × Hue, Saturação × Hue e Saturação × Saturação. O detector global de gesto reconhece agora os retângulos **efetivamente visíveis** desses controles; arrastos nessas áreas nunca disparam o menu orbital.
- Preservado o gesto horizontal no restante da área do editor e a proteção preexistente aos sliders, sem retornar à restrição "somente sobre a foto".
- Testes instrumentados ampliados com verificação das áreas de cor e envio de gesto horizontal real sobre a curva.

## Configurações
- Nova tela `SettingsScreen` com interface própria, tipografia Manrope e primeira preferência persistente: **Redimensionar painel de ajustes**.
- A preferência é **desativada por padrão**. Com ela desligada, tocar ou arrastar a alça do painel não altera a altura. Ao ativar, o gesto e os estados compacto/intermediário/expandido voltam a funcionar.
- Configurações acessíveis tanto na Home quanto no cabeçalho do editor. O título da alça identifica painel fixo ou ajustável.
- Preferência salva em armazenamento de configurações do app, independente dos documentos/projetos.

## Tela inicial
- Removidos do círculo central os anéis orbitais, traços, nós e pequenas esferas. A nova peça utiliza **apenas gradientes radiais macios**, luz ambiental e movimento suave do brilho interno.
- Fundo animado da Home preservado, com controle de pausa/retomada ao ocultar ou exibir a tela.
- Botão `Importar imagem` redesenhado com **3dp de raio**, preenchimento translúcido, gradiente de vidro fosco com desfoque de sua camada própria em Android 12+ e contorno de 1dp. O desfoque da camada do botão não captura nem modifica a imagem em edição.

## Testes / entrega
- Testar armazenamento da preferência, trava da altura, reativação da altura e ausência de abertura orbital durante edição das curvas/rodas.
- Validar capturas da Home e ciclo de vida antes de qualquer aprovação da release.
- Nome do instalador de desenvolvimento: `Noise.V1.29.1.apk`.
