package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.Iterator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.21) Gremlin's Ultimate, Swarm: the hunched imp grown from 0.3x to 0.45x (still under a block, so it fits one-block gaps), a
 * plated brow with a crest of small horns, plated knuckles, a barbed tail curled up over its back and green seams glowing along
 * the plates, with a health bar of its own on top of Gremlin's. Its model is {@code UltimateSwarm}.
 * <ul>
 * <li>R, Pounce: the huge soft-landing jump; landing on or beside a creature bites it.</li>
 * <li>G, Haywire: every lever, button, door, trapdoor and gate near the block you aim at flips at once, from far off.</li>
 * <li>V, Leech Bite: a deeper bite that steals more food, heals more and leaves the bitten hungry for longer.</li>
 * <li>B (hold), Scuttle: a quicker climb up walls that carries on along ceilings.</li>
 * <li>C, Swarm: you split into glowing gremlins that bite what you bite; C again swaps you with the farthest (nearest, sneaking).</li>
 * </ul>
 */
public final class Swarm extends XenoUltimate {
	// ---- tuning ----
	/** Pounce: cooldown, the jump's up and out, how close to where it lands a creature must be to be bitten, the bite's damage. */
	public static final int POUNCE_COOLDOWN = 30;
	public static final double POUNCE_UP = 1.1, POUNCE_OUT = 0.6, POUNCE_REACH = 2.0;
	public static final float POUNCE_DAMAGE = 5.0F;
	/** Haywire: cooldown, how far away the block can be aimed at, how far from it things flip (blocks), the most it flips at once. */
	public static final int HAYWIRE_COOLDOWN = 80, HAYWIRE_RADIUS = 4, HAYWIRE_MAX = 96;
	public static final double HAYWIRE_REACH = 24.0;
	/** Leech Bite: cooldown, reach, damage, the food it steals from a player, the Hunger it gives (ticks), what it heals you, how near a gremlin must be to join in. */
	public static final int LEECH_COOLDOWN = 60, LEECH_FOOD = 6, LEECH_HUNGER_TICKS = 300;
	public static final double LEECH_REACH = 2.5, LEECH_CLONE_RANGE = 4.0;
	public static final float LEECH_DAMAGE = 5.0F, LEECH_HEAL = 4.0F;
	/** Scuttle: how fast it climbs (blocks a tick), how hard a ceiling holds it up, how fast it crawls along one. */
	public static final double SCUTTLE_SPEED = 0.3, CEILING_GRIP = 0.08, CEILING_CRAWL = 0.12;
	/** Swarm: how many gremlins, C's cooldown for splitting again once they are all gone, the cooldown between swaps, how closely they follow and how fast. */
	public static final int CLONES = 5, SPLIT_COOLDOWN = 400, SWAP_COOLDOWN = 20;
	public static final double FOLLOW = 3.0, FOLLOW_SPEED = 1.4;
	/** How long the clones glow (ticks). */
	public static final int GLOW_TICKS = 20 * 60 * 10;

	/** (0.20.21) Set on a clone once it is in the world: one that loads back from a save carries it, and has no owner left, so it is removed. */
	private static final java.util.Set<java.util.UUID> AIRBORNE = new java.util.HashSet<>();
	private static final String CLONE_TAG = "jaymospowers_swarm_clone";

