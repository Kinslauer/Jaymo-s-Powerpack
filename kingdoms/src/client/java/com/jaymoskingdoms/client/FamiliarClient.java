package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FamiliarBodies;
import com.jaymoskingdoms.Familiars;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.entity.RabbitRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * The client's half of a familiar ({@link Familiars}): the key held to see through its eyes (default Page Down), the camera
 * that follows it while the server says so, the bodies drawn in their own coats, and the scrying bowl's screen.
 */
public final class FamiliarClient {
	private static KeyMapping eyes;
	private static boolean down;
	/** The entity id the server wants us to look through, or -1; the ticks it has been missing from the world. */
	private static int seeing = -1, missing;
	/** How long (ticks) a missing familiar is waited for before the camera comes home. */
	private static final int PATIENCE = 40;

	private FamiliarClient() {
	}

	private static Identifier skin(String kind) {
		return JaymosKingdoms.id("textures/entity/familiar/" + kind + ".png");
	}

	private static final class Bird extends ParrotRenderer {
		private final Identifier texture;

		Bird(EntityRendererProvider.Context context, String kind) {
			super(context);
			this.texture = skin(kind);
		}

		@Override
		public Identifier getTextureLocation(ParrotRenderState state) {
			return this.texture;
		}
	}

	private static final class Puss extends CatRenderer {
		Puss(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		public Identifier getTextureLocation(CatRenderState state) {
			return skin("cat");
		}
	}

	private static final class Rat extends RabbitRenderer {
		Rat(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		public Identifier getTextureLocation(RabbitRenderState state) {
			return skin("rat");
		}
	}

	private static final class Toad extends FrogRenderer {
		Toad(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		public Identifier getTextureLocation(FrogRenderState state) {
			return skin("frog");
		}
	}

	public static void register() {
		EntityRenderers.register(FamiliarBodies.OWL, c -> new Bird(c, "owl"));
		EntityRenderers.register(FamiliarBodies.RAVEN, c -> new Bird(c, "raven"));
		EntityRenderers.register(FamiliarBodies.CAT, Puss::new);
		EntityRenderers.register(FamiliarBodies.RAT, Rat::new);
		EntityRenderers.register(FamiliarBodies.FROG, Toad::new);
		eyes = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.familiar_eyes", InputConstants.KEY_PAGEDOWN,
			ClassKeys.category()));
		ClientPlayNetworking.registerGlobalReceiver(Familiars.Seeing.TYPE, (payload, context) -> context.client().execute(() -> {
			seeing = payload.entity();
			missing = 0;
			if (seeing < 0) {
				home(context.client());
			}
		}));
		ClientPlayNetworking.registerGlobalReceiver(com.jaymoskingdoms.Scrying.View.TYPE, (payload, context) -> context.client().execute(() -> {
			if (context.client().gui.screen() instanceof ScryScreen s) {
				s.update(payload);
			} else {
				context.client().gui.setScreen(new ScryScreen(payload));
			}
		}));
		ClientPlayNetworking.registerGlobalReceiver(com.jaymoskingdoms.Scrying.Close.TYPE, (payload, context) -> context.client().execute(() -> {
			if (context.client().gui.screen() instanceof ScryScreen s) {
				s.closeQuietly();
			}
		}));
		ClientTickEvents.END_CLIENT_TICK.register(FamiliarClient::tick);
	}

	private static void home(Minecraft mc) {
		if (mc.player != null && mc.getCameraEntity() != mc.player) {
			mc.setCameraEntity(mc.player);
		}
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			seeing = -1;
			down = false;
			return;
		}
		boolean want = eyes.isDown() && mc.gui.screen() == null;
		if (want != down) {
			down = want;
			ClientPlayNetworking.send(new Familiars.Eyes(want));
		}
		if (seeing >= 0) {
			Entity e = mc.level.getEntity(seeing);
			if (e == null || !e.isAlive()) {
				if (++missing > PATIENCE) {
					seeing = -1;
					home(mc);
				}
			} else {
				missing = 0;
				if (mc.getCameraEntity() != e) {
					mc.setCameraEntity(e);
				}
			}
		}
	}
}
