# Decisões Técnicas

## Princípios atuais

DarkWave é um app pessoal, educacional e de testes. O usuário é responsável por baixar apenas conteúdo permitido. O app não deve incentivar violação de direitos autorais, DRM, paywall, login, cookies ou conteúdo privado.

O app não usa cookies/login. Decisões técnicas devem preservar isso.

A fase atual é de refatoração estrutural. Não implementar features novas, não adicionar plataformas e não alterar comportamento funcional sem necessidade.

## Subpastas de destino

Decisão: novos downloads no destino padrão devem ser salvos em subpastas reais por origem.

Subpastas atuais:

- `Downloads/DarkWave/Youtube`;
- `Downloads/DarkWave/Instagram`;
- `Downloads/DarkWave/TikTok`;
- `Downloads/DarkWave/Arquivos`.

Motivo:

- organizar arquivos no armazenamento do usuário;
- evitar misturar mídias de origens diferentes;
- manter crescimento do app mais previsível.

Escopo:

- aplicado ao destino padrão MediaStore;
- aplicado ao fallback legado;
- não aplicado à SAF customizada por enquanto.

## Não mover downloads antigos

Decisão: downloads antigos não são movidos automaticamente para as novas subpastas.

Motivo:

- evitar risco de perda de arquivo;
- evitar quebrar URIs antigas;
- evitar migração complexa sem necessidade;
- preservar histórico existente.

## SAF customizada

Decisão: SAF customizada continua salvando direto na pasta escolhida, sem criar subpastas por enquanto.

Motivo:

- quando o usuário escolhe uma pasta via SAF, a expectativa é que o app respeite a escolha;
- criar subpastas via SAF exige mais validação;
- a fase atual prioriza preservar comportamento funcional.

## DownloadDestinationResolver e subpastas

Decisão: `DownloadDestinationResolver` recebe `destinationSubfolder` como `String`.

Motivo:

- evitar acoplamento de `core.utils` com `download.model`;
- manter resolução de destino genérica;
- deixar a classificação de origem separada.

`DownloadDestinationSubfolderResolver` resolve:

- Youtube;
- Instagram;
- TikTok;
- Arquivos.

## Instagram e headers seguros

Decisão: Instagram/fbcdn pode preservar headers por `httpHeadersJson`, mas apenas com allowlist segura.

Headers proibidos:

- `Cookie`;
- `Authorization`;
- tokens;
- headers `X-IG-*`;
- credenciais ou headers de sessão.

Motivo:

- alguns links públicos de mídia precisam de headers básicos;
- o app não deve transportar cookies, login ou dados privados;
- reduzir risco de vazar credenciais ou incentivar bypass.

## TikTok

Decisão: auto-update de `yt-dlp` foi ampliado para erros conhecidos de TikTok.

Motivo:

- TikTok já falhou por `yt-dlp` desatualizado;
- o extractor externo muda com frequência.

Decisão: selector TikTok MP4 virou internamente:

`best[ext=mp4]/best`

Motivo:

- o selector anterior era restritivo demais para MP4 vertical;
- o fallback `best` aumenta chance de sucesso sem adicionar lógica de bypass.

YouTube mantém selectors próprios.

## Clipboard

Decisão: `ClipboardLinkPromptController` concentra launcher intent, leitura de clipboard, anti-spam da sessão e diálogo "Link copiado encontrado".

Motivo:

- reduzir responsabilidade da `MainActivity`;
- isolar comportamento de clipboard;
- preservar regra de não iniciar download automaticamente.

Regras:

- instância estável na `MainActivity`;
- não usar getter que recria controller;
- "Usar link" só preenche Home;
- não iniciar download automaticamente.

## Quick Share

Decisão: Quick Share deve preservar fluxos por tipo de origem.

Comportamento atual:

- YouTube abre `QuickDownloadSheet` MP3/MP4;
- Instagram/Reels/carrossel abre `MediaSelectionSheet`;
- HTTP direto compartilhado não inicia automaticamente.

Motivo:

- reduzir downloads acidentais;
- manter decisão explícita do usuário;
- preservar fluxo especial de seleção para mídia compartilhada.

## Player interno

Decisão: MP3/MP4 concluídos abrem no player interno, enquanto arquivos não mídia abrem externamente.

`DownloadOpenRouter` decide:

- se abre internamente;
- categoria `MUSIC` ou `VIDEO`;
- índice na lista filtrada;
- fallback externo.

Motivo:

- tirar decisão de abertura da `MainActivity`;
- manter a `MainActivity` como dona do player real;
- permitir refatorações futuras do player em etapas menores.

## Room e migrações

Decisão: banco está em `AppDatabase version = 3`.

Migrações:

- `MIGRATION_1_2`: adiciona `qualitySelector`;
- `MIGRATION_2_3`: adiciona `httpHeadersJson`.

Regra atual:

- não mexer em Room/migrations sem necessidade;
- não criar migração para refatoração estrutural;
- não alterar `DownloadEntity` sem necessidade funcional clara.

## uiautomator

Decisão: `ERROR: could not get idle state` não é bug automaticamente quando há UI dinâmica.

Motivo:

- player, vídeo, progresso, sheets e animações podem impedir estado idle;
- falso positivo pode levar a mudanças desnecessárias.

Validação alternativa:

- screenshots;
- logs;
- estado manual do app;
- repetir dump com UI parada.

## Android 12 e POST_NOTIFICATIONS

Decisão: `POST_NOTIFICATIONS` retornando `Unknown permission` no Android 12 é esperado.

Motivo:

- a permissão é relevante em versões posteriores;
- o emulador Android 12 é secundário.

Dispositivo principal de validação: Android 16 físico `SM-M346B`.

## Refatoração estrutural

Decisão: extrair responsabilidades em etapas pequenas.

Já extraído:

- `DownloadOpenRouter`;
- `DownloadDetailsDialogController`;
- `ClipboardLinkPromptController`.

Próximos alvos prováveis:

- fluxo de request de download da Home;
- navegação/tabs;
- partes do player;
- componentes de `YtDlpDownloader`.

Motivo:

- reduzir risco;
- manter build funcional;
- preservar comportamento;
- facilitar rollback mental se algo quebrar.

