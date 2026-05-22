# Estado Atual

## Escopo geral

DarkWave é um app Android Kotlin pessoal, educacional e de testes. O usuário é responsável por baixar apenas conteúdo permitido. O app não usa cookies, login, DRM, paywall nem mecanismos para acessar conteúdo privado.

O app depende de `yt-dlp` para parte dos fluxos de mídia, então YouTube, Instagram, TikTok e outros extractors podem quebrar conforme as plataformas mudam.

## Funcionalidades atuais

- Home para colar link manualmente.
- Prompt de clipboard ao abrir o app pelo ícone.
- Quick Share Android.
- Download manual de HTTP direto pela Home.
- Lista de downloads com filtros por status, origem e busca.
- Abertura interna de MP3/MP4 concluídos.
- Abertura externa de arquivos não mídia.
- Compartilhamento de arquivo concluído via chooser externo.
- Settings com Diagnóstico, Sobre, Qualidade padrão e auto-update de `yt-dlp`.

## Fluxos de entrada

### Home

O usuário cola um link manualmente e inicia o download. HTTP direto pela Home baixa manualmente.

### Clipboard

Ao abrir o app pelo ícone, o `ClipboardLinkPromptController` pode detectar um link copiado e exibir o diálogo "Link copiado encontrado".

Regras:

- o controller cuida do launcher intent, leitura de clipboard, anti-spam da sessão e diálogo;
- a instância precisa ser estável dentro da `MainActivity`, não recriada por getter;
- "Usar link" só preenche a Home;
- clipboard prompt nunca inicia download automaticamente.

### Quick Share

O app recebe links compartilhados pelo Android.

Comportamentos atuais:

- YouTube via Quick Share abre `QuickDownloadSheet` com opções MP3/MP4.
- Instagram, Reels e carrossel via Quick Share abrem `MediaSelectionSheet`.
- HTTP direto compartilhado não inicia download automaticamente.

## Downloaders

### YtDlpDownloader

`YtDlpDownloader` ainda é grande e futuramente deve ser dividido. Hoje concentra lógica de `yt-dlp`, metadata, tentativas, selectors, progresso, watchdog, finalização, erros e fallback.

Comportamentos atuais importantes:

- YouTube mantém selectors próprios.
- TikTok usa auto-update de `yt-dlp` para erros conhecidos.
- TikTok usa selector específico para MP4 vertical: `best[ext=mp4]/best`.
- Instagram e fbcdn podem usar headers seguros preservados via `httpHeadersJson`.

### HttpDownloader

`HttpDownloader` cuida de downloads HTTP diretos. Também é relativamente grande, mas é menos urgente que `MainActivity` e `YtDlpDownloader`.

## Instagram e mídias compartilhadas

O fluxo de Instagram via Quick Share exibe thumbnails em grade de 2 colunas e permite:

- baixar itens selecionados;
- baixar todos os itens disponíveis.

Para Instagram/fbcdn, o app preserva headers seguros via `httpHeadersJson`, usando allowlist. Não devem ser usados `Cookie`, `Authorization`, tokens, headers `X-IG-*` ou credenciais.

Limitação conhecida: carrossel de Instagram só com fotos pode retornar `entries` vazio sem cookies/login.

## Player interno

MP3 e MP4 concluídos abrem no player interno.

O `DownloadOpenRouter` classifica arquivos concluídos:

- MP3 como `PlayerCategory.MUSIC`;
- MP4 como `PlayerCategory.VIDEO`;
- arquivos fora desse escopo caem no fluxo externo.

A `MainActivity` continua dona do player real. O router decide a intenção de abertura e calcula índice na lista filtrada.

## Filtros de downloads

A tela de downloads possui:

- filtro por status;
- filtro por origem;
- busca textual.

Origens atuais:

- Todos;
- Youtube;
- Instagram;
- TikTok;
- Arquivos.

## Subpastas de destino

Novos downloads no destino padrão são salvos em subpastas reais:

- `Downloads/DarkWave/Youtube`;
- `Downloads/DarkWave/Instagram`;
- `Downloads/DarkWave/TikTok`;
- `Downloads/DarkWave/Arquivos`.

Regras atuais:

- subpastas reais foram aplicadas ao destino padrão MediaStore e fallback legado;
- SAF customizada foi preservada sem subpastas por enquanto;
- downloads antigos não são movidos automaticamente.

## Banco de dados

`AppDatabase version = 3`.

Migrações:

- `MIGRATION_1_2`: adiciona `qualitySelector`.
- `MIGRATION_2_3`: adiciona `httpHeadersJson`.

Campos atuais de `DownloadEntity`:

- `id`;
- `sourceUrl`;
- `finalUrl`;
- `fileName`;
- `mimeType`;
- `destinationUri`;
- `tempPath`;
- `totalBytes`;
- `downloadedBytes`;
- `progress`;
- `speed`;
- `status`;
- `errorMessage`;
- `qualitySelector`;
- `httpHeadersJson`;
- `createdAt`;
- `updatedAt`.

## Refatorações já feitas

### DownloadOpenRouter

Arquivo: `app/src/main/java/com/androiddownload/ui/downloads/DownloadOpenRouter.kt`

Responsabilidades:

- decidir se download abre no player interno ou externo;
- classificar MP3/MP4;
- escolher `PlayerCategory.MUSIC` ou `PlayerCategory.VIDEO`;
- calcular índice na lista filtrada;
- oferecer fallback para abertura externa.

### DownloadDetailsDialogController

Arquivo: `app/src/main/java/com/androiddownload/ui/downloads/DownloadDetailsDialogController.kt`

Responsabilidades:

- orquestrar o diálogo de detalhes;
- usar `DownloadDetailsRenderer` para o corpo;
- expor botões "Fechar", "Copiar URL", "Abrir" e "Compartilhar";
- mostrar "Abrir" e "Compartilhar" apenas para downloads `COMPLETED`.

A `MainActivity` mantém os callbacks reais.

### ClipboardLinkPromptController

Arquivo: `app/src/main/java/com/androiddownload/ui/home/ClipboardLinkPromptController.kt`

Responsabilidades:

- tratar launcher intent;
- ler clipboard;
- evitar spam na sessão;
- exibir diálogo "Link copiado encontrado";
- preencher a Home quando o usuário escolhe "Usar link".

## O que está funcionando

No estado documentado:

- Home manual funciona.
- Clipboard prompt preenche Home sem iniciar download.
- Quick Share Android funciona para os fluxos principais.
- YouTube via Quick Share abre sheet MP3/MP4.
- Instagram/Reels/carrossel abre seleção de mídia.
- Instagram exibe thumbnails em grade de 2 colunas.
- Instagram permite baixar selecionados ou todos.
- TikTok tem auto-update de `yt-dlp` para erros conhecidos.
- TikTok usa selector MP4 menos restritivo.
- HTTP direto compartilhado não baixa automaticamente.
- Downloads concluídos MP3/MP4 abrem no player interno.
- Arquivos não mídia abrem externamente.
- Subpastas reais funcionam para novos downloads no destino padrão.

