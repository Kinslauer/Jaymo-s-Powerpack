package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * <b>Sketch it and sell it</b>: the Sketchbook (paper and charcoal). Hold use while looking at something within
 * {@link #RANGE} blocks for {@link #HOLD_TICKS} ticks (a bar of {@code =} and {@code -} over the hotbar) and you draw a
 * Sketch of it, named for what it shows. A Sketch is worth what its subject is ({@link #worth}): a boss, a great
 * monster or a giant most; a gifted one seen fighting; a ship on fire; any other beast least. Drawing the same thing
 * again is worth a quarter. Carry the Sketch to a pigeon roost (the town's post, {@link Roost}) and use it there: the
 * post pays by its worth, in the coin of the place, and the world's news (the town boards and, for a great one, the
 * Tidings) carries the sketcher's name.
 */
public final class Sketches {
	/** How far you can draw from (blocks), how long you hold (ticks), and the book's pages. */
	static final double RANGE = 24.0;
	static final int HOLD_TICKS = 60, PAGES = 24;
	/** What a subject is worth (a Sketch's score), highest first. */
	static final int BOSS = 60, LEGEND = 40, GIFTED = 30, SHIP = 20, MONSTER = 5, BEAST = 2, OTHER = 1;
	/** A repeat subject is worth this share (a divisor). */
	static final int REPEAT_DIVISOR = 4;
	/** What a point of worth pays (thousandths of an emerald), and the least a post pays. */
	static final long MILLI_PER_WORTH = 125L, LEAST_MILLI = 250L;
	/** From this worth up, the sale is great news (the Tidings), not only a line on the town boards. */
	static final int GREAT_AT = GIFTED;
	/** How long after a blow (ticks) something still counts as in a fight. */
	static final int FIGHT_TICKS = 200;
	/** How many subjects a sketcher is remembered to have drawn. */
	static final int REMEMBERED = 40;

	/** A sketcher's drawn subjects (their ids), saved. */
	static final AttachmentType<List<String>> DRAWN = AttachmentRegistry.create(JaymosKingdoms.id("sketch_drawn"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	public static Item BOOK, SKETCH;

	/** Who is drawing what, and since when. */
	private record Drawing(UUID subject, int since) {
	}

	private static final Map<UUID, Drawing> DRAWING = new HashMap<>();

	private Sketches() {
	}

	/** The Sketchbook: hold use to draw. */
	private static final class Book extends Item {
		Book(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return 72000;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.BRUSH;
		}

		@Override
		public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
			if (level instanceof ServerLevel server && user instanceof ServerPlayer player) {
				draw(server, player, stack, 72000 - remaining);
			}
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
			DRAWING.remove(user.getUUID());
			return false;
		}
	}

	static void register() {
		ResourceKey<Item> bookKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("sketchbook"));
		BOOK = Registry.register(BuiltInRegistries.ITEM, bookKey, new Book(new Item.Properties().setId(bookKey).stacksTo(1).durability(PAGES)));
		ResourceKey<Item> sketchKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("sketch"));
		SKETCH = Registry.register(BuiltInRegistries.ITEM, sketchKey, new Item(new Item.Properties().setId(sketchKey).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(BOOK));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DRAWING.clear());
	}

	// ---- drawing -----------------------------------------------------------------------------------------------------

	/** What {@code player} is looking at within range that can be drawn, or null. */
	private static @Nullable Entity subject(ServerPlayer player) {
		Vec3 from = player.getEyePosition(), look = player.getViewVector(1.0F), to = from.add(look.scale(RANGE));
		AABB box = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, to, box,
			e -> e != player && !e.isSpectator() && e.isAlive() && (e instanceof LivingEntity || e instanceof AbstractBoat), RANGE * RANGE);
		return hit == null ? null : hit.getEntity();
	}

	private static void draw(ServerLevel level, ServerPlayer player, ItemStack book, int held) {
		Entity subject = subject(player);
		Drawing now = DRAWING.get(player.getUUID());
		if (subject == null || now != null && !now.subject().equals(subject.getUUID())) {
			DRAWING.remove(player.getUUID());
			now = null;
		}
		if (subject == null) {
			return;
		}
		if (now == null) {
			now = new Drawing(subject.getUUID(), held);
			DRAWING.put(player.getUUID(), now);
		}
		int done = held - now.since();
		if (done < HOLD_TICKS) {
			if (done % 4 == 0) {
				int filled = done * 12 / HOLD_TICKS;
				player.sendOverlayMessage(Component.literal("=".repeat(filled) + "-".repeat(12 - filled)));
			}
			return;
		}
		DRAWING.remove(player.getUUID());
		InteractionHand hand = player.getUsedItemHand();
		player.stopUsingItem();
		ItemStack sketch = make(player, subject);
		book.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		player.getInventory().placeItemBackInInventory(sketch, net.minecraft.util.Prediction.SERVER_ONLY);
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.1F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.sketch.drawn", subject.getDisplayName()));
	}

	/** What drawing {@code subject} is worth, before any repeat. */
	static int worth(Entity subject) {
		if (subject instanceof AbstractBoat) {
			return subject.isOnFire() ? SHIP : OTHER;
		}
		EntityType<?> type = subject.getType();
		if (BossBars.boss(subject) || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WITHER || type == EntityTypes.WARDEN
			|| type == EntityTypes.ELDER_GUARDIAN || type == EntityTypes.GIANT) {
			return BOSS;
		}
		if (MarkedMonsters.barred(subject)) {
			return LEGEND;
		}
		if (subject instanceof LivingEntity living && PowersCompat.gift(subject) != null
			&& (living.tickCount - living.getLastHurtMobTimestamp() < FIGHT_TICKS || living.tickCount - living.getLastHurtByMobTimestamp() < FIGHT_TICKS)) {
			return GIFTED;
		}
		return subject instanceof Enemy || subject instanceof Mob m && m.getTarget() != null ? MONSTER : subject instanceof Animal ? BEAST : OTHER;
	}

	/** A Sketch of {@code subject} by {@code player}: named for it, its worth and its maker on it. */
	private static ItemStack make(ServerPlayer player, Entity subject) {
		List<String> drawn = new ArrayList<>(player.getAttachedOrElse(DRAWN, List.of()));
		boolean again = drawn.contains(subject.getStringUUID());
		if (!again) {
			drawn.add(subject.getStringUUID());
			while (drawn.size() > REMEMBERED) {
				drawn.removeFirst();
			}
			player.setAttached(DRAWN, List.copyOf(drawn));
		}
		int worth = Math.max(1, again ? worth(subject) / REPEAT_DIVISOR : worth(subject));
		ItemStack stack = new ItemStack(SKETCH);
		CompoundTag tag = new CompoundTag();
		tag.putInt("worth", worth);
		tag.putString("subject", subject.getDisplayName().getString());
		tag.putString("by", Post.nameOf(player).getString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.sketch.of", subject.getDisplayName())
			.withStyle(style -> style.withItalic(false)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item.jaymoskingdoms.sketch.worth." + grade(worth)).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)),
			Component.translatable("item.jaymoskingdoms.sketch.by", tag.getStringOr("by", "")).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))));
		return stack;
	}

	/** The word for a worth (lang: item.jaymoskingdoms.sketch.worth.*). */
	private static String grade(int worth) {
		return worth >= BOSS ? "grand" : worth >= LEGEND ? "fine" : worth >= GIFTED ? "good" : worth >= SHIP ? "fair" : "plain";
	}

	// ---- selling -----------------------------------------------------------------------------------------------------

	/** {@code stack} is a Sketch someone drew. */
	static boolean isSketch(ItemStack stack) {
		return stack.is(SKETCH) && stack.get(DataComponents.CUSTOM_DATA) != null;
	}

	/** The post at {@code pos} (a roost) buys {@code stack}, a Sketch: paid by its worth, and the news carries the sketcher's name. */
	static InteractionResult sell(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
		CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
		int worth = tag.getIntOr("worth", 1);
		long milli = Math.max(LEAST_MILLI, worth * MILLI_PER_WORTH);
		Currencies.Pay pay = Currencies.pay(Currencies.at(level, pos), milli);
		Currencies.give(player, pay);
		Component by = Component.literal(tag.getStringOr("by", Post.nameOf(player).getString()));
		Post.news(level.getServer(), Component.translatable(worth >= GREAT_AT ? "news.jaymoskingdoms.sketch.great" : "news.jaymoskingdoms.sketch.sold",
			by, tag.getStringOr("subject", "")).withStyle(ChatFormatting.AQUA));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.sketch.sold", Currencies.amount(pay)));
		level.playSound(null, pos, SoundEvents.VILLAGER_TRADE, SoundSource.BLOCKS, 0.8F, 1.0F);
		stack.shrink(1);
		return InteractionResult.SUCCESS;
	}
}
