# Ordo

Ordo é um aplicativo Android nativo, local-first, para planejar, executar e acompanhar treinos de musculação. Os dados ficam no próprio telefone, sem conta, servidor ou sincronização obrigatória.

## Status atual

- Versão: `0.2.0`
- `minSdk`: 28 (Android 9 ou superior)
- `targetSdk`: 36
- Pacote release: `com.vitorkrichter.ordo`
- Pacote debug: `com.vitorkrichter.ordo.debug`
- Plataforma principal: Android

## O que o app faz

### Primeiro acesso

No primeiro uso, o Ordo oferece duas opções:

1. Criar um plano editável de exemplo.
2. Importar um plano usando o formato `GYMAPP_IMPORT_V1`.

Depois que existe um plano, o app abre diretamente a tela **Hoje** nos próximos acessos.

### Hoje

- Mostra o treino planejado para o dia.
- Mostra o próximo treino da sequência quando o plano usa sequência livre.
- Permite iniciar o treino.
- Permite registrar uma sessão completa ou parcial.
- Exibe um resumo do histórico recente e dos treinos concluídos.

### Treinos

- Lista os treinos do plano ativo.
- Permite criar um novo treino.
- Edita nome, exercícios, séries, repetições e descansos.
- Adiciona, remove e reordena exercícios.
- Reordena os treinos do plano.
- Renomeia, duplica, arquiva e ativa planos.
- Abre a tela de importação e gerenciamento de planos.

### Execução do treino

- Mostra os exercícios na sequência configurada.
- Marca séries concluídas e permite desfazer uma marcação.
- Registra repetições e carga quando o exercício usa acompanhamento.
- Mostra o desempenho anterior quando disponível.
- Permite reorganizar exercícios durante a sessão.
- Possui cronômetro de descanso com ajustes de `+15s`, `-15s` e opção de pular.
- Envia uma notificação quando o descanso termina, conforme a permissão de notificações do Android.
- Mantém um rascunho local da sessão em andamento.

### Histórico

- Lista sessões completas, parciais, canceladas e em andamento.
- Abre os detalhes de cada treino realizado.
- Permite corrigir carga e repetições de uma série.
- Mostra duração e desempenho registrado.
- Exporta o histórico usando o seletor de arquivos do Android.

### Progresso

Apresenta, por exercício e período registrado:

- quantidade de sessões;
- maior carga;
- maior número de repetições;
- volume total registrado.

### Meu perfil

- Salva nome, data de nascimento e altura, todos opcionais.
- Mantém histórico de peso com data e horário.
- Permite editar e remover medições de peso.
- Calcula IMC adulto como referência quando há dados suficientes.
- Calcula uma meta de hidratação estimada a partir do peso ou aceita uma meta manual.

O IMC é apenas uma referência de triagem, não um diagnóstico. A hidratação é uma meta de lembrete: o app não registra copos nem consumo diário.

### Importação e exportação

Na área **Importar**, o usuário pode:

- colar um treino e revisar os erros antes de salvar;
- importar um plano com vários treinos;
- escolher os dias de treino;
- exportar o plano ativo em TXT;
- criar um backup completo restaurável;
- restaurar um backup existente;
- duplicar, renomear, ativar e arquivar planos.

O backup completo contém os dados locais do Ordo, incluindo planos, exercícios, agenda, sessões, histórico de peso e configurações de perfil. O arquivo é validado antes da restauração e o app reinicia para carregar o banco restaurado.

## Como usar

### 1. Criar um plano rapidamente

1. Abra o Ordo.
2. Toque em **Criar plano editável**.
3. Entre em **Treinos**.
4. Edite o nome dos treinos e configure exercícios, séries, repetições e descansos.
5. Volte para **Hoje** e inicie o treino planejado.

### 2. Importar um treino

1. No onboarding, toque em **Importar meu treino**; ou abra **Treinos > Gerenciar planos e importar**.
2. Cole um texto no formato abaixo.
3. Toque em **Revisar treino**.
4. Corrija os erros apontados, se houver.
5. Confirme os dias de treino e toque em **Salvar plano importado**.

