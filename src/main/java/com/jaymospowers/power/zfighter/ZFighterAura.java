package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.46, Andy's NOEA picks) Which look a Z Fighter's ki aura has, picked on the training screen: Sparking (the flame
 * aura with electric sparks on it, the default), Smooth (a calm glow round the outline) or Godly (tall thin flickering
 * wisps with rising motes). (0.17.83) All three are wisps now, none a shell. Kept for good and through death, and synced to everyone near, since others see it. The
 * client draws it ({@code ZFighterKiAura}); when it shows is the same for all three.
 */
public final class ZFighterAura {
	public static final int SPARKING = 0, SMOOTH = 1, GODLY = 2, STYLES = 3;
	public static final String[] KEYS = {"sparking", "smooth", "godly"};

	public static final AttachmentType<Integer> STYLE = AttachmentRegistry.create(JaymosPowers.id("zfighter_aura_style"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private ZFighterAura() {
	}

	/** His style, whatever is saved. Both sides. */
	public static int style(Player player) {
		// (0.17.50) the god form's aura is always the calm one
		if (Integer.valueOf(ZFighterForms.GOD).equals(player.getAttached(ZFighterPower.FORM))) {
			return SMOOTH;
		}
		return Math.floorMod(player.getAttachedOrElse(STYLE, SPARKING), STYLES);
	}

	/** Server: take the style the training screen picked (ignored when out of range). */
	static void pick(Player player, int style) {
		if (style >= 0 && style < STYLES && style != style(player)) {
			player.setAttached(STYLE, style);
		}
	}
}
