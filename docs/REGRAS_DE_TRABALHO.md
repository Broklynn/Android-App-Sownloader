# Regras de Trabalho

## Objetivo

Este arquivo define como qualquer IA, Codex, Gemini ou outro chat deve trabalhar no projeto DarkWave.

O objetivo atual é manter o app estável, organizado, performático e preparado para crescer sem virar um bloco impossível de manter. Esta documentação deve ajudar a continuidade do projeto em outra conta, outro chat ou outra IA sem perder as regras combinadas com o usuário.

## Regra-mãe do projeto

O foco do DarkWave é estabilidade de longo prazo. O app deve ficar funcional por bastante tempo, sem quebrar rápido e sem exigir reescrita completa.

Por isso, toda mudança deve priorizar:

- organização;
- performance;
- separação de responsabilidades;
- baixo acoplamento;
- código testável;
- arquivos menores;
- comportamento preservado;
- evolução incremental.

Uma alteração tecnicamente possível não é automaticamente uma boa alteração. Se ela concentrar lógica demais em um único lugar, aumentar acoplamento ou dificultar manutenção futura, deve ser redesenhada ou dividida em etapas menores.

## Fase atual

O projeto está em fase de refatoração estrutural, organização e otimização.

Não fazer agora:

- feature nova;
- novo site/plataforma;
- mudança visual desnecessária;
- alteração de comportamento sem motivo;
- refactor gigante;
- mudança em Room/migrations sem necessidade;
- alteração em downloaders funcionando sem necessidade;
- alteração em player/fullscreen fora do escopo;
- alteração em Quick Share fora do escopo;
- uso de cookies/login/tokens/DRM/paywall/conteúdo privado.

O foco atual é reduzir arquivos grandes, principalmente `MainActivity` e futuramente `YtDlpDownloader`, mantendo o app funcional.

## Como trabalhar com Codex/Gemini/outra IA

Sempre que for pedir algo para Codex, Gemini ou outra IA:

1. Primeiro investigar.
2. Mapear arquivos e responsabilidades.
3. Propor uma alteração pequena.
4. Separar lógica em classes, funções, helpers, controllers, resolvers, renderers ou coordinators quando necessário.
5. Não empilhar tudo na `MainActivity`.
6. Não empilhar tudo no `YtDlpDownloader`.
7. Não criar arquivo gigante novo.
8. Preservar comportamento já validado.
9. Validar build/testes.
10. Só recomendar commit depois da validação.

Todo prompt enviado a uma IA deve deixar explícito que a mudança não pode concentrar lógica nova em um único arquivo gigante. Se a implementação começar a crescer demais, a IA deve parar, propor divisão em etapas e evitar transformar um controller, downloader ou activity em outro arquivo difícil de manter.

## Regra de separação de responsabilidades

Quando uma alteração exigir nova lógica, a IA deve avaliar se essa lógica pertence a:

- controller;
- renderer;
- resolver;
- helper;
- coordinator;
- repository;
- downloader;
- service;
- model;
- util.

Não colocar lógica nova automaticamente na `MainActivity`.

Não colocar lógica nova automaticamente no `YtDlpDownloader`.

Não colocar lógica nova automaticamente no `HttpDownloader` ou em controllers que já estejam grandes.

Não duplicar lógica em vários lugares.

Se a mudança ficar grande, dividir em etapas menores.

Cada classe deve ter responsabilidade clara. Uma boa extração no DarkWave deve facilitar leitura, teste, manutenção e continuidade do projeto.

## Fluxo obrigatório antes de implementar

Antes de mexer em código:

1. Investigar.
2. Explicar o problema.
3. Listar arquivos envolvidos.
4. Listar o que NÃO pode ser alterado.
5. Propor classe/pacote.
6. Listar riscos.
7. Listar testes manuais.
8. Só então implementar, se for seguro.

Se a investigação mostrar que a mudança exige tocar em muitas áreas, o correto é propor uma etapa menor.

## Validação obrigatória

Quando houver alteração de código, rodar:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

Também verificar:

```powershell
git status --short
```

Quando for apenas documentação:

- build não é obrigatório;
- explicar que não rodou build porque não houve código, XML ou Gradle alterado;
- ainda assim rodar `git status --short`.

## Commits

Nunca fazer `git add`, commit ou push sem pedido explícito.

Depois que a validação passar, sugerir:

```powershell
git status
git diff --stat
git add <arquivos específicos>
git commit -m "mensagem clara"
git push
```

Sempre adicionar apenas arquivos da etapa.

Não incluir arquivos gerados, temporários, alterações de IDE ou mudanças fora do escopo.

## uiautomator e player

Se aparecer:

```text
ERROR: could not get idle state
```

Não tratar automaticamente como bug quando houver:

- player ativo;
- vídeo tocando;
- sheet aberta;
- progresso;
- animação;
- UI dinâmica.

Usar validação alternativa:

- teste manual;
- `dumpsys window`;
- logs;
- screenshots;
- estado do banco;
- pausar/fechar player antes do dump.

Só considerar bug se houver crash, foco errado, comportamento quebrado ou reprodução manual.

## Dispositivos

Principal:

- Android 16 físico Samsung `SM-M346B`.

Secundário:

- Android 12 emulador.

No Android 12, `POST_NOTIFICATIONS` pode retornar `Unknown permission`. Isso é esperado.

## Segurança e limites

O app é pessoal, educacional e de testes.

Não usar:

- cookies;
- login;
- Authorization;
- tokens;
- DRM;
- paywall;
- conteúdo privado;
- mecanismos de bypass.

O usuário é responsável por baixar apenas conteúdo permitido.

O app depende de `yt-dlp`, então plataformas podem quebrar com o tempo. Não prometer suporte irrestrito a sites.

## Resposta esperada após cada etapa

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

Se a etapa for apenas documentação, declarar que build não foi necessário porque nenhum código, XML ou Gradle foi alterado.

## Continuidade em outro chat

Antes de qualquer alteração, uma nova IA deve ler:

- `README.md`;
- `docs/CONTEXTO_PROJETO.md`;
- `docs/ESTADO_ATUAL.md`;
- `docs/ROADMAP.md`;
- `docs/BUGS_CONHECIDOS.md`;
- `docs/DECISOES_TECNICAS.md`;
- `docs/PROMPTS_CODEX.md`;
- `docs/REGRAS_DE_TRABALHO.md`.

Depois da leitura, a IA deve confirmar o escopo da etapa, preservar as regras do projeto e trabalhar de forma incremental.

