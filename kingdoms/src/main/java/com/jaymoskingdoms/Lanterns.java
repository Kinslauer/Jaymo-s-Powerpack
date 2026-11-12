package com.jaymoskingdoms;

import com.jaymoskingdoms.whaling.Whaling;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.541) Lanterns burn down like {@link Torches} do, but on fuel you put in them (Andy, 2026-11-04). A lantern burns until
 * its fuel runs out, then goes out and is left a {@link #BURNT_LANTERN} (no light). Use coal or charcoal on a lantern, lit or
 * burnt out, to add {@link #COAL_TICKS} of burning; ambergris burns slow and adds {@link #AMBERGRIS_TICKS}. A lantern holds at
 * most {@link #MOST_TICKS}. A lantern fresh from the crafting grid starts with one coal's worth; one set by world generation
 * or a settlement starts with {@link #KEPT_FILL}. Nothing ever says how long is left (Andy): a lantern picked up keeps its fuel in
 * {@link #FUEL_LEFT}, and its item shows a durability-style bar for what is left of its last fill (mixin.LanternItemBarMixin),
 * hidden while full. Lanterns sit behind glass, so rain doesn't put them out. Villagers relight burnt lanterns near them the way
 * they relight burnt torches. Soul lanterns never go out (Andy), and copper lanterns are left alone, as copper torches are.
 *
 * <p>(Andy, 2026-11-12: a whole field went dark too early) A lantern set by world generation or a settlement starts on
 * {@link #KEPT_FILL}, and folk keep it going as they pass ({@link Lamplight}): lanterns where people live and travel stay
 * lit, while those on roads nobody uses burn down and go dark.
 */
public final class Lanterns {
	/** One coal (or charcoal) burns this long (ticks): five days (three before 2026-11-12). */
	static final long COAL_TICKS = 120000L;
	/**
	 * A lantern set by world generation or a settlement starts on this much, and folk passing top it back up to it
	 * ({@link Lamplight}): ten days.
	 */
	static final long KEPT_FILL = 240000L;
	/** One ambergris burns this long (ticks): fifteen days. */
	static final long AMBERGRIS_TICKS = 360000L;
	/** A lantern holds no more than this much fuel (ticks): thirty days. */
	static final long MOST_TICKS = 720000L;

	public static Block BURNT_LANTERN;
	public static Item BURNT_LANTERN_ITEM;
	/** On a lantern item: [ticks of fuel left, ticks it held when last filled]. */
	public static DataComponentType<List<Long>> FUEL_LEFT;

	/** Per chunk: packed lantern position (as text) to [game time its fuel runs out, ticks it held when last filled]. Saved. */
	static final AttachmentType<Map<String, List<Long>>> FUEL = AttachmentRegistry.create(JaymosKingdoms.id("lantern_fuel"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())));

	private Lanterns() {
	}

	/** A burnt-out lantern, standing or hung: dark and cold, till someone lights it ({@link Lamplight#burntTick}). */
	static final class BurntLantern extends LanternBlock {
		BurntLantern(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected boolean isRandomlyTicking(BlockState state) {
			return true;
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			Lamplight.burntTick(state, level, pos, random);
		}
	}

	public static void register() {
		FUEL_LEFT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("lantern_fuel"),
			DataComponentType.<List<Long>>builder().persistent(Codec.LONG.listOf())
				.networkSynchronized(ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list())).build());
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("burnt_lantern"));
		BURNT_LANTERN = Registry.register(BuiltInRegistries.BLOCK, key, new BurntLantern(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).noOcclusion()
			.pushReaction(PushReaction.POPPED)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("burnt_lantern"));
		BURNT_LANTERN_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(BURNT_LANTERN, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		LootTableEvents.MODIFY_DROPS.register(Lanterns::keepFuel);
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
				long left = state.is(BURNT_LANTERN) ? 0 : Math.max(0, fuel(server, pos, now)[0] - now);
				if (left + adds > MOST_TICKS && left > MOST_TICKS - COAL_TICKS / 2) {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lantern.full").withStyle(ChatFormatting.GRAY));
					return InteractionResult.FAIL;
				}
				left = Math.min(MOST_TICKS, left + adds);
				set(server, pos, now + left, left);
				held.consume(1, sp);
				if (state.is(BURNT_LANTERN)) {
					relight(server, pos, state);
					server.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
				} else {
					server.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.2F);
				}
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

	/** Plain lanterns burn down (not soul or copper lanterns, oil lamps, or our burnt ones). */
	public static boolean burns(BlockState state) {
		return state.is(Blocks.LANTERN);
	}

	/**
	 * A lantern's random tick (mixin.LanternBlockMixin): out once its fuel is spent; never fuelled, it starts on
	 * {@link #KEPT_FILL}. One that ran dry while nobody was near to see it (its chunk unloaded, or out of every player's
	 * reach) stays lit if people live or travel there ({@link Lamplight#kept}): someone filled it in the meantime.
	 */
	public static void randomTick(BlockState state, ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		long end = fuel(level, pos, now)[0];
		if (now < end) {
			return;
		}
		if (now - end > Lamplight.UNSEEN && Lamplight.kept(level, pos)) {
			// (filled some while back, so a stretch of road doesn't all go dark on the same night)
			set(level, pos, now + KEPT_FILL / 2 + (long) (level.getRandom().nextDouble() * (KEPT_FILL / 2)), KEPT_FILL);
		} else {
			goOut(level, pos, state);
		}
	}

	/** Folk passing the lit lantern at {@code pos} fill it to {@link #KEPT_FILL} if it is under half that; true if they did. */
	static boolean topUp(ServerLevel level, BlockPos pos, long now) {
		if (!burns(level.getBlockState(pos)) || fuel(level, pos, now)[0] - now >= KEPT_FILL / 2) {
			return false;
		}
		set(level, pos, now + KEPT_FILL, KEPT_FILL);
		return true;
	}

	/** A lantern was set down from {@code stack} (mixin.LanternBlockMixin): it burns on the fuel the item carried, or a fresh coal's worth. */
	public static void placed(Level level, BlockPos pos, BlockState state, ItemStack stack) {
		if (!(level instanceof ServerLevel server) || !burns(state)) {
			return;
		}
		List<Long> carried = stack.get(FUEL_LEFT);
		long left = carried != null && carried.size() == 2 ? carried.get(0) : COAL_TICKS;
		long filled = carried != null && carried.size() == 2 ? carried.get(1) : COAL_TICKS;
		set(server, pos, server.getGameTime() + left, filled);
	}

	/** A lantern broken (by hand, blast or piston) drops with the fuel it had left. */
	private static void keepFuel(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		if (!Blocks.LANTERN.getLootTable().equals(table.unwrapKey()) || !(context.getLevel() instanceof ServerLevel level)) {
			return;
		}
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		if (origin == null) {
			return;
		}
		BlockPos pos = BlockPos.containing(origin);
		List<Long> entry = level.getChunkAt(pos).getAttachedOrElse(FUEL, Map.of()).get(String.valueOf(pos.asLong()));
		if (entry == null || entry.size() != 2) {
			return;
		}
		long left = Math.max(1, entry.get(0) - level.getGameTime());
		for (ItemStack drop : drops) {
			if (drop.is(Items.LANTERN)) {
				drop.set(FUEL_LEFT, List.of(left, Math.max(left, entry.get(1))));
			}
		}
		set(level, pos, -1, 0);
	}

	/** [fuel end time, last fill] of the lantern at {@code pos}; one never fuelled is given {@link #KEPT_FILL} from {@code now}. */
	private static long[] fuel(ServerLevel level, BlockPos pos, long now) {
		List<Long> entry = level.getChunkAt(pos).getAttachedOrElse(FUEL, Map.of()).get(String.valueOf(pos.asLong()));
		if (entry == null || entry.size() != 2) {
			set(level, pos, now + KEPT_FILL, KEPT_FILL);
			return new long[] {now + KEPT_FILL, KEPT_FILL};
		}
		return new long[] {entry.get(0), entry.get(1)};
	}

	/** Sets the lantern at {@code pos} to burn till {@code end} (or forgets it, with {@code end} below 0), dropping entries whose lantern is gone. */
	private static void set(ServerLevel level, BlockPos pos, long end, long filled) {
		LevelChunk chunk = level.getChunkAt(pos);
		Map<String, List<Long>> fuel = new HashMap<>(chunk.getAttachedOrElse(FUEL, Map.of()));
		String here = String.valueOf(pos.asLong());
		fuel.keySet().removeIf(k -> !k.equals(here) && !burns(level.getBlockState(BlockPos.of(Long.parseLong(k)))));
		if (end >= 0) {
			fuel.put(here, List.of(end, filled));
		} else {
			fuel.remove(here);
		}
		if (fuel.isEmpty()) {
			chunk.removeAttached(FUEL);
		} else {
			chunk.setAttached(FUEL, fuel);
		}
	}

	/** Whether a lantern item shows its fuel bar: only once it has burnt some of its last fill. */
	public static boolean barVisible(ItemStack stack) {
		List<Long> fuel = stack.get(FUEL_LEFT);
		return fuel != null && fuel.size() == 2 && fuel.get(0) < fuel.get(1);
	}

	/** What is left of its last fill, 0..1. */
	private static float share(ItemStack stack) {
		List<Long> fuel = stack.get(FUEL_LEFT);
		return fuel == null || fuel.size() != 2 || fuel.get(1) <= 0 ? 1.0F : Mth.clamp(fuel.get(0) / (float) fuel.get(1), 0.0F, 1.0F);
	}

	public static int barWidth(ItemStack stack) {
		return Math.round(13.0F * share(stack));
	}

	/** Green when full, through yellow, to red when nearly dry, as a tool's durability bar. */
	public static int barColor(ItemStack stack) {
		return Mth.hsvToRgb(share(stack) / 3.0F, 1.0F, 1.0F);
	}

	private static void goOut(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlockAndUpdate(pos, BURNT_LANTERN.defaultBlockState()
			.setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING))
			.setValue(LanternBlock.WATERLOGGED, state.getValue(LanternBlock.WATERLOGGED)));
		set(level, pos, -1, 0);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.8F);
		double y = pos.getY() + (state.getValue(LanternBlock.HANGING) ? 0.5 : 0.4);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, y, pos.getZ() + 0.5, 5, 0.05, 0.05, 0.05, 0.01);
		Torches.remember(level, pos);
	}

	/** The burnt lantern {@code state} at {@code pos} burns again. Fuel set by the caller, or {@link #KEPT_FILL} on its next tick. */
	static void relight(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlockAndUpdate(pos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING))
			.setValue(LanternBlock.WATERLOGGED, state.getValue(LanternBlock.WATERLOGGED)));
		Torches.forget(level, pos);
	}
}
