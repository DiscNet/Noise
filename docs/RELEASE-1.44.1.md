# Noise! V1.44.1 — Correção de estabilidade no Advanced Studio

## Correções
- Elimina reconstruções síncronas do painel de propriedades durante callbacks de sliders e digitação numérica; as atualizações passam a ser coalescidas na thread principal, fora da interação ativa.
- Protege contra reentrada do método que recria os controles, evitando que o teclado e os campos de precisão sejam destruídos enquanto estão processando uma alteração.
- Limita a frequência de recomposição de filtros e efeitos durante arrastes, preservando a aplicação obrigatória do valor final ao soltar o controle.
- Isola falhas de desenho de camadas individuais e registra a exceção no Logcat com a etiqueta `NoiseStudio`, preservando a edição das demais camadas.
- Interrompe callbacks pendentes de atualização do painel quando o Advanced Studio é fechado.

## Validação
- Introduz teste de interação real com painel de texto, ajuste de parâmetro, painel de sombra, atualização assíncrona e salvamento automático após alguns segundos.
- A build oficial é publicada somente após compilação, testes instrumentados e verificação da assinatura no GitHub Actions.

## Compatibilidade
- Sem alteração no formato nativo `.noise` v4.
- Mantém as funcionalidades da V1.44.0 e a chave de assinatura permanente.

## Diagnóstico
O fechamento observado em um dispositivo específico não pode ser diagnosticado definitivamente sem o stack trace da falha. O hotfix endereça problemas de reentrância e carga no caminho de edição recentemente alterado. Se continuar fechando, o Logcat do dispositivo, especialmente mensagens `FATAL EXCEPTION`, `OutOfMemoryError`, `ANR` ou `NoiseStudio`, deve orientar o próximo conserto.
