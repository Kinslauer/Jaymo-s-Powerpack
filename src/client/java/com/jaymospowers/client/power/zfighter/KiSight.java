package com.jaymospowers.client.power.zfighter;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.70) Ki is seen only by those who carry it themselves. Andy's universe rules (from the Gemini handoff): "it is
 * only visible to other sensitives". The sensitive are Jaymo's Kingdoms' attuned ({@code Mana.ATTUNED}, synced to
 * everyone; a Z Fighter attunes the moment the power is theirs, a Wind Controller once it wakes, casters and the
 * mana-touched as before). A Z Fighter's aura and glow ({@link ZFighterKiAura}, {@link ZFighterKiGlow}) are drawn only
 * for a viewer who is attuned, and always for your own body. Without Kingdoms everyone sees them, as before.
 */
public final class KiSight {
	/** Set false to show every aura to everyone again. */
	static final boolean ONLY_THE_ATTUNED = true;

	private static boolean looked;
	private static @Nullable AttachmentType<Boolean> attuned;

	private KiSight() {
	}

	/** Whether the local player sees the ki on {@code target}. */
	static boolean sees(Entity target) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ONLY_THE_ATTUNED || minecraft.player == null || target == minecraft.player) {
			return true;
		}
		AttachmentType<Boolean> type = type();
		return type == null || Boolean.TRUE.equals(minecraft.player.getAttached(type));
	}

	@SuppressWarnings("unchecked")
	private static @Nullable AttachmentType<Boolean> type() {
		if (!looked) {
			looked = true;
			try {
				attuned = (AttachmentType<Boolean>) Class.forName("com.jaymoskingdoms.Mana").getField("ATTUNED").get(null);
			} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
				attuned = null;
			}
		}
		return attuned;
	}
}
