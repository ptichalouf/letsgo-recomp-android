# Let's Go Recomp · système de design du lanceur (MASTER)

Version 1, 27/10/2026. Base de code : `livrable-fold7` @ 232bc5a (worktree `lg-ui`, branche `design-systeme`).
Rien n'est implanté en Java ici. Ce document décrit ce que l'étape Java suivante doit construire, jeton par jeton.

Fichiers liés :
- `maquettes/accueil.html`, `maquettes/reglages.html`, `maquettes/jeu-deux-zones.html`, `maquettes/erreurs.html` : les quatre prototypes cliquables ;
- `ECARTS.md` : les écarts entre l'existant et ce système, en trois colonnes, avec les fichiers du dépôt ;
- `ORIENTATION.md` : la décision sur l'orientation ;
- `outils/gen-strings.py` : copie les libellés des `strings.xml` vers les maquettes.

---

## 0. Cadre

**Ce qui est demandé** (verbatim de l'utilisateur) : « j'aimerais plus smooth » et « pouvoir bouger l'écran ou le clavier, exemple une fois déplié mettre le jeu sur la partie haute et avoir un setup plus confortable en bas ».

**Ce qui est en cause aujourd'hui**, d'après la capture réelle (`lanceur-etat.png`) et le code :
- une seule colonne de boutons pleine largeur dans un `ScrollView` (`LetsGoLauncherActivity.java:117`, `:505`). « Paramètres » est coupé à mi-hauteur de l'écran et rien n'indique qu'il y a une suite ;
- le thème du manifeste est `Theme.NoTitleBar.Fullscreen` (`AndroidManifest.xml:26`), d'où les boutons gris à dégradé de l'époque Holo ;
- le lanceur est forcé en paysage (`AndroidManifest.xml:30`) ;
- en jeu, l'image 16:9 est centrée (`output_scale.c:145`) et les commandes sont réparties dans deux bandes étroites, en haut et en bas (`TouchControls.java:196-198`).

**Résolution réelle.** Le brief indique 2000×1812 pour l'écran intérieur. La capture fournie mesure **2184×1968** : c'est l'écran intérieur du Fold7 (SM-F966B). Les maquettes proposent les deux, plus l'écran externe 2520×1080. La densité, mesurée, est **2,625 (420 dpi)** : `GamepadOverlay` dessine un bouton de 56 dp en 147 px (`avant-fold7-ui.xml`). En dp, cela donne :

| Écran | px | dp | Classe |
|---|---|---|---|
| Intérieur (brief) | 2000×1812 | 762×690 | étendue |
| Intérieur (Fold7 mesuré) | 2184×1968 | 832×750 | étendue |
| Externe, paysage | 2520×1080 | 960×411 | hauteur compacte |
| Externe, portrait | 1080×2520 | 411×960 | largeur compacte |

**Réglages des curseurs** (`design-taste-frontend`) : variété 3/10 (outil, pas vitrine), mouvement 4/10 (fluide mais utile), densité 5/10 (trois écrans très différents). Style : sombre, sobre, à surfaces tonales, sans ombre portée, avec un seul accent. Ce n'est pas un style « gaming néon ».

**Fiches utilisées, et ce qui en a été écarté**
- `ui-ux-pro-max` : domaines `ux` (cibles tactiles, retour au toucher, mouvement réduit, durées et courbes, récupération après erreur, indicateurs de progression), `style` (dark-mode-oled), `color`, `typography` (« Material You MD3, Roboto System »). `pro-rules.md` sert de liste de contrôle (§9).
  - Écarté : le motif « Hero + Features + CTA » et les polices Google Fonts (Outfit/Work Sans, Russo One) proposés par `--design-system`. Ces recommandations visent des pages web d'accroche. Le lanceur n'a pas de réseau au build, donc la police est celle du système (`sans-serif`).
  - Écarté : la palette « Gaming » (violet néon et rose).
  - **Le domaine `--stack` n'a pas servi** : la stack Android natif programmatique (Java, vues construites en code, sans XML ni Compose) est absente du catalogue. Aucune recette d'une autre stack n'a été reprise.
- `design-taste-frontend` : sa section 13 exclut le mobile natif. Seules ses règles de maquette ont servi (verrou de thème, verrou d'accent, verrou de rayons, contraste des CTA, aucun tiret long dans les pages, relecture des textes).
- **Continuité** : le fond `#14161C` (`Color.rgb(20,22,28)` actuel) et l'ambre « Évoli » `#E8A95B` de `maquette-accueil.html` validée le 26/10 sont conservés.

---

## 1. Principes

1. **Un écran, une action principale.** Un seul bouton ambre à la fois : « Jouer » quand le jeu est prêt, sinon l'action qui répare (choisir le dossier, autoriser l'accès). Un dialogue porte sa propre action principale, l'écran derrière est voilé.
2. **Tout se voit ou s'annonce.** Aucun contenu n'est coupé sans signal. Le rail des réglages montre toutes les sections. Un volet qui défile garde sa barre visible et un fondu de 48 dp en bas.
3. **Trois appuis au plus** depuis l'accueil, pour chaque réglage existant (tableau en §5.3).
4. **Le jeu ne se recouvre pas.** Quand il y a de la place (écran intérieur), aucune commande n'est dessinée sur l'image.
5. **Le mouvement explique.** Une animation dit d'où vient une chose et où elle va. Pas d'animation décorative, rien pendant le jeu qui coûte du GPU.
6. **Aucun texte inventé.** Tout libellé passe par `Txt.t(clé, littéral PT)`. Une clé qui manque est créée dans les trois `strings.xml` (§7).

---

## 2. Couleurs

Thème **sombre par défaut**. Le thème clair suit le système (`Configuration.uiMode & UI_MODE_NIGHT_MASK`). Le lanceur déclare déjà `uiMode` dans `configChanges`, donc `onConfigurationChanged` doit réappliquer les jetons. Les valeurs vont dans `Ui.java` (nouveau), en constantes `0xAARRGGBB`.

| Jeton | Sombre | Clair | Usage |
|---|---|---|---|
| `bg` | `#14161C` | `#F5F3EF` | fond de fenêtre |
| `surface` | `#1C1F27` | `#FFFFFF` | cartes, tuiles, feuilles |
| `surface2` | `#252A33` | `#ECE8E1` | boutons secondaires |
| `surface3` | `#2E3440` | `#E2DDD4` | piste de progression |
| `line` | `#3A4250` | `#D6CFC4` | séparateurs **décoratifs** seulement |
| `ctl` | `#6E7787` | `#857B6C` | contour des interrupteurs, radios, champs (≥ 3:1) |
| `text` | `#ECEEF2` | `#1B1D22` | texte principal |
| `text2` | `#B3BAC5` | `#474D57` | valeurs, texte secondaire |
| `text3` | `#9AA2AE` | `#5E6570` | aide, chemins, métadonnées |
| `primary` | `#E8A95B` | `#9A5A12` | fond du bouton principal, interrupteur actif |
| `onPrimary` | `#22170A` | `#FFFFFF` | texte sur `primary` |
| `primaryText` | `#F0B872` | `#8A520F` | texte ambre (lien, rail actif) |
| `primarySoft` | ambre 14 % | ambre 22 % | bouton tonal, rail actif |
| `error` / `errorSoft` | `#F19A8F` / 12 % | `#B3261E` / 8 % | erreur bloquante |
| `warn` / `warnSoft` | `#E5C07B` / 12 % | `#7A5800` / 9 % | avertissement non bloquant |
| `ok` | `#8FD3A0` | `#256B3A` | « a déjà fonctionné ici », compteur i/s |
| `statePress` | blanc 12 % | encre 10 % | calque d'appui (RippleDrawable) |
| `scrim` | `#050608` à 62 % | `#14120E` à 55 % | derrière une feuille ou un dialogue |
| `zoneBg` | `#0B0C0F` | `#E9E5DE` | zone de confort sous l'image du jeu |

**Contrastes, calculés (WCAG 2.x)**

| Paire | Sombre | Clair |
|---|---|---|
| `text` sur bg / surface / surface2 | 15,6 / 14,2 / 12,4 | 15,2 / 16,9 / 13,8 |
| `text2` sur bg / surface / surface2 | 9,3 / 8,4 / 7,4 | 7,7 / 8,5 / 7,0 |
| `text3` sur bg / surface / surface2 | 7,0 / 6,4 / 5,6 | 5,3 / 5,9 / 4,8 |
| `primaryText` sur bg / surface | 10,2 / 9,3 | 5,8 / 6,4 |
| `primaryText` sur `primarySoft` | 7,1 | 5,5 |
| `onPrimary` sur `primary` | 8,6 | 5,5 |
| `primary` sur bg (contour du bouton) | 8,8 | 4,9 |
| `error` sur `errorSoft` | 6,1 | 5,8 |
| `warn` sur `warnSoft` | 7,4 | 5,7 |
| `ok` sur surface / surface2 | 9,4 / 8,2 | 6,5 / 5,3 |
| `ctl` sur surface (non textuel, ≥ 3) | 3,7 | 4,2 |

Le texte est partout ≥ 4,5:1 et les contours de contrôle ≥ 3:1. `line` reste sous 3:1 : il ne sert qu'à séparer, jamais à délimiter un contrôle. Au clair, l'ambre `#E8A95B` ne fait que 1,85:1 sur le fond. Le bouton principal passe donc à `#9A5A12` avec un texte blanc. La teinte reste la même, le contraste devient correct.

---

## 3. Typographie

La police est celle du système : `Typeface.create("sans-serif", …)`, c'est-à-dire Roboto, ou SamsungOne sur One UI. Pas de police embarquée. Tailles en **sp** : `setTextSize(TypedValue.COMPLEX_UNIT_SP, x)`, pour suivre la taille de texte du système.

| Rôle | sp / interligne | Graisse | Usage |
|---|---|---|---|
| `titleL` | 22 / 28 | `sans-serif-medium` | nom de l'appli, titre d'écran |
| `titleM` | 18 / 24 | medium | titres de section, titre de feuille |
| `titleS` | 15 / 20 | medium | libellé d'une tuile ou d'un rang |
| `bodyL` | 16 / 24 | regular | état du jeu, message d'erreur |
| `body` | 14 / 20 | regular | texte courant |
| `bodyS` | 13 / 18 | regular | valeurs, aide |
| `caption` | 12 / 16 | medium, +0,02 em | en-têtes de groupe, badges |
| `button` | 15 / 20 | medium | boutons (pas de majuscules forcées : `setAllCaps(false)`) |
| `play` | 20 / 24 | `sans-serif` gras 600 | « Jouer » |
| `mono` | 13 / 18 | `Typeface.MONOSPACE` | chemins, compteur i/s |

Règles :
- les titres passent à la ligne au lieu d'être tronqués ;
- une valeur tient sur deux lignes au plus (`setMaxLines(2)` + `TextUtils.TruncateAt.END`) ;
- un chemin tient sur une ligne, tronqué au milieu (`TruncateAt.MIDDLE`).

---

## 4. Espacements, formes, mise en page

- **Échelle (dp)** : 4 · 8 · 12 · 16 · 24 · 32 · 48. Une rangée de contrôles est séparée de la suivante par 8 dp au moins.
- **Rayons** (un seul système) : 12 dp pour les contrôles (boutons, champs), 16 dp pour les tuiles, 20 dp pour les conteneurs (cartes, feuilles, dialogues), 28 dp ou pilule pour le rail actif.
- **Élévation** : aucune ombre. La profondeur vient des surfaces (`bg` < `surface` < `surface2`). Seuls les feuilles et les dialogues ont un voile (`scrim`).
- **Cibles** : 48 dp au minimum dans les deux sens. Tailles courantes : 56 dp pour un rang, 64 dp pour une tuile, 72 dp pour « Jouer » (64 dp en hauteur compacte), 48×48 pour un bouton icône.
- **Icônes** : grille de 24 dp, trait de 2 dp, coins arrondis, une seule famille. Elles sont dessinées en `Canvas` dans `Icon.java` (lignes, arcs, cercles) : pas de VectorDrawable XML ni de bibliothèque. Jeu complet, 21 icônes : dossier, puce, image, globe, manette, liste, curseurs, chevrons, retour, lecture, alerte, coche, télécharger, importer, appareil photo, jauge, viseur, disposition, plus, fermer, partager.
- **Encarts système** : 16 dp réservés en bas pour la barre de gestes. Le contenu fixe ne passe jamais dessous.

**Classes de taille** (`Ui.sizeClass()`, calculée depuis `Configuration.screenWidthDp` et `screenHeightDp`, recalculée dans `onConfigurationChanged`) :

| Classe | Condition | Accueil | Réglages |
|---|---|---|---|
| étendue | largeur ≥ 600 et hauteur ≥ 480 | 2 colonnes : carte du jeu (1,08 fr) + tuiles (1 fr), marges 32/24 | rail 248 dp + volet (contenu ≤ 640 dp) |
| hauteur compacte | hauteur < 480 | 2 colonnes ; tuiles en grille 2×3 de 56 dp ; aide masquée (elle reste dans Réglages > Journal) | rail 208 dp, rangs de 48 dp |
| largeur compacte | largeur < 600 | 1 colonne qui défile (barre visible, fondu) | rail transformé en rangée d'onglets défilante |

---

## 5. Composants

Chaque composant est une fabrique de `Ui.java` (`Ui.primaryButton(ctx, clé, littéral)`, etc.). Elles ne fabriquent que des vues de la plateforme : `Button`, `TextView`, `Switch`, `RadioButton`, `ProgressBar`, `LinearLayout`, `FrameLayout`, `ScrollView`, `GradientDrawable`, `RippleDrawable`.

### 5.1 Catalogue

| Composant | Anatomie | Recette Java |
|---|---|---|
| **Bouton principal** | fond `primary`, texte `onPrimary`, rayon 12 (Jouer : 20), icône 24 + 12 dp d'écart | `GradientDrawable` couleur + rayon, dans un `RippleDrawable(ColorStateList.valueOf(statePress), fond, null)` ; `setAllCaps(false)` ; `setMinHeight(dp(48))` |
| **Bouton tonal** | fond `primarySoft`, texte `primaryText` | idem |
| **Bouton secondaire** | fond `surface2`, texte `text` ; au clair, contour 1 dp `line` (sinon il se perd sur la carte blanche) | idem + `GradientDrawable.setStroke` |
| **Bouton texte** | sans fond, texte `primaryText` (ou `error` pour « Oublier ce dossier ») | `RippleDrawable` sans fond, masque arrondi |
| **Désactivé** | sans fond, contour 1 dp `ctl`, texte `textOff` (`#6B7280` / `#9A958C`), **raison écrite juste dessous** (`not_ready`) | `setEnabled(false)` ; `ColorStateList` à état `-state_enabled` ; pas d'`alpha` 0,4 global |
| **Tuile de navigation** | 64 dp ; icône 24 · titre (`titleS`) · valeur (`bodyS`, 2 lignes max) · badge facultatif · chevron | `LinearLayout` horizontal cliquable, fond `surface` rayon 16 + ripple |
| **Tuile à interrupteur** | idem, `Switch` à droite ; **toute la tuile** bascule | `Switch` teinté (`setThumbTintList`, `setTrackTintList`), tuile en `OnClickListener` qui appelle `toggle()` |
| **Rang radio** | 56 dp ; rond 20 dp · libellé · sous-ligne · badge | `RadioGroup` vertical de `RadioButton` teintés, `setButtonTintList` |
| **Rang de sélection** | 56 dp ; titre + valeur + chevron bas ; ouvre une feuille | remplace `Spinner` (résolution, langue du jeu) |
| **Carte** | `surface`, rayon 20, marge interne 8 (rangs) ou 24 (contenu) | `GradientDrawable` |
| **Alerte** | carte `errorSoft` ou `warnSoft` + contour 1 dp de la couleur à 40 % ; icône alerte · titre · message · actions | `role=alert` côté accessibilité : `setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE)` |
| **Badge d'état** | pilule 24 dp de haut ; icône 14 + texte `caption` ; jamais la couleur seule | `TextView` + `setCompoundDrawables` |
| **Feuille** | depuis le bas, largeur ≤ 560, rayon 20 en haut, poignée 32×4, voile | vue posée sur le `FrameLayout` racine et animée par `ViewPropertyAnimator`. Pas de `Dialog`, dont les animations de fenêtre exigeraient des XML `anim/` |
| **Dialogue** | centré, ≤ 520 dp, titre `titleM` + icône, corps `body`, actions à droite | `AlertDialog` avec le thème `Theme.DeviceDefault.Dialog.Alert` (plateforme) ; boutons `android.R.string.ok` ou clés existantes |
| **Progression** | barre de 4 dp, piste `surface3`, valeur `primary`, texte au-dessus | `new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)` + `setProgressTintList` |
| **Rail** | rangs 56 dp, rayon 28 ; actif : fond `primarySoft` et texte `primaryText` ; suit la section visible | `LinearLayout` vertical ; `ScrollView.setOnScrollChangeListener` met à jour l'actif |
| **Volet qui défile** | barre toujours visible, fondu de 48 dp en bas tant qu'il reste du contenu | `setScrollbarFadingEnabled(false)`, `setVerticalFadingEdgeEnabled(true)`, `setFadingEdgeLength(dp(48))` |
| **Toast** | celui du système, pour les confirmations courtes seulement | les messages longs (erreurs, chemins) vont dans la carte concernée |

### 5.2 Les écrans

- **Accueil** (`maquettes/accueil.html`)
  - Carte du jeu : nom de l'appli, état du dossier (`describe()`), chemin, « Jouer » (72 dp), puis « Choisir un autre dossier » et « Voir le journal », puis l'aide.
  - À droite, des tuiles : pilote (nom + badge `worked_before`), résolution, format, langue du jeu, puis les deux interrupteurs « Commandes tactiles » et « Gyroscope ». La dernière ligne mène à « Paramètres ».
  - Si une alerte s'affiche (pilote manquant), elle prend la place des deux interrupteurs. Ils restent à trois appuis.
- **Réglages** (`maquettes/reglages.html`) : six sections.
  - Dossier : état, « Choisir un autre dossier », « Oublier ce dossier ».
  - Pilote graphique : appareil, installés en radio, « Télécharger le pilote recommandé… », « Importer… ».
  - ⟨sec_image⟩ : résolution, format, 60 i/s, SMAA.
  - ⟨sec_game⟩ : langue du jeu, combats sauvages.
  - ⟨sec_controls⟩ : commandes tactiles, disposition complète, gyroscope, ⟨layout_zones⟩.
  - Journal : voir, partager, aide.
  - Ouvert depuis une tuile, l'écran arrive directement sur la bonne section.
- **Jeu + zone de confort** (`maquettes/jeu-deux-zones.html`)
  - Déplié à plat : image 16:9 en haut, sur toute la largeur. Dessous, une zone avec L et R au-dessus des colonnes des pouces, − et + au centre, le stick à gauche, le losange ABXY à droite, et une barre d'icônes (viseur, capture, journal, ⋯) avec le compteur i/s.
  - Mi-plié : l'image tient entière au-dessus du pli (8 dp de marge), la zone occupe toute la moitié basse.
  - ⋯ ouvre les réglages rapides **dans la zone, sans recouvrir l'image**.
  - Replié : bandes latérales. Le losange devient un carré tourné de 45° (Y X / B A) pour tenir dans 114 dp.
- **Erreurs** (`maquettes/erreurs.html`) : cinq cas.
  - Dossier absent : erreur ; « Choisir un autre dossier » devient le bouton ambre ; « Jouer » est désactivé, avec la raison dessous.
  - Accès aux fichiers : « Autoriser l'accès à tous les fichiers » devient le bouton ambre.
  - Pilote manquant : avertissement, « Jouer » reste actif (le pilote du système prend le relais, `none_selected`).
  - Pas de réseau et pilote refusé : les deux `AlertDialog` existants, mis au thème.

### 5.3 Trois appuis au plus (vérifié dans les maquettes)

| Réglage existant | Chemin depuis l'accueil | Appuis |
|---|---|---|
| Dossier : en changer | « Choisir un autre dossier » | 1 |
| Dossier : l'oublier | Paramètres › (s'ouvre sur Dossier) › Oublier | 2 |
| Pilote : en choisir un installé | tuile Pilote › rang radio | 2 |
| Pilote : télécharger / importer | tuile Pilote › bouton | 2 (+ confirmation existante) |
| Langue du jeu | tuile Langue › sélection › choix | 3 |
| Résolution | tuile Résolution › sélection › choix | 3 |
| Format d'image | tuile Format › radio | 2 |
| 60 i/s, SMAA | tuile Résolution › interrupteur | 2 |
| Combats sauvages | tuile Langue › interrupteur | 2 |
| Commandes tactiles | interrupteur sur l'accueil | 1 (3 si une alerte le remplace) |
| Disposition tactile complète | Paramètres › ⟨sec_controls⟩ › interrupteur | 3 |
| Gyroscope | interrupteur sur l'accueil | 1 (3 si une alerte le remplace) |
| Journal | « Voir le journal » | 1 |

