# Noise! 1.7

Editor de fotos offline para Android 8+ com prévia em OpenGL ES 2.0 e exportação PNG.

## Recorte direto
O botão de edição de proporções abre um **editor de recorte personalizado** sobre a fotografia. Arraste qualquer um dos quatro cantos da moldura ou mova a seleção pelo centro; grade de terços, regiões externas escurecidas e resolução do recorte são mostradas em tempo real. **Aplicar recorte** produz uma imagem com exatamente aqueles pixels, sem exigir números ou sliders. **Cancelar** mantém a imagem como estava. Após aplicar, o editor trabalha com a foto cortada; para recuperar pixels que foram removidos, reabra a imagem original.

## Galeria e álbuns
A aba inicial traz até 500 **fotos recentes**, para carregamento ágil. Diferente das versões anteriores, os álbuns são descobertos no **MediaStore inteiro**, e escolher uma pasta consulta **todas as imagens dela**, sem limite artificial de recentes. Miniaturas ficam em cache e são carregadas sob demanda. Para usuários que negam permissão de acesso a fotos, a galeria padrão (Photo Picker do Android 13+ ou app Galeria do dispositivo em versões anteriores) funciona como alternativa. Fotos nunca são enviadas para a rede.

## Exportação automática e permanente
Toque no ícone **Salvar**. O PNG é gravado automaticamente em **Imagens / Noise!**, que corresponde ao caminho público **Pictures/Noise!** do armazenamento do aparelho. Não há diálogo de selecionar pasta nem uso de `ACTION_CREATE_DOCUMENT`. A foto fica visível ao aplicativo de Galeria, ao explorador do usuário e **permanece após desinstalar o Noise!**.

- Android 10+: inserção pública via `MediaStore.Images` com `RELATIVE_PATH=Pictures/Noise!` e `IS_PENDING` para que um arquivo parcial não seja exibido na Galeria.
- Android 8 e 9: acesso à pasta pública de Imagens com permissão de armazenamento, `FileOutputStream` e `MediaScannerConnection`.
- Permissões de fotos e armazenamento são solicitadas somente quando necessárias.

## UI e aparência
Superfícies de vidro cromático inspiradas nas diretrizes atuais do Liquid Glass: translucidez, luz especular na borda, reflexos interiores, cantos e destaques suaves. São **componentes personalizados do Android**, e não os elementos proprietários do iOS, portanto não reproduzem a mesma refração física em todos os dispositivos.

Permanecem os recursos 1.6: Dither neon detalhado, glow ao redor das linhas, ícones vetoriais, desfoque rotativo aplicado antes da vinheta, poeira analógica e fundo luminoso de edição.

## Compilação
Java 17, Gradle 8.9, Android SDK 35. `gradle --no-daemon assembleDebug assembleDebugAndroidTest`. No GitHub Actions, o APK só é publicado quando o build e os testes Android são aprovados.

[Último APK](https://github.com/DiscNet/Noise/releases/latest)
