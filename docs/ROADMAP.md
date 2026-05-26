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

## Checkpoint pos-refatoracao da MainActivity

A `MainActivity.kt` foi reduzida de aproximadamente 1900+ linhas para cerca de 1643 linhas.

As extracoes seguras fora do player foram praticamente concluidas. As investigacoes recentes recusaram novas extracoes em:

- intents/share/clipboard;
- onCreate/setup/wiring;
- download start/service/queue.

Motivo: o ganho seguro restante e baixo, e o ganho maior exigiria tocar em fluxos sensiveis. Nao continuar criando controllers pequenos apenas para esconder funcoes simples.

## Ordem recomendada atualizada

1. Manter o checkpoint pos-refatoracao documentado.
2. Pausar micro-extracoes estruturais fora do player.
3. Preparar milestone proprio para player/arquitetura de player.
4. Investigar o player atual antes de qualquer mudanca em engine.
5. Avaliar uma camada `InternalPlayerEngine` apenas depois da investigacao.
6. Avaliar Media3/ExoPlayer futuramente, sem adicionar dependencia agora.
7. Dividir `YtDlpDownloader` em componentes menores somente em milestone posterior.
8. Criar checklists de release quando os blocos estruturais maiores estiverem estabilizados.

## Proximo milestone: player

Nao mexer no player real de forma incremental sem milestone proprio.

Investigacao inicial recomendada:

1. Mapear uso atual de `MediaPlayer` e `VideoView`.
2. Mapear estados de audio, video inline e fullscreen.
3. Mapear seek, completion, skip, pause/resume e lifecycle.
4. Mapear acoplamento com `DownloadOpenRouter`, `PlayerCategory` e lista filtrada.
5. Mapear fullscreen/back navigation e decidir o que fica fora do primeiro recorte.
6. Avaliar se uma interface `InternalPlayerEngine` reduz risco antes de qualquer troca para Media3/ExoPlayer.

Restricoes do milestone:

- nao adicionar Media3 diretamente;
- nao adicionar dependencia antes de plano e comparativo;
- nao alterar UI visual junto com troca de engine;
- nao misturar fullscreen/back navigation sem plano;
- nao alterar downloaders, Room, Quick Share ou service/queue;
- preservar abertura interna de MP3/MP4, indice na lista filtrada e fallback externo.

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
