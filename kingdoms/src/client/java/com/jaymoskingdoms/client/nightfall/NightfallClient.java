package com.jaymoskingdoms.client.nightfall;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.nightfall.Blightwing;
import com.jaymoskingdoms.nightfall.NightBleeder;
import com.jaymoskingdoms.nightfall.Nightfall;
import com.jaymoskingdoms.nightfall.Nightfalls;
import com.jaymoskingdoms.nightfall.VampireBat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The client's side of the Nightfall Plague ({@link Nightfall}): the Night Bleeder, Blightwing and vampire bat
 * renderers, and the Nightfall's pale miasma (a fog that closes in, {@code MiasmaFogMixin}, and ash drifting).
 */
public final class NightfallClient {
	/** The miasma: how far you can see in it (blocks), how long it takes to roll in or out (ticks), its colour. */
	public static final float SIGHT = 36.0F;
	static final int FADE_TICKS = 100;
	public static final float[] COLOUR = {0.80F, 0.80F, 0.84F};
	/** Ash drifting round you in it, a tick. */
	static final int ASH_PER_TICK = 3;

	/** How thick the miasma is now (0-1). */
	public static float miasma;

	private NightfallClient() {
	}

	public static void register() {
		Identifier[] bleeders = new Identifier[NightBleeder.VARIANTS], wings = new Identifier[NightBleeder.VARIANTS];
		for (int i = 0; i < NightBleeder.VARIANTS; i++) {
			bleeders[i] = JaymosKingdoms.id("textures/entity/nightfall/night_bleeder_" + i + ".png");
			wings[i] = JaymosKingdoms.id("textures/entity/nightfall/blightwing_" + i + ".png");
		}
		Identifier bat = JaymosKingdoms.id("textures/entity/nightfall/vampire_bat.png");
		EntityRenderers.register(Nightfall.NIGHT_BLEEDER, context -> new NightBleederRenderer(context,
			new IllagerModel<>(IllagerModel.createBodyLayer().bakeRoot()), bleeders));
		EntityRenderers.register(Nightfall.BLIGHTWING, context -> new NightBatRenderer<Blightwing>(context,
			new BatModel(BatModel.createBodyLayer().bakeRoot()), 0.6F, 2.2F, state -> wings[Math.floorMod(state.variant, wings.length)]));
		EntityRenderers.register(Nightfall.VAMPIRE_BAT, context -> new NightBatRenderer<VampireBat>(context,
			new BatModel(BatModel.createBodyLayer().bakeRoot()), 0.25F, 1.1F, state -> bat));

		ClientPlayNetworking.registerGlobalReceiver(Nightfalls.Sync.TYPE, (payload, context) -> Nightfalls.clientActive = payload.active());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Nightfalls.clientActive = false;
			miasma = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(NightfallClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean on = Nightfalls.clientActive && player != null && player.level().dimension() == Level.OVERWORLD;
		float step = 1.0F / FADE_TICKS;
		miasma = on ? Math.min(1.0F, miasma + step) : Math.max(0.0F, miasma - step);
		if (miasma < 0.3F || player == null || minecraft.isPaused()) {
			return;
		}
		RandomSource random = player.getRandom();
		for (int i = 0; i < ASH_PER_TICK; i++) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 24.0, y = player.getY() + random.nextDouble() * 10.0 - 2.0,
				z = player.getZ() + (random.nextDouble() - 0.5) * 24.0;
			player.level().addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0, -0.01, 0.0);
		}
	}

	/** Whether the local player is looking through a spyglass: then the Night Bleeders aren't there. */
	static boolean scoping() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.isScoping();
	}
}
