package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.bomb.Bombs;
import com.jaymospowers.alchemy.oil.OilKind;
import com.jaymospowers.alchemy.oil.Oils;
import com.jaymospowers.core.fx.MagicFx;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the oils and bombs (Hunter's Road, patch 7): the thrown bomb's renderer, the oil's line on a blade's tooltip, and
 * the <b>sheen</b> on a held blade with oil on it: besides the shimmer every oiled stack has ({@code OilFoilMixin}), a glint in the
 * oil's colour runs off the blade in anyone's hand (seen on every player, since the oil is on the stack the server shows).
 */
public final class OilClient {
	/** Ticks between glints from one blade, and how far along the blade's reach they start (blocks). */
	private static final int GLINT_EVERY = 5;
	private static final double REACH = 0.9;

	private OilClient() {
	}

	public static void init() {
		EntityRenderers.register(Bombs.TYPE, ThrownItemRenderer::new);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Oils.Oiled o = stack.get(Oils.OILED);
			OilKind kind = o == null ? null : o.oil();
			if (kind != null) {
				lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.oil_coat", Component.translatable(kind.flask.getDescriptionId()),
					o.swings()).withStyle(ChatFormatting.GOLD));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientLevel level = client.level;
			if (level == null || client.isPaused()) {
				return;
			}
			for (Player player : level.players()) {
				if (player.tickCount % GLINT_EVERY != 0 || player.isSpectator()) {
					continue;
				}
				ItemStack held = player.getMainHandItem();
				Oils.Oiled o = held.get(Oils.OILED);
				OilKind kind = o == null ? null : o.oil();
				if (kind == null) {
					continue;
				}
				Vec3 look = player.getLookAngle();
				Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize().scale(player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? -0.35 : 0.35);
				double along = level.getRandom().nextDouble() * REACH;
				Vec3 at = player.getEyePosition().add(look.scale(0.4 + along)).add(side).add(0.0, -0.45, 0.0);
				level.addParticle(MagicFx.mote(kind.color, 0.5F), at.x, at.y, at.z, 0.0, 0.01, 0.0);
			}
		});
	}
}
