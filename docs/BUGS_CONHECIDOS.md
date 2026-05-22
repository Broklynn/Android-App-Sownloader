# Bugs Conhecidos e Limitações

## Escopo e responsabilidade

DarkWave é um app pessoal, educacional e de testes. O usuário é responsável por baixar apenas conteúdo permitido. O app não deve incentivar violação de direitos autorais, DRM, paywall, login, cookies ou conteúdo privado.

O app não usa cookies nem login. Por isso, conteúdos que dependem de sessão, conta, permissão privada ou headers sensíveis podem não funcionar.

## Limitações técnicas gerais

- `yt-dlp` pode quebrar conforme plataformas mudam.
- TikTok, YouTube e Instagram dependem de extractors externos.
- Não há garantia de suporte a todos os sites.
- O app não está pronto para Play Store.
- O app não tenta burlar login, DRM, paywall ou conteúdo privado.

## Instagram

Limitações conhecidas:

- carrossel só de fotos pode retornar `entries` vazio sem cookies/login;
- URLs `fbcdn` podem expirar;
- headers preservados podem deixar de ser suficientes se a plataforma mudar;
- conteúdo privado ou que exige login não deve ser considerado suportado.

Workarounds:

- testar com links públicos;
- repetir com link atualizado quando `fbcdn` expirar;
- validar se o problema também acontece com `yt-dlp` atualizado;
- não adicionar cookies ou credenciais ao app.

## TikTok

Observações conhecidas:

- TikTok já falhou por `yt-dlp` desatualizado;
- auto-update de `yt-dlp` foi ampliado para erros conhecidos de TikTok;
- selector MP4 vertical foi relaxado para `best[ext=mp4]/best`;
- ainda pode quebrar quando o extractor externo mudar.

Workarounds:

- confirmar se o auto-update de `yt-dlp` ocorreu;
- testar novamente com link público;
- comparar com comportamento atual do `yt-dlp`.

## YouTube

Observações conhecidas:

- YouTube depende de `yt-dlp`;
- selectors próprios do YouTube devem ser preservados;
- mudanças na plataforma podem quebrar metadata, formatos ou download.

Não implementar mecanismos de bypass de DRM, login, conteúdo privado ou paywall.

## Android e permissões

No Android 12, `POST_NOTIFICATIONS` pode retornar `Unknown permission`. Isso é esperado e não deve ser tratado automaticamente como bug.

Dispositivos:

- Android 16 físico `SM-M346B`: principal para validação.
- Android 12 emulador: secundário.

## uiautomator

`uiautomator` pode retornar:

`ERROR: could not get idle state`

Isso não é automaticamente bug quando houver:

- player ativo;
- vídeo tocando;
- sheet aberta;
- progresso de download;
- animação;
- UI dinâmica.

Validação alternativa:

- usar screenshots;
- validar logs;
- usar dumps em momentos estáveis;
- pausar/fechar player ou sheet antes de repetir;
- validar o fluxo manualmente no dispositivo principal.

## Clipboard e ADB

ADB/Samsung pode dificultar trocar clipboard arbitrariamente. Falhas nesse setup não devem ser confundidas automaticamente com bug do `ClipboardLinkPromptController`.

O prompt de clipboard deve apenas preencher a Home quando o usuário escolher "Usar link". Ele não deve iniciar download automaticamente.

## HTTP direto

HTTP direto compartilhado não deve iniciar download automaticamente. Pela Home, HTTP direto baixa manualmente.

Se algum teste esperar início automático via Quick Share para HTTP direto, o teste está desalinhado com a decisão atual.

## Arquitetura ainda problemática

- `MainActivity` ainda é grande.
- `YtDlpDownloader` ainda é grande.
- `HttpDownloader` também é relativamente grande, mas menos urgente.
- SAF customizada ainda não cria subpastas.

Esses pontos são limitações conhecidas de organização, não necessariamente bugs funcionais.

## O que não é bug automaticamente

- `POST_NOTIFICATIONS` como `Unknown permission` no Android 12.
- `uiautomator` sem idle state com UI dinâmica ativa.
- Instagram público que mudou comportamento por extractor externo.
- `fbcdn` expirado.
- Conteúdo que exige login, cookies, paywall ou permissão privada.
- Downloads antigos não estarem nas novas subpastas.
- SAF customizada salvar direto na pasta escolhida, sem subpastas.

## Como validar sem falso positivo

1. Confirmar se o link é público e permitido.
2. Confirmar se o fluxo testado é Home, Clipboard ou Quick Share.
3. Confirmar se o comportamento esperado bate com as decisões técnicas atuais.
4. Preferir Android 16 físico `SM-M346B` para validação principal.
5. Tratar Android 12 emulador como secundário.
6. Para `yt-dlp`, considerar que a plataforma pode ter mudado.
7. Para `uiautomator`, repetir em estado estável antes de concluir bug.
8. Registrar logs, screenshots e passos exatos.

