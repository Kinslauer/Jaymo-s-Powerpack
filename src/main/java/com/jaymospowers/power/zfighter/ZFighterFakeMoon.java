package com.jaymospowers.power.zfighter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (Andy, 2026-11-01) The false moon: a made Great Ball ({@link ZFighterCreator#BALL}) big enough, thrown almost straight
 * up into an open night sky, doesn't come down. It climbs, swells and pales into a little moon that hangs there a while,
 * and it shines like a full one on any phase of the moon. Every Warrior-born under it with the tail out turns into the
 * Great Ape ({@link ZFighterLineage}; a fighter without the Z Fighter power turns for certain here, with no roll), and stays
 * the ape while it hangs. Villagers keep their tails wrapped, but the light still gets at them: each wrapped Warrior-born
 * villager under it is rolled once ({@link #VILLAGER_CHANCE}) and may turn ({@link VillagerApe}).
 *
 * <p>Nothing is saved: a moon lives only while the server runs, and dawn puts it out.
 */
public final class ZFighterFakeMoon {
	/** The ball must be at least this big (blocks across, {@link ZFighterCreator}'s size) to hold up as a moon. */
	static final double MIN_SIZE = 1.5;
	/** Thrown at least this steeply (the look's upward share; 0.75 is about 50 degrees up). */
	static final double MIN_UP = 0.75;
	/** It climbs this high above where it was thrown from, over this long (ticks), kept this far under the top of the world. */
	static final double RISE = 48.0;
	static final int RISE_TICKS = 80;
	static final int TOP_MARGIN = 6;
	/** Its size once risen: this, plus this per block it was thrown across. */
	static final double MOON_SIZE = 6.0, MOON_SIZE_PER = 1.5;
	/** How long it hangs once risen (ticks; 2 minutes), and how long it takes to fade out at the end. */
	static final int HANG_TICKS = 2400, FADE_TICKS = 60;
	/** Everyone this near it (blocks, flat distance) and out under the sky is in its light. */
	static final double REACH = 112.0;
	/** The chance each wrapped Warrior-born villager under it turns (rolled once a moon), and how often they're looked over. */
	static final float VILLAGER_CHANCE = 0.35F;
	static final int SCAN_EVERY = 20;
	/** The pale moonlight it ends at. */
	static final int MOON_COLOR = 0xF4F1DC;
	/** Each thrower keeps one moon up at a time. */
	private static final int ID_BASE = -1_900_000_000;

	private static final class Moon {
		final ServerLevel level;
		final UUID owner;
		final int id;
		final Vec3 from;
		final Vec3 to;
		final double startSize;
		final double size;
		final int color;
		final Set<UUID> rolled = new HashSet<>();
		int age;

		Moon(ServerLevel level, UUID owner, int id, Vec3 from, Vec3 to, double startSize, double size, int color) {
			this.level = level;
			this.owner = owner;
			this.id = id;
			this.from = from;
			this.to = to;
			this.startSize = startSize;
			this.size = size;
			this.color = color;
		}

		/** 0 just thrown, 1 risen. */
		float risen() {
			return Math.min(1.0F, age / (float)RISE_TICKS);
		}

		Vec3 at() {
			float t = risen();
			// it slows as it reaches the top
			double eased = 1.0 - (1.0 - t) * (1.0 - t);
			return from.lerp(to, eased);
		}

		boolean shining() {
			return age >= RISE_TICKS / 2 && age < RISE_TICKS + HANG_TICKS;
		}
	}

	private static final List<Moon> MOONS = new ArrayList<>();
	private static int nextId;

	private ZFighterFakeMoon() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterFakeMoon::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MOONS.clear());
	}

	/** Whether a ball of {@code size} thrown this way from {@code from} becomes a moon: big, steep, at night, under open sky. */
	static boolean takes(ServerPlayer player, Vec3 from, Vec3 look, double size) {
		ServerLevel level = player.level();
		return size >= MIN_SIZE && look.normalize().y >= MIN_UP && level.dimensionType().hasSkyLight() && level.isDarkOutside()
			&& level.canSeeSky(BlockPos.containing(from));
	}

	/** The ball goes up and stays up. Their last moon, if one still hangs, goes out. */
	static void raise(ServerPlayer player, Vec3 from, double size, int color) {
		ServerLevel level = player.level();
		MOONS.removeIf(moon -> moon.owner.equals(player.getUUID()));
		double top = level.getMaxY() - TOP_MARGIN;
		Vec3 to = new Vec3(from.x, Math.min(from.y + RISE, top), from.z);
		MOONS.add(new Moon(level, player.getUUID(), ID_BASE - (nextId++ & 0xFFFFF), from, to, size, MOON_SIZE + MOON_SIZE_PER * size, color));
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_fake_moon.raised").withStyle(ChatFormatting.GRAY), true);
	}

	/** Whether a false moon's light is on {@code entity}: one shines near enough and it's out under the sky. */
	public static boolean shines(ServerLevel level, Entity entity) {
		if (MOONS.isEmpty() || !level.isDarkOutside()) {
			return false;
		}
		for (Moon moon : MOONS) {
			if (moon.level == level && moon.shining() && near(moon, entity)) {
				return level.canSeeSky(BlockPos.containing(entity.getEyePosition()));
			}
		}
		return false;
	}

	private static boolean near(Moon moon, Entity entity) {
		Vec3 at = moon.to;
		double dx = entity.getX() - at.x, dz = entity.getZ() - at.z;
		return dx * dx + dz * dz <= REACH * REACH && entity.getY() <= at.y + 8.0;
	}

	private static void tick(MinecraftServer server) {
		if (MOONS.isEmpty()) {
			return;
		}
		for (var it = MOONS.iterator(); it.hasNext(); ) {
			Moon moon = it.next();
			ServerLevel level = moon.level;
			if (moon.age >= RISE_TICKS + HANG_TICKS + FADE_TICKS || level.isBrightOutside() && moon.age >= RISE_TICKS
				|| server.getLevel(level.dimension()) != level) {
				it.remove();
				continue;
			}
			moon.age++;
			draw(moon);
			if (moon.age == RISE_TICKS) {
				// it's up: a low hum and the pale light comes on
				Vec3 at = moon.to;
				level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 6.0F, 0.5F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 6.0F, 0.5F);
			}
			if (moon.shining() && moon.age % SCAN_EVERY == 0) {
				villagers(moon, level);
			}
		}
	}

	/** The orb, growing and paling as it climbs, then fading out at the end. */
	private static void draw(Moon moon) {
		float t = moon.risen();
		double size = Mth.lerp(t, moon.startSize, moon.size);
		int over = moon.age - RISE_TICKS - HANG_TICKS;
		if (over > 0) {
			size *= Math.max(0.05, 1.0 - over / (double)FADE_TICKS);
		}
		Vec3 at = moon.at();
		ZFighterKi.orb(moon.level, moon.id, at, size, blend(moon.color, MOON_COLOR, t));
		if (moon.age % 4 == 0) {
			moon.level.sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y, at.z, 2, size * 0.4, size * 0.4, size * 0.4, 0.01);
		}
	}

	private static int blend(int from, int to, float t) {
		int r = Mth.lerpInt(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
		int g = Mth.lerpInt(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
		int b = Mth.lerpInt(t, from & 0xFF, to & 0xFF);
		return r << 16 | g << 8 | b;
	}

	/** Wrapped Warrior-born villagers under it: each rolled once a moon. */
	private static void villagers(Moon moon, ServerLevel level) {
		Vec3 at = moon.to;
		AABB box = new AABB(at.x - REACH, level.getMinY(), at.z - REACH, at.x + REACH, at.y + 8.0, at.z + REACH);
		List<Mob> turning = new ArrayList<>();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && !mob.isBaby() && VillagerApe.wrapped(mob)
			&& !VillagerApe.raging(mob) && !moon.rolled.contains(mob.getUUID()))) {
			if (!near(moon, mob) || !level.canSeeSky(BlockPos.containing(mob.getEyePosition()))) {
				continue;
			}
			moon.rolled.add(mob.getUUID());
			if (mob.getRandom().nextFloat() < VILLAGER_CHANCE) {
				turning.add(mob);
			}
		}
		for (Mob mob : turning) {
			VillagerApe.turnUnderFakeMoon(mob, level);
		}
	}
}
