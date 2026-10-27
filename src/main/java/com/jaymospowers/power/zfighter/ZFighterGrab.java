package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.42, Andy's NOEA picks) Grab and throw: sneak and left click, bare-handed, at a creature in plain reach and he
 * takes hold of it, held out in front of him, unable to act ({@link #HOLD_MAX_TICKS} at most). Let go of sneak, or left
 * click again ({@link Throw}), and he throws it the way he looks, harder the stronger his fists; it slams into whatever it
 * meets ({@link ZFighterImpact}). The rush attacks reuse the hold ({@link #seize} with {@code scripted}).
 */
public final class ZFighterGrab {
	/** Longest he holds on. */
	static final int HOLD_MAX_TICKS = 60;
	/** Biggest he can lift: a bounding box volume (an iron golem is about 5). */
	static final double MAX_VOLUME = 6.0;
	static final double GRAB_REACH = 4.0;
	/** Where it hangs: this far ahead of the eyes (plus half its width) and this far down to its middle. */
	static final double HOLD_FORWARD = 1.0, HOLD_DROP = 0.55;
	/** A held player is steered: this share of the gap a tick, at most this fast. */
	static final double STEER = 0.8, STEER_MAX = 1.6;
	/** The throw: speed is base plus this per point of fists, at most {@link #THROW_MAX}; a little lift; the slam hurts this share of his fists. */
	static final double THROW_BASE = 0.9, THROW_PER_FIST = 0.12, THROW_MAX = 2.4, THROW_LIFT = 0.12;
	static final float SLAM_SHARE = 0.9F;
	static final float THROW_POISE = 8.0F;
	/** Ticks after a throw before he can grab again. */
	static final int REGRAB_TICKS = 20;

	/** Client to server: left click while holding something: throw it. */
	public record Throw() implements CustomPacketPayload {
		public static final Type<Throw> TYPE = new Type<>(JaymosPowers.id("zfighter_throw"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Throw> CODEC = StreamCodec.unit(new Throw());

		@Override
		public Type<Throw> type() {
			return TYPE;
		}
	}

	/** What he holds (its entity id), for his own client (it takes his left click for the throw). Not saved. */
	public static final AttachmentType<Integer> HELD = AttachmentRegistry.create(JaymosPowers.id("zfighter_grab"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private record Hold(UUID foe, long since, boolean scripted) {
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();
	private static final Set<UUID> HELD_FOES = new HashSet<>();
	private static final Map<UUID, Long> REGRAB = new HashMap<>();

	private ZFighterGrab() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Throw.TYPE, Throw.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Throw.TYPE, (payload, ctx) -> {
			if (!HOLDS.getOrDefault(ctx.player().getUUID(), new Hold(null, 0L, true)).scripted()) {
				throwHeld(ctx.player(), 1.0F);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(entity instanceof LivingEntity foe)) {
				return InteractionResult.PASS;
			}
			if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty() || !Powers.has(sp, ZFighterPower.INSTANCE) || !canGrab(sp, foe)) {
				return InteractionResult.PASS;
			}
			seize(sp, foe, false);
			return InteractionResult.SUCCESS;
		});
		// held out in front of him, it can't hurt anyone
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			var attacker = source.getEntity();
			var direct = source.getDirectEntity();
			return !(attacker != null && HELD_FOES.contains(attacker.getUUID()) || direct != null && HELD_FOES.contains(direct.getUUID()));
		});
	}

	public static boolean holding(Player player) {
		return player.hasAttached(HELD);
	}

	static boolean canGrab(ServerPlayer player, LivingEntity foe) {
		if (foe == player || !foe.isAlive() || foe.isSpectator() || foe instanceof ArmorStand || foe.isPassenger() || foe.isVehicle()
			|| HELD_FOES.contains(foe.getUUID()) || holding(player) || ZFighterLineage.ape(player) || ZFighterRush.active(player)
			|| ZFighterPoise.stunned(player) || foe instanceof Player other && other.isCreative()
			// the downed and crippled are the finishers' (sneak + left click held), not a grab's
			|| com.jaymospowers.core.finisher.Finishers.helpless(foe)) {
			return false;
		}
		long now = player.level().getGameTime();
		if (now < REGRAB.getOrDefault(player.getUUID(), 0L) || player.distanceTo(foe) > GRAB_REACH + foe.getBbWidth() * 0.5) {
			return false;
		}
		var box = foe.getBoundingBox();
		return box.getXsize() * box.getYsize() * box.getZsize() <= MAX_VOLUME;
	}

	/** He takes hold of {@code foe}. Scripted (a rush's grab): no sneak needed, no left click throws, no time limit but the rush's. */
	static void seize(ServerPlayer player, LivingEntity foe, boolean scripted) {
		ServerLevel level = player.level();
		HOLDS.put(player.getUUID(), new Hold(foe.getUUID(), level.getGameTime(), scripted));
		HELD_FOES.add(foe.getUUID());
		player.setAttached(HELD, foe.getId());
		lock(foe);
		ZFighterCombo.stop(player);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 0.7F);
		Shakes.send(player, 0.25F, foe.position());
	}

	static @Nullable LivingEntity foe(ServerPlayer player) {
		Hold hold = HOLDS.get(player.getUUID());
		return hold != null && player.level().getEntity(hold.foe()) instanceof LivingEntity living ? living : null;
	}

	/** Every tick, per Z Fighter: the foe hangs in front of him; sneak let go or the time out ends it. */
	static void tick(ServerPlayer player) {
		Hold hold = HOLDS.get(player.getUUID());
		if (hold == null) {
			return;
		}
		ServerLevel level = player.level();
		LivingEntity foe = foe(player);
		boolean fine = foe != null && foe.isAlive() && foe.level() == level && player.isAlive() && !player.isSpectator()
			&& player.distanceTo(foe) < 8.0 && Powers.has(player, ZFighterPower.INSTANCE);
		if (!fine) {
			release(player);
			return;
		}
		if (!hold.scripted()) {
			if (level.getGameTime() - hold.since() > HOLD_MAX_TICKS || !player.getMainHandItem().isEmpty()) {
				release(player);
				return;
			}
			if (!player.isShiftKeyDown() && level.getGameTime() - hold.since() > 2) {
				throwHeld(player, 1.0F);
				return;
			}
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 centre = player.getEyePosition().add(flat.scale(HOLD_FORWARD * player.getScale() + foe.getBbWidth() * 0.5)).add(0.0, -HOLD_DROP, 0.0);
		if (foe instanceof ServerPlayer) {
			// a player's motion is their own client's: steered, not set
			Vec3 v = centre.subtract(foe.getBoundingBox().getCenter()).scale(STEER);
			double speed = v.length();
			foe.setDeltaMovement(speed > STEER_MAX ? v.scale(STEER_MAX / speed) : v);
		} else {
			foe.setPos(centre.x, centre.y - foe.getBbHeight() * 0.5, centre.z);
			foe.setDeltaMovement(Vec3.ZERO);
		}
		foe.syncVelocity = true;
		foe.needsSync = true;
		foe.resetFallDistance();
		if (foe instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		}
		Vec3 face = player.getEyePosition().subtract(foe.getBoundingBox().getCenter());
		float yaw = (float)(Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
		foe.setYRot(yaw);
		foe.setYHeadRot(yaw);
	}

	/** He lets go: it falls where it is. */
	static void release(ServerPlayer player) {
		Hold hold = HOLDS.remove(player.getUUID());
		player.removeAttached(HELD);
		if (hold != null) {
			HELD_FOES.remove(hold.foe());
			REGRAB.put(player.getUUID(), player.level().getGameTime() + REGRAB_TICKS);
			if (player.level().getEntity(hold.foe()) instanceof LivingEntity foe) {
				foe.setDeltaMovement(Vec3.ZERO);
				foe.syncVelocity = true;
			}
		}
	}

	/** He throws what he holds the way he looks, {@code power} times as hard (the rushes' throws are harder); it slams into what it meets. */
	static void throwHeld(ServerPlayer player, float power) {
		LivingEntity foe = foe(player);
		if (foe == null) {
			release(player);
			return;
		}
		ServerLevel level = player.level();
		double fists = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		release(player);
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity locked = ZFighterLock.target(player);
		if (locked != null && locked != foe) {
			look = ZFighterLock.aim(player);
		}
		double speed = Math.min(THROW_MAX, THROW_BASE + fists * THROW_PER_FIST) * power;
		foe.setDeltaMovement(look.x * speed, look.y * speed + THROW_LIFT, look.z * speed);
		foe.syncVelocity = true;
		foe.needsSync = true;
		ZFighterImpact.launched(foe, player, (float)fists * SLAM_SHARE * power);
		ZFighterPoise.wear(foe, THROW_POISE * power);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Vec3 at = foe.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CLOUD, true, false, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(ParticleTypes.CRIT, true, false, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.4);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 1.2F);
		Shakes.send(player, 0.4F, at);
	}

	/** Pins {@code foe} where it stands this tick (a rush's lock): its sliding stopped, slowed to nothing, its mind off him. */
	static void lock(LivingEntity foe) {
		if (foe instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		}
		if (!(foe instanceof ServerPlayer)) {
			Vec3 motion = foe.getDeltaMovement();
			foe.setDeltaMovement(0.0, motion.y, 0.0);
			foe.syncVelocity = true;
		}
		if (foe.tickCount % 2 == 0) {
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 9, false, false));
			foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5, 9, false, false));
		}
	}

	static void drop(ServerPlayer player) {
		release(player);
		REGRAB.remove(player.getUUID());
	}
}
