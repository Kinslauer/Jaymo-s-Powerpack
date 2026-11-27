package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.brewing.BrewGrade;
import com.jaymospowers.alchemy.brewing.BrewGrades;
import com.jaymospowers.alchemy.toxic.Toxicity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;

/**
 * Refilling (Hunter's Road, patch 6, "D6"; the idea of refilling a potion with spirit is that of The Conjunction of the
 * Spheres by Tenebris Mors, our own code). A hunter's brew drunk leaves a <b>spent phial</b> in the pack that remembers what
 * it held ({@link #WAS}); a bomb or a bolt used (patch 7) leaves a <b>spent casing</b> the same way ({@link #spentCasing}). Strong
 * spirit ({@link #SPIRIT}: Kitchen's still) refills them, {@link #PER_SPIRIT} at a time, a bottle of spirit a go:
 * <ul>
 * <li>at the <b>alchemy bench</b> ({@link AlchemyBenchBlock}), use it;</li>
 * <li>while you <b>sleep</b>, when you wake at dawn, every bottle of spirit on you is spent on what needs refilling;</li>
 * <li>sitting still by a <b>campfire</b> ({@link Toxicity#sittingByFire}) with spirit on you, one a {@link #FIRE_SECONDS} seconds.</li>
 * </ul>
 * The hunter's flask ({@link HuntersFlask}) is topped up by the same spirit, a dose a go. A bottle of spirit comes back as a glass bottle.
 */
public final class Refills {
	/** Slots (spent things, flask doses) one bottle of spirit refills. */
	public static final int PER_SPIRIT = 3;
	/** Seconds between refills sitting at a fire. */
	public static final int FIRE_SECONDS = 10;
	/** The most bottles of spirit a night's sleep spends. */
	public static final int SLEEP_MAX_BOTTLES = 8;
	/** Strong spirit (Kitchen's aqua vitae; a data tag, so packs can add to it). */
	public static final TagKey<Item> SPIRIT = TagKey.create(Registries.ITEM, JaymosPowers.id("strong_spirits"));

