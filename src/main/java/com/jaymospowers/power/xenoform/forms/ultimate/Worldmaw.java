package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.forms.Gorger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.21.37) Gorger's Ultimate, Worldmaw: the same armadillo on four legs grown from 1.1x to 1.6x, its shell bands thickened with
 * stone plates and spikes, the snout's horn forked and the maw wider, ember light in the cracks between the bands, with a health bar
 * of its own on top of Gorger's. Its model is {@code UltimateWorldmaw}. The gut ({@code live.gorge}) holds {@link #GUT_MAX}, and
 * it remembers the last {@link #FLYING_MAX} kinds of block it ate.
 * <ul>
 * <li>R, Deep Gulp: eat the block you look at and the eight round it on that face (Gulp's rules for each).</li>
 * <li>G, Rubble Cannon: the gut comes up as rubble in a long cone, and up to three of the blocks it ate fly out as falling blocks.</li>
 * <li>V, Quake Charge: a long run that gores every creature on the way; off a ledge it turns into a slam.</li>
 * <li>B (hold), Spiked Roll: curl into the spiked ball and roll at walking pace; what you roll into is hurt and bounces off.</li>
 * <li>C, Worldmaw: unhinge the jaw and inhale; blocks in the cone are gulped, creatures are dragged in and bitten, then the whole gut
 * comes out as one Gut Blast at double damage.</li>
 * </ul>
 * Everything respects {@link #spared} and pvp ({@code canHarmPlayer}); blocks are only eaten where the player may build and interact (so
 * never in adventure mode or a protected spot), the eaten blocks leave nothing behind to farm, and the flying blocks are never placed:
 * they burst on whatever they meet, and are discarded when the Ultimate ends, the player dies, changes dimension or quits, the chunk
 * unloads or the server stops.
 */
public final class Worldmaw extends XenoUltimate {
	// ---- tuning ----
	/** Deep Gulp: reach (blocks), cooldown, and how much the gut holds. */
	public static final double GULP_REACH = 6.0;
	public static final int GULP_COOLDOWN = 6, GUT_MAX = 24;
	/** Rubble Cannon: cooldown, cone length (blocks) and tightness (cosine), shove, damage a bite. */
	public static final int CANNON_COOLDOWN = 30;
	public static final double CANNON_REACH = 14.0, CANNON_CONE = 0.75, CANNON_PUSH = 1.9, CANNON_UP = 0.4;
	public static final float CANNON_PER_BITE = 3.0F;
	/** The blocks that fly out of the cannon: at most, speed (blocks/tick), the hit's damage and radius, the most ticks they fly. */
	public static final int FLYING_MAX = 3, FLYING_LIFE = 60;
	public static final double FLYING_SPEED = 1.6, FLYING_RADIUS = 1.8, FLYING_SPREAD = 0.08;
	public static final float FLYING_DAMAGE = 8.0F;
	/** Quake Charge: ticks it runs, cooldown, speed (blocks/tick), how near a creature must be to be gored (blocks), damage, the toss out and up. */
	public static final int QUAKE_TICKS = 32, QUAKE_COOLDOWN = 70;
	public static final double QUAKE_SPEED = 0.9, QUAKE_HIT_REACH = 1.8, QUAKE_TOSS = 1.3, QUAKE_TOSS_UP = 0.7;
	public static final float QUAKE_DAMAGE = 9.0F;
	/** The ledge slam: radius, base damage, damage per block fallen, the most. */
	public static final double SLAM_RADIUS = 6.0;
	public static final float SLAM_DAMAGE = 5.0F, SLAM_PER_BLOCK = 1.0F, SLAM_MAX = 24.0F;
	/** Spiked Roll: damage, shove out and up, ticks before the same creature can be hit again, the speed (blocks/tick, squared) it takes to hit, the reach beyond its own width. */
	public static final float ROLL_DAMAGE = 4.0F;
	public static final double ROLL_BOUNCE = 0.9, ROLL_BOUNCE_UP = 0.4, ROLL_MIN_SPEED_SQ = 0.0016, ROLL_REACH = 1.0;
	public static final int ROLL_HIT_EVERY = 10;
	/** Worldmaw: cooldown (30 s), ticks it inhales (2 s), ticks between mouthfuls of blocks and the most blocks in one, the cone's width at its far end and depth (blocks). */
	public static final int MAW_COOLDOWN = 600, MAW_TICKS = 40, MAW_BEAT = 4, MAW_BLOCKS = 8;
	public static final double MAW_WIDTH = 5.0, MAW_DEPTH = 6.0;
	/** The pull on a creature (blocks/tick), ticks between bites (half a second), the bite's damage and reach, and the Gut Blast's damage a bite (double the cannon's). */
	public static final double MAW_PULL = 0.45, MAW_BITE_REACH = 1.8;
	public static final int MAW_BITE_EVERY = 10;
	public static final float MAW_BITE = 4.0F, MAW_BLAST_PER_BITE = CANNON_PER_BITE * 2.0F;
	/** Screen shake (degrees, and how far it's felt): the change-in and the stamp, the blast, the slam. */
	public static final float WORLDMAW_SHAKE = 1.4F, BLAST_SHAKE = 1.2F, SLAM_SHAKE = 0.9F;
	public static final double WORLDMAW_SHAKE_RANGE = 22.0, BLAST_SHAKE_RANGE = 18.0, SLAM_SHAKE_RANGE = 14.0;
	/** Tag on a flying block: one that loads back from a save has no Worldmaw left to answer for it, and is removed. */
	private static final String SHOT_TAG = "jaymospowers_worldmaw_shot";

	/** A flying block: watched until it meets something. */
	private static final class Shot {
		final int id;
		int age;

		Shot(int id) {
			this.id = id;
		}
	}

	/** What each Worldmaw has under way (server thread only). */
	private static final class State {
		ServerLevel level;
		/** The last kinds of block it ate, newest last. */
		final ArrayDeque<BlockState> memory = new ArrayDeque<>();
		final List<Shot> shots = new ArrayList<>();
		/** Ticks of the inhale left (0: not inhaling). */
		int maw;
		/** Creatures gored by this charge, and when each was last hit by the roll (player tick count). */
		final Set<Integer> gored = new HashSet<>();
		final Map<Integer, Integer> rolledInto = new HashMap<>();

		State(ServerLevel level) {
			this.level = level;
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	static {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof FallingBlockEntity block && block.entityTags().contains(SHOT_TAG)) {
				level.getServer().execute(block::discard);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
	}

	public Worldmaw(XenoForm base) {
		super(base, 1.6F, 24.0F, 8.0, -0.05, 0.7, 0.7);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.ARMADILLO_SCUTE, Items.COBBLESTONE, Items.IRON_NUGGET, Items.TURTLE_SCUTE, Items.TNT};
	}

	/** The change-in film zooms on these in turn (model part names): the shell, the tail, the snout, the horn. */
	@Override
	public String[] morphParts() {
		return new String[] {"shell0+shell1+shell2+shell3+shell4+shell5", "tail+tail2", "snout", "horn_r+horn_l"};
	}

	/** Whether {@code player} is inhaling right now (for the body's pose, see {@code XenoformPower.syncAct}). */
	public static boolean inhaling(ServerPlayer player) {
		State st = STATES.get(player.getUUID());
		return st != null && st.maw > 0;
	}

	private static State state(ServerPlayer player) {
		State st = STATES.get(player.getUUID());
		if (st == null || st.level != player.level()) {
			if (st != null) {
				clear(st);
			}
			st = new State(player.level());
			STATES.put(player.getUUID(), st);
		}
		return st;
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.horn = 0;
		live.hornSlam = live.rolled = false;
		ServerLevel level = player.level();
		ring(level, ParticleTypes.EXPLOSION, player.position(), 2.5, 10);
		sound(player, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4F, 0.7F);
		sound(player, SoundEvents.RAVAGER_ROAR, 1.0F, 0.6F);
		Shakes.heavy(player, player.position(), WORLDMAW_SHAKE_RANGE, WORLDMAW_SHAKE);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		State st = STATES.remove(player.getUUID());
		if (st != null) {
			clear(st);
		}
		// back in Gorger's smaller gut
		live.gorge = Math.min(live.gorge, Gorger.GORGE_MAX);
		live.horn = 0;
		live.hornSlam = live.rolled = false;
		live.hudDirty = true;
	}

	/** Every flying block it started is removed, and an inhale in progress is over. */
	private static void clear(State st) {
		st.maw = 0;
		for (Shot shot : st.shots) {
			Entity block = st.level.getEntity(shot.id);
			if (block instanceof FallingBlockEntity) {
				block.discard();
			}
		}
		st.shots.clear();
		st.gored.clear();
		st.rolledInto.clear();
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (live.rolled && source.is(DamageTypeTags.IS_PROJECTILE)) {
			sound(player, SoundEvents.ARMADILLO_SCUTE_DROP, 1.0F, 1.4F);
			return false;
		}
		// the slam's own landing doesn't hurt it
		return !(live.hornSlam && source.is(DamageTypeTags.IS_FALL));
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.horn > 0 || live.hornSlam;
	}

	// ---- who may be hurt ----

	private static boolean harmable(ServerPlayer player, Entity e) {
		return !(e instanceof ServerPlayer other) || player.canHarmPlayer(other);
	}

	/** Every creature within {@code radius} that this one's moves may hurt: not its pets or team, and not a player pvp protects. */
	private static List<LivingEntity> foes(ServerPlayer player, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity living : around(player, radius)) {
			if (harmable(player, living)) {
				out.add(living);
			}
		}
		return out;
	}

	/** Hurts {@code target} for {@code damage}; true when the blow landed (or the target is no player, who has no pvp to refuse it). */
	private static boolean hit(ServerPlayer player, LivingEntity target, float damage) {
		return target.hurtServer(player.level(), player.damageSources().playerAttack(player), damage) || !(target instanceof ServerPlayer);
	}

	// ---- keys ----

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		State st = state(player);
		if (key == PowerKey.ABILITY_4) {
			if (st.maw > 0 && down) {
				return;
			}
			if (live.rolled != down) {
				live.rolled = down;
				sound(player, down ? SoundEvents.ARMADILLO_ROLL : SoundEvents.ARMADILLO_UNROLL_FINISH, 1.0F, 0.7F);
				st.rolledInto.clear();
				if (down) {
					cooldown(player, live, B, 1);
				}
			}
			return;
		}
		if (!down || live.rolled || st.maw > 0) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && gulp(player, live, st)) {
					cooldown(player, live, R, GULP_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					if (live.gorge <= 0) {
						player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gorger.empty").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
						return;
					}
					blast(player, live, st, CANNON_PER_BITE, CANNON_REACH, true);
					cooldown(player, live, G, CANNON_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && live.horn == 0 && !live.hornSlam) {
					live.horn = QUAKE_TICKS;
					st.gored.clear();
					sound(player, SoundEvents.RAVAGER_ROAR, 0.8F, 1.3F);
					cooldown(player, live, V, QUAKE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: unhinge the jaw and inhale; the rest happens in {@link #tick}, ending in the Gut Blast. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		State st = state(player);
		if (!ready(player, live, C) || live.rolled || st.maw > 0 || live.horn > 0 || live.hornSlam) {
			return;
		}
		st.maw = MAW_TICKS;
		sound(player, SoundEvents.BREEZE_INHALE, 1.6F, 0.5F);
		sound(player, SoundEvents.RAVAGER_ROAR, 1.2F, 0.5F);
		cooldown(player, live, C, MAW_COOLDOWN);
	}

	// ---- R: Deep Gulp ----

	/** Whether {@code pos} may be eaten, by Gulp's rules: breakable (hardness up to the limit), no contents, no fluid, and the player may touch it. */
	private static boolean edible(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness <= Gorger.GULP_HARDEST && player.mayBuild() && level.mayInteract(player, pos);
	}

	/** Eats the block at {@code pos} (already checked edible): the gut fills, the kind is remembered, nothing drops. False when the gut is full. */
	private static boolean eat(ServerPlayer player, XenoformPower.Live live, State st, ServerLevel level, BlockPos pos) {
		if (live.gorge >= GUT_MAX) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		float hardness = state.getDestroySpeed(level, pos);
		int bites = Math.min(Gorger.BITES_MAX, 1 + (int)(hardness / Gorger.HARDNESS_PER_BITE));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
		level.destroyBlock(pos, false, player, 512);
		live.gorge = Math.min(GUT_MAX, live.gorge + bites);
		st.memory.addLast(state);
		while (st.memory.size() > FLYING_MAX) {
			st.memory.removeFirst();
		}
		live.hudDirty = true;
		return true;
	}

	/** R: the aimed block and the eight round it on that face. */
	private static boolean gulp(ServerPlayer player, XenoformPower.Live live, State st) {
		ServerLevel level = player.level();
		if (live.gorge >= GUT_MAX) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gorger.full").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		HitResult pick = player.pick(GULP_REACH, 0.0F, false);
		if (!(pick instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos centre = hit.getBlockPos();
		Direction.Axis axis = hit.getDirection().getAxis();
		int eaten = 0;
		for (int a = -1; a <= 1 && live.gorge < GUT_MAX; a++) {
			for (int b = -1; b <= 1 && live.gorge < GUT_MAX; b++) {
				BlockPos pos = switch (axis) {
					case X -> centre.offset(0, a, b);
					case Y -> centre.offset(a, 0, b);
					case Z -> centre.offset(a, b, 0);
				};
				if (edible(player, level, pos) && eat(player, live, st, level, pos)) {
					eaten++;
				}
			}
		}
		if (eaten == 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gorger.inedible").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		sound(player, SoundEvents.GENERIC_EAT.value(), 1.2F, 0.5F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gorger.gorge", live.gorge, GUT_MAX).withStyle(ChatFormatting.GOLD));
		return true;
	}

	// ---- G: Rubble Cannon, and the Gut Blast that ends Worldmaw ----

	/**
	 * The whole gut comes up in a cone ahead: {@code perBite} damage a bite to every creature that may be hurt, a shove, rubble, and
	 * (for the cannon) up to {@link #FLYING_MAX} of the blocks it ate as falling blocks.
	 */
	private static void blast(ServerPlayer player, XenoformPower.Live live, State st, float perBite, double reach, boolean fly) {
		ServerLevel level = player.level();
		float damage = live.gorge * perBite;
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		for (LivingEntity e : foes(player, reach)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye).normalize();
			if (to.dot(look) > CANNON_CONE && player.hasLineOfSight(e) && hit(player, e, damage)) {
				shove(e, player.position(), CANNON_PUSH * (0.5 + live.gorge / (double)GUT_MAX), CANNON_UP);
			}
		}
		BlockState rubble = st.memory.isEmpty() ? Blocks.COBBLESTONE.defaultBlockState() : st.memory.peekLast();
		for (int i = 1; i <= 10; i++) {
			Vec3 p = eye.add(look.scale(i * reach / 10.0));
			double spread = 0.06 * i;
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rubble), p.x, p.y, p.z, 4 + live.gorge / 2, spread, spread, spread, 0.2);
		}
		if (fly) {
			int sent = 0;
			for (BlockState block : new ArrayList<>(st.memory)) {
				if (sent >= FLYING_MAX) {
					break;
				}
				if (launch(player, st, block, look, sent)) {
					sent++;
				}
			}
		}
		st.memory.clear();
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.0F);
		sound(player, SoundEvents.RAVAGER_STUNNED, 1.2F, 0.9F);
		Shakes.heavy(player, player.position(), BLAST_SHAKE_RANGE, BLAST_SHAKE);
		live.gorge = 0;
		live.hudDirty = true;
	}

	/** One eaten block flies out of the mouth as a real falling block that never lands: it drops nothing and is removed when it meets something. */
	private static boolean launch(ServerPlayer player, State st, BlockState block, Vec3 look, int index) {
		ServerLevel level = player.level();
		BlockPos pos = BlockPos.containing(player.getEyePosition().add(look.scale(1.2)));
		if (!level.getBlockState(pos).isAir()) {
			pos = BlockPos.containing(player.getEyePosition());
			if (!level.getBlockState(pos).isAir()) {
				return false;
			}
		}
		FallingBlockEntity flying = FallingBlockEntity.fall(level, pos, block);
		flying.disableDrop();
		flying.addTag(SHOT_TAG);
		double side = (index - 1) * FLYING_SPREAD;
		Vec3 across = new Vec3(-look.z, 0.0, look.x);
		across = across.lengthSqr() < 1.0E-4 ? Vec3.ZERO : across.normalize();
		flying.setDeltaMovement(look.scale(FLYING_SPEED).add(across.scale(side * FLYING_SPEED)).add(0.0, 0.1, 0.0));
		flying.syncVelocity = true;
		st.shots.add(new Shot(flying.getId()));
		return true;
	}

	/** The flying blocks: one that meets a creature or a block (or runs out of life) bursts, hurts what is near and is removed, never placed. */
	private static void shotsTick(ServerPlayer player, State st) {
		ServerLevel level = player.level();
		for (Iterator<Shot> it = st.shots.iterator(); it.hasNext();) {
			Shot shot = it.next();
			Entity block = level.getEntity(shot.id);
			if (!(block instanceof FallingBlockEntity flying) || flying.isRemoved()) {
				it.remove();
				continue;
			}
			shot.age++;
			Vec3 d = flying.getDeltaMovement();
			AABB box = flying.getBoundingBox();
			boolean met = shot.age > FLYING_LIFE || !level.noBlockCollision(flying, box.expandTowards(d.add(0.0, -0.08, 0.0)));
			if (!met) {
				AABB sweep = box.expandTowards(d).inflate(0.3);
				met = !level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
					&& !e.isPassengerOfSameVehicle(player) && !spared(player, e) && harmable(player, e)).isEmpty();
			}
			if (met) {
				it.remove();
				burst(player, level, flying);
				flying.discard();
			}
		}
	}

	private static void burst(ServerPlayer player, ServerLevel level, FallingBlockEntity flying) {
		Vec3 at = flying.position().add(0.0, flying.getBbHeight() * 0.5, 0.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(FLYING_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isPassengerOfSameVehicle(player) && !spared(player, e)
				&& harmable(player, e) && e.distanceToSqr(at) <= FLYING_RADIUS * FLYING_RADIUS)) {
			if (hit(player, e, FLYING_DAMAGE)) {
				shove(e, at, 0.6, 0.3);
			}
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, flying.getBlockState()), at.x, at.y, at.z, 24, 0.4, 0.4, 0.4, 0.15);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	// ---- tick ----

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		State st = state(player);
		if (live.rolled) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 0, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 5, 1, false, false));
			rollInto(player, st);
		}
		if (live.horn > 0) {
			quake(player, live, st, level);
		}
		if (live.hornSlam && (player.onGround() || player.isInWater())) {
			slam(player, live, level);
		}
		if (st.maw > 0) {
			maw(player, live, st, level);
		}
		if (!st.shots.isEmpty()) {
			shotsTick(player, st);
		}
	}

	/** Spiked Roll: what it rolls into is hurt and bounces off (each creature at most once in {@link #ROLL_HIT_EVERY} ticks). */
	private static void rollInto(ServerPlayer player, State st) {
		Vec3 move = player.getDeltaMovement();
		if (move.horizontalDistanceSqr() < ROLL_MIN_SPEED_SQ) {
			return;
		}
		for (LivingEntity e : foes(player, ROLL_REACH + player.getBbWidth() * 0.5)) {
			Integer last = st.rolledInto.get(e.getId());
			if (last != null && player.tickCount - last < ROLL_HIT_EVERY) {
				continue;
			}
			st.rolledInto.put(e.getId(), player.tickCount);
			if (hit(player, e, ROLL_DAMAGE)) {
				shove(e, player.position(), ROLL_BOUNCE, ROLL_BOUNCE_UP);
				sound(player, SoundEvents.ARMADILLO_HURT_REDUCED, 1.0F, 0.7F);
			}
		}
		if (st.rolledInto.size() > 32) {
			st.rolledInto.values().removeIf(t -> player.tickCount - t >= ROLL_HIT_EVERY);
		}
	}

	/** One tick of the Quake Charge: forward along the ground, goring every creature on the way once; off a ledge it becomes a slam. */
	private static void quake(ServerPlayer player, XenoformPower.Live live, State st, ServerLevel level) {
		live.horn--;
		if (!player.onGround() && !player.isInWater() && player.getDeltaMovement().y < -0.1) {
			live.horn = 0;
			live.hornSlam = true;
			live.hornFrom = player.getY();
			player.setDeltaMovement(player.getDeltaMovement().x * 0.5, -1.4, player.getDeltaMovement().z * 0.5);
			player.syncVelocity = true;
			sound(player, SoundEvents.ARMADILLO_ROLL, 1.0F, 0.5F);
			st.gored.clear();
			return;
		}
		Vec3 ahead = player.getLookAngle().horizontal();
		ahead = ahead.lengthSqr() < 1.0E-4 ? Vec3.ZERO : ahead.normalize();
		player.setDeltaMovement(ahead.x * QUAKE_SPEED, player.getDeltaMovement().y, ahead.z * QUAKE_SPEED);
		player.syncVelocity = true;
		if (player.tickCount % 2 == 0) {
			BlockState ground = level.getBlockState(player.blockPosition().below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.4, 0.05, 0.4, 0.12);
			}
		}
		for (LivingEntity e : foes(player, QUAKE_HIT_REACH + player.getBbWidth())) {
			Vec3 to = e.position().subtract(player.position()).horizontal();
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(ahead) > 0.3 && st.gored.add(e.getId())) {
				if (hit(player, e, QUAKE_DAMAGE)) {
					shove(e, player.position(), QUAKE_TOSS, QUAKE_TOSS_UP);
				}
				sound(player, SoundEvents.RAVAGER_ATTACK, 1.0F, 0.9F);
				Shakes.heavy(player, player.position(), 8.0, 0.4F);
			}
		}
		if (player.horizontalCollision) {
			// a wall stops it dead
			live.horn = 0;
			sound(player, SoundEvents.ARMADILLO_HURT, 1.0F, 0.6F);
		}
		if (live.horn == 0) {
			st.gored.clear();
		}
	}

	private static void slam(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		live.hornSlam = false;
		player.resetFallDistance();
		float damage = Math.min(SLAM_MAX, SLAM_DAMAGE + (float)Math.max(0.0, live.hornFrom - player.getY()) * SLAM_PER_BLOCK);
		for (LivingEntity e : foes(player, SLAM_RADIUS)) {
			if (hit(player, e, damage)) {
				shove(e, player.position(), 1.0, 0.6);
			}
		}
		BlockState ground = level.getBlockState(player.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY(), player.getZ(), 60, SLAM_RADIUS * 0.4, 0.1,
				SLAM_RADIUS * 0.4, 0.2);
		}
		ring(level, ParticleTypes.EXPLOSION, player.position(), SLAM_RADIUS * 0.6, 8);
		sound(player, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.6F, 0.7F);
		Shakes.heavy(player, player.position(), SLAM_SHAKE_RANGE, SLAM_SHAKE);
	}

	// ---- C: Worldmaw ----

	/** One tick of the inhale: rooted, the cone gulps blocks, drags creatures in and bites them; at the end the Gut Blast. */
	private static void maw(ServerPlayer player, XenoformPower.Live live, State st, ServerLevel level) {
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		Vec3 mouth = eye.add(look.scale(0.9));
		int elapsed = MAW_TICKS - st.maw;
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 6, false, false));
		player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
		player.syncVelocity = true;
		// air streaming in along the cone
		for (int i = 0; i < 3; i++) {
			double t = 1.5 + player.getRandom().nextDouble() * (MAW_DEPTH - 1.0);
			double r = t * (MAW_WIDTH * 0.5 / MAW_DEPTH);
			Vec3 side = new Vec3(player.getRandom().nextGaussian(), player.getRandom().nextGaussian(), player.getRandom().nextGaussian()).scale(r * 0.5);
			Vec3 from = eye.add(look.scale(t)).add(side);
			Vec3 in = mouth.subtract(from).normalize();
			level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 0, in.x, in.y, in.z, 0.5);
		}
		if (elapsed % 8 == 0) {
			sound(player, SoundEvents.BREEZE_INHALE, 1.0F, 0.6F + elapsed * 0.01F);
		}
		// creatures: dragged in, bitten every half second
		for (LivingEntity e : foes(player, MAW_DEPTH + 2.0)) {
			if (!inCone(eye, look, e.getBoundingBox().getCenter(), e.getBbWidth() * 0.5 + 0.6) || !player.hasLineOfSight(e)) {
				continue;
			}
			Vec3 toMouth = mouth.subtract(e.getBoundingBox().getCenter());
			double dist = toMouth.length();
			if (dist > MAW_BITE_REACH + e.getBbWidth() * 0.5) {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(toMouth.normalize().scale(MAW_PULL)));
				e.syncVelocity = true;
				e.resetFallDistance();
			} else {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.3));
				e.syncVelocity = true;
				if (elapsed % MAW_BITE_EVERY == 0 && hit(player, e, MAW_BITE)) {
					sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
					level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
				}
			}
		}
		// blocks: a mouthful every few ticks
		if (elapsed % MAW_BEAT == 0 && live.gorge < GUT_MAX) {
			gulpCone(player, live, st, level, eye, look);
		}
		if (--st.maw <= 0) {
			st.maw = 0;
			blast(player, live, st, MAW_BLAST_PER_BITE, CANNON_REACH, false);
		}
	}

	/** Whether {@code at} lies in the inhale's cone ({@link #MAW_DEPTH} deep, {@link #MAW_WIDTH} wide at the far end) with {@code slack} blocks to spare. */
	private static boolean inCone(Vec3 eye, Vec3 look, Vec3 at, double slack) {
		Vec3 rel = at.subtract(eye);
		double t = rel.dot(look);
		if (t < 0.0 || t > MAW_DEPTH + slack) {
			return false;
		}
		double radial = rel.subtract(look.scale(t)).length();
		return radial <= t * (MAW_WIDTH * 0.5 / MAW_DEPTH) + slack;
	}

	/** Eats up to {@link #MAW_BLOCKS} blocks of the cone, nearest first, by Gulp's rules; only ones open to the air, so it works through a wall a layer at a time. */
	private static void gulpCone(ServerPlayer player, XenoformPower.Live live, State st, ServerLevel level, Vec3 eye, Vec3 look) {
		int reach = (int)Math.ceil(MAW_DEPTH) + 1;
		BlockPos origin = BlockPos.containing(eye);
		List<BlockPos> found = new ArrayList<>();
		for (int x = -reach; x <= reach; x++) {
			for (int y = -reach; y <= reach; y++) {
				for (int z = -reach; z <= reach; z++) {
					BlockPos pos = origin.offset(x, y, z);
					if (inCone(eye, look, Vec3.atCenterOf(pos), 0.5) && exposed(level, pos) && edible(player, level, pos)) {
						found.add(pos.immutable());
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
		int eaten = 0;
		for (BlockPos pos : found) {
			if (eaten >= MAW_BLOCKS || live.gorge >= GUT_MAX) {
				break;
			}
			// an earlier bite may have changed this one
			if (edible(player, level, pos) && eat(player, live, st, level, pos)) {
				eaten++;
			}
		}
		if (eaten > 0) {
			sound(player, SoundEvents.GENERIC_EAT.value(), 1.0F, 0.5F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.gorger.gorge", live.gorge, GUT_MAX).withStyle(ChatFormatting.GOLD));
		}
	}

	/** A block with at least one face open to air or something that can be replaced. */
	private static boolean exposed(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockState next = level.getBlockState(pos.relative(dir));
			if (next.isAir() || next.canBeReplaced() || !next.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
