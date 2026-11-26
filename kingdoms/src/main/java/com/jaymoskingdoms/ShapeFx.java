package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a Druid's shape-changing looks like: winding vines round the body as a shape is taken and shed, and, the first
 * time a beast is mastered, a green wisp that drifts from it into the Druid with the beast's own call. No chat text.
 */
final class ShapeFx {
	/** The vines: how long they wind (ticks), how high (blocks), their widest turn (blocks), strands, and the green of them. */
	static final int VINE_TICKS = 24;
	static final double VINE_HEIGHT = 1.8, VINE_RADIUS = 0.9;
	static final int VINE_STRANDS = 3, VINE_GREEN = 0x4FA83D;
	/** The wisp: how long it drifts (ticks) and the green of it. */
	static final int WISP_TICKS = 30, WISP_GREEN = 0x7BE87B;

	/** Vines winding in ({@code in}) as a shape is taken, or unwinding out as it is shed, round a player. */
	private record Vines(ServerLevel level, UUID who, int[] left, boolean in) {
	}

	private record Wisp(ServerLevel level, Vec3 from, UUID to, int[] left) {
	}

	private static final List<Vines> VINES = new ArrayList<>();
	private static final List<Wisp> WISPS = new ArrayList<>();

	private ShapeFx() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ShapeFx::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VINES.clear();
			WISPS.clear();
		});
	}

	/** Winding vines round {@code player} (in: closing over them as a shape is taken; out: falling away as it's shed). */
	static void vines(ServerPlayer player, boolean in) {
		VINES.add(new Vines(player.level(), player.getUUID(), new int[] {VINE_TICKS}, in));
	}

	/**
	 * A beast is mastered for the first time: a wisp drifts from where it is ({@code at}, or somewhere near if it is only known
	 * from a scan) into {@code player}, and its call is heard ({@code type}, if known).
	 */
	static void learned(ServerPlayer player, ServerLevel level, @Nullable Vec3 at, @Nullable EntityType<?> type) {
		Vec3 from = at != null ? at : player.position().add(level.getRandom().nextGaussian() * 3.0, 0.5, level.getRandom().nextGaussian() * 3.0);
		WISPS.add(new Wisp(level, from, player.getUUID(), new int[] {WISP_TICKS}));
		if (type != null && type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob) {
			mob.setPos(player.getX(), player.getY(), player.getZ());
			mob.playAmbientSound();
			mob.discard();
		}
	}

	private static void tick(MinecraftServer server) {
		if (!VINES.isEmpty()) {
			VINES.removeIf(v -> {
				ServerPlayer p = server.getPlayerList().getPlayer(v.who);
				if (p == null || p.level() != v.level) {
					return true;
				}
				int t = VINE_TICKS - --v.left[0];
				double rise = VINE_HEIGHT * t / VINE_TICKS;
				double turn = t * 0.45;
				double r = VINE_RADIUS * (v.in ? 1.0 - 0.5 * t / VINE_TICKS : 0.5 + 0.5 * t / VINE_TICKS);
				double y = v.in ? rise : VINE_HEIGHT - rise;
				for (int s = 0; s < VINE_STRANDS; s++) {
					double a = turn + s * Math.PI * 2.0 / VINE_STRANDS;
					v.level.sendParticles(Fx.wisp(VINE_GREEN, 0.9F), p.getX() + Math.cos(a) * r, p.getY() + y, p.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
					v.level.sendParticles(ParticleTypes.COMPOSTER, p.getX() + Math.cos(a + 0.4) * r, p.getY() + y, p.getZ() + Math.sin(a + 0.4) * r, 1, 0.0, 0.0, 0.0, 0.0);
				}
				return v.left[0] <= 0;
			});
		}
		if (!WISPS.isEmpty()) {
			WISPS.removeIf(w -> {
				ServerPlayer p = server.getPlayerList().getPlayer(w.to);
				if (p == null || p.level() != w.level) {
					return true;
				}
				double done = 1.0 - (double) --w.left[0] / WISP_TICKS;
				Vec3 to = p.position().add(0.0, 1.0, 0.0);
				Vec3 at = w.from.lerp(to, done * done * (3.0 - 2.0 * done)).add(Math.sin(done * 9.0) * 0.3 * (1.0 - done), Math.sin(done * 6.0) * 0.4 * (1.0 - done), 0.0);
				w.level.sendParticles(Fx.wisp(WISP_GREEN, 1.2F), at.x, at.y, at.z, 2, 0.04, 0.04, 0.04, 0.0);
				w.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
				return w.left[0] <= 0;
			});
		}
	}

	/** The entity type a kind's path names (the first of that path in the registry), or null. */
	static @Nullable EntityType<?> typeOfKind(String path) {
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath().equals(path)) {
				return type;
			}
		}
		return null;
	}
}
