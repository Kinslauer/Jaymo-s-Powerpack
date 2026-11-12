package com.AdamNew.dynamicwaters.client.gui;

import com.AdamNew.dynamicwaters.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.function.Supplier;

public final class DWConfigCategories {
    public record Option(ModConfigSpec.ConfigValue<?> value, String key, boolean worldgen) {
        public Component label() {
            return Component.translatable("dynamicwaters.configuration." + key);
        }
    }

    public record Category(String id, List<Option> options, Supplier<Component> status,
            net.minecraft.world.item.Item icon, java.util.function.BooleanSupplier enabled) {
        public Component title() {
            return Component.translatable("dynamicwaters.gui.category." + id);
        }

        public Component description() {
            return Component.translatable("dynamicwaters.gui.category." + id + ".desc");
        }
    }

    private DWConfigCategories() {
    }

    public static List<Category> all() {
        return List.of(
                new Category("lowlandTunnels", List.of(
                        new Option(Config.NORMAL_RIVER_TUNNELS, "normalRiverTunnels", true),
                        new Option(Config.NORMAL_RIVER_CANYON_HEIGHT, "normalRiverCanyonHeight", true),
                        new Option(Config.NORMAL_TUNNEL_HEIGHT, "normalTunnelHeight", true),
                        new Option(Config.NORMAL_TUNNEL_LEDGE, "normalTunnelLedge", true),
                        new Option(Config.NORMAL_TUNNEL_PILLARS, "normalTunnelPillars", true),
                        new Option(Config.NORMAL_TUNNEL_FORMATIONS, "normalTunnelFormations", true)),
                        () -> Config.NORMAL_RIVER_TUNNELS.get()
                                ? Component.empty().append(on())
                                        .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                                        .append(Component.literal("Y " + Math.round(Config.NORMAL_RIVER_CANYON_HEIGHT.get()))
                                                .withStyle(ChatFormatting.AQUA))
                                : off(),
                        net.minecraft.world.item.Items.DRIPSTONE_BLOCK,
                        () -> Config.NORMAL_RIVER_TUNNELS.get()),

                new Category("mountainTunnels", List.of(
                        new Option(Config.MOUNTAIN_RIVER_TUNNELS, "mountainRiverTunnels", true),
                        new Option(Config.MOUNTAIN_TUNNEL_FREQUENCY, "mountainTunnelFrequency", true),
                        new Option(Config.MOUNTAIN_TUNNEL_LENGTH, "mountainTunnelLength", true),
                        new Option(Config.MOUNTAIN_TUNNEL_DEPTH, "mountainTunnelDepth", true),
                        new Option(Config.MOUNTAIN_TUNNEL_HEIGHT, "mountainTunnelHeight", true),
                        new Option(Config.MOUNTAIN_TUNNEL_LEDGE, "mountainTunnelLedge", true)),
                        () -> Config.MOUNTAIN_RIVER_TUNNELS.get()
                                ? Component.empty().append(on())
                                        .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                                        .append(enumStatusShort("TunnelFrequency", Config.MOUNTAIN_TUNNEL_FREQUENCY.get()))
                                : off(),
                        net.minecraft.world.item.Items.POINTED_DRIPSTONE,
                        () -> Config.MOUNTAIN_RIVER_TUNNELS.get()),

                new Category("lowlandRivers", List.of(
                        new Option(Config.NORMAL_RIVER_MODE, "normalRiverMode", true),
                        new Option(Config.NORMAL_RIVER_DENSITY, "normalRiverDensity", true),
                        new Option(Config.DELTA_INLAND_REACH, "deltaInlandReach", true),
                        new Option(Config.RIVER_SPLITS, "riverSplits", true),
                        new Option(Config.RIVER_FALLEN_LOGS, "riverFallenLogs", true)),
                        () -> Config.normalRiverMode() == Config.NormalRiverMode.DYNAMIC
                                ? Component.empty().append(enumStatusShort("NormalRiverMode", Config.NORMAL_RIVER_MODE.get()))
                                        .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                                        .append(enumStatusShort("DensityLevel", Config.NORMAL_RIVER_DENSITY.get()))
                                : enumStatusShort("NormalRiverMode", Config.NORMAL_RIVER_MODE.get()),
                        net.minecraft.world.item.Items.WATER_BUCKET,
                        () -> Config.normalRiverMode() != Config.NormalRiverMode.NONE),

                new Category("mountainRivers", List.of(
                        new Option(Config.ENABLE_watersheds, "enableWatersheds", true),
                        new Option(Config.MOUNTAIN_RIVER_DENSITY, "mountainRiverDensity", true),
                        new Option(Config.MOUNTAIN_RIVER_WATERFALLS, "mountainRiverWaterfalls", true),
                        new Option(Config.MOUNTAIN_RIVER_ICE, "mountainRiverIce", false),
                        new Option(Config.RIVER_ICE_FLOES, "riverIceFloes", false),
                        new Option(Config.MOUNTAIN_RIVER_BIOME, "mountainRiverBiome", true)),
                        () -> Config.ENABLE_watersheds.get()
                                ? Component.translatable("dynamicwaters.gui.on").withStyle(ChatFormatting.GREEN)
                                        .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                                        .append(enumStatusShort("DensityLevel", Config.MOUNTAIN_RIVER_DENSITY.get()))
                                : off(),
                        net.minecraft.world.item.Items.STONE,
                        () -> Config.ENABLE_watersheds.get()),

                new Category("lakes", List.of(
                        new Option(Config.ENABLE_MOUNTAIN_LAKES, "enableMountainLakes", true)),
                        () -> Config.ENABLE_MOUNTAIN_LAKES.get() ? on() : off(),
                        net.minecraft.world.item.Items.LILY_PAD,
                        () -> Config.ENABLE_MOUNTAIN_LAKES.get()),

                new Category("waterside", List.of(
                        new Option(Config.RIVERBANK_PALETTES, "riverbankPalettes", true),
                        new Option(Config.WATER_PLANTS, "waterPlants", true),
                        new Option(Config.RIVER_BOULDERS, "riverBoulders", true)),
                        DWConfigCategories::watersideStatus,
                        net.minecraft.world.item.Items.SEA_PICKLE,
                        () -> Config.RIVERBANK_PALETTES.get() || Config.WATER_PLANTS.get() || Config.RIVER_BOULDERS.get()),

                new Category("flow", List.of(
                        new Option(Config.PLAYER_SPEED, "playerSpeed", false),
                        new Option(Config.ANIMAL_SPEED, "animalSpeed", false),
                        new Option(Config.WATER_MOB_SPEED, "waterMobSpeed", false),
                        new Option(Config.ITEM_SPEED, "itemSpeed", false),
                        new Option(Config.DEFAULT_SPEED, "defaultSpeed", false),
                        new Option(Config.SABLE_SHIP_SPEED, "sableShipSpeed", false),
                        new Option(Config.RAIN_FLOW_BOOST, "rainFlowBoost", false),
                        new Option(Config.THUNDERSTORM_FLOW_BOOST, "thunderstormFlowBoost", false),
                        new Option(Config.VANILLA_RIVER_FLOW, "vanillaRiverFlow", false)),
                        () -> Component.literal(String.format("%.1f×", Config.PLAYER_SPEED.get())).withStyle(ChatFormatting.AQUA),
                        net.minecraft.world.item.Items.OAK_BOAT,
                        () -> true),

                new Category("visuals", List.of(
                        new Option(Config.ENABLE_RIVER_VISUAL_FLOW, "enableRiverVisualFlow", false),
                        new Option(Config.ENABLE_RIVER_PARTICLES, "enableRiverParticles", false),
                        new Option(Config.RIVER_PARTICLE_DENSITY, "riverParticleDensity", false),
                        new Option(Config.ENABLE_RIVER_SOUND, "enableRiverSound", false),
                        new Option(Config.CUSTOM_MAIN_MENU_PANORAMA, "customMainMenuPanorama", false),
                        new Option(Config.SHOW_MAIN_MENU_BUTTON, "showMainMenuButton", false)),
                        () -> Config.ENABLE_RIVER_VISUAL_FLOW.get() ? on() : off(),
                        net.minecraft.world.item.Items.PAINTING,
                        () -> Config.ENABLE_RIVER_VISUAL_FLOW.get()),

                new Category("general", List.of(
                        new Option(Config.SHOW_WELCOME_MESSAGE, "showWelcomeMessage", false),
                        new Option(Config.DISABLE_TECTONIC_UNDERGROUND_RIVERS, "disableTectonicUndergroundRivers", true)),
                        () -> Component.empty(),
                        net.minecraft.world.item.Items.COMPASS,
                        () -> true));
    }

