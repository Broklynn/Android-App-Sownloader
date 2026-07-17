# Contexto do Projeto

## Visão geral

DarkWave é um app Android Kotlin do repositório `Broklynn/Android-App-Sownloader`. O app é pessoal, educacional e voltado a testes, com foco em baixar conteúdos permitidos pelo usuário a partir de links manuais, compartilhamento Android e fluxos internos de mídia.

O projeto não deve incentivar violação de direitos autorais, DRM, paywall, login, cookies ou conteúdo privado. O usuário é responsável por baixar apenas conteúdo que tem permissão para baixar. O app não usa cookies nem login.

Como parte importante do app depende de `yt-dlp`, plataformas externas podem quebrar com o tempo por mudanças nos sites, nos extractors ou nas políticas das plataformas.

## Objetivo do app

O DarkWave centraliza fluxos de download para uso pessoal:

- colar link manualmente na Home;
- detectar link copiado ao abrir o app pelo ícone;
- receber links via Quick Share Android;
- baixar mídia de YouTube, Instagram, TikTok e arquivos HTTP diretos;
- oferecer o preset `MP4 carro - 720p` para gerar vídeos mais compatíveis com centrais multimídia;
- gerenciar downloads com filtros;
- abrir MP3/MP4 concluídos em player interno;
- abrir arquivos não mídia em apps externos.

O objetivo atual do desenvolvimento não é adicionar novas plataformas ou novas features. A fase atual é de refatoração estrutural, organização e otimização para manter o app funcional e preparado para crescer sem virar um bloco difícil de manter.

## Stack técnica

- Kotlin Android.
- `compileSdk 36`.
- `targetSdk 36`.
- `minSdk 28`.
- Room.
- OkHttp.
- Coroutines.
- `youtubedl-android`.
- FFmpeg Android.
- MediaStore/SAF.
- PowerShell, Windows, Android Studio e ADB.

## Ambiente do usuário

O desenvolvimento e a validação acontecem principalmente em Windows, usando PowerShell, Android Studio, Gradle e ADB.

Dispositivos principais:

- Android 16 físico Samsung `SM-M346B`: dispositivo principal de validação.
- Android 12 emulador: dispositivo secundário.

Observações de ambiente:

- No Android 12, `POST_NOTIFICATIONS` pode retornar `Unknown permission`; isso é esperado e não deve ser tratado automaticamente como bug.
- Se `uiautomator` retornar `ERROR: could not get idle state`, não tratar automaticamente como bug quando player, vídeo, sheet, progresso ou outra UI dinâmica estiver ativa.
- ADB/Samsung pode dificultar a troca arbitrária de clipboard em testes.

## Regras atuais do projeto

Regra principal neste momento: o DarkWave está em fase de refatoração estrutural, organização e otimização.

Não fazer agora:

- implementar features novas;
- adicionar novos sites ou plataformas;
- alterar comportamento funcional sem necessidade;
- alterar UI visual sem necessidade;
- refatorar várias áreas ao mesmo tempo;
- mexer em Room/migrations sem necessidade;
- mexer em downloaders que já estão funcionando sem uma razão clara;
- quebrar Quick Share, subpastas, player ou fluxos existentes.

Foco atual:

- separar arquivos gigantes;
- manter o checkpoint da `MainActivity` apos reducao para cerca de 1643 linhas;
- reduzir futuramente `YtDlpDownloader`;
- manter tudo 100% funcional;
- preservar testes e build;
- preparar o app para crescimento sustentável.

## Arquitetura geral

Componentes importantes no estado atual:

