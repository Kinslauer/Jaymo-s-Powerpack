package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Debris;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bulwark's charge: an unstoppable sprint. Sprint for {@link #BUILD_TICKS} ticks and Bulwark is barrelling: faster, not
 * to be shoved, everything breakable in its path (stone as well as wood, up to {@link #HARDEST} hardness) goes to
 * rubble, and every creature it meets is hit and flung aside without slowing it. Run into something it cannot break and
 * it staggers.
 */
public final class BulwarkCharge {
	// ---- tuning ----
	/** Ticks of sprinting that build the charge, ticks of lapsed sprint forgiven, and the slowest pace (blocks a tick) that counts as running. */
	public static final int BUILD_TICKS = 24, GRACE = 5;
	public static final double MIN_PACE = 0.12;
	/** Once charging, ticks of not running before it is over. */
	public static final int END_SLACK = 8;
	/** Ticks before it can build again after it ends, and after a stagger. */
	public static final int COOLDOWN = 40, STAGGER_COOLDOWN = 80;
	/** The hardest block it goes through (stone 1.5, ore 3, cobblestone 2, deepslate 3), blocks broken a tick, the share that drop. */
	public static final float HARDEST = 3.5F, DROP_CHANCE = 0.3F;
	public static final int BREAKS_PER_TICK = 8;
	/** How far ahead of its body it breaks and hits (blocks). */
	public static final double REACH = 1.3;
	/** While charging: added speed (share) and knockback resistance. */
	public static final double SPEED_BONUS = 0.35, KNOCKBACK_RESIST = 1.0;
	/** What it hits: damage, flung ahead and up. */
	public static final float DAMAGE = 9.0F;
	public static final double FLING_OUT = 1.6, FLING_UP = 0.5;
	/** The stagger: slowed this long and this hard, shoved back this far. */
	public static final int STAGGER_TICKS = 40, STAGGER_SLOW = 2;
	public static final double STAGGER_SHOVE = 0.7;
	/** Rumble every this many ticks, the shake it gives, the hit's and the stagger's shake. */
	public static final int RUMBLE_EVERY = 6;
	public static final float RUMBLE_SHAKE = 0.25F, HIT_SHAKE = 0.6F, STAGGER_SHAKE = 1.2F;
	public static final int COLOR = 0xB8C4D0;

	/** How much Bulwark's charge weighs in a clash of blows, times his attack damage. */
	static final float CLASH_STRENGTH = 3.0F;
	private static final Identifier SPEED_ID = JaymosPowers.id("xenoform_bulwark_charge_speed");
	private static final Identifier KNOCKBACK_ID = JaymosPowers.id("xenoform_bulwark_charge_knockback");

	private static final class Run {
		int sprint, slack;
		boolean charging;
		long coolUntil;
		Vec3 last;
		final IntSet hit = new IntOpenHashSet();
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private BulwarkCharge() {
	}

	/** Whether Bulwark is charging now. */
	public static boolean charging(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		return run != null && run.charging;
	}

	/** Every tick as Bulwark. */
	public static void tick(ServerPlayer player) {
		Run run = RUNS.computeIfAbsent(player.getUUID(), id -> new Run());
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 pos = player.position();
		Vec3 moved = run.last == null ? Vec3.ZERO : pos.subtract(run.last).multiply(1.0, 0.0, 1.0);
		double pace = moved.length();
		run.last = pos;
		boolean running = player.isSprinting() && pace >= MIN_PACE;
		if (!run.charging) {
			if (now < run.coolUntil) {
				return;
			}
			if (running) {
				run.slack = 0;
				if (++run.sprint >= BUILD_TICKS) {
					begin(player, run);
				}
			} else if (++run.slack > GRACE) {
				run.sprint = 0;
			}
			return;
		}
		if (!running) {
			if (++run.slack > END_SLACK) {
				end(player, run, COOLDOWN);
				return;
			}
		} else {
			run.slack = 0;
		}
		Vec3 dir = pace > 0.05 ? moved : player.getLookAngle().horizontal();
		if (dir.lengthSqr() < 1.0E-4) {
			dir = player.getLookAngle().horizontal();
		}
		if (dir.lengthSqr() < 1.0E-4) {
			return;
		}
		dir = dir.normalize();
		// charging at another brute's great blow: the two lock (BlowClash)
		if (com.jaymospowers.core.BlowClash.meets(player, dir, (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * CLASH_STRENGTH)) {
			return;
		}
		if (now % RUMBLE_EVERY == 0) {
			Shakes.heavy(player, pos, 14.0, RUMBLE_SHAKE);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
		BlockState under = level.getBlockState(player.blockPosition().below());
		if (!under.isAir() && now % 2 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.3, 0.05, 0.3, 0.1);
		}
		fling(player, run, dir);
		if (breakAhead(player, dir)) {
			stagger(player, run, dir);
		}
	}

	/** Called as Bulwark ends: nothing of the charge is left. */
	public static void clear(ServerPlayer player) {
		Run run = RUNS.remove(player.getUUID());
		if (run != null && run.charging) {
			modifiers(player, false);
		}
	}

	private static void begin(ServerPlayer player, Run run) {
		ServerLevel level = player.level();
		run.charging = true;
		run.slack = 0;
		run.hit.clear();
		modifiers(player, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.2F, 0.5F);
		MagicFx.ring(level, player.position(), 3.0F, COLOR);
		Shakes.heavy(player, player.position(), 16.0, 0.5F);
		player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.xenoform.bulwark.charging").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
	}

	private static void end(ServerPlayer player, Run run, int cooldown) {
		run.charging = false;
		run.sprint = 0;
		run.slack = 0;
		run.hit.clear();
		run.coolUntil = player.level().getGameTime() + cooldown;
		modifiers(player, false);
	}

	private static void modifiers(ServerPlayer player, boolean on) {
		modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, SPEED_BONUS, on);
		modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, AttributeModifier.Operation.ADD_VALUE, KNOCKBACK_RESIST, on);
	}

	private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, AttributeModifier.Operation operation, double amount, boolean on) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (on) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		} else {
			instance.removeModifier(id);
		}
	}

	/** The creatures in its way are hit once each and flung ahead and up, and it runs on. */
	private static void fling(ServerPlayer player, Run run, Vec3 dir) {
		ServerLevel level = player.level();
		AABB zone = player.getBoundingBox().expandTowards(dir.scale(REACH)).inflate(0.3, 0.0, 0.3);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone, e -> e != player && e.isAlive() && !e.isSpectator()
			&& !(e instanceof ArmorStand) && !e.isPassengerOfSameVehicle(player) && !(e instanceof OwnableEntity pet && pet.getOwner() == player)
			&& !player.isAlliedTo(e) && !run.hit.contains(e.getId()))) {
			run.hit.add(target.getId());
			target.hurtServer(level, player.damageSources().playerAttack(player), BulwarkFury.boost(player, DAMAGE));
			Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
			double lean = Math.signum(target.position().subtract(player.position()).dot(side)) * 0.4;
			target.setDeltaMovement(dir.x * FLING_OUT + side.x * lean, FLING_UP, dir.z * FLING_OUT + side.z * lean);
			target.syncVelocity = true;
			Vec3 at = target.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.4, 0.5, 0.4, 0.3);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.5F);
			Shakes.heavy(player, at, 16.0, HIT_SHAKE);
		}
	}

	/** Whether Bulwark may break blocks at all: where it may build, with mob griefing on. */
	private static boolean mayBreak(ServerPlayer player, ServerLevel level) {
		return player.mayBuild() && level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/** A block the charge goes through: solid, hardness up to {@link #HARDEST}, never unbreakable, never one that holds things or a fluid. */
	private static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness <= HARDEST;
	}

	/**
	 * The blocks in its way break. True if something is left in its way that it cannot (or may not) break, and that
	 * is higher than a step: it staggers.
	 */
	private static boolean breakAhead(ServerPlayer player, Vec3 dir) {
		ServerLevel level = player.level();
		boolean may = mayBreak(player, level);
		AABB zone = player.getBoundingBox().move(dir.scale(REACH * 0.8)).deflate(0.0, 0.1, 0.0);
		int broken = 0;
		boolean blocked = false;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(zone.minX, zone.minY, zone.minZ), BlockPos.containing(zone.maxX, zone.maxY, zone.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
				continue;
			}
			if (may && breakable(level, pos, state) && level.mayInteract(player, pos)) {
				if (broken < BREAKS_PER_TICK) {
					BlockPos at = pos.immutable();
					Debris.puff(level, at, state, 1.0F);
					level.destroyBlock(at, level.getRandom().nextFloat() < DROP_CHANCE, player);
					broken++;
				}
			} else if (pos.getY() + state.getCollisionShape(level, pos).max(Direction.Axis.Y) > player.getY() + player.maxUpStep()) {
				blocked = true;
			}
		}
		return blocked;
	}

	/** Into something it cannot break: dust and cracks, a shove back, slowed a while. */
	private static void stagger(ServerPlayer player, Run run, Vec3 dir) {
		ServerLevel level = player.level();
		Vec3 ahead = player.position().add(dir.scale(player.getBbWidth() * 0.5 + 0.5)).add(0.0, 1.0, 0.0);
		BlockPos wall = BlockPos.containing(ahead);
		Debris.puff(level, wall, level.getBlockState(wall), 1.3F);
		level.playSound(null, ahead.x, ahead.y, ahead.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.6F);
		MagicFx.blast(level, ahead, 1.5F, COLOR);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, STAGGER_SLOW, false, false));
		player.setDeltaMovement(-dir.x * STAGGER_SHOVE, 0.25, -dir.z * STAGGER_SHOVE);
		player.syncVelocity = true;
		Shakes.heavy(player, ahead, 16.0, STAGGER_SHAKE);
		end(player, run, STAGGER_COOLDOWN);
	}
}
