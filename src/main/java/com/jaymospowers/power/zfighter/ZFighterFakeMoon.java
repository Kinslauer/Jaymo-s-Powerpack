package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
 * (Andy, 2026-11-01) The false moon: a made Great Ball ({@link ZFighterCreator#BALL}) big enough, gathered small in the
 * hand while aimed up at an open night sky ({@link #aiming}), then thrown, doesn't come down. It shoots up out of sight,
 * all the way to space, and takes the moon's place: for everyone under it the sky's moon is a full one, larger and in the
 * ball's own pale light ({@link Sky}, drawn by the client's {@code ZFighterFakeMoonSky}), whatever the real moon's phase.
 * Every Warrior-born under it with the tail out turns into the Great Ape ({@link ZFighterLineage}; one without the Z
 * Fighter power still rolls, as under the real moon), and stays the ape while it shines. Villagers keep their tails
 * wrapped, but the light still gets at them: each wrapped Warrior-born villager under it is rolled once
 * ({@link #VILLAGER_CHANCE}) and may turn ({@link VillagerApe}). Kingdoms' werewolves change under it too.
 *
 * <p>Nothing is saved: a moon lives only while the server runs, and dawn puts it out.
 */
public final class ZFighterFakeMoon {
	/** The ball must be at least this big (blocks across, {@link ZFighterCreator}'s size) to hold up as a moon. */
	static final double MIN_SIZE = 1.5;
	/** Thrown at least this steeply (the look's upward share; 0.75 is about 50 degrees up). */
	static final double MIN_UP = 0.75;
	/** Gathered in the hand: this big at first, this much bigger at a full charge (blocks across). */
	public static final double HAND_SIZE = 0.12, HAND_GROW = 0.38;
	/** It climbs this high above the hand over this long (ticks), faster and faster, shrinking to this as it goes. */
	static final double RISE = 220.0;
	static final int RISE_TICKS = 50;
	static final double GONE_SIZE = 0.15;
	/** How long it shines once up (ticks; 2 minutes), and how long the sky takes to fade back at the end. */
	static final int HANG_TICKS = 2400, FADE_TICKS = 60;
	/** Everyone this near where it was thrown (blocks, flat distance) and out under the sky is in its light. */
	static final double REACH = 128.0;
	/** The chance each wrapped Warrior-born villager under it turns (rolled once a moon), and how often they're looked over. */
	static final float VILLAGER_CHANCE = 0.35F;
	static final int SCAN_EVERY = 20;
	/** How often the sky's moon is sent to those under it (ticks). */
	static final int SKY_EVERY = 10;
	/** The pale moonlight it goes to as it climbs (the sky's moon is tinted halfway from this to the ball's colour). */
	static final int MOON_COLOR = 0xF4F1DC;
	/** Each thrower keeps one moon up at a time. */
	private static final int ID_BASE = -1_900_000_000;

	/** Server → client: the sky's moon is a false one ({@code fade} 0-1 how much), in {@code color}. Lapses unless re-sent. */
	public record Sky(int color, float fade) implements CustomPacketPayload {
		public static final Type<Sky> TYPE = new Type<>(JaymosPowers.id("zfighter_fake_moon_sky"));
		public static final StreamCodec<ByteBuf, Sky> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Sky::color,
			ByteBufCodecs.FLOAT, Sky::fade,
			Sky::new);

		@Override
		public Type<Sky> type() {
			return TYPE;
		}
	}

	private static final class Moon {
		final ServerLevel level;
		final UUID owner;
		final int id;
		final Vec3 from;
		final double startSize;
		final int color;
		final Set<UUID> rolled = new HashSet<>();
		int age;

		Moon(ServerLevel level, UUID owner, int id, Vec3 from, double startSize, int color) {
			this.level = level;
			this.owner = owner;
			this.id = id;
			this.from = from;
			this.startSize = startSize;
			this.color = color;
		}

		/** 0 just thrown, 1 gone into space. */
		float risen() {
			return Math.min(1.0F, age / (float)RISE_TICKS);
		}

		Vec3 at() {
			float t = risen();
			// it picks up speed as it goes
			return from.add(0.0, RISE * t * t, 0.0);
		}

		boolean shining() {
			return age >= RISE_TICKS && age < RISE_TICKS + HANG_TICKS;
		}

		/** How much the sky's moon is this one now. */
		float sky() {
			if (age < RISE_TICKS) {
				return 0.0F;
			}
			int over = age - RISE_TICKS - HANG_TICKS;
			return over <= 0 ? Math.min(1.0F, (age - RISE_TICKS) / 20.0F) : Math.max(0.0F, 1.0F - over / (float)FADE_TICKS);
		}
	}

	private static final List<Moon> MOONS = new ArrayList<>();
	private static int nextId;

	private ZFighterFakeMoon() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Sky.TYPE, Sky.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ZFighterFakeMoon::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MOONS.clear());
	}

	/** Aimed up at an open night sky: a Great Ball gathers small in the hand, to be thrown up as a moon. */
	static boolean aiming(ServerPlayer player) {
		ServerLevel level = player.level();
		return player.getViewVector(1.0F).y >= MIN_UP && level.dimensionType().hasSkyLight() && level.isDarkOutside()
			&& level.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	/** Whether a ball of {@code size} (its thrown size) let go now becomes a moon: big enough, and aimed up at the night sky. */
	static boolean takes(ServerPlayer player, double size) {
		return size >= MIN_SIZE && aiming(player);
	}

	/** The ball leaves the hand at {@code from} ({@code size} across) and goes up for good. Their last moon, if one still shines, goes out. */
	static void raise(ServerPlayer player, Vec3 from, double size, int color) {
		ServerLevel level = player.level();
		MOONS.removeIf(moon -> moon.owner.equals(player.getUUID()));
		MOONS.add(new Moon(level, player.getUUID(), ID_BASE - (nextId++ & 0xFFFFF), from, size, color));
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
		double dx = entity.getX() - moon.from.x, dz = entity.getZ() - moon.from.z;
		return dx * dx + dz * dz <= REACH * REACH;
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
			if (moon.age < RISE_TICKS) {
				draw(moon);
			} else if (moon.age == RISE_TICKS) {
				// it's gone into space, and the moon up there is a different one: a low hum all round
				for (ServerPlayer player : level.players()) {
					if (near(moon, player)) {
						level.playSound(null, player.getX(), player.getEyeY() + 12.0, player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
							2.0F, 0.5F);
						level.playSound(null, player.getX(), player.getEyeY() + 12.0, player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
							SoundSource.PLAYERS, 2.0F, 0.5F);
					}
				}
			}
			if (moon.age >= RISE_TICKS && moon.age % SKY_EVERY == 0) {
				sky(moon, level);
			}
			if (moon.shining() && moon.age % SCAN_EVERY == 0) {
				villagers(moon, level);
			}
		}
	}

	/** The ball on its way up: smaller and smaller as it goes, paling toward moonlight, with a trail. */
	private static void draw(Moon moon) {
		float t = moon.risen();
		double size = Mth.lerp(t, moon.startSize, GONE_SIZE);
		Vec3 at = moon.at();
		ZFighterKi.orb(moon.level, moon.id, at, size, blend(moon.color, MOON_COLOR, t));
		if (moon.age % 2 == 0) {
			moon.level.sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y - 1.0, at.z, 2, 0.15, 0.6, 0.15, 0.01);
		}
	}

	/** The sky's moon to everyone under it (the client drops it if not re-sent). */
	private static void sky(Moon moon, ServerLevel level) {
		Sky sky = new Sky(blend(moon.color, MOON_COLOR, 0.5F), moon.sky());
		for (ServerPlayer player : level.players()) {
			if (near(moon, player)) {
				ServerPlayNetworking.send(player, sky);
			}
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
		Vec3 at = moon.from;
		AABB box = new AABB(at.x - REACH, level.getMinY(), at.z - REACH, at.x + REACH, level.getMaxY(), at.z + REACH);
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
