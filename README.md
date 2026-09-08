# Draftmc

Plugin **tout-en-un** pour Spigot **1.8.8 / 1.8.9** (Java 8). Il regroupe les modules type AntiCleanUp, Atouts, Classement, Core, Items, RandomTP, Staff, Tags et Tokens.

## Compilation

```bash
mvn clean package
```

Le JAR se trouve dans `target/Draftmc.jar`. Place-le dans `plugins/` du serveur.

## Dépendances optionnelles

- **Vault** : money pour `/b` et placeholder `%draftmc_money%`
- **PlaceholderAPI** : `%draftmc_tokens%`, `%draftmc_money%`, `%draftmc_kills%`, `%draftmc_deaths%`, `%draftmc_faction%`, `%draftmc_tag%`
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
| `/itemdraft <id>` | Donne un des ~40 items custom |
| `/draftmc reload` | Reload config |

## Permissions

`draftmc.admin`, `draftmc.staff`, `draftmc.doublexp`, `draftmc.repair`, `draftmc.tokens.give`, `draftmc.items.give`, `draftmc.randomkey`, `draftmc.bypass.commands`
