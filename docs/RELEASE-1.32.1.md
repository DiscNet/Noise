# Noise! V1.32.1 — Câmera fixa, zoom por enquadramento e foto orientada

- **Tela de câmera fixa em retrato:** interface, botões, prévia e dimensões não giram quando o celular é colocado na horizontal. Ao sair, o app restaura a política de orientação anterior.
- **Zoom visual por moldura:** o sensor/preview não é ampliado quando o zoom muda. Em 1×, a moldura tem o tamanho de enquadramento original; em 2×, a largura e altura diminuem pela metade; em 4×, para um quarto. A imagem atrás permanece imóvel.
- **Foto corresponde à moldura:** o recorte do JPEG utiliza as coordenadas do quadrado/retângulo visível. Foi eliminado o zoom duplo que acontecia quando a prévia e o recorte eram ampliados ao mesmo tempo.
- **Giro apenas da fotografia:** um `OrientationEventListener` lê a posição física do telefone em 0/90/180/270°. A foto JPEG é primeiro enquadrada na posição fixa da câmera e só então girada para a posição do dispositivo, antes dos ajustes de cor e do salvamento.
- Mantidos foco por toque, seletor de formatos 1:1 / 4:3 / 16:9, saturação, temperatura, exposição, troca de câmera e armazenamento em `Pictures/Noise!`.

## Validação
- Testes instrumentados verificam: centro da moldura inalterado, redução geométrica correta em 2× e 4×, recorte correspondente à moldura, orientação quantizada e JPEG paisagem quando o aparelho é girado, além do layout da câmera sempre vertical.
- A compilação e publicação oficial dependem do GitHub Actions.
- A qualidade real do foco e da captura, rotação em modelos específicos de sensor/câmera frontal e campo de visão da foto ainda requerem teste em aparelho físico.

APK: `Noise.V1.32.1.apk` · `versionCode 40` · assinatura permanente da linha oficial.