### 3. Registrar um treino

1. Na tela **Hoje**, toque em **Abrir treino**.
2. Marque cada série conforme ela for concluída.
3. Preencha e confirme carga e repetições quando o exercício exigir acompanhamento.
4. Use o cronômetro de descanso entre as séries.
5. Finalize como **Treino completo** ou **Treino parcial**.

### 4. Fazer backup

1. Abra **Treinos > Gerenciar planos e importar**.
2. Na seção **Backup dos dados**, toque em **Criar backup completo**.
3. Escolha uma pasta no seletor de arquivos do Android.
4. Para restaurar, toque em **Restaurar backup** e selecione um arquivo válido do Ordo.

Faça um backup antes de trocar de telefone, limpar os dados do app ou instalar uma versão experimental.

## Formato `GYMAPP_IMPORT_V1`

O cabeçalho deve ser a primeira linha útil do arquivo. Campos antes do primeiro treino definem o plano; cada bloco `[TREINO ...]` define um treino.

### Campos

| Campo | Obrigatório | Descrição |
| --- | --- | --- |
| `# GYMAPP_IMPORT_V1` | Sim | Identifica o formato do arquivo. |
| `NOME_PLANO` | Não | Nome do plano; se faltar, o app pede um nome. |
| `DIAS_TREINO` | Não | Dias separados por vírgula ou ponto e vírgula. |
| `[TREINO A]` | Sim | Inicia um treino e define seu código. |
| `NOME` | Sim | Nome do plano ou do treino, conforme o bloco. |
| `EXERCICIO` | Sim | Nome do exercício. |
| `SERIES` | Sim | Quantidade de séries, maior que zero. |
| `REPETICOES` | Sim | Número, faixa ou um valor por série. |
| `DESCANSO_SERIES` | Sim | Descanso entre séries. |
| `DESCANSO_PROXIMO` | Não | Descanso antes do próximo exercício; padrão `0s`. |
| `ACOMPANHAMENTO` | Não | `NENHUM`, `REPETICOES` ou `CARGA_REPETICOES`. |
| `CARGA_SUGERIDA` | Não | Carga inicial sugerida em kg. |
| `OBSERVACAO` | Não | Observação livre sobre o exercício. |

Os dias aceitos são `SEGUNDA`, `TERCA`, `QUARTA`, `QUINTA`, `SEXTA`, `SABADO` e `DOMINGO`, além das formas abreviadas. O parser também aceita acentos nos nomes dos dias.

Para duração, podem ser usados segundos (`90` ou `90s`), minutos e segundos (`1m30s`) ou relógio (`01:30`). Para repetições, use um valor (`10`), uma faixa (`8-12`) ou um valor para cada série (`8,10,12`).

### Exemplo completo

```text
# GYMAPP_IMPORT_V1

NOME_PLANO=Hipertrofia 3 dias
DIAS_TREINO=SEGUNDA,QUARTA,SEXTA

[TREINO A]
NOME=Pernas

EXERCICIO=Agachamento no Rack
SERIES=3
REPETICOES=8-12
DESCANSO_SERIES=90s
DESCANSO_PROXIMO=120s
ACOMPANHAMENTO=CARGA_REPETICOES
CARGA_SUGERIDA=60
OBSERVACAO=Manter execução controlada
-------------------------------

EXERCICIO=Leg Press
SERIES=3
REPETICOES=10,10,12
DESCANSO_SERIES=90s
DESCANSO_PROXIMO=0s
ACOMPANHAMENTO=CARGA_REPETICOES
-------------------------------

[TREINO B]
NOME=Peito e tríceps

EXERCICIO=Supino reto
SERIES=4
REPETICOES=8-10
DESCANSO_SERIES=2m
ACOMPANHAMENTO=REPETICOES
-------------------------------
```

O app mostra uma prévia com treinos, exercícios, avisos e erros antes de gravar qualquer coisa.

