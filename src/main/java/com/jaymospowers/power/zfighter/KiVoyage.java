package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Flying to a near world on your own ki (Andy's Buder's Lanterns pick, ideas only). A Z Fighter of {@link #POWER_GATE} power or more,
 * flying, who climbs {@link #ABOVE_TOP} blocks past the top of the world and holds their gaze straight up for {@link #HOLD_TICKS},
 * pushes off into the dark: {@link #KI_SHARE} of their ki is burned, the flight shows as a pod trip's does (their own light in
 * place of the pod), and they come down on the other side. Only between Home and the Pale Moon, the one world near enough:
 * the rest still need a pod, and pods stay rare. Nothing hurts them on the way.
 */
public final class KiVoyage {
	// ---- tuning ----
	/** The power level it takes (a recruit to the Patrol is 1500), and the share of their ki it burns. Andy to set the gate. */
	public static final int POWER_GATE = 50_000;
	static final float KI_SHARE = 0.8F;
	/** How far over the world's top they must climb (blocks), how long they hold their gaze up (ticks), and how far up (pitch). */
	static final int ABOVE_TOP = 64, HOLD_TICKS = 60;
	static final float LOOK_UP = -60.0F;
	/** How long the crossing takes (ticks). */
	static final int TICKS = 240;

	private record Voyage(int to, long until, Vec3 hold) {
	}

	private static final Map<UUID, Voyage> VOYAGES = new HashMap<>();
	private static final Map<UUID, Integer> GAZING = new HashMap<>();

	private KiVoyage() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(KiVoyage::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VOYAGES.clear();
			GAZING.clear();
		});
	}

	/** {@code player} is crossing on their ki now (nothing hurts them). */
	static boolean voyaging(ServerPlayer player) {
		return VOYAGES.containsKey(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Voyage v = VOYAGES.get(player.getUUID());
			if (v != null) {
				if (player.level().getGameTime() >= v.until()) {
					VOYAGES.remove(player.getUUID());
					land(player, v.to());
				} else {
					// held up in the dark while the crossing shows
					player.teleportTo(v.hold().x, v.hold().y, v.hold().z);
					player.setDeltaMovement(Vec3.ZERO);
					player.resetFallDistance();
				}
				continue;
			}
			int to = destination(player);
			if (to == Integer.MIN_VALUE || player.getXRot() > LOOK_UP || player.getAttached(ZFighterPower.FLIGHT) == null
				|| player.getY() < player.level().getMaxY() + ABOVE_TOP) {
				GAZING.remove(player.getUUID());
				continue;
			}
			int held = GAZING.merge(player.getUUID(), 1, Integer::sum);
			if (held == HOLD_TICKS) {
				start(player, to);
			}
		}
	}

	/** Where a crossing from here goes: Home to the Pale Moon and back; none ({@link Integer#MIN_VALUE}) from anywhere else. */
	private static int destination(ServerPlayer player) {
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			return Integer.MIN_VALUE;
		}
		ResourceKey<Level> here = player.level().dimension();
		if (here == Level.OVERWORLD) {
			return ZFighterSpace.MOON;
		}
		return here == ZFighterSpace.DESTS[ZFighterSpace.MOON].level() ? ZFighterSpace.HOME_INDEX : Integer.MIN_VALUE;
	}

	private static void start(ServerPlayer player, int to) {
		if (ZFighterScouter.powerLevel(player) < POWER_GATE) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.ki_voyage.weak").withStyle(ChatFormatting.GRAY));
			return;
		}
		ZFighterPower.Live live = ZFighterPower.live(player);
		if (!ZFighterPower.spend(player, live, ZFighterPower.maxKi(player) * KI_SHARE)) {
			return;
		}
		if (to == ZFighterSpace.MOON && player.level().dimension() == Level.OVERWORLD) {
			// where they left from, so the way back brings them down there
			player.setAttached(ZFighterSpace.HOME, new ZFighterSpace.Home(Level.OVERWORLD.identifier().toString(), player.blockPosition()));
		}
		VOYAGES.put(player.getUUID(), new Voyage(to, player.level().getGameTime() + TICKS, player.position()));
		ServerPlayNetworking.send(player, new ZFighterPodTrip.Flight(to, TICKS, false, false, true));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.6F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.ki_voyage.start").withStyle(ChatFormatting.AQUA));
	}

	/** Down on the other side: the Pale Moon by its pad, or Home where they left from. */
	private static void land(ServerPlayer player, int to) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level;
		BlockPos near;
		if (to == ZFighterSpace.HOME_INDEX) {
			ZFighterSpace.Home home = player.getAttached(ZFighterSpace.HOME);
			Identifier id = home == null ? null : Identifier.tryParse(home.dimension());
			ServerLevel there = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
			level = there != null ? there : server.overworld();
			near = there != null ? home.pos() : level.getRespawnData().pos();
		} else {
			ZFighterSpace.Dest dest = ZFighterSpace.DESTS[to];
			level = server.getLevel(dest.level());
			if (level == null) {
				return;
			}
			near = ZFighterSpace.pad(level, dest).offset(4, 0, 0);
		}
		BlockPos ground = ground(level, near);
		player.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, java.util.Set.of(), player.getYRot(), 0.0F, true);
		player.resetFallDistance();
		ZFighterSpace.arrived(player, to == ZFighterSpace.HOME_INDEX ? null : ZFighterSpace.DESTS[to]);
	}

	private static BlockPos ground(ServerLevel level, BlockPos near) {
		level.getChunk(near.getX() >> 4, near.getZ() >> 4);
		@Nullable BlockPos top = ZFighterSpace.surface(level, near.getX(), near.getZ());
		return top != null ? top : near;
	}
}
