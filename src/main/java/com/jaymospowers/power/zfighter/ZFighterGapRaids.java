package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.KingdomsMoney;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.26.0, Z Fighter gaps 8) Four themed raids that fill the checklist:
 * <ul>
 * <li><b>Crashed pod raid:</b> a warrior-born pod crashes nearby; its pilot calls for help. Under the full moon the reinforcements turn ape.</li>
 * <li><b>Razed Verdant village:</b> a Verdant settlement burned by a tyrant's soldiers; the attackers are still near.</li>
 * <li><b>Demon army camp:</b> a camp in the demon realm; raising its alarm brings the army.</li>
 * <li><b>Machine army and its bases:</b> in the future dimension, built from existing mechs and androids.</li>
 * </ul>
 * No new original mobs: reuses player-model fighters (ZFighterRogue), MCA villagers and existing vanilla mobs.
 */
public final class ZFighterGapRaids {

    // ---- tuning ----
    /** How often a crashed pod raid may happen (days, and spread). */
    static final long POD_CRASH_DAYS = 7L, POD_CRASH_SPREAD = 48000L;
    /** How often a razed village may be found (days, and spread). */
    static final long RAZED_DAYS = 10L, RAZED_SPREAD = 72000L;
    /** How often a demon camp may be built (days, and spread). */
    static final long DEMON_CAMP_DAYS = 8L, DEMON_CAMP_SPREAD = 60000L;
    /** How often a machine base may appear (days, and spread). Future dimension only. */
    static final long MACHINE_DAYS = 12L, MACHINE_SPREAD = 96000L;

    /** How near a player must be for the event to trigger. */
    static final double NEAR = 128.0;
    /** Minimum world tier for each raid type. */
    static final int POD_CRASH_TIER = 1, RAZED_TIER = 2, DEMON_CAMP_TIER = 2, MACHINE_TIER = 3;
    /** How long a raid stays active before giving up (ticks). */
    static final int RAID_TICKS = 18000;

    /** Crashed pod: how many reinforcement pods come, and the chance they turn ape under full moon. */
    static final int POD_REINFORCEMENTS = 3, POD_APE_CHANCE_NUM = 1, POD_APE_CHANCE_DEN = 3;
    /** Razed village: how many soldiers remain, and the tyrant's scale. */
    static final int RAZED_SOLDIERS = 5, RAZED_TYRANT_SCALE_NUM = 11, RAZED_TYRANT_SCALE_DEN = 10;
    /** Demon camp: how many soldiers in the camp, and the captain's scale. */
    static final int DEMON_CAMP_SOLDIERS = 6, DEMON_CAPTAIN_SCALE_NUM = 13, DEMON_CAPTAIN_SCALE_DEN = 10;
    /** Machine base: how many androids/mechs, and the commander's scale. */
    static final int MACHINE_UNITS = 6, MACHINE_COMMANDER_SCALE_NUM = 12, MACHINE_COMMANDER_SCALE_DEN = 10;

    static final String TAG = "jaymospowers_gap_raid";
    static final long DAY = 24000L;

    /** When the next raid of each type may happen (overworld game time). */
    public static final AttachmentType<Long> NEXT_POD_CRASH = createNext("gap_pod_crash");
    public static final AttachmentType<Long> NEXT_RAZED = createNext("gap_razed");
    public static final AttachmentType<Long> NEXT_DEMON_CAMP = createNext("gap_demon_camp");
    public static final AttachmentType<Long> NEXT_MACHINE = createNext("gap_machine");

    private static AttachmentType<Long> createNext(String name) {
        return AttachmentRegistry.create(JaymosPowers.id(name), builder -> builder.persistent(Codec.LONG));
    }

    /** An active raid: its kind, location, participants, and when it ends. */
    private static final class Raid {
        final ServerLevel level;
        final Kind kind;
        final BlockPos centre;
        final long until;
        final List<UUID> combatants = new ArrayList<>();
        @Nullable UUID boss;
        int spawned;

        Raid(ServerLevel level, Kind kind, BlockPos centre, long until) {
            this.level = level;
            this.kind = kind;
            this.centre = centre;
            this.until = until;
        }
    }

