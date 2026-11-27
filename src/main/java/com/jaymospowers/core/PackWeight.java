package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.BodyWeapons;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.combat.Armed;
import com.jaymospowers.compat.ToolBeltCompat;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

/**
 * What a player carries weighs (MT Inventory Weight's idea, CC0, by megatrex4; our own numbers, in kilograms like
 * {@link ThrowWeight}). Every item has a weight ({@link #kilograms}): worked out from what it is (a block by its hardness,
 * a full bucket, an ingot, a great weapon, a spare suit of plate...) unless a data pack puts it in one of the weight tags
 * ({@code jaymospowers:weight/feather} to {@code weight/massive}). A container's contents (a shulker box, a bundle, a
 * tool belt) weigh with it. What's packed (the inventory, both hands, the back slot, the worn backpack and tool belt,
 * and a body carried in the arms, by {@link #CARRIED_SHARE} of its {@link ThrowWeight#kilograms}, so a fattened Animal
 * Weights pig is a heavier load) is set against what the body bears freely ({@link #BASE_KG}, more with the pockets of
 * worn clothes, a backpack's straps and a tool belt).
 * <p>Within that, nothing changes: nobody is slowed for carrying a sensible load. Past it the overload ({@link #over},
 * 0 to 1) slows the step, the swing and the jump, adds to the armour's load wherever that tires ({@code Exertion}) and
 * weighs the walk down ({@code client/moving/BodyWeight}). Worn armour isn't counted here: {@link ArmourKind#load}
 * already weighs it (soaked padding twice). A Gravity user feels no weight ({@code GravityWeight}).
 */
public final class PackWeight {
	// ===== the knobs (kilograms) ====================================================================================
	/** What a body bears without slowing, before pockets. */
	public static final float BASE_KG = 60.0F;
	/** Pockets: each worn piece of padding, mail or plate (padded coats have the most), a worn backpack, a worn tool belt. */
	public static final float POCKET_CLOTH = 3.0F, POCKET_MAIL = 2.0F, POCKET_PLATE = 1.0F, BACKPACK_KG = 30.0F, TOOL_BELT_KG = 6.0F;
	/** Share of a carried body's weight that counts (borne across the shoulders, the legs take most of it). */
	public static final float CARRIED_SHARE = 0.3F;
	/** The overload is how far past what's borne freely, as a share of it, up to this. */
	public static final float MAX_OVER = 1.0F;
	/** At full overload: speed lost, swing speed lost, jump lost (shares). */
	public static final float SLOW_AT_FULL = 0.45F, SWING_AT_FULL = 0.3F, JUMP_AT_FULL = 0.35F;
	/** How often (ticks) the load is weighed again. */
	public static final int CHECK_TICKS = 10;
	/**
	 * Items: a block (and per point of hardness, up to the most), slab share, and a block that breaks at a touch (a torch,
	 * a flower, seeds, redstone dust, string: all placed as blocks, none of them heavy).
	 */
	public static final float BLOCK_KG = 0.4F, BLOCK_HARDNESS_KG = 0.15F, BLOCK_MOST_KG = 6.0F, SLAB_SHARE = 0.5F,
		INSTANT_BLOCK_KG = 0.1F;
	/** An empty bucket, a full one, a bottle or potion, an ingot, a nugget, a gem or crystal, a mouthful of food, anything else. */
	public static final float BUCKET_KG = 1.0F, FULL_BUCKET_KG = 10.0F, BOTTLE_KG = 0.5F, INGOT_KG = 1.0F, NUGGET_KG = 0.1F, GEM_KG = 0.2F,
		FOOD_KG = 0.3F, ITEM_KG = 0.1F;
	/** A tool, a one-handed weapon, a great (two-handed) weapon, a polearm, a shield, a bow, a crossbow or gun. */
	public static final float TOOL_KG = 1.5F, ONE_HAND_KG = 1.5F, TWO_HAND_KG = 4.0F, POLE_KG = 3.0F, SHIELD_KG = 4.0F, BOW_KG = 1.0F,
		CROSSBOW_KG = 3.0F;
	/** A spare (unworn) piece of padding, mail or plate. */
	public static final float LOOSE_CLOTH = 1.5F, LOOSE_MAIL = 4.0F, LOOSE_PLATE = 6.0F;
	/** The data-pack weight tags and what each weighs. */
	public static final float FEATHER_KG = 0.01F, LIGHT_KG = 0.1F, MEDIUM_KG = 1.0F, HEAVY_KG = 5.0F, MASSIVE_KG = 20.0F;
	// ================================================================================================================

