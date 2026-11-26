package com.jaymoskitchen.tanning;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Soap (Soapistry's soap, lye and ash, CC0; its art, our own code and process). Made the old way, in three steps: leaves
 * burnt in a furnace or on a campfire (or charcoal crumbled) give <b>wood ash</b>; ash steeped in a bucket of water is
 * <b>lye</b>; and tallow (fat rendered down, {@link Tanning}) boiled with lye in a water cauldron over a fire comes out as
 * <b>soap</b> (use the tallow on the boiling cauldron with the lye in the other hand, or the lye with the tallow, a level
 * of water each boil; a cooking pot in the grid does it too). Soap is the best scrub there is: with water to hand it
 * takes blood, grime and the smell off you, your armour and your blades, and blood off the floor, better than straw or
 * sand (Jaymo's Powers' {@code Scrubbing}, by the {@code jaymospowers:soaps} tag). On blocks it washes a step of
 * green off weathered copper and the slime off a sticky piston ({@link SoapItem}). Each bar lasts {@link #SOAP_USES} washes.
 */
public final class Soapmaking {
	/** Washes in one bar of soap. */
	public static final int SOAP_USES = 32;
	/** Bars of soap one boil makes (one tallow, one lye, a level of water). */
	static final int BARS_A_BOIL = 2;

	public static Item WOOD_ASH, LYE, SOAP;

	private Soapmaking() {
	}

	public static void register() {
		WOOD_ASH = item("wood_ash", Item::new, new Item.Properties());
		LYE = item("lye", Item::new, new Item.Properties());
		SOAP = item("soap", SoapItem::new, new Item.Properties().durability(SOAP_USES));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(WOOD_ASH, LYE)) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(SOAP));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
			boolean pair = main.is(Tanning.TALLOW) && off.is(LYE) || main.is(LYE) && off.is(Tanning.TALLOW);
			if (!pair || !com.jaymoskitchen.climate.Climate.boiledCauldron(level, pos)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				boil(server, player, pos);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Tallow and lye boiled down in the cauldron at {@code pos}: soap, for a level of its water. */
	private static void boil(ServerLevel level, Player player, BlockPos pos) {
		if (!player.hasInfiniteMaterials()) {
			player.getMainHandItem().shrink(1);
			player.getOffhandItem().shrink(1);
		}
		LayeredCauldronBlock.lowerFillLevel(level.getBlockState(pos), level, pos);
		ItemStack soap = new ItemStack(SOAP, BARS_A_BOIL);
		if (!player.getInventory().add(soap)) {
			player.drop(soap, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.8F, 0.9F);
		level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5F, 0.7F);
		level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.25, 0.05, 0.25, 0.02);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.02);
	}

	private static Item item(String id, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** A bar of soap: on a block, it washes weathered copper back a step and the slime off a sticky piston. */
	public static final class SoapItem extends Item {
		SoapItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			BlockPos pos = context.getClickedPos();
			BlockState state = level.getBlockState(pos);
			Player player = context.getPlayer();
			BlockState washed = WeatheringCopper.getPrevious(state).orElse(null);
			boolean slime = state.is(Blocks.STICKY_PISTON);
			if (slime) {
				washed = Blocks.PISTON.withPropertiesOf(state);
			}
			if (washed == null) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				server.setBlock(pos, washed, Block.UPDATE_ALL);
				if (slime) {
					Block.popResourceFromFace(server, pos, context.getClickedFace(), new ItemStack(Items.SLIME_BALL));
				}
				server.playSound(null, pos, SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 0.8F, 1.1F);
				server.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.6F, 1.2F);
				server.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.4, 0.4, 0.4, 0.02);
				server.levelEvent(null, net.minecraft.world.level.block.LevelEvent.PARTICLES_SCRAPE, pos, 0);
				if (player != null) {
					context.getItemInHand().hurtAndBreak(1, player, context.getHand());
				}
			}
			return InteractionResult.SUCCESS;
		}
	}
}
