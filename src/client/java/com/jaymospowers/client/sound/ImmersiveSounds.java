package com.jaymospowers.client.sound;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.JaymosPowersClient;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The world's small sounds, our own take on what ImmersiveFX: Resurrected gave the pack before it came out for lag
 * (PR #1361; it is AGPL and its sounds are recorded, so nothing of it is used: the takes are made by
 * tools/gen_immersive_sounds.py). {@link Footfalls}, {@link Jets}, {@link Workstations}, {@link ItemDrops} and the bed
 * rustles all play through {@link #play}, which keeps them cheap:
 * <ul>
 * <li>nothing farther than {@link #REACH} from the camera is played at all;</li>
 * <li>at most {@link #PER_TICK} of them start in one tick;</li>
 * <li>none start while the sound engine's channels are more than {@link #ROOM} full (read once a tick from its own
 * "Sounds: x/y" count), so these extras never crowd out a real sound, which is what silenced pickups beside ImmersiveFX.</li>
 * </ul>
 * Every one of them shows in the {@link SoundBrowserScreen} and can be turned down or off there.
 */
public final class ImmersiveSounds {
	/** Farther than this from the camera (blocks) and they aren't played. */
	public static final double REACH = 20.0;
	/** At most this many of our extras start in one tick. */
	static final int PER_TICK = 6;
	/** Played only while fewer than this share of the engine's static channels are in use. */
	static final float ROOM = 0.7F;

	private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
	private static int startedThisTick;
	private static long tick;
	private static long roomCheckedAt = -1;
	private static boolean room = true;

	private ImmersiveSounds() {
	}

	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			tick++;
			startedThisTick = 0;
		});
		// the sound browser's own key, unbound until you pick one (it's also in Music & Sounds)
		KeyMapping browser = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.sound_browser",
			InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (browser.consumeClick()) {
				if (mc.gui.screen() == null) {
					mc.gui.setScreen(new SoundBrowserScreen(null));
				}
			}
		});
		Jets.init();
		Workstations.init();
		ItemDrops.init();
	}

	/** Client ticks since start. */
	public static long tick() {
		return tick;
	}

	/** Our event {@code jaymospowers:immersive.<name>} (see sounds.json). */
	public static SoundEvent event(String name) {
		return EVENTS.computeIfAbsent(name, n -> SoundEvent.createVariableRangeEvent(JaymosPowers.id("immersive." + n)));
	}

	/** Whether one more extra may start now (within {@link #PER_TICK} and the channel {@link #ROOM}). */
	public static boolean spare() {
		return startedThisTick < PER_TICK && roomy();
	}

	private static boolean roomy() {
		if (roomCheckedAt != tick) {
			roomCheckedAt = tick;
			room = parseRoom(Minecraft.getInstance().getSoundManager().getChannelDebugString());
		}
		return room;
	}

	/** "Sounds: used/max + used/max": whether the first pool is under {@link #ROOM} full. */
	static boolean parseRoom(String line) {
		try {
			int colon = line.indexOf(':'), slash = line.indexOf('/', colon), plus = line.indexOf('+', slash);
			int used = Integer.parseInt(line.substring(colon + 1, slash).trim());
			int max = Integer.parseInt(line.substring(slash + 1, plus < 0 ? line.length() : plus).trim());
			return max <= 0 || used < max * ROOM;
		} catch (RuntimeException e) {
			return true;
		}
	}

	/** Plays {@code jaymospowers:immersive.<name>} at a spot, for this client alone, if it's near and there's room. */
	public static boolean play(ClientLevel level, double x, double y, double z, String name, SoundSource source, float volume, float pitch) {
		if (volume <= 0.01F || !spare()) {
			return false;
		}
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		if (camera.distanceToSqr(x, y, z) > REACH * REACH) {
			return false;
		}
		startedThisTick++;
		level.playLocalSound(x, y, z, event(name), source, volume, pitch, false);
		return true;
	}
}
