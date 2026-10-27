package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.45, Andy's NOEA picks) Ki sense, on its own key (unbound until Andy picks one): living things within range show
 * through walls as faint outlines on the sensing player's screen only, tinted by how strong they are next to you (dim
 * when weaker, even, bright red when stronger; the strength is the scouter's power level, {@link ZFighterScouter}). The
 * range is {@link #RANGE_MIN} blocks, growing to {@link #RANGE_MAX} with the Energy stat. Every {@link #INTERVAL}
 * ticks the server tells the sensing player who is in range. Players and masters sensed are remembered for a day
 * ({@link #DAY}), which is who Instant Teleport can jump to ({@link ZFighterTeleport}).
 */
public final class ZFighterKiSense {
	/** Ticks between the server's reports (four a second). */
	static final int INTERVAL = 5;
	/** Range in blocks: this at no Energy, this at the Energy cap. */
	static final double RANGE_MIN = 24.0;
	static final double RANGE_MAX = 64.0;
	/** At most this many marks are sent (the nearest). */
	static final int MAX_MARKS = 48;
	/** A foe under this share of your power level reads dim; over the other, bright red. */
	static final float WEAKER_BELOW = 0.6F;
	static final float STRONGER_ABOVE = 1.6F;
	/** The tiers sent in a {@link ZFighterSensesNet.Mark}. */
	public static final int WEAKER = 0, EVEN = 1, STRONGER = 2;
	/** How long a sensed friend or master stays on the teleport list (one in-game day). */
	static final long DAY = 24000L;

	/** Someone sensed: when, who they are, and where they were last felt. */
	record Seen(long time, boolean master, String name, ResourceKey<Level> dimension, Vec3 pos) {
	}

	private static final Set<UUID> ON = new HashSet<>();
	/** Sensing player → what they have sensed (players and masters), by the sensed one's id. */
	private static final Map<UUID, Map<UUID, Seen>> SEEN = new HashMap<>();

	private ZFighterKiSense() {
	}

	/**
	 * The hook for a later patch: whether {@code entity} hides its ki (the Divine-born race), so ki sense and the
	 * scouter don't see it. (0.17.56) A Divine-born ({@link ZFighterLineage#divine}) hides its ki.
	 */
	public static boolean hidesKi(LivingEntity entity) {
		return ZFighterLineage.divine(entity);
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterKiSense::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ON.remove(handler.player.getUUID());
			SEEN.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ON.clear();
			SEEN.clear();
		});
	}

	/** How far {@code player} senses: grows with the Energy stat. */
	public static double range(ServerPlayer player) {
		float share = Math.min(1.0F, ZFighterTraining.of(player).stat(ZFighterTraining.ENERGY) / (float)ZFighterTraining.STAT_CAP);
		return RANGE_MIN + (RANGE_MAX - RANGE_MIN) * share;
	}

	/** The key: sense on, or off. Z Fighters only. */
	static void toggle(ServerPlayer player) {
		if (!Powers.has(player, ZFighterPower.INSTANCE) || Powers.silenced(player)) {
			return;
		}
		boolean on = ON.add(player.getUUID());
		if (!on) {
			ON.remove(player.getUUID());
			ZFighterSensesNet.send(player, new ZFighterSensesNet.Sense(false, List.of()));
		}
		player.sendOverlayMessage(Component.translatable(on ? "message.jaymospowers.zfighter_sense_on" : "message.jaymospowers.zfighter_sense_off")
			.withStyle(ChatFormatting.AQUA));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), on ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_RESONATE,
			SoundSource.PLAYERS, 0.5F, on ? 1.4F : 0.8F);
	}

	private static void tick(MinecraftServer server) {
		if (ON.isEmpty() || server.getTickCount() % INTERVAL != 0) {
			return;
		}
		for (UUID id : new ArrayList<>(ON)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				ON.remove(id);
				continue;
			}
			if (!player.isAlive() || !Powers.has(player, ZFighterPower.INSTANCE) || Powers.silenced(player)) {
				ON.remove(id);
				ZFighterSensesNet.send(player, new ZFighterSensesNet.Sense(false, List.of()));
				continue;
			}
			sense(player);
		}
	}

	private static void sense(ServerPlayer player) {
		ServerLevel level = player.level();
		double range = range(player);
		long now = level.getGameTime();
		int mine = Math.max(1, ZFighterScouter.powerLevel(player));
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !hidesKi(e)
				&& e.distanceToSqr(player) <= range * range);
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		List<ZFighterSensesNet.Mark> marks = new ArrayList<>();
		for (LivingEntity e : near) {
			if (marks.size() < MAX_MARKS) {
				float ratio = ZFighterScouter.powerLevel(e) / (float)mine;
				marks.add(new ZFighterSensesNet.Mark(e.getId(), ratio < WEAKER_BELOW ? WEAKER : ratio > STRONGER_ABOVE ? STRONGER : EVEN));
			}
			boolean master = e.entityTags().contains(ZFighterMasters.TAG) || e.entityTags().contains(ZFighterMasters.DARK_TAG);
			if (e instanceof ServerPlayer || master) {
				SEEN.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
					.put(e.getUUID(), new Seen(now, master, e.getName().getString(), level.dimension(), e.position()));
			}
		}
		ZFighterSensesNet.send(player, new ZFighterSensesNet.Sense(true, marks));
	}

	/** Friends and masters {@code player} has sensed within the last day, in the level they are in now. */
	static Map<UUID, Seen> recent(ServerPlayer player) {
		Map<UUID, Seen> all = SEEN.get(player.getUUID());
		Map<UUID, Seen> out = new HashMap<>();
		if (all == null) {
			return out;
		}
		long now = player.level().getGameTime();
		all.values().removeIf(seen -> now - seen.time() > DAY);
		for (Map.Entry<UUID, Seen> entry : all.entrySet()) {
			if (entry.getValue().dimension() == player.level().dimension()) {
				out.put(entry.getKey(), entry.getValue());
			}
		}
		return out;
	}
}
