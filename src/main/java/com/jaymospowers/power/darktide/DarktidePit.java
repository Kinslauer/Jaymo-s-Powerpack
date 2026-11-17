package com.jaymospowers.power.darktide;

import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.wind.AirlessDome;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Devouring Pit and Strangling Lift (Andy's Awakening pick: Darktide).
 * <ul>
 * <li><b>Devouring Pit</b>, sprint + B: a pit of shadow opens on the ground where you look, up to {@link #PIT_REACH} blocks
 * out. For {@link #PIT_TICKS} ({@link #PIT_DARK_TICKS} where the ground lies in full dark) it drags foes in and swallows
 * them: held at its heart in the dark, blind and gnawed, and spat out when it closes.</li>
 * <li><b>Strangling Lift</b>, sprint + V: the foe you look at (within {@link #LIFT_REACH}) is lifted off its feet by a hand of
 * shadow at its throat and choked for {@link #LIFT_TICKS}: it can't breathe, and while it hangs it can't use powers (a player's
 * Jaymo's Powers are silenced, a Kingdoms caster can't cast, an illager's spell falls apart).</li>
 * </ul>
 * Both are paid in Flow.
 */
final class DarktidePit {
	// ---- tuning ----
	private static final String TABLE = "darktide_pit";
	private static final int PIT = 0, LIFT = 1;
	/** The pit: how far out, how wide, its drag (blocks a tick), how long (ticks; longer in full dark), its harm a second. */
	static final double PIT_REACH = 28.0, PIT_RADIUS = 3.0, PIT_PULL = 0.18, PIT_HELD = 1.0;
	static final int PIT_TICKS = 80, PIT_DARK_TICKS = 160, PIT_COOLDOWN = 400;
	static final float PIT_FLOW = 25.0F, PIT_HARM = 2.0F;
	/** The lift: how far, how high (blocks), how long (ticks), the choke's harm a second, the cooldown, its Flow. */
	static final double LIFT_REACH = 14.0, LIFT_HEIGHT = 2.5;
	static final int LIFT_TICKS = 60, LIFT_COOLDOWN = 300;
	static final float LIFT_FLOW = 20.0F, CHOKE = 1.5F;
	/** On a choked body while it hangs: Kingdoms reads it ({@code Will.hushed}) and Powers silences the player. */
	public static final String CHOKED_TAG = "jaymospowers_choked";

	private static final class Pit {
		final ServerPlayer owner;
		final ServerLevel level;
		final Vec3 at;
		final long until;
		final Set<LivingEntity> swallowed = new HashSet<>();

		Pit(ServerPlayer owner, Vec3 at, long until) {
			this.owner = owner;
			this.level = owner.level();
			this.at = at;
			this.until = until;
		}
	}

	private record Lift(ServerPlayer owner, ServerLevel level, LivingEntity target, Vec3 hold, long until) {
	}

	private static final List<Pit> PITS = new ArrayList<>();
	private static final List<Lift> LIFTS = new ArrayList<>();

	private DarktidePit() {
	}

	static void init() {
		Powers.addSilencer(player -> player.entityTags().contains(CHOKED_TAG));
	}

	static void clear() {
		for (Lift lift : LIFTS) {
			lift.target.removeTag(CHOKED_TAG);
		}
		LIFTS.clear();
		PITS.clear();
	}

	// ---------------------------------------------------------------- the pit

	static void pit(ServerPlayer player) {
		if (!PickKit.ready(TABLE, player, PIT)) {
			say(player, "cooling");
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		double reach = DarktideFlow.reach(player, PIT_REACH);
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != net.minecraft.core.Direction.UP) {
			say(player, "pit_ground");
			return;
		}
		if (!DarktidePower.spend(player, PIT_FLOW)) {
			return;
		}
		Vec3 at = hit.getLocation();
		boolean dark = DarktideDark.fullDark(level, BlockPos.containing(at).above());
		int ticks = dark ? PIT_DARK_TICKS : PIT_TICKS;
		PITS.add(new Pit(player, at, level.getGameTime() + ticks));
		PickKit.spend(TABLE, player, PIT, PIT_COOLDOWN);
		MagicFx.pool(level, at, (float)AbilityPower.size(player, PIT_RADIUS), DarktideLooks.INK, ticks);
		MagicFx.ring(level, at.add(0.0, 0.05, 0.0), (float)AbilityPower.size(player, PIT_RADIUS), DarktideLooks.DEEP);
		DarktideLooks.ink(level, at.add(0.0, 0.4, 0.0), 20, AbilityPower.size(player, PIT_RADIUS) * 0.4);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.4F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6F, 0.4F);
	}

	// ---------------------------------------------------------------- the lift

	static void lift(ServerPlayer player) {
		if (!PickKit.ready(TABLE, player, LIFT)) {
			say(player, "cooling");
			return;
		}
		LivingEntity target = PickKit.aimed(player, DarktideFlow.reach(player, LIFT_REACH));
		if (target == null || choked(target) || target instanceof Player p && p.getAbilities().invulnerable) {
			say(player, "lift_none");
			return;
		}
		if (!DarktidePower.spend(player, LIFT_FLOW)) {
			return;
		}
		ServerLevel level = player.level();
		LIFTS.add(new Lift(player, level, target, target.position().add(0.0, LIFT_HEIGHT, 0.0), level.getGameTime() + LIFT_TICKS));
		target.addTag(CHOKED_TAG);
		PickKit.spend(TABLE, player, LIFT, LIFT_COOLDOWN);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8F, 1.4F);
		if (target instanceof ServerPlayer choked) {
			choked.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.choked").withStyle(ChatFormatting.DARK_AQUA));
		}
	}

	static boolean choked(LivingEntity e) {
		for (Lift lift : LIFTS) {
			if (lift.target == e) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the world's tick

	static void tickWorld(MinecraftServer server) {
		for (Iterator<Pit> it = PITS.iterator(); it.hasNext();) {
			Pit pit = it.next();
			long now = pit.level.getGameTime();
			if (now >= pit.until || pit.owner.isRemoved()) {
				it.remove();
				close(pit);
				continue;
			}
			tickPit(pit, now);
		}
		for (Iterator<Lift> it = LIFTS.iterator(); it.hasNext();) {
			Lift lift = it.next();
			long now = lift.level.getGameTime();
			LivingEntity t = lift.target;
			if (now >= lift.until || !t.isAlive() || t.level() != lift.level || lift.owner.isRemoved() || !lift.owner.isAlive()) {
				t.removeTag(CHOKED_TAG);
				it.remove();
				continue;
			}
			tickLift(lift, now);
		}
	}

	private static void tickPit(Pit pit, long now) {
		ServerLevel level = pit.level;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pit.at, pit.at).inflate(AbilityPower.size(pit.owner, PIT_RADIUS), 2.0, AbilityPower.size(pit.owner, PIT_RADIUS)),
			e -> PickKit.foe(pit.owner, e) && e.position().distanceToSqr(pit.at) <= AbilityPower.size(pit.owner, PIT_RADIUS) * AbilityPower.size(pit.owner, PIT_RADIUS) * 1.2)) {
			Vec3 in = pit.at.subtract(e.position()).multiply(1.0, 0.0, 1.0);
			double d = in.length();
			if (d > PIT_HELD) {
				e.setDeltaMovement(e.getDeltaMovement().add(in.scale(PIT_PULL / d)));
				e.syncVelocity = true;
				continue;
			}
			// swallowed: held at its heart in the dark
			if (pit.swallowed.add(e)) {
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SCULK_VEIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.5F);
			}
			if (e instanceof Player) {
				e.setDeltaMovement(in.x * 0.3, Math.min(0.0, e.getDeltaMovement().y), in.z * 0.3);
				e.syncVelocity = true;
			} else {
				e.setPos(pit.at.x, pit.at.y, pit.at.z);
				e.setDeltaMovement(Vec3.ZERO);
			}
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 6, false, false), pit.owner);
			e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false), pit.owner);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false), pit.owner);
			if (now % 20 == 0) {
				e.hurtServer(level, level.damageSources().indirectMagic(pit.owner, pit.owner), PIT_HARM);
			}
		}
		if (now % 6 == 0) {
			DarktideLooks.ink(level, pit.at.add(0.0, 0.3, 0.0), 6, AbilityPower.size(pit.owner, PIT_RADIUS) * 0.35);
		}
		if (now % 20 == 0) {
			MagicFx.ring(level, pit.at.add(0.0, 0.05, 0.0), (float)AbilityPower.size(pit.owner, PIT_RADIUS), DarktideLooks.DEEP);
		}
	}

	/** The pit closes: what it swallowed is spat out. */
	private static void close(Pit pit) {
		for (LivingEntity e : pit.swallowed) {
			if (e.isAlive()) {
				e.setDeltaMovement(e.getDeltaMovement().add(0.0, 0.5, 0.0));
				e.syncVelocity = true;
			}
		}
		DarktideLooks.ink(pit.level, pit.at.add(0.0, 0.5, 0.0), 24, AbilityPower.size(pit.owner, PIT_RADIUS) * 0.5);
		pit.level.playSound(null, pit.at.x, pit.at.y, pit.at.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	private static void tickLift(Lift lift, long now) {
		ServerLevel level = lift.level;
		LivingEntity t = lift.target;
		if (t instanceof Player) {
			Vec3 to = lift.hold.subtract(t.position());
			t.setDeltaMovement(to.scale(0.4));
			t.syncVelocity = true;
		} else {
			// up to the hold over the first few ticks, then held there
			Vec3 to = lift.hold.subtract(t.position());
			t.setPos(t.position().add(to.length() > 0.3 ? to.scale(0.35) : to));
			t.setDeltaMovement(Vec3.ZERO);
		}
		t.resetFallDistance();
		t.setAirSupply(Math.max(-20, t.getAirSupply() - 10));
		AirlessDome.breakSpell(t);
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 3, false, false), lift.owner);
		if (now % 20 == 0) {
			t.hurtServer(level, level.damageSources().source(DamageTypes.DROWN, null, lift.owner), CHOKE);
		}
		if (now % 4 == 0) {
			Vec3 hand = lift.owner.getEyePosition().add(lift.owner.getLookAngle().scale(0.6)).add(0.0, -0.4, 0.0);
			Vec3 throat = t.getEyePosition().add(0.0, -0.2, 0.0);
			MagicFx.beam(level, hand, throat, 0.07F, DarktideLooks.INK, 5);
			MagicFx.ring(level, throat, null, (float)Math.max(0.3, t.getBbWidth() * 0.45), DarktideLooks.INK, 5);
			DarktideLooks.ink(level, throat, 2, 0.15);
		}
	}

	private static void say(ServerPlayer player, String what) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide." + what).withStyle(ChatFormatting.GRAY));
	}
}
