package com.jaymospowers.power.gravity;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Planet hopping (Andy's Awakening pick: "Gravity Well as a way to travel"). Sneak + G throws a small planet, a knot of
 * weight the size of a fist, up to {@link #REACH} blocks where you look (onto a wall or the ground, or out in the open air),
 * and you fall to it: its pull, not the ground's, is your down until you reach it. Falling to a planet never hurts. Throw the
 * next one before you get there and you fall on toward it at the speed you already had, so a run of throws carries you a
 * long way. Reaching it, you are caught and it pops; it fades by itself if you never get there.
 */
final class GravityPlanets {
	// ---- tuning ----
	/** How far a planet is thrown (blocks), how hard it pulls (blocks a tick, a tick), the fastest you fall to it. */
	static final double REACH = 50.0, PULL = 0.11, MAX_SPEED = 2.2;
	/** Close enough to count as there (blocks), and what is kept of your speed as it catches you. */
	static final double ARRIVE = 1.4, CATCH_KEEPS = 0.35;
	/** How long a planet lasts (ticks), the wait between throws, the hunger each throw costs. */
	static final int LIFE = 120, COOLDOWN = 12;
	static final float EXHAUSTION = 0.6F;
	static final int COLOUR = 0x8A6CFF, CORE = 0xD9CCFF;

	private record Planet(Vec3 at, long until) {
	}

	private static final Map<UUID, Planet> PLANETS = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private GravityPlanets() {
	}

	/** Sneak + G: a planet thrown where you look. True if the key was taken (thrown, or refused for the wait). */
	static boolean throwPlanet(ServerPlayer player) {
		if (!Mastery.isUnlocked(player, PowerKey.ABILITY_2)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (READY.getOrDefault(player.getUUID(), 0L) > now) {
			return true;
		}
		if (!player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= 2) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_planets.hungry").withStyle(ChatFormatting.GRAY));
			return true;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(REACH)) : hit.getLocation().subtract(look.scale(0.8));
		PLANETS.put(player.getUUID(), new Planet(at, now + LIFE));
		READY.put(player.getUUID(), now + COOLDOWN);
		player.causeFoodExhaustion(EXHAUSTION);
		MagicFx.beam(level, eye.add(0.0, -0.3, 0.0), at, 0.05F, COLOUR, 6);
		MagicFx.orb(level, at, 0.5F, COLOUR, 12);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.4F, 1.9F);
		return true;
	}

	/** Falling to a planet now (the anti-float check lets it be). */
	static boolean falling(ServerPlayer player) {
		return PLANETS.containsKey(player.getUUID());
	}

	static void tick(ServerPlayer player) {
		Planet planet = PLANETS.get(player.getUUID());
		if (planet == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now >= planet.until() || !player.isAlive() || player.isPassenger()) {
			forget(player);
			MagicFx.blast(level, planet.at(), 0.5F, COLOUR);
			return;
		}
		Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 to = planet.at().subtract(centre);
		double distance = to.length();
		player.resetFallDistance();
		if (distance <= ARRIVE) {
			// caught: the planet pops and lets you go, most of your speed taken off
			PLANETS.remove(player.getUUID());
			player.setDeltaMovement(player.getDeltaMovement().scale(CATCH_KEEPS));
			player.syncVelocity = true;
			MagicFx.blast(level, planet.at(), 0.8F, CORE);
			level.playSound(null, planet.at().x, planet.at().y, planet.at().z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
			return;
		}
		// the planet's pull is your down: the ground's is cancelled, and you fall to it, faster the longer you fall
		Vec3 v = player.getDeltaMovement().add(0.0, 0.08, 0.0).add(to.scale(PULL / distance));
		if (v.length() > MAX_SPEED) {
			v = v.normalize().scale(MAX_SPEED);
		}
		player.setDeltaMovement(v);
		player.syncVelocity = true;
		if (now % 6 == 0) {
			MagicFx.orb(level, planet.at(), 0.5F, COLOUR, 8);
			level.sendParticles(MagicFx.mote(CORE, 0.8F), planet.at().x, planet.at().y, planet.at().z, 2, 0.3, 0.3, 0.3, 0.01);
		}
	}

	static void forget(ServerPlayer player) {
		PLANETS.remove(player.getUUID());
	}
}
