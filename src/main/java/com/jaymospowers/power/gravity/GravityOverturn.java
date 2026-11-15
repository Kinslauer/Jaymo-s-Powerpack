package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.zfighter.ZFighterScouter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Overturn (hold B, tap V at a creature; Andy, 2026-11-15: "Can gravity users directly reverse an enemies' gravity?
 * ... only for enemies he is stronger or larger than, stronger enemies will animate a struggle, dropping their weapon
 * and grabbing the ground, resisting the gravity for the duration"). The creature under the crosshair, up to
 * {@link #RANGE} blocks off:
 * <ul>
 * <li>if you are stronger ({@link #might}: the scouter's power level, raised by how much of Gravity you have mastered
 * and by your fate) or bigger (height plus width), its gravity turns over for {@link #TICKS}: it falls up, drawn
 * upside down, rising no faster than {@link #MAX_RISE}. A ceiling it hits takes the fall like a floor would
 * ({@link #SAFE}, {@link #DAMAGE_PER_BLOCK}); when its weight comes back the right way it falls down again, and a death
 * from either counts as one by weight.</li>
 * <li>otherwise it resists: its weapon drops from its hand, it goes down and digs its hands into the ground
 * ({@link #GRASP}, drawn by GravityGraspPose), and holds there, unable to move, until the pull gives up.</li>
 * </ul>
 * Uses B's cooldown ({@link #COOLDOWN}, shorter when resisted: {@link #RESISTED_COOLDOWN}).
 */
public final class GravityOverturn {
	/** Reach (blocks). */
	static final double RANGE = 32.0;
	/** How long its gravity stays turned over, or how long it holds on (ticks). */
	static final int TICKS = 60;
	/** Its gravity while overturned (as a multiplier taken off: -2 turns 0.08 down into 0.08 up), and its top speed up (b/t). */
	static final double FLIP = -2.0, MAX_RISE = 0.5;
	/** A ceiling hit: the first {@code SAFE} blocks risen are free, then {@code DAMAGE_PER_BLOCK} for each, up to {@code DAMAGE_MAX}. */
	static final double SAFE = 3.0;
	static final float DAMAGE_PER_BLOCK = 1.0F, DAMAGE_MAX = 20.0F;
	/** Your might: the power level times (1 + this x the share of Gravity mastered), times {@code FATE_BONUS} once ascended or corrupted. */
	static final double MASTERY_BONUS = 1.0, FATE_BONUS = 1.5;
	/** B's cooldown after it (ticks), and after a resisted one. */
	static final int COOLDOWN = 300, RESISTED_COOLDOWN = 160;
	/** A dropped weapon can't be picked back up for this long (ticks). */
	static final int PICKUP_DELAY = 40;

	private static final Identifier FLIP_ID = JaymosPowers.id("gravity_overturn");

	/** On an overturned creature: until when (game time). Synced to everyone, who draw it upside down. */
	public static final AttachmentType<Long> FLIPPED = AttachmentRegistry.create(JaymosPowers.id("gravity_overturned"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/** On one holding on against it: since when (game time). Synced to everyone, who draw it clutching the ground. */
	public static final AttachmentType<Long> GRASP = AttachmentRegistry.create(JaymosPowers.id("gravity_grasp"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	private static final class Turn {
		final ResourceKey<Level> dimension;
		final UUID entity;
		final boolean resisting;
		final long until;
		final long stunUntil;
		double risen;
		double lastY;

		Turn(ResourceKey<Level> dimension, LivingEntity entity, boolean resisting, long until, long stunUntil) {
			this.dimension = dimension;
			this.entity = entity.getUUID();
			this.resisting = resisting;
			this.until = until;
			this.stunUntil = stunUntil;
			this.lastY = entity.getY();
		}
	}

	private static final List<Turn> TURNS = new ArrayList<>();

	private GravityOverturn() {
	}

	/**
	 * Aimed at a creature: overturned or resisting. Returns the cooldown to put on B, or -1 with nothing to aim at
	 * (nothing happens then).
	 */
	static int start(ServerPlayer player) {
		Entity seen = GravityPower.liftableInSight(player, RANGE);
		if (!(seen instanceof LivingEntity target) || !target.isAlive() || seen instanceof ArmorStand || busy(target)) {
			return -1;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		GravityPower.touch(target, player);
		boolean resists = !overpowers(player, target);
		long stun = -1;
		if (resists) {
			disarm(level, target);
			target.setAttached(GRASP, now);
			if (target instanceof ServerPlayer held) {
				// no keys answer while they hold on
				stun = now + TICKS;
				held.setAttached(GravityPower.HELD, new GravityPower.Held(stun, false));
			}
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.5F);
			com.jaymospowers.core.Debris.ring(level, target.blockPosition().below(), 1, 0.6F);
		} else {
			GravityPower.modifier(target, Attributes.GRAVITY, FLIP_ID, FLIP, true);
			target.setAttached(FLIPPED, now + TICKS);
			target.resetFallDistance();
			// a little hop off the ground to start it rising
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.2, 0.0));
			target.needsSync = true;
			target.syncVelocity = true;
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.8F, 0.5F);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4F, 1.9F);
		level.sendParticles(MagicFx.VOID, target.getX(), target.getY() + 0.1, target.getZ(), 24, 0.4, 0.05, 0.4, 0.06);
		TURNS.add(new Turn(level.dimension(), target, resists, now + TICKS, stun));
		return resists ? RESISTED_COOLDOWN : COOLDOWN;
	}

	/** Whether {@code player} can turn {@code target} over: stronger than it, or bigger. */
	static boolean overpowers(ServerPlayer player, LivingEntity target) {
		return might(player) > ZFighterScouter.powerLevel(target) || size(player) > size(target);
	}

	/**
	 * A gravity user's might: the scouter's sums (health and attack; a Z Fighter's ki and forms through
	 * {@link ZFighterScouter#powerLevel}, but without the weak elemental ki aura it reads off a Gravity user), raised by
	 * mastery and fate.
	 */
	static double might(ServerPlayer player) {
		double level = ZFighterScouter.powerLevel(player);
		if (ZFighterScouter.elemental(player)) {
			level /= ZFighterScouter.ELEMENTAL_AURA;
		}
		level *= 1.0 + MASTERY_BONUS * Mastery.unlocked(player) / (double)Mastery.ALL;
		if (Fate.isAscended(player) || Fate.isCorrupted(player)) {
			level *= FATE_BONUS;
		}
		return level;
	}

	private static double size(LivingEntity entity) {
		return entity.getBbHeight() + entity.getBbWidth();
	}

	/** Its weapon falls from its hand. */
	private static void disarm(ServerLevel level, LivingEntity target) {
		ItemStack held = target.getMainHandItem();
		if (held.isEmpty()) {
			return;
		}
		target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		ItemEntity item = target.spawnAtLocation(level, held, (float)target.getBbHeight() * 0.5F);
		if (item != null) {
			item.setPickUpDelay(PICKUP_DELAY);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.3F, 1.6F);
	}

	private static boolean busy(LivingEntity target) {
		for (Turn turn : TURNS) {
			if (turn.entity.equals(target.getUUID())) {
				return true;
			}
		}
		return false;
	}

	static void tick(MinecraftServer server) {
		if (TURNS.isEmpty()) {
			return;
		}
		for (Iterator<Turn> it = TURNS.iterator(); it.hasNext(); ) {
			Turn turn = it.next();
			ServerLevel level = server.getLevel(turn.dimension);
			Entity entity = level == null ? null : level.getEntity(turn.entity);
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				it.remove();
				continue;
			}
			if (level.getGameTime() >= turn.until) {
				it.remove();
				end(living, turn);
				continue;
			}
			if (turn.resisting) {
				hold(level, living);
			} else {
				rise(level, living, turn);
			}
		}
	}

	/** Holding on: rooted where it is, shaking against the pull. */
	private static void hold(ServerLevel level, LivingEntity living) {
		Vec3 v = living.getDeltaMovement();
		living.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
		living.needsSync = true;
		living.syncVelocity = true;
		if (living instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (level.getRandom().nextInt(3) == 0) {
			level.sendParticles(MagicFx.VOID, living.getX(), living.getY() + 0.1, living.getZ(), 2, 0.35, 0.05, 0.35, 0.02);
		}
		if (level.getGameTime() % 10 == 0) {
			BlockPos under = living.blockPosition().below();
			com.jaymospowers.core.Debris.puff(level, under, level.getBlockState(under), 0.4F);
		}
	}

	/** Falling up: no faster than {@link #MAX_RISE}; a ceiling hit takes the fall in one go. */
	private static void rise(ServerLevel level, LivingEntity living, Turn turn) {
		double moved = living.getY() - turn.lastY;
		turn.lastY = living.getY();
		turn.risen += Math.max(0.0, moved);
		living.resetFallDistance();
		Vec3 v = living.getDeltaMovement();
		if (v.y > MAX_RISE) {
			Vec3 capped = new Vec3(v.x, MAX_RISE, v.z);
			living.setDeltaMovement(capped);
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer player) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(capped));
			}
		}
		if (living.verticalCollision && !living.verticalCollisionBelow && turn.risen > 0.0) {
			float damage = (float)Math.min(DAMAGE_MAX, Math.max(0.0, turn.risen - SAFE) * DAMAGE_PER_BLOCK);
			turn.risen = 0.0;
			if (damage > 0.0F) {
				living.hurtServer(level, level.damageSources().fall(), damage);
				BlockPos above = BlockPos.containing(living.getX(), living.getBoundingBox().maxY + 0.2, living.getZ());
				com.jaymospowers.core.Debris.puff(level, above, level.getBlockState(above), (float)Mth.clamp(damage / 8.0F, 0.4F, 2.0F));
				level.playSound(null, living.getX(), living.getY(), living.getZ(), damage > 8.0F ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND,
					SoundSource.PLAYERS, 1.0F, 0.8F);
			}
		}
	}

	/** The pull gives up: weight the right way again (it falls), or it lets go of the ground. */
	private static void end(LivingEntity living, Turn turn) {
		if (turn.resisting) {
			living.removeAttached(GRASP);
			if (living instanceof ServerPlayer player) {
				GravityPower.Held held = player.getAttached(GravityPower.HELD);
				if (held != null && held.stunUntil() == turn.stunUntil) {
					player.removeAttached(GravityPower.HELD);
				}
			}
			return;
		}
		GravityPower.modifier(living, Attributes.GRAVITY, FLIP_ID, 0.0, false);
		living.removeAttached(FLIPPED);
		// it falls from where it is now, so the fall counts only from here
		living.resetFallDistance();
	}

	static void clear() {
		TURNS.clear();
	}
}
