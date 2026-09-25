require("dotenv").config();
const { Client, GatewayIntentBits, SlashCommandBuilder, REST, Routes, EmbedBuilder } = require("discord.js");

const TOKEN = process.env.DISCORD_TOKEN;
const GUILD_ID = process.env.GUILD_ID;
const CHANNEL_ID = process.env.LINK_CHANNEL_ID;
const EVENTS_CHANNEL_ID = process.env.EVENTS_CHANNEL_ID || "";
const PLUGIN_URL = (process.env.PLUGIN_URL || "http://127.0.0.1:8765").replace(/\/$/, "");
const SECRET = process.env.PLUGIN_SECRET;
const CODE_REGEX = /\bDMC-[A-HJ-NP-Z2-9]{6}\b/i;

if (!TOKEN || !GUILD_ID || !CHANNEL_ID || !SECRET) {
  console.error("Remplis DISCORD_TOKEN, GUILD_ID, LINK_CHANNEL_ID et PLUGIN_SECRET dans .env");
  process.exit(1);
}

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
    GatewayIntentBits.GuildMembers,
  ],
});

async function pluginFetch(path, options) {
  const res = await fetch(PLUGIN_URL + path, {
    ...options,
    headers: {
      Authorization: "Bearer " + SECRET,
      "Content-Type": "application/json",
      ...(options && options.headers),
    },
  });
  const text = await res.text();
  let json = {};
  try {
    json = JSON.parse(text);
  } catch (e) {
    json = { ok: false, error: "invalid_json", raw: text };
  }
  return { ok: res.ok && json.ok !== false, status: res.status, json };
}

const STATS_CHOICES = [
  { name: "Tout le mois", value: "all" },
  { name: "Totem géant (points)", value: "totemgeant" },
  { name: "Totem (blocs cassés)", value: "totem" },
  { name: "KOTH géant (kills)", value: "koth_kills" },
  { name: "KOTH géant (morts)", value: "koth_deaths" },
  { name: "TeamFight (hits)", value: "teamfight" },
  { name: "Conquest (points cap)", value: "conquest" },
  { name: "Domination (points cap)", value: "domination_caps" },
  { name: "Domination (morts)", value: "domination_deaths" },
];

const STATS_ORDER = [
  "totemgeant",
  "totem",
  "koth_kills",
  "koth_deaths",
  "teamfight",
  "conquest",
  "domination_caps",
  "domination_deaths",
];

function formatBoard(board, max) {
  if (!board || !Array.isArray(board.entries) || board.entries.length === 0) {
    return "_Aucune donnée ce mois._";
  }
  const medals = ["🥇", "🥈", "🥉"];
  return board.entries.slice(0, max).map((entry, i) => {
    const medal = medals[i] || "`" + (i + 1) + ".`";
    return medal + " **" + entry.name + "** — **" + entry.value + "** " + (board.unit || "");
  }).join("\n");
}

function statsEmbeds(json, filter) {
  const boards = (json && json.boards) || {};
  const keys = filter && filter !== "all" ? [filter] : STATS_ORDER;
  const embeds = [];
  const chunk = new EmbedBuilder()
    .setColor(0xf1c40f)
    .setTitle("Stats events — " + (json.label || "ce mois"))
    .setDescription("Classements **in-game** du mois en cours. Les compteurs repartent à zéro le 1er de chaque mois.")
    .setFooter({ text: "Draftmc • " + (json.month || "") });
  for (const key of keys) {
    const board = boards[key];
    if (!board) {
      continue;
    }
    chunk.addFields({
      name: board.title || key,
      value: formatBoard(board, 5).slice(0, 1024),
      inline: false,
    });
  }
  if (!chunk.data.fields || chunk.data.fields.length === 0) {
    chunk.setDescription("Aucune stat pour ce filtre.");
  }
  embeds.push(chunk);
  return embeds;
}

async function registerSlashCommands() {
  const command = new SlashCommandBuilder()
    .setName("stats")
    .setDescription("Classements events IG du mois (totem, koth, teamfight, conquest, domination)")
    .addStringOption((option) =>
      option
        .setName("event")
        .setDescription("Filtrer un event")
        .setRequired(false)
        .addChoices(...STATS_CHOICES)
    );
  const rest = new REST({ version: "10" }).setToken(TOKEN);
  await rest.put(Routes.applicationGuildCommands(client.user.id, GUILD_ID), {
    body: [command.toJSON()],
  });
  console.log("Commande slash /stats enregistrée.");
}

async function applyMember(guild, job) {
  const member = await guild.members.fetch(job.discordId).catch(() => null);
  if (!member) {
    return;
  }
  const toRemove = (job.removeRoleIds || []).filter((id) => id && member.roles.cache.has(id));
  if (toRemove.length) {
    await member.roles.remove(toRemove, "Draftmc link").catch(() => null);
  }
  if (job.unlink) {
    await member.setNickname(null, "Draftmc unlink").catch((err) => {
      console.warn("Reset pseudo", member.user.tag, ":", err.message);
    });
    return;
  }
  const nick = String(job.nick || "").trim().slice(0, 32);
  if (nick && member.nickname !== nick) {
    if (!member.manageable) {
      console.warn(
        "Impossible de changer le pseudo de",
        member.user.tag,
        ": mets le role du bot AU-DESSUS de ce membre (et pas le proprio du serveur)."
      );
    } else {
      await member.setNickname(nick, "Draftmc link").catch((err) => {
        console.warn("Pseudo Discord", member.user.tag, ":", err.message);
      });
    }
  }
  if (job.roleId && !member.roles.cache.has(job.roleId)) {
    await member.roles.add(job.roleId, "Draftmc grade").catch(() => null);
  }
}