## Instalação no telefone

### Pelo script do Windows

Pré-requisitos:

- Android SDK instalado;
- `adb` disponível no SDK Platform-Tools;
- telefone conectado por USB;
- Depuração USB ativada;
- autorização da chave de depuração aceita no telefone.

Na raiz do projeto, execute:

```text
instalar_ordo_debug.bat
```

Esse script compila e instala a versão debug. Para instalar a versão release assinada, use:

```text
instalar_ordo_release.bat
```

Os scripts foram preparados para o SDK em `C:\Users\Usuario\AppData\Local\Android\Sdk`. Se o SDK estiver em outro local, altere a variável `ADB` nos dois arquivos `.bat`.

### Pela linha de comando

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Para release:

```powershell
.\gradlew.bat :app:assembleRelease
adb install -r .\app\build\outputs\apk\release\app-release.apk
```

Se houver uma versão debug instalada, ela usa outro application ID e pode coexistir com a release. A release usa `com.vitorkrichter.ordo`; a debug usa `com.vitorkrichter.ordo.debug`.

## Desenvolvimento local

### Requisitos

- Android Studio recente;
- Android SDK Platform 36;
- JDK 17 recomendado;
- Gradle Wrapper incluído no repositório;
- dispositivo Android API 28 ou superior, ou emulador.

### Abrir o projeto

1. Clone o repositório.
2. Abra a pasta no Android Studio.
3. Aguarde a sincronização do Gradle.
4. Deixe o Android Studio criar ou ajustar `local.properties` para o SDK local.
5. Execute o app pela configuração `app` ou use os comandos abaixo.

```powershell
git clone https://github.com/VitorKreis/Ordo.git
cd Ordo
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

### Build release e assinatura

O arquivo `signing.properties` não é versionado. Para gerar uma release assinada, crie esse arquivo na raiz do projeto com os dados da sua própria chave:

```properties
storeFile=keystore/arquivo-da-chave.jks
storePassword=SENHA_DO_KEYSTORE
keyAlias=ALIAS_DA_CHAVE
keyPassword=SENHA_DA_CHAVE
```

O arquivo da chave deve existir no caminho indicado. Nunca publique `signing.properties`, `.jks`, `.keystore`, senhas ou certificados privados. Esses arquivos já estão protegidos pelo `.gitignore`.

## Testes e CI

Testes locais:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

O workflow em `.github/workflows/ci.yml` executa, a cada push ou pull request:

- testes unitários JVM;
- build debug;
- build release;
- lint debug.

Os testes instrumentados existem no projeto, mas não são executados no CI padrão porque o workflow não provisiona um emulador Android nem depende de um telefone conectado.

## Arquitetura e tecnologias

- Kotlin;
- Jetpack Compose e Material 3;
- Navigation Compose;
- Room/SQLite para persistência local;
- Hilt para injeção de dependências;
- WorkManager para tarefas relacionadas ao cronômetro de descanso;
- DataStore para preferências locais;
- arquitetura organizada por domínio, dados, banco, navegação e funcionalidades.

O banco local possui schemas versionados em `app/schemas` e migrações testadas para preservar os dados durante atualizações.

## Privacidade e limitações atuais

- Não há login, conta de usuário, backend ou sincronização em nuvem.
- Os dados ficam no armazenamento privado do aplicativo até serem exportados pelo usuário.
- O backup é manual e deve ser guardado pelo usuário em local seguro.
- O IMC é uma referência, não uma avaliação médica.
- A hidratação é uma estimativa de lembrete; não é prescrição e não registra o consumo real.
- O app solicita notificações apenas para avisos do cronômetro de descanso.

## Repositório

- GitHub: <https://github.com/VitorKreis/Ordo>
- Branch principal: `main`
- Os artefatos gerados (`build/`, APKs, caches, chaves e configurações locais) não fazem parte do repositório.

## Licença

Ainda não foi definida uma licença de código aberto para o projeto.