---

## 6. Mouvement et fluidité

### 6.1 Jetons

| Jeton | Durée | Courbe (`PathInterpolator`) | Pour |
|---|---|---|---|
| `press` | 90 ms | (0.2, 0, 0, 1) | calque d'appui, échelle 0,98 |
| `fast` | 150 ms | sortie (0.3, 0, 0.8, 0.15) | départ d'un écran ou d'une feuille, fondu enchaîné |
| `std` | 220 ms | arrivée (0.05, 0.7, 0.1, 1) | arrivée d'un écran, changement sur place |
| `big` | 280 ms | arrivée | montée d'une feuille, déplacement de l'image ou de la zone |
| progression | continue | `LinearInterpolator` | barre de téléchargement |

La sortie dure environ 65 % de l'arrivée. Tout est interruptible : un appui pendant une animation agit tout de suite, l'animation ne bloque rien.

**Mouvement réduit.** `Ui.motionScale()` lit `Settings.Global.ANIMATOR_DURATION_SCALE`. À 0 (réglage « supprimer les animations »), toutes les durées passent à 0 et seuls les changements d'état restent. `ValueAnimator` le fait déjà de lui-même. `ViewPropertyAnimator` et les transitions passent par la même échelle.

### 6.2 Motifs

| # | Motif | Règle | Recette |
|---|---|---|---|
| M1 | Retour au toucher | réponse visible en moins de 100 ms sur tout élément touchable ; vibration courte sur « Jouer » et sur les touches du jeu | `RippleDrawable` + `StateListAnimator` (échelle 0,98, 90 ms) ; `performHapticFeedback(VIRTUAL_KEY)` (déjà fait par `TouchControls.java:99`) |
| M2 | Pas de saut de mise en page | un état qui change (statut, pilote, téléchargement) remplace son contenu en fondu, à la même place, dans un bloc de hauteur réservée | `TextSwitcher` ou `TransitionManager.beginDelayedTransition(bloc, new Fade().setDuration(150))` |
| M3 | Continuité spatiale | Accueil → Réglages : l'écran arrive de la droite (24 dp, `std`) ; retour : sortie `fast` vers la droite ; une feuille monte du bas | échange de deux vues dans un `FrameLayout`, `animate().translationX().alpha()` ; retour par `OnBackInvokedCallback` (API 33) ou `onBackPressed` |
| M4 | Raccourci vers une section | la tuile ouvre Réglages **déjà défilés** sur la section, sans animation de défilement ; le rail met la section en évidence | `scrollTo` avant le premier affichage |
| M5 | « Jouer » | à l'appui, l'icône pulse aussitôt (état occupé) et la vue est verrouillée contre le double appui ; l'activité du jeu démarre aussitôt | `setEnabled(false)` + animation d'alpha en boucle ; aucune attente simulée |
| M6 | Pliage, dépliage, rotation : lanceur | la mise en page change de classe en gardant l'écran, la section et le défilement ; les volets glissent vers leur nouvelle place | `TransitionManager.beginDelayedTransition(racine, new ChangeBounds().setDuration(250))` puis reconstruction |
| M7 | Pliage, dépliage, rotation : jeu | les commandes s'effacent en 80 ms, se replacent, puis reviennent en 150 ms. Pas de saut, pas de touche orpheline sous le doigt (`releaseAll()` existe déjà) | `TouchControls.onSizeChanged` → `animate().alpha(0)` → `requestLayout()` → `animate().alpha(1)` |
| M8 | Cadence perçue en jeu | une cadence **régulière** paraît plus fluide qu'une cadence plus haute mais irrégulière : 30 i/s stables par défaut, 60 reste « expérimental ». Les commandes, dessinées par l'interface Android au rafraîchissement de l'écran, suivent le doigt sans attendre l'image du jeu | voir `ECARTS.md` C6 (mesures du 26/10 : le mode 60 donne 28 i/s en ville et un pire écart de 57 ms) |
| M9 | Budget GPU en jeu | rien d'animé par-dessus l'image : pas de flou, pas de transparence animée. En mode « zones », aucune vue ne recouvre la surface du jeu | la feuille des réglages rapides vit dans la zone ; la seule exception est l'écran replié, sur action explicite |

