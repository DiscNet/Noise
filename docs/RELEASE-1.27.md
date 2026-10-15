# Noise! V1.27 — Corrigida a orientação horizontal das rodas

- **Ferramentas permanece na direita** e **Efeitos permanece na esquerda**, com abertura somente pelas bordas correspondentes.
- Corrigido o sentido do arco: a **roda de Ferramentas é convexa para a direita**, e a **roda de Efeitos é convexa para a esquerda**. Em ambas, os itens superiores e inferiores curvam-se para a borda correta, sem inverter horizontalmente o formato da roda.
- O centro selecionado permanece dentro do lado correspondente; raio responsivo evita cortes abruptos em celulares estreitos.
- Mantida a trajetória circular real, sem limitar artificialmente coordenadas horizontais. Os itens junto às bordas desaparecem gradualmente.
- Preservados o scroll e giro orbital com mola criticamente amortecida, entrada/saída independentes do dedo e fade in/out do fundo escuro e gradiente.
- Testes Android foram atualizados para exigir convexidade horizontal correta nas duas rodas, além de verificar a localização dos painéis.

Prévia: `Noise.V1.27.apk`. Executar testes funcionais em emulador e validar visualmente em dispositivo físico antes da publicação definitiva.
