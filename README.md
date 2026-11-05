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

Avec le mod côté client, le bouton « Vendre » de l'écran du garage ouvre un écran de vente : véhicule (celui choisi dans le garage est présélectionné), acheteur parmi les joueurs proches (le plus proche en premier, affiché avec son nom RP de carte d'identité) et prix. L'acheteur reçoit un écran avec l'offre (vendeur, véhicule, plaque, prix, son solde) à accepter ou refuser ; les boutons du chat restent disponibles.

Le bouton « Assurer » de l'écran du garage assure directement le véhicule sélectionné dans la liste, même rangé (carte si possible, sinon espèces ; même prix que le menu d'assurance).

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
| `/garagezone orienter <nom>` | Les véhicules de ce garage sortiront tournés dans le sens où tu regardes (arrondi au quart de tour). |
| `/garagezone remove <nom>` | Supprime la zone. |
| `/garageadmin add <joueur>` | Ajoute un véhicule au garage d'un joueur. |
| `/garageadmin rangertout` | Range tous les véhicules posés (zones chargées) dans le garage de leur propriétaire, même hors ligne, intacts. Compte rendu par joueur ; les véhicules sans propriétaire et ceux dont le garage est plein restent en place. |
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

À l'achat, MTS fait apparaître le véhicule directement au centre de la zone de sortie (avec ses pièces d'origine, comme si on posait l'item), au nom de l'acheteur. Un instant après, la plaque (`mts:gvp.eu_plate`) est montée sur chaque emplacement de plaque du modèle, avec l'immatriculation enregistrée au fichier, et le plein est fait (`fillFuel`). La clé est donnée comme avant. Si le modèle n'a pas d'emplacement de plaque reconnu, les plaques sont données en main ; si l'apparition échoue, l'acheteur reçoit l'item à poser dans la zone (ancien fonctionnement).

Écran de la concession : champ de recherche (nom du modèle ou catégorie, sans accents ni majuscules) et pastilles de catégorie toutes visibles (« Tous » + une par catégorie, avec le nombre de modèles) : un clic pour filtrer. Quand la place manque, une pastille « + n » ouvre un panneau avec les catégories restantes.

Fiche affichée dans l'écran de la concession : champs optionnels `"seats"`, `"trunk"` et `"speed"` (km/h) sur chaque véhicule de `catalog.json`.

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
(option `clicAccroupi`), ou par « Coffre du véhicule » dans l'ancien menu coffre du garage (l'écran du garage n'a plus de bouton Coffre). Le menu propose d'ouvrir le coffre et d'acheter une extension.

