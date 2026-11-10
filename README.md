# Controle Parental — limite diário de tempo de tela (Android)

Aplicativo Android nativo (Kotlin, sem bibliotecas externas) que limita quanto tempo
por dia a tela do celular pode ficar em uso. Quando o tempo acaba, uma tela de bloqueio
cobre todos os apps até a meia-noite, ou até o responsável liberar mais tempo com o PIN.

## Funcionalidades

- Limite diário configurável: um valor de **segunda a sexta** e outro para **sábado e domingo**.
- Conta apenas o tempo com a **tela ligada**. A contagem zera automaticamente à meia-noite.
- Notificação fixa com o tempo restante, mais avisos quando faltam **5 minutos** e **1 minuto**.
- **Tela de bloqueio** sobre todos os apps quando o tempo acaba. Ela não sai com Voltar nem com Início.
- **PIN do responsável** (4 a 8 números, guardado com hash e sal). Ele libera +15 min, +30 min, +1 hora ou o resto do dia.
- Bloqueio por 60 segundos depois de 5 PINs errados.
- A área do responsável no app exige o PIN e mostra o uso do dia, os limites e o estado das permissões.
- Volta a funcionar sozinho depois que o celular reinicia.
- Modo **administrador do dispositivo** (opcional): impede desinstalar ou forçar a parada do app sem antes desativar a administração. Se alguém remover a permissão de sobreposição, o app trava a tela do celular como alternativa.

## Como gerar o APK

1. Instale o [Android Studio](https://developer.android.com/studio). Ele já traz o JDK e o Android SDK.
2. No Android Studio: **File › Open** e escolha a pasta `Controle Parental`.
3. Aguarde a sincronização do Gradle. Na primeira vez ele baixa o Gradle 8.7 e o Android Gradle Plugin.
4. **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
   O APK de teste fica em `app/build/outputs/apk/debug/app-debug.apk`.
   - Para uma versão final, use **Build › Generate Signed App Bundle / APK**.
5. Copie o APK para o celular da criança e instale. É preciso permitir "instalar apps de fontes desconhecidas".
   Outra opção: conecte o celular por USB com a depuração USB ativada e clique em **Run ▶**.

Requisito: Android 8.0 (API 26) ou superior.

## Configuração no celular da criança (feita pelo responsável)

1. Abra o app e **crie o PIN do responsável**.
2. Na área do responsável, toque nos 4 botões de permissão, nesta ordem:
   1. **Sobrepor outros apps**: obrigatório para a tela de bloqueio aparecer.
   2. **Notificações**: para os avisos de tempo.
   3. **Ignorar otimização de bateria**: evita que o sistema pare a contagem.
   4. **Proteger contra desinstalação**: ativa o administrador do dispositivo.
3. Defina os limites em minutos (ex.: `120` = 2 horas) e toque em **Salvar limites**.
4. Toque em **Sair da área do responsável**.

> Em celulares Xiaomi, Samsung, Motorola etc., também vale ativar a **Inicialização automática**
> e marcar a bateria como **Sem restrições** para o app nas configurações do sistema.

## Estrutura do código

| Arquivo | Função |
|---|---|
| `app/src/main/java/br/com/controleparental/ScreenTimeService.kt` | Serviço em primeiro plano. Conta o tempo de tela ligada, zera à meia-noite, avisa e bloqueia. |
| `app/src/main/java/br/com/controleparental/LockOverlay.kt` | Tela de bloqueio que fica sobre todos os apps, com entrada de PIN. |
| `app/src/main/java/br/com/controleparental/MainActivity.kt` | Área do responsável: criar e alterar PIN, limites, permissões e tempo extra. |
| `app/src/main/java/br/com/controleparental/Prefs.kt` | Armazena configurações, contadores do dia e o hash do PIN. |
| `app/src/main/java/br/com/controleparental/Receivers.kt` | Reinício depois do boot e administrador do dispositivo. |

## Limitações (importante)

- **Somente Android.** No iPhone, a Apple não permite que apps comuns bloqueiem o aparelho.
  Para iPhone, use o **Tempo de Uso** nativo do iOS (Ajustes › Tempo de Uso).
- Uma criança com conhecimento técnico pode tentar contornar o limite. Por exemplo:
  - adiantar a **data do sistema** para zerar a contagem. Mantenha "data/hora automática" ativada e, se possível, bloqueada;
  - iniciar em **modo de segurança**;
  - fazer **restauração de fábrica**.
- Ligações de emergência continuam disponíveis pela tela de bloqueio do sistema (botão "Emergência"), mas não pela tela do app.
- Para controle mais rígido (bloqueio por app, controle remoto pelo celular dos pais), combine este app com o
  **Google Family Link**, que tem permissões de sistema que um app comum não consegue ter.
