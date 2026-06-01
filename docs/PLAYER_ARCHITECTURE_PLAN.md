# Player Architecture Plan

## Objetivo

Este documento registra o design tecnico do milestone de player do DarkWave.

Ele nao implementa engine, nao adiciona Media3/ExoPlayer e nao muda runtime. A funcao deste plano e definir limites entre UI do player, sessao de playback e engine antes de qualquer extracao ou troca de tecnologia.

## Estado Atual Pos-Controllers

A `MainActivity` continua sendo a coordinator/orquestradora do player real, mas o runtime direto de playback e parte dos controles fullscreen ja foram extraidos para controllers pequenos.

Hoje o player usa:

- `AudioPlaybackController` para controlar o runtime de audio com `MediaPlayer`;
- `InlineVideoPlaybackController` para controlar o runtime do video inline em `playerVideoView`;
- `FullscreenVideoPlaybackController` para controlar o runtime do video fullscreen em `videoFullscreenView`;
- `FullscreenControlsController` para controlar a UI dos controles fullscreen: mostrar/ocultar, auto-hide, feedback de seek e callbacks de play/pause e fechar;
- `AspectRatioVideoView`, baseado em `VideoView`, para video inline e fullscreen;
- troca inline/fullscreen recriando playback no outro `VideoView` e preservando posicao;
- `Handler` para progresso geral e auto-hide de controles inline;
- `onPause` e `onStop` pausando playback;
- `onDestroy` liberando player;
- back navigation fechando fullscreen antes de settings ou saida.

O fullscreen atual nao compartilha a mesma instancia visual do video inline. Nao ha movimentacao real de surface ou target visual entre inline e fullscreen. Ao abrir fullscreen, a Activity captura posicao e estado de play, para o inline e prepara o `videoFullscreenView`. Ao fechar fullscreen com restore, captura posicao e estado de play, para o fullscreen e prepara novamente o inline.

O aspect ratio do video e tratado por `AspectRatioVideoView`, que mede o conteudo com base no tamanho informado pelo `MediaPlayer`/`VideoView` preparado. Essa responsabilidade e visual e nao deve entrar no contrato puro da engine.

Validacoes recentes confirmaram:

- MP3 iniciou, pausou/retomou e seek funcionou;
- MP4 inline iniciou, pausou/retomou e seek funcionou;
- fullscreen abriu, reproduziu, pausou/retomou, double tap seek funcionou, seekbar funcionou, fechou restaurando inline e Back fechou fullscreen;
- controles fullscreen, auto-hide e feedback de seek foram validados.

## Responsabilidades Atuais

Responsabilidades que ainda vivem na `MainActivity`:

- lista e categoria do player: `playerItems`, `playerCategory`, `currentPlayerIndex`;
- selecao atual e sincronizacao com downloads filtrados;
- decisao audio/video e start de playback por indice;
- start, stop, pause e resume como orquestracao entre os controllers;
- seek inline e fullscreen;
- timer de progresso;
- completion, play next e stop at end;
- erro, falha de start e skip para proximo item;
- resolucao de URI `content://` e `file://`;
- labels de now playing, status e botoes;
- open/close fullscreen;
- restore inline ao fechar fullscreen;
- fullscreen chrome, orientacao e system UI;
- back navigation quando fullscreen esta aberto.

## Helpers Ja Separados

Estes componentes ja estao extraidos e devem ser preservados durante o milestone:

- `AudioPlaybackController`;
- `InlineVideoPlaybackController`;
- `FullscreenVideoPlaybackController`;
- `FullscreenControlsController`;
- `FullscreenChromeController`;
- `FullscreenSeekController`;
- `FullscreenOverlayController`;
- `FullscreenGestureController`;
- `PlayerListController`;
- `PlayerListRenderer`;
- `PlayerProgressCalculator`;
- `PlayerMediaLabelResolver`;
- `PlayerNowPlayingTextFormatter`;
- `PlayerControlsController`;
- `PlayerControlsStateResolver`;
- `PlayerAdjacentNavigator`;
- `PlayerCompletionResolver`;
- `PlayerPlaybackFailureResolver`.

Eles devem continuar sendo usados como pecas de UI/regras puras. O milestone do player nao deve reimplementar essas responsabilidades dentro de uma engine.

Os quatro controllers reais ja extraidos nao devem conhecer sessao/lista/categoria, `DownloadEntity`, back navigation, orientacao, system UI ou decisoes de skip/next/stop. Eventos continuam voltando para a `MainActivity` por callbacks.

