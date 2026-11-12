package com.jaymospowers.power.xenoform.minds;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.XenoMinds;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Two things Wisp does on top of its Phase:
 * <ul>
 * <li>The ghost hand. While Phase is held, V phases one hand instead of wailing: the next blow you land with your fist passes
 * into the chest. It ignores armour, hits for {@link #HAND_DAMAGE} and a share of the victim's health, and leaves a cold shudder
 * (slowness, weakness, a freezing breath). The hand stays out for {@link #HAND_TICKS} ticks or until it strikes.</li>
 * <li>Thinning out. Z tapped thins it for {@link #THIN_TICKS} ticks: it takes {@link #THIN_SHARE} of the damage it would, and when
 * struck it sinks through the floor and rises behind whatever hit it.</li>
 * </ul>
 */
public final class WispHand {
	// ---- tuning ----
	public static final int HAND_TICKS = 200, HAND_COOLDOWN = 100;
	public static final float HAND_DAMAGE = 6.0F, HAND_SHARE = 0.15F, HAND_CAP = 14.0F;
	public static final int SHUDDER_TICKS = 100, FREEZE_TICKS = 140;
	public static final int THIN_TICKS = 200, THIN_COOLDOWN = 200, REAPPEAR_COOLDOWN = 80;
	public static final float THIN_SHARE = 0.5F;
	public static final double REAPPEAR_BEHIND = 1.8;
	public static final int COLOR = 0xB8E8F8;

	private static final class State {
		long handUntil, thinUntil;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static boolean striking, repeating;

	private WispHand() {
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	/** True while the hand's own blow is landing (a phasing Wisp's hits are otherwise turned to nothing). */
	public static boolean striking() {
		return striking;
	}

	public static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}

	/** V while Phase is held: put a hand through. */
	public static void arm(ServerPlayer player) {
		if (!XenoMinds.ready(player, "wisp_hand")) {
			return;
		}
		State s = state(player);
		s.handUntil = player.level().getGameTime() + HAND_TICKS;
		MindsKit.sound(player, SoundEvents.PHANTOM_BITE, 0.7F, 1.9F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.wisp.hand").withStyle(ChatFormatting.AQUA));
	}

	private static boolean armed(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.handUntil > player.level().getGameTime();
	}

	/** {@code attacker} struck {@code victim} with the hand out: true if it went through the chest (the blow itself is cancelled). */
	public static boolean strike(ServerPlayer attacker, LivingEntity victim, float amount) {
		if (striking || !armed(attacker) || !MindsKit.fair(attacker, victim)) {
			return false;
		}
		State s = state(attacker);
		s.handUntil = 0L;
		ServerLevel level = attacker.level();
		float damage = HAND_DAMAGE + Math.min(HAND_CAP, victim.getMaxHealth() * HAND_SHARE);
		striking = true;
		try {
			victim.setInvulnerableTime(0);
			victim.hurtServer(level, attacker.damageSources().indirectMagic(attacker, attacker), damage);
		} finally {
			striking = false;
		}
		if (victim.isAlive()) {
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHUDDER_TICKS, 2), attacker);
			victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SHUDDER_TICKS, 1), attacker);
			victim.setTicksFrozen(victim.getTicksRequiredToFreeze() + FREEZE_TICKS);
			if (victim instanceof ServerPlayer shaken) {
				MindsKit.vignette(shaken, COLOR, 60);
			}
		}
		Vec3 chest = victim.position().add(0.0, victim.getBbHeight() * 0.65, 0.0);
		MagicFx.blast(level, chest, 1.1F, COLOR);
		MagicFx.ring(level, chest, attacker.getLookAngle(), 1.0F, COLOR, 10);
		MagicFx.beam(level, attacker.getEyePosition().add(0.0, -0.4, 0.0), chest, 0.12F, COLOR, 5);
		MindsKit.soundAt(level, chest, SoundEvents.PHANTOM_AMBIENT, 1.0F, 0.6F);
		MindsKit.soundAt(level, chest, SoundEvents.PLAYER_HURT_FREEZE, 1.2F, 0.8F);
		XenoMinds.cool(attacker, "wisp_hand", HAND_COOLDOWN);
		return true;
	}

	/** Z tapped: thin out, or fill back in. */
	public static void toggleThin(ServerPlayer player) {
		State s = state(player);
		long now = player.level().getGameTime();
		if (s.thinUntil > now) {
			endThin(player, s);
			return;
		}
		if (!XenoMinds.ready(player, "wisp_thin")) {
			return;
		}
		s.thinUntil = now + THIN_TICKS;
		MagicFx.pool(player.level(), player.position().add(0.0, 0.05, 0.0), 1.3F, COLOR, 16);
		MindsKit.sound(player, SoundEvents.PHANTOM_SWOOP, 0.8F, 1.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.wisp.thin").withStyle(ChatFormatting.AQUA));
	}

	public static boolean thin(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.thinUntil > player.level().getGameTime();
	}

	private static void endThin(ServerPlayer player, State s) {
		s.thinUntil = 0L;
		XenoMinds.cool(player, "wisp_thin", THIN_COOLDOWN);
		MagicFx.pool(player.level(), player.position().add(0.0, 0.05, 0.0), 1.0F, COLOR, 10);
		MindsKit.sound(player, SoundEvents.PHANTOM_BITE, 0.6F, 1.6F);
	}

	/** The Wisp is about to take a blow. False: it is turned away here (a thinned one takes the smaller blow itself). */
	public static boolean struck(ServerPlayer player, DamageSource source, float amount) {
		if (repeating || !thin(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		ServerLevel level = player.level();
		repeating = true;
		try {
			player.hurtServer(level, source, amount * THIN_SHARE);
		} finally {
			repeating = false;
		}
		if (player.isAlive() && source.getEntity() instanceof LivingEntity attacker && attacker != player && attacker.isAlive() && XenoMinds.ready(player, "wisp_sink")) {
			sink(player, attacker);
		}
		return false;
	}

	/** Down through the floor, and up behind {@code attacker}. */
	private static void sink(ServerPlayer player, LivingEntity attacker) {
		ServerLevel level = player.level();
		Vec3 look = attacker.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 behind = attacker.position().subtract(flat.scale(REAPPEAR_BEHIND));
		Vec3 shift = behind.subtract(player.position());
		AABB box = player.getBoundingBox().move(shift);
		Vec3 spot = null;
		for (double dy : new double[] {0.0, 1.0, -1.0}) {
			if (level.noCollision(null, box.move(0.0, dy, 0.0))) {
				spot = behind.add(0.0, dy, 0.0);
				break;
			}
		}
		if (spot == null) {
			return;
		}
		Vec3 here = player.position();
		MagicFx.pool(level, here.add(0.0, 0.05, 0.0), 1.4F, COLOR, 14, 4);
		MindsKit.soundAt(level, here, SoundEvents.PHANTOM_SWOOP, 1.0F, 1.5F);
		// it rises facing the one it came up behind
		float yaw = Mth.wrapDegrees((float)(Math.atan2(-(attacker.getX() - spot.x), attacker.getZ() - spot.z) * Mth.RAD_TO_DEG));
		player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F, false);
		player.resetFallDistance();
		MagicFx.pool(level, spot.add(0.0, 0.05, 0.0), 1.4F, COLOR, 14);
		MagicFx.blast(level, spot.add(0.0, 1.0, 0.0), 1.0F, COLOR);
		MindsKit.soundAt(level, spot, SoundEvents.PHANTOM_BITE, 1.0F, 1.7F);
		XenoMinds.cool(player, "wisp_sink", REAPPEAR_COOLDOWN);
	}

	/** Every tick as Wisp. */
	public static void tick(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		if (s == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (s.handUntil > now && player.tickCount % 4 == 0) {
			Vec3 hand = player.position().add(MindsKit.flat(player).scale(0.5)).add(0.0, player.getBbHeight() * 0.6, 0.0);
			MagicFx.orb(level, hand, 0.18F, COLOR, 5);
		}
		if (s.thinUntil > 0L) {
			if (s.thinUntil <= now) {
				endThin(player, s);
			} else if (player.tickCount % 6 == 0) {
				level.sendParticles(MagicFx.mote(COLOR, 0.5F), player.getX(), player.getY() + 0.9, player.getZ(), 2, 0.3, 0.5, 0.3, 0.0);
			}
		}
	}
}
