package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.332) The Mask. A vampire by day who stands still (no movement key for {@link #STILL_TICKS}) raises a mask: to
 * everyone else they look like anyone, without the ashen face ({@link #MASK}, synced to all; {@code VampireLooks}
 * checks it). It costs fever, {@link #COST} a second ({@link BloodFever#stoke}; a dhampir pays nothing). It drops when
 * they move on (faster than {@link #MOVING} blocks a tick), take damage, or night falls; feeding drops it and blocks it
 * for {@link #FED_BLOCK} ticks.
 */
public final class VampireMask {
	/** Standing still this long (ticks) raises it. */
	static final int STILL_TICKS = 60;
	/** The fever it costs each second it's up. */
	static final float COST = 0.5F;
	/** Faster than this (blocks a tick, along the ground) is moving on. */
	static final double MOVING = 0.12;
	/** Feeding blocks it this long (ticks). */
	static final long FED_BLOCK = 1200L;

	/** Set while the mask is up; synced to everyone, not kept. */
	public static final AttachmentType<Boolean> MASK = AttachmentRegistry.create(JaymosKingdoms.id("vampire_mask"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** How long each vampire's stood still, and where they were last tick (not kept). */
	private static final Map<UUID, Still> STILL = new HashMap<>();
	/** No mask before this game time (not kept). */
	private static final Map<UUID, Long> BLOCKED = new HashMap<>();

	private static final class Still {
		Vec3 at = Vec3.ZERO;
		int ticks;
	}

	private VampireMask() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VampireMask::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				drop(player, false);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STILL.clear();
			BLOCKED.clear();
		});
	}

	/** Whether {@code e} wears the mask now (on either side). */
	public static boolean up(Entity e) {
		return e.hasAttached(MASK);
	}

	/** They fed: down, and blocked for a minute. */
	static void fed(ServerPlayer player) {
		BLOCKED.put(player.getUUID(), player.level().getGameTime() + FED_BLOCK);
		drop(player, false);
	}

	private static void drop(ServerPlayer player, boolean quietly) {
		STILL.remove(player.getUUID());
		if (player.hasAttached(MASK)) {
			player.removeAttached(MASK);
			if (!quietly) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mask.down").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		boolean night = Vampires.night(server.overworld().getOverworldClockTime());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Vampires.isVampire(player)) {
				if (player.hasAttached(MASK)) {
					player.removeAttached(MASK);
				}
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || night || Seizures.held(player) || player.isSleeping() || player.isPassenger()
				|| now < BLOCKED.getOrDefault(player.getUUID(), 0L)) {
				if (player.hasAttached(MASK)) {
					drop(player, !night || !player.isAlive());
				}
				STILL.remove(player.getUUID());
				continue;
			}
			Still s = STILL.computeIfAbsent(player.getUUID(), id -> new Still());
			Vec3 here = player.position();
			double speed = Math.sqrt((here.x - s.at.x) * (here.x - s.at.x) + (here.z - s.at.z) * (here.z - s.at.z));
			s.at = here;
			Input input = player.getLastClientInput();
			boolean keys = input != null && (input.forward() || input.backward() || input.left() || input.right() || input.jump());
			if (player.hasAttached(MASK)) {
				if (speed > MOVING) {
					drop(player, false);
				} else if ((now + player.getId()) % 20 == 5) {
					BloodFever.stoke(player, COST);
				}
				continue;
			}
			if (keys || speed > MOVING / 3.0 || player.isUsingItem()) {
				s.ticks = 0;
				continue;
			}
			if (++s.ticks >= STILL_TICKS) {
				player.setAttached(MASK, true);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mask.up").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
	}
}
