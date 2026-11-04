package com.jaymoskingdoms;

import com.jaymoskingdoms.whaling.Whaling;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.7.538) Lanterns burn down like {@link Torches} do, but on fuel you put in them (Andy, 2026-11-04). A lantern or soul
 * lantern burns until its fuel runs out, then goes out and is left a {@link #BURNT_LANTERN} (no light). Use coal or charcoal
 * on a lantern, lit or burnt out, to add {@link #COAL_TICKS} of burning; ambergris burns slow and adds
 * {@link #AMBERGRIS_TICKS}. A lantern holds at most {@link #MOST_TICKS}. A lantern that has never been fuelled (just placed,
 * crafted, or set by world generation or a settlement) starts with one coal's worth. Lanterns sit behind glass, so rain
 * doesn't put them out. Villagers relight burnt lanterns near them the way they relight burnt torches. Copper lanterns
 * are left alone, as copper torches are.
 */
public final class Lanterns {
	/** One coal (or charcoal) burns this long (ticks): three days. */
	static final long COAL_TICKS = 72000L;
	/** One ambergris burns this long (ticks): fifteen days. */
	static final long AMBERGRIS_TICKS = 360000L;
	/** A lantern holds no more than this much fuel (ticks): thirty days. */
	static final long MOST_TICKS = 720000L;

	/** A burnt-out lantern: soul tells which lantern it was, so it lights again as the same. */
	public static final BooleanProperty SOUL = BooleanProperty.create("soul");

	public static Block BURNT_LANTERN;
	public static Item BURNT_LANTERN_ITEM;

	/** Per chunk: packed lantern position (as text) to the game time its fuel runs out. Saved. */
	static final AttachmentType<Map<String, Long>> FUEL = AttachmentRegistry.create(JaymosKingdoms.id("lantern_fuel"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	private Lanterns() {
	}

	/** A burnt-out lantern, standing or hung: dark and cold. */
	static final class BurntLantern extends LanternBlock {
		BurntLantern(BlockBehaviour.Properties properties) {
			super(properties);
			this.registerDefaultState(this.defaultBlockState().setValue(SOUL, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(SOUL);
		}
	}

	public static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("burnt_lantern"));
		BURNT_LANTERN = Registry.register(BuiltInRegistries.BLOCK, key, new BurntLantern(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).noOcclusion()
			.pushReaction(PushReaction.POPPED)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("burnt_lantern"));
		BURNT_LANTERN_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(BURNT_LANTERN, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (!burns(state) && !state.is(BURNT_LANTERN)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			long adds = fuelOf(held);
			if (adds == 0) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				BlockPos pos = hit.getBlockPos();
				long now = server.getGameTime();
				long left = state.is(BURNT_LANTERN) ? 0 : Math.max(0, endOf(server, pos, now) - now);
				if (left + adds > MOST_TICKS && left > MOST_TICKS - COAL_TICKS / 2) {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lantern.full").withStyle(ChatFormatting.GRAY));
					return InteractionResult.FAIL;
				}
				left = Math.min(MOST_TICKS, left + adds);
				setEnd(server, pos, now + left);
				held.consume(1, sp);
				if (state.is(BURNT_LANTERN)) {
					relight(server, pos, state);
					server.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
				} else {
					server.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.2F);
				}
				long days = Math.max(1, Math.round(left / 24000.0));
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lantern.fuel", days).withStyle(ChatFormatting.GOLD));
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Lantern fuel a stack is worth (ticks), or 0. */
	static long fuelOf(ItemStack stack) {
		if (stack.is(Whaling.AMBERGRIS)) {
			return AMBERGRIS_TICKS;
		}
		return stack.is(ItemTags.COALS) ? COAL_TICKS : 0;
	}

	/** Lanterns and soul lanterns burn down (not copper lanterns, oil lamps, or our burnt ones). */
	public static boolean burns(BlockState state) {
		return state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN);
	}

	/** A lantern's random tick (mixin.LanternBlockMixin): out once its fuel is spent; never fuelled, it starts on a coal's worth. */
	public static void randomTick(BlockState state, ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		if (now >= endOf(level, pos, now)) {
			goOut(level, pos, state);
		}
	}

	/** When the lantern at {@code pos} runs dry; one never fuelled is given a coal's worth from {@code now}. */
	private static long endOf(ServerLevel level, BlockPos pos, long now) {
		Long end = level.getChunkAt(pos).getAttachedOrElse(FUEL, Map.of()).get(String.valueOf(pos.asLong()));
		if (end == null) {
			setEnd(level, pos, now + COAL_TICKS);
			return now + COAL_TICKS;
		}
		return end;
	}

	/** Sets the lantern at {@code pos} to burn till {@code end} (or forgets it, with {@code end} below 0), dropping entries whose lantern is gone. */
	private static void setEnd(ServerLevel level, BlockPos pos, long end) {
		LevelChunk chunk = level.getChunkAt(pos);
		Map<String, Long> fuel = new HashMap<>(chunk.getAttachedOrElse(FUEL, Map.of()));
		fuel.keySet().removeIf(k -> !burns(level.getBlockState(BlockPos.of(Long.parseLong(k)))));
		if (end >= 0) {
			fuel.put(String.valueOf(pos.asLong()), end);
		} else {
			fuel.remove(String.valueOf(pos.asLong()));
		}
		if (fuel.isEmpty()) {
			chunk.removeAttached(FUEL);
		} else {
			chunk.setAttached(FUEL, fuel);
		}
	}

	private static void goOut(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlockAndUpdate(pos, BURNT_LANTERN.defaultBlockState().setValue(SOUL, state.is(Blocks.SOUL_LANTERN))
			.setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING))
			.setValue(LanternBlock.WATERLOGGED, state.getValue(LanternBlock.WATERLOGGED)));
		setEnd(level, pos, -1);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.8F);
		double y = pos.getY() + (state.getValue(LanternBlock.HANGING) ? 0.5 : 0.4);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, y, pos.getZ() + 0.5, 5, 0.05, 0.05, 0.05, 0.01);
		Torches.remember(level, pos);
	}

	/** The burnt lantern {@code state} at {@code pos} burns again, as the lantern it was. Fuel set by the caller, or a coal's worth on its next tick. */
	static void relight(ServerLevel level, BlockPos pos, BlockState state) {
		Block lit = state.getValue(SOUL) ? Blocks.SOUL_LANTERN : Blocks.LANTERN;
		level.setBlockAndUpdate(pos, lit.defaultBlockState().setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING))
			.setValue(LanternBlock.WATERLOGGED, state.getValue(LanternBlock.WATERLOGGED)));
		Torches.forget(level, pos);
	}
}
