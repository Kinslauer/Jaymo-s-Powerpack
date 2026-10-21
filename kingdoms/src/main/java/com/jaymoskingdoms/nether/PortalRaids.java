package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.FactionType;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.WorldTiers;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Kingdoms 0.7.145) The Nether faction, stage 3: Hornkin raid parties come through a Nether portal near a town for its
 * gold. While a Hornkin war fort stands in the Nether, a town with a portal near it and a player about may now and then
 * see a party step out of it. They go for the town's chests (gold only), fight whoever crosses them, and when they have
 * enough gold or their time is up they walk back into the portal with it. An Emberborn can buy the whole party off with a
 * gold block ({@link Hornkin}). The raiders themselves keep their state ({@link Hornkin#raid}); this only sends them.
 */
public final class PortalRaids {
	/** How often towns are looked over, and the chance each time for a town that could be raided. */
	static final int CHECK_EVERY = 20 * 20;
	static final float RAID_CHANCE = 0.03F;
	/** A town raided isn't raided again for this long (not saved: a restart forgets). */
	static final long TOWN_GAP = 20L * 60 * 60;
	/** A player this near the seat, and a portal this near it. */
	static final double PLAYER_RANGE = 96.0;
	/** (Andy) Only for a player in this world tier or higher (Borderlands, the second); ops' /kingdom netherfort raid ignores it. */
	static final WorldTiers.Tier MIN_TIER = WorldTiers.Tier.BORDERLANDS;
	static final int PORTAL_RANGE = 96;
	/** Who hears of it. */
	static final double NEWS_RANGE = 160.0;
	/** How many come, and how long they stay before going home (sooner with RAID_LOOT gold: see Hornkin). */
	static final int PARTY_MIN = 3, PARTY_MAX = 6;
	static final float BRUTE_CHANCE = 0.3F;
	static final int RAID_TICKS = 20 * 60 * 5;
	/** Where they step out: a free spot this near the portal block. */
	static final int STEP_OUT = 3;
	/** Bought off: every raider of the party this near turns for home. */
	static final double BUY_OFF_RANGE = 128.0;
	/** {@code /kingdom netherfort raid} picks a town this near. */
	static final double TEST_TOWN_RANGE = 256.0;

	private static final Map<Integer, Long> LAST = new HashMap<>();

	private PortalRaids() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_EVERY == 0) {
				consider(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	private static void consider(MinecraftServer server) {
		ServerLevel overworld = Factions.level(server);
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (overworld == null || nether == null || overworld.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		RandomSource random = overworld.getRandom();
		NetherForts.Fort from = standingFort(nether, random);
		if (from == null) {
			return;
		}
		long now = overworld.getGameTime();
		for (Faction town : Factions.all(server)) {
			if (town.fallen() || town.type() == FactionType.BARBARIAN || now - LAST.getOrDefault(town.id(), -TOWN_GAP) < TOWN_GAP
				|| !overworld.isPositionEntityTicking(town.seat()) || overworld.getNearestPlayer(town.seat().getX() + 0.5, town.seat().getY(),
					town.seat().getZ() + 0.5, PLAYER_RANGE, p -> !p.isSpectator() && p instanceof Player player
						&& WorldTiers.of(player).ordinal() >= MIN_TIER.ordinal()) == null) {
				continue;
			}
			if (random.nextFloat() < RAID_CHANCE && start(overworld, town.seat(), town.name(), from)) {
				LAST.put(town.id(), now);
				return;
			}
		}
	}

	/** A Hornkin fort still standing in the Nether, any one; null with none (no forts, no raids). */
	static NetherForts.@Nullable Fort standingFort(ServerLevel nether, RandomSource random) {
		List<NetherForts.Fort> standing = NetherForts.forts(nether).stream().filter(f -> f.side() == NetherForts.HORNKIN && !f.fallen()).toList();
		return standing.isEmpty() ? null : standing.get(random.nextInt(standing.size()));
	}

	/** Sends a party from {@code from} through the portal nearest {@code seat} (a town's). False if it has none near (or nowhere to step out). */
	static boolean start(ServerLevel level, BlockPos seat, String town, NetherForts.Fort from) {
		Optional<BlockPos> portal = level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.NETHER_PORTAL), seat, PORTAL_RANGE,
			PoiManager.Occupancy.ANY);
		if (portal.isEmpty() || !level.isPositionEntityTicking(portal.get())) {
			return false;
		}
		BlockPos out = stepOut(level, portal.get());
		if (out == null) {
			return false;
		}
		RandomSource random = level.getRandom();
		int raid = random.nextInt(Integer.MAX_VALUE);
		int size = PARTY_MIN + random.nextInt(PARTY_MAX - PARTY_MIN + 1);
		int sent = 0;
		for (int i = 0; i < size; i++) {
			EntityType<Hornkin> type = i == 0 && random.nextFloat() < BRUTE_CHANCE ? Nether.HORNKIN_BRUTE
				: random.nextInt(3) == 0 ? Nether.HORNKIN_HURLER : Nether.HORNKIN;
			Hornkin raider = type.create(level, EntitySpawnReason.EVENT);
			if (raider == null) {
				continue;
			}
			raider.snapTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			raider.finalizeSpawn(level, level.getCurrentDifficultyAt(out), EntitySpawnReason.EVENT, null);
			raider.raid(raid, out, seat, RAID_TICKS);
			level.addFreshEntity(raider);
			sent++;
		}
		if (sent == 0) {
			return false;
		}
		level.sendParticles(ParticleTypes.PORTAL, out.getX() + 0.5, out.getY() + 1.0, out.getZ() + 0.5, 60, 0.6, 1.0, 0.6, 0.4);
		level.playSound(null, out, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 1.0F, 0.7F);
		level.playSound(null, out, SoundEvents.HOGLIN_ANGRY, SoundSource.HOSTILE, 2.0F, 0.6F);
		news(level, seat, Component.translatable("message.jaymoskingdoms.portal_raid", from.name(), town).withStyle(ChatFormatting.RED));
		JaymosKingdoms.LOGGER.info("{} Hornkin from {} came through the portal at {} to raid {}", sent, from.name(), portal.get(), town);
		return true;
	}

	/** A spot by the portal (not in it) with room to stand and ground under it, nearest first; null if none. */
	private static @Nullable BlockPos stepOut(ServerLevel level, BlockPos portal) {
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(portal.offset(-STEP_OUT, -STEP_OUT, -STEP_OUT), portal.offset(STEP_OUT, STEP_OUT, STEP_OUT))) {
			if (free(level, pos) && free(level, pos.above()) && free(level, pos.above(2)) && level.getBlockState(pos.below()).isSolid()) {
				spots.add(pos.immutable());
			}
		}
		return spots.stream().min(Comparator.comparingDouble(p -> p.distSqr(portal))).orElse(null);
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		var state = level.getBlockState(pos);
		return !state.is(Blocks.NETHER_PORTAL) && state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	/** An Emberborn paid {@code raider} in gold: its whole party turns for home. */
	static void boughtOff(ServerLevel level, Hornkin raider, Player payer) {
		for (Hornkin h : level.getEntitiesOfClass(Hornkin.class, raider.getBoundingBox().inflate(BUY_OFF_RANGE), h -> h.raidId() == raider.raidId())) {
			h.goHome();
		}
		level.playSound(null, raider.getX(), raider.getY(), raider.getZ(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.HOSTILE, 1.5F, 0.6F);
		news(level, raider.blockPosition(), Component.translatable("message.jaymoskingdoms.portal_raid_bought", payer.getDisplayName()).withStyle(ChatFormatting.GOLD));
	}

	/** A raider steps back into the portal, gold and all. */
	static void leave(ServerLevel level, Hornkin raider) {
		level.sendParticles(ParticleTypes.PORTAL, raider.getX(), raider.getY() + 1.0, raider.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
		level.playSound(null, raider.getX(), raider.getY(), raider.getZ(), SoundEvents.PORTAL_TRAVEL, SoundSource.HOSTILE, 0.3F, 1.2F);
		raider.discard();
	}

	private static void news(ServerLevel level, BlockPos at, Component message) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(Vec3.atCenterOf(at)) < NEWS_RANGE * NEWS_RANGE) {
				p.sendSystemMessage(message);
			}
		}
	}

	/** {@code /kingdom netherfort raid}: a party on the town nearest whoever runs it (within TEST_TOWN_RANGE; else on the spot itself), now. */
	static int raidNow(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		MinecraftServer server = c.getSource().getServer();
		if (level != Factions.level(server)) {
			c.getSource().sendFailure(Component.literal("Portal raids are on the towns of the Overworld"));
			return 0;
		}
		ServerLevel nether = server.getLevel(Level.NETHER);
		NetherForts.Fort from = nether == null ? null : standingFort(nether, level.getRandom());
		if (from == null) {
			c.getSource().sendFailure(Component.literal("No Hornkin fort stands in the Nether to raid from (/kingdom netherfort found there first)"));
			return 0;
		}
		Vec3 here = c.getSource().getPosition();
		Faction town = Factions.all(server).stream().filter(f -> !f.fallen() && f.type() != FactionType.BARBARIAN
				&& Vec3.atCenterOf(f.seat()).distanceToSqr(here) < TEST_TOWN_RANGE * TEST_TOWN_RANGE)
			.min(Comparator.comparingDouble(f -> Vec3.atCenterOf(f.seat()).distanceToSqr(here))).orElse(null);
		BlockPos seat = town != null ? town.seat() : BlockPos.containing(here);
		String name = town != null ? town.name() : "this place";
		if (!start(level, seat, name, from)) {
			c.getSource().sendFailure(Component.literal("No loaded Nether portal within " + PORTAL_RANGE + " blocks of " + name));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Hornkin from " + from.name() + " raid " + name), true);
		return 1;
	}
}
