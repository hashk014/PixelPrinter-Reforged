# PixelPrinter Reforged

**Made by hash014 — version 2.0.0.** Réécriture du plugin serveur PixelPrinter : images en blocs, GIF animés, cartes Minecraft, murs de cadres et statues de skins. Aucun mod client n'est nécessaire.

Le projet d'origine est [PixelPrinter de Zombie_Striker](https://github.com/ZombieStriker/PixelPrinter). Reforged conserve son attribution et sa licence GPL-2.0-or-later. Les sources et scripts de construction accompagnent le JAR.

## Installation

1. Arrêter le serveur Bukkit, Spigot ou Paper.
2. Retirer l'ancien JAR PixelPrinter du dossier `plugins` : les deux plugins utilisent `/pp`.
3. Placer `PixelPrinter-Reforged-2.0.0.jar` dans `plugins`, puis démarrer.
4. Déposer les images dans `plugins/PixelPrinterReforged/images`. Deux exemples sont installés automatiquement.
5. Se placer au coin inférieur gauche de la construction et lancer `/pp create South hash014.png 32`.

Pour reprendre une ancienne bibliothèque, copier les fichiers du dossier `plugins/PixelPrinter/images` dans le nouveau dossier `images`. Les fichiers `.txt` contenant une URL sont reconnus. Les anciennes sauvegardes Java de GIF et les anciens historiques d'annulation ne sont pas importés.

L'objectif de compatibilité est **Minecraft Java 1.7.10 à 26.3**, via les capacités de l'API Bukkit. Consulter [la matrice de tests](docs/COMPATIBILITY.md) pour les versions effectivement exécutées et leurs builds. Les microversions sans binaire Paper disponible ne sont pas considérées comme testées. Fabric, Forge, Folia et Bedrock natif ne sont pas des plateformes prises en charge.

## Commandes

Les alias sont `/pp`, `/ppr`, `/pixelprinter` et `/pixelprinterreforged`.

| Commande | Effet |
| --- | --- |
| `/pp create <direction> <image> <hauteur> [transparent] [dither]` | Image ou GIF en blocs ; hauteur en blocs, proportions conservées. |
| `/pp map <image> [rangées]` | Donner les cartes de l'image, 128 pixels par carte ; GIF accepté. |
| `/pp mapwall <direction> <image> <rangées>` | Construire le support et placer les cadres automatiquement. |
| `/pp createFrame <direction> <image> <hauteur-pixels>` | Syntaxe historique ; alias `cf`. |
| `/pp createSkin <direction> <joueur/UUID/skin.png> [échelle]` | Statue 3D ; skin moderne/classique, modèle slim distant, couches de vêtements et cape distante. Échelle 1 à 4. |
| `/pp downloadimage <nom> <URL>` | Télécharger et valider une image locale ; alias `di`. |
| `/pp download <nom> <URL>` | Valider et enregistrer une référence `.txt` ; alias `d`. |
| `/pp list`, `/pp specs <image>`, `/pp delete <image>` | Bibliothèque et informations. |
| `/pp jobs`, `/pp listGifs` | Identifier les travaux actifs. |
| `/pp pause <id>`, `/pp resume <id>`, `/pp stop <id>` | Contrôler un travail ; alias `stopGif`. |
| `/pp stopAllGifs` | Arrêter toutes les animations de blocs. |
| `/pp undo [id]`, `/pp history`, `/pp clearundo` | Annuler un travail ou libérer l'historique. |
| `/pp setLoadCount <1-5000>` | Budget global de positions par tick, partagé entre travaux. |
| `/pp reload`, `/pp version` | Recharger les limites ou afficher le diagnostic. |

Exemples :

```text
/pp create South hash014.png 32 true true
/pp create FlatNorthEast hash014.gif 24
/pp mapwall North hash014.png 2
/pp createSkin West Notch 2
/pp undo
```

Directions verticales : `South`, `North`, `East`, `West` (alias `UP_SOUTH`, etc.). La direction est la face visible. Directions au sol : `FlatNorthEast`, `FlatSouthEast`, `FlatNorthWest`, `FlatSouthWest`. Les murs de cartes et statues utilisent les directions verticales. L'origine est capturée au lancement de la commande ; un déplacement pendant le chargement ne déplace pas la construction.

`transparent=true` laisse les pixels transparents intacts. Dans un GIF, une frame transparente restaure le bloc original de cette position. `transparent=false` compose l'image sur fond blanc. `dither=true` active la diffusion d'erreur Floyd–Steinberg pour les dégradés.

## Permissions et protection

Toutes les permissions de modification sont réservées aux opérateurs par défaut : `pixelprinter.create`, `pixelprinter.download`, `pixelprinter.delete`, `pixelprinter.stop`, `pixelprinter.admin`. `pixelprinter.*` les regroupe.

Les joueurs contrôlent leurs propres travaux ; agir sur un autre joueur exige `pixelprinter.admin`. Une image distante ou une référence `.txt` exige aussi `pixelprinter.download`. Les fichiers ne peuvent pas sortir de `images`, et les fichiers déjà présents ne sont pas écrasés.

