package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The <b>Void Crown</b> ({@code jaymospowers:void_crown}): a relic helmet only the Void Arsenal ever meets. Now and then
 * ({@link #FIND_CHANCE}) something else comes through a corrupted Reach behind the weapon. It's bound to the brow it
 * settles on (Curse of Binding), and it only settles on a brow the void has taken: anyone else is refused, and if the
 * void lets its wearer go, the crown slips off.
 *
 * <p>Struck by something (a creature or a projectile), its wearer steps aside the way an Enderman does: to a random safe
 * spot within {@link #STEP_RANGE}. Jaymo's Kingdoms looks for it by id in the head slot (every faction then sees you at
 * your worst); nothing here knows about that.
 */
public final class VoidCrown {
	/** Armour and toughness (a netherite helmet is 3 and 3). */
	static final int ARMOR = 6;
	static final float TOUGHNESS = 3.0F;
	/** Durability = 11 × this (a netherite helmet is 11 × 37). */
	static final int DURABILITY_MULTIPLIER = 45;
	static final int ENCHANTABILITY = 15;
	/** Chance a corrupted Reach brings the crown through too, while you don't already have one. */
	static final float FIND_CHANCE = 0.02F;
	/** Ender step: how far (horizontally, and up or down), how many tries, and how long before it can happen again. */
	static final double STEP_RANGE = 16.0;
	static final int STEP_HEIGHT = 8;
	static final int STEP_ATTEMPTS = 16;
	static final int STEP_COOLDOWN = 30;
	/** Wearers are checked this often (ticks). */
	static final int CHECK_INTERVAL = 10;

	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("void_crown"));

	public static Item CROWN;

	/** Wearer → game time their next ender step is allowed. */
	private static final Map<UUID, Long> STEP_READY = new HashMap<>();

	private VoidCrown() {
	}

	static void register() {
		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		for (ArmorType type : ArmorType.values()) {
			defense.put(type, type == ArmorType.HELMET ? ARMOR : 0);
		}
		ArmorMaterial material = new ArmorMaterial(DURABILITY_MULTIPLIER, defense, ENCHANTABILITY, SoundEvents.ARMOR_EQUIP_NETHERITE,
			TOUGHNESS, 0.0F, ItemTags.REPAIRS_NETHERITE_ARMOR, ASSET);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("void_crown"));
		Item.Properties properties = new Item.Properties().setId(key)
			.humanoidArmor(material, ArmorType.HELMET)
			// players only, and never off a dispenser
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
				.setAsset(ASSET)
				.setAllowedEntities(EntityTypes.PLAYER)
				.setDispensable(false)
				.build())
			.delayedComponent(DataComponents.ENCHANTMENTS, registries -> {
				ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
				enchantments.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
				return enchantments.toImmutable();
			})
			.rarity(Rarity.EPIC)
			.fireResistant()
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.void_crown.lore1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
				Component.translatable("item.jaymospowers.void_crown.lore2").withStyle(ChatFormatting.DARK_PURPLE))));
		CROWN = Registry.register(BuiltInRegistries.ITEM, key, new Crown(properties));
		// beside the other relics
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(CROWN));

		ServerLivingEntityEvents.AFTER_DAMAGE.register(VoidCrown::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(VoidCrown::serverTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	static void clear() {
		STEP_READY.clear();
	}

	// ---------------------------------------------------------------- queries

	public static boolean is(ItemStack stack) {
		return CROWN != null && stack.is(CROWN);
	}

	/** Whether this entity has the crown on its head. */
	public static boolean wearing(@Nullable Entity entity) {
		return entity instanceof LivingEntity living && is(living.getItemBySlot(EquipmentSlot.HEAD));
	}

	/** Only a brow the void has taken (a corrupted Arsenal user's) will hold it. Both sides: the power and Fate are synced. */
	public static boolean mayWear(LivingEntity entity) {
		return ArsenalVoid.isVoid(entity);
	}

	/** From {@code ArsenalLivingEntityMixin}: refuses the head slot to anyone else (inventory clicks, shift-clicks). */
	public static boolean refuses(LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
		return slot == EquipmentSlot.HEAD && is(stack) && !mayWear(entity);
	}

	// ---------------------------------------------------------------- finding it

	/** From a corrupted Reach, once the weapon is in hand: sometimes the crown comes through behind it. */
	static void maybeFind(ServerPlayer player, Vec3 rift) {
		if (player.getRandom().nextFloat() >= FIND_CHANCE || owns(player)) {
			return;
		}
		ItemStack crown = new ItemStack(CROWN);
		player.getInventory().placeItemBackInInventory(crown, Prediction.SERVER_ONLY);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SQUID_INK, rift.x, rift.y, rift.z, 12, 0.2, 0.2, 0.2, 0.02);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, rift.x, rift.y, rift.z, 40, 0.3, 0.3, 0.3, 0.08);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 0.6F, 0.5F);
		ArsenalVoid.whisper(player, "message.jaymospowers.arsenal.crown_found", SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.4F);
	}

	/** On the head, in the inventory or in the Pocket Stash. */
	private static boolean owns(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (is(inventory.getItem(slot))) {
				return true;
			}
		}
		if (wearing(player)) {
			return true;
		}
		NonNullList<ItemStack> stash = NonNullList.withSize(ArsenalStash.SIZE, ItemStack.EMPTY);
		player.getAttachedOrElse(ArsenalStash.STASH, ItemContainerContents.EMPTY).copyInto(stash);
		return stash.stream().anyMatch(VoidCrown::is);
	}

	// ---------------------------------------------------------------- the wrong brow

	private static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (wearing(player) && !mayWear(player)) {
				slipOff(player);
			}
		}
	}

	/** Not (or no longer) the void's: it slides off into the inventory, or to the ground. */
	private static void slipOff(ServerPlayer player) {
		ItemStack crown = player.getItemBySlot(EquipmentSlot.HEAD).copy();
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		if (!player.getInventory().add(crown)) {
			player.drop(crown, false, Prediction.SERVER_ONLY);
		}
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getEyeY(), player.getZ(), 20, 0.3, 0.2, 0.3, 0.05);
		ArsenalVoid.whisper(player, "message.jaymospowers.arsenal.crown_slips", SoundEvents.ENDERMAN_AMBIENT, 0.6F, 0.5F);
	}

	// ---------------------------------------------------------------- ender step

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0.0F || !(entity instanceof ServerPlayer player) || !player.isAlive() || !wearing(player) || !mayWear(player)) {
			return;
		}
		// only something that struck you (a creature, or what it loosed); not falls, the void, hunger, drowning...
		Entity by = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		if (by == null || by == player) {
			return;
		}
		long now = player.level().getGameTime();
		Long ready = STEP_READY.get(player.getUUID());
		if (ready != null && now < ready) {
			return;
		}
		if (enderStep(player)) {
			STEP_READY.put(player.getUUID(), now + STEP_COOLDOWN);
		}
	}

	/**
	 * Steps the wearer aside the way an Enderman teleports: a random spot within {@link #STEP_RANGE}, dropped to the floor
	 * below it, with room to stand and no liquid ({@link LivingEntity#randomTeleport}, minus the blocks Endermen avoid).
	 */
	public static boolean enderStep(ServerPlayer player) {
		ServerLevel level = player.level();
		if (player.isPassenger()) {
			player.stopRiding();
		}
		Vec3 from = player.position();
		for (int i = 0; i < STEP_ATTEMPTS; i++) {
			double x = from.x + (player.getRandom().nextDouble() - 0.5) * 2.0 * STEP_RANGE;
			double y = Mth.clamp(from.y + player.getRandom().nextInt(STEP_HEIGHT * 2 + 1) - STEP_HEIGHT,
				level.getMinY(), level.getMinY() + level.getHeight() - 1);
			double z = from.z + (player.getRandom().nextDouble() - 0.5) * 2.0 * STEP_RANGE;
			if (player.randomTeleport(x, y, z, true, BlockTags.ENDERMAN_DOES_NOT_TELEPORT_TO)) {
				player.resetFallDistance();
				level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 48, 0.3, 0.9, 0.3, 0.8);
				level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 48, 0.3, 0.9, 0.3, 0.8);
				level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
				return true;
			}
		}
		return false;
	}

	/** The crown itself: it won't go on a head it doesn't want. */
	public static final class Crown extends Item {
		Crown(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!mayWear(player)) {
				if (player instanceof ServerPlayer serverPlayer) {
					ArsenalVoid.whisper(serverPlayer, "message.jaymospowers.arsenal.crown_refuses", SoundEvents.ENDERMAN_AMBIENT, 0.5F, 0.6F);
				}
				return InteractionResult.FAIL;
			}
			return super.use(level, player, hand);
		}
	}
}
