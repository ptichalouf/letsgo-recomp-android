# Réglages — plan d'implantation t_238ac775

But : solder B2 du MASTER validé f561c12, sans APK ni changement du jeu.
Architecture : deux vues dans la même activité ; contrôles existants rangés en six sections toujours ouvertes. Les mêmes instances et leurs callbacks restent en place. Rail de 248 dp uniquement pour largeur >=600 dp ET hauteur >=480 dp ; ailleurs colonne unique (la carte prime sur MASTER §4). Barre persistante et fondu 48 dp sur les volets. Réagencement par reparentage, sans recréer les contrôles.
Stack : Java / vues Android plateforme ; aucun ajout de dépendance.

- [x] Écrire dans apk/test/SettingsJumpTest.java une sonde de structure du lanceur + règles géométriques ; vérifier le rouge sur main (six sections absentes).
- [x] SettingsJump.java : wide(width,height), active(tops,scrollY,canScrollDown), target(top,margin) ; tester seuils 599/600 et 479/480, dernières sections courtes, défilement inverse.
- [x] LetsGoLauncherActivity.java : construire six groupes titrés ; déplacer les réglages dans ces groupes ; écran accueil/Réglages, retour système et raccourcis directs, rail synchronisé, conservation de section/position au pliage et recréation.
- [x] Ajouter sec_image/sec_game/sec_controls dans values, values-en, values-fr ; lancer gen-strings.py (source XML vers table maquette, et non l'inverse).
- [ ] Compiler tous les Java du lanceur avec SDL et android-34/android.jar, exécuter les tests Java et la sonde structure, conserver logs ; auto-relire le diff, commit conventionnel, push unique et PR interne vers main, revue native.

Points de vigilance : bornes compactes ; section Journal trop courte pour atteindre le haut ; rotation pendant Réglages (garder un décalage relatif) ; retour système vers accueil ; callbacks de paramètres inchangés. La sonde de structure est statique et ne prétend pas prouver le rendu/TalkBack : la carte de déploiement porte la recette sur appareil. Hors périmètre : thème global, zone de confort, moteur, formats, APK, appareils et publication amont.

## Profils de scène — t_8586394d

Décision Michel 27/10 16:36 : raccordement rendu refusé, refus de bascule conforme. Deux colonnes dans Image, Monde et Combat / Menus, mêmes contrôles (hauteur interne, SMAA, format). Les six clés `profile_{world,battle}_{height,smaa,aspect}` restent dans l’INI du jeu. Le bouton « Utiliser au prochain démarrage » applique manuellement le profil aux réglages de lancement existants ; aucune mutation de rendu pendant la partie. Une valeur manuelle non représentée est conservée, pas réécrite en ouvrant l’écran.

L’état publié par `fps60_profile_scene()` est atomique et mis à jour à chaque présentation, même en mode 30 : `FIELD_UPDATE` observé => Monde ; construction de combat/capture ou chemin `fps60_scene_begin()` => Combat/Capture jusqu’au retour du monde ; sans ces marqueurs => Menu/Chargement. Il ne prétend pas distinguer chaque type d’écran de chargement. Les hooks n’altèrent ni registre ni minuterie en mode 30 ; le hook d’accès à la minuterie reste conditionné au mode expérimental/recherche.

**Refus de bascule :** `[profiles] … switch=refused reason=renderer-not-prewarmed`. `settings_profiles_rejected()` retourne vrai ; `settings_profile_apply()` ne change aucun réglage du moteur. Le format et l’échelle sont mémorisés au démarrage dans output_scale ; SMAA crée ses pipelines au premier usage ; les images redimensionnées sont réallouées. Sans toucher ces chemins réservés, une simple écriture dans `g_settings` serait à la fois inefficace pour le format et non garantie sans trou. Pré-chauffage NON implémenté, aucune promesse de gain.

Vérifications : `clang -D_GNU_SOURCE -I src/runtime src/tools/test_scene_profiles.c -o build/test_scene_profiles -lm && build/test_scene_profiles` : SCENE_OK / SETTINGS_OK / GUARD_OK. `SettingsJumpTest` : 34 vérifications. Compilation Java contre android-34 + SDL, compilation et liaison runtime NDK r28c. Recette appareil et identités binaires dans le rapport de carte ; avant/après i/s et pires écarts **NON MESURÉS** (la bascule est refusée).

Retour arrière : revenir au paquet précédent par reconstruction avec versionCode supérieur et même signature, jamais désinstaller ni vider les données. Les clés supplémentaires sont ignorées par l’ancien runtime. Le raccordement au rendu aura sa carte après libération de la carte deux zones ; ne pas étendre cette PR à output_scale.c, nvn/**, platform/**, LetsGoActivity ou scripts de build.
