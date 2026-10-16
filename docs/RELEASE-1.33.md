# Noise! V1.33 — Camera Studio

Reestruturação da câmera existente, sem substituir o editor de fotos.

## Implementado
- CameraX (PreviewView em modo compatível, Camera2 por baixo, LifecycleRegistry independente), com captura JPEG real via ImageCapture em resolução máxima selecionada pela implementação.
- A foto é produzida pelo JPEG capturado, **nunca** por screenshot ou bitmap do preview.
- Visor grande, fundo escuro, canto discreto, ferramentas circulares em rolagem horizontal e régua graduada com indicador central, inércia, feedback tátil e toque duplo para valor neutro.
- Zoom por moldura: jamais chama CameraControl.setZoomRatio, jamais amplia o PreviewView; a moldura diminui com o zoom e pode ser arrastada pela borda.
- Fotos na proporção exata: 1:1, 4:3, 3:4, 16:9, 9:16 e 3:2. O modo nativo é selecionado pela câmera (ver limitações).
- Foco/medição por toque usando CameraX FocusMeteringAction.
- Flash Off/Auto/On apenas quando o dispositivo suporta flash; câmera frontal/traseira; temporizador 0/3/5/10 s.
- Ferramentas contínuas ao vivo: exposição EV física quando suportada (complementada por processamento digital), saturação, temperatura, matiz e contraste. Zoom funciona por recorte, não por ampliação do sensor/preview.
- Captura assíncrona com crop de JPEG em sua resolução original usando BitmapRegionDecoder (sem downsampling silencioso), tratamento de EXIF, rotação da foto detectada por sensor e salvamento MediaStore em Pictures/Noise!.
- Botões **Galeria** e **Editar** conectados ao app original.

## Limitações e próximos passos
- O preview é colorido por ColorMatrix sobre uma camada hardware: alguns GPUs de fabricantes podem ignorar filtros de camada do PreviewView; validar em aparelho real.
- Não foram adicionados recursos de nitidez ao vivo, foco manual por distância e HEIC: precisam de implementação específica e checagem de hardware; não mostrar controles falsos.
- O alinhamento fino de imagem não está disponível na régua; a orientação do dispositivo já é aplicada à foto final.
- O enquadramento pode ter diferença residual de campo de visão entre Preview e ImageCapture em aparelhos que usam sensores/streams com crop distintos; necessita calibração com capturas reais.
- Testes automatizados validam matemática de zoom, mirror/framing e UI no emulador, mas **não substituem testes físicos**, inclusive captura frontal, flash e detalhes de cor.
- A alta resolução pode demandar memória significativa com filtros ativos; falhas são exibidas, não ocultadas por diminuição automática da resolução.

`versionName=1.33.0`, `versionCode=41`, mesma assinatura permanente das Releases oficiais. Não publicar antes de CI verde.
