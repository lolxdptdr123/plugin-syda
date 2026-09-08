require("dotenv").config();
const { Client, GatewayIntentBits } = require("discord.js");

const TOKEN = process.env.DISCORD_TOKEN;
const GUILD_ID = process.env.GUILD_ID;
const CHANNEL_ID = process.env.LINK_CHANNEL_ID;
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
    await member.setNickname(null, "Draftmc unlink").catch(() => null);
    return;
  }
  if (job.nick) {
    await member.setNickname(job.nick, "Draftmc link").catch(() => null);
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

client.once("ready", async () => {
  console.log("Bot pret :", client.user.tag);
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
  setInterval(pollSync, 15000);
  pollSync();
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

client.login(TOKEN);
