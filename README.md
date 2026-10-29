# Let's Go Eevee · Android

[![CI](https://github.com/ptichalouf/letsgo-recomp-android/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/ptichalouf/letsgo-recomp-android/actions/workflows/ci.yml)

Port experimental e não oficial de **Pokémon: Let's Go, Eevee!** para Android,
baseado em recompilação estática. Este fork reúne o runtime, o aplicativo Android,
os testes e a documentação para continuar o trabalho em comunidade.

**Não é um emulador genérico nem um APK pronto para jogar.** A compilação completa
precisa de uma cópia do jogo obtida legalmente pelo próprio usuário e das suas
próprias chaves. Não solicite nem publique esses arquivos aqui.

[Estado e próximos passos](docs/ROADMAP.md) · [Compilar](BUILD.md) ·
[Contribuir](CONTRIBUTING.md) · [Guia para assistentes de IA](AGENTS.md) ·
[Índice para LLMs](docs/INDEX-FOR-LLMS.md)

## Configuração automatizada por LLM · AC-Création

A configuração deste fork e do seu fluxo de contribuição foi automatizada com
assistentes de IA (LLMs) por **[AC-Création — ac-creation.com](https://ac-creation.com)**:
documentação, orientação para os assistentes, preparação do ambiente, testes e
verificações automáticas das contribuições.

Os assistentes podem ler as instruções em [`AGENTS.md`](AGENTS.md), localizar os
componentes pelo [índice do repositório](docs/INDEX-FOR-LLMS.md) e propor uma PR com
comandos e resultados reproduzíveis. Se uma ferramenta não carregar `AGENTS.md`
automaticamente, forneça o arquivo como contexto.

**Automação não é prova de funcionamento.** A assistência de IA deve ser declarada;
os testes, a revisão e as limitações da verificação devem acompanhar cada correção.
A aprovação de uma PR não equivale a testar o jogo em um telefone.

## Estado deste fork · 29 de outubro de 2026

| Área | O que está estabelecido |
|---|---|
| Execução do jogo | O projeto de origem documenta boot, mundo 3D, batalhas, menus e saves no Galaxy S23. As capturas abaixo são do build histórico v0090, não uma validação da versão atual. |
| Aplicativo Android | Código do importador, controles, instalação e ordenação de mods, resolução e perfis Mundo / Combate-Menus presente neste fork. |
| Desempenho | PRs [#2](https://github.com/ptichalouf/letsgo-recomp-android/pull/2) e [#3](https://github.com/ptichalouf/letsgo-recomp-android/pull/3) integradas: cópias de buffers, suspensão em segundo plano e regressões dos perfis de renderização. A CI não mede FPS. |
| Compatibilidade dos shaders | Fallback para GPUs sem `shaderInt64` integrado na [PR #5](https://github.com/ptichalouf/letsgo-recomp-android/pull/5), com regressões sintéticas. A legibilidade do texto no telefone ainda precisa de confirmação. |
| Empacotamento do APK | [PR #4](https://github.com/ptichalouf/letsgo-recomp-android/pull/4) integrada: falhas do ZIP, DEX ausente e bibliotecas nativas obrigatórias ausentes são recusadas antes da publicação do arquivo. |
| Testes automáticos | Referência da CI após #4: **57 testes na raiz, com 6 ignorados explicitamente, e 8 testes do aplicativo**, sem falhas. Não incluem execução do jogo em aparelho. |

**Texto invisível continua como problema a validar em aparelho.** O fallback de
shaders foi integrado, mas um teste no computador não prova o resultado visual.
[`ISSUES.md`](ISSUES.md) preserva as investigações históricas;
[`docs/ROADMAP.md`](docs/ROADMAP.md) separa o trabalho integrado da validação pendente.

Este repositório contém o **port recompilado**. Raitchi é outro projeto, com outro
runtime: resultados, defeitos e correções de um não validam automaticamente o outro.

## Começar

### Ajudar sem ter o jogo

Você pode contribuir com documentação, testes sintéticos ou correções isoladas.
Os testes usam pequenas bibliotecas e arquivos fictícios, sem código nem dados do jogo.

No Linux x86_64, o ambiente de CI utiliza:

- Python 3, compiladores C/C++, `make`, `zip` e `unzip`;
- JDK 17;
- Android SDK: plataforma `android-34` e build-tools `35.0.0`;
- Android NDK `28.2.13676358`.

```bash
# Na raiz de um clone Git completo, com o ambiente instalado:
source tools/env.sh
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s apk/test -p 'test_*.py' -v

# Verificações do conteúdo e da documentação:
python3 tools/check_no_game_content.py
python3 tools/check_no_secrets.py --history
python3 tools/check_line_endings.py
python3 tools/check_doc_links.py
```

`tools/env.sh` procura ferramentas já instaladas e configura variáveis; **não instala
nem baixa nada**. Consulte o [workflow de CI](.github/workflows/ci.yml) para o ambiente
reproduzível. Alguns testes precisam do histórico Git para comparar o código antes
e depois de um corretivo: use um clone completo, não apenas o ZIP do repositório.

Os testes de espera do SDL, do kit privado e da validação SPIR-V são ignorados quando
os seus pré-requisitos específicos estão ausentes. Um teste ignorado não é um teste
aprovado; cada execução informa o que ficou sem verificar.

### Compilar para uso pessoal

Siga o [guia de compilação](BUILD.md), com seu próprio dump, suas próprias chaves e
as dependências Android preparadas. O guia contém as etapas do runtime, do
importador, da biblioteca do jogo e do APK.

O comando `GAME=none ./build-apk.sh` monta o aplicativo sem a biblioteca do jogo;
`GAME=with ./build-apk.sh` exige uma biblioteca pessoal compatível. Um APK que
contenha código ou dados derivados do jogo **não deve ser publicado**.

## Contribuir com ou sem IA

1. Leia a [roadmap](docs/ROADMAP.md) e os [problemas conhecidos](ISSUES.md).
2. Abra uma [issue](https://github.com/ptichalouf/letsgo-recomp-android/issues) para
   combinar o escopo, especialmente se for alterar o renderizador.
3. Trabalhe em uma branch baseada em `publication`: **um problema por PR**.
4. Inclua a reprodução, o teste antes/depois e as limitações. Não apresente resultados
   previstos como medições.
5. Siga [`CONTRIBUTING.md`](CONTRIBUTING.md) e, para assistência de IA,
   [`AGENTS.md`](AGENTS.md) e [a política de contribuição assistida](.github/AI_CONTRIBUTING.md).

As branches `master` e `publication` são protegidas: alterações passam por PR,
com os dois controles de CI aprovados e a base atualizada. Push direto, exclusão e
reescrita do histórico são bloqueados. A proteção não concede exceção ao proprietário.

## O que há no repositório

| Caminho | Conteúdo |
|---|---|
| `src/runtime/` | Runtime HLE, tradução NVN→Vulkan e integração de plataforma. |
| `src/tools/` | Ferramentas de geração e diagnóstico. |
| `apk/` | Aplicativo Android, launcher e controles. |
| `importer/` | Importação do dump do próprio usuário. |
| `tests/`, `apk/test/` | Testes sintéticos e testes JVM. |
| `tools/` | Detecção de ambiente e verificações do repositório. |
| `docs/` | Roadmap, auditorias, relatórios e índice de navegação. |

**Nunca envie** cópias do jogo (`.xci`, `.nsp`, `.nca`, `.exefs`, `.romfs`…), chaves
(`prod.keys`, `title.keys`), arquivos extraídos, código gerado, fontes de sistema
Switch (`.bfttf`), texturas, modelos, música, textos do jogo, saves ou mods que
contenham arquivos do jogo. Isso vale também para issues, comentários e PRs.

## Capturas históricas · Galaxy S23, v0090

<details>
<summary>Ver capturas do projeto de origem, anteriores às correções atuais</summary>

Estas imagens registram o estado antigo, inclusive diálogos e menus sem texto.
Os números de FPS das capturas não são um benchmark da versão atual.

| Mundo e diálogo | Batalha |
|---|---|
| ![Captura histórica do mundo, com diálogo sem texto](screenshots/01-mundo-casa-dialogo.jpg) | ![Captura histórica de uma batalha](screenshots/06-batalha.jpg) |

Mais capturas em [`screenshots/`](screenshots/).

</details>

## Origem, créditos e licenças

Este fork continua o trabalho de
[CowKillers / letsgo-recomp-android](https://github.com/CowKillers/letsgo-recomp-android).
Os créditos e as licenças dos autores originais são preservados.

- [raulzrrs / letsgo-recomp](https://github.com/raulzrrs/letsgo-recomp) — recompilação estática original.
- [peiikai / LASRecomp](https://github.com/peiikai/LASRecomp) — base do runtime.
- [Nycolazs / LASRecomp-Android-Plus](https://github.com/Nycolazs/LASRecomp-Android-Plus) — referência de port Android.
- Canal indicado pelo projeto de origem: [emuucheck](https://youtube.com/@emuucheck).

Consulte [`LICENSE`](LICENSE), [`LICENSE-LASRecomp`](LICENSE-LASRecomp),
[`LICENSES/`](LICENSES/) e [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
A atribuição à AC-Création refere-se ao trabalho neste fork, não à autoria do jogo
ou de todo o runtime original.

Projeto não oficial, sem afiliação com Nintendo, The Pokémon Company ou Game Freak.
As marcas pertencem aos respectivos titulares.
