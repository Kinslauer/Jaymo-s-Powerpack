package com.jaymospowers.power.zfighter;

import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.jaymospowers.power.zfighter.pod.PodLog;
import com.jaymospowers.power.zfighter.pod.PodModules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The pod's scanner (the {@link PodModules.Kind#SCANNER} module): after landing on a far world a needle points to the nearest
 * settlement or mission site there, an arrow and a distance on the action bar once a second, for {@link #NEEDLE_TICKS}. What it
 * finds: the Maw's wrecks (the only places to land there, {@code MawWorld}), the foes and bosses of the fighter's own mission
 * ({@link ZFighterSpaceMissions#FOE_TAG}), and the world's pad, where the first pod came down and was built a landing. Opening the
 * pod's map winds the needle again.
 */
public final class PodScanner {
	// ---- tuning ----
	/** How long the needle swings after a landing or a look at the map (ticks), and how often it is drawn. */
	static final int NEEDLE_TICKS = 2400, SHOW_EVERY = 20;
	/** How far (blocks) a mission foe is searched for round the fighter. */
	static final double FOE_RANGE = 400.0;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	/** By player: the game time the needle swings until. */
	private static final Map<UUID, Long> SWINGING = new HashMap<>();

	private PodScanner() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PodScanner::tick);
	}

	/** {@code player} came down in (or opened) {@code pod}: its needle swings, when it has a scanner and the world is a far one. */
	public static void wind(ServerPlayer player, PodEntity pod) {
		if (PodModules.has(pod, PodModules.Kind.SCANNER) && ZFighterSpace.here(player.level()) != null) {
			SWINGING.put(player.getUUID(), player.level().getGameTime() + NEEDLE_TICKS);
		}
	}

	private static void tick(MinecraftServer server) {
		if (SWINGING.isEmpty() || server.getTickCount() % SHOW_EVERY != 0) {
			return;
		}
		SWINGING.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level().getGameTime() > entry.getValue() || ZFighterSpace.here(player.level()) == null) {
				return true;
			}
			point(player);
			return false;
		});
	}

	/** The nearest site to {@code from} on {@code level}, and what it is (a lang key tail), or null if the scanner finds none. */
	static @Nullable Site nearest(ServerLevel level, BlockPos from) {
		Site best = null;
		ZFighterSpace.Dest dest = ZFighterSpace.here(level);
		if (dest != null && dest.kind() == ZFighterSpace.Kind.WRECKS) {
			for (MawWorld.Wreck wreck : MawWorld.wrecks()) {
				best = closer(best, new Site("wreck", wreck.centre()), from);
			}
		}
		BlockPos pad = level.getAttached(ZFighterSpace.PAD);
		if (pad != null) {
			best = closer(best, new Site("pad", pad), from);
		}
		for (Entity foe : level.getEntities((Entity)null, new AABB(from).inflate(FOE_RANGE, 256.0, FOE_RANGE), e -> e.isAlive() && e.entityTags().contains(ZFighterSpaceMissions.FOE_TAG))) {
			best = closer(best, new Site(foe.entityTags().contains(ZFighterSpaceMissions.BOSS_TAG) ? "boss" : "mission", foe.blockPosition()), from);
		}
		return best;
	}

	/** A place the needle can point to: what it is and where. */
	record Site(String kind, BlockPos pos) {
	}

	private static @Nullable Site closer(@Nullable Site best, Site next, BlockPos from) {
		return best == null || flat(from, next.pos()) < flat(from, best.pos()) ? next : best;
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	private static void point(ServerPlayer player) {
		Site site = nearest(player.level(), player.blockPosition());
		if (site == null) {
			return;
		}
		double dx = site.pos().getX() + 0.5 - player.getX(), dz = site.pos().getZ() + 0.5 - player.getZ();
		float aim = (float)(Math.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		int arrow = Math.floorMod(Math.round(Mth.wrapDegrees(aim - player.getYRot()) / 45.0F), 8);
		PodLog.note(player, PodLog.Step.SCANNED);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.scanner." + site.kind(), ARROWS[arrow], Math.round(Math.sqrt(dx * dx + dz * dz)))
			.withStyle(ChatFormatting.AQUA));
	}
}