- **Taille** : selon la catégorie du catalogue (`config/minenorth-coffre.toml`, `[tailles] categories`, format `Catégorie;base;max`, multiples de 9, 9 à 54 : scooter/moto petits, citadine moyenne, berline/SUV plus grands, utilitaire/camion très grands). Le nom peut n'être qu'une partie de la catégorie (« Moto » vaut pour « Motos »). Sinon taille par type (`[tailles] types` : `terre`, `air`, `mer`), puis valeur par défaut. Un coffre déjà plus rempli que sa nouvelle taille garde tout son contenu. Sur un serveur existant, les nouvelles listes par défaut ne remplacent pas celles déjà écrites dans le fichier de config : les recopier à la main. Les anciens véhicules reçoivent leur coffre à la première ouverture.
- **Extension** : paliers payants (espèces ou carte, remise de grade appliquée), `emplacementsParPalier` et `prixDesPaliers` configurables, jusqu'au maximum de la catégorie ; réservée au propriétaire, liée au véhicule. `zoneGarageRequise` impose une zone garage.
- **Touche C** (« Coffre du véhicule », catégorie MineNorth d'Options › Commandes, réglable) : à pied, ouvre directement le contenu du coffre du véhicule visé (sinon du plus proche, à `distanceMax` blocs). Assis dans un véhicule, la touche ne fait rien de plus : C reste le klaxon de MTS. Le client n'envoie que l'appui (paquet `TrunkKeyPacket`, sans donnée) ; le serveur choisit le véhicule et refait tous les contrôles. Option `toucheCoffre`.
- **Accès** (`coffreOuvertRequis`, vrai par défaut) : pour fouiller un coffre, par la touche, le clic accroupi ou le menu, le véhicule doit être **déverrouillé** et son **coffre ouvert** (porte du coffre ouverte dans MTS), propriétaire et OP compris. Sinon : « Voiture verrouillée : déverrouille-la pour accéder au coffre. » ou « Le coffre est fermé : ouvre la porte du coffre pour y accéder. ». Le coffre se referme si quelqu'un verrouille le véhicule, ferme la porte ou s'éloigne pendant l'usage. Le menu (extension) reste accessible au propriétaire d'un véhicule verrouillé. Sur `false`, ancien fonctionnement : verrouillé = accessible au seul propriétaire, porte ignorée.
- **Porte du coffre** : lue dans MTS par réflexion. D'abord l'emplacement de coffre du modèle (type contenant `trunk`, par exemple `interactable_crate_trunk` du pack New Cars) et ses `interactableVariables` (ex. `door_b`) ; sinon une variable de `variablesPorteCoffre` (`door_trunk`, `trunk`, `hatchback`, `door_tailgate`...) déjà présente sur le véhicule. Modèle sans porte reconnue : `sansPorteCoffreOuvert` (vrai = seul le verrouillage compte).
- **Verrouillage** : MTS 24 range le verrou dans la variable `lockedVar` du véhicule ; avant ce correctif, le garage lisait un champ `locked` disparu et voyait tous les véhicules déverrouillés.
- **Persistance** : contenu et palier sont dans les données de l'entité (`mnrp_coffre`) : ils suivent le véhicule au garage, à la fourrière et à la vente. Un seul conteneur partagé par véhicule, pas de duplication.
- **Disparition hors garage** (destruction, revente au receleur du mod vol, ramassage) : le contenu est lâché au sol avant la suppression. L'assurance rend le véhicule avec le palier mais un coffre vide.

## Bornes de garage et de fourrière (04/11)
Blocs `borne_garage` (panneau P bleu) et `borne_fourriere` (panneau orange, dépanneuse), onglet créatif Garage, aperçu `apercu-bornes.png`.
Façon serveurs GTA RP : on s'approche, on regarde la borne (« [Clic droit] Ouvrir le garage » s'affiche) et clic droit = le même menu que les PNJ
(`Garage.openMenu`, `Impound.openRecoverMenu`). Écran et panneau lumineux. Incassables en survie : le staff les pose / retire en créatif,
dans la zone de garage (les véhicules sortent comme avec le PNJ).

## Filet de pêche des bateaux (04/11)
Objet `filet_peche` (onglet créatif Garage, vendu 350 € au comptoir « Poissonnerie - Matériel de pêche » du pack `poissonnerie`).
- **Clic droit à bord d'un bateau sur l'eau** : le filet est mis à l'eau. Un seul filet par bateau et par joueur ; bateau = véhicule de type « mer »
  (catégorie Bateau du catalogue, ou nom de bateau), option `bateauxSeulement`. Un filet déchiré ne se déploie pas.
- **Visible par tous** : derrière le bateau, câble, ligne de bouées rouges et blanches et nappe du filet qui bouillonne (plus agitée quand il se
  remplit). Particules envoyées par le serveur, pas de modèle 3D ; pendant la remontée, le filet se rapproche de la poupe.
- Tant que le pêcheur reste à bord et que le bateau **avance sur l'eau** entre `vitesseMinKmh` (3) et `vitesseMaxKmh` (40) km/h, un poisson est pris
  toutes les `secondesParPoisson` (45) secondes de navigation, jusqu'à `capacite` (16). La vitesse est mesurée par le serveur (distance parcourue
  chaque seconde), pas envoyée par le client.
- **Jauge** dans la barre d'action : remplissage (■■■■□□), état (pêche / avance un peu / trop vite / plein), bonus actifs, usure du filet.
- **Bonus** (`[bonus]`) : haute mer (océans profonds) +35 % de vitesse et poissons rares / épiques / légendaires x1,5 ; au large +12 % ;
  pluie +15 % (orage +24 %) ; aube et crépuscule +20 %. Près des côtes, les épaves sont deux fois plus fréquentes.
