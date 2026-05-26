# Prompts para Codex

Este arquivo guarda prompts reutilizáveis para continuar o desenvolvimento do DarkWave em outro chat, conta ou IA sem perder o contexto técnico do projeto.

Contexto permanente: DarkWave é um app Android Kotlin pessoal, educacional e de testes. O usuário é responsável por baixar apenas conteúdo permitido. O app não usa cookies/login e não deve incentivar violação de direitos autorais, DRM, paywall ou conteúdo privado. O app depende de `yt-dlp`, então plataformas podem quebrar com o tempo.

Dispositivo principal de validação: Android 16 físico `SM-M346B`. Android 12 emulador é secundário. No Android 12, `POST_NOTIFICATIONS` pode retornar `Unknown permission`. Se `uiautomator` retornar `ERROR: could not get idle state`, não tratar automaticamente como bug quando player/UI dinâmica estiver ativa.

## Prompt para continuar em outro chat/conta

Você está trabalhando no repositório `Broklynn/Android-App-Sownloader`, app Android Kotlin chamado DarkWave.

Antes de agir, leia a documentação interna em `docs/`:

- `docs/CONTEXTO_PROJETO.md`;
- `docs/ESTADO_ATUAL.md`;
- `docs/ROADMAP.md`;
- `docs/BUGS_CONHECIDOS.md`;
- `docs/DECISOES_TECNICAS.md`;
- `docs/PROMPTS_CODEX.md`;
- `docs/REGRAS_DE_TRABALHO.md`.

O projeto está em fase de refatoração estrutural. Não implemente features novas, não adicione plataformas, não altere UI visual sem necessidade e não mude comportamento funcional sem necessidade.

Prioridades atuais:

- manter o checkpoint da `MainActivity`, hoje em cerca de 1643 linhas;
- pausar micro-extracoes fora do player quando o ganho real for baixo;
- preparar milestone proprio para player/arquitetura de player;
- preservar Quick Share, Home, player, downloads e subpastas;
- futuramente reduzir `YtDlpDownloader`;
- manter testes/build funcionando.

Não usar cookies, login, headers privados, DRM, paywall ou qualquer mecanismo para acessar conteúdo privado.

## Checkpoint pos-refatoracao para novos prompts

As extracoes seguras fora do player foram praticamente concluidas. Ja foram investigados e recusados novos recortes em intents/share/clipboard, onCreate/setup/wiring e download start/service/queue.

Para proximas etapas:

- sempre rodar `git status --short` antes de investigar ou editar;
- nao fazer `git add`, commit ou push;
- o usuario faz commit manualmente;
- nao pedir permissao para acoes basicas quando o prompt ja autorizar leitura, busca, diff e testes;
- parar e reportar se a etapa exigir alterar fora do escopo;
- nao adicionar Media3/ExoPlayer sem investigacao e plano;
- ao tocar player, validar no Android 16 fisico `SM-M346B` e pausar/parar audio ou video apos validar.

## Prompt padrão de refatoração estrutural

Você está no projeto DarkWave, app Android Kotlin.

Objetivo desta etapa: fazer uma refatoração estrutural pequena e segura.

Regras:

- Não implementar feature nova.
- Não adicionar novo site/plataforma.
- Não alterar comportamento funcional sem necessidade.
- Não alterar UI visual sem necessidade.
- Não mexer em Room/migrations sem necessidade.
- Não mexer em README.
- Não fazer `git add`, commit ou push sem pedido explícito.
- Preservar Quick Share, Home, player interno, downloaders e subpastas.
- Não adicionar cookies, login, Authorization, tokens, DRM, paywall ou conteúdo privado.
- Sempre que a alteração exigir nova lógica, separar em funções, helpers, controllers, resolvers, renderers ou classes conforme necessário.
- Não empilhar tudo em um único arquivo, especialmente `MainActivity`, `YtDlpDownloader`, `HttpDownloader` ou controllers já grandes.
- Se a mudança começar a crescer demais, dividir em etapas menores.

Antes de editar:

- leia os arquivos relevantes;
- mapeie responsabilidade atual;
- escolha extração pequena;
- avalie onde a nova lógica deve morar antes de implementar;
- explique quais arquivos serão alterados.

Depois de editar, rode:

- `.\gradlew.bat testDebugUnitTest`;
- `.\gradlew.bat assembleDebug`;
- `.\gradlew.bat installDebug`, quando houver validação em dispositivo;
- `git status --short`.

Responda no formato definido em "Como responder depois de cada etapa".

## Prompt padrão de checkpoint

Você está no projeto DarkWave.

Objetivo: checkpoint de validação. Não alterar código, XML, Gradle ou README.

Faça:

