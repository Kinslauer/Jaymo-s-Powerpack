package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.web.KitchenCompat;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * <p>
 * Andy: a sneak + right-click no longer lifts straight away. It opens a small box ({@link BlockChoice}) with the block's
 * own use ("Use (bake)") and Lift, so the mixin lets Carry On lift only while {@link #liftChosen} runs.
 */
public final class CarryOnCompat {
	/** Blocks Carry On never lifts (in our data, so a datapack can add more). */
	public static final TagKey<Block> NO_LIFT = TagKey.create(Registries.BLOCK, JaymosPowers.id("carry_on_no_lift"));
	private static final Identifier BUSY_PHASE = JaymosPowers.id("carry_on_busy");
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("carryon");
	private static @Nullable Method getCarryData, isCarrying, isKeyPressed, isPermitted, tryPickUpBlock;
	private static boolean broken, liftBroken, listBroken;
	/** True only while {@link #liftChosen} has Carry On lift (server thread). */
	private static boolean chosen;

	private CarryOnCompat() {
	}

	static void init() {
		BlockChoice.init();
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

	/** Server, from the mixin: a lift picked in the box is running. */
	public static boolean chosen() {
		return chosen;
	}

	/**
	 * Server, from the mixin while {@link #chosen}: Carry On's own {@code canCarryGeneral} without its key and same-tick
	 * checks (the box released the key, and saving its data stamps this tick): empty hands, nothing in Carry On's arms,
	 * not a spectator or in adventure mode.
	 */
	public static boolean canLiftNow(ServerPlayer player) {
		return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty() && !isCarrying(player) && !player.isSpectator()
			&& player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE;
	}

	/**
	 * Whether Carry On would lift this block (either side): a block entity, not {@link #NO_LIFT}, not one half of a door
	 * or other two-block block, breakable (or in creative), and allowed by Carry On's own block lists. False without
	 * Carry On. The box ({@link BlockChoice}) opens only on these.
	 */
	public static boolean couldLift(Player player, Level level, BlockPos pos, BlockState state) {
		return LOADED && state.hasBlockEntity() && !noLift(state) && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& (state.getDestroySpeed(level, pos) != -1.0F || player.isCreative()) && permitted(state.getBlock());
	}

	/** Whether Carry On's lift key (sneak, unless rebound) is held by this player, as Carry On itself reads it. */
	public static boolean keyPressed(Player player) {
		if (!LOADED || broken) {
			return false;
		}
		try {
			if (isKeyPressed == null) {
				isKeyPressed = Class.forName("tschipp.carryon.common.carry.CarryOnData").getMethod("isKeyPressed");
			}
			isCarrying(player);
			Object data = getCarryData == null ? null : getCarryData.invoke(null, player);
			return data != null && (boolean) isKeyPressed.invoke(data);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			broken = true;
			JaymosPowers.LOGGER.warn("Couldn't read Carry On's key state; the lift box stays shut", e);
			return false;
		}
	}

	/** Carry On's config block lists ({@code ListHandler.isPermitted}); true if they can't be read. */
	private static boolean permitted(Block block) {
		if (listBroken) {
			return true;
		}
		try {
			if (isPermitted == null) {
				isPermitted = Class.forName("tschipp.carryon.common.config.ListHandler").getMethod("isPermitted", Block.class);
			}
			return (boolean) isPermitted.invoke(null, block);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			listBroken = true;
			JaymosPowers.LOGGER.warn("Couldn't read Carry On's block lists; the lift box takes every block entity", e);
			return true;
		}
	}

	/**
	 * Server: Carry On lifts the block at {@code pos} because the player picked Lift in the box. The break-permission
	 * check is the same one Carry On passes itself (Fabric's {@code PlayerBlockBreakEvents.BEFORE}). Whether it lifted.
	 */
	public static boolean liftChosen(ServerPlayer player, BlockPos pos) {
		if (!LOADED || liftBroken) {
			return false;
		}
		Level level = player.level();
		BiFunction<BlockState, BlockPos, Boolean> mayBreak = (state, at) -> PlayerBlockBreakEvents.BEFORE.invoker()
			.beforeBlockBreak(level, player, at, state, level.getBlockEntity(at));
		try {
			if (tryPickUpBlock == null) {
				tryPickUpBlock = Class.forName("tschipp.carryon.common.carry.PickupHandler")
					.getMethod("tryPickUpBlock", ServerPlayer.class, BlockPos.class, Level.class, BiFunction.class);
			}
			chosen = true;
			return (boolean) tryPickUpBlock.invoke(null, player, pos, level, mayBreak);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			liftBroken = true;
			JaymosPowers.LOGGER.warn("Couldn't call Carry On's block lift; Lift in the box does nothing", e);
			return false;
		} finally {
			chosen = false;
		}
	}
}