	/** What a spent phial or casing held: the item and its brew grade ("" for none). */
	public record Was(Identifier item, String grade) {
		public static final Codec<Was> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("item").forGetter(Was::item),
			Codec.STRING.optionalFieldOf("grade", "").forGetter(Was::grade)).apply(i, Was::new));
		public static final StreamCodec<ByteBuf, Was> STREAM = StreamCodec.composite(
			Identifier.STREAM_CODEC, Was::item, ByteBufCodecs.STRING_UTF8, Was::grade, Was::new);
	}

	public static DataComponentType<Was> WAS;
	public static Item SPENT_PHIAL, SPENT_CASING;

	private static final Map<ServerPlayer, Vec3> LAST = new WeakHashMap<>();

	private Refills() {
	}

	static void init() {
		WAS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("refill_was"),
			DataComponentType.<Was>builder().persistent(Was.CODEC).networkSynchronized(Was.STREAM).build());
		SPENT_PHIAL = Alchemy.item("spent_phial", p -> new SpentItem(p.stacksTo(16), "phial"));
		SPENT_CASING = Alchemy.item("spent_casing", p -> new SpentItem(p.stacksTo(16), "casing"));
		AlchemyBenchBlock.init();
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level && level.isBrightOutside()) {
				overnight(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			int second = server.getTickCount() / 20;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Vec3 now = player.position();
				Vec3 was = LAST.put(player, now);
				if (second % FIRE_SECONDS == 0 && player.isAlive() && Toxicity.sittingByFire(player, was, now)) {
					refill(player);
				}
			}
		});
	}

	// ---- making and reading spent things ------------------------------------------------------------------------------

	/** A spent phial that remembers {@code full} (the bottle it was). */
	public static ItemStack spentPhial(ItemStack full) {
		return spent(SPENT_PHIAL, full);
	}

	/** A spent casing that remembers {@code full}: for the bombs and bolts of patch 7. */
	public static ItemStack spentCasing(ItemStack full) {
		return spent(SPENT_CASING, full);
	}

	private static ItemStack spent(Item kind, ItemStack full) {
		ItemStack out = new ItemStack(kind);
		BrewGrade grade = BrewGrades.of(full);
		out.set(WAS, new Was(BuiltInRegistries.ITEM.getKey(full.getItem()), grade == null ? "" : grade.id));
		return out;
	}

	/** What a spent stack refills into (one of it), or empty if it remembers nothing or the item is gone. */
	public static ItemStack refilled(ItemStack spent) {
		Was was = spent.get(WAS);
		if (was == null) {
			return ItemStack.EMPTY;
		}
		ItemStack out = BuiltInRegistries.ITEM.getOptional(was.item()).map(ItemStack::new).orElse(ItemStack.EMPTY);
		BrewGrade grade = BrewGrade.byId(was.grade());
		if (!out.isEmpty() && grade != null) {
			BrewGrades.set(out, grade);
		}
		return out;
	}

	// ---- the refilling ------------------------------------------------------------------------------------------------

	/**
	 * Spends one bottle of spirit from {@code player}'s pack, if they have one and something needs it, on up to
	 * {@link #PER_SPIRIT} slots: spent phials and casings first, then flask doses. Returns the slots refilled.
	 */
	public static int refill(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int spirit = -1;
		for (int i = 0; i < inventory.getContainerSize() && spirit < 0; i++) {
			if (inventory.getItem(i).is(SPIRIT)) {
				spirit = i;
			}
		}
		if (spirit < 0) {
			return 0;
		}
		int done = 0;
		for (int i = 0; i < inventory.getContainerSize() && done < PER_SPIRIT; i++) {
			ItemStack stack = inventory.getItem(i);
			while (done < PER_SPIRIT && stack.getItem() instanceof SpentItem && !refilled(stack).isEmpty()) {
				ItemStack full = refilled(stack);
				stack.shrink(1);
				inventory.placeItemBackInInventory(full, Prediction.SERVER_ONLY);
				done++;
			}
		}
		for (int i = 0; i < inventory.getContainerSize() && done < PER_SPIRIT; i++) {
			ItemStack stack = inventory.getItem(i);
			while (done < PER_SPIRIT && stack.getItem() instanceof HuntersFlask && HuntersFlask.topUp(stack, 1)) {
				done++;
			}
		}
		if (done > 0) {
			inventory.getItem(spirit).shrink(1);
			inventory.placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), Prediction.SERVER_ONLY);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 0.9F);
		}
		return done;
	}

	/** Refills until the pack's spirit or its need runs out (the alchemy bench). Returns the slots refilled. */
	public static int refillAll(ServerPlayer player) {
		int total = 0;
		for (int i = 0; i < SLEEP_MAX_BOTTLES * 4; i++) {
			int n = refill(player);
			if (n == 0) {
				break;
			}
			total += n;
		}
		return total;
	}

	/** A night's sleep over: spirit on you refills what needs it, and a flask with the Refill enchantment fills itself. */
	private static void overnight(ServerPlayer player) {
		int bottles = 0;
		while (bottles++ < SLEEP_MAX_BOTTLES && refill(player) > 0) {
			// each pass spends one bottle
		}
		HuntersFlask.refillOvernight(player);
	}

	/** A spent phial or casing: shows what it was. */
	public static class SpentItem extends Item {
		private final String kind;

		public SpentItem(Item.Properties properties, String kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public Component getName(ItemStack stack) {
			Was was = stack.get(WAS);
			if (was == null) {
				return super.getName(stack);
			}
			Item item = BuiltInRegistries.ITEM.getOptional(was.item()).orElse(Items.AIR);
			return Component.translatable("item.jaymospowers.spent_" + this.kind + ".of", item.getName(new ItemStack(item)));
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.spent_" + this.kind).withStyle(ChatFormatting.GRAY));
			super.appendHoverText(stack, context, display, builder, flag);
		}
	}
}