Les poses et annulations déclenchent `BlockPlaceEvent`, les cadres `HangingPlaceEvent`. Une protection qui annule l'événement interrompt le travail ; les blocs déjà posés restent annulables. Un GIF et un travail de joueur attendent son retour lorsque le joueur est déconnecté. L'intégration complète avec chaque plugin de protection doit être validée sur le serveur concerné ; les tests utilisent un listener qui refuse réellement les poses.

## Configuration et ressources

Les valeurs initiales limitent les travaux à 200 positions/tick et 6 ms de traitement par tick. Le budget inclut la pose, les frames GIF et l'annulation. Un chargement de chunk individuel reste une opération Bukkit synchrone ; le budget n'est pas une garantie absolue de durée d'un tick.

Les limites de hauteur, blocs, frames, téléchargement, pixels décodés, mémoire de frames actives, cartes persistantes et historique se règlent dans `config.yml`. Les jobs gardent leurs chunks pendant leur exécution, puis libèrent leurs tickets. La palette et les options réseau nécessitent un redémarrage après modification ; `/pp reload` applique les limites de construction et budgets.

La palette comprend la laine, terre cuite, béton, pierre, bois et des matériaux plus récents lorsqu'ils existent sur le serveur. Les noms inexistants sont ignorés. `materials` permet de limiter les matériaux ; sur les anciens serveurs une variante peut être sélectionnée avec `WOOL:14`.

La version 1.7 utilise un correctif par réflexion pour conserver le point d'attache des cadres lors d'un changement d'orientation. Les autres adaptateurs utilisent les capacités de l'API Bukkit.

Pour un resource pack, déposer un ZIP dans `custom_textures`, ou des PNG comme `red_wool.png`. Les textures de `assets/minecraft/textures/block` et `blocks` fournissent une couleur moyenne. Ce n'est pas un moteur de modèles 3D/resource packs : les textures de faces sont moyennées et les nouveaux matériaux doivent exister dans le serveur. La sélection utilise la distance colorimétrique CIELAB.

Les téléchargements HTTP/HTTPS sont bornés, ont des délais et contrôlent les adresses DNS à chaque redirection. Les adresses privées/locales et les URL avec identifiants sont refusées. `allowed-hosts` peut restreindre les domaines, y compris les services Mojang utilisés par les skins. Aucune télémétrie, auto-mise à jour ou dépendance téléchargée au lancement du plugin.

## Sauvegardes et limites

Les cartes, leurs rendus et leurs animations persistent dans `maps`. Les GIF de blocs actifs persistent dans `animations.json`, avec leurs blocs d'origine ; ils reprennent lorsque leur propriétaire est connecté et `resume-gifs` est activé. Garder leurs fichiers sources accessibles et identiques. Un descripteur qui ne peut pas être restauré est conservé et signalé dans la console.

L'historique des images/statues et murs terminés est conservé en mémoire pour la session. Leur annulation n'est plus disponible après redémarrage. L'annulation d'un travail actif arrête ses animations et retire ses cadres. Les cartes déjà données au joueur ne sont pas retirées de son inventaire par l'annulation d'un mur. Les GIF ne remplacent pas les coffres, panneaux et autres blocs contenant des données persistantes. Les images statiques utilisent des snapshots Bukkit, y compris les inventaires de coffres.

Les ralentissements peuvent allonger la durée d'une frame GIF : une frame est finie avant de passer à la suivante. Les maps animées suivent leurs délais GIF à partir de l'horloge de rendu. Les animations de maps ne sont pas des jobs de blocs et ne sont pas arrêtées par `stopAllGifs`.

## Développement et tests

JDK 17 ou plus récent et Maven 3.9 : `mvn clean verify` ou `./build.ps1`. Le bytecode livré cible Java 8 ; utiliser la version de Java exigée par le serveur (Java 25 pour 26.3).

`python tools/bootstrap.py` installe des JDK et Maven portables dans `.tools`. `python tools/prepare_servers.py --all` télécharge les builds Paper officiels et contrôle leurs SHA-256. `python tools/cache_vanilla.py` prépare les JAR Mojang nécessaires aux vieux Paperclip dont les URL historiques ont disparu. Les outils de test client s'installent avec `npm install --prefix .tools/protocol minecraft-protocol@1.68.0 minecraft-data@3.117.0`.

`python tools/run_matrix.py --all --workers 3` exécute des serveurs jetables liés à **127.0.0.1**, fait jouer un client protocole de test, vérifie le monde et redémarre pour contrôler la persistance. Il nettoie uniquement les mondes et données du plugin sous `test-servers` avant chaque test. Les commandes internes `selftest`, `playercheck`, `persistfixture`, `persistcheck`, `protocolinfo` exigent l'option JVM `-Dpixelprinter.reforged.test=true` et servent uniquement à ces serveurs jetables.

Les preuves couvrent l'API serveur, le monde, les commandes, les protections événementielles, les paquets de cartes et les redémarrages. Elles ne constituent pas une revue visuelle humaine, un benchmark massif, ni une certification de toutes les combinaisons de plugins tiers.
