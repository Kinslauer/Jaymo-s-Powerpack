package com.jaymoskingdoms;

import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Riders;
import com.jaymoskingdoms.money.Money;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Horse races at a settlement's stable (one of the Rider ideas Andy took: "add everything you thought up"). The
 * stablehand's screen offers a short and a long course; pay the entry and a finish post goes up that far off across open
 * land (a column of light only the racer needs to find). Ride there against the clock: fast enough for gold or silver,
 * or just finish, each with its prize; too slow and the race is lost. Anyone on their own horse may race; a Rider takes a
 * bigger prize ({@link #RIDER_PRIZE}). Tuning numbers live at the top.
 */
public final class Races {
	/** The courses: how far the finish is (blocks), and the prizes for gold, silver and finishing (emeralds' worth). */
	public static final int[] DISTANCE = {160, 320};
	public static final int[][] PRIZE = {{5, 3, 1}, {12, 6, 2}};
	/** What it costs to enter (emeralds' worth). */
	public static final int FEE = 1;
	/** The average pace (blocks a tick) for gold and silver, and below which the race is lost. */
	static final double GOLD_PACE = 0.5, SILVER_PACE = 0.36, LOST_PACE = 0.15;
	/** How close to the post counts as over the line (blocks, flat). */
	static final double FINISH_REACH = 5.0;
	/** A Rider's prize, as a share of the course's. */
	public static final double RIDER_PRIZE = 1.5;

	private record Race(int course, ResourceKey<Level> dimension, Vec3 from, Vec3 finish, long start) {
		double distance() {
			return Math.sqrt(this.from.distanceToSqr(this.finish));
		}
	}

	private static final Map<UUID, Race> RACES = new HashMap<>();

	private Races() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(Races::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RACES.clear());
	}

	public static boolean racing(ServerPlayer player) {
		return RACES.containsKey(player.getUUID());
	}

	/** {@code player} on their own horse (or riding one they steer), able to race. */
	static @Nullable AbstractHorse mount(ServerPlayer player) {
		return player.getVehicle() instanceof AbstractHorse horse && Equestrian.covered(horse) && horse.getControllingPassenger() == player
			&& com.jaymoskingdoms.equestrian.Bonding.owner(horse, player) ? horse : null;
	}

	/** Sets {@code player} off on course {@code course} from {@code from}. False if no finish could be laid (nothing is charged then). */
	static boolean start(ServerPlayer player, Vec3 from, int course) {
		ServerLevel level = player.level();
		int distance = DISTANCE[Mth.clamp(course, 0, DISTANCE.length - 1)];
		float angle = level.getRandom().nextFloat() * Mth.TWO_PI;
		for (int i = 0; i < 12; i++) {
			float a = angle + i * Mth.TWO_PI / 12.0F;
			int x = Mth.floor(from.x + Mth.cos(a) * distance);
			int z = Mth.floor(from.z + Mth.sin(a) * distance);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos ground = new BlockPos(x, y - 1, z);
			if (!level.getFluidState(ground).isEmpty() || !level.getBlockState(ground).isSolid()) {
				continue;
			}
			Vec3 finish = new Vec3(x + 0.5, y, z + 0.5);
			RACES.put(player.getUUID(), new Race(course, level.dimension(), from, finish, level.getGameTime()));
			level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.race.start", distance, x, z).withStyle(ChatFormatting.GOLD));
			return true;
		}
		return false;
	}

	private static void tick(ServerLevel level) {
		if (RACES.isEmpty() || level.getGameTime() % 5 != 0) {
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Map.Entry<UUID, Race>> it = RACES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Race> e = it.next();
			Race race = e.getValue();
			if (!race.dimension.equals(level.dimension())) {
				continue;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || player.level() != level) {
				it.remove();
				continue;
			}
			long ticks = now - race.start;
			double distance = race.distance();
			if (ticks > distance / LOST_PACE) {
				it.remove();
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.race.lost").withStyle(ChatFormatting.GRAY));
				continue;
			}
			double dx = race.finish.x - player.getX(), dz = race.finish.z - player.getZ();
			double left = Math.sqrt(dx * dx + dz * dz);
			if (left <= FINISH_REACH && mount(player) != null) {
				it.remove();
				finish(level, player, race, ticks);
				continue;
			}
			if (now % 20 == 0) {
				// the post: a column of light, seen from far off
				for (int h = 0; h < 24; h += 3) {
					level.sendParticles(ParticleTypes.END_ROD, true, true, race.finish.x, race.finish.y + h, race.finish.z, 2, 0.1, 0.5, 0.1, 0.0);
				}
			}
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.race.progress", (int)Math.round(left), time(ticks),
				Component.translatable("direction.jaymoskingdoms." + direction(dx, dz))));
		}
	}

	private static void finish(ServerLevel level, ServerPlayer player, Race race, long ticks) {
		double pace = race.distance() / Math.max(1L, ticks);
		int medal = pace >= GOLD_PACE ? 0 : pace >= SILVER_PACE ? 1 : 2;
		int prize = PRIZE[race.course][medal];
		long milli = Money.worth(prize);
		if (Riders.is(player)) {
			milli = Math.round(milli * RIDER_PRIZE);
		}
		Money.pay(player, milli);
		level.playSound(null, player.blockPosition(), medal == 0 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.5, player.getZ(), 30, 0.6, 0.6, 0.6, 0.15);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.race.finish." + medal, time(ticks), Money.textPaid(player, milli))
			.withStyle(ChatFormatting.GOLD));
	}

	private static String time(long ticks) {
		long s = ticks / 20;
		return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
	}

	/** The way to the post, as a compass point. */
	private static String direction(double dx, double dz) {
		String[] points = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};
		double deg = Math.toDegrees(Math.atan2(-dx, dz));
		return points[Math.floorMod((int)Math.round(deg / 45.0), 8)];
	}
}
