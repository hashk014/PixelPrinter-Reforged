# Compatibilité — PixelPrinter Reforged 2.0.0

**Made by hash014.** Rapport du 11 octobre 2026.

**55/55 versions Paper exécutées avec succès**, avec le même JAR (SHA-256 `75d4a213e5c6563ac52ff4bfed5395a0eaf4fa0afd9f075238e8a59d1387724c`). 2648 contrôles dans les serveurs, en plus des 29 tests unitaires.

Chaque version a été démarrée réellement dans un dossier jetable. Les vérifications ont posé et annulé des blocs dans le monde, testé la transparence, la couleur legacy, les inventaires des coffres, les GIF, les directions, les statues et les cartes. La région inspectée a été maintenue chargée ; sur 1.7.10, les cadres ont été observés après 125 ticks pour inclure leur contrôle physique. Un client protocole connecté a testé les permissions, les protections événementielles et les murs de cartes dans les quatre orientations, puis chaque serveur a été arrêté et redémarré pour vérifier le GIF et son état original.

Minecraft 26.3 a été testé avec Paper build 174 **BETA**, Java 25 et un client protocole utilisant le registre de paquets réel du serveur. Les avertissements Windows OSHI/JNA de ce serveur sont distincts des erreurs du plugin ; ils n’ont pas empêché les tests.

La liste couvre les versions de sortie publiées dans le catalogue Paper obtenu pour ce travail. Les microversions Minecraft absentes de ce catalogue, les snapshots/RC et les autres distributions ne sont pas certifiés par cette matrice. Le plugin vise Bukkit/Spigot/Paper de 1.7.10 à 26.3 ; aucun test Fabric/Forge/Folia ou Bedrock natif.

Les clients utilisés sont des sondes automatisées. Aucune revue graphique humaine, charge de milliers de joueurs ou combinaison exhaustive avec les plugins tiers n’est revendiquée. Les tests de protection annulent de vrais événements Bukkit, et ne remplacent pas un test de configuration WorldGuard/équivalent sur le serveur cible.

| Minecraft | Build Paper | Canal | Java testé | Monde | Joueur | Reboot GIF | Paquets carte |
| --- | ---: | --- | ---: | ---: | ---: | --- | ---: |
| 1.7.10 | 2025 | STABLE | 8 | 28 | 20 | OK | 0 |
| 1.8.8 | 445 | STABLE | 8 | 28 | 20 | OK | 11 |
| 1.9.4 | 775 | STABLE | 8 | 28 | 20 | OK | 8 |
| 1.10.2 | 918 | STABLE | 8 | 28 | 20 | OK | 8 |
| 1.11.2 | 1106 | STABLE | 8 | 28 | 20 | OK | 8 |
| 1.12 | 1169 | STABLE | 8 | 28 | 20 | OK | 8 |
| 1.12.1 | 1204 | STABLE | 8 | 28 | 20 | OK | 8 |
| 1.12.2 | 1620 | STABLE | 8 | 28 | 20 | OK | 8 |
| 1.13 | 173 | STABLE | 8 | 27 | 20 | OK | 8 |
| 1.13.1 | 386 | STABLE | 8 | 27 | 20 | OK | 13 |
| 1.13.2 | 657 | STABLE | 8 | 27 | 20 | OK | 13 |
| 1.14 | 17 | STABLE | 8 | 27 | 20 | OK | 16 |
| 1.14.1 | 50 | STABLE | 8 | 27 | 20 | OK | 16 |
| 1.14.2 | 107 | STABLE | 8 | 27 | 20 | OK | 16 |
| 1.14.3 | 134 | STABLE | 8 | 27 | 20 | OK | 18 |
| 1.14.4 | 245 | STABLE | 8 | 27 | 20 | OK | 17 |
| 1.15 | 21 | STABLE | 8 | 27 | 20 | OK | 17 |
| 1.15.1 | 62 | STABLE | 8 | 27 | 20 | OK | 17 |
| 1.15.2 | 393 | STABLE | 8 | 27 | 20 | OK | 18 |
| 1.16.1 | 138 | STABLE | 8 | 27 | 20 | OK | 17 |
| 1.16.2 | 189 | STABLE | 8 | 27 | 20 | OK | 18 |
| 1.16.3 | 253 | STABLE | 8 | 27 | 20 | OK | 17 |
| 1.16.4 | 416 | STABLE | 8 | 27 | 20 | OK | 18 |
| 1.16.5 | 794 | STABLE | 8 | 27 | 20 | OK | 18 |
| 1.17 | 79 | STABLE | 16 | 27 | 20 | OK | 11 |
| 1.17.1 | 411 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.18 | 66 | ALPHA | 17 | 27 | 20 | OK | 13 |
| 1.18.1 | 216 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.18.2 | 388 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.19 | 81 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.19.1 | 111 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.19.2 | 307 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.19.3 | 448 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.19.4 | 550 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.20 | 17 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.20.1 | 196 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.20.2 | 318 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.20.4 | 499 | STABLE | 17 | 27 | 20 | OK | 12 |
| 1.20.5 | 22 | ALPHA | 21 | 27 | 20 | OK | 12 |
| 1.20.6 | 151 | STABLE | 21 | 27 | 20 | OK | 12 |
| 1.21 | 130 | STABLE | 21 | 27 | 20 | OK | 12 |
| 1.21.1 | 133 | STABLE | 21 | 27 | 20 | OK | 12 |
| 1.21.3 | 83 | STABLE | 21 | 27 | 20 | OK | 13 |
| 1.21.4 | 232 | STABLE | 21 | 27 | 20 | OK | 13 |
| 1.21.5 | 114 | ALPHA | 21 | 27 | 20 | OK | 13 |
| 1.21.6 | 48 | STABLE | 21 | 27 | 20 | OK | 12 |
| 1.21.7 | 32 | STABLE | 21 | 27 | 20 | OK | 13 |
| 1.21.8 | 60 | STABLE | 21 | 27 | 20 | OK | 13 |
| 1.21.9 | 59 | ALPHA | 21 | 27 | 20 | OK | 13 |
| 1.21.10 | 130 | STABLE | 21 | 27 | 20 | OK | 13 |
| 1.21.11 | 132 | STABLE | 21 | 27 | 20 | OK | 13 |
| 26.1.1 | 29 | ALPHA | 25 | 27 | 20 | OK | 13 |
| 26.1.2 | 74 | STABLE | 25 | 27 | 20 | OK | 12 |
| 26.2 | 133 | STABLE | 25 | 27 | 20 | OK | 12 |
| 26.3 | 174 | BETA | 25 | 27 | 20 | OK | 13 |

Les valeurs Java sont celles utilisées pour les tests, pas une promesse que toutes les autres JVM sont acceptées. Le build historique 1.17 exige Java 16 ; les versions récentes peuvent exiger Java 25.

Fichiers de preuve : `compatibility-results.json`, `PixelPrinter-Reforged-2.0.0-test-evidence.zip` et `target/surefire-reports`. Le nombre de paquets de cartes peut être nul sur le client 1.7.10 ; les objets de carte, les renderers, leur sérialisation et les cadres ont été validés par l’API serveur.

Un probe distinct a validé la récupération réelle d’un profil et de son skin auprès de Mojang avec le JAR livré, sous Java 8 et Java 25 (voir `skin-probe.txt`).

Sources techniques : [PixelPrinter original](https://github.com/ZombieStriker/PixelPrinter), [Paper 26.3](https://papermc.io/news/26-3/), [service de téléchargement Paper](https://docs.papermc.io/misc/downloads-service/), [Java pour Paper](https://docs.papermc.io/misc/java-install/).
