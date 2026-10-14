# Noise! V1.18 — Gestos independentes e motion design amortecido

- Uma passada para a esquerda iniciada **fora da faixa extrema direita** abre **Ferramentas**.
- Uma passada da **borda direita (24% finais da tela)** para a esquerda abre **Efeitos diretamente**, sem abrir Ferramentas antes.
- Repetir o mesmo gesto sobre uma roda aberta **não alterna** para a outra. Arrastar para a direita fecha a roda e retorna ao editor.
- Removido o acoplamento 1:1 entre dedo e camada visual: translação/opacidade seguem o gesto com resposta exponencial amortecida de cerca de 108ms, independente da taxa de atualização.
- Finalização e cancelamento com desaceleração e easing entre 310–480ms. Sem saltos, elasticidade excessiva ou delay artificial bloqueando comandos.
- Mantidos preview GPU, ajustes, exportação, comparação, zoom e correção da curva Brancos.
- Testes de instrumentação incluem gestos por origem, abertura direta de Efeitos, ausência de troca sequencial e revelação progressiva.

Prévia: `Noise.V1.18.apk`; distribuir como build de teste até que a suíte do emulador conclua.
