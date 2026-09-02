# Sydaria

Plugin **tout-en-un** pour Spigot **1.8.8 / 1.8.9** (Java 8). Il regroupe les modules type AntiCleanUp, Atouts, Classement, Core, Events, Items, RandomTP, Staff, Tags et Tokens.

## Compilation

```bash
mvn clean package
```

Le JAR se trouve dans `target/Sydaria.jar`. Place-le dans `plugins/` du serveur.

## Dépendances optionnelles

- **Vault** : money pour `/b` et placeholder `%sydaria_money%`
- **PlaceholderAPI** : `%sydaria_tokens%`, `%sydaria_money%`, `%sydaria_kills%`, `%sydaria_deaths%`, `%sydaria_faction%`, `%sydaria_tag%`, `%sydaria_event%`
- **Votifier / NuVotifier** : incrémente le VoteParty automatiquement

## Commandes principales

| Commande | Description |
|---|---|
| `/atouts` | Speed, Force, FireRes, Haste, AntiChute, NoHunger, NoDebuff, KeepXP |
| `/classement` | Tops (minage, quêtes, playtime, cultures, mobs, kills, morts, hits event, totem) |
| `/enclume` `/enchantement` `/furnace` `/poubelle` | Utilitaires portables |
| `/bottlexp` `/repair` `/vision` `/randomtp` `/randomkey` | XP, repair, NV, RTP, clés |
| `/b <joueur>` | Bienvenue + money |
| `/title` `/actionbar` | Messages globaux (admin) |
| `/f create\|invite\|join\|leave\|chest\|upgrade\|fly` | Faction + coffre + fly |
| `/event start <type>` | Totem, KOTH, DTC, Nexus, PTK, Masterkill, TeamFight, BR, CTF... |
| `/staff` `/sc` `/cps` | Mode staff, chat, CPS |
| `/tags` `/tokens` `/portal` `/voteparty` | Tags, tokens boutique, portails, VP |
| `/itemsyd <id>` | Donne un des ~40 items custom |
| `/sydaria reload` | Reload config |

## Permissions

`sydaria.admin`, `sydaria.staff`, `sydaria.doublexp`, `sydaria.repair`, `sydaria.event.admin`, `sydaria.tokens.give`, `sydaria.items.give`, `sydaria.randomkey`, `sydaria.bypass.commands`

## Events

`/event set <type>` sur la zone, puis `/event start <type>`. Types : `totem`, `totem_geant`, `koth`, `koth_geant`, `domination`, `sanctuaire`, `dtc`, `nexus`, `protect_the_king`, `masterkill`, `teamfight`, `battleroyal`, `ctf`.

Webhook Discord : `discord.webhook-url` dans `config.yml`.
