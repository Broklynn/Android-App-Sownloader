# Fluxos de entrada

## 1. Visao geral

O app possui multiplos fluxos de entrada para receber links e iniciar ou preparar downloads:

- Abrir o app pelo icone com um link copiado no clipboard.
- Compartilhar links do YouTube pelo menu de compartilhamento do Android.
- Compartilhar links do Instagram, Reels e carrosseis pelo menu de compartilhamento do Android.
- Compartilhar links HTTP diretos.
- Colar manualmente um link na Home.

Cada fluxo preserva o comportamento atual do app: alguns caminhos iniciam o download diretamente apos uma escolha do usuario, enquanto outros apenas preenchem a Home ou exigem acao manual para evitar downloads acidentais.

## 2. Abrir pelo icone com link copiado

Ao abrir o app pelo icone, a `MainActivity` le o clipboard da sessao atual. O `ClipboardUrlReader` tenta extrair uma URL valida do conteudo copiado, e o `SharedTextUrlExtractor` valida se o link usa `http` ou `https`.

Quando um link valido e encontrado, o app mostra o dialogo "Link copiado encontrado". A acao "Usar link" preenche o campo da Home com o link detectado, mas nao inicia o download automaticamente. A acao "Ignorar" fecha o dialogo e nao executa nenhuma outra operacao.

Existe uma protecao anti-spam para evitar que o mesmo link seja oferecido repetidamente na mesma sessao.

## 3. Compartilhar YouTube

Quando o usuario compartilha um link do YouTube com o app, a `QuickShareDownloadActivity` recebe o `ACTION_SEND`. O fluxo identifica que o link deve ser tratado pelo yt-dlp e que nao e um link do Instagram.

Nesse caso, a tela inteira da Home nao e aberta. O app mostra o `QuickDownloadSheet`, onde o usuario escolhe MP3 ou MP4. Depois da escolha, o download e iniciado pelo fluxo rapido.

## 4. Compartilhar Instagram, Reels e carrossel

Quando o usuario compartilha um link do Instagram, Reels ou carrossel, a `QuickShareDownloadActivity` detecta que a entrada e do Instagram. O `SharedMediaPreviewExtractor` extrai os metadados disponiveis e o `SharedMediaPreviewParser` transforma esses dados em itens `SharedMediaItem`.

Em seguida, o `MediaSelectionSheet` mostra uma grade com thumbnails. O usuario pode escolher um item especifico ou selecionar todos. A partir dessa selecao, o `SharedMediaDownloadCoordinator` enfileira os downloads.

Quando a midia aponta para URLs `fbcdn`, os headers necessarios sao preservados em `httpHeadersJson`. O `HttpDownloader` aplica apenas headers seguros durante o download. Os arquivos recebem nomes amigaveis e, quando a midia baixada e suportada, ela abre no player interno do app.

## 5. Compartilhar HTTP direto

Um link HTTP direto compartilhado com o app nao inicia download automaticamente. Conforme o comportamento atual, o app fecha ou mostra feedback sem disparar o download.

Para baixar um HTTP direto, o usuario deve abrir a Home, colar ou preencher o link e tocar em Baixar manualmente. Esse comportamento evita downloads acidentais a partir do menu de compartilhamento.

## 6. Home manual

Na Home, o usuario cola ou digita o link no campo principal. Ao tocar em Baixar, o app usa o `DownloadRequestPlanner` para decidir o caminho correto.

Links HTTP diretos sao baixados via `HttpDownloader`. Fontes que usam yt-dlp seguem o fluxo padrao de qualidade. Se a qualidade padrao estiver configurada como "Perguntar sempre", o app abre o dialogo antigo de qualidade antes de iniciar o download.

## 7. Player interno

Quando um download MP3 ou MP4 esta concluido, o botao Abrir usa o player interno do app para reproduzir a midia.

Arquivos que nao sao midia continuam seguindo o comportamento externo existente via `FileActionsController` e abertura por app externo. A acao Compartilhar continua abrindo o chooser externo do Android.

## 8. Diferencas Android 16 e Android 12

O Android 16 fisico e o dispositivo principal de validacao dos fluxos atuais. O Android 12 emulador e tratado como compatibilidade secundaria.

No Android 12, a permissao `POST_NOTIFICATIONS` pode retornar `Unknown permission`; isso e esperado para esse ambiente. Os testes com downloads reais, Instagram, YouTube e player interno sao priorizados no Android 16 fisico.

## 9. Limitacoes conhecidas

- Instagram carrossel somente de fotos pode retornar `entries` vazio sem cookies ou login.
- O app nao usa cookies nem login para acessar conteudos.
- URLs do Instagram e `fbcdn` podem expirar.
- Alguns comportamentos podem variar entre emulador e dispositivo fisico.
- A leitura do clipboard pode disparar o aviso visual do Android moderno.

## 10. Arquitetura envolvida

Componentes principais envolvidos nos fluxos de entrada:

- `MainActivity`
- `ClipboardUrlReader`
- `SharedTextUrlExtractor`
- `QuickShareDownloadActivity`
- `QuickDownloadSheetController` / `QuickDownloadSheetRenderer`
- `MediaSelectionSheetController` / `MediaSelectionSheetRenderer`
- `MediaThumbnailLoader`
- `SharedMediaPreviewExtractor`
- `SharedMediaPreviewParser`
- `SharedMediaDownloadCoordinator`
- `HttpHeadersJsonParser`
- `HttpDownloader`
- `FileActionsController`
- Player interno
