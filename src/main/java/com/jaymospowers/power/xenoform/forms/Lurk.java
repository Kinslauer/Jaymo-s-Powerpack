package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.HunterHits;
import com.jaymospowers.power.xenoform.LurkBlend;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoStalker;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.96, Xeno-Band patch 4) Lurk, the stalker's own shape, scanned from the skin it sheds ({@link XenoStalker}): tall,
 * thin, long-armed, a face that's mostly eyes. Hidden, like Nadir (it isn't counted toward a complete band), but once
 * found it's the band's to keep. 26 health, fists of 5, quick.
 * <ul>
 * <li>R, Watch: mark the creature you look at, as far as {@link #WATCH_REACH}: it glows (for everyone) and loses track
 * of you.</li>
 * <li>G, Behind: step out of the dark behind the watched creature (or the one you look at, nearer) and strike; a blow
 * it didn't see coming hits twice as hard.</li>
 * <li>V, Dread: everything within {@link #DREAD_RADIUS} that is looking at you is blinded with dread: darkness, slow and
 * weak.</li>
 * <li>B, Fade: unseen (invisible, and creatures hunting you forget you) until you strike or B again.</li>
 * </ul>
 * It blends in, with no key: it takes the colour of the wall it touches or the ground under it ({@link LurkBlend}); held
 * still while crouched for {@link #STILL_TICKS} ticks it fades from sight (like Fade: mobs lose it) until it moves or is
 * hit; unseen and moving, other players see a faint outline of it; and keeping a creature in the crosshair while unseen
 * builds a bonus on the first blow on it, up to {@link #BONUS_MAX} of the fist's damage more.
 */
public final class Lurk extends XenoForm {
	// ---- tuning ----
	public static final double WATCH_REACH = 64.0;
	public static final int WATCH_TICKS = 400, WATCH_COOLDOWN = 60;
	public static final double BEHIND_REACH = 24.0, BEHIND_STEP = 1.2;
	public static final float BEHIND_DAMAGE = 7.0F, UNSEEN_TIMES = 2.0F;
	public static final int BEHIND_COOLDOWN = 100;
	public static final double DREAD_RADIUS = 16.0, DREAD_LOOK = 0.8;
	public static final int DREAD_TICKS = 120, DREAD_COOLDOWN = 300;
	public static final int FADE_COOLDOWN = 200;
	/** Crouched and still this long (ticks) and it fades from sight; moving faster than this (blocks a tick) is moving. */
	public static final int STILL_TICKS = 30;
	public static final double MOVING = 0.04;
	/** The crosshair bonus: how far it can see a target for it (blocks), ticks of aim for the full bonus, the most it adds (share of the fist's damage), ticks of being seen it is kept for. */
	public static final double AIM_REACH = 32.0;
	public static final int AIM_FULL = 60, KEEP_EXPOSED = 20;
	public static final float BONUS_MAX = 0.5F;

	/** One Lurk's stealth: ticks held still crouched, where it was last tick, its colour (RGB) and the tint last set, the creature in its crosshair and for how long, ticks it has been seen. */
	private static final class Stealth {
		int still, charge, exposed, aim = -1;
		Vec3 last;
		int rgb = 0xFFFFFF, tint;
	}

	private static final Map<UUID, Stealth> STEALTH = new HashMap<>();

	private static Stealth stealth(ServerPlayer player) {
		return STEALTH.computeIfAbsent(player.getUUID(), id -> new Stealth());
	}

	/** Whether {@code player}, as Lurk, is held still and faded from sight (the pose reads it as Fade's). */
	public static boolean hidden(ServerPlayer player) {
		Stealth s = STEALTH.get(player.getUUID());
		return s != null && s.still >= STILL_TICKS;
	}

	private final com.jaymospowers.power.xenoform.XenoUltimate nightfall = new com.jaymospowers.power.xenoform.forms.ultimate.Nightfall(this);

	public Lurk() {
		super("lurk", 1.05F, 26.0F, 5.0, 0.15, 0.3, 0.0, Items.SPYGLASS, 0x6E52B0);
	}

	/** (0.21.37) Nightfall is its own evolved form: the Ultimate move, health bar, model and kit are in {@code Nightfall}. */
	@Override
	public com.jaymospowers.power.xenoform.XenoUltimate evolved() {
		return nightfall;
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof XenoStalker stalker && stalker.shed();
	}

	@Override
	public boolean hidden() {
		return true;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		unfade(player, live);
		live.watched = -1;
		STEALTH.remove(player.getUUID());
		player.removeAttached(LurkBlend.TINT);
	}

	/** A blow on Lurk breaks the stillness. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		Stealth s = STEALTH.get(player.getUUID());
		if (s != null) {
			s.still = 0;
		}
		return true;
	}

	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		Stealth s = stealth(player);
		s.still = 0;
		if (s.aim == target.getId() && s.charge > 0 && !HunterHits.busy()) {
			// the first blow on what it has watched unseen lands harder
			float more = (float)(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * BONUS_MAX * s.charge / AIM_FULL);
			HunterHits.extra(player, target, more);
			player.level().sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 6 + (int)(10 * s.charge / AIM_FULL), 0.3, 0.3, 0.3, 0.2);
		}
		s.charge = 0;
		s.aim = -1;
		if (live.faded) {
			unfade(player, live);
			cooldown(player, live, B, FADE_COOLDOWN);
		}
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (!ready(player, live, R)) {
					return;
				}
				LivingEntity target = aimed(player, WATCH_REACH, e -> true);
				if (target == null) {
					return;
				}
				live.watched = target.getId();
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, WATCH_TICKS, 0, false, false), player);
				forget(target, player);
				beam(level, player.getEyePosition(), target.getEyePosition(), ParticleTypes.SQUID_INK, 16);
				sound(player, SoundEvents.ENDERMAN_STARE, 0.5F, 1.6F);
				cooldown(player, live, R, WATCH_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = level.getEntity(live.watched) instanceof LivingEntity w && w.isAlive() && w.distanceTo(player) <= WATCH_REACH ? w
					: aimed(player, BEHIND_REACH, e -> true);
				if (target == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.lurk.no_target").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					return;
				}
				behind(player, live, level, target);
				cooldown(player, live, G, BEHIND_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					dread(player, level);
					cooldown(player, live, V, DREAD_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (live.faded) {
					unfade(player, live);
					cooldown(player, live, B, FADE_COOLDOWN);
				} else if (ready(player, live, B)) {
					live.faded = true;
					burst(level, ParticleTypes.SQUID_INK, player.position().add(0.0, 1.0, 0.0), 20, 0.4, 0.02);
					sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 0.4F);
					cooldown(player, live, B, 1);
				}
			}
			default -> {
			}
		}
	}

	/** Out of the dark behind {@code target}, and a blow: twice as hard if it wasn't looking your way. */
	private static void behind(ServerPlayer player, XenoformPower.Live live, ServerLevel level, LivingEntity target) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, target.getYRot());
		Vec3 spot = target.position().subtract(facing.scale(target.getBbWidth() * 0.5 + BEHIND_STEP));
		boolean saw = target.getViewVector(1.0F).dot(player.position().subtract(target.position()).normalize()) > 0.3;
		burst(level, ParticleTypes.SQUID_INK, player.position().add(0.0, 1.0, 0.0), 16, 0.3, 0.02);
		player.teleportTo(level, spot.x, target.getY(), spot.z, Set.of(), target.getYRot(), 10.0F, false);
		player.resetFallDistance();
		float damage = saw ? BEHIND_DAMAGE : BEHIND_DAMAGE * UNSEEN_TIMES;
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		live.hurt = true;
		sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.6F);
		if (!saw) {
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
		}
	}

	private static void dread(ServerPlayer player, ServerLevel level) {
		int caught = 0;
		for (LivingEntity e : around(player, DREAD_RADIUS)) {
			Vec3 toMe = player.getEyePosition().subtract(e.getEyePosition()).normalize();
			if (e.getViewVector(1.0F).dot(toMe) < DREAD_LOOK || !e.hasLineOfSight(player)) {
				continue;
			}
			e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DREAD_TICKS, 0), player);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DREAD_TICKS, 1), player);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DREAD_TICKS, 0), player);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), player);
			caught++;
		}
		burst(level, ParticleTypes.SCULK_SOUL, player.getEyePosition(), 10 + caught * 4, 0.6, 0.05);
		sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.4F, 0.6F);
	}

	/** A creature hunting {@code player} loses them. */
	private static void forget(LivingEntity e, ServerPlayer player) {
		if (e instanceof Mob mob && mob.getTarget() == player) {
			mob.setTarget(null);
		}
	}

	private static void unfade(ServerPlayer player, XenoformPower.Live live) {
		if (live.faded) {
			live.faded = false;
			player.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		Stealth s = stealth(player);
		// still, crouched: it fades from sight; moving ends it
		boolean moving = s.last != null && player.position().distanceToSqr(s.last) > MOVING * MOVING;
		s.last = player.position();
		if (player.isCrouching() && !moving) {
			s.still = Math.min(s.still + 1, STILL_TICKS);
		} else {
			s.still = 0;
		}
		boolean unseen = live.faded || s.still >= STILL_TICKS;
		if (unseen) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, false, false));
			if (player.tickCount % 10 == 0) {
				for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0), m -> m.getTarget() == player)) {
					mob.setTarget(null);
				}
			}
			watch(player, s);
		} else if (++s.exposed > KEEP_EXPOSED) {
			s.charge = 0;
			s.aim = -1;
		}
		if (unseen) {
			s.exposed = 0;
		}
		// the look of what it touches, and how much of it shows
		if (player.tickCount % LurkBlend.SAMPLE_EVERY == 0) {
			s.rgb = LurkBlend.mix(s.rgb, LurkBlend.sample(player));
		}
		int alpha = unseen ? (moving ? LurkBlend.FAINT_ALPHA : 0) : 255;
		int tint = LurkBlend.tint(s.rgb, alpha);
		if (tint != s.tint || !player.hasAttached(LurkBlend.TINT)) {
			s.tint = tint;
			player.setAttached(LurkBlend.TINT, tint);
		}
	}

	/** Unseen: whatever stays in the crosshair builds the bonus on the first blow on it. */
	private static void watch(ServerPlayer player, Stealth s) {
		if (player.tickCount % 2 != 0) {
			return;
		}
		LivingEntity target = aimed(player, AIM_REACH, e -> true);
		if (target == null) {
			return;
		}
		if (target.getId() == s.aim) {
			s.charge = Math.min(AIM_FULL, s.charge + 2);
		} else {
			s.aim = target.getId();
			s.charge = 0;
		}
	}
}
