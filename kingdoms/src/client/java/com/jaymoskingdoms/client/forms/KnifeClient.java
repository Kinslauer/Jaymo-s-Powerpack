package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.forms.Bolas;
import com.jaymoskingdoms.forms.KnifeForms;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

/**
 * (0.7.383) The Knife school on the client: the Marked Quarry's outline, drawn through walls for this player alone (the foe's
 * own glowing flag is set on this client only and held there each tick, so nobody else sees it and the Glowing effect isn't
 * used), and the thrown bolas.
 */
public final class KnifeClient {
	/** Entity id → the client game tick the outline ends. */
	private static final Map<Integer, Long> MARKED = new HashMap<>();

	private KnifeClient() {
	}

	public static void register() {
		EntityRenderers.register(Bolas.TYPE, context -> new ThrownItemRenderer<>(context, 0.8F, false));
		ClientPlayNetworking.registerGlobalReceiver(KnifeForms.Mark.TYPE, (payload, context) -> {
			Minecraft minecraft = context.client();
			if (minecraft.level == null) {
				return;
			}
			if (payload.ticks() > 0) {
				MARKED.put(payload.entity(), minecraft.level.getGameTime() + payload.ticks());
			} else {
				MARKED.remove(payload.entity());
				release(minecraft, payload.entity());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(KnifeClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> MARKED.clear());
	}

	private static void tick(Minecraft minecraft) {
		if (MARKED.isEmpty() || minecraft.level == null) {
			return;
		}
		long now = minecraft.level.getGameTime();
		for (Iterator<Map.Entry<Integer, Long>> it = MARKED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Long> entry = it.next();
			Entity entity = minecraft.level.getEntity(entry.getKey());
			if (entry.getValue() <= now || entity == null || !entity.isAlive()) {
				it.remove();
				release(minecraft, entry.getKey());
			} else if (!entity.hasGlowingTag()) {
				// (the server's own updates of its flags would put this back)
				entity.setGlowingTag(true);
			}
		}
	}

	/** Lets go of the outline, unless the server really has the foe glowing. */
	private static void release(Minecraft minecraft, int id) {
		if (minecraft.level != null && minecraft.level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity living
			&& !living.hasEffect(MobEffects.GLOWING)) {
			living.setGlowingTag(false);
		}
	}
}
