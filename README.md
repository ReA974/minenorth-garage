# MineNorth Garage

Mod Forge 1.20.1 (`minenorth_rp_vehicles`) de gestion des véhicules MTS / Immersive Vehicles pour MineNorthRP : garage, fourrière, concessions,
pompes à carburant, ferme, immatriculations et vente de véhicule entre joueurs.

Dépend de `mts` et de `minenorth_eurobank` (voir `mods.toml`).

## Commandes

Les commandes « admin » demandent le niveau d'op `commandPermissionLevel` de la config (2 par défaut).
Les commandes de PNJ (`garagemenu`, `fourrieremenu`, `vendeur`, `vendeurvehicule`) s'exécutent en général depuis un PNJ ou la console.

### Joueurs

| Commande | Effet |
|---|---|
| `/vente <acheteur> <prix>` | Propose la vente du véhicule que tu vises à un autre joueur. |
| `/vente accepter` | L'acheteur accepte la proposition en cours. |
| `/vente refuser` | L'acheteur refuse la proposition. |
| `/vente annuler` | Le vendeur annule sa proposition. |

### Immatriculations (police ou OP)

La police est déterminée par le mod Police via l'API MineNorth. Les sous-commandes d'écriture sont réservées aux OP.

| Commande | Effet |
|---|---|
| `/immat <plaque>` | Fiche du véhicule et de son propriétaire. |
| `/immat recherche <texte>` | Recherche par plaque, nom, prénom, pseudo ou modèle. |
| `/immat joueur <joueur>` | Véhicules immatriculés au nom d'un joueur. |
| `/immat transferer <plaque> <joueur>` | OP : change le propriétaire d'une plaque. |
| `/immat supprimer <plaque>` | OP : retire la plaque du fichier. |
| `/immat export` | OP : réécrit `<monde>/minenorth_immatriculations.json`. |

### Garage (admin)

| Commande | Effet |
|---|---|
| `/garagemenu [joueur]` | Ouvre le menu du garage (PNJ garagiste). |
| `/garagezone list` | Liste les zones de garage. |
| `/garagezone wand` | Donne la baguette de sélection de zone. |
| `/garagezone select <nom> [tag]` | Crée la zone depuis la sélection à la baguette. |
| `/garagezone <nom> [rayon] [tag]` | Crée une zone circulaire à ta position (rayon par défaut dans la config). |
| `/garagezone settype <nom> <types>` | Définit les types de véhicules acceptés dans la zone. |
| `/garagezone remove <nom>` | Supprime la zone. |
| `/garageadmin add <joueur>` | Ajoute un véhicule au garage d'un joueur. |
| `/vehicledebug` | Diagnostic MTS. |

Voir, retirer ou vider le garage et la fourrière d'un joueur : panneau `/mnadmin` (mod `minenorth_admin`).

### Concessions (admin)

| Commande | Effet |
|---|---|
| `/vendeur <concession> [joueur]` | Ouvre une concession (PNJ vendeur). |
| `/vendeurvehicule [joueur]` | Ouvre la concession par défaut. |
| `/vendeuracces <concession> tous` | Ouvre la concession à tout le monde. |
| `/vendeuracces <concession> mairie` | Réserve la concession à la mairie (maire, agents et staff). |
| `/vendeuracces <concession> entreprise <nom> [direction]` | Réserve la concession aux employés d'une entreprise active, ou à sa direction avec `direction`. |
| `/vendeurzone set <concession>` | Définit la zone de sortie des véhicules à ta position. |
| `/vendeurzone remove <concession>` | Retire cette zone. |
| `/vendeurzone list` | Liste les zones. |
| `/vloaddata` ou `/vehicleshop reload` | Recharge `catalog.json`. |
| `/vehicleshop check` | Vérifie que les objets du catalogue existent. |

### Fourrière (admin)

| Commande | Effet |
|---|---|
| `/fourrierezone set` | Définit le point de sortie de la fourrière à ta position. |
| `/fourrieremenu [joueur]` | Ouvre le menu de récupération (PNJ fourrière). |

### Pompes à carburant (admin)

Viser une pompe MTS à moins de 6 blocs.

| Commande | Effet |
|---|---|
| `/pompe limiter` | La pompe se vide à chaque plein. |
| `/pompe illimitee` | La pompe ne se vide plus. |
| `/pompe statut` | Affiche le mode et le stock de la pompe. |

### Ferme (admin)

| Commande | Effet |
|---|---|
| `/farm reload` | Recharge `farm.json`. |
| `/farm info` | Infos sur les profils de ferme. |
| `/farmzone pos1` / `pos2` | Définit les deux coins de la zone à ta position. |
| `/farmzone create <nom>` | Crée la zone entre pos1 et pos2. |
| `/farmzone list` | Liste les zones. |
| `/farmzone remove <nom>` | Supprime la zone. |

## Coffre des véhicules et extension de coffre

Chaque véhicule a un coffre serveur (sans commande, interface seulement). Il s'ouvre en **accroupi + clic droit à mains vides** sur le véhicule
(option `clicAccroupi`), ou par « Coffre du véhicule » dans le menu du garage. Le menu propose d'ouvrir le coffre et d'acheter une extension.

- **Taille** : selon la catégorie du catalogue (`config/minenorth-coffre.toml`, `[tailles] categories`, format `Catégorie;base;max`, multiples de 9, 9 à 54), valeur par défaut pour les catégories inconnues. Les anciens véhicules reçoivent leur coffre à la première ouverture.
- **Extension** : paliers payants (espèces ou carte, remise de grade appliquée), `emplacementsParPalier` et `prixDesPaliers` configurables, jusqu'au maximum de la catégorie ; réservée au propriétaire, liée au véhicule. `zoneGarageRequise` impose une zone garage.
- **Accès** : véhicule verrouillé (MTS) = coffre fermé sauf pour son propriétaire ; les OP suivent les mêmes règles. Tout est contrôlé côté serveur.
- **Persistance** : contenu et palier sont dans les données de l'entité (`mnrp_coffre`) : ils suivent le véhicule au garage, à la fourrière et à la vente. Un seul conteneur partagé par véhicule, pas de duplication.
- **Disparition hors garage** (destruction, revente au receleur du mod vol, ramassage) : le contenu est lâché au sol avant la suppression. L'assurance rend le véhicule avec le palier mais un coffre vide.
