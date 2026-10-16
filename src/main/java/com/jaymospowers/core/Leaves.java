package com.jaymospowers.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;

/**
 * Realistic leaves, for everyone (our own take on The Realistic Leaves Mod, which only exists for 1.7.10 on PML;
 * nothing of it is copied): leaves are soft. Players, mobs, animals, items and vehicles push into and through them
 * instead of standing on them, and they drag on you while you're inside, so you sink slowly through a canopy, wade
 * through hedges, and a fall into a tree is cushioned. Hold Jump inside leaves to climb up through them. Arrows and
 * other projectiles still hit leaves, and falling sand and gravel still land on them.
 *
 * <p>A hit hard enough to hurt (a fall past your safe distance, or flying into a tree fast) crashes through instead:
 * every leaves block you hit that tick snaps and falls apart in a burst of leaf debris, and the fall is still
 * cushioned, so a long drop smashes through the top of a canopy and slows inside it.
 *
 * <p>Mobs path through leaves as through open air ({@code LeavesPathMixin}): they wade through hedges and walk out of
 * bushes and low canopies instead of being stuck in them, and never try to walk on top of leaves. Spiders cling
 * inside leaves instead of sinking, and lurk in them ({@link LeafSpiders}).
 *
 * <p>Both sides run the same rules (the collision shape and the drag), so movement stays in step; the rustle and the
 * shaken-loose leaves are drawn on each client ({@code client/LeafRustle}).
 */
public final class Leaves {
	/** Inside leaves, sideways speed is kept at this share each tick: like wading. */
	public static final double SIDEWAYS_DRAG = 0.72;
	/** Falling speed kept each tick inside leaves: you settle down through a canopy at about two blocks a second. */
	public static final double FALL_DRAG = 0.55;
	/** Rising speed kept each tick (a jump inside leaves doesn't go far). */
	public static final double RISE_DRAG = 0.8;
	/** Holding Jump inside leaves climbs at this speed (blocks a tick). */
	public static final double CLIMB_SPEED = 0.13;
	/** Share of the fall so far kept each tick inside leaves, so a drop through branches hurts much less. */
	public static final double CUSHION = 0.5;
	/** Sideways speed (blocks a tick) that crashes through leaves: past a galloping horse, short of flight. */
	public static final double CRASH_SPEED = 0.6;
	/** Leaf debris thrown up by each leaves block that snaps. */
	public static final int CRASH_DEBRIS = 24;

	/** Something an entity remembers: the last tick leaves dragged on it (one drag a tick, however many it touches). */
	public interface Dragged {
		int jaymospowers$leafTick();

		void jaymospowers$leafTick(int tick);

		/** Whether it hit the leaves this tick hard enough to crash through them. */
		boolean jaymospowers$leafCrash();

		void jaymospowers$leafCrash(boolean crash);
	}

	/** Lets us read whether a living thing is holding Jump. */
	public interface Jumper {
		boolean jaymospowers$jumping();
	}

	private Leaves() {
	}

	/** Any leaves, modded ones too (checked on every collision, so a class check rather than a tag lookup). */
	public static boolean is(BlockState state) {
		return state.getBlock() instanceof LeavesBlock;
	}

	/** Whether this collision is an entity that goes through leaves (not a projectile, falling block, or no entity at all). */
	public static boolean passesThrough(CollisionContext context) {
		if (!(context instanceof EntityCollisionContext entityContext)) {
			return false;
		}
		Entity entity = entityContext.getEntity();
		return entity != null && passesThrough(entity);
	}

	public static boolean passesThrough(Entity entity) {
		return !(entity instanceof Projectile) && !(entity instanceof FallingBlockEntity) && !entity.noPhysics;
	}

	/**
	 * An entity is inside the leaves at {@code pos} this tick: crash through them if it hit hard, then drag on it once,
	 * cushion its fall, and let it climb if it jumps.
	 */
	public static void inside(Level level, BlockPos pos, Entity entity) {
		if (!passesThrough(entity) || entity.isSpectator() || entity instanceof Player player && player.getAbilities().flying) {
			return;
		}
		Dragged dragged = (Dragged)entity;
		if (dragged.jaymospowers$leafTick() != entity.tickCount) {
			dragged.jaymospowers$leafTick(entity.tickCount);
			// judged once a tick, before this tick's drag and cushion take the speed off
			dragged.jaymospowers$leafCrash(crashes(entity));
			drag(entity);
		}
		if (dragged.jaymospowers$leafCrash() && level instanceof ServerLevel serverLevel) {
			crash(serverLevel, pos, entity);
		}
	}

	/** Whether this hit would hurt: a fall past its safe distance, or flying in fast. */
	private static boolean crashes(Entity entity) {
		if (!(entity instanceof LivingEntity living) || living instanceof Spider) {
			return false;
		}
		if (living.fallDistance > living.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)) {
			return true;
		}
		// (a server player's own speed lives on its client: this is what the server last saw it move)
		return living.getKnownMovement().horizontalDistance() > CRASH_SPEED;
	}

	private static void drag(Entity entity) {
		Vec3 v = entity.getDeltaMovement();
		double y = v.y * (v.y < 0.0 ? FALL_DRAG : RISE_DRAG);
		if (entity instanceof LivingEntity && ((Jumper)entity).jaymospowers$jumping()) {
			y = Math.max(y, CLIMB_SPEED);
		} else if (entity instanceof Spider spider && spider.getTarget() == null && y < 0.0) {
			// a spider clings in the leaves instead of sinking through them (it drops when it has prey below)
			y = 0.0;
		}
		entity.setDeltaMovement(v.x * SIDEWAYS_DRAG, y, v.z * SIDEWAYS_DRAG);
		entity.fallDistance *= CUSHION;
	}

	/** The leaves snap: broken (dropping what leaves drop), with a crack of twigs and a burst of leaf debris. */
	private static void crash(ServerLevel level, BlockPos pos, Entity entity) {
		BlockState state = level.getBlockState(pos);
		if (!is(state)) {
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
			CRASH_DEBRIS, 0.4, 0.4, 0.4, 0.15);
		level.playSound(null, pos, SoundEvents.BAMBOO_WOOD_BREAK, SoundSource.BLOCKS, 0.9F, 0.7F + level.getRandom().nextFloat() * 0.3F);
		level.destroyBlock(pos, true, entity);
	}
}
