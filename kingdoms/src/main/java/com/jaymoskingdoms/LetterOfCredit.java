package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * A letter of credit ({@link Banks}): a sealed note for {@link Note#amount} drawn on a realm's bank, in its drawer's name.
 * Only the one it names can cash it, at any bank's counter (another realm's keeps {@link Banks#LETTER_FEE}); in anyone
 * else's hands it's paper, and when the realm it's drawn on falls it's paper in theirs too. Drawn with the sealed
 * letter's look ({@link Letters}).
 */
public final class LetterOfCredit extends Item {
	/** The note: the realm it's drawn on (its id, seat and name), whose it is (id and name), how much (milli), and when (game time). */
	public record Note(int realm, BlockPos seat, String realmName, UUID owner, String ownerName, long amount, long issued) {
		static final Codec<Note> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("realm").forGetter(Note::realm),
			BlockPos.CODEC.fieldOf("seat").forGetter(Note::seat),
			Codec.STRING.fieldOf("realm_name").forGetter(Note::realmName),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Note::owner),
			Codec.STRING.fieldOf("owner_name").forGetter(Note::ownerName),
			Codec.LONG.fieldOf("amount").forGetter(Note::amount),
			Codec.LONG.fieldOf("issued").forGetter(Note::issued)).apply(i, Note::new));
	}

	public static DataComponentType<Note> NOTE;
	public static Item ITEM;

	private LetterOfCredit(Properties properties) {
		super(properties);
	}

	static void register() {
		NOTE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("credit_note"),
			DataComponentType.<Note>builder().persistent(Note.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(Note.CODEC)).build());
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("letter_of_credit"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new LetterOfCredit(new Item.Properties().stacksTo(1).setId(key)));
	}

	static ItemStack make(Note note) {
		ItemStack stack = new ItemStack(ITEM);
		stack.set(NOTE, note);
		return stack;
	}

	/** The note {@code stack} carries, if it's a letter of credit; else null. */
	static @Nullable Note note(ItemStack stack) {
		return ITEM == null || !stack.is(ITEM) ? null : stack.get(NOTE);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		Note note = stack.get(NOTE);
		if (note == null) {
			return;
		}
		builder.accept(Component.translatable("item.jaymoskingdoms.letter_of_credit.amount", Money.text(note.amount()), note.realmName())
			.withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("item.jaymoskingdoms.letter_of_credit.owner", note.ownerName()).withStyle(ChatFormatting.GRAY));
	}
}
