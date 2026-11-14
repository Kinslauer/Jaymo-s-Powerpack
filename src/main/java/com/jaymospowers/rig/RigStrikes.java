package com.jaymospowers.rig;

import com.jaymospowers.core.HitStop;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Rig's heavier hitting (Andy's picks from The Awakening's suit; ideas only, our own code):
 * <ul>
 * <li><b>Tagged targets:</b> the hostiles a scan finds are tagged for {@link #TAG_TICKS}. With no lock, the launcher's
 * missiles each pick a tagged target of their own and home on it, and everything the wearer does to a tagged target
 * lands {@link #TAG_BONUS} harder.</li>
 * <li><b>Thruster dash</b> (a Rig menu pick, whole set): after a two-tick wind-up the boots throw the wearer along the
 * look (level with the ground when standing on it); for {@link #DASH_RAM_TICKS} they ram whatever they meet, once each.</li>
 * <li><b>Artillery barrage</b> (a Rig menu pick, Mind level {@link Rig#MAX_LEVEL}): the ground looked at is marked, the
 * shoulder pods empty straight up, and the missiles come down round the mark a moment later (more on the Siege frame).
 * They hurt and throw but leave the blocks alone, like the launcher's.</li>
 * <li><b>Powered punches:</b> a bare fist with the Arms on lands with a flare at the elbow, a step into it, and a beat of
 * hit-stop ({@link HitStop}).</li>
 * </ul>
 */
public final class RigStrikes {
	/** Tagged: how long a scan's tag lasts (ticks), and the share more the wearer's blows do to a tagged target. */
	static final int TAG_TICKS = 180;
	static final float TAG_BONUS = 0.35F;
	/** Thruster dash: charge, cooldown (ticks), the burst's speed (b/t), how long it rams (ticks), the ram's reach round the wearer, damage, knockback, and its hold. */
	static final int DASH_COST = 50, DASH_COOLDOWN = 40, DASH_RAM_TICKS = 7, DASH_HOLD = 2;
	static final double DASH_SPEED = 1.7, DASH_REACH = 0.7, DASH_KNOCKBACK = 1.1;
	static final float DASH_DAMAGE = 5.0F;
	/** Artillery barrage: charge, cooldown (ticks), how far the mark can be (blocks), missiles (plus the frame's extra, doubled), the ticks from the pods emptying to the first landing and between landings, the scatter round the mark, and each burst's size. */
	static final int BARRAGE_COST = 400, BARRAGE_COOLDOWN = 600, BARRAGE_MISSILES = 6, BARRAGE_FALL = 22, BARRAGE_GAP = 3;
	static final double BARRAGE_RANGE = 48.0, BARRAGE_SCATTER = 3.5, BARRAGE_HEIGHT = 26.0;
	static final float BARRAGE_BURST = 2.0F;
	/** A powered punch: its hold (ticks), the step into it (b/t), and the flare at the elbow. */
	static final int PUNCH_HOLD = 2;
	static final double PUNCH_STEP = 0.22;
	static final float PUNCH_FLARE = 0.3F;

	private static final class State {
		/** Entity id to the game time its tag ends. */
		final Map<Integer, Long> tagged = new HashMap<>();
		long dashReady, barrageReady;
		/** A dash under way: the game time its ram ends, and who it has already hit. */
		long ramUntil;
		final Set<Integer> rammed = new HashSet<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** Set while a tag's extra damage goes in, so it does not earn a bonus of its own. */
	private static boolean bonus;

	private RigStrikes() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(RigStrikes::afterDamage);
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), u -> new State());
	}

	// ---- tagged targets -----------------------------------------------------------------------------------------------

	/** A scan's hostiles (entity ids) are tagged from now. */
	static void tag(ServerPlayer player, List<Integer> ids) {
		State state = state(player);
		long until = player.level().getGameTime() + TAG_TICKS;
		for (int id : ids) {
			state.tagged.put(id, until);
		}
	}

	static boolean tagged(ServerPlayer player, Entity target) {
		State state = STATES.get(player.getUUID());
		Long until = state == null ? null : state.tagged.get(target.getId());
		return until != null && until > player.level().getGameTime();
	}

	/** The tagged targets in reach and in sight, nearest first; empty with none. */
	static List<LivingEntity> tagged(ServerPlayer player) {
		List<LivingEntity> found = new ArrayList<>();
		State state = STATES.get(player.getUUID());
		if (state == null) {
			return found;
		}
		long now = player.level().getGameTime();
		state.tagged.values().removeIf(until -> until <= now);
		for (int id : state.tagged.keySet()) {
			if (player.level().getEntity(id) instanceof LivingEntity living && living.isAlive() && living.distanceTo(player) <= RigActives.LOCK_RANGE
				&& player.hasLineOfSight(living)) {
				found.add(living);
			}
		}
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		return found;
	}

	/** The launcher's {@code i}th missile with no lock: a tagged target of its own (they share when there are fewer), or null. */
	static @Nullable LivingEntity target(List<LivingEntity> tagged, int i) {
		return tagged.isEmpty() ? null : tagged.get(i % tagged.size());
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (bonus || blocked || taken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || victim == player
			|| !(victim.level() instanceof ServerLevel level) || !Rig.wearsAny(player)) {
			return;
		}
		if (source.getDirectEntity() == player && !source.is(DamageTypeTags.IS_PROJECTILE) && player.getMainHandItem().isEmpty()
			&& Rig.wears(player, Rig.Piece.ARMS) && RigMoves.get(player) == null) {
			punch(level, player, victim);
		}
		if (victim.isAlive() && tagged(player, victim)) {
			bonus = true;
			try {
				victim.setInvulnerableTime(0);
				victim.hurtServer(level, level.damageSources().indirectMagic(player, player), taken * TAG_BONUS);
			} finally {
				bonus = false;
			}
		}
	}

	// ---- powered punches ----------------------------------------------------------------------------------------------

	private static void punch(ServerLevel level, ServerPlayer player, LivingEntity victim) {
		RigMoves.start(player, RigMoves.PUNCH);
		Vec3 look = player.getLookAngle();
		// the flare: at the elbow, behind the fist
		Vec3 elbow = player.getEyePosition().add(0.0, -0.4, 0.0).subtract(look.scale(0.25));
		MagicFx.blast(level, elbow, PUNCH_FLARE, RigActives.FLAME_HOT);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.8F);
		HitStop.hit(player, victim, PUNCH_HOLD);
		player.push(look.x * PUNCH_STEP, 0.0, look.z * PUNCH_STEP);
		player.syncVelocity = true;
	}

	// ---- thruster dash ------------------------------------------------------------------------------------------------

	static void dash(ServerPlayer player) {
		if (!Rig.fullSet(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
			return;
		}
		State state = state(player);
		long now = player.level().getGameTime();
		if (now < state.dashReady) {
			return;
		}
		if (!Rig.spend(player, DASH_COST)) {
			RigMind.empty(player);
			return;
		}
		state.dashReady = now + DASH_COOLDOWN;
		RigMoves.start(player, RigMoves.DASH);
		RigActives.later(player, RigMoves.DASH_FIRE, () -> {
			ServerLevel level = player.level();
			Vec3 look = player.getLookAngle();
			Vec3 dir = player.onGround() ? new Vec3(look.x, 0.0, look.z) : look;
			dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : dir.normalize();
			double speed = DASH_SPEED * Rig.frame(player).airSpeed;
			player.setDeltaMovement(dir.scale(speed).add(0.0, player.onGround() ? 0.12 : 0.0, 0.0));
			player.syncVelocity = true;
			player.resetFallDistance();
			state.ramUntil = level.getGameTime() + DASH_RAM_TICKS;
			state.rammed.clear();
			Vec3 feet = player.position();
			MagicFx.ring(level, feet.add(0.0, 0.6, 0.0), dir, 1.2F, RigActives.FLAME_COLOR, 8);
			MagicFx.blast(level, feet.add(0.0, 0.3, 0.0).subtract(dir.scale(0.4)), 0.8F, RigActives.FLAME_HOT);
			MagicFx.stroke(level, feet.add(0.0, 0.9, 0.0), feet.add(0.0, 0.9, 0.0).add(dir.scale(speed * 4.0)), 0.5F, RigForgeBlock.FX_COLOR);
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.9F, 1.3F);
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
			Shakes.send(player, 0.7F, feet.subtract(dir));
		});
	}

	/** Every tick: a dash under way rams what it meets. */
	static void tick(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		if (state == null || state.ramUntil == 0L) {
			return;
		}
		ServerLevel level = player.level();
		if (level.getGameTime() > state.ramUntil || !Rig.fullSet(player)) {
			state.ramUntil = 0L;
			return;
		}
		Vec3 motion = player.getDeltaMovement();
		Vec3 dir = motion.lengthSqr() < 1.0E-4 ? player.getLookAngle() : motion.normalize();
		DamageSource source = level.damageSources().playerAttack(player);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DASH_REACH),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable() && !e.isAlliedTo(player))) {
			if (!state.rammed.add(target.getId())) {
				continue;
			}
			if (target.hurtServer(level, source, DASH_DAMAGE * Rig.frame(player).shotDamage)) {
				target.knockback(DASH_KNOCKBACK, -dir.x, -dir.z, source, DASH_DAMAGE);
				target.syncVelocity = true;
				MagicFx.blast(level, target.getBoundingBox().getCenter(), 0.6F, RigForgeBlock.FX_COLOR);
				HitStop.hit(player, target, DASH_HOLD);
				Shakes.send(player, 0.8F, target.position());
			}
		}
	}

	// ---- artillery barrage --------------------------------------------------------------------------------------------

	static void barrage(ServerPlayer player) {
		if (!Rig.fullSet(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
			return;
		}
		if (!RigActives.needs(player, Rig.MAX_LEVEL)) {
			return;
		}
		State state = state(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < state.barrageReady) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.barrage_wait", (state.barrageReady - now + 19) / 20));
			return;
		}
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(BARRAGE_RANGE)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.ANY, player));
		if (hit.getType() == HitResult.Type.MISS) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.barrage_no_mark"));
			return;
		}
		if (!Rig.spend(player, BARRAGE_COST)) {
			RigMind.empty(player);
			return;
		}
		state.barrageReady = now + BARRAGE_COOLDOWN;
		RigMoves.start(player, RigMoves.BARRAGE);
		Vec3 mark = hit.getLocation();
		RigFrame frame = Rig.frame(player);
		int count = BARRAGE_MISSILES + frame.extraMissiles * 2;
		float burst = BARRAGE_BURST + (frame == RigFrame.SIEGE ? RigFrame.GUN_BURST : 0.0F);
		// the mark, for everyone to read and get out of
		MagicFx.sigil(level, mark.add(0.0, 0.1, 0.0), (float)BARRAGE_SCATTER, RigActives.FLAME_COLOR, RigMoves.BARRAGE_FIRE + BARRAGE_FALL + count * BARRAGE_GAP);
		level.playSound(null, mark.x, mark.y, mark.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.6F);
		for (int i = 0; i < count; i++) {
			int n = i;
			// out of the pods, straight up, one after another
			RigActives.later(player, RigMoves.BARRAGE_FIRE + i * 2, () -> {
				Vec3 shoulder = player.getEyePosition().add(n % 2 == 0 ? 0.45 : -0.45, 0.3, 0.0);
				MagicFx.stroke(level, shoulder, shoulder.add(0.0, 9.0, 0.0), 0.25F, RigActives.FLAME_COLOR);
				level.sendParticles(MagicFx.miasma(0x55585E, 0.9F), shoulder.x, shoulder.y + 0.6, shoulder.z, 3, 0.1, 0.3, 0.1, 0.01);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.9F, 0.5F + n * 0.03F);
				Shakes.send(player, 0.45F);
			});
			// and down round the mark
			RigActives.later(player, RigMoves.BARRAGE_FIRE + BARRAGE_FALL + i * BARRAGE_GAP, () -> {
				double angle = level.getRandom().nextDouble() * Math.PI * 2.0, far = Math.sqrt(level.getRandom().nextDouble()) * BARRAGE_SCATTER;
				Vec3 at = mark.add(Math.cos(angle) * far, 0.0, Math.sin(angle) * far);
				Vec3 above = at.add((level.getRandom().nextDouble() - 0.5) * 6.0, BARRAGE_HEIGHT, (level.getRandom().nextDouble() - 0.5) * 6.0);
				BlockHitResult ground = level.clip(new ClipContext(at.add(0.0, 6.0, 0.0), at.add(0.0, -6.0, 0.0), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.ANY, player));
				Vec3 land = ground.getType() == HitResult.Type.MISS ? at : ground.getLocation();
				MagicFx.stroke(level, above, land, 0.3F, RigActives.FLAME_HOT);
				level.explode(player, land.x, land.y + 0.2, land.z, burst, Level.ExplosionInteraction.NONE);
			});
		}
	}
}
