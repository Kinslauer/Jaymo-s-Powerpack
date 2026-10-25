package com.jaymoskingdoms;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.201) Letters, after Courier Owls (remade, not copied: that mod is All Rights Reserved). Andy: "i want a
 * messenger system for getting information across the lands, this is so we get less information through chat and the
 * world is more immersive". A blank {@link #LETTER} (paper and a feather) is written on its own screen: who it's for (a
 * character name) and what it says; sealing it makes a {@link #SEALED} letter, "Letter to ...", which anyone holding it can
 * read. A sealed letter goes by a messenger pigeon you've tamed ({@link Pigeon}: use it on the bird) or by a townsman
 * ({@link Couriers}: sneak and use it on a grown villager), who walks or rides it across the land.
 * <p>The world's news comes the same way ({@link Post}): what used to be a line in everyone's chat is a "Tidings" letter a
 * pigeon or a townsman brings you, and so is MCA's mail (letters from family, condolences).
 */
public final class Letters {
	/** What a player may write: the name, the letter. */
	public static final int NAME_MAX = 48, TEXT_MAX = 600;

	/**
	 * A sealed letter's words: {@code to} the character name it's for, {@code from} who wrote it, {@code lines} what it
	 * says (a player's own words, or the news headlines as they were written); {@code title} its name in place of
	 * "Letter to ..." (the news: "Tidings").
	 */
	public record Letter(String to, Component from, List<Component> lines, Optional<Component> title) {
		static final Codec<Letter> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("to").forGetter(Letter::to),
			ComponentSerialization.CODEC.fieldOf("from").forGetter(Letter::from),
			ComponentSerialization.CODEC.listOf().fieldOf("lines").forGetter(Letter::lines),
			ComponentSerialization.CODEC.optionalFieldOf("title").forGetter(Letter::title)).apply(i, Letter::new));
		static final StreamCodec<RegistryFriendlyByteBuf, Letter> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Letter::to,
			ComponentSerialization.STREAM_CODEC, Letter::from,
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Letter::lines,
			ComponentSerialization.OPTIONAL_STREAM_CODEC, Letter::title,
			Letter::new);
	}

	public static Item LETTER, SEALED;
	/** The client's letter screen, for the letter in that hand (set by the client). */
	public static Consumer<InteractionHand> open = hand -> {
	};
	public static DataComponentType<Letter> CONTENTS;

	private Letters() {
	}

	/** Client → server: seal the blank letter in {@code hand}, for {@code to}, saying {@code text}. */
	public record Seal(boolean offhand, String to, String text) implements CustomPacketPayload {
		public static final Type<Seal> TYPE = new Type<>(JaymosKingdoms.id("letter_seal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Seal> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Seal::offhand,
			ByteBufCodecs.stringUtf8(NAME_MAX * 4), Seal::to,
			ByteBufCodecs.stringUtf8(TEXT_MAX * 4), Seal::text,
			Seal::new);

		@Override
		public Type<Seal> type() {
			return TYPE;
		}
	}

	static void register() {
		CONTENTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("letter"),
			DataComponentType.<Letter>builder().persistent(Letter.CODEC).networkSynchronized(Letter.STREAM_CODEC).build());
		ResourceKey<Item> blank = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("letter"));
		LETTER = Registry.register(BuiltInRegistries.ITEM, blank, new Item(new Item.Properties().setId(blank).stacksTo(16)));
		ResourceKey<Item> sealed = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("sealed_letter"));
		SEALED = Registry.register(BuiltInRegistries.ITEM, sealed, new SealedLetter(new Item.Properties().setId(sealed).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.WRITABLE_BOOK, LETTER));
		// (use one: it opens, to write or to read)
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(LETTER) && !stack.is(SEALED)) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				open.accept(hand);
			}
			return InteractionResult.SUCCESS;
		});
		PayloadTypeRegistry.serverboundPlay().register(Seal.TYPE, Seal.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Seal.TYPE, (payload, context) -> seal(context.player(), payload));
	}

	private static void seal(ServerPlayer player, Seal p) {
		InteractionHand hand = p.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack held = player.getItemInHand(hand);
		String to = p.to().strip();
		String text = p.text().strip();
		if (!held.is(LETTER) || to.isEmpty() || text.isEmpty() || to.length() > NAME_MAX || text.length() > TEXT_MAX) {
			return;
		}
		ItemStack letter = sealed(new Letter(to, Post.nameOf(player), List.of(Component.literal(text)), Optional.empty()));
		held.shrink(1);
		if (held.isEmpty()) {
			player.setItemInHand(hand, letter);
		} else if (!player.getInventory().add(letter)) {
			player.drop(letter, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.1F);
	}

	/** A sealed letter with these words. */
	public static ItemStack sealed(Letter letter) {
		ItemStack stack = new ItemStack(SEALED);
		stack.set(CONTENTS, letter);
		return stack;
	}

	/** {@code stack}'s words, if it's a sealed letter. */
	public static @Nullable Letter of(ItemStack stack) {
		return stack.is(SEALED) ? stack.get(CONTENTS) : null;
	}

	/** "Letter to X", "From Y" beneath. */
	static final class SealedLetter extends Item {
		SealedLetter(Item.Properties properties) {
			super(properties);
		}

		@Override
		public Component getName(ItemStack stack) {
			Letter letter = stack.get(CONTENTS);
			if (letter == null) {
				return super.getName(stack);
			}
			return letter.title().orElseGet(() -> Component.translatable("item.jaymoskingdoms.sealed_letter.to", letter.to()));
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			Letter letter = stack.get(CONTENTS);
			if (letter != null) {
				builder.accept(Component.translatable("item.jaymoskingdoms.sealed_letter.from", letter.from()).withStyle(ChatFormatting.GRAY));
			}
		}
	}
}