## Decisoes Arquiteturais Atuais

- Nao criar `InternalPlayerEngine` ainda. A fronteira entre controllers atuais, session, target visual e engine ainda nao esta clara o bastante.
- Nao adicionar Media3/ExoPlayer ainda. A engine atual deve continuar preservada ate existir contrato claro e validado.
- Nao criar camada comum entre `AudioPlaybackController`, `InlineVideoPlaybackController` e `FullscreenVideoPlaybackController` sem ganho claro. A duplicacao atual e pequena e mais segura que uma abstracao prematura.
- Nao mover o fullscreen coordinator completo ainda. Back navigation, orientacao, system UI, overlay lifecycle e restore inline continuam sensiveis e devem ser tratados em recorte proprio.
- O proximo recorte deve ser escolhido com cuidado, porque o runtime principal de playback e os controles fullscreen ja sairam da `MainActivity`.

## Decisao Sobre FullscreenCoordinator

`FullscreenCoordinator` nao deve ser retomado agora.

Uma tentativa nao commitada de consolidar o fullscreen em um coordinator unico causou regressao critica no lifecycle/background/foreground: ao sair do app e voltar, o audio duplicava; ao pausar, uma fonte parava e outra continuava tocando em fundo.

A tentativa foi revertida: `MainActivity.kt` voltou ao ultimo commit, `FullscreenCoordinator.kt` foi removido, o workspace ficou limpo e o teste manual confirmou que a duplicacao foi resolvida.

O estado estavel atual e a arquitetura com controllers separados por responsabilidade. Fullscreen deve continuar dividido em controllers pequenos para video, controles, chrome, seek, overlay e gestos. Nao consolidar open/close/restore/back/lifecycle em um coordinator unico sem desenho mais forte.

Qualquer tentativa futura deve comecar por design e validacao de lifecycle, nao por extracao direta. A validacao obrigatoria para uma nova tentativa deve cobrir:

- abrir fullscreen;
- sair do app;
- voltar;
- pausar;
- retomar;
- fechar fullscreen;
- Back;
- confirmar que nao ha audio duplicado.

## Proximas Opcoes Possiveis

- Investigar um fullscreen coordinator completo apenas em etapa futura de design/teste de lifecycle, somente se o escopo conseguir separar claramente overlay, restore inline, back navigation, foreground/background e chrome.
- Investigar camada comum entre controllers, apenas se aparecer duplicacao real e repetida que reduza risco ao ser extraida.
- Investigar reducao adicional da `MainActivity` como coordinator, priorizando limites de session/lista/categoria ou now playing/progresso.
- Pausar refatoracoes de player e estabilizar, caso o proximo recorte misture responsabilidades demais.

## Checkpoint De Micro-Refatoracoes

As micro-refatoracoes seguras no player atual chegaram ao limite util.

- `activePlaybackSource()` foi criado na `MainActivity` e reaproveitado onde era seguro.
- `seekCurrentPlayback()` foi ajustado para usar `activePlaybackSource()`.
- Nao vale conectar `PlayerPositionSnapshot` em `updatePlaybackProgress()` agora: o ganho e baixo e toca progresso/seek visivel.
- Nao vale mexer agora em lifecycle, release ou `stopCurrentPlayback(...)`: a ordem atual de callbacks, fullscreen, release e UI deve continuar explicita.
- `pauseCurrentPlayback()` nao deve usar `activePlaybackSource()`, porque pausa audio independentemente de `playerCategory` quando `audioPlayer` esta tocando.
- A proxima fase nao deve ser outra micro-refatoracao; deve ser um recorte maior e explicito de runtime/controller/engine, com validacao manual completa.
- Nao criar Media3/ExoPlayer ainda.
- Nao criar `PlayerVideoTarget` ou `InternalPlayerEngine` sem recorte definido.

## Acoplamentos Atuais

Problemas principais:

- engine e UI estao misturadas na `MainActivity`;
- video inline e fullscreen dependem de duas instancias visuais separadas;
- fullscreen ainda esta acoplado ao estado da session, ao chrome visual, a orientacao e ao restore inline;
- back navigation conhece diretamente o fullscreen;
- progresso geral e now playing continuam coordenados pela Activity;
- URI resolution usa `contentResolver` e `File` dentro da Activity;
- timer de progresso geral fica dentro da Activity;
- `currentPlayerIndex`, `playerItems` e `playerCategory` estao misturados com engine, UI e navegacao.

Consequencia: uma troca direta para Media3/ExoPlayer tende a tocar UI, fullscreen, lifecycle, erro/completion e navegacao ao mesmo tempo.

