# Atualizações do APK sem desinstalar

No Android, **não basta ter o mesmo nome de arquivo ou o mesmo identificador**. O botão **Atualizar** aparece apenas quando o APK novo possui:
1. o mesmo `applicationId` (`com.discnet.noise`);
2. a **mesma chave de assinatura** do APK já instalado;
3. `versionCode` superior ao instalado.

## Situação histórica
Os APKs de teste anteriores do Noise! receberam certificados de debug **diferentes**. Uma assinatura nova não pode substituir por atualização uma instalação assinada com uma chave diferente. Isso é uma regra de segurança do Android; não é possível corrigir com uma alteração de layout ou nas propriedades do aplicativo.

## Configuração única de uma chave permanente no GitHub

Crie uma keystore em uma máquina confiável e guarde um backup **fora do repositório**. Por exemplo:

```sh
keytool -genkeypair -v -keystore noise-release.jks -alias noise -keyalg RSA -keysize 3072 -validity 10000
```

Escolha senhas fortes e preserve-as. Nunca publique o arquivo `.jks` ou suas senhas no código, no README ou em issues.

No GitHub, abra **Settings → Secrets and variables → Actions** e crie quatro **Repository secrets**:
- `NOISE_SIGNING_KEYSTORE_B64`: representação Base64 do arquivo **binário** `noise-release.jks`;
- `NOISE_SIGNING_STORE_PASSWORD`: senha do arquivo JKS;
- `NOISE_SIGNING_KEY_ALIAS`: `noise` (ou o alias que você escolheu);
- `NOISE_SIGNING_KEY_PASSWORD`: senha da chave.

Em PowerShell no Windows, para obter a representação Base64 do arquivo local:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path '.\noise-release.jks')))
```

Depois de configurar os quatro segredos, execute o workflow **Build Noise APK**. Ele decodifica a chave em diretório temporário do runner, assina a build e não publica a chave. Não use credenciais de repositórios de terceiros.

O workflow em `.github/workflows/android.yml` **bloqueia releases oficiais sem chave permanente**. Na branch de desenvolvimento, a ausência dos segredos ainda permite um APK de prévia, mas ele é temporário e **não possui garantia de atualização in-place**.

## Migração de instalações antigas
1. Antes de remover o Noise! atual, use **Meus Projetos → Proteger projetos após desinstalar**, selecione a pasta compartilhada e confirme seus arquivos. Sem backup, a desinstalação apaga a pasta privada do aplicativo.
2. Configure a chave permanente e baixe o primeiro APK assinado por ela.
3. **Uma única desinstalação pode ser necessária**, porque as versões antigas já estão assinadas com outros certificados.
4. Instale o APK permanente. A partir dessa instalação, versões posteriores com o **mesmo certificado** e `versionCode` maior permitirão **Atualizar**, preservando os dados do app.
5. Para restaurar projetos, abra **Meus Projetos** e selecione novamente a pasta de backup original.

Não é necessário desinstalar para cada atualização futura após fixar a chave. **Nunca perca o arquivo JKS original e suas senhas**: uma chave diferente volta a impedir a atualização direta.