    private enum Kind {
        POD_CRASH("pod_crash", BossEvent.BossBarColor.RED, 0xE06020, "pod_crash"),
        RAZED_VERDANT("razed_verdant", BossEvent.BossBarColor.GREEN, 0x40C040, "razed_verdant"),
        DEMON_CAMP("demon_camp", BossEvent.BossBarColor.PURPLE, 0xA040E0, "demon_camp"),
        MACHINE_ARMY("machine_army", BossEvent.BossBarColor.WHITE, 0xC0C0D0, "machine_army");

        final String key;
        final BossEvent.BossBarColor color;
        final int dust;
        final String langKey;

        Kind(String key, BossEvent.BossBarColor color, int dust, String langKey) {
            this.key = key;
            this.color = color;
            this.dust = dust;
            this.langKey = langKey;
        }
    }

    private static final List<Raid> RAIDS = new ArrayList<>();

    private ZFighterGapRaids() {}

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ZFighterGapRaids::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RAIDS.clear());
        // leftovers from before the server stopped go
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && !tracked(entity)) {
                entity.discard();
            }
        });
    }

    private static boolean tracked(Entity e) {
        for (Raid raid : RAIDS) {
            if (raid.combatants.contains(e.getUUID()) || (raid.boss != null && raid.boss.equals(e.getUUID()))) {
                return true;
            }
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        long ticks = server.getTickCount();
        if (ticks % 20 == 0 && !RAIDS.isEmpty()) {
            updateRaids(server);
        }
        if (ticks % 600 == 0) {
            tryStart(server);
        }
    }

    /** Periodically try to start each kind of raid. */
    private static void tryStart(MinecraftServer server) {
        if (!ZFighterKingdoms.present()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        RandomSource random = overworld.getRandom();

        // Crashed pod raid
        tryStartKind(overworld, now, random, Kind.POD_CRASH, NEXT_POD_CRASH, POD_CRASH_DAYS, POD_CRASH_SPREAD, POD_CRASH_TIER,
            (level, seat, kingdom) -> startPodCrash(level, seat, kingdom));
        // Razed Verdant village
        tryStartKind(overworld, now, random, Kind.RAZED_VERDANT, NEXT_RAZED, RAZED_DAYS, RAZED_SPREAD, RAZED_TIER,
            (level, seat, kingdom) -> startRazedVerdant(level, seat, kingdom));
        // Demon camp (in the Nether)
        ServerLevel nether = net.minecraft.world.level.Level.NETHER == overworld.dimension() ? overworld
            : com.jaymospowers.compat.Stacked.level(server, net.minecraft.world.level.Level.NETHER);
        if (nether != null) {
            tryStartKind(nether, now, random, Kind.DEMON_CAMP, NEXT_DEMON_CAMP, DEMON_CAMP_DAYS, DEMON_CAMP_SPREAD, DEMON_CAMP_TIER,
                (level, seat, kingdom) -> startDemonCamp(level, seat, kingdom));
        }
        // Machine army (future dimension only)
        ServerLevel future = com.jaymospowers.compat.Stacked.level(server, com.jaymospowers.power.chrono.future.Future.FUTURE);
        if (future != null) {
            tryStartKind(future, now, random, Kind.MACHINE_ARMY, NEXT_MACHINE, MACHINE_DAYS, MACHINE_SPREAD, MACHINE_TIER,
                (level, seat, kingdom) -> startMachineArmy(level, seat, kingdom));
        }
    }

    @FunctionalInterface
    private interface RaidStarter {
        boolean start(ServerLevel level, BlockPos seat, String kingdom);
    }

    private static void tryStartKind(ServerLevel level, long now, RandomSource random, Kind kind,
            AttachmentType<Long> nextAttachment, long days, long spread, int minTier, RaidStarter starter) {
        if (!RAIDS.isEmpty()) {
            // Only one gap raid at a time for simplicity
            return;
        }
        Long next = level.getAttached(nextAttachment);
        if (next == null) {
            level.setAttached(nextAttachment, now + days * DAY + random.nextInt((int) spread));
            return;
        }
        if (now < next || !level.isDarkOutside()) {
            return;
        }
        List<ZFighterKingdoms.Settlement> near = new ArrayList<>();
        for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(level.getServer())) {
            BlockPos seat = s.seat();
            if (level.getNearestPlayer(seat.getX(), seat.getY(), seat.getZ(), NEAR,
                    p -> !p.isSpectator() && ZFighterKingdoms.worldTier((Player) p) >= minTier) != null) {
                near.add(s);
            }
        }
        if (near.isEmpty()) {
            return;
        }
        ZFighterKingdoms.Settlement s = near.get(random.nextInt(near.size()));
        if (starter.start(level, s.seat(), s.name())) {
            level.setAttached(nextAttachment, now + days * DAY + random.nextInt((int) spread));
        }
    }

    // ---------------------------------------------------------------- Crashed Pod Raid

    /** A warrior-born pod crashes; its pilot calls for help. Under full moon, reinforcements turn ape. */
    private static boolean startPodCrash(ServerLevel level, BlockPos seat, String kingdom) {
        BlockPos crash = findCrashSite(level, seat);
        if (crash == null) {
            return false;
        }
        Raid raid = new Raid(level, Kind.POD_CRASH, crash, level.getGameTime() + RAID_TICKS);
        RAIDS.add(raid);

        // The crashed pod (visual: a damaged pod entity)
        ZFighterPatrol.Flier flier = ZFighterPatrol.land(level, crash, level.getRandom().nextInt(10),
            f -> {
                ZFighterRogue pilot = pilot(level, f.ground, true, f.name, TAG, r -> {
                    raid.combatants.add(r.getUUID());
                    raid.boss = r.getUUID();
                });
                if (pilot != null) {
                    pilot.addTag("jaymospowers_crashed_pilot");
                    // The pilot calls for help
                    level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.jaymospowers.gap_raid.pod_crash_call", kingdom, pilot.getDisplayName())
                            .withStyle(ChatFormatting.GOLD), false);
                }
            });
        flier.pod.addTag(TAG);
        raid.combatants.add(flier.pod.getUUID());

        // Reinforcement pods come down
        int reinforcements = POD_REINFORCEMENTS;
        boolean fullMoon = isFullMoon(level);
        for (int i = 0; i < reinforcements; i++) {
            BlockPos ground = findReinforcementSpot(level, crash);
            if (ground == null) continue;
            ZFighterPatrol.Flier reinf = ZFighterPatrol.land(level, ground, level.getRandom().nextInt(10),
                f -> {
                    ZFighterRogue raider = pilot(level, f.ground, false, f.name, TAG, r -> raid.combatants.add(r.getUUID()));
                    if (raider != null) {
                        raider.addTag("jaymospowers_reinforcement");
                        // Under full moon, chance to turn ape
                        if (fullMoon && level.getRandom().nextInt(POD_APE_CHANCE_DEN) < POD_APE_CHANCE_NUM) {
                            raider.addTag("jaymospowers_ape_turn");
                        }
                    }
                });
            reinf.pod.addTag(TAG);
            raid.combatants.add(reinf.pod.getUUID());
        }

        level.playSound(null, crash.getX(), crash.getY() + 30, crash.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 1.5F);
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("message.jaymospowers.gap_raid.pod_crash", kingdom).withStyle(ChatFormatting.RED), false);
        return true;
    }

    private static BlockPos findCrashSite(ServerLevel level, BlockPos seat) {
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 12; tries++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 20.0 + random.nextDouble() * 30.0;
            int x = seat.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = seat.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos surface = ZFighterSpace.surface(level, x, z);
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    private static BlockPos findReinforcementSpot(ServerLevel level, BlockPos near) {
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            int x = near.getX() + random.nextInt(31) - 15;
            int z = near.getZ() + random.nextInt(31) - 15;
            BlockPos surface = ZFighterSpace.surface(level, x, z);
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    private static boolean isFullMoon(ServerLevel level) {
        long day = level.getGameTime() / DAY;
        return day % 8 == 0; // full moon every 8 days
    }

    private static @Nullable ZFighterRogue pilot(ServerLevel level, BlockPos at, boolean chief, int name, String tag,
            java.util.function.Consumer<ZFighterRogue> before) {
        ZFighterRogue raider = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
        if (raider == null) {
            return null;
        }
        raider.snapTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        Component shown = chief ? Component.translatable("entity.jaymospowers.raider.chief." + Math.floorMod(name, 8))
            : Component.translatable("entity.jaymospowers.raider");
        raider.become(chief ? ZFighterRogue.TYRANT : ZFighterRogue.CHAMPION, shown);
        double health = chief ? ZFighterRogue.TYRANT_HEALTH * 0.8 : ZFighterRogue.CHAMPION_HEALTH * 1.5;
        raider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        raider.setHealth((float) health);
        raider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(chief ? ZFighterRogue.TYRANT_DAMAGE * 0.7
            : ZFighterRogue.CHAMPION_DAMAGE * 1.2);
        raider.addTag(tag);
        raider.setPersistenceRequired();
        before.accept(raider);
        level.addFreshEntity(raider);
        return raider;
    }

    // ---------------------------------------------------------------- Razed Verdant Village

    /** A Verdant settlement burned by a tyrant's soldiers; attackers still near. */
    private static boolean startRazedVerdant(ServerLevel level, BlockPos seat, String kingdom) {
        BlockPos village = findRazedSite(level, seat);
        if (village == null) {
            return false;
        }
        Raid raid = new Raid(level, Kind.RAZED_VERDANT, village, level.getGameTime() + RAID_TICKS);
        RAIDS.add(raid);

        // Build the razed village structure
        buildRazedVillage(level, village);

        // Spawn remaining soldiers
        for (int i = 0; i < RAZED_SOLDIERS; i++) {
            BlockPos spawn = findSoldierSpot(level, village);
            if (spawn == null) continue;
            ZFighterRogue soldier = soldier(level, spawn, false, TAG, r -> raid.combatants.add(r.getUUID()));
            if (soldier != null) {
                soldier.addTag("jaymospowers_razed_soldier");
            }
        }

        // Spawn the tyrant
        BlockPos tyrantSpot = findSoldierSpot(level, village);
        if (tyrantSpot != null) {
            ZFighterRogue tyrant = soldier(level, tyrantSpot, true, TAG, r -> {
                raid.combatants.add(r.getUUID());
                raid.boss = r.getUUID();
            });
            if (tyrant != null) {
                tyrant.addTag("jaymospowers_razed_tyrant");
                tyrant.getAttribute(Attributes.SCALE).setBaseValue((double) RAZED_TYRANT_SCALE_NUM / RAZED_TYRANT_SCALE_DEN);
            }
        }

        level.playSound(null, village.getX(), village.getY() + 10, village.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 1.3F);
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("message.jaymospowers.gap_raid.razed_verdant", kingdom).withStyle(ChatFormatting.DARK_GREEN), false);
        return true;
    }

    private static BlockPos findRazedSite(ServerLevel level, BlockPos seat) {
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 10; tries++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 25.0 + random.nextDouble() * 35.0;
            int x = seat.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = seat.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos surface = ZFighterSpace.surface(level, x, z);
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    private static BlockPos findSoldierSpot(ServerLevel level, BlockPos near) {
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            int x = near.getX() + random.nextInt(21) - 10;
            int z = near.getZ() + random.nextInt(21) - 10;
            BlockPos surface = ZFighterSpace.surface(level, x, z);
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    private static void buildRazedVillage(ServerLevel level, BlockPos centre) {
        RandomSource random = level.getRandom();
        // Burned huts (nether brick, blackstone, soul fire)
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz > 64) continue;
                if (random.nextFloat() < 0.7F) {
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX() + dx, centre.getZ() + dz);
                    // Burned ground
                    for (int down = 0; down < 3; down++) {
                        BlockPos under = new BlockPos(centre.getX() + dx, y - down, centre.getZ() + dz);
                        BlockState below = level.getBlockState(under);
                        if (below.isAir() || !below.getFluidState().isEmpty()) {
                            level.setBlock(under, Blocks.BLACKSTONE.defaultBlockState(), 3);
                        } else {
                            break;
                        }
                    }
                    // Burned hut remains
                    level.setBlock(new BlockPos(centre.getX() + dx, y, centre.getZ() + dz), Blocks.AIR.defaultBlockState(), 3);
                    if (random.nextFloat() < 0.3F) {
                        level.setBlock(new BlockPos(centre.getX() + dx, y, centre.getZ() + dz), Blocks.SOUL_FIRE.defaultBlockState(), 3);
                    } else if (random.nextFloat() < 0.5F) {
                        level.setBlock(new BlockPos(centre.getX() + dx, y, centre.getZ() + dz), Blocks.NETHER_BRICKS.defaultBlockState(), 3);
                    } else {
                        level.setBlock(new BlockPos(centre.getX() + dx, y, centre.getZ() + dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
                    }
                }
            }
        }
        // Central soul campfire
        int cy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        level.setBlock(new BlockPos(centre.getX(), cy, centre.getZ()), Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3);
    }

    private static @Nullable ZFighterRogue soldier(ServerLevel level, BlockPos at, boolean chief, String tag,
            java.util.function.Consumer<ZFighterRogue> before) {
        ZFighterRogue raider = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
        if (raider == null) {
            return null;
        }
        raider.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        Component shown = chief ? Component.translatable("entity.jaymospowers.gap_raid.razed_tyrant")
            : Component.translatable("entity.jaymospowers.gap_raid.razed_soldier");
        raider.become(chief ? ZFighterRogue.TYRANT : ZFighterRogue.CHAMPION, shown);
        double health = chief ? ZFighterRogue.TYRANT_HEALTH * 0.9 : ZFighterRogue.CHAMPION_HEALTH * 1.4;
        raider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        raider.setHealth((float) health);
        raider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(chief ? ZFighterRogue.TYRANT_DAMAGE * 0.8
            : ZFighterRogue.CHAMPION_DAMAGE * 1.3);
        raider.addTag(tag);
        raider.setPersistenceRequired();
        before.accept(raider);
        level.addFreshEntity(raider);
        return raider;
    }

    // ---------------------------------------------------------------- Demon Army Camp

    /** A camp in the demon realm; raising its alarm brings the army. */
    private static boolean startDemonCamp(ServerLevel level, BlockPos seat, String kingdom) {
        // Find a spot in a demon biome
        BlockPos camp = findDemonCampSite(level, seat);
        if (camp == null) {
            return false;
        }
        Raid raid = new Raid(level, Kind.DEMON_CAMP, camp, level.getGameTime() + RAID_TICKS);
        RAIDS.add(raid);

        // Build the camp structure
        buildDemonCamp(level, camp);

        // Spawn camp soldiers (initially passive until alarm raised)
        for (int i = 0; i < DEMON_CAMP_SOLDIERS; i++) {
            BlockPos spawn = findCampSpot(level, camp);
            if (spawn == null) continue;
            ZFighterRogue soldier = campSoldier(level, spawn, false, TAG, r -> raid.combatants.add(r.getUUID()));
            if (soldier != null) {
                soldier.addTag("jaymospowers_demon_camp_soldier");
                // Initially idle - they wait for alarm
                soldier.getNavigation().stop();
            }
        }

        // Spawn captain
        BlockPos captainSpot = findCampSpot(level, camp);
        if (captainSpot != null) {
            ZFighterRogue captain = campSoldier(level, captainSpot, true, TAG, r -> {
                raid.combatants.add(r.getUUID());
                raid.boss = r.getUUID();
            });
            if (captain != null) {
                captain.addTag("jaymospowers_demon_captain");
                captain.getAttribute(Attributes.SCALE).setBaseValue((double) DEMON_CAPTAIN_SCALE_NUM / DEMON_CAPTAIN_SCALE_DEN);
                // The captain holds the alarm bell
                captain.addTag("jaymospowers_alarm_holder");
            }
        }

        // Place an alarm bell that players can ring
        placeAlarmBell(level, camp);

        level.playSound(null, camp.getX(), camp.getY() + 10, camp.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2.0F, 1.2F);
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("message.jaymospowers.gap_raid.demon_camp", kingdom).withStyle(ChatFormatting.DARK_PURPLE), false);
        return true;
    }

    private static BlockPos findDemonCampSite(ServerLevel level, BlockPos seat) {
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 15; tries++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 30.0 + random.nextDouble() * 50.0;
            int x = seat.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = seat.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos surface = ZFighterSpace.surface(level, x, z);
            if (surface != null && ZFighterDemonRealm.layerAt(level, surface) >= 0) {
                return surface;
            }
        }
        return null;
    }

    private static BlockPos findCampSpot(ServerLevel level, BlockPos near) {
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            int x = near.getX() + random.nextInt(17) - 8;
            int z = near.getZ() + random.nextInt(17) - 8;
            BlockPos surface = ZFighterSpace.surface(level, x, z);
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    private static void buildDemonCamp(ServerLevel level, BlockPos centre) {
        RandomSource random = level.getRandom();
        // Blackstone platform with nether brick tents
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                if (dx * dx + dz * dz > 100) continue;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX() + dx, centre.getZ() + dz);
                // Platform
                level.setBlock(new BlockPos(centre.getX() + dx, y - 1, centre.getZ() + dz), Blocks.BLACKSTONE.defaultBlockState(), 3);
                if (random.nextFloat() < 0.2F) {
                    level.setBlock(new BlockPos(centre.getX() + dx, y - 1, centre.getZ() + dz), Blocks.GILDED_BLACKSTONE.defaultBlockState(), 3);
                }
            }
        }
        // Tents (nether brick walls, blackstone roof)
        for (int[] corner : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            int cx = centre.getX() + corner[0];
            int cz = centre.getZ() + corner[1];
            int cy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        level.setBlock(new BlockPos(cx + dx, cy, cz + dz), Blocks.NETHER_BRICKS.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(cx + dx, cy + 1, cz + dz), Blocks.NETHER_BRICKS.defaultBlockState(), 3);
                    }
                }
            }
            level.setBlock(new BlockPos(cx, cy + 2, cz), Blocks.BLACKSTONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(cx, cy, cz), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
        // Central fire pit
        int cy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        level.setBlock(new BlockPos(centre.getX(), cy, centre.getZ()), Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3);
    }

    private static void placeAlarmBell(ServerLevel level, BlockPos camp) {
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, camp.getX(), camp.getZ());
        level.setBlock(new BlockPos(camp.getX(), y + 1, camp.getZ()), Blocks.BELL.defaultBlockState(), 3);
        // The bell is tagged so we know it's the alarm
        // Note: blocks can't have tags in vanilla, but we track the position
    }

    private static @Nullable ZFighterRogue campSoldier(ServerLevel level, BlockPos at, boolean captain, String tag,
            java.util.function.Consumer<ZFighterRogue> before) {
        ZFighterRogue raider = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
        if (raider == null) {
            return null;
        }
        raider.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        Component shown = captain ? Component.translatable("entity.jaymospowers.gap_raid.demon_captain")
            : Component.translatable("entity.jaymospowers.gap_raid.demon_soldier");
        raider.become(captain ? ZFighterRogue.TYRANT : ZFighterRogue.CHAMPION, shown);
        double health = captain ? ZFighterRogue.TYRANT_HEALTH * 0.7 : ZFighterRogue.CHAMPION_HEALTH * 1.3;
        raider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        raider.setHealth((float) health);
        raider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(captain ? ZFighterRogue.TYRANT_DAMAGE * 0.75
            : ZFighterRogue.CHAMPION_DAMAGE * 1.25);
        if (captain) {
            raider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            raider.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        raider.addTag(tag);
        raider.addTag(ZFighterDemonRealm.ARMY_TAG);
        raider.setPersistenceRequired();
        before.accept(raider);
        level.addFreshEntity(raider);
        return raider;
    }

    // ---------------------------------------------------------------- Machine Army (Future Dimension)

    /** In the future dimension, a machine army and its bases built from existing mechs and androids. */
    private static boolean startMachineArmy(ServerLevel level, BlockPos seat, String kingdom) {
        BlockPos base = findMachineBaseSite(level, seat);
        if (base == null) {
            return false;
        }
        Raid raid = new Raid(level, Kind.MACHINE_ARMY, base, level.getGameTime() + RAID_TICKS);
        RAIDS.add(raid);

        // Build the machine base
        buildMachineBase(level, base);

        // Spawn android/mech units
        for (int i = 0; i < MACHINE_UNITS; i++) {
            BlockPos spawn = findMachineSpot(level, base);
            if (spawn == null) continue;
            ZFighterRogue unit = machineUnit(level, spawn, false, TAG, r -> raid.combatants.add(r.getUUID()));
            if (unit != null) {
                unit.addTag("jaymospowers_machine_unit");
            }
        }

        // Spawn commander
        BlockPos commanderSpot = findMachineSpot(level, base);
        if (commanderSpot != null) {
            ZFighterRogue commander = machineUnit(level, commanderSpot, true, TAG, r -> {
                raid.combatants.add(r.getUUID());
                raid.boss = r.getUUID();
            });
            if (commander != null) {
                commander.addTag("jaymospowers_machine_commander");
                commander.getAttribute(Attributes.SCALE).setBaseValue((double) MACHINE_COMMANDER_SCALE_NUM / MACHINE_COMMANDER_SCALE_DEN);
            }
        }

        level.playSound(null, base.getX(), base.getY() + 10, base.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0F, 1.0F);
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("message.jaymospowers.gap_raid.machine_army", kingdom).withStyle(ChatFormatting.GRAY), false);
        return true;
    }

    private static BlockPos findMachineBaseSite(ServerLevel level, BlockPos seat) {
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 12; tries++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 25.0 + random.nextDouble() * 40.0;
            int x = seat.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = seat.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos surface = ZFighterSpace.surface(level, x, z);
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    private static BlockPos findMachineSpot(ServerLevel level, BlockPos near) {
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            int x = near.getX() + random.nextInt(21) - 10;
            int z = near.getZ() + random.nextInt(21) - 10;
            BlockPos surface = ZFighterSpace.surface(level, x, z);
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    private static void buildMachineBase(ServerLevel level, BlockPos centre) {
        RandomSource random = level.getRandom();
        // Iron/quartz/redstone structure
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                if (dx * dx + dz * dz > 144) continue;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX() + dx, centre.getZ() + dz);
                BlockState floor = random.nextFloat() < 0.5F ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.QUARTZ_BLOCK.defaultBlockState();
                level.setBlock(new BlockPos(centre.getX() + dx, y - 1, centre.getZ() + dz), floor, 3);
                if (random.nextFloat() < 0.1F) {
                    level.setBlock(new BlockPos(centre.getX() + dx, y - 1, centre.getZ() + dz), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                }
            }
        }
        // Walls and towers
        for (int[] corner : new int[][] {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            int cx = centre.getX() + corner[0];
            int cz = centre.getZ() + corner[1];
            int cy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            for (int h = 0; h < 6; h++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                            level.setBlock(new BlockPos(cx + dx, cy + h, cz + dz), Blocks.IRON_BARS.defaultBlockState(), 3);
                        }
                    }
                }
            }
            // Beacon on top
            level.setBlock(new BlockPos(cx, cy + 6, cz), Blocks.BEACON.defaultBlockState(), 3);
        }
        // Central command block
        int cy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        level.setBlock(new BlockPos(centre.getX(), cy, centre.getZ()), Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
    }

    private static @Nullable ZFighterRogue machineUnit(ServerLevel level, BlockPos at, boolean commander, String tag,
            java.util.function.Consumer<ZFighterRogue> before) {
        ZFighterRogue raider = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
        if (raider == null) {
            return null;
        }
        raider.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        Component shown = commander ? Component.translatable("entity.jaymospowers.gap_raid.machine_commander")
            : Component.translatable("entity.jaymospowers.gap_raid.machine_unit");
        raider.become(commander ? ZFighterRogue.TYRANT : ZFighterRogue.CHAMPION, shown);
        double health = commander ? ZFighterRogue.TYRANT_HEALTH * 0.85 : ZFighterRogue.CHAMPION_HEALTH * 1.5;
        raider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        raider.setHealth((float) health);
        raider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(commander ? ZFighterRogue.TYRANT_DAMAGE * 0.9
            : ZFighterRogue.CHAMPION_DAMAGE * 1.4);
        if (commander) {
            raider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
            raider.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        } else {
            raider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            raider.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        // Android visual: iron armor
        Item[][] iron = {{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS}};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < slots.length; i++) {
            raider.setItemSlot(slots[i], new ItemStack(iron[0][i]));
            raider.setDropChance(slots[i], 0.0F);
        }
        raider.addTag(tag);
        raider.addTag(ZFighterAndroid.TAG); // Reuse android tag for recognition
        raider.setPersistenceRequired();
        before.accept(raider);
        level.addFreshEntity(raider);
        return raider;
    }

    // ---------------------------------------------------------------- Raid Updates

    private static void updateRaids(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Iterator<Raid> it = RAIDS.iterator(); it.hasNext();) {
            Raid raid = it.next();
            ServerLevel level = raid.level;

            // Clean up dead combatants
            raid.combatants.removeIf(id -> {
                Entity e = level.getEntity(id);
                return e == null || !e.isAlive();
            });
            if (raid.boss != null && (level.getEntity(raid.boss) == null || !level.getEntity(raid.boss).isAlive())) {
                raid.boss = null;
            }

            // Check if raid is over (all combatants dead or time up)
            if (raid.combatants.isEmpty() || now >= raid.until) {
                endRaid(raid);
                it.remove();
                continue;
            }

            // Periodic particle effects for active raids
            if (now % 40 == 0) {
                raidParticles(raid);
            }

            // AI: make combatants hunt players/defenders
            if (now % 20 == 0) {
                huntTargets(raid.level, raid);
            }
        }
    }

    private static void raidParticles(Raid raid) {
        ServerLevel level = raid.level;
        ParticleOptions dust = MagicFx.wisp(raid.kind.dust, 1.5F);
        Vec3 c = Vec3.atBottomCenterOf(raid.centre);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(c) <= 256.0 * 256.0) {
                for (int h = 0; h < 3; h++) {
                    level.sendParticles(player, dust, true, false,
                        c.x + (level.getRandom().nextDouble() - 0.5) * 20.0,
                        c.y + 2.0 + h,
                        c.z + (level.getRandom().nextDouble() - 0.5) * 20.0,
                        1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    private static void huntTargets(ServerLevel level, Raid raid) {
        AABB box = new AABB(raid.centre).inflate(64.0, 32.0, 64.0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e.isAlive() && !e.isSpectator()
                && (e instanceof Player p && !p.isCreative() || e instanceof AbstractVillager
                || e instanceof net.minecraft.world.entity.animal.golem.IronGolem));
        if (targets.isEmpty()) return;

        for (UUID id : raid.combatants) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || mob.getTarget() != null) continue;
            LivingEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity target : targets) {
                double d = mob.distanceToSqr(target);
                if (d < best) {
                    best = d;
                    nearest = target;
                }
            }
            if (nearest != null) {
                mob.setTarget(nearest);
            }
        }
    }

    private static void endRaid(Raid raid) {
        ServerLevel level = raid.level;
        // Clean up remaining combatants
        for (UUID id : raid.combatants) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                    8, 0.3, 0.5, 0.3, 0.05);
                entity.discard();
            }
        }
        // Broadcast end message
        boolean won = raid.combatants.isEmpty();
        if (won) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.jaymospowers.gap_raid.won", Component.translatable("gap_raid.jaymospowers." + raid.kind.langKey))
                    .withStyle(ChatFormatting.GOLD), false);
        } else {
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.jaymospowers.gap_raid.fled", Component.translatable("gap_raid.jaymospowers." + raid.kind.langKey))
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    // ---------------------------------------------------------------- Debug/Describe

    /** Whether any gap raid is active. */
    static boolean active() {
        return !RAIDS.isEmpty();
    }

    /** Describe active raids for /zfighter gapraids command. */
    static List<Component> describe() {
        List<Component> lines = new ArrayList<>();
        if (RAIDS.isEmpty()) {
            lines.add(Component.translatable("message.jaymospowers.gap_raid.none").withStyle(ChatFormatting.GRAY));
        } else {
            for (Raid raid : RAIDS) {
                lines.add(Component.translatable("message.jaymospowers.gap_raid.state",
                    Component.translatable("gap_raid.jaymospowers." + raid.kind.langKey),
                    raid.centre.getX(), raid.centre.getZ(),
                    raid.combatants.size()).withStyle(ChatFormatting.WHITE));
            }
        }
        return lines;
    }
}