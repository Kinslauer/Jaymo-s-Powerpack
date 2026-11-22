package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Holding back your power (Z Fighter gaps 2): how much of his power a Z Fighter lets out, in quarters from 0 to 100%.
 * Sneak+R lowers it a quarter; charging (R held) raises it a quarter every {@link #CHARGE_TICKS}. His fists
 * ({@link ZFighterPower#refresh}) and his ki techniques ({@link ZFighterPower#kiPower}) scale with it. Held back at all,
 * his blows never kill anything but a player (who goes down as ever): a foe is left at {@link #SPARED_HEALTH}, to spar with,
 * take alive or knock out. Not saved: a fighter who logs back in lets out all of it.
 */
public final class ZFighterRelease {
	/** Quarters: 4 is all of it. */
	static final int STEPS = 4;
	/** Charging raises it a quarter this often (ticks). */
	static final int CHARGE_TICKS = 20;
	/** What a foe is left with when a held-back blow would have killed it. */
	static final float SPARED_HEALTH = 1.0F;

	private static final Map<UUID, Integer> LET_OUT = new HashMap<>();

	private ZFighterRelease() {
	}

	/** From {@link ZFighterPower#init}. */
	static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof Player) && source.getEntity() instanceof ServerPlayer player && player != entity && holding(player)
				&& Powers.has(player, ZFighterPower.INSTANCE)) {
				entity.setHealth(SPARED_HEALTH);
				return false;
			}
			return true;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LET_OUT.clear());
	}

	static int quarters(Player player) {
		return LET_OUT.getOrDefault(player.getUUID(), STEPS);
	}

	/** The share of his power he lets out, 0-1. */
	static float factor(Player player) {
		return quarters(player) / (float)STEPS;
	}

	static boolean holding(Player player) {
		return quarters(player) < STEPS;
	}

	/** Sneak+R: a quarter less. */
	static void lower(ServerPlayer player) {
		set(player, Math.max(0, quarters(player) - 1));
	}

	/** Each tick he charges: a quarter more every {@link #CHARGE_TICKS}, up to all of it. */
	static void charge(ServerPlayer player) {
		if (holding(player) && player.tickCount % CHARGE_TICKS == 0) {
			set(player, quarters(player) + 1);
		}
	}

	private static void set(ServerPlayer player, int value) {
		if (value >= STEPS) {
			LET_OUT.remove(player.getUUID());
		} else {
			LET_OUT.put(player.getUUID(), value);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_let_out", value * 100 / STEPS).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F,
			0.6F + 0.2F * value);
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}
}
