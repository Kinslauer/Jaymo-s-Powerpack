package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.biome.Biome;

/**
 * Downpours (Andy's ImmersiveVFX pick, built as our own): at the heart of a gathered storm cell the rain comes down far
 * heavier ({@link Storms#downpour}). Andy: "making you wetter faster, reducing body heat and slowing you slightly". Out
 * under one you are soaked through at once and stay wet three times as long as plain rain leaves you (Kitchen's body
 * heat, by reflection), you lose more heat than in rain, and you walk a little slower. A roof stops all three. The
 * client thickens the rain and closes the view in ({@code SkiesClient}).
 */
public final class Downpours {
	/** How often players are seen to (ticks). */
	static final int EVERY = 20;
	/** A downpour this hard or harder counts. */
	static final float FELT = 0.25F;
	/** How long a soaking lasts (ticks): Kitchen's rain leaves you wet for 1200. */
	static final int SOAK_TICKS = 3600;
	/** Body heat lost at the full downpour, on top of the rain's own (Kitchen's rain is -15). */
	static final double BODY_DOWNPOUR = 10.0;
	/** How much slower you walk at the full downpour (a share of your speed). */
	static final double SLOW = 0.08;

	private static final Identifier SLOW_ID = JaymosKingdoms.id("downpour");
	private static Method soak;
	private static boolean looked;

	private Downpours() {
	}

	/** How hard the downpour falls on {@code p}, 0..1: nothing under a roof, in the dry or where it would snow. */
	public static float on(ServerPlayer p) {
		float hard = Storms.downpour(p.level(), p.getX(), p.getZ());
		if (hard < FELT) {
			return 0.0F;
		}
		BlockPos head = BlockPos.containing(p.getEyePosition());
		return p.level().canSeeSky(head) && p.level().precipitationAt(head) == Biome.Precipitation.RAIN ? hard : 0.0F;
	}

	/** What a downpour does to body heat (Kitchen's {@code BodyHeat.addWeather}). */
	public static double bodyHeat(ServerPlayer p) {
		return -BODY_DOWNPOUR * on(p);
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			float hard = p.isSpectator() || p.isCreative() ? 0.0F : on(p);
			AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				if (hard <= 0.0F) {
					speed.removeModifier(SLOW_ID);
				} else {
					speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, -SLOW * hard, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
			if (hard > 0.0F) {
				soak(p);
			}
		}
	}

	private static void soak(ServerPlayer p) {
		if (!looked) {
			looked = true;
			try {
				soak = Class.forName("com.jaymoskitchen.climate.BodyHeat").getMethod("soak", ServerPlayer.class, int.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosKingdoms.LOGGER.info("No Jaymo's Kitchen soaking for downpours: {}", e.toString());
			}
		}
		if (soak != null) {
			try {
				soak.invoke(null, p, SOAK_TICKS);
			} catch (ReflectiveOperationException e) {
				soak = null;
			}
		}
	}
}
