package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Locks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * This client's side of a locked door that was pushed, not used ({@link Locks#pushBarred}): walking into a Spooky Doors
 * door locked against you doesn't swing it, so it's said here, as the server says it for a click: the lock's words on the
 * action bar and its rattle, at most every {@link #EVERY_MS}.
 */
final class LocksClient {
	/** A pushed lock speaks up at most this often (ms). */
	static final long EVERY_MS = 2000L;

	private static long told;

	private LocksClient() {
	}

	static void register() {
		Locks.clientRefused = LocksClient::refused;
	}

	private static void refused(Level level, BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		Locks.Fitted f = Locks.lockOn(level, pos);
		if (mc.player == null || f == null || now - told < EVERY_MS) {
			return;
		}
		told = now;
		mc.player.sendOverlayMessage(Locks.refusal(f));
		level.playLocalSound(pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6F, 1.0F, false);
	}
}
