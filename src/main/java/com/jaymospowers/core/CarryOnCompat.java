package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.web.KitchenCompat;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.17, Andy: "We need to swap our block carrying for this", Carry On) Blocks are carried by Carry On (LGPL-3.0,
 * Tschipp; shipped unchanged, nothing of it copied): sneak + right-click a chest, furnace, barrel or any other block with
 * a block entity, empty hands, to lift it with everything in it; right-click to set it down. Bodies stay ours
 * ({@link Carry}: players, small animals, Gravity's big mobs, Kitchen's corpses): {@code mixin/carryon/CarryOnPickupMixin}
 * keeps Carry On off every entity, keeps it from lifting while our own carry has your arms full, and keeps it off the
 * blocks in {@link #NO_LIFT} (multiblocks, dungeon spawners, the pod parts our own carry still takes).
 * <p>
 * Carry On's state is read by reflection ({@code CarryOnDataManager.getCarryData(player).isCarrying()}, a synced
 * attachment, so both sides), so the pack still runs without it and nothing here needs it to compile.
 */
public final class CarryOnCompat {
	/** Blocks Carry On never lifts (in our data, so a datapack can add more). */
	public static final TagKey<Block> NO_LIFT = TagKey.create(Registries.BLOCK, JaymosPowers.id("carry_on_no_lift"));
	private static final Identifier BUSY_PHASE = JaymosPowers.id("carry_on_busy");
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("carryon");
	private static @Nullable Method getCarryData, isCarrying;
	private static boolean broken;

	private CarryOnCompat() {
	}

	static void init() {
		if (!LOADED) {
			return;
		}
		// a block in Carry On's arms: no picking up a body or a corpse (or anything else done to a creature) until it's down
		UseEntityCallback.EVENT.addPhaseOrdering(BUSY_PHASE, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(BUSY_PHASE, (player, level, hand, target, hit) -> isCarrying(player) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** Whether Carry On has something in this player's arms (either side). False without Carry On. */
	public static boolean isCarrying(Player player) {
		if (!LOADED || broken) {
			return false;
		}
		try {
			if (getCarryData == null) {
				Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
				getCarryData = manager.getMethod("getCarryData", Player.class);
				isCarrying = Class.forName("tschipp.carryon.common.carry.CarryOnData").getMethod("isCarrying");
			}
			Object data = getCarryData.invoke(null, player);
			return data != null && (boolean) isCarrying.invoke(data);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			broken = true;
			JaymosPowers.LOGGER.warn("Couldn't read Carry On's carrying state; treating everyone as empty-handed", e);
			return false;
		}
	}

	/**
	 * Server, from the mixin: our own carry, a Kitchen corpse in the arms, being carried or being down keep Carry On from
	 * lifting anything.
	 */
	public static boolean armsBusy(Player player) {
		return Carry.isCarrying(player) || Carry.isCarried(player) || Carry.isThrown(player) || KitchenCompat.isCarryingCorpse(player)
			|| Downed.isDowned(player);
	}

	/** Server, from the mixin: Carry On may not lift this block. */
	public static boolean noLift(BlockState state) {
		return state.is(NO_LIFT);
	}
}
