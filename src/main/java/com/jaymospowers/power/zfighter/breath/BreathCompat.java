package com.jaymospowers.power.zfighter.breath;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

/**
 * (0.18.48, SevTech picks) Glacia's cold. Glacia is a world of snow, so Jaymo's Kitchen's body heat (the winter cold rule: lands
 * that are cold chill you, shivering burns food, deep cold frostbites; warm clothing, a hot meal and fires keep it off) already
 * reaches it through its biomes. This adds {@link #CHILL} on top there, so no corner of Glacia is mild, by the weather hook Kitchen
 * offers other mods ({@code BodyHeat.addWeather}, read by reflection: a Kitchen without it is simply left alone), less for each
 * warm piece worn ({@link #WARM_PIECE}, Kitchen's {@code #jaymoskitchen:warm_clothing}). Without Kitchen's body heat, Glacia's air
 * costs hunger instead ({@link #FALLBACK_EXHAUSTION} a second).
 */
public final class BreathCompat {
	// ---- tuning ----
	/** The extra chill on Glacia, in body heat (Kitchen: chilly -35, freezing -60), and how much each warm piece takes off it. */
	public static final double CHILL = 25.0, WARM_PIECE = 6.0;
	/** Under a roof only this share of the chill. */
	public static final double SHELTERED = 0.5;
	/** Hunger a second on Glacia when Kitchen's body heat isn't there to do the cold. */
	static final float FALLBACK_EXHAUSTION = 0.03F;

	private static final TagKey<Item> WARM_CLOTHING = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("jaymoskitchen", "warm_clothing"));
	private static boolean hooked;

	private BreathCompat() {
	}

	static void init() {
		if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			try {
				Class.forName("com.jaymoskitchen.climate.BodyHeat").getMethod("addWeather", ToDoubleFunction.class)
					.invoke(null, (ToDoubleFunction<ServerPlayer>) BreathCompat::glaciaChill);
				hooked = true;
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				JaymosPowers.LOGGER.info("Jaymo's Kitchen has no body heat to hook Glacia's cold on: {}", e.toString());
			}
		}
		if (!hooked) {
			ServerTickEvents.END_SERVER_TICK.register(BreathCompat::fallback);
		}
	}

	/** Body heat Glacia adds for {@code player} (negative: cold), 0 anywhere else. */
	static double glaciaChill(ServerPlayer player) {
		if (player.level().dimension() != ZFighterSpace.DESTS[ZFighterSpace.GLACIA].level()) {
			return 0.0;
		}
		double chill = CHILL * (player.level().canSeeSky(player.blockPosition().above()) ? 1.0 : SHELTERED);
		int warm = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (player.getItemBySlot(slot).is(WARM_CLOTHING)) {
				warm++;
			}
		}
		return -Math.max(0.0, chill - warm * WARM_PIECE);
	}

	private static void fallback(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel glacia = server.getLevel(ZFighterSpace.DESTS[ZFighterSpace.GLACIA].level());
		if (glacia == null) {
			return;
		}
		for (ServerPlayer player : glacia.players()) {
			if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
				player.causeFoodExhaustion(FALLBACK_EXHAUSTION);
			}
		}
	}
}
