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
- O APK oficial `Noise.V1.30.2.apk` usa a variante **release, sem depuração**, e a keystore permanente configurada nos quatro GitHub Actions Secrets.
- O workflow exige testes instrumentados aprovados, assinatura válida e certificado correspondente à keystore antes da publicação. O SHA-256 do APK e a impressão digital pública do certificado acompanham o download.
- APKs com sufixo `-preview.apk` usam certificado temporário de debug e não fazem parte da linha oficial de atualização.
- Leia [o guia de assinatura e migração](https://github.com/DiscNet/Noise/blob/main/docs/APK-UPDATES.md). Instalações antigas com outro certificado podem exigir uma única desinstalação — faça backup dos projetos antes.

## Validação e publicação
Esta é a nota preparada para a Release `v1.30.2`. A publicação só ocorre quando os quatro Secrets estão configurados e as verificações do workflow terminam com sucesso; a existência deste arquivo não significa que a versão já foi publicada.

O workflow testa os presets e o processamento GPU em um emulador Android usando a variante debug do mesmo código, compila a variante release e verifica sua assinatura, identificador, versão e ausência de depuração. Não há teste físico declarado.
