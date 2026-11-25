package com.jaymospowers.power.web;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.finisher.Finishers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rupture pounce: the red strain, suited, leaps onto prey within {@link #REACH}, pins it and stabs it again and again.
 * The leap is the first half: {@link #use} throws you at it; the moment you are on it ({@link #CONTACT}) the scene begins
 * on the execution code ({@link WebExecutions.Move#RUPTURE_STAB}: two tendrils stab through a pinned, rooted body, six
 * times, the last the kill), drawn by the client's execution pose. The kill is an execution like any other
 * ({@code Downed.execute}, credited to you), so the body is carried and fed on after, never fed on alive. Prey is any
 * creature that isn't a player and has at most {@link #MAX_HEALTH} health. Costs {@link #COST} satiety, cools down
 * {@link #COOLDOWN} ticks; on the skill bar while suited ({@link WebBarMoves#RUPTURE}).
 */
public final class WebRupture {
	/** How far off prey can be pounced on (blocks), and the strongest creature it will take (max health). */
	public static final double REACH = 9.0;
	static final float MAX_HEALTH = 40.0F;
	/** The leap's speed along the line to the prey and its lift, and how many ticks it flies before giving up. */
	static final double LEAP = 1.15, LEAP_LIFT = 0.3;
	static final int FLIGHT_TICKS = 24;
	/** Close enough to pin (blocks between the two bodies' edges). */
	static final double CONTACT = 1.3;
	public static final int COST = 6, COOLDOWN = 300;
	static final float LEAP_SHAKE = 0.5F, PIN_SHAKE = 1.0F;

	/** A pounce in the air: the prey's entity id and when the leap gives up (game time). */
	private record Flight(int prey, long until) {
	}

	private static final Map<UUID, Flight> FLYING = new HashMap<>();

	private WebRupture() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (FLYING.isEmpty()) {
				return;
			}
			FLYING.entrySet().removeIf(e -> {
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				return player == null || !land(player, e.getValue());
			});
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FLYING.clear());
	}

	/** Red, suited, awake: the pounce is open to you (both sides: the bar asks). */
	public static boolean available(Player player) {
		return WebSymbiote.isBonded(player) && WebSymbiote.state(player).kind() == WebSymbioteStrain.RED && WebExecutions.applies(player)
			&& player.isAlive() && !player.isSpectator();
	}

	private static boolean prey(ServerPlayer player, @Nullable Entity entity) {
		return entity instanceof LivingEntity living && !(living instanceof Player) && living.isAlive() && living.getMaxHealth() <= MAX_HEALTH
			&& !Finishers.targeted(living) && !WebTendrils.held(living) && !WebHeldGrab.heldByAnyone(living) && living.level() == player.level()
			&& living.distanceTo(player) <= REACH + living.getBbWidth() * 0.5;
	}

	/** The pounce at {@code target} (null: whatever is in sights): false (nothing spent) if it can't go. */
	public static boolean use(ServerPlayer player, @Nullable LivingEntity target) {
		if (!available(player) || WebSymbioteBond.dormant(player) || WebSymbiote.driving(player) || WebExecutions.busy(player)
			|| FLYING.containsKey(player.getUUID()) || WebLash.cooling(player, WebLash.SLOT_RUPTURE)) {
			return false;
		}
		Entity aimed = target != null ? target : WebPower.entityInSight(player, REACH);
		if (!prey(player, aimed)) {
			return false;
		}
		LivingEntity body = (LivingEntity)aimed;
		if (!WebLash.pay(player, COST)) {
			return false;
		}
		WebLash.cool(player, WebLash.SLOT_RUPTURE, COOLDOWN);
		ServerLevel level = player.level();
		FLYING.put(player.getUUID(), new Flight(body.getId(), level.getGameTime() + FLIGHT_TICKS));
		Vec3 to = body.getBoundingBox().getCenter().subtract(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0));
		Vec3 leap = to.normalize().scale(LEAP).add(0.0, LEAP_LIFT, 0.0);
		player.setDeltaMovement(leap);
		ServerPlayNetworking.send(player, new WebNet.Nudge(leap));
		Vec3 at = player.position();
		level.sendParticles(WebSymbiote.dust(1.4F), at.x, at.y + 0.8, at.z, 14, 0.3, 0.5, 0.3, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.8F, 1.3F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
		Shakes.send(player, LEAP_SHAKE);
		WebSymbiote.narrate(player, "rupture");
		return true;
	}

	/** One tick of a pounce; false once it is over (landed on the prey, or lost it). */
	private static boolean land(ServerPlayer player, Flight flight) {
		ServerLevel level = player.level();
		Entity entity = level.getEntity(flight.prey());
		if (!player.isAlive() || !available(player) || !(entity instanceof LivingEntity body) || !body.isAlive() || level.getGameTime() > flight.until()) {
			return false;
		}
		double gap = player.distanceTo(body) - body.getBbWidth() * 0.5 - player.getBbWidth() * 0.5;
		if (gap > CONTACT) {
			return true;
		}
		// on it: pinned, and the stabbing begins
		player.setDeltaMovement(Vec3.ZERO);
		ServerPlayNetworking.send(player, new WebNet.Nudge(Vec3.ZERO));
		Vec3 at = body.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.6F);
		Shakes.send(player, PIN_SHAKE, body.position());
		WebExecutions.begin(level, player, body, WebExecutions.Move.RUPTURE_STAB);
		return false;
	}
}
