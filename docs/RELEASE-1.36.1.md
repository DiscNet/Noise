# Noise! V1.36.1 — Vidro refinado, formas atmosféricas e transições suaves

Correção visual após avaliação da V1.36.0 em aparelho físico e comparação com quatro referências fornecidas.

## 1. Geometria mais precisa
- **Cards de edição e câmera: 7 dp** (antes 10 dp).
- **Painel principal da Home: 8 dp** (antes 12 dp).
- **Botões superiores: 7 dp**; retomada de projeto: 6 dp.
- O círculo central continua perfeitamente circular e mantém a sua borda única e fina.

## 2. TexturedGlass reconstruído
- O antigo vidro usava **amostra de fundo a 1/8 da resolução**, ampliada e combinada a ruído de células grandes. Isso produzia a aparência de nuvens/manchas pixeladas no print de teste.
- A amostra do fundo agora é **1/4 da resolução**, filtrada por duas passagens de blur separável; a transmissão no cartão utiliza **interpolação bilinear**.
- O material é renderizado com malha de aproximadamente metade da resolução do cartão, limitada a dimensões seguras. O relevo martelado usa campos procedurais com células de poucos pixels e refração inferior a um pixel, sem borra grosseira.
- Reflexos, contornos e coloração foram reduzidos e neutralizados para não competir com os textos.
- A textura e os bitmaps ficam em cache e são atualizados aproximadamente a cada 2,8 segundos, não a cada quadro. A interface continua responsiva.
- O efeito é exclusivo dos painéis de UI: **não modifica nenhum pixel da imagem editada ou fotografada**.

## 3. Fundo híbrido: antiga atmosfera + formas da referência
- As silhuetas planas de alto contraste da primeira V1.36 foram substituídas por **campos de tinta orgânicos com bordas gradativas**. Não são mais polígonos pretos com preenchimento opaco.
- Os campos são desenhados uma vez por dimensão numa textura RGBA de baixo custo, com variação orgânica sutil e opacidade parcial.
- O plano de fundo mantém a base grafite/azul/violeta com manchas de luz atmosférica animadas, criando profundidade sem parecer wallpaper genérico.
- A transparência das formas deixa o material do vidro reagir às mesmas cores, com direção de arte consistente.

## 4. Menu rotativo: suavidade maior
- Amortecimento do menu de ferramentas e efeitos reduzido para proporcionar **assentamento mais suave**, sem salto instantâneo.
- Inércia do gesto limitada para evitar avanços bruscos entre itens.
- A massa líquida usa **uma única membrana Bézier contínua** com tangentes no pescoço da fusão, contorno antisserrilhado, brilho difuso mínimo e opacidade suavizada nas extremidades.
- A deformação final do item focado dura **455 ms** (antes 285 ms) e começa com uma borda mais suave, evitando que a seleção pareça surgir de repente.
- O líquido continua ligado ao offset fracionário da roda: acompanha o dedo, o movimento de inércia e a inversão de direção sem reiniciar animadores de ponte.
- Apenas o ícone alinhado ao centro recebe o contorno branco permanente.

## Validação
- Testes instrumentados de geometria (cantos 6–8 dp), resolução do fundo amostrado, gradientes translúcidos da tinta e transição de alpha no metaball.
- Mantidos os testes de câmera, filtro GPU, exportação, banco de projetos, assinaturas e compatibilidade.
- A avaliação visual de material e a latência percebida devem ser validadas adicionalmente em hardware real; testes no emulador não garantem aparência idêntica em todas as telas.

`versionCode 47` · `versionName 1.36.1` · certificado permanente preservado.
