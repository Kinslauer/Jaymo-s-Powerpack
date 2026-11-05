package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.Stacked;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.36, Andy) The high sky: the top band of a One Dimension world, over the Aether's islands, where the air all but runs out
 * at the edge of space ({@code data/jaymospowers/dimension/high_sky.json}: open air, nothing to stand on; drawn from below with
 * the planet's curve under you and the other worlds hanging close, client {@code ZFighterHighSkyClient}).
 * <ul>
 * <li><b>Thin air.</b> Only a fighter's ki keeps a body breathing up here: a Z Fighter spends {@link #KI_BREATH} ki a second to
 * breathe (on top of flying), and anyone else, or a fighter run dry, chokes: {@link #THIN_DAMAGE} a second after
 * {@link #THIN_AFTER} seconds. Inside a pod you breathe the pod's air. A Warrior-born raider never runs short.</li>
 * <li><b>Ambushes.</b> A pod leaving the stacked world passes up through it, and now and then ({@link #AMBUSH_CHANCE}, for a
 * pilot who can fly on ki) a tyrant's raider band is waiting there ({@link #raid}): the pod is struck, the pilot thrown out into
 * the air, and the fight is up here, above the planet. The trip side is {@code ZFighterPodTrip}'s ambush.</li>
 * </ul>
 * An ordinary world (no stack, or the stack made before the high sky) has none of this: nothing reaches its high sky.
 */
public final class ZFighterHighSky {
	// ---- tuning ----
	/** Ki a second a Z Fighter spends to breathe up here, and the least ki he must have for it. */
	static final float KI_BREATH = 1.0F;
	/** Seconds without breath before the thin air hurts, and the hurt each second after that. */
	static final int THIN_AFTER = 3;
	static final float THIN_DAMAGE = 2.0F;
	/** The share of trips out of the stacked world that meet a raider band up here (from the second trip on). */
	static final float AMBUSH_CHANCE = 0.35F;
	/** How far up the high sky the ambush happens (share of its height from the bottom). */
	static final double AMBUSH_HEIGHT = 0.55;
	/** How many raiders (the first is their chief, a tyrant), and how far off (blocks) they come in from. */
	static final int RAID_MIN = 3, RAID_MAX = 5;
	static final double RAID_NEAR = 18.0, RAID_FAR = 30.0, RAID_RISE = 6.0;
	/** How fast raiders fly up here (blocks a tick, top speed), how near they hold off a foe, and how far they look for one. */
	static final double SKY_SPEED = 0.55, HOLD_OFF = 2.5, LOOK = 64.0;

	/** The high sky's dimension (a band of the stacked Overworld in a One Dimension world). */
	public static final ResourceKey<Level> HIGH_SKY = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("high_sky"));
	/** On the raiders of a high-sky ambush. */
	static final String TAG = "jaymospowers.sky_raider";

	/** The raiders of ambushes under way (raiders left from before a restart are cleared as they load). */
	private static final Set<UUID> RAIDERS = new HashSet<>();
	/** Seconds each player has gone without breath up here. Not saved. */
	private static final Map<UUID, Integer> CHOKING = new HashMap<>();
	/** Who has been told their ki is keeping them breathing, this time up. */
	private static final Set<UUID> TOLD = new HashSet<>();

	private ZFighterHighSky() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterHighSky::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RAIDERS.clear();
			CHOKING.clear();
			TOLD.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && !RAIDERS.contains(entity.getUUID())) {
				entity.discard();
			}
		});
	}

	// ---------------------------------------------------------------- where

	/** Whether {@code entity} is up in the high sky. */
	public static boolean in(Entity entity) {
		return Stacked.in(entity, HIGH_SKY);
	}

	/** Whether this world has a high sky you can get to: it is the top band of the stack. */
	static boolean open(MinecraftServer server) {
		return Stacked.joined(server, HIGH_SKY);
	}

	/** Whether a Warrior-born raider (or any rogue fighter) is in the high sky, where he must fly ({@link #steer}). */
	static boolean airborne(Mob mob) {
		return in(mob);
	}

	// ---------------------------------------------------------------- thin air

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			breathe(player);
		}
		if (!RAIDERS.isEmpty()) {
			look(server);
		}
	}

	/** One second of {@code player}'s breath. */
	private static void breathe(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!player.isAlive() || player.isSpectator() || player.isCreative() || !in(player)
			|| player.getVehicle() instanceof com.jaymospowers.power.zfighter.pod.PodEntity) {
			CHOKING.remove(id);
			if (!in(player)) {
				TOLD.remove(id);
			}
			return;
		}
		if (Powers.has(player, ZFighterPower.INSTANCE)) {
			ZFighterPower.Live live = ZFighterPower.live(player);
			if (live.ki >= KI_BREATH || live.raging()) {
				if (!live.raging()) {
					live.ki -= KI_BREATH;
				}
				if (CHOKING.remove(id) != null || TOLD.add(id)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.high_sky.ki_breath").withStyle(ChatFormatting.AQUA));
				}
				return;
			}
		}
		// (0.20.99, Andy) the Sky Mask lets anyone without ki to breathe on breathe up here too
		if (com.jaymospowers.baubles.Baubles.wearing(player, com.jaymospowers.baubles.Baubles.SKY_MASK)) {
			CHOKING.remove(id);
			return;
		}
		int seconds = CHOKING.merge(id, 1, Integer::sum);
		if (seconds == 1) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.high_sky.no_air").withStyle(ChatFormatting.RED));
		}
		if (seconds >= THIN_AFTER) {
			ServerLevel level = player.level();
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, true, false, true));
			player.hurtServer(level, player.damageSources().drown(), THIN_DAMAGE);
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getEyeY(), player.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
		}
	}

	// ---------------------------------------------------------------- ambushes

	/** Whether a pod leaving {@code from} meets a raider band on the way up: a stacked world, a pilot who flies on ki, the odds. */
	static boolean ambushes(ServerPlayer pilot, ResourceKey<Level> from, int flown) {
		MinecraftServer server = pilot.level().getServer();
		return from == Level.OVERWORLD && open(server) && flown > 0 && canHold(pilot) && pilot.getRandom().nextFloat() < AMBUSH_CHANCE;
	}

	/** Whether {@code player} can fight up here: a Z Fighter who has his flight and isn't kept from it. */
	static boolean canHold(ServerPlayer player) {
		return Powers.has(player, ZFighterPower.INSTANCE) && ZFighterPower.INSTANCE.slotUnlocked(player, "mode") && !Powers.silenced(player)
			&& !ZFighterLineage.ape(player) && com.jaymospowers.power.wind.WindFlight.canFly(player);
	}

	/** The height of the ambush in the stacked level. */
	static double ambushY(MinecraftServer server) {
		int low = Stacked.minY(server, HIGH_SKY), high = Stacked.maxY(server, HIGH_SKY);
		return low + (high - low) * AMBUSH_HEIGHT;
	}

	/** Thrown out of the pod: a fighter who can fly is flying at once (no fall to start the fight with). */
	static void thrown(ServerPlayer player) {
		if (canHold(player)) {
			ZFighterPower.hoverNow(player, 1);
		}
		player.resetFallDistance();
	}

	/** A tyrant's raider band comes in on the pod at {@code at}, after {@code pilot} (and {@code guest}); their ids. */
	static List<UUID> raid(ServerLevel level, Vec3 at, ServerPlayer pilot, @Nullable ServerPlayer guest) {
		RandomSource random = level.getRandom();
		int count = RAID_MIN + random.nextInt(RAID_MAX - RAID_MIN + 1);
		int name = random.nextInt(ZFighterRaiders.NAMES);
		double turn = random.nextDouble() * Math.PI * 2.0;
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double angle = turn + i * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.6;
			double distance = RAID_NEAR + random.nextDouble() * (RAID_FAR - RAID_NEAR);
			double x = at.x + Math.cos(angle) * distance, z = at.z + Math.sin(angle) * distance;
			double y = at.y + (random.nextDouble() * 2.0 - 1.0) * RAID_RISE;
			ServerPlayer foe = guest != null && i % 2 == 1 ? guest : pilot;
			ZFighterRogue raider = ZFighterRaiders.raider(level, x, y, z, i == 0, name, TAG, r -> {
				RAIDERS.add(r.getUUID());
				r.setNoGravity(true);
			});
			if (raider == null) {
				continue;
			}
			raider.setTarget(foe);
			ids.add(raider.getUUID());
			level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 12, 0.3, 0.5, 0.3, 0.08);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 1.5F);
		return ids;
	}

	/** How many of {@code ids} are still alive and up here. */
	static int alive(ServerLevel level, List<UUID> ids) {
		int alive = 0;
		for (UUID id : ids) {
			if (level.getEntity(id) instanceof ZFighterRogue raider && raider.isAlive()) {
				alive++;
			}
		}
		return alive;
	}

	/** Whoever of {@code ids} is left breaks off and is gone (a flash of ki and they're away). */
	static void flee(ServerLevel level, List<UUID> ids) {
		for (UUID id : ids) {
			RAIDERS.remove(id);
			if (level.getEntity(id) instanceof ZFighterRogue raider && raider.isAlive()) {
				level.sendParticles(ParticleTypes.CLOUD, raider.getX(), raider.getY() + 1.0, raider.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
				raider.discard();
			}
		}
	}

	/** Forgets the dead of {@code ids} (their kills are done). */
	static void forgetDead(ServerLevel level, List<UUID> ids) {
		ids.removeIf(id -> {
			boolean gone = !(level.getEntity(id) instanceof ZFighterRogue raider) || !raider.isAlive();
			if (gone) {
				RAIDERS.remove(id);
			}
			return gone;
		});
	}

	/** Every second: a raider up here with nobody to fight goes for the nearest player who isn't sat in a pod. */
	private static void look(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (UUID id : RAIDERS) {
			if (!(level.getEntity(id) instanceof ZFighterRogue raider) || !raider.isAlive()) {
				continue;
			}
			LivingEntity target = raider.getTarget();
			if (target != null && target.isAlive() && !(target.getVehicle() instanceof com.jaymospowers.power.zfighter.pod.PodEntity)) {
				continue;
			}
			Player nearest = level.getNearestPlayer(raider.getX(), raider.getY(), raider.getZ(), LOOK,
				p -> p.isAlive() && !p.isSpectator() && !((Player) p).isCreative() && !(p.getVehicle() instanceof com.jaymospowers.power.zfighter.pod.PodEntity));
			raider.setTarget(nearest);
		}
	}

	// ---------------------------------------------------------------- flying fighters

	/**
	 * A rogue fighter's flight up here (every tick, from his own): no gravity; at a foe he flies straight for him, quicker the
	 * farther off, and holds just off him to throw his strings; without one he hangs where he is.
	 */
	static void steer(Mob mob, @Nullable LivingEntity target) {
		mob.setNoGravity(true);
		mob.resetFallDistance();
		Vec3 motion = mob.getDeltaMovement();
		if (target == null || !target.isAlive()) {
			mob.setDeltaMovement(motion.scale(0.8));
			return;
		}
		Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.4, 0.0).subtract(mob.position().add(0.0, mob.getBbHeight() * 0.4, 0.0));
		double distance = to.length();
		double speed = distance > HOLD_OFF ? Math.min(SKY_SPEED, 0.08 + (distance - HOLD_OFF) * 0.04) : 0.0;
		Vec3 want = distance > 0.01 ? to.scale(speed / distance) : Vec3.ZERO;
		mob.setDeltaMovement(motion.lerp(want, 0.3));
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		mob.setYRot((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
		mob.yBodyRot = mob.getYRot();
	}
}
