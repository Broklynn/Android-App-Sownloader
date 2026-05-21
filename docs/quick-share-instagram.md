# Quick Share e Instagram/Carrossel

## Visao Geral

Quick Share e o fluxo de entrada usado quando outro aplicativo compartilha um link de texto com o DarkWave. O app recebe o `ACTION_SEND`, extrai a primeira URL valida do texto compartilhado e decide qual experiencia abrir de acordo com a origem do link.

Links de video suportados por yt-dlp seguem para a folha rapida de formato. Links diretos HTTP sao tratados de forma conservadora no compartilhamento e continuam podendo ser baixados pela Home. Links do Instagram passam por uma etapa de preview para permitir selecionar midias especificas antes de enfileirar downloads.

## Fluxo YouTube

Ao compartilhar um link do YouTube, o app abre a `QuickDownloadSheet` sem carregar a Home inteira. A sheet apresenta opcoes de MP3 e MP4 com as qualidades configuradas pelo app.

Depois que o usuario escolhe uma opcao, o download e enfileirado com o seletor de qualidade correspondente e iniciado pelo service de download. Esse fluxo continua separado da selecao de midias do Instagram.

## Fluxo Instagram

Ao compartilhar um link do Instagram, a `QuickShareDownloadActivity` identifica o host e chama o `SharedMediaPreviewExtractor`. O extractor usa a infraestrutura de extracao para obter metadados do post e entregar um preview estruturado.

O `SharedMediaPreviewParser` transforma esse resultado em `SharedMediaItem`s. Quando ha itens baixaveis, a `MediaSelectionSheet` e exibida. Para posts com multiplas midias, como carrosseis, a sheet lista cada item separadamente.

Cada item mostra tipo, indice, titulo e thumbnail quando `thumbnailUrl` esta disponivel. Se a thumbnail nao existir ou falhar, a UI mantem um placeholder visual. O usuario pode selecionar itens especificos e tocar em "Baixar selecionados", ou usar "Baixar todos".

O enfileiramento dos itens selecionados fica centralizado no `SharedMediaDownloadCoordinator`, que transforma cada `SharedMediaItem` em um download individual e inicia os IDs resultantes.

## Headers HTTP

URLs diretas do Instagram/fbcdn podem exigir headers HTTP semelhantes aos informados pelo yt-dlp. Sem esses headers, algumas URLs retornam `403 Forbidden` ou falham rapidamente.

O modelo `SharedMediaItem` carrega `httpHeaders` opcionais por item. Esses headers sao lidos do preview e passam por sanitizacao antes de chegar ao fluxo de download. Apenas headers nao sensiveis sao preservados, como:

- `User-Agent`
- `Accept`
- `Accept-Language`
- `Referer`
- `Sec-Fetch-Mode`

Headers sensiveis ou perigosos sao descartados, incluindo cookies, autorizacao, tokens, headers de proxy, `Range`, `Host`, `Connection`, tamanho/conteudo e qualquer nome associado a sessao, credencial, cookie, auth ou token.

Para sobreviver ao limite entre Activity e service, os headers permitidos sao serializados em `httpHeadersJson` e persistidos no Room junto do download. O `HttpDownloader` le esse JSON, sanitiza novamente na fronteira de rede e aplica os headers somente ao request daquele download. Nao ha interceptor global e nao ha suporte a cookies, login ou credenciais.

## Limitacoes Conhecidas

- Alguns carrosseis so de fotos podem retornar `entries` vazio sem cookies/login.
- O app nao usa cookies, login ou credenciais para contornar restricoes.
- Posts privados, restritos ou dependentes de sessao podem falhar.
- URLs diretas do Instagram/fbcdn podem expirar.
- O emulador Android 12 pode falhar em fbcdn enquanto o Android 16 fisico funciona.
- `POST_NOTIFICATIONS` nao existe como permissao runtime no Android 12; falha com `Unknown permission` nesse ambiente e isso e esperado.

## Arquitetura Envolvida

- `QuickShareDownloadActivity`: ponto de entrada para links compartilhados via `ACTION_SEND`; decide entre YouTube/yt-dlp, Instagram e links diretos.
- `QuickDownloadSheetController`: controla a exibicao da sheet rapida de formato para links do fluxo yt-dlp.
- `QuickDownloadSheetRenderer`: renderiza as opcoes de MP3/MP4 e o botao de download da sheet rapida.
- `MediaSelectionSheetController`: controla a exibicao da sheet de selecao de midias compartilhadas.
- `MediaSelectionSheetRenderer`: renderiza a lista de midias, estado selecionado, thumbnails/placeholders e botoes de acao.
- `MediaThumbnailLoader`: baixa thumbnails por URL em background, decodifica bitmap e atualiza o `ImageView` sem bloquear a UI.
- `SharedMediaPreviewExtractor`: extrai metadados do link compartilhado do Instagram para montar um preview.
- `SharedMediaPreviewParser`: transforma o JSON/metadata extraido em `SharedMediaPreview` e `SharedMediaItem`s, incluindo headers permitidos.
- `SharedMediaItem`: modelo de cada midia compartilhada, com URL de origem, tipo, titulo, thumbnail e headers HTTP opcionais.
- `SharedMediaDownloadCoordinator`: enfileira e inicia downloads para os itens selecionados, preservando headers por item.
- `HttpHeadersJsonParser`: parseia e sanitiza `httpHeadersJson` antes da aplicacao em requests HTTP.
- `HttpDownloader`: executa downloads diretos HTTP e aplica headers opcionais por download quando presentes e seguros.

## Testes Realizados

Validacoes realizadas com Android 16 fisico como dispositivo principal:

- Instagram carrossel com videos: abriu `MediaSelectionSheet`, mostrou thumbnails, baixou 1 item e enfileirou multiplos itens com "Baixar todos".
- Reels: abriu sheet com 1 item, thumbnail ou placeholder presente, sem crash.
- Carrossel so de fotos sem `entries`: nao crashou e nao iniciou download errado.
- YouTube: continuou abrindo `QuickDownloadSheet` MP3/MP4, sem abrir `MediaSelectionSheet`.
- HTTP direto: compartilhamento nao iniciou download automaticamente; pela Home baixou como HTTP direto.

Tambem houve verificacao secundaria no emulador Android 12 para compatibilidade. Nesse ambiente, falha de `POST_NOTIFICATIONS` com `Unknown permission` e esperada.

## Proximos Passos Possiveis

- Melhorar feedback para carrossel sem midia detectada.
- Mostrar contador explicito de itens selecionados e totais.
- Evoluir a lista para um grid visual quando houver muitos itens.
- Testar mais links reais de posts, reels e carrosseis.
- Melhorar logs e exportacao de diagnostico para falhas de fbcdn.
- Validar player, abrir arquivo e compartilhar arquivo em uma bateria futura.