async function pollSync() {
  const guild = client.guilds.cache.get(GUILD_ID);
  if (!guild) {
    return;
  }
  try {
    const { ok, json } = await pluginFetch("/sync", { method: "GET" });
    if (!ok || !json.pending) {
      return;
    }
    for (const job of json.pending) {
      await applyMember(guild, job);
    }
  } catch (err) {
    console.warn("Sync plugin:", err.message);
  }
}

async function pollEvents() {
  if (!EVENTS_CHANNEL_ID) {
    return;
  }
  try {
    const { ok, json } = await pluginFetch("/events", { method: "GET" });
    if (!ok || !json.pending) {
      return;
    }
    const channel = await client.channels.fetch(EVENTS_CHANNEL_ID).catch(() => null);
    if (!channel || !channel.isTextBased()) {
      return;
    }
    for (const post of json.pending) {
      const content = post.content
        || [post.title, "", post.field || ":crossed_swords: Résultats", "", ...(Array.isArray(post.lines) ? post.lines : [])]
            .join("\n");
      await channel.send({ content: String(content).slice(0, 2000) }).catch((err) => console.warn("Event Discord:", err.message));
    }
  } catch (err) {
    console.warn("Events plugin:", err.message);
  }
}

client.once("ready", async () => {
  console.log("Bot pret :", client.user.tag);
  try {
    await registerSlashCommands();
  } catch (err) {
    console.warn("Enregistrement /stats :", err.message);
  }
  try {
    const health = await pluginFetch("/health", { method: "GET" });
    if (!health.ok) {
      console.warn("Plugin Minecraft injoignable sur", PLUGIN_URL);
    } else {
      console.log("Plugin Minecraft connecte.");
    }
  } catch (err) {
    console.warn("Plugin Minecraft injoignable :", err.message);
  }
  if (EVENTS_CHANNEL_ID) {
    console.log("Salon events :", EVENTS_CHANNEL_ID);
  } else {
    console.log("EVENTS_CHANNEL_ID vide : les resultats d'events passent par le webhook du plugin.");
  }
  setInterval(pollSync, 15000);
  setInterval(pollEvents, 10000);
  pollSync();
  pollEvents();
});

client.on("messageCreate", async (message) => {
  if (message.author.bot || message.channelId !== CHANNEL_ID) {
    return;
  }
  const match = message.content.toUpperCase().match(CODE_REGEX);
  if (!match) {
    return;
  }
  const code = match[0];
  try {
    const { ok, json } = await pluginFetch("/link", {
      method: "POST",
      body: JSON.stringify({
        code: code,
        discordId: message.author.id,
      }),
    });
    if (ok) {
      const guild = message.guild || await client.guilds.fetch(GUILD_ID);
      await applyMember(guild, {
        discordId: message.author.id,
        nick: json.nick,
        roleId: json.roleId,
        removeRoleIds: json.removeRoleIds || [],
        unlink: false,
      });
      await message.reply({
        content: "Compte **Minecraft** lie : **" + (json.player || "?") + "**.",
        allowedMentions: { repliedUser: false },
      });
      await message.delete().catch(() => null);
      return;
    }
    const errors = {
      unknown_code: "Code invalide ou expire. Refais **/discord** en jeu.",
      discord_taken: "Ce Discord est deja lie a un autre compte.",
      unauthorized: "Secret API incorrect (PLUGIN_SECRET / config.yml).",
      timeout: "Le serveur Minecraft ne repond pas.",
    };
    await message.reply(errors[json.error] || "Impossible de lier le compte. Reessaie.");
  } catch (err) {
    console.warn("Link:", err.message);
    await message.reply("Le plugin Minecraft est hors ligne.").catch(() => null);
  }
});

client.on("interactionCreate", async (interaction) => {
  if (!interaction.isChatInputCommand() || interaction.commandName !== "stats") {
    return;
  }
  if (interaction.guildId && interaction.guildId !== GUILD_ID) {
    return;
  }
  await interaction.deferReply();
  try {
    const { ok, json } = await pluginFetch("/stats", { method: "GET" });
    if (!ok) {
      await interaction.editReply("Le serveur Minecraft ne répond pas (plugin hors ligne ou secret API).");
      return;
    }
    const filter = interaction.options.getString("event") || "all";
    await interaction.editReply({ embeds: statsEmbeds(json, filter) });
  } catch (err) {
    console.warn("/stats:", err.message);
    await interaction.editReply("Impossible de récupérer les stats IG.").catch(() => null);
  }
});

client.login(TOKEN);
