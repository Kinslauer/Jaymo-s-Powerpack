package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * The harness: the armour slots a body has beyond helmet, chestplate, leggings and boots. Padding and mail under the
 * chest piece, shoulders, arms, hands, thighs and shins, the neck (a gorget) and the waist (faulds and tassets),
 * something over it all, a hood and a crest ({@link Slot}); the
 * pieces are {@link HeraldryArmour}'s. Kept on the wearer (players, folk, villagers alike), saved, and synced so every
 * client draws it.
 * <p>
 * A player opens the harness from a tab on the inventory (or its key) and fits the pieces in {@link HarnessMenu};
 * right-clicking with a piece puts it on if its place is free. Spaulders are fitted after the chest piece (they come
 * off without one, a shirt of padding or mail will do), and a crest needs a helm to stand on. Each piece adds its armour
 * and toughness, wears as it takes blows, and falls with the rest of what its wearer carried.
 * <p>
 * With Jaymo's Powers the layers soak different blows and add their weight ({@link #layers}, handed over by
 * {@link PowersCompat#shareHarness}).
 */
public final class Harness {
	/** The chance each piece takes wear from a blow its wearer's armour met, and the share of the blow it takes (at least 1). */
	static final float WEAR_CHANCE = 0.25F, WEAR_SHARE = 0.25F;
	/** How often (ticks) a player's harness is checked for pieces that no longer fit. */
	static final int CHECK = 20;

	/**
	 * The places of the harness, in the order they are kept. New places go on the end, never between: a saved harness is
	 * a list in this order, and an older, shorter one is read with its new places bare ({@link #worn}).
	 */
	public enum Slot {
		PADDING, MAIL, SHOULDERS, ARMS, HANDS, THIGHS, SHINS, OVER, HOOD, CREST, NECK, WAIST;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final int SLOTS = Slot.values().length;
	private static final List<ItemStack> NONE = Collections.nCopies(SLOTS, ItemStack.EMPTY);

	/** What each place holds (always {@link #SLOTS} long). Saved; synced (drawn on the wearer); a player keeps it through death as they keep their inventory. */
	public static final AttachmentType<List<ItemStack>> WORN = AttachmentRegistry.create(JaymosKingdoms.id("harness"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath()
			.syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.all()));

	public static MenuType<HarnessMenu> MENU;

	/** Player → server: open the harness. */
	public record Open() implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("harness_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.unit(new Open());

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** A piece for one of the places: right-clicked, it goes on if that place is free. */
	static final class Part extends Item {
		Part(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			Slot slot = slotOf(stack);
			if (slot == null || !get(player, slot).isEmpty() || !fits(player, slot) || !HeraldryWear.mayWear(player, stack)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				set(player, slot, stack.copyWithCount(1));
				stack.shrink(1);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
	}

	private Harness() {
	}

	static void register() {
		MENU = Registry.register(BuiltInRegistries.MENU, JaymosKingdoms.id("harness"), new MenuType<>(HarnessMenu::new, FeatureFlags.VANILLA_SET));
		PayloadTypeRegistry.serverboundPlay().register(Open.TYPE, Open.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Open.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			if (player.isAlive() && !player.isSpectator()) {
				player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new HarnessMenu(id, inventory, p),
					Component.translatable("container.jaymoskingdoms.harness")));
			}
		}));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && !blocked && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
				wear(player, base);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && !player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
				spill(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refresh(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					check(player);
				}
			}
		});
	}

	// ---- what is worn --------------------------------------------------------------------------------------------------

	/**
	 * Everything in {@code wearer}'s harness, by {@link Slot#ordinal()} (empty stacks for bare places). A harness saved
	 * before the gorget and tassets had places is shorter: its pieces stay where they were and the new places are bare.
	 */
	public static List<ItemStack> worn(LivingEntity wearer) {
		List<ItemStack> worn = wearer.getAttached(WORN);
		if (worn == null || worn.isEmpty() || worn.size() > SLOTS) {
			return NONE;
		}
		if (worn.size() < SLOTS) {
			List<ItemStack> padded = new ArrayList<>(worn);
			while (padded.size() < SLOTS) {
				padded.add(ItemStack.EMPTY);
			}
			return padded;
		}
		return worn;
	}

	public static ItemStack get(LivingEntity wearer, Slot slot) {
		return worn(wearer).get(slot.ordinal());
	}

	/** Puts {@code stack} in {@code slot} of {@code wearer}'s harness (empty to clear it), and its armour with it. */
	public static void set(LivingEntity wearer, Slot slot, ItemStack stack) {
		List<ItemStack> now = new ArrayList<>(worn(wearer));
		now.set(slot.ordinal(), stack);
		put(wearer, now);
	}

	/** Puts each of {@code pieces} in its own place on {@code wearer}, over whatever was there. */
	public static void dress(LivingEntity wearer, ItemStack... pieces) {
		List<ItemStack> now = new ArrayList<>(worn(wearer));
		for (ItemStack piece : pieces) {
			Slot slot = slotOf(piece);
			if (slot != null) {
				now.set(slot.ordinal(), piece);
			}
		}
		put(wearer, now);
	}

	private static void put(LivingEntity wearer, List<ItemStack> worn) {
		if (worn.stream().allMatch(ItemStack::isEmpty)) {
			wearer.removeAttached(WORN);
		} else {
			wearer.setAttached(WORN, List.copyOf(worn));
		}
		refresh(wearer);
	}

	/** The harness's pieces were changed where they lie (fitted, oiled): saved and sent to every client again. */
	static void resync(LivingEntity wearer) {
		put(wearer, new ArrayList<>(worn(wearer)));
	}

	/** Takes everything off {@code wearer} (nothing is dropped). */
	public static void clear(LivingEntity wearer) {
		put(wearer, NONE);
	}

	/** The place {@code stack} goes in, or null if it isn't a piece of the harness. */
	public static @Nullable Slot slotOf(ItemStack stack) {
		HeraldryArmour.Piece piece = HeraldryArmour.of(stack);
		return piece == null ? null : piece.slot();
	}

	/**
	 * Whether {@code slot} can be filled on {@code wearer} as they are dressed now: spaulders are fitted after the chest
	 * piece (a chestplate, or a shirt of mail or padding), and a crest stands on a helm.
	 */
	public static boolean fits(LivingEntity wearer, Slot slot) {
		return switch (slot) {
			case SHOULDERS -> !wearer.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || !get(wearer, Slot.MAIL).isEmpty() || !get(wearer, Slot.PADDING).isEmpty();
			case CREST -> !wearer.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
			default -> true;
		};
	}

	/**
	 * (for Jaymo's Powers, {@link PowersCompat#shareHarness}) What a blow to {@code place} of {@code wearer} meets in the
	 * harness, outermost first. "torso": the mail and padding under the chest piece. "arms": the plate on the limb (arm
	 * harness, else spaulders, else gauntlets) over the shirt's sleeves, or nothing when no limb piece is worn (the blow
	 * is the chest piece's then). "legs": the cuisses, else the greaves (over whatever leggings are worn). "all": every
	 * piece that has weight.
	 */
	public static List<ItemStack> layers(LivingEntity wearer, String place) {
		List<ItemStack> worn = worn(wearer);
		if (worn == NONE) {
			return List.of();
		}
		List<ItemStack> out = new ArrayList<>();
		switch (place) {
			case "torso" -> add(out, worn, Slot.MAIL, Slot.PADDING);
			case "arms" -> {
				ItemStack limb = first(worn, Slot.ARMS, Slot.SHOULDERS, Slot.HANDS);
				if (!limb.isEmpty()) {
					out.add(limb);
					add(out, worn, Slot.MAIL, Slot.PADDING);
				}
			}
			case "legs" -> {
				ItemStack limb = first(worn, Slot.THIGHS, Slot.SHINS);
				if (!limb.isEmpty()) {
					out.add(limb);
				}
			}
			case "all" -> add(out, worn, Slot.PADDING, Slot.MAIL, Slot.NECK, Slot.SHOULDERS, Slot.ARMS, Slot.HANDS, Slot.WAIST, Slot.THIGHS, Slot.SHINS);
			default -> {
			}
		}
		return out;
	}

	private static void add(List<ItemStack> out, List<ItemStack> worn, Slot... slots) {
		for (Slot slot : slots) {
			if (!worn.get(slot.ordinal()).isEmpty()) {
				out.add(worn.get(slot.ordinal()));
			}
		}
	}

	private static ItemStack first(List<ItemStack> worn, Slot... slots) {
		for (Slot slot : slots) {
			if (!worn.get(slot.ordinal()).isEmpty()) {
				return worn.get(slot.ordinal());
			}
		}
		return ItemStack.EMPTY;
	}

	// ---- armour, wear, loss --------------------------------------------------------------------------------------------

	/** Sets {@code wearer}'s armour and toughness from the harness to what its pieces give now. */
	static void refresh(LivingEntity wearer) {
		if (wearer.level().isClientSide()) {
			return;
		}
		List<ItemStack> worn = worn(wearer);
		for (Slot slot : Slot.values()) {
			HeraldryArmour.Piece piece = HeraldryArmour.of(worn.get(slot.ordinal()));
			modifier(wearer, Attributes.ARMOR, slot, piece == null ? 0.0 : piece.armour());
			modifier(wearer, Attributes.ARMOR_TOUGHNESS, slot, piece == null ? 0.0 : piece.toughness());
		}
		Fitting.bonus(wearer);
	}

	private static void modifier(LivingEntity wearer, Holder<Attribute> attribute, Slot slot, double amount) {
		AttributeInstance instance = wearer.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		net.minecraft.resources.Identifier id = JaymosKingdoms.id("harness_" + slot.key());
		AttributeModifier has = instance.getModifier(id);
		if (amount <= 0.0) {
			if (has != null) {
				instance.removeModifier(id);
			}
		} else if (has == null || has.amount() != amount) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** A blow of {@code damage} met {@code player}'s armour: some of the harness wears with it. */
	private static void wear(ServerPlayer player, float damage) {
		List<ItemStack> worn = worn(player);
		if (worn == NONE || player.getAbilities().invulnerable) {
			return;
		}
		List<ItemStack> now = null;
		for (int i = 0; i < SLOTS; i++) {
			if (worn.get(i).isDamageableItem() && player.getRandom().nextFloat() < WEAR_CHANCE) {
				if (now == null) {
					now = new ArrayList<>(worn);
				}
				ItemStack piece = worn.get(i).copy();
				piece.hurtAndBreak(Math.max(1, Math.round(damage * WEAR_SHARE)), player.level(), player, broken -> player.level().playSound(null,
					player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 1.0F));
				now.set(i, piece);
			}
		}
		if (now != null) {
			put(player, now);
		}
	}

	/** {@code player} died without keeping their inventory: the harness falls where they did. */
	private static void spill(ServerPlayer player) {
		for (ItemStack stack : worn(player)) {
			if (!stack.isEmpty()) {
				player.spawnAtLocation(player.level(), stack.copy());
			}
		}
		clear(player);
	}

	/** Pieces that no longer fit how {@code player} is dressed (spaulders without a chest piece, a crest without a helm) or that they may not wear come off into the inventory. */
	private static void check(ServerPlayer player) {
		// (a fitted helm, cuirass or sabatons put on or taken off in the inventory counts here)
		Fitting.bonus(player);
		List<ItemStack> worn = worn(player);
		if (worn == NONE) {
			return;
		}
		for (Slot slot : Slot.values()) {
			ItemStack stack = worn.get(slot.ordinal());
			if (!stack.isEmpty() && (!fits(player, slot) || !HeraldryWear.mayWear(player, stack))) {
				if (!HeraldryWear.mayWear(player, stack)) {
					HeraldryWear.refused(player);
				}
				set(player, slot, ItemStack.EMPTY);
				player.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY);
			}
		}
	}

	/** Gives {@code player} a piece: on in its place if that is free and fits, else into the inventory. */
	static void give(ServerPlayer player, ItemStack stack) {
		Slot slot = slotOf(stack);
		if (slot != null && get(player, slot).isEmpty() && fits(player, slot)) {
			set(player, slot, stack);
		} else {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}
}
