# Palier A — builder Android local (D218)

Entrée : dossier **déjà extrait**, choisi via le sélecteur existant, contenant
`extracted/exefs/{main,sdk,subsdk0,subsdk1,main.npdm}` et romfs compatible.
Version admise au premier palier : Eevee 1.0.0, identifiant NSO contrôlé avant génération.
Aucun NSP/XCI ni fichier de clés n'est lu ici (PR B distincte).

## Construire sans jeu

Exécuter le préflight de l'environnement, puis charger le SDK/NDK/JDK. Fournir
`LETSGO_KIT_SOURCE`, un kit **local** dont `upstream.apk` est l'APK sans jeu de
Nycolazs/LASRecomp-Android-Plus android-v1.2.0, SHA256 épinglé dans
`tools/stage-buildkit.py`. Le script ne télécharge rien. Disposition du kit :
`lib/arm64-v8a/libzig.so`, `assets-unpacked/{pyhome,cckit/clang/include,cckit/sysroot/usr/include}`.
Les Python/LZ4/Capstone/Unicorn et les licences sont conservés ; notre générateur
est copié du présent dépôt, jamais celui d'un autre jeu.

```sh
python3 tools/stage-buildkit.py
bash build-runtime.sh && bash link-runtime.sh
GAME=none LETSGO_BUILD_ASSETS="$PWD/build/build-assets" \
  LETSGO_APK_DEST=/chemin/prive/build/LetsGoRecomp-android.apk bash build-apk.sh
python3 -m unittest discover -s tests -v
```

Après toute modification du générateur : restager **avant** le build runtime pour
recalculer GAMELIB_ID ; le packaging refuse un kit/runtime incompatibles.
`GAME=none` ignore toute vieille bibliothèque dans apk-lib et contrôle les assets
principaux **et supplémentaires**. `GAME=with` requiert une bibliothèque/id présents
et correspondants. Les APK de recette ne sont ni attachés ni publiés.

## Sur l'appareil

Choisir le dossier puis « Importer mon jeu — dossier déjà extrait », « préparer /
reprendre ». Service foreground `specialUse`, notification de progression,
WakeLock borné à trois heures. Exécution `fork/execv` depuis nativeLibraryDir,
jamais les stubs `plat_exec`/`plat_spawn` Android ni codeCache. Stockage privé
`files/gamebuild`. Le processus service est isolé du rendu SDL.

La clé de travail inclut recette, dossier et SHA256 des quatre modules. Les EXEFS
sont copiés/vérifiés avant génération ; aucun dérivé n'est dans l'APK. Les objets
ne sont engagés qu'après exit0 et réutilisés seulement si recette/options/source
**et** hash de l'objet correspondent. Interrompre tue le groupe interpréteur/
compilateurs et attend le parent. La reprise est une action explicite utilisateur,
jamais une relance silencieuse après force-stop.

Le link emploie la liste exacte d'objets, dépend de liblasrecomp.so, crée une
bibliothèque privée en lecture seule nommée par contenu et engage `ready.json`
en dernier. Un échec ne remplace pas le précédent résultat valide. Avant SDL,
le lanceur vérifie hash bibliothèque, EXEFS, recette **de l'APK courant** et
GAMELIB_ID ; le runtime vérifie aussi son ABI/ID lors du dlopen.

## Limites

Pas d'importeur NSP/XCI, pas d'optimisation de durée ni promesse de onze minutes.
La batterie sur secteur ne prouve pas l'autonomie ; Fold7 exclu. Les tests hôte
utilisent des C synthétiques et un vrai compilateur/éditeur de liens, pas le jeu.
Une compilation interrompue peut laisser des `.part` privés non réutilisés ;
aucune purge automatique du dossier de l'utilisateur n'est faite. Un téléphone
partagé se teste exclusivement sous fold-lock pris dans un appel séparé.
