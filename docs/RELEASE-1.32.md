# Noise! V1.32 — Foco por toque, orientação e enquadramento real

## Câmera separada
- A opção **Câmera** permanece isolada da galeria e do editor. Um toque no visor ativa o foco automático no ponto escolhido, com marcador visível. Não abre seleção de imagens.
- O foco usa as áreas de foco/medição disponibilizadas pelo hardware. Em dispositivos sem foco por toque, informa a limitação e mantém o comportamento que a câmera suporta.

## Orientação, proporção e zoom
- A tela de câmera aceita rotação com o sensor do celular. Em posição horizontal, os ajustes passam para um painel lateral rolável, mantendo o visor disponível.
- A orientação do visor e a rotação do JPEG usam a orientação declarada pelo hardware. Fotos EXIF são endireitadas antes do salvamento.
- Proporções **1:1**, **4:3** e **16:9**: a moldura central muda de formato conforme a posição do telefone.
- **Zoom digital 1–4×**, controlado por slider ou pinça no visor. Uma moldura luminosa com grade delimita a área efetivamente salva; as áreas externas ficam escurecidas.
- O recorte é calculado sobre a fotografia orientada usando a geometria da prévia, o zoom e o formato selecionado; a foto exportada corresponde à região central indicada.

## Ajustes preservados
- Saturação de 0 a 200%, temperatura fria/quente e exposição digital de −2 a +2 EV permanecem ao vivo.
- **Capturar** salva em `Pictures/Noise!`, com os ajustes e o recorte aplicados. **Editar** abre o JPEG no editor Noise!.
- O pacote `com.discnet.noise`, a chave de assinatura permanente e a importação normal de imagens permanecem inalterados.

## Validação e limitações
- Android CI cobre compilação, testes colorimétricos anteriores, matemática dos formatos/recorte, zoom e toque no visor, além da assinatura de release.
- O emulador CI não valida foco físico, câmera frontal, autofocus de fabricantes nem diferenças entre campo de visão da câmera e fotografia em todos os aparelhos. Essas verificações exigem teste real em dispositivo.
- O zoom é **digital por recorte**, não o zoom óptico do sensor. Dispositivos que expõem tamanhos diferentes de imagem de prévia e JPEG podem apresentar pequenas diferenças de enquadramento.

Versão `1.32`, `versionCode 39`, Android 8+, APK oficial `Noise.V1.32.apk`.
