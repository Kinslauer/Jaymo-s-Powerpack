package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Soundscape;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Great Breath, a charged technique (sneak + V, held): a long gust from the mouth that shoves back everything in a cone
 * {@link #RANGE} blocks long and {@link #HALF_ANGLE} degrees either side of the look: creatures, dropped items and shots in
 * flight (a shot is turned and sent on along the gust). It does no damage of its own, and costs {@link #KI_PER_TICK} ki a tick.
 * <p>
 * For a Frost-born the gust is cold (pale blue) and whatever it stays on for about 6 s freezes ({@link ZFighterChill}). Other
 * lineages' breath is plain wind: the cold is the Frost-born's alone (their lore is the cold), not bought with ki.
 */
final class ZFighterGreatBreath {
	static final float MIN_KI = 5.0F, MAX_KI = 8.0F;
	static final int FULL_TICKS = 30;
	static final int COOLDOWN = 60;

	static final float KI_PER_TICK = 0.35F;
	static final double RANGE = 11.0, HALF_ANGLE = 28.0;
	/** The shove a tick at the mouth, falling off to {@link #FAR_SHARE} of it at the far end; its lift; the most speed it gives. */
	static final double PUSH = 0.55, FAR_SHARE = 0.4, LIFT = 0.05, MAX_SPEED = 1.4;
	/** How fast a shot or item it catches is sent along the gust. */
	static final double SHOT_SPEED = 1.1;
	static final int WIND_COLOR = 0xE8F6FF;
	static final int WAVE_EVERY = 6;
	/** Ticks the gust may be held at most, so it can't be an endless stun. */
	static final int MAX_TICKS = 160;

	private static final Soundscape.Layer GUST = Soundscape.Layer.of(0.5F, 0.7F, 0.7F, 0.9F, SoundEvents.BREEZE_IDLE_AIR);

	private static final class Breath {
		long first = -100L, last = -100L;
		final ZFighterChill chill = new ZFighterChill();
	}

	private static final Map<UUID, Breath> BREATHS = new HashMap<>();

	private ZFighterGreatBreath() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterGreatBreath::tick);
	}

	static void clear() {
		BREATHS.clear();
	}

	/** Always free to start. */
	static @Nullable String blocker(ServerPlayer player) {
		return null;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Breath>> it = BREATHS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Breath> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level().getGameTime() - entry.getValue().last > 5) {
				it.remove();
			}
		}
	}

	/** While V is held: the gust. */
	static void gather(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Breath breath = BREATHS.computeIfAbsent(player.getUUID(), id -> new Breath());
		if (now - breath.last > 2) {
			breath.first = now;
			breath.chill.reset();
		}
		breath.last = now;
		boolean free = player.isCreative() || live.raging();
		if (!free && charge <= 0.0F || now - breath.first > MAX_TICKS) {
			return;
		}
		if (!free) {
			live.ki = Math.max(0.0F, live.ki - KI_PER_TICK * ZFighterAndroid.costFactor(player));
		}
		boolean cold = live.lineage == ZFighterLineage.FROST;
		int color = cold ? ZFighterChill.COLOR : WIND_COLOR;
		Vec3 look = player.getViewVector(1.0F);
		Vec3 mouth = player.getEyePosition().add(0.0, -0.22, 0.0).add(look.scale(0.3));
		double cos = Math.cos(Math.toRadians(HALF_ANGLE));
		LivingEntity longest = null;
		double longestDot = cos;
		for (Entity entity : level.getEntities(player, new AABB(mouth, mouth.add(look.scale(RANGE))).inflate(RANGE * 0.45))) {
			Vec3 to = entity.getBoundingBox().getCenter().subtract(mouth);
			double distance = to.length();
			if (distance > RANGE || distance < 1.0E-3 || to.scale(1.0 / distance).dot(look) < cos) {
				continue;
			}
			if (entity instanceof LivingEntity living) {
				if (!ZFighterArts.valid(player, living)) {
					continue;
				}
				double falloff = 1.0 - (1.0 - FAR_SHARE) * distance / RANGE;
				ZFighterKi.push(living, look, PUSH * falloff, LIFT);
				capSpeed(living);
				if (cold) {
					double dot = to.scale(1.0 / distance).dot(look);
					if (dot > longestDot) {
						longest = living;
						longestDot = dot;
					}
				}
			} else if (entity instanceof Projectile || entity instanceof ItemEntity) {
				entity.setDeltaMovement(look.scale(SHOT_SPEED));
				entity.syncVelocity = true;
				if (entity instanceof Projectile shot) {
					shot.setOwner(player);
				}
			}
		}
		if (cold && breath.chill.tick(level, longest)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_gamma.frozen").withStyle(ChatFormatting.AQUA));
		}
		show(level, mouth, look, color, now, cold);
		if (Soundscape.due(player, "breath.gust", 5, 9)) {
			Soundscape.play(level, mouth, SoundSource.PLAYERS, GUST);
		}
	}

	private static void capSpeed(LivingEntity living) {
		Vec3 motion = living.getDeltaMovement();
		double flat = Math.hypot(motion.x, motion.z);
		if (flat > MAX_SPEED) {
			living.setDeltaMovement(motion.x * MAX_SPEED / flat, motion.y, motion.z * MAX_SPEED / flat);
		}
	}

	/** The gust drawn: motes streaming out along the cone and, now and then, a wave rolling over the ground. */
	private static void show(ServerLevel level, Vec3 mouth, Vec3 look, int color, long now, boolean cold) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 5; i++) {
			double spread = Math.tan(Math.toRadians(HALF_ANGLE)) * 0.8;
			Vec3 dir = look.add((random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread).normalize();
			level.sendParticles(MagicFx.wisp(color, 1.1F + random.nextFloat() * 0.8F), true, false, mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y, dir.z,
				0.6 + random.nextDouble() * 0.5);
		}
		if (cold && now % 3 == 0) {
			level.sendParticles(MagicFx.spark(color, 0.7F), true, false, mouth.x + look.x * 4.0, mouth.y + look.y * 4.0, mouth.z + look.z * 4.0, 2, 1.0, 0.6, 1.0, 0.1);
		}
		if (now % WAVE_EVERY == 0) {
			MagicFx.wave(level, mouth, look, RANGE, 3.5F, color, 8);
		}
	}

	/** V let go with the ki paid: the gust ends in a last puff. */
	static void release(ServerPlayer player, ZFighterPower.Live live, float charge) {
		BREATHS.remove(player.getUUID());
		Vec3 look = player.getViewVector(1.0F);
		Vec3 mouth = player.getEyePosition().add(0.0, -0.22, 0.0).add(look.scale(0.5));
		MagicFx.blast(player.level(), mouth, 0.7F, live.lineage == ZFighterLineage.FROST ? ZFighterChill.COLOR : WIND_COLOR, 8);
	}
}
