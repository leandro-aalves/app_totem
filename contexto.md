# Totem Android — Contexto do Projeto

App Android nativo (Kotlin) que funciona como **totem de senha**: uma WebView em modo Kiosk travada no sistema web existente (`Sua Vez`), rodando num tablet Android dedicado.

## Objetivo original

- Instalar num tablet Android um app que:
  1. Não deixa a tela bloquear/apagar sozinha
  2. Trava o tablet dentro de um único app (não dá pra sair sem senha)
  3. Bloqueia os botões físicos de volume e o botão de Power (tela de bloqueio)
  4. Carrega o sistema de totem existente, que já roda só via navegador — então virou um WebView, não um app nativo do zero

## URL do sistema

```
http://suavez.heliomarb.com.br/totem
```

Sistema: Next.js + Tailwind CSS v4 ("Sua Vez" — gerenciamento de filas/atendimento). Na primeira abertura pede um **Token da Unidade** pra vincular aquela instalação a uma unidade específica.

## Estrutura do projeto

```
C:\Users\L-TECH\Documents\Site\Tablet\
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ltech/totem/
│       │   ├── MainActivity.kt           # tela única: WebView + kiosk
│       │   ├── SettingsActivity.kt       # tela de manutenção (URL, PIN, orientação)
│       │   ├── ConfigManager.kt          # SharedPreferences (URL, PIN hash, orientação)
│       │   └── TotemDeviceAdminReceiver.kt  # receiver do Device Admin/Owner
│       └── res/
│           ├── layout/ (activity_main, activity_settings, dialog_password)
│           ├── values/ (strings, themes)
│           └── xml/ (device_admin_receiver, network_security_config)
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── gradlew.bat / gradlew (wrapper gerado, Gradle 8.9)
└── local.properties (aponta pro SDK em C:\Android\sdk)
```

Pacote: `com.ltech.totem`

## Como funciona o app

### Kiosk Mode (modo totem)
- `MainActivity` entra em **Lock Task Mode** (`startLockTask()`) assim que abre, via `DevicePolicyManager` (o app é **Device Owner** do tablet).
- Registrado como "Home" do tablet (`addPersistentPreferredActivity`), então nem o botão Home tira do app.
- `setLockTaskFeatures(adminComponent, LOCK_TASK_FEATURE_NONE)` — desativa **todos** os recursos opcionais do Lock Task: home, recentes, notificações, status bar, **e o menu de Desligar/Reiniciar** (Global Actions) que aparece ao segurar o Power.
- Status bar desativada (`setStatusBarDisabled`).

### Botões físicos
- **Volume (+/-) e Voltar**: interceptados em `dispatchKeyEvent()` e ignorados (são eventos que chegam até o app).
- **Power (toque curto)**: não existe API pública que impeça o SO de apagar a tela ao apertar o Power — isso é tratado pelo sistema antes de qualquer app. Solução usada (padrão em soluções comerciais de kiosk): um `BroadcastReceiver` escuta `ACTION_SCREEN_OFF` e **acende a tela de volta na hora** via `PowerManager.WakeLock`, sem pedir PIN de desbloqueio (`setKeyguardDisabled` + `setShowWhenLocked`/`setTurnScreenOn`). Na prática vira um "pisca" rápido, não trava.
- **Power (toque longo / menu desligar)**: bloqueado pelo `setLockTaskFeatures(LOCK_TASK_FEATURE_NONE)` acima.

### Tela sempre ligada
- `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`.

### Saída do modo Kiosk
- Tocar **5 vezes** numa área invisível no **canto superior direito** da tela (dentro de ~2,5s) → abre diálogo pedindo PIN.
- PIN correto → `stopLockTask()` + abre `SettingsActivity`.
- PIN é guardado como hash SHA-256 (`ConfigManager`), não em texto puro.
- **PIN padrão: `1234`** — trocar antes de produção.

### Tela de Configurações (`SettingsActivity`)
Acessível só depois do PIN. Permite:
- Trocar a URL do sistema
- Trocar o PIN (4 a 8 dígitos)
- Alternar **orientação** (Paisagem/Retrato) — fica salva e persiste, aplicada tanto na tela do totem quanto na própria tela de Configurações (ambas seguem sempre a orientação configurada, não há rotação automática por sensor)
- Botão "Voltar ao modo Totem" → reabre `MainActivity`, que reentra em Lock Task automaticamente

### Orientação
- Controlada por código (`requestedOrientation` em ambas as Activities), não fixa no Manifest.
- Persistida em `ConfigManager` (`ORIENTATION_LANDSCAPE` / `ORIENTATION_PORTRAIT`, padrão paisagem).

