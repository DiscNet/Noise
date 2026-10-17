# Noise! V1.49.0 — Bisel e relevo: luz/sombra independentes

O Noise! V1.48 ainda misturava luz branca e sombra preta num único mapa de pixels, aplicando um cinza translúcido sobre texto colorido. Isso causava relevo opaco, acinzentado e visualmente "sujo".

## Motor gráfico refeito
- Geometria calculada em alta resolução adaptativa com **campo de distância euclidiana** e normais suavizadas, mantendo as bordas antialiased dos caracteres.
- Renderização de dois mapas separados: **realce e sombra**. Zero relevo produz transparência, nunca tinta cinza.
- Composição de realce em **Screen** e sombra em **Multiply** por padrão; cada mapa agora recebe seu próprio modo de mesclagem, cor e opacidade, seguindo os conceitos do Photopea/Photoshop.
- Iluminação calculada contra a face plana, considerando ângulo, altitude e profundidade; perfis geométricos distintos para Suave, Cinzel duro e Cinzel suave.
- Contornos configuráveis continuam editáveis; sua curva modula o brilho sem contaminar os pixels neutros.
- Bisel interno permanece restrito ao alfa real do texto, evitando halos externos e manchas brancas ou pretas.
- Cache LRU específico de 12 MiB para os dois mapas de bisel e orçamento adaptativo de 960 mil pixels para cálculo de iluminação no Android.
- Interface do efeito reorganizada para apresentar modo, cor e opacidade da luz e da sombra separadamente.

## Compatibilidade
- Mantido **.noise formato v7** e leitura dos projetos de versões anteriores. Propriedades ópticas novas usam valores padrão para projetos antigos; não é preciso rasterizar o texto.
- Esta implementação é **independente** e reproduz o fluxo público de efeitos do Photopea. Não contém código-fonte proprietário do Photopea e não deve ser considerada reprodução pixel-perfect de todas as suas técnicas internas.

## Regressões
- Suíte Android com o texto "Luka" verifica antialias, ausência de silhuetas fantasma, separação de modos Suave/Cinzel e ausência de névoa cinza em realces/sombras.
- Testes de persistência verificam que Screen/Multiply e modos personalizados são restaurados ao abrir o .noise.
