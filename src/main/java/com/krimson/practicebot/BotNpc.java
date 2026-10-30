package com.krimson.practicebot;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A real ServerPlayer NPC, so attacks use normal player knockback handling. */
public final class BotNpc {
    public static final String DISPLAY_NAME = "krimsonprac.mc.gg";

    // Classic Steve texture, encoded as a normal GameProfile textures property.
    private static final String STEVE_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjBhNWJkMDE2YjNjOWExYjkyNzJlNDkyOWUzMDgyN2E2N2JlNGViYjIxOTAxN2FkYmJjNGE0ZDIyZWJkNWIxIn19fQ==";

    private static final Set<UUID> BOT_IDS = ConcurrentHashMap.newKeySet();

    public static final class Settings {
        public boolean slowFalling;
        public boolean shielding;
        public boolean lookAt;
        public boolean fireResistance;
        public boolean ownerGodMode;
        public final boolean[] blastProtection = new boolean[4]; // helmet, chest, legs, boots
    }

    private final ServerPlayer handle;
    private final Player player;
    private final UUID uuid;

    private BotNpc(ServerPlayer handle) {
        this.handle = handle;
        this.player = (Player) handle.getBukkitEntity();
        this.uuid = handle.getUUID();
    }

    public static BotNpc spawn(Location location, Settings settings) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("The spawn location has no world.");
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        // The internal profile name stays within Minecraft's 16-character limit.
        // The visible overhead name is set separately to the exact requested text.
        UUID id = UUID.randomUUID();
        GameProfile profile = new GameProfile(id, "krimsonpracmcgg");
        profile.properties().put("textures", new Property("textures", STEVE_TEXTURE));

        ServerPlayer npc = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        npc.setPos(location.getX(), location.getY(), location.getZ());
        npc.setYRot(location.getYaw());
        npc.setXRot(location.getPitch());

        // Do not mark the entity invulnerable: normal hit/knockback processing must run.
        npc.setInvulnerable(false);

        BOT_IDS.add(id); // Register before placeNewPlayer fires PlayerJoinEvent.
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(
                connection,
                npc,
                CommonListenerCookie.createInitial(profile, false)
        );

        BotNpc bot = new BotNpc(npc);
        bot.player.setCustomName(DISPLAY_NAME);
        bot.player.setCustomNameVisible(true);
        bot.player.setInvulnerable(false);
        bot.configureHealth();
        bot.refreshEquipment(settings);
        bot.hideFromTabList();
        return bot;
    }

    public static boolean isBot(UUID id) {
        return BOT_IDS.contains(id);
    }

    public static void forgetBot(UUID id) {
        BOT_IDS.remove(id);
    }

    public Player getPlayer() {
        return player;
    }

    public void hideFromTabList() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(uuid)) {
                viewer.unlistPlayer(player);
            }
        }
    }

    public void despawn() {
        if (handle.connection != null) {
            handle.connection.disconnect(Component.literal("Practice bot removed"));
        } else {
            player.remove();
        }
    }

    private void configureHealth() {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(2048.0);
            player.setHealth(2048.0);
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

    public void refreshEquipment(Settings settings) {
        PlayerInventory inventory = player.getInventory();
        inventory.setArmorContents(new ItemStack[]{
                enchantedArmor(Material.NETHERITE_BOOTS, settings.blastProtection[3]),
                enchantedArmor(Material.NETHERITE_LEGGINGS, settings.blastProtection[2]),
                enchantedArmor(Material.NETHERITE_CHESTPLATE, settings.blastProtection[1]),
                enchantedArmor(Material.NETHERITE_HELMET, settings.blastProtection[0])
        });
        inventory.setItemInMainHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        inventory.setItemInOffHand(settings.shielding
                ? new ItemStack(Material.SHIELD)
                : new ItemStack(Material.TOTEM_OF_UNDYING));
    }

    /** Run once each server tick. Keeps settings/equipment active without AI wandering. */
    public void tick(Settings settings) {
        if (!player.isOnline() || player.isDead()) return;

        configureHealth();
        applyEffect(PotionEffectType.SLOW_FALLING, settings.slowFalling);
        applyEffect(PotionEffectType.FIRE_RESISTANCE, settings.fireResistance);

        // Refill either hand if a totem is consumed or an item is moved by another plugin.
        PlayerInventory inventory = player.getInventory();
        if (inventory.getItemInMainHand().getType() != Material.TOTEM_OF_UNDYING) {
            inventory.setItemInMainHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        }
        Material wantedOffhand = settings.shielding ? Material.SHIELD : Material.TOTEM_OF_UNDYING;
        if (inventory.getItemInOffHand().getType() != wantedOffhand) {
            inventory.setItemInOffHand(new ItemStack(wantedOffhand));
        }

        if (settings.shielding) {
            if (!handle.isUsingItem()) handle.startUsingItem(InteractionHand.OFF_HAND);
        } else if (handle.isUsingItem()) {
            handle.stopUsingItem();
        }

        if (settings.lookAt) {
            Player nearest = null;
            double nearestDistance = 24.0 * 24.0;
            for (Player candidate : player.getWorld().getPlayers()) {
                if (candidate.getUniqueId().equals(uuid)) continue;
                double distance = candidate.getLocation().distanceSquared(player.getLocation());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
            }
            if (nearest != null) {
                Location eye = nearest.getEyeLocation();
                handle.lookAt(EntityAnchorArgument.Anchor.EYES,
                        new Vec3(eye.getX(), eye.getY(), eye.getZ()));
            }
        }
    }

    private void applyEffect(PotionEffectType type, boolean enabled) {
        if (enabled) {
            player.addPotionEffect(new PotionEffect(
                    type, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        } else {
            player.removePotionEffect(type);
        }
    }

    private static ItemStack enchantedArmor(Material material, boolean blast) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(blast ? Enchantment.BLAST_PROTECTION : Enchantment.PROTECTION, 4, true);
        item.setItemMeta(meta);
        return item;
    }
}
