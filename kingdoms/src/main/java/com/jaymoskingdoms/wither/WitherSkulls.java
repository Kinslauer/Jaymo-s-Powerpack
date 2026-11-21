package com.jaymoskingdoms.wither;

import com.jaymoskingdoms.Fx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Wither's skulls: the ones it throws at the ruins and the ones it rains, each ending in a bigger blast than a vanilla
 * skull makes (the blast goes through the explosion's own mob interaction, so the mobGriefing rule decides whether it
 * breaks anything); and the blue ones, which a player's swing sends back at it for heavy damage.
 */
final class WitherSkulls {
	enum Kind {
		/** Thrown at a wrecked town's walls. */
		RAVAGE,
		/** Falls from above onto a marked spot. */
		RAIN
	}

	/** A skull of ours, where it was last seen and what it's for. */
	private static final class Shot {
		final WitherBoss wither;
		final Kind kind;
		final Vec3 spot;
		final long expires;
		Vec3 last;

		Shot(WitherBoss wither, Kind kind, Vec3 spot, Vec3 last, long expires) {
			this.wither = wither;
			this.kind = kind;
			this.spot = spot;
			this.last = last;
			this.expires = expires;
		}
	}

	/** The blast of a thrown (ravage) skull and of a rained one, and whether the rained one burns. */
	static final float RAVAGE_POWER = 3.2F, RAIN_POWER = 4.0F;
	/** How long a skull of ours lives before it blasts where it is (ticks). */
	static final int SHOT_LIFE = 120;
	/** How fast a rained skull is thrown down (the skull gathers speed as it falls). */
	static final double RAIN_DROP = 1.0;
	/** The blast's colour: the Wither's violet for a ravaging skull, fire for rain. */
	static final int RAVAGE_COLOR = 0x6A3FA0, RAIN_COLOR = 0xFF7A20;

	/** A blue skull is within this many blocks of a swinging player (and in front of them) to be batted. */
	static final double SWAT_REACH = 4.5;
	static final double SWAT_FRONT = 0.3;
	/** How fast a batted skull flies back (blocks per tick), and what it does to the Wither (health). */
	static final double BAT_SPEED = 1.4;
	static final float BAT_DAMAGE = 45.0F;

	private static final Map<WitherSkull, Shot> SHOTS = new IdentityHashMap<>();
	private static final Map<WitherSkull, ServerPlayer> BATTED = new IdentityHashMap<>();
	private static final Set<java.util.UUID> SWINGING = new HashSet<>();
	/** The heavy blow of a batted skull is itself a blow on the Wither: it isn't taken for another skull. */
	private static boolean batting;

