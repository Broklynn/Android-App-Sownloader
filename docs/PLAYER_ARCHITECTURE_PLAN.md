# Player Architecture Plan

## Objetivo

Este documento registra o design tecnico do milestone de player do DarkWave.

Ele nao implementa engine, nao adiciona Media3/ExoPlayer e nao muda runtime. A funcao deste plano e definir limites entre UI do player, sessao de playback e engine antes de qualquer extracao ou troca de tecnologia.

## Estado Atual

A `MainActivity` ainda e dona do player real. O bloco direto de player/playback/fullscreen ocupa cerca de 835 linhas, ou cerca de 900-950 linhas quando incluidos campos, wiring, lifecycle e back navigation.

Hoje o player usa:

- `MediaPlayer` para audio;
- `AspectRatioVideoView`, baseado em `VideoView`, para video inline;
- outro `AspectRatioVideoView` separado para fullscreen;
- troca inline/fullscreen recriando playback e preservando posicao;
- `Handler` para progresso, auto-hide de controles inline, auto-hide de controles fullscreen e feedback de seek fullscreen;
- `onPause` e `onStop` pausando playback;
- `onDestroy` liberando player;
- back navigation fechando fullscreen antes de settings ou saida.

O fullscreen atual nao compartilha a mesma instancia visual do video inline. Ao abrir fullscreen, a Activity captura posicao e estado de play, para o inline e prepara o `videoFullscreenView`. Ao fechar fullscreen com restore, captura posicao e estado de play, para o fullscreen e prepara novamente o inline.

## Responsabilidades Atuais

Responsabilidades que ainda vivem na `MainActivity`:

- lista e categoria do player: `playerItems`, `playerCategory`, `currentPlayerIndex`;
- selecao atual e sincronizacao com downloads filtrados;
- start de playback por indice;
- preparacao e execucao de audio com `MediaPlayer`;
- preparacao e execucao de video inline com `playerVideoView`;
- preparacao e execucao de video fullscreen com `videoFullscreenView`;
- play, pause, resume, stop e release;
- seek inline e fullscreen;
- timer de progresso;
- completion, play next e stop at end;
- erro, falha de start e skip para proximo item;
- resolucao de URI `content://` e `file://`;
- labels de now playing, status e botoes;
- fullscreen chrome, orientacao, system UI e controles;
- back navigation quando fullscreen esta aberto.

## Helpers Ja Separados

Estes componentes ja estao extraidos e devem ser preservados durante o milestone:

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

## Acoplamentos Atuais

Problemas principais:

- engine e UI estao misturadas na `MainActivity`;
- `MediaPlayer` e `VideoView` sao manipulados diretamente pela Activity;
- fullscreen esta acoplado ao playback e ao estado da engine;
- back navigation conhece diretamente o fullscreen;
- URI resolution usa `contentResolver` e `File` dentro da Activity;
- timers/handlers ficam dentro da Activity;
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

No curto prazo, esse conceito pode ser um adapter sobre `VideoView`/`AspectRatioVideoView`. No futuro, poderia representar uma view/surface de Media3.

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
- URI resolver: transforma `DownloadEntity.destinationUri` em URI tocavel, validando `content://` e `file://`.
- Fullscreen coordinator: continua separado da engine no primeiro momento, porque mistura orientacao, system UI e back navigation.

No primeiro recorte, fullscreen deve permanecer na `MainActivity` ou em coordenador proprio, mas nao deve ser misturado com a criacao inicial da engine.

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

### Fase 3: contrato Kotlin sem conectar runtime

Criar contrato de engine apenas depois de estabilizar nomes e eventos.

Nao conectar `MainActivity`, `MediaPlayer` ou `VideoView` ainda nessa fase.

### Fase 4: adapter da engine atual

Adaptar o comportamento existente de `MediaPlayer`/`VideoView` atras do contrato, preservando:

- MP3;
- MP4 inline;
- completion;
- erro/skip;
- seek;
- pause/resume;
- lifecycle.

Fullscreen pode continuar temporariamente fora do contrato se isso reduzir risco.

### Fase 5: validacao do comportamento atual

Validar que a engine atual atras do contrato nao mudou comportamento.

### Fase 6: avaliar Media3/ExoPlayer

Somente depois do contrato e da engine atual estabilizados:

- comparar Media3/ExoPlayer contra o contrato;
- avaliar dependencia Gradle;
- avaliar migracao de video target;
- avaliar fullscreen;
- planejar rollback.

## Restricoes

- Nao trocar engine direto.
- Nao adicionar Media3/ExoPlayer antes do contrato.
- Nao alterar UI visual junto com engine.
- Nao mexer em `DownloadOpenRouter` no primeiro recorte.
- Nao mover back navigation sem plano.
- Nao misturar fullscreen com a primeira interface se isso aumentar risco.
- Nao mexer em Room, downloaders, service/queue ou Quick Share.

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
