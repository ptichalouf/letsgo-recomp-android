# ISSUES.md — Problemas conhecidos do Let's Go Android

Estado em **26/10/2026, v0090**. Tudo aqui foi verificado com logs do Galaxy S23
(Snapdragon 8 Gen 2, Adreno 740, Android 16). Nada aqui é palpite sem prova.

## 1. Texto invisível — PROBLEMA PRINCIPAL (não resolvido)

**Sintoma:** nenhum texto aparece em menus, diálogos, caixas de seleção ou HUD.
As caixas de diálogo abrem vazias. O resto do jogo funciona.

### O que já se sabe (com prova)

- **O atlas de fonte existe e tem conteúdo real.** Beluga rasteriza os glifos;
  dump do atlas (2048×1024 R8) contém 8.358 pixels não-zero, glifos legíveis.
- **O driver Adreno do sistema não suporta `VK_EXT_external_memory_host`.**
  Por isso o heap do jogo nunca é importado como buffer GPU e todas as leituras de
  memória global dos shaders retornavam zero. Fallback `gcopy` implementado (v0078):
  copia ranges da CPU para um buffer GPU antes de cada draw.
- **O diálogo produz ZERO fragmentos.** Um shader de diagnóstico (TEXT_DIAG) que força
  magenta opaco no fragment shader do diálogo não produz nenhum pixel — enquanto o
  mesmo diagnóstico no menu produz a tela toda magenta (v0081). Ou seja: o draw do
  diálogo executa, mas nenhum fragmento sobrevive.
- **O menu é um problema SEPARADO.** O vertex shader do menu não usa memória global
  (`gmem`/`gfind`), só atributos + matriz. Todo o trabalho de `gcopy` é irrelevante
  para o menu — e o menu também está sem texto.
- **Um quad forçado no vertex shader APARECE (v0087).** O VS_DIAG (override de
  `gl_Position` para um quad centrado) produziu quads magenta grandes sobre o mundo,
  o personagem e o menu. Isso provou que o draw executa, o pipeline está certo e o
  fragment shader outputa — **as posições originais do VS eram o killer**.

### O que já foi ELIMINADO por prova (não perca tempo de novo)

| Suspeito | Versão | Prova |
|---|---|---|
| Janela do `gcopy` / dados / `gfind` | v0084 | Dados reais na janela, mapeamento correto, floats plausíveis |
| Culling dinâmico | v0085 | Forçado NONE, nada mudou |
| Depth test dinâmico | v0085 | Forçado OFF, nada mudou |
| `write_mask` (color write) | v0086 | Original já era 0xF (cheio); forçar foi no-op |
| Scissor / viewport final | v0086 | (0,0 1280×720) cheio |
| Pipeline stale / TEXT_DIAG | v0086 | Mecanismo verificado; menu provou o caminho |
| VS ler cor como multiplicador | v0088 | Execução simbólica: mapa real de loads é outro (+0x50, não +0x20) |
| Atributo `a0` desvinculado | v0088 | Vinculado, layout padrão, `pipe_bind=0` (não ZERO_BINDING) |
| Matriz `cb3` (projeção) | v0089 | Projeção ortográfica válida; NDC dos 4 cantos dentro de [-1,1] |

### Teste decisivo atual (v0090, pendente)

**VS_RANGE_DIAG** — override **condicional** no vertex shader do diálogo: se o
`gl_Position` final for ruim (w ≤ 0, NaN, fora do clip range), força um quad centrado;
se for bom, deixa como está.

- **Quads magenta CENTRADOS no diálogo** → o VS real emite fora do range (diverge da
  simulação estática). VS é o killer; próxima frente: achar onde o VS traduzido diverge.
- **NADA (nem glifo, nem quad)** → VS exonerado por prova positiva (o override teria
  forçado quads visíveis). O problema é downstream do VS (rasterização em diante).

### Detalhes técnicos (para quem for investigar)

- Shader do texto/diálogo: fshash NVN `6443b63b341c73ca` → GLSL `b0feab581b78f5e7`.
- Shader do menu: fshash `7270eb69fbecccee`, sem `discard` no fragment shader.
- Vertex shaders do diálogo: `8e41cc9edf14ab8c` e `afd67833c2e8f5ee`.
- `HEAP_BASE = 0x7240000000`; `HEAP_MAX = 0xD0000000` (não confundir com o range
  reservado `0x320000000` do log de boot).
- `gcopy` é **só leitura** — escritas em memória global (STG) continuam descartadas.
- A hipótese "layout escrito via STG" foi investigada e refutada (sonda de estabilidade v0087).

## 2. Driver Turnip (nenhum driver vem no APK; o app baixa o certo para o aparelho)

