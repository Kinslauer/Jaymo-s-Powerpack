package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Not dead yet (Jaymo's Kingdoms' Stubborn perk, which marks its owner with {@link #TAG}): once a day, the blow that would have
 * killed you leaves you downed ({@link Downed}) as it does anyone, and then for {@link #WINDOW_TICKS} (40 s) you mend fast
 * though you can't stand: {@link #HEAL_PER_TICK} a tick, the bleeding bound as it goes. When the window ends you rise. Killed
 * again inside it, you die for real (the downed die as ever). It acts only on a downing that happened this very tick, after
 * every other save has had its say, so it never doubles with Chrono's cheat death or another power's rescue.
 */
public final class Stubborn {
	// ---- tuning ----
	/** The window (ticks), and the health mended each tick of it (half hearts). */
	public static final int WINDOW_TICKS = 800;
	public static final float HEAL_PER_TICK = 0.05F;
	/** How often it can save you (ticks): a day. */
	public static final long EVERY = 24000L;
	/** The scoreboard tag Kingdoms puts on a player with the perk. */
	public static final String TAG = "jaymospowers_stubborn";

	/** When it last saved this player (game time). Saved, kept through death. */
	static final AttachmentType<Long> LAST = AttachmentRegistry.create(JaymosPowers.id("stubborn_last"), builder -> builder.persistent(Codec.LONG).copyOnDeath());

	static final Identifier PHASE = JaymosPowers.id("stubborn");

	/** Player → the game time their window ends. */
	private static final Map<UUID, Long> WINDOWS = new HashMap<>();

	private Stubborn() {
	}

	static void register() {
		// after the downed's own phases: only a downing that went through, this tick, is ours
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Downed.LIE_PHASE, PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(PHASE, (entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !player.entityTags().contains(TAG) || !Downed.isDowned(player)
				|| !(player.level() instanceof ServerLevel level)) {
				return true;
			}
			Long downed = player.getAttached(Downed.DOWNED);
			long now = level.getGameTime();
			Long last = player.getAttached(LAST);
			if (downed == null || (downed >> 1) != now || last != null && now >= last && now - last < EVERY) {
				return true;
			}
			player.setAttached(LAST, now);
			WINDOWS.put(player.getUUID(), now + WINDOW_TICKS);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.4F, 0.6F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.stubborn.down").withStyle(ChatFormatting.GOLD));
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(Stubborn::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> WINDOWS.clear());
	}

	/** Whether {@code player} is mending in the window now. */
	public static boolean mending(ServerPlayer player) {
		return WINDOWS.containsKey(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (WINDOWS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Long>> it = WINDOWS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || !Downed.isDowned(player) || !(player.level() instanceof ServerLevel level)) {
				// dead for real, brought round, or gone: the window is over
				it.remove();
				continue;
			}
			// (the downed don't heal through heal(): the mending is set directly)
			player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + HEAL_PER_TICK));
			if (level.getGameTime() >= entry.getValue()) {
				it.remove();
				Downed.getUp(level, player);
				if (!Downed.isDowned(player)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.stubborn.up").withStyle(ChatFormatting.GOLD));
				}
			}
		}
	}
}
