# Noise! V1.24 — Escurecimento completo e bordas com gradiente

A captura do usuário na V1.23 revelou que o gradiente anterior desaparecia por completo à distância do menu, deixando a imagem clara, além de elementos da roda cortados repentinamente nas bordas.

## Alterações
- **Fade in e fade out de tela inteira:** um scrim preto translúcido fica acima do editor inteiro e chega a 69% de opacidade. Não depende do lado selecionado.
- **Gradiente lateral adicional:** uma segunda camada transparente ocupa toda a tela e escurece mais a região da roda, diminuindo gradualmente até desaparecer no lado oposto. O menu à esquerda inverte a orientação em relação ao menu à direita.
- **Animações independentes:** o scrim uniforme e o gradiente lateral animam com easing temporal por frame (~200 ms), separadamente do fade e da mola criticamente amortecida do conteúdo do menu.
- **Nenhuma borda preta retangular:** o conteúdo orbital continua sem fundo opaco, sobreposto às duas camadas translúcidas.
- **Corte de textos e ícones corrigido:** as linhas da roda são mantidas dentro da região visível, com atenuação gradual para itens distantes e arco menos marcado.
- **Gestos mantidos:** arrastar apenas a partir da borda direita abre Ferramentas à direita; a borda esquerda abre Efeitos à esquerda. Não seguem a posição do dedo.
- Testes instrumentados atualizados para exigir escurecimento uniforme em tela inteira, gradiente adicional com fade, e desaparecimento progressivo das duas camadas.

APK de teste: `Noise.V1.24.apk`. Validar em aparelho real e executar testes em emulador antes de distribuir como release definitiva.