	static {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Silverfish && entity.entityTags().contains(CLONE_TAG)) {
				level.getServer().execute(entity::discard);
			}
		});
	}

	/** (0.20.21) Forces the class to load at mod init so the clone clean-up on entity load is registered before any chunk loads. */
	public static void init() {
	}

	public Swarm(XenoForm base) {
		super(base, 0.45F, 10.0F, 3.0, 0.4, 0.0, 0.0);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.SLIME_BLOCK, Items.REDSTONE_LAMP, Items.FERMENTED_SPIDER_EYE, Items.COBWEB, Items.INFESTED_STONE};
	}

	/** The change-in film zooms on these in turn (model part names): the knuckle plates, the barbed tail, the ears, the horned brow's head. */
	@Override
	public String[] morphParts() {
		return new String[] {"right_fist+left_fist", "tail", "right_ear", "head"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.climb = false;
		live.spring = 0;
		live.perched = false;
		ServerLevel level = player.level();
		burst(level, ParticleTypes.POOF, player.position().add(0.0, 0.3, 0.0), 14, 0.4, 0.04);
		sound(player, SoundEvents.SILVERFISH_AMBIENT, 1.5F, 1.9F);
		sound(player, SoundEvents.ANVIL_PLACE, 0.4F, 1.8F);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.climb = false;
		live.spring = 0;
		AIRBORNE.remove(player.getUUID());
		live.perched = false;
		player.removeAttached(XenoformState.CEILING);
		player.removeAttached(XenoformState.CLIMB);
		dismiss(player.level(), live.minions);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_4) {
			live.climb = down;
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && (player.onGround() || player.horizontalCollision)) {
					Vec3 out = player.getLookAngle().horizontal();
					out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(POUNCE_OUT);
					player.setDeltaMovement(out.x, POUNCE_UP, out.z);
					player.syncVelocity = true;
					live.spring = 1;
					AIRBORNE.remove(player.getUUID());
					sound(player, SoundEvents.SLIME_JUMP_SMALL, 1.0F, 1.5F);
					burst(player.level(), ParticleTypes.POOF, player.position(), 8, 0.25, 0.02);
					cooldown(player, live, R, POUNCE_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && haywire(player)) {
					cooldown(player, live, G, HAYWIRE_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && leechBite(player, live)) {
					cooldown(player, live, V, LEECH_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: split off the swarm if there is none; else swap places with the gremlin farthest off (nearest, sneaking). */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		prune(level, live);
		if (live.minions.isEmpty()) {
			if (ready(player, live, C) && split(player, live)) {
				cooldown(player, live, C, SPLIT_COOLDOWN);
			}
			return;
		}
		if (!ultimateReady(player, live)) {
			return;
		}
		Mob pick = null;
		double best = player.isShiftKeyDown() ? Double.MAX_VALUE : -1.0;
		for (int id : live.minions) {
			if (level.getEntity(id) instanceof Mob clone && clone.isAlive()) {
				double d = clone.distanceToSqr(player);
				if (player.isShiftKeyDown() ? d < best : d > best) {
					best = d;
					pick = clone;
				}
			}
		}
		if (pick == null) {
			return;
		}
		Vec3 mine = player.position();
		Vec3 theirs = pick.position();
		player.teleportTo(level, theirs.x, theirs.y, theirs.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		pick.teleportTo(mine.x, mine.y, mine.z);
		player.resetFallDistance();
		burst(level, ParticleTypes.POOF, mine.add(0.0, 0.3, 0.0), 8, 0.3, 0.03);
		burst(level, ParticleTypes.POOF, theirs.add(0.0, 0.3, 0.0), 8, 0.3, 0.03);
		sound(player, SoundEvents.SILVERFISH_STEP, 1.0F, 1.8F);
		ultimateCooldown(player, live, SWAP_COOLDOWN);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		if (live.spring > 0) {
			live.spring++;
			if (!player.onGround()) {
				AIRBORNE.add(player.getUUID()); // (0.20.21) the landing only counts once the server has seen the jump
			}
			if (live.spring > 3 && (player.isInWater() || player.onGround() && AIRBORNE.contains(player.getUUID()))) {
				live.spring = 0;
				AIRBORNE.remove(player.getUUID());
				if (player.onGround()) {
					pounce(player);
				}
			} else if (live.spring > 200) {
				live.spring = 0;
				AIRBORNE.remove(player.getUUID());
			}
		}
		scuttle(player, live);
		followTick(player, live);
	}

	/** Light enough to land from anything. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.climb || live.perched;
	}

	// ---- R: Pounce ----

	/** The jump is over: the creature nearest where it landed, if any is on or beside it, is bitten. */
	private static void pounce(ServerPlayer player) {
		LivingEntity prey = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity near : around(player, POUNCE_REACH + player.getBbWidth())) {
			if (near.entityTags().contains(XenoformState.MINION_TAG)) {
				continue;
			}
			double d = near.distanceToSqr(player);
			if (d < best) {
				best = d;
				prey = near;
			}
		}
		if (prey == null) {
			return;
		}
		ServerLevel level = player.level();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		prey.hurtServer(level, player.damageSources().playerAttack(player), POUNCE_DAMAGE);
		level.sendParticles(ParticleTypes.CRIT, prey.getX(), prey.getY() + prey.getBbHeight() * 0.5, prey.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
		sound(player, SoundEvents.SILVERFISH_HURT, 0.8F, 1.6F);
		sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 0.8F, 1.4F);
	}

	// ---- G: Haywire ----

	/** Every switch within {@link #HAYWIRE_RADIUS} of the block you look at flips at once. False when nothing was aimed at or nothing flipped. */
	private static boolean haywire(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(HAYWIRE_REACH)), ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos centre = hit.getBlockPos();
		int r = HAYWIRE_RADIUS;
		int flipped = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			if (flipped >= HAYWIRE_MAX) {
				break;
			}
			if (pos.distSqr(centre) > r * r || !level.mayInteract(player, pos)) {
				continue;
			}
			if (flip(player, level, pos.immutable(), flipped < 6)) {
				flipped++;
			}
		}
		if (flipped > 0) {
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 24, r * 0.4, r * 0.3, r * 0.4, 0.05);
			level.playSound(null, centre, SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.7F);
		}
		return flipped > 0;
	}

	/** One switch at {@code pos}, thrown the other way, if it is one. */
	private static boolean flip(ServerPlayer player, ServerLevel level, BlockPos pos, boolean loud) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		if (block instanceof LeverBlock lever) {
			lever.pull(state, level, pos, null);
		} else if (block instanceof ButtonBlock button) {
			button.press(state, level, pos, null);
		} else if (block instanceof DoorBlock door) {
			if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
				return false; // the two halves open as one: only the lower is thrown
			}
			door.setOpen(player, level, state, pos, !state.getValue(DoorBlock.OPEN));
		} else if (block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
			level.setBlock(pos, state.cycle(BlockStateProperties.OPEN), Block.UPDATE_ALL);
			if (loud) {
				level.playSound(null, pos, state.getValue(BlockStateProperties.OPEN) ? SoundEvents.WOODEN_TRAPDOOR_CLOSE : SoundEvents.WOODEN_TRAPDOOR_OPEN,
					SoundSource.BLOCKS, 1.0F, 1.0F);
			}
		} else {
			return false;
		}
		if (loud) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
		}
		return true;
	}

	// ---- V: Leech Bite ----

	private static boolean leechBite(ServerPlayer player, XenoformPower.Live live) {
		LivingEntity target = aimed(player, LEECH_REACH, e -> !e.entityTags().contains(XenoformState.MINION_TAG));
		if (target == null) {
			return false;
		}
		ServerLevel level = player.level();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (!target.hurtServer(level, player.damageSources().playerAttack(player), LEECH_DAMAGE)) {
			return false; // (0.20.21) a refused hit (pvp off, blocked, i-frames, invulnerable): no food, no Hunger, no heal, no clone bites
		}
		int stolen = 0;
		if (target instanceof Player bitten) {
			int had = bitten.getFoodData().getFoodLevel();
			stolen = Math.min(had, LEECH_FOOD);
			bitten.getFoodData().setFoodLevel(had - stolen);
		} else {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LEECH_HUNGER_TICKS / 2, 0));
		}
		target.addEffect(new MobEffectInstance(MobEffects.HUNGER, LEECH_HUNGER_TICKS, 1));
		// the swarm bites too
		for (int id : live.minions) {
			if (level.getEntity(id) instanceof Mob clone && clone.isAlive() && clone.distanceToSqr(target) < LEECH_CLONE_RANGE * LEECH_CLONE_RANGE && !(target instanceof Player)) {
				clone.doHurtTarget(level, target);
			}
		}
		player.heal(LEECH_HEAL);
		player.getFoodData().eat(Math.max(2, stolen / 2), 0.3F);
		level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 10, 0.25, 0.25, 0.25, 0.0);
		sound(player, SoundEvents.GENERIC_EAT.value(), 1.0F, 1.4F);
		sound(player, SoundEvents.SILVERFISH_HURT, 0.7F, 1.6F);
		return true;
	}

	// ---- B: Scuttle ----

	/** The side of the wall it is pressed against, the one nearest where it faces; null if none. */
	private static @Nullable Direction wall(ServerPlayer player) {
		AABB box = player.getBoundingBox();
		Direction best = null;
		float bestTurn = Float.MAX_VALUE;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (player.level().noCollision(player, box.move(side.getStepX() * 0.05, 0.0, side.getStepZ() * 0.05))) {
				continue;
			}
			float turn = Math.abs(net.minecraft.util.Mth.wrapDegrees(side.toYRot() - player.getYRot()));
			if (turn < bestTurn) {
				bestTurn = turn;
				best = side;
			}
		}
		return best;
	}

	/** A solid underside right over its head. */
	private static boolean ceiling(ServerPlayer player) {
		AABB box = player.getBoundingBox();
		BlockPos above = BlockPos.containing(player.getX(), box.maxY + 0.3, player.getZ());
		return player.level().getBlockState(above).isFaceSturdy(player.level(), above, Direction.DOWN);
	}

	/** B held: up a wall it walks into, and on along the ceiling it reaches (clinging upside down, drawn so by every client). */
	private static void scuttle(ServerPlayer player, XenoformPower.Live live) {
		Direction climbing = null;
		if (live.perched) {
			if (!live.climb || !ceiling(player) || player.isInWater()) {
				live.perched = false;
			} else {
				Vec3 m = player.getDeltaMovement();
				Vec3 crawl = m.horizontal().lengthSqr() > CEILING_CRAWL * CEILING_CRAWL ? m.horizontal().normalize().scale(CEILING_CRAWL) : m.horizontal();
				player.setDeltaMovement(crawl.x, CEILING_GRIP, crawl.z);
				player.syncVelocity = true;
				player.resetFallDistance();
			}
		} else if (live.climb && !player.onGround() && !player.isInWater() && ceiling(player)) {
			live.perched = true;
			player.setDeltaMovement(player.getDeltaMovement().x, CEILING_GRIP, player.getDeltaMovement().z);
			player.syncVelocity = true;
			sound(player, SoundEvents.SILVERFISH_STEP, 0.7F, 1.6F);
		} else if (live.climb && player.horizontalCollision && !player.onGround() && !player.isInWater()) {
			climbing = wall(player);
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(motion.x, SCUTTLE_SPEED, motion.z);
			player.syncVelocity = true;
			player.resetFallDistance();
			if (player.tickCount % 5 == 0) {
				sound(player, SoundEvents.SILVERFISH_STEP, 0.6F, 1.6F);
			}
		}
		// every client draws it on the wall while it climbs one
		Integer side = climbing != null ? climbing.get2DDataValue() : null;
		Integer was = player.getAttached(XenoformState.CLIMB);
		if (side != null && !side.equals(was)) {
			player.setAttached(XenoformState.CLIMB, side);
		} else if (side == null && was != null) {
			player.removeAttached(XenoformState.CLIMB);
		}
		if (live.perched != Boolean.TRUE.equals(player.getAttached(XenoformState.CEILING))) {
			if (live.perched) {
				player.setAttached(XenoformState.CEILING, true);
			} else {
				player.removeAttached(XenoformState.CEILING);
			}
		}
	}

	// ---- C: Swarm ----

	/** Splits off {@link #CLONES} glowing gremlins round you. False if none could be made. */
	/** (0.20.21) A clone's bite never lands on a player (their Player target goals cannot be stripped from outside, so the hit is refused). */
	public static boolean bitesPlayer(net.minecraft.world.damagesource.DamageSource source, net.minecraft.world.entity.Entity victim) {
		return victim instanceof Player && source.getEntity() instanceof Silverfish clone && clone.entityTags().contains(CLONE_TAG);
	}

	private static boolean split(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		for (int i = 0; i < CLONES; i++) {
			double a = i / (double)CLONES * Math.PI * 2.0;
			Vec3 at = player.position().add(Math.cos(a) * 1.2, 0.1, Math.sin(a) * 1.2);
			Silverfish clone = summon(level, net.minecraft.world.entity.EntityTypes.SILVERFISH, at, player.getYRot());
			if (clone == null) {
				continue;
			}
			clone.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
			clone.setCustomName(player.getDisplayName());
			clone.setCustomNameVisible(false);
			clone.addTag(CLONE_TAG);
			clone.skipDropExperience(); // (0.20.21) killing your own clones is no XP farm
			// (0.20.21) a clone never hides in stone or wakes infested blocks
			clone.removeAllGoals(goal -> goal.getClass().getSimpleName().startsWith("SilverfishMerge") || goal.getClass().getSimpleName().startsWith("SilverfishWakeUp"));
			live.minions.add(clone.getId());
		}
		if (live.minions.isEmpty()) {
			return false;
		}
		burst(level, ParticleTypes.POOF, player.position().add(0.0, 0.3, 0.0), 24, 0.6, 0.05);
		sound(player, SoundEvents.SILVERFISH_AMBIENT, 1.5F, 1.2F);
		sound(player, SoundEvents.SILVERFISH_AMBIENT, 1.5F, 1.6F);
		return true;
	}

	/** (0.20.21) A clone left in another dimension (a portal) is removed there; one in an unloaded chunk is removed when it loads. */
	private static void leftBehind(ServerLevel here, int id) {
		for (ServerLevel other : here.getServer().getAllLevels()) {
			if (other != here && other.getEntity(id) instanceof Silverfish clone && clone.entityTags().contains(CLONE_TAG)) {
				clone.discard();
			}
		}
	}

	/** Forgets the gremlins that are gone. */
	private static void prune(ServerLevel level, XenoformPower.Live live) {
		for (Iterator<Integer> it = live.minions.iterator(); it.hasNext();) {
			int id = it.next();
			if (!(level.getEntity(id) instanceof Mob clone) || !clone.isAlive()) {
				leftBehind(level, id);
				it.remove();
			}
		}
	}

	/** The gremlins follow you and never go for a player or each other. */
	private static void followTick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		for (Iterator<Integer> it = live.minions.iterator(); it.hasNext();) {
			int id = it.next();
			if (!(level.getEntity(id) instanceof Mob clone) || !clone.isAlive()) {
				leftBehind(level, id);
				it.remove();
				continue;
			}
			LivingEntity target = clone.getTarget();
			if (target instanceof Player || target != null && target.entityTags().contains(XenoformState.MINION_TAG)) {
				clone.setTarget(null);
			}
			if (clone.tickCount % 10 == 0 && clone.getTarget() == null && clone.distanceToSqr(player) > FOLLOW * FOLLOW) {
				clone.getNavigation().moveTo(player, FOLLOW_SPEED);
			}
		}
	}
}