- `MainActivity`: ainda concentra o player real, fullscreen, back navigation e wiring principal. As refatoracoes seguras fora do player foram praticamente concluidas.
- `QuickShareDownloadActivity`: entrada via compartilhamento Android.
- `AppContainer`: wiring de dependências.
- `AppDatabase`, `DownloadEntity`, `DownloadDao`: persistência Room.
- `DownloadRepository`: acesso e coordenação de dados.
- `DownloadQueue`: fila de downloads.
- `DownloadForegroundService`: execução em foreground.
- `HttpDownloader`: downloads HTTP diretos.
- `YtDlpDownloader`: integração com `yt-dlp`, metadata, seleção de formatos, tentativas e progresso.
- `VideoCompatibilityProfile`: descreve o perfil semântico conservador usado pelo modo carro.
- `CarCompatibilityTranscoder`: executa a conversão FFmpeg para H.264/AAC sem sobrescrever o temporário original.
- `DownloadMediaPostProcessor`: decide quando aplicar o perfil e retorna o arquivo convertido ou o original como fallback.
- `DownloadDestinationResolver`: resolve destino de arquivo.
- `DownloadOriginResolver`: classifica origem do download.
- `DownloadDestinationSubfolderResolver`: define subpastas reais por origem.
- `DownloadsController`: controle da tela/lista de downloads.
- `DownloadOpenRouter`: decide abertura interna ou externa de downloads concluídos.
- `DownloadDetailsDialogController`: orquestra diálogo de detalhes.
- `DownloadDetailsRenderer`: renderiza corpo do diálogo de detalhes.
- `ClipboardLinkPromptController`: controla leitura de clipboard e prompt ao abrir pelo ícone.
- `MediaSelectionSheetController` e `MediaSelectionSheetRenderer`: fluxo de seleção de mídias compartilhadas.
- `MediaThumbnailLoader`: carregamento de thumbnails.
- `SharedMediaPreviewExtractor` e `SharedMediaPreviewParser`: extração/parse de previews compartilhados.
- `SharedMediaDownloadCoordinator`: coordena downloads a partir da seleção de mídia.
- `HttpHeadersJsonParser`: aplica headers preservados com allowlist segura.
- Player interno: reprodução de MP3/MP4 concluídos.
- `SettingsController` e `DiagnosticsController`: configurações, diagnóstico e telas auxiliares.
- Controllers/helpers recentes: `HomeDownloadRequestController`, `HomeRecentUrlController`, `MainNavigationController`, `MainHeaderController`, `DownloadTextProvider`, `ClearFinishedDownloadsController`, `DefaultQualityController`, `YtDlpUpdateController`, `DownloadLocationController`, `SettingsInfoController` e helpers puros do player.

## Checkpoint atual de arquitetura

A decisao atual e pausar micro-extracoes estruturais fora do player. As investigacoes recentes recusaram novos recortes em intents/share/clipboard, onCreate/setup/wiring e download start/service/queue por baixo ganho seguro ou risco alto.

O proximo milestone recomendado e player/arquitetura de player:

- investigar o uso atual de `MediaPlayer` e `VideoView`;
- mapear audio, video inline, fullscreen, seek, completion, skip e lifecycle;
- avaliar uma camada `InternalPlayerEngine`;
- avaliar Media3/ExoPlayer futuramente, sem adicionar dependencia agora.

Antes desse milestone, nao mexer em player real, fullscreen, back navigation, downloaders, Room, Quick Share ou service/queue.

## Como pensar antes de mexer no projeto

Antes de alterar qualquer código:

1. Identificar qual responsabilidade será separada.
2. Confirmar que a mudança não adiciona feature nova.
3. Confirmar que o comportamento externo será preservado.
4. Localizar callbacks que devem continuar pertencendo à `MainActivity`.
5. Preferir extrações pequenas, com classe nova e limites claros.
6. Evitar tocar em downloaders, banco, UI visual e navegação se não forem parte direta da etapa.
7. Validar com `testDebugUnitTest`, `assembleDebug` e, quando aplicável, `installDebug`.
8. Conferir `git status --short` antes de recomendar commit.

Refatoração pronta significa: build passando, testes relevantes passando, fluxo afetado validado, comportamento preservado e diff limitado ao objetivo.
