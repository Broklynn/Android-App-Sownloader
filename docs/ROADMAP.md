# Roadmap

## Fase atual

O DarkWave está em fase de refatoração estrutural, organização e otimização. Nesta fase, não implementar features novas, não adicionar plataformas e não alterar comportamento funcional sem necessidade.

O app é pessoal, educacional e de testes. Ele não usa cookies/login e não deve incentivar violação de direitos autorais, DRM, paywall ou conteúdo privado. O usuário é responsável por baixar apenas conteúdo permitido. Como parte do app depende de `yt-dlp`, plataformas podem quebrar com o tempo.

## Objetivo da fase

Reduzir acoplamento e tamanho dos arquivos críticos sem quebrar fluxos existentes:

- reduzir `MainActivity`;
- reduzir futuramente `YtDlpDownloader`;
- manter build e testes passando;
- preservar UI visual e comportamento;
- preparar o projeto para crescimento futuro.

## Ordem recomendada

1. Confirmar commit/push do `ClipboardLinkPromptController`, se ainda não foi feito.
2. Fazer checkpoint específico do `ClipboardLinkPromptController`.
3. Investigar e extrair o fluxo de request de download da Home.
4. Investigar navegação/tabs.
5. Extrair partes do player em etapas pequenas.
6. Dividir `YtDlpDownloader` em componentes menores.
7. Revisar `HttpDownloader`, se necessário.
8. Criar documentação e checklists de release.
9. Só depois voltar a pensar em features novas.

## Próxima extração sugerida

Investigar o fluxo de request de download da Home antes de implementar.

Possíveis nomes:

- `HomeDownloadRequestController`;
- `DownloadRequestFlowController`.

Responsabilidade provável:

- receber link e intenção da Home;
- validar entrada;
- coordenar callbacks para iniciar download;
- preservar a decisão de que clipboard prompt só preenche a Home;
- preservar a decisão de que HTTP direto compartilhado não inicia automaticamente.

Antes de criar a classe, mapear responsabilidades atuais na `MainActivity`, callbacks necessários, riscos e testes manuais.

## Player: etapas pequenas

Não extrair o player inteiro de uma vez.

Sequência mais segura:

1. Estado/categoria do player.
2. Lista usada pelo player.
3. Controles.
4. Fullscreen, apenas se for seguro.

Critério: cada etapa deve preservar abertura interna de MP3/MP4, índice na lista filtrada e fallback externo.

## YtDlpDownloader: divisão futura

Não atacar o arquivo inteiro de uma vez.

Possíveis componentes futuros:

- init/update de `yt-dlp`;
- metadata/getInfo;
- attempts/selectors;
- progress/watchdog;
- finalização;
- errors/fallback.

Cuidados:

- YouTube mantém selectors próprios.
- TikTok usa `best[ext=mp4]/best`.
- Auto-update do `yt-dlp` foi ampliado para TikTok.
- Instagram/fbcdn usa headers seguros via `httpHeadersJson`.
- Não adicionar cookies, login, Authorization, tokens ou headers privados.

## O que não fazer agora

- Não implementar features novas.
- Não adicionar novos sites/plataformas.
- Não mexer em Room/migrations sem necessidade.
- Não mover downloads antigos automaticamente.
- Não alterar SAF customizada para criar subpastas agora.
- Não refatorar player inteiro de uma vez.
- Não refatorar `YtDlpDownloader` inteiro de uma vez.
- Não alterar downloaders funcionando sem necessidade.
- Não quebrar Quick Share.
- Não quebrar destination/subpastas.
- Não alterar UI visual sem necessidade.
- Não tratar automaticamente `uiautomator` com `ERROR: could not get idle state` como bug quando houver UI dinâmica.

## Critério de refatoração pronta

Uma refatoração deve ser considerada pronta quando:

- o diff está limitado ao objetivo;
- nenhum comportamento externo mudou sem intenção explícita;
- `testDebugUnitTest` passou;
- `assembleDebug` passou;
- `installDebug` passou quando a etapa exige validação em dispositivo;
- o fluxo afetado foi testado manualmente;
- `git status --short` foi revisado;
- os riscos restantes foram documentados.

Para validação manual, priorizar Android 16 físico `SM-M346B`. Android 12 emulador é secundário. No Android 12, `POST_NOTIFICATIONS` retornando `Unknown permission` é esperado.

## Quando voltar a implementar features

Só voltar a features novas quando:

- `MainActivity` estiver menor e com responsabilidades mais claras;
- o fluxo da Home estiver separado;
- o player tiver extrações seguras;
- `YtDlpDownloader` tiver componentes menores ou pelo menos limites claros;
- existir checklist de release;
- os fluxos principais de Quick Share, Home, player, subpastas e downloads continuarem validados.

