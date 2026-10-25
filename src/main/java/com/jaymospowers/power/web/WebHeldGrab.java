package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.7) Living Armor's G, close up (Andy: "change Living armor G move to a close range grab, that holds the mob (up to
 * cow size) in the left hand and left clicking plays 1 of 6 attack animations. bite, stab, weapon arm."): with the
 * tendrils out, G within {@link #GRAB_REACH} of something living no bigger than a cow seizes it in the LEFT hand. It
 * hangs there, pinned at the hand, facing you, weightless, unable to move or hurt anyone, and comes along as you walk.
 * Left click mauls it: one of six attacks, never the same one twice running, each with its own damage, sound and mess
 * ({@link #BITE}: the head lunges and the teeth close on it, feeding you a little; {@link #STAB}: the right hand
 * sharpens into a spike; {@link #WEAPON}: the right arm forms the strain's weapon; {@link #SLAM}: it's smashed into the
 * ground in front of you, stunned; {@link #CRUSH}: both hands squeeze, through its armour; {@link #SKEWER}: the right
 * shoulder's tendril spears it). G again drops it, as does the hold running out ({@link #HELD_MAX_TICKS}), the tendrils
 * going in, or its death (the body falls where it was held). Anything bigger than a cow, or further off, G still takes
 * with the far tendril grab and hurls, as before. Everyone sees the hold and the attacks (the {@link Held} attachment is
 * synced; WebHeldGrabPose draws them).
 */
public final class WebHeldGrab {
	/** How close (blocks from the eyes, plus half its width) something must be for the hand to take it. */
	public static final double GRAB_REACH = 3.0;
	/** The biggest the hand takes: a bounding box no bigger than a cow's (0.9 x 1.4 x 0.9 blocks), with a little slack. */
	public static final double GRAB_MAX_VOLUME = EntityTypes.COW.getWidth() * EntityTypes.COW.getHeight() * EntityTypes.COW.getWidth() * 1.05;
	/** Longest the hand holds before it drops what it has. */
	public static final int HELD_MAX_TICKS = 400;
	/** Where it's held, from the eyes, in your size: this far ahead (plus half its width), this far to the left, this far down. */
	public static final double HOLD_FORWARD = 0.75, HOLD_LEFT = 0.55, HOLD_DROP = 0.3;
	/** Pinned: each tick it closes this share of the gap to the hand (at most {@code PIN_MAX_SPEED}); a gap past {@code PIN_SNAP} it's just put there. */
	static final double PIN_STEER = 0.8, PIN_MAX_SPEED = 1.6, PIN_SNAP = 1.5;
	/** Further than this from you (something moved it) and the hand has lost it. */
	static final double LOSE_RANGE = 6.0;

	/** The six attacks, by index. */
	public static final int BITE = 0, STAB = 1, WEAPON = 2, SLAM = 3, CRUSH = 4, SKEWER = 5, ATTACKS = 6;
	/** One attack after another no sooner than this (ticks); each is animated for {@code ANIM_TICKS} and lands {@code HIT_TICK} ticks in. */
	public static final int ATTACK_TICKS = 18, ANIM_TICKS = 18;
	public static final int[] HIT_TICK = {7, 6, 9, 10, 8, 7};
	/** The bite: heavy, and it feeds you this much health. */
	static final float BITE_DAMAGE = 9.0F, BITE_HEAL = 2.0F;
	/** The stab. */
	static final float STAB_DAMAGE = 7.0F;
	/** The weapon arm (the red strain's blades hit WebSymbioteStrains.RED_BLADE_DAMAGE harder, as the weapon arm's do). */
	static final float WEAPON_DAMAGE = 8.0F;
	/** The slam: into the ground this far ahead of you, stunned (Slowness) this long at this level. */
	static final float SLAM_DAMAGE = 6.0F;
	static final double SLAM_FORWARD = 1.4;
	static final int SLAM_STUN_TICKS = 30, SLAM_STUN_LEVEL = 2, SLAM_DUST = 18;
	/** The crush: lighter, but it ignores armour. */
	static final float CRUSH_DAMAGE = 5.5F;
	/** The skewer. */
	static final float SKEWER_DAMAGE = 10.0F;
	/** How hard your view shakes as an attack lands. */
	static final float SHAKE = 0.6F;

	/**
	 * What the left hand holds (an entity id), since when (game time), and the attack under way on it ({@link #BITE}...,
	 * -1 for none) and when it began. Synced to everyone, who draw the hold and the attack (WebHeldGrabPose).
	 */
	public record Held(int entity, long since, int attack, long attackStart) {
		public static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Held::entity, ByteBufCodecs.VAR_LONG, Held::since,
			ByteBufCodecs.VAR_INT, Held::attack, ByteBufCodecs.VAR_LONG, Held::attackStart, Held::new);
	}

	public static final AttachmentType<Held> HELD = AttachmentRegistry.create(JaymosPowers.id("web_held_grab"),
		builder -> builder.syncWith(Held.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** The last attack each player used, so the next is never the same one. */
	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private WebHeldGrab() {
	}

	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(WebNet.HeldSeize.TYPE, (payload, ctx) -> {
			if (!WebSymbiote.driving(ctx.player())) {
				seize(ctx.player(), payload.entityId());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(WebNet.HeldAttack.TYPE, (payload, ctx) -> {
			if (!WebSymbiote.driving(ctx.player())) {
				attack(ctx.player());
			}
		});
		// held up in the hand, it can't hurt anyone
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Entity attacker = source.getEntity();
			Entity direct = source.getDirectEntity();
			return !(attacker != null && heldByAnyone(attacker) || direct != null && direct != attacker && heldByAnyone(direct));
		});
	}

	/** The hand is holding something. Both sides. */
	public static boolean holding(Player player) {
		return player.getAttached(HELD) != null;
	}

	/** What the hand holds (null for nothing, or something gone). Both sides. */
	public static @Nullable LivingEntity heldEntity(Player player) {
		Held held = player.getAttached(HELD);
		return held != null && player.level().getEntity(held.entity()) instanceof LivingEntity living ? living : null;
	}

	/** Some player's hand (anyone's) holds this. Both sides. */
	public static boolean heldByAnyone(Entity entity) {
		for (Player other : entity.level().players()) {
			Held held = other.getAttached(HELD);
			if (held != null && held.entity() == entity.getId()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the hand would take this: with the tendrils out, something living the tendrils could grab, no bigger than a
	 * cow ({@link #GRAB_MAX_VOLUME}), within {@link #GRAB_REACH}, in nobody's grip. Both sides (the client decides between
	 * this and the far tendril grab before it sends).
	 */
	public static boolean canSeize(Player player, @Nullable Entity target) {
		if (!(target instanceof LivingEntity living) || !WebSymbiote.tendrilKeys(player) || !WebTendrils.grabbable(player, living)
			|| heldByAnyone(living) || WebTendrils.held(living)) {
			return false;
		}
		AABB box = living.getBoundingBox();
		if (box.getXsize() * box.getYsize() * box.getZsize() > GRAB_MAX_VOLUME) {
			return false;
		}
		return box.getCenter().distanceTo(player.getEyePosition()) <= GRAB_REACH + living.getBbWidth() * 0.5 + 0.5;
	}

	/** G: the hand takes entity {@code id} (or lets go, with -1 or whenever it already holds something). */
	static void seize(ServerPlayer player, int id) {
		if (holding(player) || id < 0) {
			drop(player);
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		Entity target = player.level().getEntity(id);
		if (!canSeize(player, target) || !(target instanceof LivingEntity living)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebTendrils.weightless(living, true);
		player.setAttached(HELD, new Held(living.getId(), now, -1, 0L));
		if (living instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 0.55F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.8F, 0.8F);
		com.jaymospowers.core.Shakes.send(player, 0.3F, living.position());
		WebSymbiote.narrate(player, "seize");
	}

	/** The hand opens: whatever it held falls where it is. */
	static void drop(ServerPlayer player) {
		Held held = player.getAttached(HELD);
		if (held == null) {
			return;
		}
		player.removeAttached(HELD);
		if (player.level().getEntity(held.entity()) instanceof LivingEntity living) {
			WebTendrils.weightless(living, false);
			living.setDeltaMovement(Vec3.ZERO);
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(Vec3.ZERO));
			}
		}
	}

	/** Left click: one of the six attacks on what's held, never the one before, no sooner than {@link #ATTACK_TICKS} after the last. */
	static void attack(ServerPlayer player) {
		Held held = player.getAttached(HELD);
		LivingEntity target = heldEntity(player);
		if (held == null || target == null || !target.isAlive() || !player.isAlive() || player.isSpectator() || !WebSymbiote.tendrilKeys(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (held.attack() >= 0 && now - held.attackStart() < ATTACK_TICKS || now - held.since() < 2) {
			return;
		}
		int last = LAST.getOrDefault(player.getUUID(), -1);
		int next = player.getRandom().nextInt(last < 0 ? ATTACKS : ATTACKS - 1);
		if (last >= 0 && next >= last) {
			next++;
		}
		LAST.put(player.getUUID(), next);
		player.setAttached(HELD, new Held(held.entity(), held.since(), next, now));
		// the wind-up, heard by everyone
		switch (next) {
			case BITE -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.9F, 0.5F);
			case STAB, WEAPON -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.6F);
			case SLAM -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.9F, 0.5F);
			case CRUSH -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0F, 0.5F);
			default -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.5F);
		}
	}

	/** Where the hand holds it right now (the middle of its box): at the left hand, or down on the ground ahead through a slam. */
	static Vec3 point(ServerPlayer player, LivingEntity target, Held held, long now) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = look.horizontal();
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		double scale = player.getScale();
		Vec3 hand = player.getEyePosition().add(flat.scale(HOLD_FORWARD * scale + target.getBbWidth() * 0.5)).subtract(right.scale(HOLD_LEFT * scale))
			.add(0.0, -HOLD_DROP * scale, 0.0);
		if (held.attack() == SLAM) {
			// down onto the ground in front, held there a moment, then hauled back up
			long t = now - held.attackStart();
			int hit = HIT_TICK[SLAM];
			double down = t < hit ? Mth.clamp((t - hit * 0.5) / (hit * 0.5), 0.0, 1.0) : t < hit + 4 ? 1.0 : Mth.clamp(1.0 - (t - hit - 4) / 4.0, 0.0, 1.0);
			down = down * down * (3.0 - 2.0 * down);
			Vec3 ground = player.position().add(flat.scale(SLAM_FORWARD * scale + target.getBbWidth() * 0.5)).add(0.0, target.getBbHeight() * 0.5, 0.0);
			return hand.lerp(ground, down);
		}
		return hand;
	}

	/** Every tick, per web slinger (from {@link WebTendrils#tick}): what's held hangs at the hand, facing you; the attacks land. */
	static void tick(ServerPlayer player) {
		Held held = player.getAttached(HELD);
		if (held == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		LivingEntity target = heldEntity(player);
		if (!player.isAlive() || player.isSpectator() || !WebSymbiote.tendrilKeys(player) || target == null || !target.isAlive()
			|| target.level() != level || target.distanceTo(player) > LOSE_RANGE || now - held.since() > HELD_MAX_TICKS) {
			// (dead, it falls where it was held)
			drop(player);
			return;
		}
		if (held.attack() >= 0 && now - held.attackStart() == HIT_TICK[Mth.clamp(held.attack(), 0, ATTACKS - 1)]) {
			land(player, target, held.attack());
			if (!target.isAlive()) {
				drop(player);
				return;
			}
		}
		Vec3 point = point(player, target, held, now);
		Vec3 to = point.subtract(target.getBoundingBox().getCenter());
		if (to.length() > PIN_SNAP && !(target instanceof Player)) {
			// (a player's motion is their own client's: they're only ever steered)
			target.setPos(point.x, point.y - target.getBbHeight() * 0.5, point.z);
			target.setDeltaMovement(Vec3.ZERO);
		} else {
			Vec3 v = to.scale(PIN_STEER);
			double speed = v.length();
			if (speed > PIN_MAX_SPEED) {
				v = v.scale(PIN_MAX_SPEED / speed);
			}
			target.setDeltaMovement(v);
		}
		target.needsSync = true;
		target.syncVelocity = true;
		target.resetFallDistance();
		// facing you, held still
		Vec3 face = player.getEyePosition().subtract(target.getBoundingBox().getCenter());
		float yaw = (float)(Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
		target.setYRot(yaw);
		target.setYBodyRot(yaw);
		target.setYHeadRot(yaw);
		target.setXRot(0.0F);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (target instanceof ServerPlayer other && now % 2 == 0) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(target.getDeltaMovement()));
		}
	}

	/** An attack lands on what's held: its damage, sound and mess. */
	private static void land(ServerPlayer player, LivingEntity target, int attack) {
		ServerLevel level = player.level();
		Vec3 at = target.getBoundingBox().getCenter();
		switch (attack) {
			case BITE -> {
				target.hurtServer(level, level.damageSources().playerAttack(player), BITE_DAMAGE);
				player.heal(BITE_HEAL);
				blood(level, at, 24, target.getBbWidth() * 0.5);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.1F, 0.6F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 0.5F, 0.8F);
			}
			case STAB -> {
				target.hurtServer(level, level.damageSources().playerAttack(player), STAB_DAMAGE);
				blood(level, at, 14, 0.2);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.15);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.9F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 0.8F);
			}
			case WEAPON -> {
				// TODO WebMorph: a public strike entry point that takes a held entity (something like
				// WebMorph.strikeHeld(player, target)) would form the strain's own weapon here and hit with its damage; until it
				// exists this is a heavy blow with a blade formed on the arm (WebHeldGrabPose, WebTendrilLayer.heldGrab).
				boolean red = WebSymbioteStrains.of(player) == WebSymbioteStrain.RED;
				target.hurtServer(level, level.damageSources().playerAttack(player), WEAPON_DAMAGE * (red ? WebSymbioteStrains.RED_BLADE_DAMAGE : 1.0F));
				blood(level, at, 18, 0.3);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.2);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.5F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.8F, 0.7F);
			}
			case SLAM -> {
				target.hurtServer(level, level.damageSources().playerAttack(player), SLAM_DAMAGE);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLAM_STUN_TICKS, SLAM_STUN_LEVEL), player);
				KitchenCompat.jolt(target);
				Vec3 foot = new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ());
				BlockPos under = BlockPos.containing(foot.x, foot.y - 0.1, foot.z);
				BlockState state = level.getBlockState(under);
				if (!state.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), foot.x, foot.y + 0.1, foot.z, SLAM_DUST,
						target.getBbWidth() * 0.6, 0.1, target.getBbWidth() * 0.6, 0.12);
					level.playSound(null, foot.x, foot.y, foot.z, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.5F);
				}
				level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 0.5F);
			}
			case CRUSH -> {
				// straight through the armour
				target.hurtServer(level, level.damageSources().indirectMagic(player, player), CRUSH_DAMAGE);
				com.jaymospowers.core.Crushing.mark(target);
				blood(level, at, 10, 0.25);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.4F);
			}
			default -> {
				target.hurtServer(level, level.damageSources().playerAttack(player), SKEWER_DAMAGE);
				blood(level, at, 20, 0.3);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.2);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.9F, 1.1F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
		}
		com.jaymospowers.core.Shakes.send(player, SHAKE, at);
		WebSymbiote.narrate(player, "maul");
	}

	private static void blood(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(WebTendrilCombos.BLOOD, 1.2F), at.x, at.y, at.z, count, spread, spread, spread, 0.0);
	}

	static void forget(ServerPlayer player) {
		LAST.remove(player.getUUID());
	}
}
