# Noise! V1.31 — Câmera com ajustes ao vivo

## Câmera integrada ao Noise!
- Novo acesso **Câmera** na Home, ao lado de **Importar imagem**, além de opção no menu do editor.
- Prévia da câmera do aparelho diretamente no Noise!, sem aplicativos externos nem envio de imagens para a internet.
- Três sliders aplicados **ao vivo** à imagem da câmera:
  - **Saturação**: 0–200%, com 100% neutro.
  - **Temperatura**: fria (−100%) ↔ quente (+100%), centro neutro.
  - **Exposição**: −2 a +2 EV, com ganho digital e neutralidade em 0 EV.
- **Zerar** restaura os três parâmetros. **Virar** alterna câmera traseira e frontal (se houver).
- A mesma matriz de cor dos controles é aplicada ao frame de prévia e à fotografia capturada: nenhum ajuste é apenas decoração.
- **Capturar** salva JPEG em `Pictures/Noise!`; **Editar** abre a última foto no editor não destrutivo do Noise!.

## Permissões e compatibilidade
- Solicita `CAMERA` somente ao abrir a câmera, e não no primeiro uso do editor.
- Câmera é opcional no Manifest: aparelhos sem câmera continuam podendo usar o restante do app.
- Android 8/9 requer permissão de armazenamento para salvar na pasta pública; Android 10+ utiliza `MediaStore` com publicação transacional.
- Fotos JPEG são orientadas conforme EXIF e processadas sem alterar a foto original capturada por outros aplicativos; exportadas em no máximo 4096 px no maior lado para controle de memória.
- Usa `TextureView` com camada de composição por hardware; não decodifica cópias de todos os frames da câmera.

## Garantia de qualidade
- Teste instrumentado de saturação, calor/frio e exposição em pixels reais, neutralidade e presença dos três sliders.
- CI cobre build Android, suíte focalizada, assinatura permanente e release sem depuração.
- **Não foi validado com câmera física**: implementação depende do suporte Camera1 e da composição de filtro em hardware (`TextureView.setLayerPaint`) do fabricante; requer teste em aparelhos reais, principalmente câmera frontal, rotação e equivalência entre foto salva e prévia.
- Emulador do CI pode não possuir câmera virtual: o teste colorimétrico não comprova captura física ou funcionamento em todas as GPUs.

## Atualização
`versionName=1.31`, `versionCode=38`, `applicationId=com.discnet.noise`. A versão oficial mantém a keystore permanente da V1.30.3 para oferecer **Atualizar**.
