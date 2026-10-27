# Mods sur Android

## Ce qui n'est pas décidé : le bouton principal

Réglages › Mods a **un seul bouton ambre**, dont l'action n'est pas tranchée. Trois actions sont codées et
interchangeables sans recompiler (`ModsPanel.java`, `Primary`) :

| Valeur | Bouton ambre | Actions secondaires |
|---|---|---|
| `file` (défaut) | « Ajouter un mod » : sélecteur du système, un `.zip`, `.rar` ou `.7z` déjà téléchargé | lien texte vers la page du jeu sur GameBanana |
| `link` | « Chercher des mods sur GameBanana » : ouvre le site dans le navigateur | « Ajouter un fichier téléchargé » |
| `catalogue` | « Explorer les mods » : la liste de GameBanana dans l'appli, téléchargement depuis `gamebanana.com/dl/<id>` | « Ajouter un fichier téléchargé », lien vers la page du jeu |

Pour essayer une valeur sur un téléphone de test :

```
adb shell setprop debug.letsgo.mods_primary link        # ou catalogue ; vide = file
# puis rouvrir Réglages › Mods (relancer l'appli)
```

**La décision revient à Alexandre.** Les faits mesurés le 27/10 pour la prendre (détail et sources :
`work/perso/letsgo-recomp-android/mods/DECOUVERTE.md`, `MOTEUR.md`, `LICENCES-75.md`, hors dépôt) :

- **Volume** : 75 mods publiés pour le jeu (GameBanana, jeu 6773, Pikachu et Évoli partagent la page), 101 fichiers,
  **818 Mo** au total, **médiane 2,0 Mo**, maximum 255 Mo. Une fois dépaquetés et les variantes séparées, ils donnent
  **94 mods** installables, que le vrai `mods.c` trouve tous (8 469 fichiers remplacés au total).
- **Ce que les auteurs ont accordé** : la licence que les conditions de GameBanana donnent au site se limite à
  l'affichage et à la diffusion **sur GameBanana** (« displaying Your Content on GameBanana and distributing Your
  Content … to users seeking to download »). Rien ne l'étend à une application tierce.
- **Une voie officielle existe pour le téléchargement intégré : le « 1-Click »** de GameBanana. Elle se négocie avec
  GameBanana (schéma d'URL propre à l'appli, contrôle de l'archive côté serveur), et **chaque auteur peut s'en
  retirer** en plaçant un fichier `.disable_gb1click` dans son archive. Le catalogue codé ici (`catalogue`) passe par
  l'API publique `apiv11`, non documentée, **hors** de ce programme : il ne peut pas lire ce refus avant d'avoir
  téléchargé l'archive.
- `file` et `link` ne téléchargent rien eux-mêmes : c'est le joueur qui récupère le fichier sur le site.

Tant que rien n'est décidé, le bouton reste sur `file`.

## Ce qui est fait, quel que soit le bouton

- **Installer** : l'archive est dépaquetée par `libmodunpack.so` (`apk/modunpack/modunpack.c`, libarchive 3.8.1 +
  liblzma 5.8.1, statiques) dans le stockage privé de l'appli, `<filesDir>/mods/<nom>/…/romfs`. Le runtime lit ce
  dossier par `LARECOMP_MODS_DIR` (`src/runtime/mods.c`, `mods_dir()`). Seuls les dossiers `romfs/` sont écrits ; les
  parties « code » (`exefs/`, `.ips`, `.pchtxt`) sont comptées et ignorées ; une archive à plusieurs variantes donne
  un dossier par variante.
- **Activer / désactiver** : un interrupteur par mod. Le mod actif est une préférence du lanceur, passée au runtime
  par la variable `LARECOMP_MOD` (`LetsGoActivity`, puis `mods.c`, `active_mod()`). Vide = aucun mod. **Aucun fichier
  n'est déplacé ni réécrit** pour activer ou désactiver. Sans `LARECOMP_MOD` (PC), c'est la clé `mod` de
  `larecomp.ini` qui s'applique, comme avant.
- **Un seul mod actif** à la fois : le moteur n'a qu'un emplacement. En activer un désactive l'autre.
- **Retirer** : supprime `mods/<nom>` (un dossier placé directement dans `mods/`, liens non suivis). Les sauvegardes ne
  sont pas touchées.
- **Le dossier du jeu du joueur n'est jamais écrit** par l'écran Mods : ni `mods/`, ni `larecomp.ini`.
- L'origine d'un mod installé depuis le catalogue (identifiants GameBanana, auteur, page) est notée dans
  `<filesDir>/mods/<nom>/.letsgo-mod.properties`.

### Si le bouton `catalogue` était retenu

- Le fichier doit être servi en https par `gamebanana.com` ou `*.gamebanana.com` ; sa taille et son MD5 doivent être
  ceux de l'API (101/101 conformes le 27/10).
- Installation dans l'appli refusée (la page du mod s'ouvre à la place) pour : un mod avec classement de contenu
  (`_bHasContentRatings`) ou dont la visibilité initiale n'est pas `show` ; un fichier non `clean` à l'antivirus de
  GameBanana ou non `ok` à son analyse ; un fichier archivé ; un format autre que zip, rar ou 7z. Le 27/10, aucun des
  101 fichiers n'était dans ce cas.
- Reste ouvert : le respect de `.disable_gb1click`, et la question juridique ci-dessus.

## Construire et tester

```
./build-modunpack.sh       # build/apk-lib/libmodunpack.so (tarballs libarchive et xz dans deps/, SHA-256 vérifiés)
./build-apk.sh             # l'emballe avec les autres (avertit s'il manque)

AJ=$ANDROID_SDK_ROOT/platforms/android-34/android.jar
javac -encoding UTF-8 -cp $AJ -d /tmp/mst apk/java/com/letsgorecomp/{ModStore,GameBanana,ModsPanel,ModUnpack,Txt}.java apk/test/ModStoreTest.java
java -cp /tmp/mst:$AJ com.letsgorecomp.ModStoreTest      # depuis la racine du dépôt
```

## Limites connues

- Un seul mod actif à la fois (moteur). Pour en empiler plusieurs, `mods.c` devrait chercher dans une liste ordonnée.
- Correctifs de code (exefs, IPS, pchtxt) : jamais appliqués. Ce portage exécute du code recompilé, pas le programme
  d'origine. Un mod qui mêle données et code est chargé sans son code, ce qui peut le rendre incohérent.
- Un mod qui ne contient qu'une langue de textes (`bin/message/<Langue>/`) force cette langue dans le jeu
  (`mod_language`) : c'est voulu pour les traductions, mais un mod de gameplay qui retouche quelques textes anglais fait
  aussi passer le jeu en anglais.
- Mods en fichiers « en vrac » (sans `romfs/`) : refusés avec la raison.
- Le dépôt ne contient ni mod ni fichier de jeu.
