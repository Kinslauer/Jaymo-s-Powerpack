package com.jaymospowers.power.web;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Crowned onslaught: at a whole bond, in the big suit, tendrils rise round the head like a crown and strike in volleys,
 * two at a time, at the nearest enemies in {@link #REACH}, every {@link #VOLLEY_EVERY} ticks for {@link #TICKS}. It spends
 * {@link #FURY_COST} fury to begin ({@link WebFury#spend}) and cools down {@link #COOLDOWN} ticks; on the skill bar
 * ({@link WebBarMoves#ONSLAUGHT}). It ends early if the big suit comes down.
 */
public final class WebOnslaught {
	/** How long it lasts, how often a volley goes (ticks), tendrils to a volley, and the fury it spends. */
	public static final int TICKS = 100, VOLLEY_EVERY = 8, PER_VOLLEY = 2, FURY_COST = 40, COOLDOWN = 600;
	/** The crown strikes this far (blocks) and each tendril hurts this much. */
	static final double REACH = 7.0;
	static final float DAMAGE = 6.0F;
	/** The crown's colour, and the shake of the start and of each volley. */
	static final int INK = 0x2A1238;
	static final float START_SHAKE = 0.9F, VOLLEY_SHAKE = 0.3F;

	private static final Map<UUID, Long> UNTIL = new HashMap<>();

	private WebOnslaught() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (UNTIL.isEmpty()) {
				return;
			}
			UNTIL.entrySet().removeIf(e -> {
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				if (player == null) {
					return true;
				}
				long now = player.level().getGameTime();
				if (!player.isAlive() || !WebSymbiote.bulked(player) || now >= e.getValue()) {
					finish(player);
					return true;
				}
				crown(player);
				if ((e.getValue() - now) % VOLLEY_EVERY == 0) {
					volley(player);
				}
				return false;
			});
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> UNTIL.clear());
	}

	/** Whole bond, in the big suit, awake: the onslaught is open to you (both sides: the bar asks). */
	public static boolean available(net.minecraft.world.entity.player.Player player) {
		return WebSymbiote.isBonded(player) && WebSymbiote.bulked(player) && WebSymbiote.fullBond(player) && player.isAlive() && !player.isSpectator();
	}

	/** The onslaught begins: false (nothing spent) if it can't. */
	public static boolean use(ServerPlayer player) {
		if (!available(player) || WebSymbioteBond.dormant(player) || WebSymbiote.driving(player) || UNTIL.containsKey(player.getUUID())
			|| WebLash.cooling(player, WebLash.SLOT_ONSLAUGHT)) {
			return false;
		}
		if (!WebFury.spend(player, FURY_COST)) {
			WebSymbiote.nag(player, "onslaught_fury");
			return false;
		}
		WebLash.cool(player, WebLash.SLOT_ONSLAUGHT, COOLDOWN);
		ServerLevel level = player.level();
		UNTIL.put(player.getUUID(), level.getGameTime() + TICKS);
		Vec3 head = head(player);
		MagicFx.ring(level, head, 1.6F, INK);
		level.playSound(null, head.x, head.y, head.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playSound(null, head.x, head.y, head.z, SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.2F, 0.5F);
		Shakes.send(player, START_SHAKE);
		WebSymbiote.narrate(player, "onslaught");
		return true;
	}

	private static Vec3 head(ServerPlayer player) {
		return player.position().add(0.0, player.getBbHeight() * 1.05, 0.0);
	}

	/** The crown: tendrils rising round the head, swaying. */
	private static void crown(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 head = head(player);
		double turn = player.level().getGameTime() * 0.35;
		for (int i = 0; i < 5; i++) {
			double a = turn + i * (Math.PI * 2.0 / 5.0);
			Vec3 base = head.add(Math.cos(a) * 0.45, -0.1, Math.sin(a) * 0.45);
			Vec3 tip = head.add(Math.cos(a) * 0.7, 0.9 + 0.15 * Math.sin(turn * 2.0 + i), Math.sin(a) * 0.7);
			if (level.getGameTime() % 3 == 0) {
				MagicFx.stroke(level, base, tip, 0.1F, INK);
			}
		}
	}

	/** A volley: two tendrils leave the crown for the nearest two enemies. */
	private static void volley(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 head = head(player);
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
			e -> WebLash.foe(player, e) && e.distanceTo(player) <= REACH);
		foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		int struck = 0;
		for (LivingEntity foe : foes) {
			if (struck >= PER_VOLLEY) {
				break;
			}
			Vec3 at = foe.getBoundingBox().getCenter();
			MagicFx.stroke(level, head, at, 0.14F, INK);
			foe.hurtServer(level, level.damageSources().playerAttack(player), DAMAGE);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.8F, 1.1F);
			struck++;
		}
		if (struck > 0) {
			Shakes.send(player, VOLLEY_SHAKE);
		}
	}

	private static void finish(ServerPlayer player) {
		if (!player.isAlive()) {
			return;
		}
		Vec3 head = head(player);
		player.level().sendParticles(ParticleTypes.SQUID_INK, head.x, head.y, head.z, 14, 0.4, 0.4, 0.4, 0.03);
		player.level().playSound(null, head.x, head.y, head.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.9F, 0.5F);
		WebSymbiote.whisper(player, "onslaught.over");
	}
}