	public static final TagKey<Item> FEATHER = tag("feather"), LIGHT = tag("light"), MEDIUM = tag("medium"), HEAVY = tag("heavy"),
		MASSIVE = tag("massive");

	/** What a player carries and bears freely (tenths of a kilogram), synced to everyone (the walk is drawn by it). */
	public record Load(int carried, int bearable) {
		public static final StreamCodec<ByteBuf, Load> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Load::carried, ByteBufCodecs.VAR_INT, Load::bearable, Load::new);

		public float carriedKg() {
			return carried / 10.0F;
		}

		public float bearableKg() {
			return bearable / 10.0F;
		}
	}

	public static final AttachmentType<Load> LOAD = AttachmentRegistry.create(JaymosPowers.id("pack_weight"),
		builder -> builder.syncWith(Load.CODEC, AttachmentSyncPredicate.all()));

	private static final net.minecraft.resources.Identifier SPEED = JaymosPowers.id("pack_weight_speed"),
		SWING = JaymosPowers.id("pack_weight_swing"), JUMP = JaymosPowers.id("pack_weight_jump");

	private PackWeight() {
	}

	private static TagKey<Item> tag(String name) {
		return TagKey.create(Registries.ITEM, JaymosPowers.id("weight/" + name));
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PackWeight::tick);
	}

	/** What {@code player} carries and bears now, as last weighed (null before the first weighing); client and server. */
	public static @Nullable Load load(Player player) {
		return player.getAttached(LOAD);
	}

	/** The overload, 0 (within what's borne freely) to {@link #MAX_OVER}; client and server. */
	public static float over(Player player) {
		Load load = load(player);
		if (load == null || load.bearable() <= 0) {
			return 0.0F;
		}
		return Math.clamp(load.carried() / (float)load.bearable() - 1.0F, 0.0F, MAX_OVER);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean free = player.isCreative() || player.isSpectator() || com.jaymospowers.power.gravity.GravityWeight.lightens(player);
			Load load = free ? null : new Load(Math.round(carried(player) * 10.0F), Math.round(bearable(player) * 10.0F));
			if (load == null) {
				if (player.getAttached(LOAD) != null) {
					player.removeAttached(LOAD);
				}
			} else if (!load.equals(player.getAttached(LOAD))) {
				player.setAttached(LOAD, load);
			}
			apply(player, over(player));
		}
	}

	/** Everything {@code player} carries (kilograms), worn armour aside. */
	static float carried(ServerPlayer player) {
		float kg = 0.0F;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			kg += stackKilograms(inventory.getItem(slot));
		}
		kg += stackKilograms(player.getOffhandItem());
		List<ItemStack> worn = Baubles.worn(player);
		kg += stackKilograms(worn.get(Baubles.BACK_SLOT));
		kg += stackKilograms(worn.get(Baubles.BELT_SLOT));
		ItemStack backpack = player.getAttached(com.jaymospowers.backpack.Backpack.WORN);
		if (backpack != null) {
			kg += stackKilograms(backpack);
		}
		Entity held = Carry.held(player);
		if (held != null && !CarryBlock.is(held)) {
			kg += (float)ThrowWeight.kilograms(held) * CARRIED_SHARE;
		} else if (held != null) {
			kg += (float)ThrowWeight.BLOCK_KG;
		}
		return kg;
	}

	/** What {@code player} bears without slowing (kilograms): the base, and the pockets of what they wear. */
	static float bearable(ServerPlayer player) {
		float kg = BASE_KG;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			kg += switch (ArmourKind.worn(player, slot)) {
				case CLOTH -> POCKET_CLOTH;
				case MAIL -> POCKET_MAIL;
				case PLATE -> POCKET_PLATE;
				case NONE -> 0.0F;
			};
		}
		if (com.jaymospowers.backpack.Backpack.wearing(player)) {
			kg += BACKPACK_KG;
		}
		if (!ToolBeltCompat.worn(player).isEmpty()) {
			kg += TOOL_BELT_KG;
		}
		return kg;
	}

	/** The step, the swing and the jump, slowed by the overload (or let go again). */
	private static void apply(ServerPlayer player, float over) {
		set(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, -over * SLOW_AT_FULL);
		set(player.getAttribute(Attributes.ATTACK_SPEED), SWING, -over * SWING_AT_FULL);
		set(player.getAttribute(Attributes.JUMP_STRENGTH), JUMP, -over * JUMP_AT_FULL);
	}

	private static void set(@Nullable AttributeInstance instance, net.minecraft.resources.Identifier id, float share) {
		if (instance == null) {
			return;
		}
		// (a hundredth at a time, so a crumb more doesn't give a new modifier every check)
		double amount = Math.round(share * 100.0F) / 100.0;
		AttributeModifier now = instance.getModifier(id);
		if (amount == 0.0) {
			if (now != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (now == null || now.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** What the whole stack weighs, contents and all (kilograms). */
	public static float stackKilograms(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0F;
		}
		float kg = kilograms(stack) * stack.getCount();
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents != null) {
			for (ItemStack inside : contents.nonEmptyItemCopyStream().toList()) {
				kg += stackKilograms(inside);
			}
		}
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			for (ItemStack inside : bundle.itemCopies().toList()) {
				kg += stackKilograms(inside);
			}
		}
		return kg;
	}

	/** What one of {@code stack} weighs (kilograms), its contents aside. Both sides (the tooltip shows it). */
	public static float kilograms(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0F;
		}
		if (stack.is(FEATHER)) {
			return FEATHER_KG;
		}
		if (stack.is(LIGHT)) {
			return LIGHT_KG;
		}
		if (stack.is(MEDIUM)) {
			return MEDIUM_KG;
		}
		if (stack.is(HEAVY)) {
			return HEAVY_KG;
		}
		if (stack.is(MASSIVE)) {
			return MASSIVE_KG;
		}
		Item item = stack.getItem();
		if (item instanceof ShieldItem) {
			return SHIELD_KG;
		}
		ArmourKind armour = stack.has(DataComponents.EQUIPPABLE) ? ArmourKind.of(stack) : ArmourKind.NONE;
		if (armour != ArmourKind.NONE) {
			return switch (armour) {
				case CLOTH -> LOOSE_CLOTH;
				case MAIL -> LOOSE_MAIL;
				default -> LOOSE_PLATE;
			};
		}
		Armed.Kind armed = Armed.kind(stack);
		if (armed != null) {
			return switch (armed) {
				case TWO_HAND -> TWO_HAND_KG;
				case POLE -> POLE_KG;
				case ONE_HAND -> ONE_HAND_KG;
			};
		}
		if (item instanceof CrossbowItem || com.jaymospowers.armory.OldGuns.is(stack)) {
			return CROSSBOW_KG;
		}
		if (item instanceof ProjectileWeaponItem) {
			return BOW_KG;
		}
		if (stack.has(DataComponents.TOOL) || BodyWeapons.carriedOnBody(stack)) {
			return TOOL_KG;
		}
		if (item == Items.BUCKET) {
			return BUCKET_KG;
		}
		if (item instanceof BucketItem || item == Items.MILK_BUCKET || item instanceof SolidBucketItem || item instanceof MobBucketItem) {
			return FULL_BUCKET_KG;
		}
		if (item instanceof PotionItem || item == Items.GLASS_BOTTLE || item == Items.HONEY_BOTTLE || item == Items.EXPERIENCE_BOTTLE) {
			return BOTTLE_KG;
		}
		if (item instanceof BlockItem block) {
			float hardness = block.getBlock().defaultDestroyTime();
			if (hardness == 0.0F) {
				return INSTANT_BLOCK_KG;
			}
			hardness = Math.max(0.0F, hardness);
			float kg = Math.min(BLOCK_MOST_KG, BLOCK_KG + hardness * BLOCK_HARDNESS_KG);
			return block.getBlock() instanceof net.minecraft.world.level.block.SlabBlock ? kg * SLAB_SHARE : kg;
		}
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		if (path.contains("nugget")) {
			return NUGGET_KG;
		}
		if (path.contains("ingot")) {
			return INGOT_KG;
		}
		if (path.contains("gem") || path.contains("shard") || path.contains("crystal") || item == Items.DIAMOND || item == Items.EMERALD) {
			return GEM_KG;
		}
		if (stack.has(DataComponents.FOOD)) {
			return FOOD_KG;
		}
		return ITEM_KG;
	}
}
