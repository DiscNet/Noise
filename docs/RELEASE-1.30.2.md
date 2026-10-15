# Noise! V1.30.2 — Presets limpos, círculo e assinatura persistente

## Presets de câmera sem ruído
- Todos os 13 presets da seção **Câmeras & Filmes** passam a ter intensidade **zero** para Ruído, Poeira, Granulação e Textura de filme (`values[7]`, `[14]`, `[54]` e `[65]`).
- A cor, a resposta tonal de película, a saturação, o halation e os vazamentos de luz permanecem aplicados. O preset `Cobre R · Rich Film` mantém cores quentes e halation visíveis, **sem grão gerado**.
- `Filme suave` e `Prata` também não recebem ruído adicional.
- Os controles manuais de grão/ruído continuam disponíveis para quem optar por ativá-los. Projetos e presets personalizados existentes não são regravados retroativamente.

## Tela inicial
- O elemento central voltou a ser um **círculo perfeito** com luz e brilho suave animados.
- Removidos o formato quadrado arredondado, os traçados pontilhados e os contornos decorativos internos.
- Permanece **uma única borda contínua** com halo sutil.

## Atualização de APK no Android
- `versionCode 36` e `versionName 1.30.2`, com `applicationId=com.discnet.noise` inalterado.
- O workflow agora suporta uma **chave de assinatura permanente** via GitHub Actions Repository Secrets, evitando certificados de debug diferentes a cada compilação.
- Sem as quatro secrets configuradas, a prévia continua usando a assinatura temporária de debug; ela **não pode prometer** a opção Atualizar sobre instalações anteriores.
- Releases públicas na branch `main` passam a exigir assinatura permanente; se faltar, a publicação falha de maneira explícita.
- Leia [APK-UPDATES.md](APK-UPDATES.md) para configurar a assinatura uma única vez e migrar instalações antigas. A versão anterior pode exigir uma única desinstalação devido a certificados historicamente diferentes — faça backup dos projetos antes.

## Status
Prévia de desenvolvimento. Build e testes em emulador devem ser concluídos antes de publicar no GitHub Releases. Não há teste físico declarado.
