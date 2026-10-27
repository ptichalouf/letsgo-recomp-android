# Circuit de fusion de ce dépôt

Ce dépôt appartient à l'utilisateur `ac-dev-bot`, pas à l'organisation `AC-Creation`. Depuis le 27/10/2026, il suit
quand même le circuit natif de la forge :

1. Une PR ouverte (ni brouillon ni `WIP:`) sans carte d'auteur liée reçoit une carte « Revue A » pour `qa_reviewer`
   (tenant `letsgo`).
2. Avec un APPROVE de `ac-review-bot` au head exact (et la revue B si la classe de risque l'exige), et une CI verte ou
   pas de CI déclarée, le webhook fusionne la PR (squash) avec le compte `ac-merge-bot`.
3. Personne ne fusionne à la main depuis une carte worker.

Ce qui rend cela possible :

- un hook de dépôt vers `http://192.168.1.10:8644/webhooks/gitea-pr` (le hook de l'organisation ne voit pas les
  dépôts d'un utilisateur) ;
- la ligne `ac-dev-bot/letsgo-recomp-android` du fichier `/opt/data/scripts/gitea_webhook_owners.json`, lu par
  `gitea_pr_to_kanban.py`.

Si l'un des deux manque, la PR n'est pas vue et aucune carte n'est créée. Un autre dépôt de `ac-dev-bot` qui ne figure
pas dans ce fichier est ignoré, sans carte.
