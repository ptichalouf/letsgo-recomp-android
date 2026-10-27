# Résolution et format d'image (Android)

Trois niveaux, de natures différentes :

| Niveau | Ce que ça change | Coût | État |
|---|---|---|---|
| (a) Préréglages de hauteur | `LARECOMP_SCALE` = hauteur / 1080 : 480p, 540p, 720p (défaut), 900p, 1080p, 1440p, « Native de l'écran » (`native` : les pixels de l'écran, 2000x1125 dans la boîte 16:9 d'un écran 2000x1812) | Liste du lanceur | Sûr : même chemin qu'avant, image 16:9 avec bandes noires |
| (b) Résolution libre L x H | « Personnalisée » : `1440x1080` donne `LARECOMP_SCALE=1.0` et `LARECOMP_ASPECT=1440:1080` ; le rendu est vraiment 1440x1080 (échelle 0,75 x 1,0), placé dans une boîte 4:3 de l'écran | Largeur indépendante (`sx = h * shape`, `sy = h`) + crochet de caméra | Expérimental : voir (c) |
| (c) Remplir l'écran | `LARECOMP_ASPECT=fill` : la forme de la fenêtre ; sur un écran presque carré, le champ vertical grandit et le champ horizontal reste celui du jeu (« Vert+ ») | Crochet de caméra `rt_cam_ldp` (3 lectures `ldp s,s,[xN,#0xd0]` du champ / de l'aspect, `tools/patches.py`) | 3D correcte, interface 2D étirée |

## Ce qui est démontré (Fold4, écran 2176x1812, Eevee 1.0.0)

- Le jeu accepte un champ plus haut : la caméra passe de fov 0,5236 / aspect 1,778 à 0,7553 / 1,2009 (`[cam]` dans `lasrecomp.log`).
- Le professeur Chen de l'introduction, en « Remplir », se superpose au 16:9 à l'échelle 1,0 et à la même place (corrélation 0,92 ; 0,45 à l'échelle 1,48) : la 3D n'est pas déformée, on voit simplement plus haut et plus bas.
- L'interface 2D est dessinée dans la même image plein écran que la 3D : la boîte de dialogue passe d'environ 210 à 310 lignes (x 1,48 = 1 / forme), et l'icône de la Poké Ball Plus de l'écran des manettes devient 1,5 fois plus haute que large. Le tri « composites plein écran / interface dans une boîte 16:9 » de `vulkan/exec.c` ne voit pas cette interface, parce qu'elle n'est pas dessinée directement dans l'image présentée.

## Ce qu'il reste à faire pour (b) et (c)

Garder l'interface dans une boîte 16:9 : trouver la passe où le jeu compose l'interface (la cible qu'il présente ensuite par une copie plein écran) et y appliquer la boîte de `exec.c`. Tant que ce n'est pas fait, les formats autres que 16:9 sont marqués « interface étirée » dans le lanceur.

Les autres versions du jeu (Pikachu 1.0.0, mises à jour) n'ont pas les adresses du crochet : leur image reste en 16:9 (`output_scale.c` n'élargit qu'une fois que la caméra est passée par le crochet).

Test sans le jeu : `tools/test_output_format.c` (commande en tête du fichier).