## Interface Simples Insuficiente

A ideia abaixo e util como direcao, mas insuficiente sozinha:

```kotlin
interface InternalPlayerEngine {
    fun play(...)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    fun release()
    fun isPlaying(): Boolean
    fun durationMs(): Long
    fun positionMs(): Long
}
```

Problemas:

- nao representa tipo de midia: audio, video inline ou video fullscreen;
- nao modela prepared async;
- nao modela completion;
- nao modela erro recuperavel ou fatal;
- nao informa mudancas de estado para UI;
- nao define como anexar alvo de video;
- nao diferencia prepare, playWhenReady e resume;
- nao trata troca inline/fullscreen preservando posicao;
- nao define ownership de timers de progresso;
- nao preserva explicitamente `currentPlayerIndex`, `playerItems` e categoria fora da engine.

Uma engine real precisa ser orientada a comandos, estado e callbacks/eventos.

## Contrato Conceitual Futuro

Este contrato ainda nao deve virar codigo sem uma etapa propria. Ele descreve os conceitos provaveis.

### PlayerMediaKind

Representa o tipo de midia:

- `Audio`;
- `Video`.

O modo visual do video deve ser separado do tipo de midia. Fullscreen e inline sao targets/containers, nao tipos de arquivo.

### PlayerPlaybackRequest

Dados minimos para preparar uma midia:

- URI resolvida;
- `PlayerMediaKind`;
- posicao inicial em ms;
- `playWhenReady`;
- identificador opcional do download;
- titulo/nome opcional para logs ou UI;
- target de video opcional quando for video.

### PlayerVideoTarget

Conceito para representar onde o video sera renderizado:

- inline target;
- fullscreen target;
- target ausente para audio.

Esse conceito e necessario antes de uma engine completa com video, mas nao deve ser criado ainda. Um target puramente conceitual e insuficiente para renderizar video real, porque algum ponto do sistema precisa conversar com `View`, surface, texture ou wrapper Android equivalente.

O target real provavelmente sera Android-dependent ou um adapter que esconda `View`/surface da engine. A engine pura nao deve receber `VideoView` diretamente. O target tambem nao deve misturar fullscreen, back navigation, orientacao ou controles fullscreen; esses pontos pertencem a um coordinator de UI.

### PlayerEngineState

Estado observavel necessario:

- idle;
- preparing;
- prepared;
- playing;
- paused;
- completed;
- error;
- released.

Campos associados:

- `mediaKind`;
- `durationMs`;
- `positionMs`;
- `isPlaying`;
- `isPrepared`;
- erro opcional.

### PlayerEngineCallbacks

Eventos minimos:

- `onPrepared(durationMs)`;
- `onStateChanged(state)`;
- `onPositionChanged(positionMs, durationMs)`, se a engine assumir progresso;
- `onCompletion()`;
- `onError(error)`.

Mesmo que a primeira versao mantenha polling por `Handler`, completion/error/prepared precisam vir por callbacks.

### Comandos

Comandos provaveis:

- `prepare(request)`;
- `play(request)` ou `prepare(request)` + `resume()`;
- `pause()`;
- `resume()`;
- `stop()`;
- `seekTo(positionMs)`;
- `release()`;
- `attachVideoTarget(target)`;
- `detachVideoTarget()`.

`play(...)` nao deve esconder todos os estados. Preparacao async precisa ser visivel.

## Limite Entre Session, UI e Engine

Separacao desejada:

- Player session: guarda `playerItems`, `currentPlayerIndex`, categoria e item atual.
- Player UI/controller: atualiza lista, botoes, labels, seekbars e fullscreen controls.
- Player engine: prepara/toca/pausa/para/seek/release e emite eventos.
- Video visual target: adapta o destino visual do video sem expor `VideoView` diretamente para uma engine pura.
- URI resolver: transforma `DownloadEntity.destinationUri` em URI tocavel, validando `content://` e `file://`.
- Fullscreen coordinator: abre/fecha overlay fullscreen, preserva posicao/play state entre targets, controla orientacao, system UI, controles fullscreen e integracao com back navigation.

No primeiro recorte, fullscreen deve permanecer na `MainActivity` ou em coordenador proprio, mas nao deve ser misturado com a criacao inicial da engine. A UI/MainActivity, ou um futuro coordinator, continua decidindo quando abrir e fechar o overlay fullscreen.

Fullscreen, back navigation e controles fullscreen nao sao responsabilidades da engine. A engine deve ficar limitada a comandos, eventos, estado e leitura de posicao/duracao.

