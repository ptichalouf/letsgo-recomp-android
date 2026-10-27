# Écarts entre le lanceur livré et le système de design

Base : `livrable-fold7` @ 232bc5a. Les numéros de ligne renvoient à cette version. Chemins relatifs à la racine du dépôt.
Le tri suit le coût de l'implantation :

- **A. Java pur, structure inchangée** : la colonne unique reste, on change thème, styles, contrôles et comportements. Livrable seul, sans risque pour le jeu.
- **B. Casser la colonne unique** : accueil en deux volets, écran Réglages séparé, états d'erreur structurés.
- **C. Rendu du jeu** : la position de l'image, la zone de confort, l'orientation et la cadence. On touche au natif (`src/runtime/…`), ou à `LetsGoActivity` et `TouchControls`, qui doivent suivre le même rectangle que le rendu.

## Vue d'ensemble

| A. Java pur (structure inchangée) | B. Plusieurs écrans | C. Rendu du jeu |
|---|---|---|
| A1 Thème Holo → DeviceDefault/Material | B1 Accueil en deux volets | C1 Image ancrée en haut / au-dessus du pli |
| A2 Lanceur et journal non forcés en paysage | B2 Écran Réglages séparé (rail + volet) | C2 Bascule de position sans redémarrer |
| A3 Signal de suite (barre + fondu) | B3 Tuiles = raccourcis vers une section | C3 Commandes dans la zone du bas |
| A4 Largeur de contenu plafonnée | B4 États d'erreur structurés | C4 Orientation en jeu |
| A5 Un seul bouton principal | B5 Progression de téléchargement | C5 Posture mi-pliée (capteur de charnière) |
| A6 « Jouer » désactivé avec sa raison | B6 Cinq clés nouvelles | C6 Cadence régulière plutôt que haute |
| A7 CheckBox → Switch pleine ligne | B7 Relayout au pliage et à la rotation | C7 Écran noir au démarrage |
| A8 Couleurs en jetons (sombre/clair) | B8 Découper l'activité de 1029 lignes | C8 Formats non 16:9 et zone du bas |
| A9 Titres et rythme vertical | B9 Feuilles de sélection au lieu de Spinner | C9 Mode de présentation à mesurer |
| A10 Pilotes en RadioGroup | B10 Préférence « zones » lue par le jeu | |
| A11 « OK » codés en dur | | |
| A12 « FPS », « LOG » codés en dur | | |
| A13 Retour au toucher, vibration | | |
| A14 Mouvement réduit | | |
| A15 Thème clair qui suit le système | | |
| A16 Nom du pilote lisible | | |
| A17 Journal au même thème | | |
| A18 Messages longs hors Toast | | |

---

## A. Java pur, sans toucher à la structure

