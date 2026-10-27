package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Corruption. Every dark master's lesson ({@link ZFighterMasters}) fills a third of the Z Fighter's corruption meter
 * ({@link Fate}); the third corrupts him. A corrupted Z Fighter wears a crimson mark on his forehead in every form
 * (drawn on everyone's screen) and hits harder: {@link #POWER} more fists and ki power.
 * <p>
 * And he has <b>Corrupted mode</b> (sneak+C in base form; again to leave, or C to go Super instead): Super 1's fists,
 * speed and ki power without its ki drain, paid for in blood ({@link #DRAIN} health a second, down to one heart, when
 * it ends), with life steal on every hit ({@link #LIFE_STEAL} of the damage). He gets {@link #DODGE_BONUS} golden feathers
 * ({@code stamina.Feathers}), and every dodge is a blink instead: towards his target, or behind it if he's already close.
 */
public final class ZFighterCorruption {
	/** A dark lesson fills this much of the meter (three corrupt). */
	static final int LESSON_CORRUPTION = Fate.MAX_CORRUPTION / 3 + 1;
	/** Corrupted, in every form: fists and ki power this much stronger. */
	static final double POWER = 0.05;
	/** Corrupted mode: health lost a second, the share of damage dealt healed back, and when it gives out. */
	static final float DRAIN = 1.0F;
	static final float LIFE_STEAL = 0.35F;
	static final float LOWEST = 2.0F;
	/** Corrupted mode's golden feathers (half-feathers: two more dodges). */
	static final int DODGE_BONUS = 12;
	/** A dodge's blink: how far towards a target (stopping this short), behind one within this, or ahead with none. */
	static final double BLINK = 8.0;
	static final double BLINK_SHORT = 1.8;
	static final double BEHIND_WITHIN = 5.0;
	static final double BEHIND = 1.6;
	static final double AHEAD = 6.0;
	static final double SEEK = 24.0;
	static final int MARK_COLOR = 0xB01030;

	/** Corrupted mode is on, for everyone's client (the mark burns, the aura). */
	public static final AttachmentType<Boolean> MODE = AttachmentRegistry.create(JaymosPowers.id("zfighter_corrupted_mode"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final Identifier FIST_ID = JaymosPowers.id("zfighter_corrupted_fists");
	private static final Identifier SPEED_ID = JaymosPowers.id("zfighter_corrupted_speed");

	private ZFighterCorruption() {
	}

	static void init() {
		// life steal: whatever he hurts in Corrupted mode heals him
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && source.getEntity() instanceof ServerPlayer player && entity != player && mode(player)) {
				player.heal(damageTaken * LIFE_STEAL);
				player.level().sendParticles(new DustParticleOptions(MARK_COLOR, 1.0F), entity.getX(), entity.getY() + entity.getBbHeight() * 0.6,
					entity.getZ(), 6, 0.25, 0.3, 0.25, 0.0);
			}
		});
		// (0.15.5) the pack's own dodge: golden feathers in Corrupted mode, and every dodge a blink
		com.jaymospowers.stamina.Feathers.addBonus(player -> mode(player) ? DODGE_BONUS : 0);
		com.jaymospowers.stamina.Dodge.onDodge((player, direction) -> {
			if (mode(player)) {
				blink(player);
			}
		});
	}

	public static boolean corrupted(net.minecraft.world.entity.player.Player player) {
		return Fate.isCorrupted(player) && Powers.has(player, ZFighterPower.INSTANCE);
	}

	static boolean mode(ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttached(MODE));
	}

	/** A dark master's lesson: a third of the way to corruption. */
	static void darkLesson(ServerPlayer player) {
		boolean was = Fate.isCorrupted(player);
		Fate.addCorruption(player, LESSON_CORRUPTION);
		if (!was && Fate.isCorrupted(player)) {
			ZFighterPower.refresh(player, ZFighterPower.live(player));
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_corrupted").withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** Sneak+C in base form: into Corrupted mode, or out of it. False if he isn't corrupted (the key does what it did). */
	static boolean toggle(ServerPlayer player, ZFighterPower.Live live) {
		// (0.17.50) Ego: sneak+C again from Corrupted mode (or on a dark lesson's corruption) opens the dark mirror
		if (ZFighterEgo.toggle(player, live)) {
			return true;
		}
		if (!corrupted(player)) {
			return false;
		}
		if (mode(player)) {
			end(player, live);
			return true;
		}
		if (player.getHealth() <= LOWEST + 1.0F) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_corrupted_weak").withStyle(ChatFormatting.DARK_RED));
			return true;
		}
		player.setAttached(MODE, true);
		ZFighterPower.refresh(player, live);
		ServerLevel level = player.level();
		level.sendParticles(new DustParticleOptions(MARK_COLOR, 2.0F), true, false, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.8, 1.0,
			0.8, 0.1);
		level.sendParticles(ParticleTypes.SQUID_INK, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.3F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_corrupted_on").withStyle(ChatFormatting.DARK_RED));
		return true;
	}

	/** Out of Corrupted mode (a key, Super, the Great Ape, blood run low, or the power gone). */
	static void end(ServerPlayer player, ZFighterPower.Live live) {
		if (!mode(player)) {
			return;
		}
		player.removeAttached(MODE);
		ZFighterPower.refresh(player, live);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.6F);
	}

	/** The power is gone: no mode, no corrupted attributes. */
	static void clear(ServerPlayer player) {
		player.removeAttached(MODE);
		AttributeInstance fists = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		if (fists != null) {
			fists.removeModifier(FIST_ID);
		}
		AttributeInstance speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_ID);
		}
	}

	/** Every tick: the mode bleeds him and burns round him. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (!mode(player)) {
			return;
		}
		if (!corrupted(player) || live.superOn() || ZFighterLineage.ape(player)) {
			end(player, live);
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now % 20 == 0 && !player.isCreative()) {
			if (player.getHealth() - DRAIN < LOWEST) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_corrupted_spent").withStyle(ChatFormatting.DARK_RED));
				end(player, live);
				return;
			}
			player.setHealth(player.getHealth() - DRAIN);
		}
		if (now % 3 == 0) {
			level.sendParticles(new DustParticleOptions(now % 6 == 0 ? MARK_COLOR : 0x200008, 1.5F), true, false, player.getX(),
				player.getY() + 0.9, player.getZ(), 3, 0.35, 0.7, 0.35, 0.0);
		}
	}

	/** What corruption adds to his fists and speed: refreshed with the rest of his attributes. */
	static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		boolean corrupted = corrupted(player);
		boolean mode = corrupted && mode(player);
		ZFighterPower.set(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, FIST_ID,
			(corrupted ? POWER : 0.0) + (mode ? ZFighterPower.FORM_FIST[1] : 0.0), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, SPEED_ID, mode ? ZFighterPower.FORM_SPEED[1] : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Ki power: corrupted adds {@link #POWER}; Corrupted mode counts as Super. */
	static float kiFactor(ServerPlayer player, ZFighterPower.Live live) {
		if (!corrupted(player)) {
			return 1.0F;
		}
		float factor = 1.0F + (float)POWER;
		if (mode(player) && !live.superOn()) {
			factor *= ZFighterPower.FORM_KI[1];
		}
		return factor;
	}

	// ---------------------------------------------------------------- the dodge's blink

	/** The blink: towards his target, behind it if close, or ahead with none; only to somewhere he fits. */
	static void blink(ServerPlayer player) {
		ServerLevel level = player.level();
		LivingEntity target = target(player);
		Vec3 from = player.position();
		Vec3 to;
		float yaw = player.getYRot();
		if (target != null) {
			Vec3 at = target.position();
			Vec3 flat = new Vec3(at.x - from.x, 0.0, at.z - from.z);
			double distance = flat.length();
			if (distance <= BEHIND_WITHIN) {
				// in behind him, facing his back
				Vec3 back = target.getLookAngle().multiply(1.0, 0.0, 1.0);
				back = back.lengthSqr() < 1.0E-4 ? flat.normalize() : back.normalize();
				to = at.subtract(back.scale(BEHIND + target.getBbWidth() * 0.5));
			} else {
				to = from.add(flat.normalize().scale(Math.min(BLINK, distance - BLINK_SHORT))).add(0.0, at.y - from.y > 0 ? Math.min(at.y - from.y, 2.0) : 0.0, 0.0);
			}
			Vec3 face = at.subtract(to);
			yaw = (float)(Math.atan2(face.z, face.x) * (180.0 / Math.PI)) - 90.0F;
		} else {
			Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			to = look.lengthSqr() < 1.0E-4 ? from : from.add(look.normalize().scale(AHEAD));
		}
		Vec3 safe = fit(player, to);
		if (safe == null) {
			return;
		}
		level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y + 1.0, from.z, 20, 0.3, 0.6, 0.3, 0.05);
		level.sendParticles(new DustParticleOptions(MARK_COLOR, 1.5F), from.x, from.y + 1.0, from.z, 15, 0.3, 0.6, 0.3, 0.0);
		player.teleportTo(level, safe.x, safe.y, safe.z, Set.of(), yaw, player.getXRot(), false);
		player.setDeltaMovement(Vec3.ZERO);
		player.needsSync = true;
		player.resetFallDistance();
		level.sendParticles(new DustParticleOptions(MARK_COLOR, 1.5F), safe.x, safe.y + 1.0, safe.z, 20, 0.3, 0.6, 0.3, 0.0);
		level.playSound(null, safe.x, safe.y, safe.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	/** Whoever he's aiming at, or the last thing he hit, or the nearest thing after him. */
	private static @Nullable LivingEntity target(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		EntityHitResult aimed = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(SEEK)),
			player.getBoundingBox().expandTowards(look.scale(SEEK)).inflate(1.5), e -> e instanceof LivingEntity l && l.isAlive() && e != player
				&& !(e instanceof ArmorStand) && !e.isSpectator(), SEEK * SEEK);
		if (aimed != null && aimed.getEntity() instanceof LivingEntity living) {
			return living;
		}
		LivingEntity last = player.getLastHurtMob();
		if (last != null && last.isAlive() && last.level() == player.level() && last.distanceToSqr(player) <= SEEK * SEEK) {
			return last;
		}
		AABB around = player.getBoundingBox().inflate(16.0);
		return player.level().getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && m.getTarget() == player).stream()
			.min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player))).orElse(null);
	}

	/** The spot, or up to two blocks above it, where he fits; null if nowhere. */
	private static @Nullable Vec3 fit(Entity player, Vec3 spot) {
		for (int up = 0; up <= 2; up++) {
			Vec3 at = spot.add(0.0, up, 0.0);
			AABB box = player.getBoundingBox().move(at.subtract(player.position()));
			if (player.level().noCollision(player, box)) {
				return at;
			}
		}
		return null;
	}
}