---

## 7. Libellés

- Toute vue nouvelle passe par `Txt.t(clé, littéral PT)` ; les fichiers `apk/res/values{,-en,-fr}/strings.xml` restent la source.
- Un libellé de champ s'obtient par `Ui.label(clé)` : le même texte, sans le « : » final ni le « · » initial (« Résolution interne : » devient « Résolution interne »). Aucune clé n'est dupliquée pour ça.
- Les noms des langues du jeu restent les endonymes de `LetsGoLauncherActivity.java:412` : ce sont des données, pas des traductions.
- **Clés nouvelles (5)**, marquées `⟨clé⟩` dans les maquettes. Leur texte est à rédiger dans les trois fichiers au moment de l'implantation. Ce document en donne le sens, pas le libellé :

| Clé | Sens | Où |
|---|---|---|
| `sec_image` | nom de section : résolution, format, cadence, lissage | rail et titre de section des Réglages |
| `sec_game` | nom de section : langue du jeu, combats sauvages | idem |
| `sec_controls` | nom de section : commandes tactiles, gyroscope, disposition | idem |
| `layout_zones` | interrupteur : sur un écran intérieur, image du jeu en haut et commandes dans la zone du bas | Réglages › commandes ; réglages rapides en jeu |
| `capture` | action : capture d'écran (aujourd'hui seule `capture_on`, la confirmation, existe) | réglages rapides en jeu |