| # | Écart constaté | Correction | Fichier(s) |
|---|---|---|---|
| A1 | Le thème de l'application est `@android:style/Theme.NoTitleBar.Fullscreen` : boutons, cases et listes déroulantes ont l'aspect Holo (dégradé gris de la capture) | `Theme.DeviceDefault.NoActionBar.Fullscreen` (One UI) ou `Theme.Material.NoActionBar.Fullscreen`, thèmes de la plateforme ; les fonds viennent ensuite de `Ui` | `apk/AndroidManifest.xml:26` ; `apk/res/values/styles.xml` (le style `Fullscreen` du jeu est déjà Material) |
| A2 | Lanceur et journal forcés en paysage : sur l'écran intérieur, la rotation « ne marche pas » | retirer `screenOrientation` des deux activités (orientation libre). Le lanceur déclare déjà `configChanges` (`:31`), sa colonne se réajuste sans recréation ; le journal, qui ne le déclare pas, est recréé et relit le journal (sans effet de bord) | `apk/AndroidManifest.xml:30`, `:40` |
| A3 | « Paramètres » coupé à mi-hauteur sans signal de suite | `scroll.setScrollbarFadingEnabled(false)`, `setVerticalFadingEdgeEnabled(true)`, `setFadingEdgeLength(dp(48))`, `setScrollBarStyle(SCROLLBARS_INSIDE_INSET)` | `apk/java/com/letsgorecomp/LetsGoLauncherActivity.java:505-508` |
| A4 | Boutons de 790 dp de large sur un écran de 832 dp | contenu plafonné à 640 dp et centré (un `FrameLayout` entre le `ScrollView` et `root`) | `LetsGoLauncherActivity.java:117-122`, `:505` |
| A5 | Tous les boutons se ressemblent ; « Jouer » ne se distingue que par sa position | « Jouer » en bouton principal (`primary`, 72 dp, 20 sp) ; dossier et journal en secondaire ; « Oublier ce dossier » en bouton texte `error` | `LetsGoLauncherActivity.java:138-207` |
| A6 | « Jouer » non prêt = `alpha 0.4`, la raison n'apparaît qu'en Toast au toucher | état désactivé stylé, et `not_ready` (clé existante) affiché sous le bouton | `LetsGoLauncherActivity.java:846-848` (`refreshStatus`), `:148` |
| A7 | Réglages en `CheckBox` (cible = la case) | `Switch` sur une ligne de 56 dp, toute la ligne bascule | `LetsGoLauncherActivity.java:306-316`, `:438-477` |
| A8 | Couleurs `Color.rgb(…)` dispersées (une vingtaine), aucune pour le thème clair | jetons de `Ui.java` (§2 de MASTER) | `LetsGoLauncherActivity.java:120, 127, 133, 217, 223, 229, 302, 308, 313, 320, 352, 353, 371, 408, 440, 447, 458, 469, 500` ; `LogActivity.java:31, 41, 93` |
| A9 | Titres de section en 16-18 sp blancs, espacements 4/8/12/16/20/24 sans règle | `titleM` + échelle 8/16/24/32 | `LetsGoLauncherActivity.java:213-219`, `:299-304` |
| A10 | La liste des pilotes est un `TextView` cliquable : chaque toucher **fait tourner** le pilote choisi (« touche pour changer ») | un `RadioGroup`, une ligne par pilote, badge `worked_before` / `on_trial` / `did_not_start` | `LetsGoLauncherActivity.java:280-297`, `:599-648` |
| A11 | Quatre dialogues ont un bouton `"OK"` codé en dur | `android.R.string.ok` (traduit par le système) | `LetsGoLauncherActivity.java:704`, `:709`, `:729`, `:758` |
| A12 | Le tiroir tactile affiche `"FPS"` et `"LOG"` codés en dur | icônes + `setContentDescription(Txt.t(…))` | `apk/java/com/letsgorecomp/TouchControls.java:37` |
| A13 | Aucun retour visuel ni vibration sur les boutons du lanceur | `RippleDrawable` + `StateListAnimator` (90 ms, échelle 0,98) ; `performHapticFeedback` sur « Jouer » (le jeu le fait déjà) | `LetsGoLauncherActivity.java:138-207` ; référence `TouchControls.java:99` |
| A14 | Pas d'animation aujourd'hui, mais aucun garde-fou pour celles qu'on ajoute | `Ui.motionScale()` (lit `ANIMATOR_DURATION_SCALE`) appliqué à toute durée | nouveau `apk/java/com/letsgorecomp/Ui.java` |
| A15 | `configChanges` contient `uiMode` : le lanceur ne recrée rien quand le système passe en clair | `onConfigurationChanged` → réappliquer les jetons | `apk/AndroidManifest.xml:31` ; `LetsGoLauncherActivity.java` (nouvelle méthode) |
| A16 | Le pilote s'affiche « WN-Turnip-1.19-b Axxx (WN-1.19-b) » : `label()` répète la version | nom en titre, version et origine en sous-ligne | `LetsGoLauncherActivity.java:606`, `:620`, `:631` (`label()`) |
| A17 | Le journal a un autre fond (`12,14,18`) et des boutons Holo | mêmes jetons et mêmes fabriques que le lanceur | `apk/java/com/letsgorecomp/LogActivity.java:31-93` |
| A18 | Messages longs en Toast (où choisir le dossier, pilote installé, échec d'un paquet) : ils disparaissent avant d'être lus | statut écrit dans la carte concernée (`drvStatus` existe déjà) ; Toast réservé aux confirmations courtes | `LetsGoLauncherActivity.java:806-817`, `:923-940` |

## B. Casser la colonne unique en plusieurs écrans

| # | Écart | Correction | Fichier(s) |
|---|---|---|---|
| B1 | Une seule colonne pour tout | accueil en deux volets (carte du jeu + tuiles), selon la classe de taille (MASTER §4) | `LetsGoLauncherActivity.java:117-509` (`onCreate` monolithique) → `buildHome()` |
| B2 | **Traité côté code/tests — t_238ac775, recette appareil à faire.** | Réglages dans la même activité, six sections ouvertes et titrées, rail 248 dp synchronisé avec le volet ; compact en colonne unique conformément à la carte. Pas d'animation dans cette tranche. | `LetsGoLauncherActivity.java` : `section`, `layoutSettings`, `updateRail` ; test `SettingsJumpTest` et `outils/verify-settings.py` |
| B3 | Aucun raccourci | tuiles de l'accueil = valeur courante + saut vers la section (règle des trois appuis, MASTER §5.3) | `buildHome()` |
| B4 | `describe()` renvoie une phrase ; l'écran ne sait ni la gravité ni l'action qui répare | objet d'état : gravité (prêt / avertissement / bloquant), message (mêmes clés), action principale, actions secondaires, calculé depuis `GameFolder.State` | `LetsGoLauncherActivity.java:826-900` ; `apk/java/com/letsgorecomp/GameFolder.java:15-30` |
| B5 | Téléchargement montré en texte « … 42% » | `ProgressBar` horizontale déterminée sous le texte | `LetsGoLauncherActivity.java:752-830` |
| B6 | Pas de nom pour trois sections, ni pour la disposition et la capture | 5 clés : `sec_image`, `sec_game`, `sec_controls`, `layout_zones`, `capture` | `apk/res/values/strings.xml`, `values-en/`, `values-fr/` |
| B7 | Au pliage, au dépliage et à la rotation, rien ne s'adapte | `onConfigurationChanged` → recalculer la classe, reconstruire en gardant écran, section et défilement ; `ChangeBounds` 250 ms | `LetsGoLauncherActivity.java` |
| B8 | Activité de 1029 lignes | `Ui.java` (jetons, fabriques, `Icon`) + construction de l'accueil et des réglages séparée ; rien à changer au build (`build-apk.sh:18` compile tout `apk/java`) | nouveaux fichiers dans `apk/java/com/letsgorecomp/` |
| B9 | `Spinner` déroulant pour la résolution (8 choix) et la langue (10 choix) | rang de sélection + feuille de radios | `LetsGoLauncherActivity.java:318-436` |
| B10 | Aucune préférence pour la position de l'image | préférence `layout` = `zones` (défaut sur l'écran intérieur) ou `center`, dans Réglages › commandes ; lue par le jeu (dépend de C1 et C3) | `LetsGoLauncherActivity.java` ; `LetsGoActivity.java:205-215` |

