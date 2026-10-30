package com.krimson.practicebot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KrimsonPracticeBot extends JavaPlugin implements Listener, CommandExecutor {
    private final BotNpc.Settings settings = new BotNpc.Settings();
    private BotNpc bot;
    private UUID botOwnerId;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("bot") != null) getCommand("bot").setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (bot != null) {
                if (!bot.getPlayer().isOnline()) {
                    bot = null;
                } else {
                    bot.tick(settings);
                }
            }
        }, 1L, 1L);
        getLogger().info("KrimsonPracticeBot enabled.");
    }

    @Override
    public void onDisable() {
        if (bot != null) {
            bot.despawn();
            bot = null;
            botOwnerId = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("krimsonpracticebot.use")) {
            player.sendMessage(Component.text("You don't have permission to use this.", NamedTextColor.RED));
            return true;
        }
        openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        BotMenuHolder holder = new BotMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("KRIMSON  /  PRACTICE BOT", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        holder.inventory = inventory;
        render(inventory);
        player.openInventory(inventory);
    }

    private void render(Inventory inventory) {
        inventory.clear();

        ItemStack frame = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, frame);

        // Two clearly separated, balanced columns.
        inventory.setItem(4, item(Material.NETHER_STAR, "KRIMSON PRACTICE BOT", NamedTextColor.AQUA,
                "Player-style knockback training dummy", "Spawned in your current world and location."));
        inventory.setItem(11, armorButton(Material.NETHERITE_HELMET, "Helmet", 0));
        inventory.setItem(20, armorButton(Material.NETHERITE_CHESTPLATE, "Chestplate", 1));
        inventory.setItem(29, armorButton(Material.NETHERITE_LEGGINGS, "Leggings", 2));
        inventory.setItem(38, armorButton(Material.NETHERITE_BOOTS, "Boots", 3));

        inventory.setItem(15, toggleButton(Material.FEATHER, "Slow Falling", settings.slowFalling,
                "Gives the bot permanent Slow Falling."));
        inventory.setItem(24, toggleButton(Material.SHIELD, "Shielding", settings.shielding,
                "Makes the bot continuously block with a shield.",
                "The shield occupies the off-hand while enabled."));
        inventory.setItem(33, toggleButton(Material.ENDER_EYE, "Look at You", settings.lookAt,
                "Makes the bot look at the nearest player."));
        inventory.setItem(42, toggleButton(Material.BLAZE_POWDER, "Fire Resistance", settings.fireResistance,
                "Gives the bot permanent Fire Resistance."));
        inventory.setItem(43, toggleButton(Material.TOTEM_OF_UNDYING, "Owner God Mode", settings.ownerGodMode,
                "While your bot is alive, you take no damage."));

        inventory.setItem(48, item(Material.LIME_CONCRETE, "SPAWN BOT", NamedTextColor.GREEN,
                "Spawn at your exact location", "Works in any loaded world, including RTP worlds."));
        inventory.setItem(49, item(bot == null ? Material.GRAY_DYE : Material.EMERALD,
                bot == null ? "BOT STATUS: DESPAWNED" : "BOT STATUS: ACTIVE",
                bot == null ? NamedTextColor.GRAY : NamedTextColor.GREEN,
                "Name: " + BotNpc.DISPLAY_NAME));
        inventory.setItem(50, item(Material.RED_CONCRETE, "DESPAWN BOT", NamedTextColor.RED,
                "Remove the practice bot."));

        // Small visual labels keep the left/right layout obvious.
        inventory.setItem(10, item(Material.IRON_CHESTPLATE, "ARMOUR", NamedTextColor.AQUA,
                "Select an armour piece to switch its enchantment."));
        inventory.setItem(16, item(Material.COMPARATOR, "BOT OPTIONS", NamedTextColor.LIGHT_PURPLE,
                "Toggle the bot's effects and behaviour."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BotMenuHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        switch (event.getSlot()) {
            case 11 -> settings.blastProtection[0] = !settings.blastProtection[0];
            case 20 -> settings.blastProtection[1] = !settings.blastProtection[1];
            case 29 -> settings.blastProtection[2] = !settings.blastProtection[2];
            case 38 -> settings.blastProtection[3] = !settings.blastProtection[3];
            case 15 -> settings.slowFalling = !settings.slowFalling;
            case 24 -> settings.shielding = !settings.shielding;
            case 33 -> settings.lookAt = !settings.lookAt;
            case 42 -> settings.fireResistance = !settings.fireResistance;
            case 43 -> settings.ownerGodMode = !settings.ownerGodMode;
            case 48 -> {
                if (bot != null && bot.getPlayer().isOnline()) {
                    player.sendMessage(Component.text("The practice bot is already spawned.", NamedTextColor.YELLOW));
                } else {
                    try {
                        bot = BotNpc.spawn(player.getLocation().clone(), settings);
                        botOwnerId = player.getUniqueId();
                        player.sendMessage(Component.text("Practice bot spawned.", NamedTextColor.GREEN));
                    } catch (Exception exception) {
                        getLogger().severe("Could not spawn practice bot: " + exception.getMessage());
                        player.sendMessage(Component.text("Could not spawn the bot. Check the server console.", NamedTextColor.RED));
                    }
                }
            }
            case 50 -> {
                if (bot == null) {
                    player.sendMessage(Component.text("The practice bot is not spawned.", NamedTextColor.YELLOW));
                } else {
                    bot.despawn();
                    bot = null;
                    botOwnerId = null;
                    player.sendMessage(Component.text("Practice bot despawned.", NamedTextColor.RED));
                }
            }
            default -> { return; }
        }

        if (bot != null) bot.refreshEquipment(settings);
        render(holder.inventory);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BotMenuHolder) {
            event.setCancelled(true);
        }
    }

    // NPCs are real ServerPlayers, so suppress the normal join/quit broadcasts and tab entry.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (BotNpc.isBot(event.getPlayer().getUniqueId())) {
            event.setJoinMessage(null);
        }
        if (bot != null && bot.getPlayer().isOnline()) {
            Bukkit.getScheduler().runTask(this, bot::hideFromTabList);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID quittingId = event.getPlayer().getUniqueId();

        if (BotNpc.isBot(quittingId)) {
            event.setQuitMessage(null);
            BotNpc.forgetBot(quittingId);
        }

        // The practice bot belongs to the player who spawned it. Remove it immediately
        // when that owner disconnects; it is not left behind for the next session.
        if (bot != null && quittingId.equals(botOwnerId)) {
            bot.despawn();
            bot = null;
            botOwnerId = null;
        }
    }

    // God Mode protects only the bot's owner, and only while that owner's bot is alive.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOwnerDamage(EntityDamageEvent event) {
        if (!settings.ownerGodMode || bot == null || bot.getPlayer().isDead() || botOwnerId == null) return;
        if (event.getEntity() instanceof Player damaged && damaged.getUniqueId().equals(botOwnerId)) {
            event.setCancelled(true);
        }
    }

    // Let the normal hit calculation and knockback happen, but cap lethal damage.
    // The bot has a large health pool and is restored every tick.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotDamage(EntityDamageEvent event) {
        if (bot == null || !event.getEntity().getUniqueId().equals(bot.getPlayer().getUniqueId())) return;
        double health = bot.getPlayer().getHealth();
        if (event.getFinalDamage() >= health) {
            event.setDamage(Math.max(0.0, health - 1.0));
        }
    }

    private ItemStack armorButton(Material material, String piece, int index) {
        boolean blast = settings.blastProtection[index];
        return item(material, piece, NamedTextColor.GOLD,
                "Current: " + (blast ? "Blast Protection IV" : "Protection IV"),
                "Click to switch enchantment.");
    }

    private ItemStack toggleButton(Material material, String name, boolean enabled, String... description) {
        List<String> lore = new ArrayList<>(List.of(description));
        lore.add("");
        lore.add("Status: " + (enabled ? "ON" : "OFF"));
        lore.add("Click to toggle.");
        return item(material, name + (enabled ? "  [ON]" : "  [OFF]"),
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED, lore.toArray(String[]::new));
    }

    private ItemStack item(Material material, String name, NamedTextColor color, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (loreLines.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static final class BotMenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public Inventory getInventory() { return inventory; }
    }
}