- conferir `git status --short`;
- rodar `.\gradlew.bat testDebugUnitTest`;
- rodar `.\gradlew.bat assembleDebug`;
- rodar `.\gradlew.bat installDebug`, se o objetivo incluir validação no dispositivo;
- validar manualmente os fluxos afetados;
- reportar bugs, riscos e observações.

Não implemente correções nesta etapa, exceto se eu pedir explicitamente depois.

Lembretes:

- Android 16 físico `SM-M346B` é o dispositivo principal.
- Android 12 emulador é secundário.
- `POST_NOTIFICATIONS` como `Unknown permission` no Android 12 é esperado.
- `uiautomator` com `ERROR: could not get idle state` não é bug automaticamente quando há player, vídeo, sheet, progresso ou UI dinâmica.

## Prompt padrão de investigação antes de implementar

Você está no projeto DarkWave.

Objetivo: investigar antes de implementar.

Regras:

- Só investigar.
- Não criar arquivos.
- Não alterar código.
- Não alterar XML.
- Não alterar Gradle.
- Não alterar README.
- Não fazer `git add`, commit ou push.

Entregue:

- responsabilidades atuais encontradas;
- arquivos e funções/classes envolvidos;
- proposta de classe ou pacote;
- callbacks necessários;
- o que deve permanecer onde está;
- riscos;
- testes unitários e manuais recomendados;
- recomendação se a extração é segura agora ou deve ser adiada.

## Prompt padrão com uiautomator/player

Você está validando o DarkWave com ADB/uiautomator.

Se aparecer:

`ERROR: could not get idle state`

Não trate automaticamente como bug quando houver:

- player ativo;
- vídeo tocando;
- sheet aberta;
- download em progresso;
- animação;
- UI dinâmica.

Use validação alternativa:

- screenshots;
- logs;
- teste manual;
- fechar/pausar player ou sheet;
- repetir dump quando a UI estiver estável.

Android 16 físico `SM-M346B` é a validação principal. Android 12 emulador é secundário. `POST_NOTIFICATIONS` como `Unknown permission` no Android 12 é esperado.

## Prompt padrão para commit

Você está no projeto DarkWave.

Objetivo: preparar commit após validação.

Faça:

- `git status --short`;
- `git diff --stat`;
- revisar arquivos alterados;
- confirmar que não há mudanças indesejadas;
- `git add` apenas dos arquivos específicos da etapa;
- `git commit -m "mensagem clara"`;
- `git push`.

Antes do commit, confirme:

- testes relevantes passaram;
- build passou;
- install passou quando aplicável;
- não houve alteração de comportamento fora do escopo;
- não foram adicionadas features novas;
- não foram adicionados cookies, login, DRM, paywall ou acesso a conteúdo privado.

## Prompt para refatorar fluxo da Home

Você está no projeto DarkWave.

Objetivo: investigar e, se seguro, extrair o fluxo de request de download da Home para uma classe pequena, possivelmente:

- `HomeDownloadRequestController`;
- `DownloadRequestFlowController`.

Regras:

- Não implementar feature nova.
- Não alterar comportamento.
- Clipboard prompt deve apenas preencher Home.
- HTTP direto compartilhado não deve iniciar automaticamente.
- A `MainActivity` pode manter callbacks reais quando necessário.
- Não mexer em downloaders se não for necessário.

Antes de implementar, mapeie responsabilidades atuais, callbacks e testes. Se a extração for grande demais, pare na investigação e recomende uma etapa menor.

## Prompt para dividir YtDlpDownloader futuramente

Você está no projeto DarkWave.

Objetivo: dividir `YtDlpDownloader` em componentes menores, em uma etapa pequena por vez.

Não dividir tudo de uma vez.

Possíveis áreas:

- init/update;
- metadata/getInfo;
- attempts/selectors;
- progress/watchdog;
- finalização;
- errors/fallback.

Preservar:

- selectors próprios do YouTube;
- TikTok com `best[ext=mp4]/best`;
- auto-update de `yt-dlp` para erros conhecidos de TikTok;
- Instagram/fbcdn com `httpHeadersJson` usando allowlist segura;
- ausência de cookies, login, Authorization, tokens e headers privados.

## Como responder depois de cada etapa

Use este formato:

1. Arquivos modificados/criados.
2. O que foi movido/separado.
3. O que permaneceu.
4. Áreas não alteradas.
5. Resultado `testDebugUnitTest`.
6. Resultado `assembleDebug`.
7. Resultado `installDebug`.
8. Testes manuais.
9. Bugs encontrados.
10. Recomendação: pode commitar ou precisa ajustar.

Se a etapa for apenas documentação, explique que build não foi necessário porque nenhum código, XML ou Gradle foi alterado.
