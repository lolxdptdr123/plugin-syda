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
| `/stats` `/statistique` | Stats perso + top serveur (têtes) |
| `/enclume` `/enchantement` `/furnace` `/poubelle` | Utilitaires portables |
| `/bottlexp` `/repair` `/vision` `/randomtp` `/randomkey` | XP, repair, NV, RTP, clés |
| `/b <joueur>` | Bienvenue + money |
| `/title` `/actionbar` | Messages globaux (admin) |
| `/f create\|invite\|join\|leave\|chest\|upgrade\|fly` | Faction + coffre + fly |
| `/staff` `/sc` `/cps` | Mode staff, chat, CPS |
| `/tags` `/tokens` `/portal` `/voteparty` | Tags, tokens boutique, portails, VP |
| `/itemdraft <id>` | Donne un des ~40 items custom |
| `/draftmc reload` | Reload config |

## Discord (lien comptes + resultats d'events)

Deux morceaux independants :

1. **Lien des comptes** : bot Node dans `discord-bot/` + API HTTP du plugin (`discord-link`).
2. **Resultats d'events** (KOTH, Totem, Conquest, Domination, MasterKill, BattleRoyal, TeamFight) : webhook Discord **ou** salon `EVENTS_CHANNEL_ID` du bot. N'en utilise qu'un, sinon rien n'est double si le webhook est rempli (le webhook a priorite).

### 1. Creer le bot

1. [Discord Developer Portal](https://discord.com/developers/applications) → **New Application** → onglet **Bot**.
2. **Reset Token**, copie-le dans `discord-bot/.env` (`DISCORD_TOKEN`).
3. Active **MESSAGE CONTENT INTENT** et **SERVER MEMBERS INTENT**.
4. **OAuth2 → URL Generator** : scope `bot`. Permissions : Lire les messages, Envoyer des messages, Integrer des liens, Gerer les surnoms, Gerer les roles.
5. Invite le bot. Place son role **au-dessus** des roles de grade.

### 2. IDs Discord

Active le **mode developpeur** (Parametres → Avance). Puis :

- clic droit serveur → Copier l'identifiant = `GUILD_ID`
- salon ou les joueurs collent le code `/discord` = `LINK_CHANNEL_ID`
- salon des resultats d'events = `EVENTS_CHANNEL_ID` (inutile si `discord.webhook-url` est deja rempli)

### 3. Plugin (`plugins/Draftmc/config.yml`)

Le JAR **n'ecrase pas** un `config.yml` deja present. A verifier :

```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."   # salon events, ou "" si tu utilises le bot
  events:
    enabled: true
discord-link:
  enabled: true
  api-bind: "0.0.0.0"
  api-port: 8765
  api-secret: "un-secret-long"   # identique a PLUGIN_SECRET
  roles:
    chevalier: "ID_ROLE_DISCORD"
```

`api-secret` et `PLUGIN_SECRET` **doivent etre identiques**. Si le bot tourne sur la meme machine que Minecraft : `PLUGIN_URL=http://127.0.0.1:8765`. Sinon, IP publique du serveur + port `8765` ouvert.

### 4. Lancer le bot

```bash
cd discord-bot
copy .env.example .env
npm install
node index.js
```

Au demarrage tu dois voir `Plugin Minecraft connecte.`

Les textes Discord se changent dans `plugins/Draftmc/config.yml` sous `discord.events` (`title`, `ranking`, `winner`, `extra.<event>`) puis `/draftmc reload`.

### 5. Joueurs

En jeu : `/discord` → coller `DMC-XXXXXX` dans le salon lien. `/discord unlink` pour detacher.

Un **forcestop** admin n'envoie pas de resultat Discord (pas de F Top non plus).

## Permissions

`draftmc.admin`, `draftmc.staff`, `draftmc.doublexp`, `draftmc.repair`, `draftmc.tokens.give`, `draftmc.items.give`, `draftmc.randomkey`, `draftmc.bypass.commands`
