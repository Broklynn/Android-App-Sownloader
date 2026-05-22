# DarkWave

App Android pessoal para baixar midias e arquivos a partir de links, com suporte a Quick Share, yt-dlp, HTTP direto e player interno.

## Aviso importante

Este projeto e pessoal, educacional e voltado para testes. Ele nao incentiva violacao de direitos autorais, termos de uso de plataformas, DRM, paywalls, login, conteudo privado ou restrito.

O usuario e responsavel por baixar apenas conteudo que tenha permissao para acessar, baixar e armazenar.

## Funcionalidades

- Home para colar ou digitar links manualmente.
- Deteccao de link copiado no clipboard ao abrir o app.
- Download HTTP direto.
- Download via yt-dlp para YouTube, TikTok e outras fontes compativeis.
- Quick Share do Android para links compartilhados por outros apps.
- Quick Share do YouTube com `QuickDownloadSheet` para escolher MP3 ou MP4.
- Quick Share do Instagram, Reels e carrossel com `MediaSelectionSheet`.
- Instagram com thumbnails em grade de 2 colunas.
- Instagram com opcao de baixar itens selecionados ou baixar todos.
- Preservacao de headers HTTP seguros para Instagram/fbcdn via `httpHeadersJson`.
- TikTok com selector especifico para MP4 vertical.
- Auto-update de yt-dlp para erros conhecidos, quando habilitado.
- Lista de downloads com filtros por status, origem e busca.
- Origens: Todos, YouTube, Instagram, TikTok e Arquivos.
- MP3/MP4 concluidos abrem no player interno.
- Arquivos que nao sao midia abrem por app externo ou chooser do Android.
- Compartilhamento de arquivo concluido via chooser externo.
- Settings com Diagnostico, Sobre, Qualidade padrao e auto-update do yt-dlp.

## Fluxos de entrada

### Link copiado ao abrir o app

Ao abrir o app pelo icone, o DarkWave pode detectar uma URL valida no clipboard e oferecer a opcao de preencher a Home com esse link. O download nao e iniciado automaticamente nesse fluxo.

### Home manual

O usuario cola ou digita um link na Home e toca em baixar. O app decide se o link deve seguir pelo downloader HTTP direto ou pelo fluxo yt-dlp.

### Quick Share YouTube

Ao compartilhar um link do YouTube com o DarkWave, o app abre a `QuickDownloadSheet` para escolha de formato/qualidade, como MP3 ou MP4, antes de iniciar o download.

### Quick Share Instagram/Reels/carrossel

Ao compartilhar links do Instagram, Reels ou carrosseis, o app extrai um preview, mostra a `MediaSelectionSheet` com thumbnails quando disponiveis, e permite baixar itens selecionados ou todos os itens encontrados.

### Quick Share HTTP direto

Links HTTP diretos compartilhados pelo Android sao tratados de forma conservadora. O fluxo de compartilhamento nao inicia automaticamente esse tipo de download; para baixar, use a Home manual.

## Downloads e organizacao

Os downloads possuem status como fila, preparando, baixando, pausado, concluido, falhou e cancelado. A tela de downloads permite filtrar por status, origem e busca textual.

As origens reconhecidas incluem:

- Todos
- YouTube
- Instagram
- TikTok
- Arquivos

O destino padrao atual e:

```text
Downloads/DarkWave
```

Subpastas reais por origem ainda nao foram implementadas. Um caminho futuro esperado e:

```text
Downloads/DarkWave/Youtube
Downloads/DarkWave/Instagram
Downloads/DarkWave/TikTok
Downloads/DarkWave/Arquivos
```

## Player interno

Downloads MP3 e MP4 concluidos podem ser abertos no player interno do app. O botao de abrir tenta reproduzir midias suportadas dentro do DarkWave.

Arquivos que nao sao midia continuam usando abertura externa por app compativel ou chooser do Android. A acao de compartilhar arquivo concluido tambem preserva o fluxo externo do Android.

## Tecnologias

- Kotlin
- Android SDK
- Room
- OkHttp
- Coroutines
- youtubedl-android
- FFmpeg Android
- MediaStore
- SAF

## Como rodar

No PowerShell, a partir da raiz do repositorio:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

APK debug:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Dispositivos de teste

- Android 16 fisico como dispositivo principal de validacao.
- Android 12 emulador como compatibilidade secundaria.

No Android 12, a permissao `POST_NOTIFICATIONS` pode retornar `Unknown permission`. Isso e esperado nesse ambiente e nao representa, por si so, bug do app.

## Documentacao adicional

- [Fluxos de entrada](docs/input-flows.md)
- [Quick Share e Instagram/carrossel](docs/quick-share-instagram.md)

## Roadmap

- Criar checklists de release.
- Implementar subpastas reais por origem.
- Refatorar `MainActivity`.
- Refatorar o player interno.
- Refatorar `YtDlpDownloader` em componentes menores.
- Melhorar o player com repeat, shuffle e continuacao.
- Melhorar diagnostico e exportacao de logs.

## Status atual

Projeto em desenvolvimento ativo.
