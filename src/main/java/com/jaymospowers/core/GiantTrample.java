package com.jaymospowers.core;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.39) Andy: "apes should also knock trees down with their strides, as well as towerback, all giants should get this
 * damaging effect". A giant's steps (the Great Ape's, Towerback's) knock over the trees round its feet, and walking into a
 * tree pushes it down. With Jaymo's Kingdoms the tree topples whole, away from the giant ({@code Trees.trample}, as the
 * Aether Giants'), and the falling wood crushes whatever it lands on; without it the trunk just breaks. A player giant
 * only fells where they may build; a mob giant only with mob griefing on.
 * <p>
 * Running into a tree is harder still: instead of toppling, the tree is knocked flying, whole from its roots, on ahead of
 * the giant and tumbling as it goes (Kingdoms' {@code Trees.uproot}, as a tornado's). One too big for the giant to send
 * flying ({@link GiantTrees#mostLogs}) topples as a walk would; without Kingdoms the broken trunk's logs fly on ahead.
 */
public final class GiantTrample {
	/** How far past its body a step reaches for trees (blocks, plus this per scale), and how high up its legs it looks for a trunk. */
	public static final double REACH = 1.0, REACH_PER_SCALE = 0.15;
	public static final int HEIGHT = 6;
	/** Walking into a tree: checked this often (ticks). */
	public static final int PUSH_EVERY = 5;
	/** Running into a tree: checked this often (ticks). */
	public static final int RAM_EVERY = 2;
	/**
	 * A giant counts as running at this speed over the ground (blocks a tick), or a player giant when sprinting: mobs
	 * hardly ever sprint, and a player's speed isn't known on the server.
	 */
	public static final double RAM_SPEED = 0.2;
	/** How far ahead of its body a run reaches for trees (blocks, plus this per scale). */
	public static final double RAM_REACH = 1.0, RAM_REACH_PER_SCALE = 0.15;
	/**
	 * A tree run into flies on at this share of the giant's speed, at least {@link #RAM_THROW_MIN}, with this lift (blocks a
	 * tick), both times the root of the giant's height in people, at most {@link #RAM_THROW_FASTEST}.
	 */
	public static final double RAM_THROW_SHARE = 2.0, RAM_THROW_MIN = 0.45, RAM_THROW_LIFT = 0.4, RAM_THROW_FASTEST = 2.0;
	/** A tree knocked flying turns end over end this fast (degrees a tick). */
	public static final float RAM_TUMBLE = 8.0F;
	/** Without Kingdoms: the logs of a trunk run into fly ahead at about this speed (blocks a tick). */
	public static final double RAM_LOG_SPEED = 0.4;
	/** Without Kingdoms: the most logs one broken trunk drops. */
	public static final int FALLBACK_LOGS = 24;

	private static @Nullable Method trample, uproot;
	private static boolean looked;

	private GiantTrample() {
	}

	/** Each tick of a giant: running into a tree throws it; walking into one pushes it over. */
	public static void tick(LivingEntity giant, long now) {
		if (now % RAM_EVERY == 0 && ram(giant)) {
			return;
		}
		if (now % PUSH_EVERY == 0 && giant.horizontalCollision) {
			step(giant);
		}
	}

	/**
	 * Running into a tree: the first standing trunk just ahead of its body, from its feet up {@link #HEIGHT} blocks, is
	 * thrown on ahead. True if one went.
	 */
	public static boolean ram(LivingEntity giant) {
		if (!(giant.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 motion = giant.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		double speed = motion.length();
		boolean running = giant instanceof ServerPlayer ? giant.isSprinting() : speed >= RAM_SPEED;
		if (!running || !mayFell(giant, level)) {
			return false;
		}
		// a sprinting player's motion isn't known here: its facing stands in for it
		Vec3 ahead = speed > 1.0E-3 && !(giant instanceof ServerPlayer)
			? motion.normalize() : Vec3.directionFromRotation(0.0F, giant.getYRot());
		double reach = RAM_REACH + RAM_REACH_PER_SCALE * giant.getScale();
		AABB body = giant.getBoundingBox();
		AABB box = body.move(ahead.scale(reach)).minmax(body.move(ahead.scale(body.getXsize() * 0.5)));
		int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
		int x1 = Mth.floor(box.maxX), z1 = Mth.floor(box.maxZ);
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y0 + HEIGHT, z1)) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.LOGS) || !standing(state) || giant instanceof ServerPlayer player && !level.mayInteract(player, pos)) {
				continue;
			}
			BlockPos log = pos.immutable();
			double size = Math.sqrt(Math.max(1.0, giant.getBbHeight() / 1.8));
			Vec3 v = ahead.scale(Math.max(RAM_THROW_MIN, speed * RAM_THROW_SHARE) * size).add(0.0, RAM_THROW_LIFT * size, 0.0);
			if (v.length() > RAM_THROW_FASTEST) {
				v = v.scale(RAM_THROW_FASTEST / v.length());
			}
			if (knockFlying(level, log, giant, v)) {
				level.playSound(null, log, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 2.0F, 0.5F);
				return true;
			}
			boolean kingdoms = kingdoms() != null;
			fell(level, log, giant);
			if (!kingdoms) {
				fling(level, log, ahead);
			}
			return true;
		}
		return false;
	}

	/** Kingdoms' {@code Trees.uproot}: the tree on {@code log} flies off whole at {@code v}. False if it can't (too big, not a living tree, no Kingdoms). */
	private static boolean knockFlying(ServerLevel level, BlockPos log, LivingEntity giant, Vec3 v) {
		kingdoms();
		if (uproot == null) {
			return false;
		}
		BlockPos foot = log;
		while (foot.getY() > level.getMinY() && level.getBlockState(foot.below()).is(BlockTags.LOGS)) {
			foot = foot.below();
		}
		try {
			return uproot.invoke(null, level, foot, GiantTrees.mostLogs(giant), v, (float)Math.atan2(v.z, v.x), RAM_TUMBLE) != null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			uproot = null;
			return false;
		}
	}

	/** Without Kingdoms: the logs a broken trunk dropped fly on ahead of the giant that ran into it. */
	private static void fling(ServerLevel level, BlockPos log, Vec3 ahead) {
		AABB trunk = new AABB(log).expandTowards(0.0, FALLBACK_LOGS, 0.0).inflate(1.0);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, trunk, e -> e.getItem().is(net.minecraft.tags.ItemTags.LOGS))) {
			double push = RAM_LOG_SPEED * (0.75 + level.getRandom().nextDouble() * 0.5);
			item.setDeltaMovement(ahead.scale(push).add(0.0, 0.2 + level.getRandom().nextDouble() * 0.2, 0.0));
		}
	}

	/** One of its steps: the trees round its feet go down. */
	public static void step(LivingEntity giant) {
		if (!(giant.level() instanceof ServerLevel level) || !mayFell(giant, level)) {
			return;
		}
		double reach = REACH + REACH_PER_SCALE * giant.getScale();
		AABB box = giant.getBoundingBox().inflate(reach, 0.0, reach);
		int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
		int x1 = Mth.floor(box.maxX), z1 = Mth.floor(box.maxZ);
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y0 + HEIGHT, z1)) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.LOGS) || !standing(state) || giant instanceof ServerPlayer player && !level.mayInteract(player, pos)) {
				continue;
			}
			fell(level, pos.immutable(), giant);
		}
	}

	static boolean mayFell(LivingEntity giant, ServerLevel level) {
		if (giant instanceof ServerPlayer player) {
			return player.mayBuild() && !player.isSpectator();
		}
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/** An upright log (a fallen trunk lies on its side and stays where it is). */
	private static boolean standing(BlockState state) {
		return !state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
	}

	/** The tree standing on {@code log} comes down: Kingdoms topples it whole; otherwise its trunk breaks. */
	private static void fell(ServerLevel level, BlockPos log, LivingEntity giant) {
		Method method = kingdoms();
		if (method != null) {
			try {
				method.invoke(null, level, log, giant);
				return;
			} catch (ReflectiveOperationException | RuntimeException e) {
				trample = null;
			}
		}
		// no Kingdoms: the trunk snaps where it stood, log by log up
		BlockPos.MutableBlockPos at = log.mutable();
		for (int i = 0; i < FALLBACK_LOGS && level.getBlockState(at).is(BlockTags.LOGS); i++, at.move(Direction.UP)) {
			BlockState state = level.getBlockState(at);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.4, 0.4, 0.4,
				0.1);
			level.destroyBlock(at, true, giant);
		}
		level.playSound(null, log, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.5F, 0.6F);
	}

	/** Kingdoms' {@code Trees.trample(level, log, giant)}, if Kingdoms is loaded. */
	private static @Nullable Method kingdoms() {
		if (!looked) {
			looked = true;
			try {
				trample = Class.forName("com.jaymoskingdoms.Trees").getMethod("trample", ServerLevel.class, BlockPos.class,
					net.minecraft.world.entity.Entity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				trample = null;
			}
			try {
				uproot = Class.forName("com.jaymoskingdoms.Trees").getMethod("uproot", ServerLevel.class, BlockPos.class, int.class,
					Vec3.class, float.class, float.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				uproot = null;
			}
		}
		return trample;
	}
}