## Ambiente de desenvolvimento configurado nesta máquina

Como não foi instalado o Android Studio completo (opção escolhida: só ferramentas de linha de comando), ficou tudo em:

- **JDK**: já existia (Temurin 21) — `JAVA_HOME` configurado
- **Android SDK**: `C:\Android\sdk` (variável `ANDROID_HOME`)
  - `platform-tools` (adb), `build-tools;34.0.0`, `platforms;android-34`
- **Gradle 8.9** standalone: `C:\Android\gradle-8.9` (usado só pra gerar o wrapper do projeto; o projeto já tem `gradlew.bat` próprio agora)
- `ANDROID_HOME` e os `PATH`s acima foram adicionados permanentemente nas variáveis de ambiente do usuário (Windows), funcionam em qualquer terminal novo

### Comandos úteis

Build (gera o APK debug):
```powershell
cd "C:\Users\L-TECH\Documents\Site\Tablet"
.\gradlew.bat assembleDebug
```
APK gerado em: `app\build\outputs\apk\debug\app-debug.apk`

Ver tablets conectados:
```powershell
C:\Android\sdk\platform-tools\adb.exe devices
```

Instalar/atualizar no tablet conectado via USB:
```powershell
C:\Android\sdk\platform-tools\adb.exe install -r "app\build\outputs\apk\debug\app-debug.apk"
```

Reiniciar o app depois de instalar (força reentrar no kiosk):
```powershell
C:\Android\sdk\platform-tools\adb.exe shell am force-stop com.ltech.totem
C:\Android\sdk\platform-tools\adb.exe shell am start -n com.ltech.totem/.MainActivity
```

## Configuração do Device Owner (feita uma vez, já concluída no tablet atual)

Pré-requisito: tablet **sem nenhuma conta cadastrada** no momento da configuração (depois de configurado, dá pra logar numa conta sem perder o Device Owner).

```powershell
adb shell dpm set-device-owner com.ltech.totem/.TotemDeviceAdminReceiver
```

Isso já foi feito no tablet atual (serial `R9XNA0JQ0KV` na época da configuração). **Se for configurar um tablet novo**, repetir: resetar de fábrica → pular login Google → ativar Opções do Desenvolvedor + Depuração USB → instalar o APK → rodar o comando acima.

## Problema resolvido: WebView desatualizado

**Sintoma**: a página carregava mas sem nenhum estilo (texto cru, sem CSS do Tailwind aplicado).

**Causa raiz**: o tablet tinha o **Android System WebView em Chromium 87** (dez/2020). O site usa Tailwind CSS v4, que depende de recursos de CSS que só existem a partir do Chromium 99–112 (2022–2023). O WebView carregava o CSS mas não conseguia interpretar boa parte das regras, então ignorava e renderizava sem estilo.

**Correção aplicada**: baixado e instalado manualmente (via `adb install -r`, sem precisar logar conta Google) um APK mais novo do **Android System WebView** (versão 155.0.8059.46, outubro/2026) direto do APKMirror — matching a arquitetura do tablet (`arm64-v8a`).

```powershell
adb install -r "com.google.android.webview_155.0.8059.46-...apk"
```

**⚠️ Atenção pra manutenção futura**: como essa atualização foi feita manualmente (não via Play Store), o WebView **não vai se atualizar sozinho**. Se o site "Sua Vez" um dia usar CSS/JS ainda mais novo e a tela voltar a quebrar, repetir o processo:
1. Baixar a versão estável mais recente em `apkmirror.com` → "Android System WebView" → variante `arm64-v8a + arm-v7a`
2. `adb install -r` no tablet

Alternativa (não usada aqui por decisão do usuário): logar o Google Play Store no tablet (não quebra o Device Owner) e deixar o WebView atualizar sozinho por lá.

## Ajustes relevantes no WebView (código)

```kotlin
useWideViewPort = true
loadWithOverviewMode = true
mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
```
Necessários pra respeitar a tag `<meta viewport>` do site corretamente.

## Pendências / próximos passos possíveis

- [ ] Trocar o PIN padrão (`1234`) antes de produção
- [ ] Confirmar/vincular o Token da Unidade na tela do totem
- [ ] Decidir se vale logar o Play Store no tablet pra manter o WebView atualizado automaticamente no futuro
- [ ] Se for replicar pra outros tablets, repetir o processo de Device Owner (ou considerar provisionamento via QR Code se forem muitos aparelhos)
