# Noise! 1.14 — validação e limites

## Critério de liberação

A versão **não deve ser integrada nem publicada** enquanto a compilação, instrumentação Android e testes GPU apresentarem falhas. Um build Gradle bem-sucedido, isoladamente, não comprova a funcionalidade dos shaders ou controles.

## Execuções verificadas

| Execução | Resultado | Evidência |
|---|---|---|
| PR #13, commit `05a51aa`, Android API 30 (13/10/2026) | Compilação de `assembleDebug` e `assembleDebugAndroidTest` aprovada; instrumentação reprovada | [Run 38410657001](https://github.com/DiscNet/Noise/actions/runs/38410657001) |
| Correção `4e88b2a` | O teste deixava `effectsImage` ser reciclado antes de criar a imagem de validação de 60 controles; substituído por fixture independente | [Commit](https://github.com/DiscNet/Noise/commit/4e88b2ac393be8c3df9dcef8e65c6f1b17cb8cf9) |

A instrumentação da primeira execução alcançou as etapas de shaders, efeitos artísticos, Glow 200%, ASCII e paleta Nonqual antes da falha no próprio teste em `NoiseSmokeTest.java:322`: `IllegalArgumentException: cannot use a recycled source in createBitmap`. Essas etapas teriam avançado, mas o resultado **geral foi falha**, e as verificações seguintes não foram executadas.

**Estado da validação atual:** consulte a aba [Actions](https://github.com/DiscNet/Noise/actions/workflows/android.yml) e confirme a última execução para o commit final do PR. Não inferir aprovação de builds anteriores.

## Matriz de cobertura automatizada

O arquivo `NoiseSmokeTest.java` procura validar:
- Início de Activity, leitura/escrita e bypass da GPU.
- Parâmetros de cor, tonalidade, shaders artísticos, glow e ASCII/Nonqual.
- Histórico agrupado, persistência de documentos e presets.
- 60 controles, Bayer, threshold e dependência do ASCII em ajustes tonais.
- Recorte não destrutivo, rotação e dimensões.
- PNG/JPEG, caminho público MediaStore, igualdade amostral da imagem exportada.
- Abas, tamanho do painel e canvas em 320×568, 800×360 e 800×1100.
- Prévia atualizada durante gesto, falha de arquivo e restauração após recriar Activity.

**Importante:** estar listado no teste não significa aprovação até existir execução verde `NOISE_SMOKE_OK` do commit final. A verificação automatizada não substitui inspeção de layout real nem teste em aparelhos físicos.

## Limitações reais

- Preview com bitmap reduzido e exportação máxima de 4096 px no lado maior ou menos, conforme a GPU; ambos podem divergir em frequências espaciais finas.
- Sem máscaras locais, curvas RGB, Floyd–Steinberg, Atkinson ou histogramas interativos.
- Projetos e presets locais são removidos ao desinstalar o aplicativo; exportações públicas permanecem.
- Não houve validação em aparelhos reais ou em múltiplas GPUs/versões Android nesta entrega.
- A precisão de qualquer benchmark de FPS depende de amostragem repetida, características da imagem e dispositivo; o teste de frames durante o gesto verifica atividade da renderização, **não garante 60 FPS**.
- Cor, desempenho e memória em documentos grandes precisam de testes manuais extensivos antes de reivindicar estabilidade universal.