## Fases Seguras Do Milestone

### Fase 0: documentacao/design

Estado atual deste documento. Nenhum codigo runtime deve mudar.

### Fase 1: modelos/estado puro

Se fizer sentido, extrair modelos puros como:

- media kind;
- playback state;
- playback request sem Android runtime;
- resultado/erro de engine.

Validar com testes JVM quando a logica for pura.

### Fase 2: URI resolver

Avaliar extracao de resolucao de URI para um componente pequeno.

Risco: envolve `ContentResolver`, `File` e permissoes de leitura. Fazer apenas se o recorte for claro e validavel.

### Fase 3: target visual de video

Investigar e definir o limite de `PlayerVideoTarget`/adapter antes de uma engine completa com video.

Direcao atual:

- nao criar `PlayerVideoTarget` ainda;
- nao expor `VideoView` diretamente para uma engine pura;
- aceitar que o adapter real de video provavelmente sera Android-dependent;
- manter fullscreen/back navigation fora desse conceito;
- tratar inline/fullscreen como decisao de UI/coordinator, nao como tipo de midia.

### Fase 4: contrato Kotlin sem conectar runtime

Criar `InternalPlayerEngine` apenas depois de estabilizar nomes, eventos, URI resolver e fronteira de target visual.

Nao conectar `MainActivity`, `MediaPlayer` ou `VideoView` ainda nessa fase. Criar o contrato sem target de video geraria uma interface boa para audio, mas incompleta para video. Criar o contrato com target de video cedo demais pode acoplar a engine a UI.

### Fase 5: adapter da engine atual

Adaptar o comportamento existente de `MediaPlayer`/`VideoView` atras do contrato, preservando:

- MP3;
- MP4 inline;
- completion;
- erro/skip;
- seek;
- pause/resume;
- lifecycle.

Fullscreen pode continuar temporariamente fora do contrato se isso reduzir risco.

### Fase 6: validacao do comportamento atual

Validar que a engine atual atras do contrato nao mudou comportamento.

### Fase 7: avaliar Media3/ExoPlayer

Somente depois do contrato e da engine atual estabilizados:

- comparar Media3/ExoPlayer contra o contrato;
- avaliar dependencia Gradle;
- avaliar migracao de video target;
- avaliar fullscreen;
- planejar rollback.

## Restricoes

- Nao trocar engine direto.
- Nao adicionar Media3/ExoPlayer antes do contrato.
- Nao adicionar Media3 antes de contrato claro.
- Nao alterar UI visual junto com engine.
- Nao alterar XML/UI junto com a primeira engine.
- Nao alterar XML/Gradle junto com controller.
- Nao mexer em `DownloadOpenRouter` no primeiro recorte.
- Nao mover back navigation sem plano.
- Nao mexer em back navigation junto com target visual.
- Nao misturar back navigation, orientacao ou system UI com playback controller.
- Nao misturar fullscreen com a primeira interface se isso aumentar risco.
- Nao mover fullscreen junto com a primeira engine.
- Nao acoplar engine pura a `Activity`, `View`, `VideoView`, `AspectRatioVideoView` ou fullscreen overlay.
- Nao mexer em Room, downloaders, service/queue ou Quick Share.
- Validacao manual de MP4 deve ser feita pelo usuario quando necessario.
- Codex nao deve insistir em automacao ADB extensa para MP4.

## Validacao Futura

Quando uma etapa tocar apenas documentacao:

- `git diff -- docs`;
- `git status --short`.

Quando criar modelos puros:

- `testDebugUnitTest`;
- `assembleDebug`.

Quando tocar runtime/player:

- `testDebugUnitTest`;
- `assembleDebug`;
- `installDebug`;
- validar no Android 16 fisico `SM-M346B`;
- usar Android 12 emulador como secundario se disponivel.

Fluxos manuais minimos para runtime:

- MP3 toca, pausa, resume, seek e completion;
- MP4 toca inline, pausa, resume, seek e completion;
- fullscreen abre, preserva posicao, pausa/resume e fecha restaurando inline;
- erro/falha e skip quando aplicavel;
- back fecha fullscreen antes de outras acoes;
- audio/video deve ser pausado ou parado ao fim da validacao para evitar problemas de idle/uiautomator.

Quando uma etapa futura tocar target visual, fullscreen ou runtime de video, a validacao manual deve ser focada no recorte alterado e deve confirmar explicitamente que a engine nao assumiu responsabilidades de fullscreen/back navigation.
