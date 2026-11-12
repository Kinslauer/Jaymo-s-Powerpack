package com.jaymoskitchen.fresh;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.stomach.RawFood;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Food that doesn't keep (Andy's TFC Hardrock picks). Food a player carries is stamped with the day it was made
 * ({@link #MADE}, the day only, so food made the same day still stacks) and shows a freshness bar; once its
 * {@link #days} are up it turns to rotten flesh (meat and fish) or Spoiled Food. It starts slow: raw meat keeps a week,
 * bread a season. Food that's {@link #PRESERVED} (salted, or smoked in the smoker's minigame) keeps {@link #PRESERVED_KEEPS}
 * times as long. Kept in a chest or barrel in a cold cellar (dark and under the sky, deep down, or beside ice or an
 * icebox) it ages at {@link #CELLAR_RATE} of the speed, counted when the container is opened ({@link #SEEN}).
 */
public final class Freshness {
	/** Days each kind keeps (raw meat, raw fish, cooked meat or fish, fruit, vegetables, bread and baking, soup, anything else). */
	static final int RAW_MEAT = 7, RAW_FISH = 4, COOKED = 14, FRUIT = 14, VEGETABLE = 21, BREAD = 21, SOUP = 5, OTHER = 14;
	/** How much longer preserved food keeps, and how fast food ages in a cold cellar. */
	static final int PRESERVED_KEEPS = 4;
	static final float CELLAR_RATE = 0.5F;
	/** A cellar: block light under this, and either below this height or within {@link #ICE_REACH} of ice. */
	static final int CELLAR_DARK = 8, CELLAR_DEEP = 40, ICE_REACH = 2;
	/** Food salted with one salt. */
	static final int PER_SALT = 8;
	/** How often players' pockets are checked (ticks). */
	static final int CHECK_EVERY = 100;

	/** Never rots (golden food, honey, dried kelp, rotten flesh...), plus anything a data pack adds. */
	public static final TagKey<Item> KEEPS = TagKey.create(Registries.ITEM, JaymosKitchen.id("keeps"));

	public static DataComponentType<Long> MADE;
	public static DataComponentType<Unit> PRESERVED;
	public static Item SPOILED_FOOD;
	/** The day a container was last opened, for the cellar's slower ageing. Saved with the block entity. */
	public static final AttachmentType<Long> SEEN = AttachmentRegistry.create(JaymosKitchen.id("cellar_seen"), b -> b.persistent(Codec.LONG));

	/** Today on the client (set each client tick), or -1 off a world: what the freshness bar reads. */
	public static volatile long clientToday = -1L;

	private Freshness() {
	}

	public static void register() {
		MADE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("made"),
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());
		PRESERVED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("preserved"),
			DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(Unit.INSTANCE)).build());
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("spoiled_food"));
		SPOILED_FOOD = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
		JaymosKitchen.LOGGER.debug("cellar attachment: {}", SEEN);
		ServerTickEvents.END_SERVER_TICK.register(Freshness::tick);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel server && hand == InteractionHand.MAIN_HAND) {
				BlockEntity entity = level.getBlockEntity(hit.getBlockPos());
				if (entity instanceof Container container) {
					opened(server, hit.getBlockPos(), entity, container);
				}
			}
			return InteractionResult.PASS;
		});
		// salt in the main hand, food in the other: salts it (one salt to PER_SALT)
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack salt = player.getItemInHand(hand);
			ItemStack food = player.getOffhandItem();
			if (hand != InteractionHand.MAIN_HAND || !salt.is(com.jaymoskitchen.tanning.Hides.SALT) || !perishable(food) || food.has(PRESERVED)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				food.set(PRESERVED, Unit.INSTANCE);
				if (!player.getAbilities().instabuild) {
					salt.shrink(Mth.positiveCeilDiv(food.getCount(), PER_SALT));
				}
				level.playSound(null, player.blockPosition(), SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.6F, 1.4F);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Today, as Freshness counts days. */
	public static long today(Level level) {
		return level.getOverworldClockTime() / 24000L;
	}

	/** It's food that rots. */
	public static boolean perishable(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.FOOD) && !stack.is(KEEPS) && !stack.is(ConventionalItemTags.GOLDEN_FOODS)
			&& !stack.is(Items.ROTTEN_FLESH) && !stack.is(SPOILED_FOOD);
	}

	/** How many days {@code stack} keeps from the day it was made. */
	public static int days(ItemStack stack) {
		int days;
		if (stack.is(ConventionalItemTags.RAW_FISH_FOODS)) {
			days = RAW_FISH;
		} else if (stack.is(RawFood.RAW_MEATS) || stack.is(ConventionalItemTags.RAW_MEAT_FOODS)) {
			days = RAW_MEAT;
		} else if (stack.is(ConventionalItemTags.COOKED_MEAT_FOODS) || stack.is(ConventionalItemTags.COOKED_FISH_FOODS)) {
			days = COOKED;
		} else if (stack.is(ConventionalItemTags.SOUP_FOODS)) {
			days = SOUP;
		} else if (stack.is(ConventionalItemTags.FRUIT_FOODS) || stack.is(ConventionalItemTags.BERRY_FOODS)) {
			days = FRUIT;
		} else if (stack.is(ConventionalItemTags.VEGETABLE_FOODS)) {
			days = VEGETABLE;
		} else if (stack.is(ConventionalItemTags.BREAD_FOODS) || stack.is(ConventionalItemTags.COOKIE_FOODS) || stack.is(ConventionalItemTags.PIE_FOODS)) {
			days = BREAD;
		} else {
			days = OTHER;
		}
		return stack.has(PRESERVED) ? days * PRESERVED_KEEPS : days;
	}

	/** How fresh it still is, 1 (just made) to 0 (about to turn), or -1 if it isn't stamped. */
	public static float left(ItemStack stack, long today) {
		Long made = stack.get(MADE);
		if (made == null) {
			return -1.0F;
		}
		return Mth.clamp(1.0F - (float)(today - made) / days(stack), 0.0F, 1.0F);
	}

	/** Marks {@code stack} preserved (the smoker's minigame does, salt does). */
	public static void preserve(ItemStack stack) {
		if (perishable(stack)) {
			stack.set(PRESERVED, Unit.INSTANCE);
		}
	}

	/** What {@code stack} becomes once it has rotted. */
	static ItemStack rotten(ItemStack stack) {
		boolean flesh = stack.is(RawFood.RAW_MEATS) || stack.is(ConventionalItemTags.RAW_MEAT_FOODS) || stack.is(ConventionalItemTags.COOKED_MEAT_FOODS)
			|| stack.is(ConventionalItemTags.RAW_FISH_FOODS) || stack.is(ConventionalItemTags.COOKED_FISH_FOODS);
		return new ItemStack(flesh ? Items.ROTTEN_FLESH : SPOILED_FOOD, stack.getCount());
	}

	/** Stamps or rots one stack; what should be in its place, or null to leave it. */
	static @Nullable ItemStack age(ItemStack stack, long today) {
		if (!perishable(stack)) {
			return null;
		}
		Long made = stack.get(MADE);
		if (made == null) {
			stack.set(MADE, today);
			return null;
		}
		return today - made >= days(stack) ? rotten(stack) : null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			long today = today(player.level());
			var inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack instead = age(inventory.getItem(i), today);
				if (instead != null) {
					inventory.setItem(i, instead);
				}
			}
		}
	}

	/** A container opened: in a cold cellar, the days since it was last opened count at {@link #CELLAR_RATE}. */
	static void opened(ServerLevel level, BlockPos pos, BlockEntity entity, Container container) {
		long today = today(level);
		Long seen = entity.getAttached(SEEN);
		if (seen == null || seen > today || !cellar(level, pos)) {
			entity.setAttached(SEEN, today);
			return;
		}
		long elapsed = today - seen;
		// the days it gave back, in whole days, so what stacked still stacks; the rest carries over to the next opening
		long credit = (long)(elapsed * (1.0F - CELLAR_RATE));
		if (credit <= 0) {
			return;
		}
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			Long made = stack.get(MADE);
			if (made != null && perishable(stack)) {
				stack.set(MADE, Math.min(today, made + credit));
			}
		}
		entity.setAttached(SEEN, seen + Math.round(credit / (1.0F - CELLAR_RATE)));
		container.setChanged();
	}

	/** Other mods' cold places (by reflection, {@link #addCold}): Jaymo's Powers' gifted Cryo villagers keep their larders cold. */
	private static final List<BiPredicate<ServerLevel, BlockPos>> COLD = new CopyOnWriteArrayList<>();

	/** For other mods (by reflection): more places cold enough to keep food (still only where it's dark and out of the sky). */
	public static void addCold(BiPredicate<ServerLevel, BlockPos> cold) {
		COLD.add(cold);
	}

	/** Cool and dark enough to keep food: dark and out of the sky, and deep down, beside ice or an icebox, or in another mod's cold. */
	public static boolean cellar(ServerLevel level, BlockPos pos) {
		if (level.canSeeSky(pos.above()) || level.getBrightness(LightLayer.BLOCK, pos.above()) >= CELLAR_DARK) {
			return false;
		}
		if (pos.getY() < CELLAR_DEEP) {
			return true;
		}
		for (BiPredicate<ServerLevel, BlockPos> cold : COLD) {
			if (cold.test(level, pos)) {
				return true;
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-ICE_REACH, -1, -ICE_REACH), pos.offset(ICE_REACH, 1, ICE_REACH))) {
			BlockState state = level.getBlockState(p);
			if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.ICE)
				|| state.getBlock() instanceof com.jaymoskitchen.climate.HearthBlock hearth && hearth.cold) {
				return true;
			}
		}
		return false;
	}
}
