package com.jaymospowers.power.smoke;

import com.jaymospowers.core.Fate;
import com.jaymospowers.power.wind.WindWeather;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Smoke's ascension, Cloudshaper.
 * <ul>
 * <li><b>How it comes:</b> with everything mastered, fly in Vapour Form into a thunderstorm (a real storm overhead,
 * rain on you) at Y {@link #ASCEND_Y}+ and stay in it {@link #ASCEND_TICKS} (30 s). Nothing explains it.</li>
 * <li><b>Passive:</b> your smoke is white cloud high up ({@link #CLOUD_Y}+), in the rain, or while you sneak
 * ({@link SmokeField#puff}); the rain doesn't wash your smoke away; a thick cloud of yours holds you up (you sink
 * slowly through it); Vapour Form flies faster in the rain (the client).</li>
 * <li><b>C, Harness the Sky:</b> a tap calls a raincloud where you aim (puts out fires, waters crops). Held: at a
 * tornado within {@link #TORNADO_RANGE} you unwind it a rank at a time while it drags you in; under a storm you calm it
 * (a supercell never puts down its tornado). Both through Jaymo's Kingdoms' {@code SkyCalm} (found by reflection);
 * without Kingdoms a held C calms vanilla weather: thunder to rain, then clear.</li>
 * </ul>
 */
final class SmokeCloudshaper {
	// ---- ascension
	static final double ASCEND_Y = 150.0;
	static final int ASCEND_TICKS = 600;

	// ---- the passive
	static final double CLOUD_Y = 120.0;
	/** Your own cloud this thick at your feet holds you up. */
	static final float HOLD_UP = 1.5F;

	// ---- Harness the Sky
	/** Held less than this (ticks) is a tap: a raincloud. */
	static final int TAP_TICKS = 10;
	static final double RAIN_RANGE = 64.0;
	static final int RAIN_TICKS = 600, RAIN_COOLDOWN = 600;
	static final int RAIN_RADIUS = 10;
	/** The raincloud sits this high over the ground it waters (lower under a ceiling). */
	static final int RAIN_HEIGHT = 9;
	static final double TORNADO_RANGE = 48.0;
	/** How hard a tornado you're unwinding drags you toward it (blocks a tick a tick). */
	static final double DRAG = 0.05;
	/** Hunger a tick while holding; at or below this food you can't. */
	static final float HOLD_EXHAUSTION = 0.05F;
	static final int MIN_FOOD = 6;
	/** Without Kingdoms: seconds held to turn a thunderstorm to rain, and rain to a clear sky. */
	static final int VANILLA_THUNDER_SECONDS = 20, VANILLA_RAIN_SECONDS = 40;

	private static final MethodHandle CALM = find("calm", MethodType.methodType(boolean.class, Level.class, double.class, double.class, float.class));
	private static final MethodHandle UNWIND = find("unwind",
		MethodType.methodType(double[].class, Level.class, double.class, double.class, double.class, float.class, boolean.class));

	private static final class Hold {
		final long from;
		int seconds;

		Hold(long from) {
			this.from = from;
		}
	}

	private record Rain(ServerLevel level, UUID owner, BlockPos ground, int top, long until) {
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();
	private static final Map<UUID, Integer> STORM_TICKS = new HashMap<>();
	private static final List<Rain> RAINS = new ArrayList<>();

	private SmokeCloudshaper() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SmokeCloudshaper::rains);
	}

	private static @Nullable MethodHandle find(String name, MethodType type) {
		try {
			return MethodHandles.publicLookup().findStatic(Class.forName("com.jaymoskingdoms.disasters.SkyCalm"), name, type);
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	/** Whether this Smoke user's smoke comes out as white cloud right now (ascended, not smog). */
	static boolean cloudy(ServerPlayer player) {
		if (!Fate.isAscended(player) || Fate.isCorrupted(player)) {
			return false;
		}
		return player.getY() >= CLOUD_Y || player.isShiftKeyDown() || player.level().isRainingAt(BlockPos.containing(player.getEyePosition()));
	}

	/** From the power's tick. */
	static void tick(ServerPlayer player) {
		if (!player.isAlive()) {
			return;
		}
		if (!Fate.isAscended(player)) {
			ascending(player);
			return;
		}
		ServerLevel level = player.level();
		if (!Fate.isCorrupted(player)) {
			SmokeField.markCloud(player, level.getGameTime() + 40);
		}
		// a thick cloud of your own holds you up
		if (!SmokeVapour.active(player) && player.getDeltaMovement().y < 0.0 && player.tickCount % 10 == 0
			&& SmokeField.owner(level, player.blockPosition()) == player.getId() && SmokeField.density(level, player.blockPosition()) >= HOLD_UP) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 15, 0, false, false, true));
		}
		holding(player, level);
	}

	// ---------------------------------------------------------------- ascension

	private static void ascending(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean inStorm = Fate.canAscend(player) && SmokeVapour.active(player) && player.getY() >= ASCEND_Y && WindWeather.stormOverhead(player);
		if (!inStorm) {
			STORM_TICKS.remove(id);
			return;
		}
		int ticks = STORM_TICKS.merge(id, 1, Integer::sum);
		ServerLevel level = player.level();
		if (ticks % 40 == 0) {
			// the storm's cloud gathers into you
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 1.5, 1.0, 1.5, 0.05);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.4F,
				0.6F + ticks / (float)ASCEND_TICKS * 0.6F);
		}
		if (ticks >= ASCEND_TICKS) {
			STORM_TICKS.remove(id);
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 120, 2.0, 1.5, 2.0, 0.15);
			Fate.ascend(player);
		}
	}

	// ---------------------------------------------------------------- Harness the Sky

	/** C pressed ({@code down}) or let go (ascended, not corrupted). */
	static void key(ServerPlayer player, boolean down) {
		long now = player.level().getGameTime();
		if (down) {
			if (!SmokeVapour.wholly(player)) {
				HOLDS.put(player.getUUID(), new Hold(now));
			}
			return;
		}
		Hold hold = HOLDS.remove(player.getUUID());
		if (hold == null || now - hold.from >= TAP_TICKS) {
			return;
		}
		long left = SmokePower.state(player).left(SmokeState.SKY, now);
		if (left > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_raincloud_wait", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (raincloud(player)) {
			SmokePower.cooldown(player, SmokeState.SKY, RAIN_COOLDOWN);
		}
	}

	private static void holding(ServerPlayer player, ServerLevel level) {
		Hold hold = HOLDS.get(player.getUUID());
		if (hold == null) {
			return;
		}
		long held = level.getGameTime() - hold.from;
		if (held < TAP_TICKS) {
			return;
		}
		if (!player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= MIN_FOOD) {
			HOLDS.remove(player.getUUID());
			return;
		}
		player.causeFoodExhaustion(HOLD_EXHAUSTION);
		boolean second = (held - TAP_TICKS) % 20 == 0;
		double[] tornado = second ? unwind(level, player) : null;
		if (tornado != null) {
			Vec3 at = new Vec3(tornado[0], tornado[1] + 8.0, tornado[2]);
			stream(level, at, player);
			Vec3 pull = new Vec3(tornado[0] - player.getX(), 0.0, tornado[2] - player.getZ());
			if (pull.lengthSqr() > 1.0) {
				player.push(pull.normalize().scale(DRAG * 20.0));
				player.syncVelocity = true;
			}
			hold.seconds++;
			return;
		}
		if (second) {
			hold.seconds++;
			calm(level, player, hold.seconds);
		}
		if (held % 4 == 0) {
			// the sky's cloud drawn down into you
			Vec3 above = player.position().add(level.getRandom().nextGaussian() * 6.0, 14.0, level.getRandom().nextGaussian() * 6.0);
			stream(level, above, player);
		}
	}

	/** A tornado within reach unwound by a second; where it is, or null if there's none (or no Kingdoms). */
	private static double @Nullable [] unwind(ServerLevel level, ServerPlayer player) {
		if (UNWIND == null) {
			return null;
		}
		try {
			return (double[])UNWIND.invokeExact((Level)level, player.getX(), player.getZ(), TORNADO_RANGE, 1.0F, false);
		} catch (Throwable t) {
			return null;
		}
	}

	/** The storm overhead calmed by a second: Kingdoms' cell, or else vanilla's weather. */
	private static void calm(ServerLevel level, ServerPlayer player, int seconds) {
		if (CALM != null) {
			try {
				if ((boolean)CALM.invokeExact((Level)level, player.getX(), player.getZ(), 1.0F)) {
					return;
				}
			} catch (Throwable ignored) {
			}
		}
		if (level.dimension() != Level.OVERWORLD || !level.isRaining()) {
			return;
		}
		WeatherData weather = level.getWeatherData();
		if (level.isThundering() && seconds >= VANILLA_THUNDER_SECONDS) {
			weather.setThundering(false);
			weather.setThunderTime(12000);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_calm_thunder").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		} else if (!level.isThundering() && seconds >= VANILLA_THUNDER_SECONDS + VANILLA_RAIN_SECONDS) {
			weather.setRaining(false);
			weather.setRainTime(0);
			weather.setClearWeatherTime(12000);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_calm_rain").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			HOLDS.remove(player.getUUID());
		}
	}

	/** A line of cloud from {@code from} into the player. */
	private static void stream(ServerLevel level, Vec3 from, ServerPlayer player) {
		Vec3 to = player.position().add(0.0, 1.2, 0.0);
		for (int i = 0; i < 8; i++) {
			Vec3 p = from.lerp(to, i / 8.0);
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.0);
		}
	}

	// ---------------------------------------------------------------- the raincloud

	private static boolean raincloud(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		HitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(RAIN_RANGE)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getLocation();
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		if (hit.getType() == HitResult.Type.BLOCK && BlockPos.containing(at).getY() < ground.getY() - 2) {
			ground = BlockPos.containing(at).above(); // under a roof: the rain falls in here
		}
		int top = ground.getY() + RAIN_HEIGHT;
		for (int y = ground.getY() + 1; y <= ground.getY() + RAIN_HEIGHT; y++) {
			if (!level.getBlockState(new BlockPos(ground.getX(), y, ground.getZ())).getCollisionShape(level, ground).isEmpty()) {
				top = y - 1;
				break;
			}
		}
		RAINS.add(new Rain(level, player.getUUID(), ground, top, level.getGameTime() + RAIN_TICKS));
		SmokeField.puffCloud(level, new Vec3(ground.getX() + 0.5, top, ground.getZ() + 0.5), 30.0F, player);
		level.playSound(null, ground.getX(), top, ground.getZ(), SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 1.0F, 1.0F);
		return true;
	}

	private static void rains(MinecraftServer server) {
		if (RAINS.isEmpty()) {
			return;
		}
		RAINS.removeIf(rain -> {
			ServerLevel level = rain.level;
			long now = level.getGameTime();
			if (now >= rain.until) {
				return true;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(rain.owner);
			double cx = rain.ground.getX() + 0.5, cz = rain.ground.getZ() + 0.5;
			// the cloud keeps itself up, and rains
			if (now % 10 == 0) {
				SmokeField.puffCloud(level, new Vec3(cx, rain.top, cz), 3.0F, owner);
			}
			level.sendParticles(ParticleTypes.FALLING_WATER, cx, rain.top - 0.5, cz, 24, RAIN_RADIUS * 0.5, 0.2, RAIN_RADIUS * 0.5, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, cx, rain.top + 0.5, cz, 4, RAIN_RADIUS * 0.45, 0.3, RAIN_RADIUS * 0.45, 0.0);
			if (now % 40 == 0) {
				level.playSound(null, cx, rain.top, cz, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.6F, 1.0F);
			}
			if (now % 10 == 0) {
				water(level, rain);
			}
			return false;
		});
	}

	/** Under the raincloud: fires go out, burning things stop burning, crops grow. */
	private static void water(ServerLevel level, Rain rain) {
		BlockPos g = rain.ground;
		for (BlockPos pos : BlockPos.betweenClosed(g.offset(-RAIN_RADIUS, -4, -RAIN_RADIUS), g.offset(RAIN_RADIUS, rain.top - g.getY(), RAIN_RADIUS))) {
			if (pos.distSqr(new BlockPos(g.getX(), pos.getY(), g.getZ())) > RAIN_RADIUS * RAIN_RADIUS) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.FIRE)) {
				level.removeBlock(pos, false);
				level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
			} else if (state.is(BlockTags.CROPS) && level.getRandom().nextInt(6) == 0) {
				state.randomTick(level, pos.immutable(), level.getRandom());
			}
		}
		AABB box = new AABB(g).inflate(RAIN_RADIUS, 0, RAIN_RADIUS).expandTowards(0, rain.top - g.getY() + 2, 0);
		for (Entity e : level.getEntitiesOfClass(Entity.class, box, Entity::isOnFire)) {
			e.clearFire();
		}
	}

	static void forget(ServerPlayer player) {
		HOLDS.remove(player.getUUID());
		STORM_TICKS.remove(player.getUUID());
	}

	static void clear() {
		HOLDS.clear();
		STORM_TICKS.clear();
		RAINS.clear();
	}
}