    private static Component watersideStatus() {
        final int on = (Config.RIVERBANK_PALETTES.get() ? 1 : 0) + (Config.WATER_PLANTS.get() ? 1 : 0)
                + (Config.RIVER_BOULDERS.get() ? 1 : 0);
        return on == 3 ? on() : on == 0 ? off() : Component.literal(on + "/3").withStyle(ChatFormatting.AQUA);
    }

    static Component on() {
        return Component.translatable("dynamicwaters.gui.on").withStyle(ChatFormatting.GREEN);
    }

    static Component off() {
        return Component.translatable("dynamicwaters.gui.off").withStyle(ChatFormatting.RED);
    }

    static Component enumStatusShort(String enumName, Enum<?> value) {
        final String full = enumStatus(enumName, value).getString();
        final int paren = full.indexOf(" (");
        return Component.literal(paren > 0 ? full.substring(0, paren) : full).withStyle(ChatFormatting.AQUA);
    }

    static Component enumStatus(String enumName, Enum<?> value) {
        final String key = "dynamicwaters.configuration." + enumName + "." + value.name();
        return net.minecraft.locale.Language.getInstance().has(key)
                ? Component.translatable(key).withStyle(ChatFormatting.AQUA)
                : Component.literal(value.name()).withStyle(ChatFormatting.AQUA);
    }
}