## C. Ce qui touche au rendu du jeu

| # | Écart | Correction | Fichier(s) |
|---|---|---|---|
| C1 | L'image 16:9 est toujours centrée (`o->vx = (dw - o->vw)/2; o->vy = (dh - o->vh)/2`) : on ne peut pas la monter | paramètre d'alignement, `LARECOMP_VALIGN=top`, `hinge:<px>` ou `center`, posé par Java comme `LARECOMP_ASPECT`. Le rectangle part déjà dans `g_present_rect`, que le toucher du jeu utilise : il suivra sans autre changement | `src/runtime/nvn/output_scale.c:138-145` ; `src/runtime/nvn/vulkan/device.c:1218-1310` ; `src/runtime/hle/input.c:116-128`, `:671` ; `apk/java/com/letsgorecomp/LetsGoActivity.java:31-52` |
| C2 | Les variables d'environnement ne sont lues qu'au démarrage | JNI `nativeSetLayout(int)` sur le modèle de `nativeSetButton`, appliqué à la présentation suivante (réglages rapides et pliage en cours de partie) | `LetsGoActivity.java:262` ; `output_scale.c` |
| C3 | Les commandes minimales supposent une image centrée (deux bandes égales, plancher 112 dp, débordement sur l'image sinon) | mode « zones » : toutes les commandes dans la zone sous l'image (MASTER §5.2). L'image et les commandes doivent lire le même rectangle, sinon elles se décalent | `apk/java/com/letsgorecomp/TouchControls.java:172-241` (calcul des bandes `:196-198`) |
| C4 | Jeu forcé en paysage par trois chemins qui ne disent pas la même chose (`LANDSCAPE` puis `SENSOR_LANDSCAPE`) | voir `ORIENTATION.md` | `LetsGoActivity.java:29-30`, `:234`, `:254-259` ; `apk/AndroidManifest.xml:47` |
| C5 | Rien ne détecte le téléphone mi-plié | capteur `Sensor.TYPE_HINGE_ANGLE` (API 30, plateforme, sans Jetpack) : angle entre 60 et 150° en paysage sur l'écran intérieur → image au-dessus du pli. La position du pli n'est pas publiée sans `androidx.window` : on suppose le milieu de l'écran intérieur, vrai sur Fold, à confirmer sur le Fold7 | `LetsGoActivity.java` (nouvel écouteur) ; C1 |
| C6 | « Smooth » : le mode 60 i/s donne des intervalles irréguliers (mesure du 26/10 : 28 i/s en ville, pire écart 57 ms) ; le jeu tient 30 i/s natifs, que le port respecte | garder 30 i/s stables par défaut ; mesurer l'écart-type des intervalles et pas seulement la moyenne ; « 60 i/s (expérimental) » reste marqué comme tel | `src/runtime/nvn/objects.c:725-745` (`present_pace`) ; `src/runtime/hle/fps60.c:194-199` ; données `PERF-FOLD7-2026-10-26.md`, `PLAFOND-30-2026-10-27.md` |
| C7 | Écran noir entre « Jouer » et la première image | vue de démarrage dans `LetsGoActivity` (icône + progression indéterminée), retirée à la première image : `GpuDrivers` sait déjà la détecter pour l'essai des pilotes | `LetsGoActivity.java:203-215` ; `apk/java/com/letsgorecomp/GpuDrivers.java` |
| C8 | Les formats 4:3 et « Remplir » étirent l'interface 2D du jeu | le mode « zones » reste en 16:9. « Remplir » ne se combine pas avec la zone du bas tant que la passe d'interface n'est pas mise en boîte | `docs/FORMATS_IMAGE.md` ; `output_scale.c:105-145` |
| C9 | Présentation `MAILBOX` par défaut + sommeil de cadence | à mesurer avec C6 : `FIFO` est plus régulier mais ajoute une image de latence ; ne rien changer sans mesure | `src/runtime/nvn/vulkan/device.c:779-787` |

## Ordre conseillé

1. **A** entier, en une ou deux PR. Le gain est immédiat et visible, sans risque pour le jeu : thème, signal de suite, orientation libre du lanceur.
2. **B1-B4 + B6 + B9**, puis **B5, B7, B8**.
3. **C1 + C3 + B10** ensemble : la demande « jeu en haut, commandes en bas ». Ensuite **C4** (orientation), **C5** (mi-plié), **C2** (bascule à chaud).
4. **C6, C7, C9** : mesures sur le Fold7 avant de changer quoi que ce soit.
