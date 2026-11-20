# Citizen Factions

Mod de factions RP du serveur **Citizen** : Dragonniers et Chasseurs de dragon, grades, emblèmes,
règles de monte des dragons **Ice and Fire** et lien progressif joueur / dragon.

| | |
|---|---|
| Minecraft | Java 1.21.1 |
| Modloader | NeoForge **21.1.215** (exactement) |
| Java | 21 |
| Mod ID | `citizenfactions` |
| Compatibilité | Ice and Fire: Dragons (Unofficial Port) — modId `iceandfire` |

## Installation

1. Placez `citizenfactions-1.2.0.jar` dans `mods/` **du serveur et des joueurs** (l'interface et la barre de lien sont côté client).
   Avec Citizen Updater, déposez simplement le jar dans le pack.
2. Ice and Fire doit être installé (sinon le mod démarre mais ne reconnaît aucun dragon ; un avertissement apparaît dans les logs).
3. Au premier démarrage, les factions **Dragonnier** et **Chasseur de dragon** sont créées avec leurs grades et emblèmes.

Données : `world/citizenfactions/factions.json` (+ `factions.json.bak`, copie de secours automatique).
Configuration : `world/serverconfig/citizenfactions-server.toml` (serveur) et `config/citizenfactions-client.toml` (position de la barre).

## Règles

- **Sans faction, pas de dragon** : impossible de monter un dragon, d'interagir avec ses dragons apprivoisés
  (nourrir, ordres, inventaire) ou de poser un œuf de dragon.
- **Dragonniers** : tous les dragons sauf les corrompus. **Chasseurs de dragon** : uniquement les corrompus.
- Dragons corrompus : **Noir** (foudre, variante 3), **Gris** (feu, variante 3), **Cuivre** (foudre, variante 2).
  La vérification utilise le **type d'entité** Ice and Fire et sa **variante interne** (jamais le nom affiché).
  Liste modifiable dans la config (`corruptedDragons`).
- Toute tentative refusée affiche un message explicatif.
- Option `untameDragonsOfFactionlessOwners` (désactivée par défaut) : rend sauvages les dragons d'un joueur connecté sans faction.
- Les administrateurs ignorent ces règles (`adminsBypassRules = true`, modifiable).

## Lien avec les dragons

- Barre « Lien » au-dessus de la barre d'inventaire pendant la monte.
- +0,75 point par minute de monte (≈ 2 h 15 pour 100 %), plafonné à 100 %.
- Abandon : après 12 h sans monter le dragon, −1 point par heure (temps réel, même serveur éteint).
- Stocké **dans les données du dragon** (survit à la mort du joueur, aux déconnexions et aux redémarrages), un lien par joueur.
- Lien faible : refus de monte (jusqu'à 35 % à 0), mouvements brusques (< 50 %), désarçonnage possible (< 10 %), dragon plus lent (−25 % à 0).
- Lien élevé : plus aucun refus, vitesse jusqu'à +10 %, régénération du dragon pendant la monte (≥ 80 %).
- Tous les réglages sont dans la section `[bond]` de la config.

## Dragons primordiaux

Un par faction, réservés au grade ayant la permission `MONTER_PRIMORDIAL` **de leur faction** (Primodiau par défaut ;
les sauvegardes existantes reçoivent ce droit automatiquement pour le grade le plus haut).

| | Dragonnier | Chasseur de dragon |
|---|---|---|
| Base Ice and Fire | dragon de glace **Blanc** | dragon de foudre **Noir** |
| Souffle | accompagné de **flammes bleues**, embrase les cibles | accompagné de **flammes rouges**, embrase les cibles |
| Taille, vie, dégâts, vitesse, armure | ×2 | ×2 |

Tous les multiplicateurs sont réglables dans la section `[primordial]` de la config.
La taille passe par un mixin sur `EntityDragonBase.getRenderSize/getScale` (Ice and Fire calcule lui-même la taille de ses dragons) ;
s'il ne trouve pas ces méthodes dans le portage, il ne fait rien (le dragon garde sa taille normale, le reste fonctionne).

```
/factions admin primordial creer dragonnier|chasseur [propriétaire]   invoque le primordial adulte devant vous
/factions admin primordial marquer dragonnier|chasseur                transforme le dragon regardé (bonne espèce requise)
/factions admin primordial retirer                                    le dragon regardé redevient ordinaire
/factions admin primordial liste
```

## Auras de grade (avec Citizen Aura 1.2.0+)

Alpha : **éclair blanc**, Primodiau : **éclair rouge**, dans toutes les factions. Mis à jour toutes les 2 secondes
(changement de grade, exclusion…). Réglable dans `[auras] gradeAuras`. Une aura donnée à la main avec `/aura donner` reste prioritaire.

## Interface

Touche **U** (modifiable : Options > Commandes > Citizen Factions) ou `/factions`.
Faction et emblème, grade, membres en ligne / hors ligne avec pagination et filtres, fiche de chaque membre
(grade, droits, dragons liés et niveau de lien), demandes d'adhésion, administration complète (factions, grades
et permissions, emblèmes avec aperçu), bouton « Actualiser » partout.

### Permissions de grade

`VOIR_MEMBRES`, `VOIR_DRAGONS`, `GERER_DEMANDES`, `PROMOUVOIR` (grades inférieurs au sien), `EXCLURE` (membres inférieurs).
Par défaut : Primodiau = tout ; Alpha = voir, demandes, promouvoir ; Confirmé = voir membres et dragons ; Petit = voir membres.

## Commandes

Joueurs : `/factions` (alias `/faction`), `/factions liste`, `/factions membres`, `/factions dragons`.

Administrateurs (op niveau 4 par défaut, ou `adminPlayers` dans la config) :

```
/factions admin                                   ouvre l'administration
/factions admin creer <id> <nom>
/factions admin supprimer <faction>
/factions admin modifier <faction> nom|description <texte>
/factions admin modifier <faction> regle NON_CORROMPUS|CORROMPUS|TOUS|AUCUN
/factions admin modifier <faction> embleme <embleme|aucun>
/factions admin ajouter <faction> <joueur> [grade]
/factions admin retirer <joueur>
/factions admin transferer <joueur> <faction>
/factions admin grade <joueur> <grade>
/factions admin liste | membres <faction> | joueur <joueur>
/factions admin dragon [uuid]                     dragon regardé / monté, ou par UUID : espèce, couleur, lien
/factions admin grades <faction> [creer <nom> | renommer <grade> <nom> | supprimer <grade> | monter <grade> | descendre <grade> | permission <grade> <PERM> <true|false>]
/factions admin emblemes [creer <id> <nom> | couleurs <id> <#RRGGBB> <#RRGGBB> | forme <id> ECU|ROND|LOSANGE|BANNIERE | motif <id> UNI|BANDE|CROIX|CHEVRON|MOITIE | symbole <id> <car> | supprimer <id>]
/factions admin sauvegarder
```

Les joueurs hors ligne sont gérés (ils doivent s'être connectés au moins une fois).

## Compilation

`./gradlew build` → `build/libs/citizenfactions-1.2.0.jar`. GitHub Actions compile et publie une Release à chaque push.

## Compatibilité

- Aucune dépendance de compilation vers Ice and Fire : détection par type d'entité + variante (réflexion sur `getVariant()`,
  repli sur la donnée sauvegardée `Variant`). Parties multiples des dragons gérées (`getParent()`).
- Aucune classe client chargée sur serveur dédié ; tout paquet client est revérifié côté serveur (droits, grade, faction).
- Indépendant de CitizenEconomy (aucune interaction financière), de CitizenGuilds et de CitizenHibou.
