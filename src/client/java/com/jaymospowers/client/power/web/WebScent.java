package com.jaymospowers.client.power.web;

import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteBody;
import com.jaymospowers.power.web.WebSymbioteState;
import com.jaymospowers.power.web.WebSymbioteStrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * The black strain's scent, the red strain's heartbeat sense tuned to blood: with the black one bonded (and the bond
 * high enough, the same as the red's), living things within {@link #RANGE} blocks show through walls on your own
 * screen, the wounded ones (under {@link #WOUNDED_SHARE} of their health, or bleeding where that is known) in red and
 * the rest in a dim grey. It is only a sense: it never leads the symbiote to feed. Drawn with the outline the other
 * senses use; nobody else sees anything.
 */
public final class WebScent {
	/** How far it reaches (blocks). */
	public static final double RANGE = 24.0;
	/** Under this share of its health a thing counts as wounded. */
	static final float WOUNDED_SHARE = 0.6F;
	/** The colours (ARGB): the wounded, and everything else. */
	static final int WOUNDED_COLOR = 0xFFD01818, WELL_COLOR = 0xFF5E5E66;

	private WebScent() {
	}

	/** Whether {@code self} has the scent: the black strain, bonded, its bond high enough (or creative). */
	public static boolean has(LocalPlayer self) {
		WebSymbioteState state = WebSymbiote.state(self);
		return state.bonded() && state.kind() == WebSymbioteStrain.BLACK && (self.isCreative() || state.bond() >= WebSymbioteBody.SENSE_BOND);
	}

	/** The outline colour for {@code entity} on our screen, or 0. */
	public static int outline(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		if (self == null || entity == self || !(entity instanceof LivingEntity living) || entity instanceof ArmorStand || !living.isAlive()
			|| entity.distanceToSqr(self) > RANGE * RANGE || !has(self)) {
			return 0;
		}
		boolean wounded = living.getHealth() < living.getMaxHealth() * WOUNDED_SHARE || Bleeding.bleeding(living) != 0;
		return wounded ? WOUNDED_COLOR : WELL_COLOR;
	}
}
