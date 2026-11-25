package com.jaymoskingdoms;

import com.jaymoskingdoms.glowwood.creatures.GlowCreatures;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The little life of a Sylvan haven's glow-woods: sporelings, trufflers and (at night) glowmoths gather round its giant
 * fungi ({@link GlowWoodHavens}). Stewardship: a haven that is looked after (not dark) keeps more of them the more giants
 * it has; a haven gone dark draws none, and the ones already there stay or wander as they like. Nothing is taken from
 * anyone who leaves it alone.
 */
public final class GlowHavenLife {
	/** How often (ticks) a haven is looked at, and how near a player must be (blocks) for it to be worth spawning in. */
	static final long CHECK_TICKS = 400L;
	static final double PLAYER_RANGE = 72.0;
	/** What a haven's woods hold: the area counted round its seat (blocks), and the most of each a looked-after haven keeps. */
	static final double HOLD_RANGE = 56.0;
	static final int SPORELINGS_MOST = 6, TRUFFLERS_MOST = 2, MOTHS_MOST = 6;
	/** A giant at this many keeps one more sporeling, one more truffler, one more moth. */
	static final int PER_SPORELING = 2, PER_TRUFFLER = 6;
	/** How far from a giant (blocks) one gathers, and how many places are tried. */
	static final int GATHER = 9, TRIES = 10;

	private GlowHavenLife() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GlowHavenLife::tick);
	}

	/** Whether a Sylvan (or mixed) haven's seat lies within {@code radius} blocks of {@code pos}, looked after or not. */
	public static boolean sylvanNear(ServerLevel level, BlockPos pos, double radius) {
		MinecraftServer server = level.getServer();
		if (level != Factions.level(server)) {
			return false;
		}
		for (Faction f : Factions.all(server)) {
			if (f.type() != FactionType.BARBARIAN && sylvan(server, f) && f.seat().distSqr(pos) <= radius * radius) {
				return true;
			}
		}
		return false;
	}

	/** The shade a sporeling needs to spread anything: a crown, a roof or a cap overhead. */
	public static boolean shaded(ServerLevel level, BlockPos pos) {
		return GlowWoodHavens.shaded(level, pos);
	}

	private static boolean sylvan(MinecraftServer server, Faction f) {
		String haven = Havens.of(server, f);
		return haven.equals(Heritage.Kind.SYLVAN.key()) || haven.equals(Havens.MIXED);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CHECK_TICKS != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.type() == FactionType.BARBARIAN || f.fallen() || !sylvan(server, f) || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			GlowWoodHavens.Glow glow = GlowWoodHavens.glowOf(server, f.id());
			if (glow.dark() >= 0L || glow.giants().isEmpty() || level.getNearestPlayer(f.seat().getX(), f.seat().getY(), f.seat().getZ(), PLAYER_RANGE, false) == null) {
				continue;
			}
			int giants = glow.giants().size();
			gather(level, f, glow, GlowCreatures.SPORELING, Math.min(SPORELINGS_MOST, 1 + giants / PER_SPORELING));
			gather(level, f, glow, GlowCreatures.TRUFFLER, Math.min(TRUFFLERS_MOST, 1 + giants / PER_TRUFFLER));
			if (level.isDarkOutside()) {
				gather(level, f, glow, GlowCreatures.GLOWMOTH, Math.min(MOTHS_MOST, giants));
			}
		}
	}

	/** One more {@code type} beside a giant of {@code f}'s, if the haven holds fewer than {@code most} of them. */
	private static <T extends Mob> void gather(ServerLevel level, Faction f, GlowWoodHavens.Glow glow, EntityType<T> type, int most) {
		BlockPos seat = f.seat();
		int held = level.getEntities(type, e -> e.distanceToSqr(seat.getX(), seat.getY(), seat.getZ()) <= HOLD_RANGE * HOLD_RANGE).size();
		if (held >= most) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int i = 0; i < TRIES; i++) {
			BlockPos giant = glow.giants().get(random.nextInt(glow.giants().size())).pos();
			BlockPos at = spot(level, giant, random, type == GlowCreatures.GLOWMOTH);
			if (at == null) {
				continue;
			}
			T mob = type.create(level, EntitySpawnReason.NATURAL);
			if (mob == null) {
				return;
			}
			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, null);
			level.addFreshEntity(mob);
			return;
		}
	}

	/** A free spot within {@link #GATHER} blocks of {@code giant}: shaded ground, or (a moth) the air a few blocks over it. */
	private static @Nullable BlockPos spot(ServerLevel level, BlockPos giant, RandomSource random, boolean air) {
		BlockPos column = giant.offset(random.nextInt(GATHER * 2 + 1) - GATHER, 0, random.nextInt(GATHER * 2 + 1) - GATHER);
		for (int dy = 6; dy >= -4; dy--) {
			BlockPos pos = column.above(dy);
			BlockState below = level.getBlockState(pos.below());
			if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above()) && below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
				if (!air && !(below.is(BlockTags.DIRT) || below.is(com.jaymoskingdoms.glowwood.GlowWoods.FUNGUS_SOIL))) {
					return null;
				}
				if (!shaded(level, pos)) {
					return null;
				}
				return air ? pos.above(2 + random.nextInt(3)) : pos;
			}
		}
		return null;
	}

	/** Every kind a haven gathers (for tools that want the list). */
	public static List<EntityType<? extends Mob>> kinds() {
		return List.of(GlowCreatures.SPORELING, GlowCreatures.TRUFFLER, GlowCreatures.GLOWMOTH);
	}
}