- **Poissons Star Catcher** : la table est lue dans les fiches du mod (`data/*/starcatcher/fish/*.json`, datapacks compris, relue au `/reload`) :
  mêmes objets, même chance de base (`base_chance`) et mêmes conditions que la canne (dimension, eau, biome et tags de biome, altitude, heure,
  météo). Raretés `raretes` (commun à légendaire par défaut, pas les déchets) ; poissons à appât, structure, limite de prises ou lumière exclus.
  Seuls les objets `starcatcher:*` (`espacesDeNoms`) : tous rachetés par la poissonnerie. Rien ne mord ici = message, pas de prise.
- **Remontée au treuil** (nouveau clic droit) : `dureeRemontee` (8) secondes, pêcheur à bord jusqu'au bout (sinon interrompue, le reste de la prise
  reste dans le filet). Son de treuil, et la prise sort poisson par poisson (nom affiché, éclaboussures) jusque dans l'inventaire.
- **Trésors** : `chanceTresor` (5 % pour un filet plein, au prorata sinon, x2 près des côtes) : coffre d'épave (table « trésor » de la pêche
  vanilla : livres enchantés, arc, nautile...) ou bouteille à la mer (message RP).
- **Usure** (`[usure]`) : 64 points, `usureParRemontee` (2) + 1 par tranche de 4 poissons. Le filet n'est jamais détruit : déchiré (1 point), il ne
  se déploie plus. Réparation : **bobine de fil de pêche** (`bobine_fil_peche`, 40 € à la poissonnerie) en clic droit avec le filet dans l'autre
  main (+`reparationBobine` = 16 points), ou à l'enclume (bobine ou ficelle).
- État dans les données du bateau (`mnrp_filet`) : il suit le bateau au garage et reprend après un redémarrage. Bateau détruit / revendu hors
  garage : les poissons tombent au sol. L'assurance rend un bateau sans filet (pas de duplication).
- Config serveur `config/minenorth-filet.toml`.

## Motos et bateaux HavenRP (04/11)
30 véhicules du jar `minenorth-motos-bateaux` (dossier `../minenorth-motos-bateaux`, packs MTS `minenorthmoto` / `minenorthmarine`) ajoutés au
catalogue : motos et scooters (Moto), BMW R 1250 RT Gendarmerie / Police nationale (Police), bateaux de la marque fictive Galen : barque, 650 RIB, 750 Sport, 900 Cruiser, 85 Fly (Bateau),
vedette de police (Bateaux police). Aucun code spécifique : plaques, coffre, garage et fourrière fonctionnent comme pour les autres packs.
La **Kawasaki Ninja H2R** est sous licence CC-BY-NC-SA : jamais vendue contre de l'argent réel (VIP, boutique), argent du jeu seulement.

## Orientation des véhicules à la sortie (04/11)
Les véhicules sortent **dans l'axe de la place**, jamais de travers :
- **Concession** : sens où regardait l'admin au `/vendeurzone set <concession>` (arrondi au quart de tour). Avant, le lacet du joueur
  était passé tel quel à MTS, qui le compte dans l'autre sens que Minecraft : le véhicule sortait en miroir, souvent de travers.
- **Garage** : sens enregistré à la création de la zone (`/garagezone <nom>`, `/garagezone select`) ou par `/garagezone orienter <nom>`.
- **Fourrière** : sens où regardait l'admin au `/fourrierezone set`.
- **Retrait « en main »** : le sens dans lequel le joueur a posé le véhicule.
- Zones créées avant ce correctif (pas de sens enregistré) : concession = sens de l'acheteur arrondi au quart de tour ; garage et
  fourrière = sens du véhicule au rangement, arrondi au quart de tour. Pour fixer le sens : refaire `/vendeurzone set`,
  `/fourrierezone set`, ou `/garagezone orienter <nom>`, en regardant vers la sortie de la place.
- Le véhicule est aussi remis à plat (sans tangage ni roulis gardés du rangement).

## Véhicules offerts (05/11)
`garage/GiftVehicles.give(joueur, item, libellé, carte)` (appelé par réflexion par le mod Bienvenue) : fait apparaître un véhicule neuf à 2 blocs devant le joueur, à son nom, avec la clé, et pose le drapeau `mnrp_cadeau` (numéro de carte d'identité) dans ses données. Le drapeau suit le véhicule au garage. Un véhicule offert **ne se revend pas** (« Vendre un véhicule » refuse). Le mod Vol refuse aussi de le crocheter. Non testé en jeu.
