package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.Encounters;
import com.jaymoskingdoms.Factions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.56) The mounts of legend (Mo' Creatures' nightmare and bat horse, as breeds of our horses): now and then one is
 * found running wild near a player out in the wild at night: a bat horse in the woods, a nightmare over the badlands or
 * anywhere in the Nether. Tame and ride them like any horse. Ridden, a bat horse flies (hold jump to climb, let go to
 * glide down; the client steers, {@code MythicMountsClient}) and neither it nor its rider takes falls; a nightmare and its
 * rider shrug off fire and lava. Untamed ones fade with the dawn (or after a while in the Nether) if no one is near.
 */
public final class MythicMounts {
	/**
	 * How often each player's surroundings are weighed (ticks), and the chance one turns up then: (0.5.57) exceedingly
	 * rare, about once in 50 hours a player spends where they can turn up (only nights in the wild, or the Nether), and
	 * never while another wild one is out anywhere.
	 */
	static final int CHECK_TICKS = 600;
	static final float CHANCE = 1.0F / 6000.0F;
	/** How long the fire-proofing lasts on each refresh (ticks). */
	static final int FIRE_TICKS = 60;
	/** An untamed one in the Nether is gone after this (ticks), if no one is near. */
	static final int NETHER_STAY_TICKS = 12000;
	static final double LEAVE_RANGE = 64.0;

	/** Wild ones out now, and when (game time) a Nether one may leave. */
	private record Wild(AbstractHorse horse, long until) {
	}

	private static final List<Wild> WILD = new ArrayList<>();
	/** Bat horses set flying (no gravity) for their riders. */
	private static final Set<AbstractHorse> FLYING = new HashSet<>();

	private MythicMounts() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MythicMounts::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WILD.clear();
			FLYING.clear();
		});
	}

	public static boolean is(@Nullable AbstractHorse horse, Breed breed) {
		Equine e = horse == null ? null : Equestrian.peek(horse);
		return e != null && e.breed() == breed;
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		Set<AbstractHorse> flying = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.getVehicle() instanceof AbstractHorse horse)) {
				continue;
			}
			if (is(horse, Breed.BAT_HORSE)) {
				flying.add(horse);
				horse.setNoGravity(true);
				horse.resetFallDistance();
				player.resetFallDistance();
			} else if (is(horse, Breed.NIGHTMARE) && tick % 20 == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_TICKS, 0, true, false));
			}
		}
		for (AbstractHorse horse : FLYING) {
			if (!flying.contains(horse) && !horse.isRemoved()) {
				horse.setNoGravity(false);
			}
		}
		FLYING.clear();
		FLYING.addAll(flying);
		if (tick % 20 == 0) {
			fireproof(server);
		}
		if (tick % CHECK_TICKS == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (WILD.isEmpty() && player.level() instanceof ServerLevel level && level.getRandom().nextFloat() < CHANCE) {
					find(server, level, player);
				}
			}
			leave(server);
		}
	}

	/** Nightmares near players (wild, tamed, ridden) are fire-proof. */
	private static void fireproof(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (AbstractHorse horse : player.level().getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(48.0),
				h -> is(h, Breed.NIGHTMARE))) {
				horse.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_TICKS, 0, true, false));
			}
		}
	}

	/** One turns up near {@code player}, if they're somewhere one would. */
	private static void find(MinecraftServer server, ServerLevel level, ServerPlayer player) {
		boolean nether = level.dimension() == Level.NETHER;
		if (!nether && (level != Factions.level(server) || !level.isDarkOutside() || !Encounters.inTheWild(server, level, player))) {
			return;
		}
		RandomSource random = level.getRandom();
		BlockPos spot = nether ? near(level, player, random) : Encounters.spot(level, player, random);
		if (spot == null) {
			return;
		}
		Holder<Biome> biome = level.getBiome(spot);
		Breed breed = nether || biome.is(BiomeTags.IS_BADLANDS) || biome.is(Biomes.DESERT) ? Breed.NIGHTMARE
			: biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) ? Breed.BAT_HORSE : null;
		if (breed == null) {
			return;
		}
		AbstractHorse horse = Equestrian.create(level, breed, spot, EntitySpawnReason.EVENT);
		if (horse == null || !level.noCollision(horse)) {
			return;
		}
		level.addFreshEntity(horse);
		WILD.add(new Wild(horse, nether ? level.getGameTime() + NETHER_STAY_TICKS : Long.MAX_VALUE));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mythic_mount." + breed.id()).withStyle(ChatFormatting.DARK_PURPLE));
	}

	/** A spot on solid ground in the Nether near the player (their own height, give or take). */
	private static @Nullable BlockPos near(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			BlockPos at = player.blockPosition().offset(random.nextInt(33) - 16, random.nextInt(9) - 4, random.nextInt(33) - 16);
			if (level.isLoaded(at) && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
				&& level.getBlockState(at.above(2)).isAir() && level.getBlockState(at.below()).isSolid() && player.blockPosition().distSqr(at) > 64.0) {
				return at;
			}
		}
		return null;
	}

	/** Untamed ones nobody is near go: overworld ones at dawn, Nether ones in time. */
	private static void leave(MinecraftServer server) {
		for (Iterator<Wild> it = WILD.iterator(); it.hasNext(); ) {
			Wild w = it.next();
			AbstractHorse horse = w.horse();
			if (horse.isRemoved() || horse.isTamed()) {
				it.remove();
				continue;
			}
			Level level = horse.level();
			boolean due = level.dimension() == Level.NETHER ? level.getGameTime() > w.until() : !level.isDarkOutside();
			if (due && level.getNearestPlayer(horse, LEAVE_RANGE) == null) {
				horse.discard();
				it.remove();
			}
		}
	}
}
