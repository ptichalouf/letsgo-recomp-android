# Orientation : décision

**Décision : orientation adaptée au pliage, en deux temps.**

1. **Tout de suite (Java pur, sans risque pour le jeu).** Le lanceur et le journal passent en orientation libre. Le jeu reste en paysage, mais dans les deux sens : `SENSOR_LANDSCAPE` partout, au lieu du mélange actuel `LANDSCAPE` / `SENSOR_LANDSCAPE`.
2. **Avec la zone de confort (colonne C des écarts).** L'orientation du jeu dépend de l'écran :
   - **écran externe** (`smallestScreenWidthDp` < 600) : paysage seulement, dans les deux sens ;
   - **écran intérieur** (≥ 600) : libre. L'image se cale en haut, les commandes vont dans la zone du bas, quelle que soit la tenue. En posture mi-pliée (capteur de charnière), l'image tient au-dessus du pli.

Les deux options écartées : **« libre partout »** (en portrait, l'écran externe réduit l'image à 411 dp de large) et **« paysage seulement en jeu »** (elle ne répond pas à la demande « jeu en haut, réglage confortable en bas » quand on tient l'appareil autrement).

## Ce qui est en place aujourd'hui

| Où | Ce qui est forcé | Fichier |
|---|---|---|
| Lanceur | `landscape` | `apk/AndroidManifest.xml:30` |
| Journal | `landscape` | `apk/AndroidManifest.xml:40` |
| Jeu (manifeste) | `landscape` | `apk/AndroidManifest.xml:47` |
| Jeu, `onCreate` et `onResume` | `SCREEN_ORIENTATION_LANDSCAPE` (un seul sens) | `apk/java/com/letsgorecomp/LetsGoActivity.java:30`, `:234` |
| Jeu, appel natif de SDL | `setOrientationBis` surchargé → `SENSOR_LANDSCAPE` (deux sens) | `LetsGoActivity.java:254-259` |

Sur l'écran intérieur, presque carré, « paysage » et « portrait » se distinguent de peu, et c'est le système qui tourne le contenu quand l'appareil change de tenue. D'où le constat de l'utilisateur : « la rotation ne fonctionne pas ».

## Géométrie (calculée, densité 2,625)

Image 16:9 du jeu calée en haut ; « zone » = ce qui reste dessous pour les commandes.

| Écran, tenue | Image (px) | Zone de commandes | Remarque |
|---|---|---|---|
| Intérieur Fold7 2184×1968, paysage, à plat | 2184×1229 | 739 px = 282 dp | le pli (y = 984) traverse l'image vers les 80 % de sa hauteur |
| Intérieur Fold7, paysage, **mi-plié** | 1712×963 | 984 px = 375 dp | image entière au-dessus du pli, 8 dp de marge |
| Intérieur Fold7 1968×2184, portrait (tenue « livre ») | 1968×1107 | 1077 px = 410 dp | image 10 % plus étroite qu'en paysage ; pli vertical au milieu de l'image |
| Intérieur brief 2000×1812, paysage, à plat | 2000×1125 | 687 px = 262 dp | |
| Intérieur brief, paysage, mi-plié | 1573×885 | 906 px = 345 dp | |
| Intérieur brief 1812×2000, portrait | 1812×1019 | 981 px = 374 dp | |
| Externe 2520×1080, paysage | 1920×1080 | 2 bandes de 300 px = 114 dp | pas de zone du bas ; commandes latérales (losange en carré) |
| Externe 1080×2520, portrait | 1080×608 | 1912 px | image de 411 dp de large : trop petite → **refusé** |

Lecture :
- sur l'écran intérieur, autoriser le portrait ne coûte que 10 % de largeur d'image et ajoute 45 % de zone de commandes ;
- la tenue que l'utilisateur décrit (« une fois déplié, le jeu sur la partie haute ») correspond au **paysage mi-plié** : pli horizontal, image au-dessus, mains sur la moitié basse posée. C'est le cas qui demande le capteur de charnière (écart C5) ;
- sur l'écran externe, le portrait n'apporte rien et dégrade l'image : il reste exclu.

## Mise en œuvre

**Temps 1** (colonne A, écart A2, et ce fichier) :
- `AndroidManifest.xml:30` et `:40` : retirer `android:screenOrientation`. Le lanceur a déjà `configChanges` (`:31`) et n'est donc pas recréé ; son `onConfigurationChanged` réagence la mise en page (écart B7) ;
- `AndroidManifest.xml:47` et `LetsGoActivity.java:30`, `:234` : `sensorLandscape` / `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, comme `setOrientationBis` (`:258`). Le jeu se retourne quand on retourne le téléphone, sans changer de forme.

**Temps 2** (avec C1, C3, C5) :
- `LetsGoActivity` choisit l'orientation dans `onCreate`, `onResume` et `onConfigurationChanged` : `SCREEN_ORIENTATION_FULL_USER` si `getResources().getConfiguration().smallestScreenWidthDp >= 600`, sinon `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`. `setOrientationBis` applique la même règle au lieu d'imposer le paysage ;
- `FULL_USER` respecte le verrou de rotation du système : un utilisateur qui verrouille l'écran n'est pas contredit ;
- la position de l'image (`LARECOMP_VALIGN`, puis `nativeSetLayout`) et la disposition de `TouchControls` se recalculent à chaque changement de taille, comme c'est déjà le cas au pliage (`TouchControls.onSizeChanged`, commit bff05cd).

## Risque et garde-fou

Le commentaire de `LetsGoActivity.java:29` (« locked (not sensor) to avoid SDL resize confusion ») et les incidents `surface lost` / `surface recovery FAILED` relevés le 26 et le 27/10 montrent que le redimensionnement de la surface Vulkan est le point fragile. Deux éléments réduisent le risque :
- l'activité du jeu déclare `orientation|screenSize|…` dans `configChanges` (`AndroidManifest.xml:48`) : une rotation n'est pas une recréation ;
- le pliage et le dépliage, qui marchent aujourd'hui, passent déjà par le même chemin (la taille de la fenêtre change).

**Test d'acceptation sur le Fold7, avant d'activer le temps 2** : 20 rotations et 20 pliages en jeu. Zéro `surface lost` ou `recovery FAILED` attendu dans `lasrecomp.log`, et le toucher toujours aligné sur l'image (`g_present_rect`).

**Repli** si le test échoue : garder la règle par écran, mais figer l'orientation pour la session une fois la première image affichée (`SCREEN_ORIENTATION_LOCKED`). Le choix se fait alors au lancement, selon la tenue du moment.
