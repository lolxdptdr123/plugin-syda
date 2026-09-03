# Sydaria

Plugin **tout-en-un** pour Spigot **1.8.8 / 1.8.9** (Java 8). Il regroupe les modules type AntiCleanUp, Atouts, Classement, Core, Items, RandomTP, Staff, Tags et Tokens.

## Compilation

```bash
mvn clean package
```

Le JAR se trouve dans `target/Sydaria.jar`. Place-le dans `plugins/` du serveur.

## Dépendances optionnelles

- **Vault** : money pour `/b` et placeholder `%sydaria_money%`
- **PlaceholderAPI** : `%sydaria_tokens%`, `%sydaria_money%`, `%sydaria_kills%`, `%sydaria_deaths%`, `%sydaria_faction%`, `%sydaria_tag%`
- **Votifier / NuVotifier** : incrémente le VoteParty automatiquement

## Commandes principales

| Commande | Description |
|---|---|
| `/atouts` | Speed, Force, FireRes, Haste, AntiChute, NoHunger, NoDebuff, KeepXP |
| `/classement` | Tops (minage, quêtes, playtime, cultures, mobs, kills, morts) |
| `/enclume` `/enchantement` `/furnace` `/poubelle` | Utilitaires portables |
| `/bottlexp` `/repair` `/vision` `/randomtp` `/randomkey` | XP, repair, NV, RTP, clés |
| `/b <joueur>` | Bienvenue + money |
| `/title` `/actionbar` | Messages globaux (admin) |
| `/f create\|invite\|join\|leave\|chest\|upgrade\|fly` | Faction + coffre + fly |
| `/staff` `/sc` `/cps` | Mode staff, chat, CPS |
| `/tags` `/tokens` `/portal` `/voteparty` | Tags, tokens boutique, portails, VP |
| `/itemsyd <id>` | Donne un des ~40 items custom |
| `/sydaria reload` | Reload config |

## Permissions

`sydaria.admin`, `sydaria.staff`, `sydaria.doublexp`, `sydaria.repair`, `sydaria.tokens.give`, `sydaria.items.give`, `sydaria.randomkey`, `sydaria.bypass.commands`
