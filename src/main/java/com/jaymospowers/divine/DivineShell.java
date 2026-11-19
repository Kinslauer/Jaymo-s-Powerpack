package com.jaymospowers.divine;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Divine-born's golden shell (Andy: "div born"): while nothing has hurt them for {@link #QUIET_TICKS}, golden
 * absorption hearts slowly build round them, one every {@link #EVERY} ticks, up to {@link #CAP} points (ten hearts). Any
 * hit that lands starts the quiet over. A faint gold mote lifts off the halo when a heart forms.
 */
public final class DivineShell {
	/** How long nothing may have hurt them before the shell builds, and the wait between hearts (ticks). */
	public static final int QUIET_TICKS = 20 * 10, EVERY = 20 * 4;
	/** The most absorption it builds (points: ten hearts), and what a heart is worth. */
	public static final float CAP = 20.0F, HEART = 2.0F;

	private static final Identifier ROOM_ID = JaymosPowers.id("divine_shell_room");
	private static final Map<UUID, Long> HURT = new HashMap<>();
	private static final Map<UUID, Long> LAST_HEART = new HashMap<>();

	private DivineShell() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && base > 0.0F && !blocked) {
				HURT.put(player.getUUID(), player.level().getGameTime());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				boolean born = DivineSense.is(player);
				room(player, born);
				if (born && player.isAlive() && !player.isSpectator()) {
					grow(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HURT.remove(handler.player.getUUID());
			LAST_HEART.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HURT.clear();
			LAST_HEART.clear();
		});
	}

	/** The shell needs room to hold: the Divine-born's absorption ceiling is raised by {@link #CAP}, and lowered again when they are not. */
	private static void room(ServerPlayer player, boolean born) {
		AttributeInstance max = player.getAttribute(Attributes.MAX_ABSORPTION);
		if (max == null) {
			return;
		}
		if (born && !max.hasModifier(ROOM_ID)) {
			max.addOrUpdateTransientModifier(new AttributeModifier(ROOM_ID, CAP, AttributeModifier.Operation.ADD_VALUE));
		} else if (!born && max.hasModifier(ROOM_ID)) {
			max.removeModifier(ROOM_ID);
		}
	}

	private static void grow(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now - HURT.getOrDefault(player.getUUID(), 0L) < QUIET_TICKS || now - LAST_HEART.getOrDefault(player.getUUID(), 0L) < EVERY
			|| player.getAbsorptionAmount() >= CAP || player.hasEffect(MobEffects.ABSORPTION)) {
			return;
		}
		LAST_HEART.put(player.getUUID(), now);
		player.setAbsorptionAmount(Math.min(CAP, player.getAbsorptionAmount() + HEART));
		level.sendParticles(MagicFx.mote(0xFFD76A, 0.7F), player.getX(), player.getY() + player.getBbHeight() + 0.35, player.getZ(), 5, 0.2, 0.05, 0.2, 0.01);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.25F, 1.9F);
	}
}