	private WitherSkulls() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WitherSkulls::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (batting || !(entity instanceof WitherBoss wither) || !(source.getDirectEntity() instanceof WitherSkull skull)
				|| !(wither.level() instanceof ServerLevel level)) {
				return true;
			}
			ServerPlayer by = BATTED.remove(skull);
			if (by == null) {
				return true;
			}
			// the skull the player sent back: it doesn't hit for a skull's eight, but for a great deal more
			batting = true;
			try {
				wither.setInvulnerableTime(0);
				wither.hurtServer(level, level.damageSources().playerAttack(by), BAT_DAMAGE);
			} finally {
				batting = false;
			}
			Vec3 at = skull.position();
			Fx.blast(level, at, 2.5F, 0x4060FF);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.7F);
			return false;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SHOTS.clear();
			BATTED.clear();
			SWINGING.clear();
		});
	}

	/** A skull from {@code from} toward {@code to}; {@code kind} (null for a plain blue or black one) sets how it ends. */
	static WitherSkull throwSkull(ServerLevel level, WitherBoss wither, Vec3 from, Vec3 to, boolean dangerous, Kind kind, Vec3 spot) {
		Vec3 direction = to.subtract(from);
		WitherSkull skull = new WitherSkull(level, wither, direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, -1.0, 0.0) : direction.normalize());
		skull.setOwner(wither);
		skull.setDangerous(dangerous);
		skull.setPos(from.x, from.y, from.z);
		level.addFreshEntity(skull);
		if (kind != null) {
			SHOTS.put(skull, new Shot(wither, kind, spot, from, level.getGameTime() + SHOT_LIFE));
		}
		return skull;
	}

	/** A rained skull, dropped from above {@code spot}. */
	static void rain(ServerLevel level, WitherBoss wither, Vec3 spot, double height) {
		Vec3 from = spot.add(0.0, height, 0.0);
		WitherSkull skull = throwSkull(level, wither, from, spot, false, Kind.RAIN, spot);
		skull.setDeltaMovement(0.0, -RAIN_DROP, 0.0);
	}

	private static void tick(MinecraftServer server) {
		if (!SHOTS.isEmpty()) {
			for (Map.Entry<WitherSkull, Shot> entry : new ArrayList<>(SHOTS.entrySet())) {
				WitherSkull skull = entry.getKey();
				Shot shot = entry.getValue();
				boolean gone = skull.isRemoved();
				if (gone && skull.getRemovalReason() != Entity.RemovalReason.DISCARDED) {
					// (unloaded with its chunk: no blast)
					SHOTS.remove(skull);
					continue;
				}
				if (!(shot.wither.level() instanceof ServerLevel level)) {
					SHOTS.remove(skull);
					continue;
				}
				boolean landed = shot.kind == Kind.RAIN && skull.getY() <= shot.spot.y + 1.0;
				if (!gone && !landed && level.getGameTime() < shot.expires) {
					shot.last = skull.position();
					continue;
				}
				SHOTS.remove(skull);
				Vec3 at = gone ? shot.last : skull.position();
				skull.discard();
				blast(level, shot, at);
			}
		}
		swat(server);
		if (!BATTED.isEmpty()) {
			BATTED.keySet().removeIf(Entity::isRemoved);
		}
	}

	private static void blast(ServerLevel level, Shot shot, Vec3 at) {
		boolean rain = shot.kind == Kind.RAIN;
		float power = rain ? RAIN_POWER : RAVAGE_POWER;
		// (Level.ExplosionInteraction.MOB: breaks blocks only where mobGriefing allows it)
		level.explode(shot.wither, at.x, at.y, at.z, power, rain, Level.ExplosionInteraction.MOB);
		Fx.blast(level, at, power, rain ? RAIN_COLOR : RAVAGE_COLOR);
		if (rain) {
			Fx.ring(level, at, power * 1.4F, RAIN_COLOR);
		}
	}

	/** A player who swings with a blue skull close in front of them sends it back at the Wither it came from. */
	private static void swat(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				boolean swinging = player.isSwinging();
				boolean started = swinging && SWINGING.add(player.getUUID());
				if (!swinging) {
					SWINGING.remove(player.getUUID());
				}
				if (!started || !player.isAlive() || player.isSpectator()) {
					continue;
				}
				Vec3 look = player.getLookAngle();
				Vec3 eye = player.getEyePosition();
				WitherSkull best = null;
				double bestDistance = Double.MAX_VALUE;
				for (WitherSkull skull : level.getEntitiesOfClass(WitherSkull.class, player.getBoundingBox().inflate(SWAT_REACH),
					s -> s.isDangerous() && s.isAlive() && s.getOwner() instanceof WitherBoss && !BATTED.containsKey(s))) {
					Vec3 to = skull.position().subtract(eye);
					double distance = to.length();
					if (distance < 1.0E-3 || distance > SWAT_REACH || to.scale(1.0 / distance).dot(look) < SWAT_FRONT) {
						continue;
					}
					if (distance < bestDistance) {
						best = skull;
						bestDistance = distance;
					}
				}
				if (best != null) {
					bat(level, player, best);
				}
			}
		}
	}

	private static void bat(ServerLevel level, ServerPlayer player, WitherSkull skull) {
		WitherBoss from = skull.getOwner() instanceof WitherBoss w ? w : null;
		if (from == null) {
			return;
		}
		Vec3 back = from.position().add(0.0, from.getBbHeight() * 0.6, 0.0).subtract(skull.position()).normalize();
		skull.setOwner(player);
		skull.setDeltaMovement(back.scale(BAT_SPEED));
		skull.needsSync = true;
		BATTED.put(skull, player);
		Vec3 at = skull.position();
		Fx.ring(level, at, 1.6F, 0x4060FF);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.4F);
	}
}