---

## 8. Ce que les maquettes simulent, et comment les relire

- Dans l'écran simulé, 1 px CSS = 1 dp, et l'écran est agrandi par la densité (2,625) : sa taille en pixels est exactement celle de l'écran réel. « Zoom 1:1 px » affiche un pixel d'écran par pixel physique.
- Commandes de la page (hors téléphone) : langue PT/EN/FR, thème auto/sombre/clair, écran (3 tailles), tenue paysage/portrait. Sur la page jeu, en plus : posture et position de l'image. Sur la page erreurs : l'état.
- Les données d'exécution (nom du jeu, chemin, appareil, pilote) sont celles de la capture réelle. Les libellés viennent de `commun/strings.js`, généré par `outils/gen-strings.py` depuis les `strings.xml` du dépôt (180 clés × 3 langues, @ 232bc5a).
- Les maquettes sont du HTML et du CSS. Seuls les jetons, les tailles, l'ordre et les comportements sont à reprendre ; le code HTML ne se transpose pas en Java.

---

## 9. Contrôle avant livraison (`pro-rules.md`, « Pre-Delivery Checklist »)

Vérifié par un audit automatique dans un navigateur sur les 4 pages × 3 écrans × 2 tenues × 3 langues × 2 thèmes, avec le mouvement réduit émulé :
- [x] toutes les cibles touchables ≥ 48×48 dp. Aucune exception, y compris le stick, les touches de jeu et les boutons icônes ;
- [x] aucune commande de jeu ne recouvre l'image en mode « zones », dans les deux postures et les deux tenues. Seul le mode « Centré (actuel) », montré pour comparaison, le fait ;
- [x] aucune collision entre commandes de jeu, aucun débordement de texte de bouton, aucun élément hors de l'écran ;
- [x] l'accueil tient sans défilement en classe étendue et en hauteur compacte, erreurs comprises. Seule la largeur compacte (externe en portrait) défile, avec barre et fondu ;
- [x] texte ≥ 4,5:1 et contours de contrôle ≥ 3:1, dans les deux thèmes (§2) ;
- [x] un seul bouton plein ambre par écran (un dialogue modal porte le sien, l'écran derrière est voilé) ; un seul rayon par famille ; pas d'emoji comme icône, une seule famille d'icônes ;
- [x] la couleur n'est jamais seule : badge = icône + texte, erreur = icône + message ;
- [x] mouvement réduit : toutes les durées à 0, aucun état bloqué ;
- [x] parcours cliquables : tuile → bonne section, feuille de sélection → valeur mise à jour, téléchargement → progression puis confirmation (libellés existants), retour, Jouer → jeu, ⋯ → feuille dans la zone, bascule de position avec relayout animé ;
- [x] aucun tiret long dans les pages des maquettes (règle de `design-taste-frontend`).

Non vérifiable ici, à faire sur le téléphone à l'implantation :
- [ ] TalkBack : ordre de lecture et annonces (les maquettes posent rôles et `aria-label`, ce n'est pas une preuve sur Android) ;
- [ ] texte système au plus grand (`fontScale` 1,3 et plus) : les tailles en sp et les titres qui passent à la ligne sont prévus pour, mais non mesurés ;
- [ ] encoche ou capteur sous l'écran intérieur : la zone sûre n'a pas été mesurée sur le Fold7.