- Causa: as macros de dispatch eram canceladas em `device.c` e nunca restauradas, então o jogo
  desenhava com o driver do sistema mesmo com o Turnip carregado (corrigido pelo PR #3).
- O APK não traz nenhum driver. No lançador, "Baixar o driver recomendado para este aparelho":
  - detecta a GPU (`/sys/class/kgsl/kgsl-3d0/gpu_model`, senão o SoC), a família (A6XX, A7XX,
    A7XX_LOW, A8XX), a API do Android, o caso Samsung + Adreno 740 e a versão do driver do sistema;
  - lê as regras publicadas pelo DroidDeck (`auto-drivers.json` de github.com/Droid-Deck/Drivers-CI;
    tabela interna equivalente se o arquivo não puder ser lido): a primeira regra que bate dá a
    lista de preferência (Adreno 830 → `wn-b`, Adreno 740 Samsung → `banner-8g2-oneui`,
    Adreno 730 → `banner`, Adreno 840 → `dd-turnip` e depois `wn-b`...);
  - pega o pacote mais recente de cada chave nas publicações do GitHub (Banners-Turnip, WinNative,
    DroidDeck). Os endereços vêm só da API de publicações (nunca do arquivo de regras), apontam para
    `github.com/<repo>/releases/download/` e os redirecionamentos só podem levar a
    `*.githubusercontent.com`;
  - mostra nome, origem, versão, tamanho e sha256 e só baixa depois da confirmação. O arquivo tem de
    bater com o sha256 publicado pelo GitHub para esse asset; no pacote DroidDeck (`android/` +
    `manifest.json`), a biblioteca tem de bater também com o sha256 do manifest;
  - se o download ou a verificação falhar, passa ao próximo da lista; nada muda se todos falharem.
- O pacote fica **em teste** até o jogo mostrar as primeiras imagens com ele: o runtime grava
  `.started` ao lado da `.so` depois de 120 quadros, ou `.start_failed` (com o motivo) quando recusa
  o driver. De volta ao lançador, um teste que terminou sem `.started` (recusa, ou o app fechou/caiu)
  é desfeito: volta o driver que já funcionou no aparelho, ou nenhum, e o lançador diz isso numa
  janela, com o motivo, propondo baixar o próximo da lista. O pacote que falhou não é mais
  recomendado nesse aparelho. Sair do jogo antes das primeiras imagens não conta como falha.
- "Importar driver Turnip (.zip)" continua: aparelho sem rede, ou um build preferido. Aceita o
  formato AdrenoTools (meta.json + .so na raiz) e o pacote DroidDeck (usa só a parte `android/`).
- Os pacotes vêm direto do GitHub de quem os publica (que oferece o código-fonte Mesa
  correspondente); não passam pelos nossos servidores nem pelo APK.
- Para confirmar o driver em uso: `[vk] active driver: turnip Mesa driver (driverID=18, ...)`
  no `lasrecomp.log`; sem driver escolhido, `[vk] system GPU driver (no custom driver selected)`.
- Um driver escolhido que não carrega (arquivo ilegível, adrenotools falha, sem dispositivo
  Vulkan 1.3) **para o app com uma mensagem**: o jogo nunca volta sozinho ao driver do sistema.
  O mesmo vale para um driver do sistema sem Vulkan 1.3 (Adreno 730 no Android 16: Vulkan 1.1),
  onde só um Turnip roda o jogo.
- Lançador e jogo dividem o mesmo processo: `LARECOMP_GPU_DRIVER` é apagada quando o driver
  do sistema é escolhido, senão o driver de um lançamento anterior continuava ativo.

## 3. Atraso de ~2s na primeira abertura pós-v0078 (provável custo único)

- Causa provável: recompilação em massa de shaders — o `gcopy` mudou o GLSL de todos os
  shaders com memória global → hash mudou → cache SPIR-V invalidou → shaderc recompila
  tudo no primeiro uso.
- O cache persiste em `cache/spirv/` no storage externo. Se voltar em toda abertura,
  investigar a persistência do cache.

## 4. Sombras blocadas na Viridian Forest (parcialmente tratado)

- v0080 forçou samplers de sombra de NEAREST para LINEAR (PCF bilinear 4 taps em hardware).
  Resultado visual **não confirmado**.
- Se sem mudança, próxima investigação = tamanho do shadow map.
- Remoção total das sombras NÃO implementada (existe `LARECOMP_NO_SHADOW`, força
  `VK_COMPARE_OP_ALWAYS`; decisão pendente).

## 5. Performance na floresta (não resolvido)

- Teto de ~13–14 fps na Viridian Forest em 1x (720p), ~17 mil draws de vegetação.
- `vk_wait_seq()` é GPU real; uploads proporcionais aos draws.
- Instancing/merging fora de escopo sem profiling (P3 pendente).

## Como ajudar

Se você investigar o texto invisível, comece por **ISSUES.md §1** e pelo resultado da
v0090 — não repita as eliminações acima. Logs úteis: `[gcopy]`, `[font-draw]`,
`[font-geo]`, `[diag] v0085/v0086/v0088/v0089` e o screenshot do diálogo.
