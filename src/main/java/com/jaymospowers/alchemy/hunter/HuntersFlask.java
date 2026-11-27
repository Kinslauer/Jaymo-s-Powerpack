package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.brewing.BrewGrade;
import com.jaymospowers.alchemy.brewing.BrewGrades;
import com.jaymospowers.alchemy.toxic.Toxicity;
import com.jaymospowers.compat.KingdomsCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * The hunter's flask (Hunter's Road, patch 6, "T-D1"; an idea of Toxony's, written clean-room): holds up to {@link #DOSES}
 * doses of one drink, a draught or an ordinary potion (never a monster brew: its load is too heavy to sip). Each sip works
 * {@link #LONGER} times as long as the bottle would ({@code netherite}: {@link #LONGER_NETHERITE}). To pour a bottle in, sneak and
 * use the flask with the bottle in the other hand (an empty flask takes any; one with doses takes more of the same, at the same
 * grade). Use it to drink a dose. It is topped up by strong spirit like a spent phial ({@link Refills}, a dose a go), and a
 * flask with the {@code jaymospowers:refill} enchantment fills itself overnight, if the sleeper has Knowledge level
 * {@link #REFILL_KNOWLEDGE} (the level that opens the enchanting table).
 */
public class HuntersFlask extends Item {
	// ---- tuning ----
	public static final int DOSES = 4;
	public static final double LONGER = 2.0, LONGER_NETHERITE = 3.0;
	public static final int SIP_TICKS = 28;
	/** The Knowledge level a sleeper needs before a Refill enchantment works. */
	public static final int REFILL_KNOWLEDGE = 3;
	/** The enchantment that fills a flask overnight. */
	public static final Identifier REFILL = JaymosPowers.id("refill");

	/** What is in a flask: the drink (its item and grade) and the doses left. */
	public record Contents(Identifier item, String grade, int doses) {
		public static final Codec<Contents> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("item").forGetter(Contents::item),
			Codec.STRING.optionalFieldOf("grade", "").forGetter(Contents::grade),
			Codec.intRange(1, DOSES).fieldOf("doses").forGetter(Contents::doses)).apply(i, Contents::new));
		public static final StreamCodec<ByteBuf, Contents> STREAM = StreamCodec.composite(
			Identifier.STREAM_CODEC, Contents::item, ByteBufCodecs.STRING_UTF8, Contents::grade, ByteBufCodecs.VAR_INT, Contents::doses, Contents::new);

		/** One bottle of it, as it was poured in. */
		public ItemStack bottle() {
			ItemStack out = BuiltInRegistries.ITEM.getOptional(item).map(ItemStack::new).orElse(ItemStack.EMPTY);
			BrewGrade g = BrewGrade.byId(grade);
			if (!out.isEmpty() && g != null) {
				BrewGrades.set(out, g);
			}
			return out;
		}
	}

	public static DataComponentType<Contents> CONTENTS;

	private final double longer;

	public HuntersFlask(Properties properties, double longer) {
		super(properties);
		this.longer = longer;
	}

	static void register() {
		CONTENTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("flask_contents"),
			DataComponentType.<Contents>builder().persistent(Contents.CODEC).networkSynchronized(Contents.STREAM).build());
	}

	// ---- what it holds ------------------------------------------------------------------------------------------------

	public static int doses(ItemStack flask) {
		Contents c = flask.get(CONTENTS);
		return c == null ? 0 : c.doses();
	}

	/** Whether a bottle can be poured into a flask: a draught or an ordinary drinking potion, not a monster brew. */
	public static boolean pourable(ItemStack bottle) {
		if (bottle.getItem() instanceof DraughtItem d) {
			return !d.draught().monster();
		}
		return bottle.is(Items.POTION);
	}

	/** Adds up to {@code n} doses of what is already in {@code flask}; true if any went in. A flask with nothing in it takes none. */
	public static boolean topUp(ItemStack flask, int n) {
		Contents c = flask.get(CONTENTS);
		if (c == null || c.doses() >= DOSES) {
			return false;
		}
		flask.set(CONTENTS, new Contents(c.item(), c.grade(), Math.min(DOSES, c.doses() + n)));
		return true;
	}

	/** A night's sleep over: every flask in the pack with the Refill enchantment is filled, if the sleeper has the knowledge. */
	static void refillOvernight(ServerPlayer player) {
		if (KingdomsCompat.knowledge(player) < REFILL_KNOWLEDGE) {
			return;
		}
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.getItem() instanceof HuntersFlask && refills(stack) && doses(stack) > 0) {
				topUp(stack, DOSES);
			}
		}
	}

	private static boolean refills(ItemStack stack) {
		for (Holder<Enchantment> e : stack.getEnchantments().keySet()) {
			if (e.unwrapKey().map(k -> k.identifier().equals(REFILL)).orElse(false)) {
				return true;
			}
		}
		return false;
	}

	// ---- using it -----------------------------------------------------------------------------------------------------

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack flask = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if (!pourable(other)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				pour(player, flask, other);
			}
			return InteractionResult.SUCCESS;
		}
		if (doses(flask) <= 0) {
			return InteractionResult.PASS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	private static void pour(Player player, ItemStack flask, ItemStack bottle) {
		Contents c = flask.get(CONTENTS);
		Identifier id = BuiltInRegistries.ITEM.getKey(bottle.getItem());
		BrewGrade grade = BrewGrades.of(bottle);
		String g = grade == null ? "" : grade.id;
		if (c != null && (!c.item().equals(id) || !c.grade().equals(g))) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.flask.other"));
			return;
		}
		if (c != null && c.doses() >= DOSES) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.flask.full"));
			return;
		}
		flask.set(CONTENTS, new Contents(id, g, c == null ? 1 : c.doses() + 1));
		ItemStack empty = bottle.getItem() instanceof DraughtItem ? Refills.spentPhial(bottle) : new ItemStack(Items.GLASS_BOTTLE);
		bottle.shrink(1);
		player.getInventory().placeItemBackInInventory(empty, Prediction.SERVER_ONLY);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.1F);
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.DRINK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return SIP_TICKS;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack flask, Level level, LivingEntity user) {
		Contents c = flask.get(CONTENTS);
		if (c == null || !(level instanceof ServerLevel) || !(user instanceof ServerPlayer player)) {
			return flask;
		}
		ItemStack bottle = c.bottle();
		if (bottle.getItem() instanceof DraughtItem d) {
			Draughts.drink(player, d.draught(), Draughts.tier(bottle), this.longer, bottle);
		} else if (!bottle.isEmpty()) {
			PotionContents contents = bottle.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
			contents.applyToLivingEntity(player, (float) this.longer * bottle.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F));
			Toxicity.consumed(player, bottle);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
		if (c.doses() <= 1) {
			flask.remove(CONTENTS);
		} else {
			flask.set(CONTENTS, new Contents(c.item(), c.grade(), c.doses() - 1));
		}
		return flask;
	}

	@Override
	public Component getName(ItemStack stack) {
		Contents c = stack.get(CONTENTS);
		if (c == null) {
			return super.getName(stack);
		}
		Item item = BuiltInRegistries.ITEM.getOptional(c.item()).orElse(Items.AIR);
		return Component.translatable("item.jaymospowers.hunters_flask.of", super.getName(stack), item.getName(new ItemStack(item)));
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.hunters_flask").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.jaymospowers.hunters_flask.doses", doses(stack), DOSES).withStyle(ChatFormatting.DARK_GRAY));
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
