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
