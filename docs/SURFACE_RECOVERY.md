# Rendu noir après changement de surface — t_2b131062

## Diagnostic établi
Le risque décrit dans `docs/design/ORIENTATION.md` (« Risque et garde-fou » ; le brief
l'appelle `docs/ORIENTATION.md`) se reproduit sur Fold4 SM-F936B, Android16, Turnip,
publication `deb0fceb889aeec48b60a804ef4a7be7a8635ec4`.
Séquence : rotation système manuelle1 → HOME → rotation0 → retour à la tâche via
MAIN/LAUNCHER. Les deux captures témoin `witness-c/d`, espacées d'au moins8s, sont
identiques (SHA256 `9614897b63acfdcf49f8a52115fdafc5094932339ba08b263efcaf51b721c340`),
alors que le journal `[vk]` indique30fps.

La présentation **s'arrête**, elle n'est pas démontrée comme réussissant vers une
surface morte : BufferQueue abandonnée, tentative de reconstruction échouée
`vkCreateAndroidSurfaceKHR=-1000000001`, puis `PRESENT_WAITING_FOR_WINDOW` sans
nouvelle tentative après `surfaceCreated`. Un compteur moteur n'est pas un compteur
`vkQueuePresentKHR`, et VK_SUCCESS ne prouverait pas le scanout.

Deux causes corrigées : ANativeWindow définitivement cachée ; état WAITING sans
réveil. Un Surface Java peut garder son identité pendant le remplacement natif :
réacquisition de la cible native, référence équilibrée, pas d'IsSameObject comme
génération. Cache de classe SDL résolu depuis Java (FindClass sur pthread attaché
provoquait ClassNotFoundException). Les callbacks SurfaceHolder signalent les
changements ; seul le thread rendu reconstruit surface/swapchain.

## Garde-fou et recette diagnostique
Après8s sans présentation acceptée / avec cible invalidée, un overlay Android
indépendant de Vulkan journalise le silence et propose « Reprendre l'image ».
Le geste consomme DOWN, annule sur CANCEL/sortie ; le bouton demande une reconstruction.

Injection inactive par défaut : avant un nouveau lancement du jeu, définir
`debug.larecomp.diag=1` **et** `debug.larecomp.force_display_stalled=1`.
Java exporte alors `LARECOMP_FORCE_DISPLAY_STALLED=1`, sinon l'efface ; le fallback
historique LARECOMP_DIAG seul n'active pas cette sonde. Après une première image,
le thread rendu retient l'acquisition, sans image ni sémaphore acquis, et laisse le
moteur tourner. La demande de reconstruction libère cette sonde one-shot. Maintien
au-delà8s jusqu'au bouton (pas d'expiration qui masquerait une panne de bouton).
Remettre les deux propriétés à0 et relancer après la recette.

## Vérifications réelles du 27/10/2026
- `python3 -m unittest discover -s tests -v` : quatre tests ciblés, probes C/Java
  extraites du produit ; pas une simulation du framework. Génération1→2→invalid→3,
  geste DOWN/UP/CANCEL/sortie/orphelin/caché, porte36 combinaisons, injection off/on
  et reprise one-shot. Les deux régressions de revue échouaient sur3d32bca ; les deux
  nouvelles sondes échouaient avant ajout de l'injection.
- JDK17/SDK/NDKr28c réels : runtime66 objets SMOKE_OK, lien RUNTIME_LINK_OK,
  javac/d8/aapt2, APK signée et alignement16Ko vérifiés.
- APK privée1793115329, SHA256
  `568a04a4837e9c6ce822b63f47e6f1b21e26f4ed72fec4e7a9cf68954718ca6c`, install-r
  Success, UID/dossier conservés. Cette APK contient le jeu : **ne pas publier**.
- Récupération automatique : nouveau surfaceCreated16:37:09.084 → READY09.110,
  soit26ms. Deux captures16:37:09.702/16:37:15.120 différentes dans la zone du jeu ;
  presents acceptés120→240→360.
- Injection : début16:37:30.458 ; moteur30fps, frames67→127→187→247 ; bandeau
  visible16:37:39.191 (`silent_ms=8739`, seuil8s + polling1s).
- Tap bouton16:38:19.801 → READY19.818 (17ms), bandeau caché16:38:20.197 ;
  capture avant/après différente et jeu visible. `guard-visible.png` du premier
  script était **prématurée** (match visible=true d'un autre tag) : ne pas la citer ;
  la bonne capture est `guard-after8s.png`. Filtre du script corrigé.
- Lanceur rotations0 et1 observées dans XML, rail Paramètres et sections Image/Mods
  exercés sans changer de réglage ; lanceur/build-apk identiques à publication.
- Fold4 rendu accueil/force-stop, diagnostic0/force0, rotation0/auto1 relus, verrou
  libéré. Aucun Fold7 ni commande device_state forcée. Pliage physique différé
  explicitement par Michel ; les20 rotations/20 pliages du futur temps2 ne sont
  pas revendiqués par ce correctif.

Preuves privées durables : `/opt/data/work/t_2b131062/preuves-rotation/` (témoin),
`preuves-finales/` (logs bruts, XML, captures horodatées), rapports de reprise voisins.
Captures et APK restent privées ; cette PR ne contient que code/tests/diagnostic.

## Contradiction ciblée
1. Contre-exemple testé : même Surface Java, cible native remplacée sans observer
   l'intervalle invalide ; la nouvelle génération est acquise.
2. Non couvert : scanout physique / pilote totalement bloqué dans vkQueuePresent,
   pliage réel Fold7, validation20/20 du futur placement d'image.
3. Alternative écartée : identifier la génération par IsSameObject (ne détecte pas
   le remplacement natif), ou forcer l'orientation du lanceur (régression UX).
