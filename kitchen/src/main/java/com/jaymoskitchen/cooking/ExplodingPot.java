package com.jaymoskitchen.cooking;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;

/**
 * The exploding pot. Right-click a lit cooking station (campfire, furnace, smoker, oven...) holding gunpowder, TNT or
 * WeaponMod's dynamite (if it's installed) and you slip one into whatever cooks there next: the cook isn't told. A
 * dish cooked with it that comes out burnt or charred (scorched in the pot, left on the fire, burnt on the grill) blows
 * up at the station instead: a small blast that knocks the cook back and hurts a little, and the dish and its
 * ingredients are gone. A dish cooked well with it is served as usual (the powder fizzled).
 */
public final class ExplodingPot {
	/** Whether the blast breaks blocks; how big it is; the most it hurts anyone (half-hearts). */
	static final boolean EXPLODING_POT_BREAKS = false;
	static final float POWER = 2.0F, MAX_DAMAGE = 4.0F;
	/** How long a spiked station waits for a dish before the powder is spoiled (ticks). */
	static final long SPIKED_TICKS = 20L * 60 * 10;
	static final Identifier DYNAMITE = Identifier.fromNamespaceAndPath("weaponmod", "dynamite");

	private record Spot(ResourceKey<Level> dimension, BlockPos pos) {
	}

	/** Spiked stations, and until when. */
	private static final Map<Spot, Long> SPIKED = new HashMap<>();

	private ExplodingPot() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SPIKED.clear());
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !explosive(player.getMainHandItem())) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			Station station = Station.of(level, pos);
			if (station == null || !station.heats()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
				ItemStack held = sp.getMainHandItem();
				Component name = held.getHoverName();
				if (!sp.hasInfiniteMaterials()) {
					held.shrink(1);
				}
				SPIKED.put(new Spot(server.dimension(), pos.immutable()), server.getGameTime() + SPIKED_TICKS);
				server.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.5F, 1.4F);
				sp.sendOverlayMessage(Component.translatable("message.jaymoskitchen.pot_spiked", name));
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Gunpowder, TNT, or WeaponMod's dynamite. */
	static boolean explosive(ItemStack stack) {
		if (stack.is(Items.GUNPOWDER) || stack.is(Items.TNT)) {
			return true;
		}
		return !stack.isEmpty() && FabricLoader.getInstance().isModLoaded(DYNAMITE.getNamespace())
			&& DYNAMITE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/**
	 * A dish at {@code pos} came out at {@code quality}: whatever was slipped in there is cooked with it. True if it blew
	 * up (scorched with powder in it), and then there's no dish to hand out.
	 */
	static boolean blowsUp(ServerPlayer cook, BlockPos pos, Quality quality) {
		ServerLevel level = cook.level();
		Long until = SPIKED.remove(new Spot(level.dimension(), pos));
		if (until == null || until < level.getGameTime() || quality != Quality.BURNT && quality != Quality.CHARRED) {
			return false;
		}
		ExplosionDamageCalculator gentle = new ExplosionDamageCalculator() {
			@Override
			public boolean shouldBlockExplode(Explosion explosion, net.minecraft.world.level.BlockGetter getter, BlockPos at,
				net.minecraft.world.level.block.state.BlockState state, float power) {
				return EXPLODING_POT_BREAKS && super.shouldBlockExplode(explosion, getter, at, state, power);
			}

			@Override
			public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
				return Math.min(MAX_DAMAGE, super.getEntityDamageAmount(explosion, entity, exposure));
			}
		};
		level.explode(null, null, gentle, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, POWER, false,
			EXPLODING_POT_BREAKS ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
		cook.sendOverlayMessage(Component.translatable("message.jaymoskitchen.pot_exploded"));
		return true;
	}
}
