# Assinatura permanente e atualizações do Noise!

O APK oficial usa `applicationId=com.discnet.noise`. Para manter as atualizações, preserve **a mesma chave de assinatura** e aumente o `versionCode` em cada nova versão. A V1.30.2 tem `versionCode 36`. O nome do arquivo ou uma senha igual não recriam a mesma chave.

A configuração abaixo é feita **uma única vez**, em um dispositivo confiável (Android ou computador). Não envie a keystore, seu Base64 ou senhas para o chat, commits, issues, logs ou Releases. O GitHub guarda os quatro valores como **Repository Secrets do Actions**. Não são Variables nem Secrets do Dependabot ou Codespaces.


## Android com Termux

Todo o processo pode ser feito no celular, sem computador ou root. A keystore é criada no armazenamento privado do Termux; a compilação do APK continua no GitHub Actions.

### A. Preparar o Termux

Instale o [Termux pelo F-Droid](https://f-droid.org/packages/com.termux/), fonte indicada pelo projeto. Se já usa o Termux, preserve seus arquivos e não o desinstale para seguir este guia.

Abra o Termux e execute:

```bash
pkg update && pkg install openjdk-21 gh
```

Confirme a instalação quando solicitado. O OpenJDK fornece o `keytool`; o pacote `gh` é o GitHub CLI. O Java 21 é usado apenas para criar a chave no celular; o build do projeto continua usando Java 17 no Actions.

### B. Criar a chave uma única vez

Cole este bloco no Termux:

```bash
umask 077
mkdir -p "$HOME/Noise-Signing"
if [ -e "$HOME/Noise-Signing/noise-release.p12" ]; then
  printf '%s\n' 'A keystore já existe. Preserve o arquivo e use a senha original.'
else
  keytool -genkeypair -v -storetype PKCS12 \
    -keystore "$HOME/Noise-Signing/noise-release.p12" \
    -alias noise -keyalg RSA -keysize 3072 -sigalg SHA256withRSA \
    -validity 36500 -dname "CN=Noise, O=DiscNet, C=BR"
fi
```

Crie e confirme uma senha forte quando o `keytool` pedir. A digitação pode ficar invisível no terminal; isso é normal. Guarde a senha no seu gerenciador de senhas. Neste formato PKCS12, os dois Secrets de senha recebem **a mesma senha**. Se o comando falhar, resolva o erro antes de continuar; não apague uma chave existente para tentar novamente.

### C. Fazer backup antes de publicar

A desinstalação do Termux ou a limpeza de seus dados pode apagar a chave. Para disponibilizar uma cópia do arquivo protegido por senha no aplicativo Arquivos, execute:

```bash
termux-setup-storage
```

Autorize o acesso ao armazenamento na solicitação do Android. Depois, execute:

```bash
cp -i "$HOME/Noise-Signing/noise-release.p12" "$HOME/storage/downloads/Noise-release-backup.p12"
```

No aplicativo Arquivos, procure **Downloads → Noise-release-backup.p12**. Guarde cópias em **dois locais protegidos e independentes do celular**, como um cofre criptografado e um dispositivo USB protegido. Preserve também a senha no gerenciador de senhas. A pasta Downloads serve apenas como passagem; retire a cópia dali depois de conferir os backups.

Para conferir uma cópia retornada do backup, use `keytool -list -v -keystore CAMINHO_DA_COPIA -alias noise` e digite a senha quando solicitado. Compare a impressão SHA-256 com a do original. Não envie esse arquivo ao chat ou ao repositório.

### D. Conectar sua conta GitHub

No Termux:

```bash
gh auth login --hostname github.com --git-protocol https --web
```

Siga o login pelo navegador com sua conta **DiscNet**. Se o navegador não abrir automaticamente, abra no Chrome o endereço de autorização exibido pelo próprio GitHub CLI e informe o código gerado. Volte ao Termux quando terminar. Não envie o código de login ou tokens a outras pessoas.

### E. Cadastrar os quatro Secrets

Execute **um comando por vez**, esperando o resultado. O Base64 vai direto do arquivo para o GitHub CLI, sem ser impresso nem copiado para a área de transferência:

```bash
base64 -w 0 "$HOME/Noise-Signing/noise-release.p12" | gh secret set NOISE_SIGNING_KEYSTORE_B64 --repo DiscNet/Noise --app actions
```

```bash
printf '%s' 'noise' | gh secret set NOISE_SIGNING_KEY_ALIAS --repo DiscNet/Noise --app actions
```

```bash
gh secret set NOISE_SIGNING_STORE_PASSWORD --repo DiscNet/Noise --app actions
```

O comando acima pede a senha de forma interativa. Informe a senha escolhida ao criar a keystore, aguarde a confirmação e execute:

```bash
gh secret set NOISE_SIGNING_KEY_PASSWORD --repo DiscNet/Noise --app actions
```

Informe **a mesma senha** novamente. Não coloque senhas no comando com `--body`; o GitHub CLI as recebe na entrada interativa e criptografa os valores antes de enviá-los ao GitHub. Se qualquer comando falhar, não inicie a publicação até corrigir o erro.

Confira os quatro nomes, sem mostrar valores:

```bash
gh secret list --repo DiscNet/Noise --app actions
```

### F. Publicar a V1.30.2

Com os backups conferidos e os quatro Secrets cadastrados, execute:

```bash
gh workflow run android.yml --ref main --repo DiscNet/Noise
```

Acompanhe em [Actions → Build Noise APK](https://github.com/DiscNet/Noise/actions/workflows/android.yml). O workflow testa, assina e publica `Noise.V1.30.2.apk` em Releases; não substitui uma Release que já exista.

Você também pode avisar ao assistente apenas **“Secrets configurados”**, sem valores, para ele acompanhar e prosseguir com a publicação.

Após concluir, se não precisar mais da sessão GitHub no Termux, use `gh auth logout --hostname github.com`. Preserve a keystore e seus backups.


## 1. Gerar a keystore no Windows

É necessário um JDK com `keytool` disponível (o projeto usa Java 17). Abra o **PowerShell**. Se `keytool` não for reconhecido, configure o `bin` do JDK no PATH e reabra o terminal.

Se você já possui uma keystore permanente usada em versões distribuídas, **reutilize esse arquivo e suas credenciais**, em vez de criar outra. As prévias históricas do Noise! usaram certificados temporários; uma nova chave não recupera essas assinaturas.

Para criar a primeira chave permanente, execute:

```powershell
$NoiseKeys = Join-Path $env:USERPROFILE 'Noise-Signing'
$NoiseKeystore = Join-Path $NoiseKeys 'noise-release.p12'
New-Item -ItemType Directory -Force -Path $NoiseKeys | Out-Null
if (Test-Path $NoiseKeystore) { throw 'A keystore já existe. Preserve-a; não gere outra.' }
keytool -genkeypair -v -storetype PKCS12 -keystore "$NoiseKeystore" -alias noise -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 36500 -dname "CN=Noise, O=DiscNet, C=BR"
if ($LASTEXITCODE -ne 0) { throw 'A geração falhou. Não prossiga.' }
```

- Crie e confirme uma senha forte e exclusiva, preferencialmente gerada por um gerenciador de senhas. Digite-a **somente quando o keytool pedir**. Não acrescente a senha ao comando.
- Neste formato **PKCS12**, use a **mesma senha** nos dois Secrets de senha. O alias é `noise`.
- A validade é de 36.500 dias, aproximadamente 100 anos. “Permanente” significa reutilizar a identidade por toda a vida do app, não um certificado sem expiração.
- `CN=Noise, O=DiscNet, C=BR` é informação pública do certificado. A chave privada e a senha não fazem parte desses dados.
- O arquivo fica em `%USERPROFILE%\Noise-Signing\noise-release.p12`, fora da pasta do código.

## 2. Guardar e conferir o backup

Antes de publicar:

1. Guarde duas cópias da keystore em locais protegidos e independentes, por exemplo um dispositivo externo criptografado e um cofre de backup criptografado. Não dependa apenas do disco do computador ou dos Secrets do GitHub.
2. Guarde a senha e o alias `noise` no seu gerenciador de senhas. Anote que o arquivo pertence ao Noise!, pacote `com.discnet.noise`.
3. Confira uma das cópias com o comando abaixo, substituindo o caminho pelo local real do backup. Ele pede a senha e mostra apenas dados públicos do certificado:

```powershell
keytool -list -v -keystore 'E:\Backup-Noise\noise-release.p12' -alias noise
```

Compare a impressão digital SHA-256 com a do original. Preserve também esse valor público como registro da identidade. Nunca substitua a keystore por outra para “corrigir” um erro de senha. Se a chave privada for perdida, não será possível continuar assinando atualizações comuns dessa instalação.

## 3. Cadastrar os quatro Repository Secrets

Abra [Settings → Secrets and variables → Actions](https://github.com/DiscNet/Noise/settings/secrets/actions) e use **New repository secret** para cada linha:

| Nome exato | Valor |
| --- | --- |
| `NOISE_SIGNING_KEYSTORE_B64` | Base64 do arquivo binário `noise-release.p12` |
| `NOISE_SIGNING_STORE_PASSWORD` | Senha escolhida na geração |
| `NOISE_SIGNING_KEY_ALIAS` | `noise` |
| `NOISE_SIGNING_KEY_PASSWORD` | A mesma senha, para a keystore PKCS12 criada acima |

Se estiver reutilizando uma keystore JKS, use suas senhas e alias originais; a senha da chave pode ser diferente da senha do arquivo.

Para copiar **somente o Base64** para a área de transferência, sem imprimi-lo no terminal, execute:

```powershell
$NoiseKeystore = Join-Path $env:USERPROFILE 'Noise-Signing\noise-release.p12'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($NoiseKeystore)) | Set-Clipboard
```

Cole no campo **Secret** de `NOISE_SIGNING_KEYSTORE_B64` e salve. Depois limpe a área de transferência:

```powershell
Set-Clipboard -Value ''
```

O Base64 contém a keystore inteira e **não é criptografia**. Não o cole em conversas nem use conversores online. Desative o histórico/sincronização da área de transferência antes de copiar, ou use a alternativa CLI abaixo.

### Alternativa: GitHub CLI

Com o GitHub CLI instalado e autenticado pelo fluxo oficial `gh auth login`, execute no seu computador:

```powershell
$NoiseKeystore = Join-Path $env:USERPROFILE 'Noise-Signing\noise-release.p12'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($NoiseKeystore)) | gh secret set NOISE_SIGNING_KEYSTORE_B64 --repo DiscNet/Noise --app actions
if ($LASTEXITCODE -ne 0) { throw 'Falha ao cadastrar a keystore.' }
'noise' | gh secret set NOISE_SIGNING_KEY_ALIAS --repo DiscNet/Noise --app actions
if ($LASTEXITCODE -ne 0) { throw 'Falha ao cadastrar o alias.' }
gh secret set NOISE_SIGNING_STORE_PASSWORD --repo DiscNet/Noise --app actions
if ($LASTEXITCODE -ne 0) { throw 'Falha ao cadastrar a senha do arquivo.' }
gh secret set NOISE_SIGNING_KEY_PASSWORD --repo DiscNet/Noise --app actions
if ($LASTEXITCODE -ne 0) { throw 'Falha ao cadastrar a senha da chave.' }
gh secret list --repo DiscNet/Noise --app actions
```

Os dois comandos de senha abrem entrada interativa; não use `--body SUA_SENHA` nem escreva senhas em arquivos de configuração. A listagem final mostra os nomes, não os valores. Não é necessário criar um quinto Secret para publicar: o workflow usa o `GITHUB_TOKEN` fornecido automaticamente pelo Actions.

## 4. Publicar oficialmente a V1.30.2

Depois de guardar o backup e cadastrar os quatro Secrets:

1. Abra [Actions → Build Noise APK](https://github.com/DiscNet/Noise/actions/workflows/android.yml).
2. Clique em **Run workflow**, escolha **main** e confirme **Run workflow**. Inicie uma execução nova para usar o workflow atualizado; não reexecute a execução antiga de antes desta configuração.
3. Aguarde os jobs de build/testes e **Publish official signed APK**. O segundo só roda na `main`, após o primeiro terminar com sucesso.
4. A publicação cria a tag **`v1.30.2`**, título **Noise! V1.30.2**, APK **`Noise.V1.30.2.apk`**, seu checksum SHA-256 e **`SIGNING-CERTIFICATE.txt`** com a impressão digital pública do certificado.

Também é possível iniciar pela CLI:

```powershell
gh workflow run android.yml --ref main --repo DiscNet/Noise
```

Salvar Secrets, por si só, **não inicia uma execução**. A publicação também acontece em novos pushes na `main` após os testes, desde que a versão ainda não exista em Releases. O fluxo nunca sobrescreve uma Release existente; para uma nova versão, aumente `versionName` e `versionCode` e prepare `docs/RELEASE-<versão>.md`.

## O que o workflow protege

- Pull requests e prévias não recebem a keystore permanente; usam uma chave de debug temporária.
- O CI testa a configuração de release com uma chave descartável de teste, que não é usada em Releases nem nos APKs oficiais.
- O job oficial exige os quatro Secrets e não tem fallback para debug.
- A keystore é decodificada em diretório temporário com permissões restritas e removida ao terminar, inclusive em caso de falha normal. Senhas não são gravadas no código nem no `GITHUB_ENV`.
- O build de release não usa cache do Gradle ou cache de configuração. Apenas APK, checksum e dados públicos do certificado são enviados como artefatos oficiais.
- `apksigner` verifica a assinatura e o certificado é comparado com o da keystore. O pacote, a versão e a ausência de depuração também são conferidos antes de publicar.
- Somente o job oficial tem permissão de escrita em Releases; a tag aponta para o commit testado.

## Migração de instalações antigas

1. Antes de remover uma instalação antiga, use **Meus Projetos → Proteger projetos após desinstalar**, selecione a pasta compartilhada e confira os arquivos. A desinstalação apaga os dados privados do aplicativo.
2. Baixe o APK oficial em Releases. Uma instalação antiga assinada com outro certificado pode exigir **uma única desinstalação** para migrar.
3. Instale a primeira versão permanente e restaure seus projetos pela pasta de backup.
4. Nas próximas versões oficiais, a mesma assinatura e um `versionCode` maior permitirão atualizar preservando os dados do app. Não troque a keystore entre versões.

## Referências

- [Assinatura de apps Android](https://developer.android.com/studio/publish/app-signing)
- [Secrets no GitHub Actions](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [GitHub CLI: gh secret set](https://cli.github.com/manual/gh_secret_set)
- [Java 17: keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)

- [Termux: instalação](https://github.com/termux/termux-app#installation)
- [GitHub CLI no Android](https://github.com/cli/cli/blob/trunk/docs/install_linux.md#android)
