# Noise! V1.54.0 — Ordem dos estilos e guias pelos pixels

## Inspiração técnica
Referência de arquitetura: `storytold/photocraft`, especialmente `crates/compose/src/effects.rs` e `crates/doc/src/effects.rs`. Esta é uma implementação Android independente, não a cópia integral do código Rust nem garantia de paridade perfeita com Photoshop.

## Composição e camada
- Corrigida a ordem de pintura: sobreposições de padrão, degradê e cor são aplicadas como **preenchimento da camada**, antes da iluminação e do relevo.
- O contorno central conserva suas metades interna/externa, pintando a interna por cima do degradê e do bisel. Contorno não desaparece quando usado simultaneamente com degradê.
- No caso de Bisel/Relevo, o preenchimento é aplicado na **mesma máscara do glifo em alta resolução**, antes do cálculo de luz e sombra, evitando que um segundo desenho encubra ou duplique as bordas da letra.
- Modos de mesclagem individuais de sobreposição continuam preservados; caches incluem os parâmetros das sobreposições.
- As duas rotas de renderização, nativa e por software para modos de mesclagem especiais, seguem a mesma ordem de pintura.

## Alinhamento e movimento
- As réguas inteligentes passam a utilizar os limites de **pixels opacos reais** (alfa maior que 8), em vez da largura/altura total da imagem e das margens tipográficas transparentes.
- Respeitam máscara raster, densidade, escala, rotação e inclinação da camada.
- Cache de limites alfa por conteúdo/geração do bitmap para evitar varredura durante cada deslocamento.
- Arrasto calculado de forma absoluta a partir do ponto inicial do gesto; **não realimenta deslocamentos já ajustados pelo snap**, impedindo aprisionamento em guias.
- Limiar de alinhamento reduzido de 7 dp para 3,5 dp, sem impedir que a camada passe pela guia.
- Testes instrumentados cobrem tamanho visual, máscara e ordenação de estilos com degradê, contorno central e bisel.

## Compatibilidade
- Projetos `.noise` preservam o formato atual e os mesmos parâmetros; a mudança afeta a composição e a interface de movimento, não altera dados existentes.
- O alvo é manter estabilidade e boa resposta em dispositivos Android de memória restrita.
