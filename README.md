# Noise!

Editor de imagens Android, offline, com interface nativa em Java e renderização **OpenGL ES 2.0**. Os ajustes são representados por uma receita editável; a imagem original é preservada no projeto.

> **Versão 1.14:** redesign do editor em validação. O APK só é distribuído depois do build e dos testes instrumentados aprovados. [Versão publicada](https://github.com/DiscNet/Noise/releases/latest) · [Trabalho da 1.14](https://github.com/DiscNet/Noise/pull/13).

## Editor

O cabeçalho compacto concentra voltar, desfazer, refazer, exportar e menu secundário. A prévia mantém proporção, suporta zoom por pinça, arraste após ampliar, comparação com o original ao pressionar e tela cheia. Em retrato, o painel de ajustes pode ser expandido ou recolhido pela alça; em paisagem, fica ao lado da imagem.

As sete categorias, acessíveis por navegação horizontal, são **Luz, Cor, Detalhes, Efeitos, Dither, ASCII e Ferramentas**. Cada categoria mostra apenas seus controles pertinentes; o estado é preservado ao alternar de ferramenta. Os valores podem ser digitados; toque duplo no nome restaura o valor neutro, e pressão longa alterna o ajuste sem perder seu valor. Sliders atualizam o estado GPU durante o movimento e geram uma operação lógica de histórico por gesto.

## Edição e efeitos

- **Luz:** exposição, brilho, contraste, realces, sombras, brancos e pretos.
- **Cor:** saturação, vibração, temperatura, tonalidade, matiz e inversão.
- **Detalhes:** nitidez, textura, redução de ruído, granulação, tamanho e aspereza do grão, realce de bordas.
- **Efeitos:** vinheta, fade, glow ajustável (intensidade/limiar/raio), aberração cromática, CRT, glitch, anéis e névoa.
- **Dither:** Neon/Nonqual, Bayer 4×4, threshold, controles por algoritmo e opções de paleta (Nonqual azul, laranja e vermelho; preto e branco; RGB).
- **ASCII:** caracteres renderizados como imagem exportável; densidade, brilho, contraste, limiar, espaçamento, tamanho, símbolos alternativos e modos monocromático, cores da foto ou Nonqual.
- **Ferramentas:** recorte não destrutivo com alças, proporções livres e fixas, rotação, espelhamento, presets, projetos, copiar/colar ajustes e exportação.

O modelo de edição inclui **desfazer/refazer**, projetos locais e presets salvos com parâmetros reais. Projetos guardam uma cópia privada do arquivo de origem, estado de edição e histórico. Os presets podem ser criados, renomeados, excluídos e favoritados.

Os efeitos compatíveis com o fluxo atual usam shaders GPU; a prévia é limitada a uma textura reduzida para favorecer a fluidez e a exportação decodifica a imagem de origem separadamente. O teto atual de exportação é **4096 px no lado maior** ou o limite físico de textura, prevalecendo o menor.

## Arquivos

PNG com transparência quando aplicável ou JPEG com qualidade selecionável e fundo branco. O arquivo é publicado em **Pictures/Noise!** por MediaStore (Android 10+) ou na pasta pública correspondente em versões anteriores, preservando-o após desinstalação. Há opção de compartilhar pelo Android.

A galeria utiliza MediaStore, permite selecionar pastas/álbuns e consulta todas as imagens acessíveis no álbum. O seletor de fotos do sistema é uma alternativa de importação.

## Build e verificação

Requisitos: **Java 17, Gradle 8.9, Android SDK 35**. Para compilar:

```sh
gradle --no-daemon assembleDebug assembleDebugAndroidTest
```

O fluxo em `.github/workflows/android.yml` compila, inicia um emulador Android API 30 e executa verificações instrumentadas dos shaders, estados, efeitos, galeria, importação, recorte, histórico, presets e exportação. Um resultado verde exige também `NOISE_SMOKE_OK` nos logs. Apenas o fluxo de `push` em `main`, após a verificação, cria uma release versionada.

Cobertura, evidências e limitações: [Auditoria](docs/AUDIT-1.14.md) · [Validação](docs/VALIDATION.md).

## Limitações conhecidas

Edição por máscaras, curvas RGB, dithering por difusão de erro (Floyd–Steinberg e Atkinson) e exportação sem limite de tamanho **não estão implementados**. O preview e a exportação podem diferir em detalhes dependentes da resolução, apesar de compartilharem os mesmos parâmetros. Projetos ficam no armazenamento privado do app; ao desinstalá-lo, **projetos e presets locais são removidos**, mas os arquivos exportados em Pictures/Noise! permanecem.

Referências conceituais: [Lightroom Mobile](https://helpx.adobe.com/lightroom/mobile/get-started/gesture-controls-in-lightroom-for-mobile.html) · [Photopea](https://www.photopea.com/learn/workspace).
