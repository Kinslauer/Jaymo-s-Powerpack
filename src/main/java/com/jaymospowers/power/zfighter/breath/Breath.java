package com.jaymospowers.power.zfighter.breath;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * (0.18.49, SevTech picks) Breath. The Pale Moon and the Maw ({@link #AIRLESS}) have no air to breathe. Out of the pod, with no
 * air to draw on, a player takes <b>thin air</b>: slowness after {@link #THIN_AFTER} seconds and a point of damage every
 * {@link #THIN_EVERY} seconds. Two things carry air with them, each a reserve of seconds spent one a second while on an airless
 * world, shown as the item's bar:
 * <ul>
 * <li>the <b>Hide Air Skin</b>, worn in the chest slot (no armour): {@link #SKIN_SECONDS} of air; it <i>tears</i> (is gone) when
 * its wearer takes a single hit of more than {@link #TEAR_DAMAGE};</li>
 * <li>the <b>Sealed Helm</b> (forged: the seal blank in {@code WeaponParts}, a glass block at the Tool Station): leather-tier
 * armour with {@link #HELM_SECONDS} of air that never tears.</li>
 * </ul>
 * Worn together the times add (the skin is drawn on first). Standing within {@link #REFILL_RANGE} of a pod for
 * {@link #REFILL_SECONDS} seconds fills both again. {@link Heat} is Cinder's side of this; {@link BreathCompat} Glacia's cold.
 */
public final class Breath {
	// ---- tuning ----
	/** The worlds with no air (destination indices). */
	public static final int[] AIRLESS = {ZFighterSpace.MOON, ZFighterSpace.MAW};
	/** Seconds out of the pod with no air before thin air bites, and the seconds between its hurts once it does. */
	public static final int THIN_AFTER = 20, THIN_EVERY = 5;
	public static final float THIN_DAMAGE = 1.0F;
	/** How long the air skin and the sealed helm last (seconds of air). */
	public static final int SKIN_SECONDS = 180, HELM_SECONDS = 480;
	/** A single hit over this many points tears the air skin. */
	public static final float TEAR_DAMAGE = 6.0F;
	/** Seconds beside a pod (within this many blocks) that fill the gear again. */
	public static final int REFILL_SECONDS = 10;
	public static final double REFILL_RANGE = 4.0;
	/** The sealed helm: leather's armour, a little more wear. */
	static final int HELM_DURABILITY = 55;

	public static final ResourceKey<EquipmentAsset> SKIN_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("air_skin"));
	public static final ResourceKey<EquipmentAsset> HELM_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("sealed_helm"));

	/** Seconds of air a piece of gear has spent. */
	public static DataComponentType<Integer> USED;
	public static Item AIR_SKIN, SEALED_HELM;

	/** Seconds each player has been out in the thin air, and beside a pod. Not saved. */
	private static final Map<UUID, Integer> EXPOSED = new HashMap<>();
	private static final Map<UUID, Integer> BESIDE_POD = new HashMap<>();

	private Breath() {
	}

	public static void init() {
		USED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("air_used"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		ResourceKey<Item> skinKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("air_skin"));
		AIR_SKIN = Registry.register(BuiltInRegistries.ITEM, skinKey, new AirGear(new Item.Properties().setId(skinKey).stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
				.setAsset(SKIN_ASSET)
				.setAllowedEntities(EntityTypes.PLAYER)
				.build()), SKIN_SECONDS, "item.jaymospowers.air_skin.tooltip"));
		ResourceKey<Item> helmKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("sealed_helm"));
		SEALED_HELM = Registry.register(BuiltInRegistries.ITEM, helmKey, new AirGear(new Item.Properties().setId(helmKey).rarity(Rarity.UNCOMMON)
			.humanoidArmor(helmMaterial(), ArmorType.HELMET), HELM_SECONDS, "item.jaymospowers.sealed_helm.tooltip"));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(AIR_SKIN);
			output.accept(SEALED_HELM);
		});
		ServerTickEvents.END_SERVER_TICK.register(Breath::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (entity instanceof ServerPlayer player && damage > TEAR_DAMAGE && player.getItemBySlot(EquipmentSlot.CHEST).is(AIR_SKIN)) {
				tear(player);
			}
		});
		Heat.init();
		BreathCompat.init();
	}

	/** Leather's protection (a helmet's one point) in a sealed shell. */
	private static ArmorMaterial helmMaterial() {
		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		defense.put(ArmorType.BOOTS, 1);
		defense.put(ArmorType.LEGGINGS, 2);
		defense.put(ArmorType.CHESTPLATE, 3);
		defense.put(ArmorType.HELMET, 1);
		defense.put(ArmorType.BODY, 3);
		return new ArmorMaterial(HELM_DURABILITY, defense, 9, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR, HELM_ASSET);
	}

	// ---------------------------------------------------------------- the gear

	/** Seconds of air a piece of gear has left (0 for anything that isn't air gear). */
	public static int left(ItemStack stack) {
		return stack.getItem() instanceof AirGear gear ? Math.max(0, gear.seconds - stack.getOrDefault(USED, 0)) : 0;
	}

	/** Spends {@code seconds} of the gear's air. */
	static void spend(ItemStack stack, int seconds) {
		if (stack.getItem() instanceof AirGear gear) {
			stack.set(USED, Mth.clamp(stack.getOrDefault(USED, 0) + seconds, 0, gear.seconds));
		}
	}

	/** The air {@code player} carries, in seconds: the skin on their chest and the helm on their head. */
	public static int reserve(Player player) {
		return left(player.getItemBySlot(EquipmentSlot.CHEST)) + left(player.getItemBySlot(EquipmentSlot.HEAD));
	}

	private static boolean wearsGear(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).is(AIR_SKIN) || player.getItemBySlot(EquipmentSlot.HEAD).is(SEALED_HELM);
	}

	/** A hard hit: the skin is gone. */
	private static void tear(ServerPlayer player) {
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.air.torn").withStyle(ChatFormatting.RED));
	}

	/** Whether {@code level} is one of the worlds with no air. */
	public static boolean airless(Level level) {
		for (int index : AIRLESS) {
			if (ZFighterSpace.DESTS[index].level() == level.dimension()) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- every second

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (!player.isAlive() || player.isSpectator()) {
				EXPOSED.remove(id);
				BESIDE_POD.remove(id);
				continue;
			}
			refill(player);
			if (player.isCreative() || !airless(player.level())) {
				EXPOSED.remove(id);
				continue;
			}
			if (player.getVehicle() instanceof PodEntity || com.jaymospowers.power.zfighter.backhome.FarFood.breathing(player)) {
				// inside the pod, or (0.18.56) on a far-world meal's breath
				EXPOSED.remove(id);
				continue;
			}
			if (reserve(player) > 0) {
				EXPOSED.remove(id);
				ItemStack skin = player.getItemBySlot(EquipmentSlot.CHEST);
				spend(left(skin) > 0 ? skin : player.getItemBySlot(EquipmentSlot.HEAD), 1);
				if (reserve(player) == 0) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.air.spent").withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			int seconds = EXPOSED.merge(id, 1, Integer::sum);
			if (seconds == THIN_AFTER) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.air.thin").withStyle(ChatFormatting.GRAY));
			}
			if (seconds >= THIN_AFTER) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false, true));
				if ((seconds - THIN_AFTER) % THIN_EVERY == 0) {
					ServerLevel level = player.level();
					player.hurtServer(level, player.damageSources().drown(), THIN_DAMAGE);
					level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getEyeY(), player.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
				}
			}
		}
	}

	/** Beside a pod long enough, the gear is full again. */
	private static void refill(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!wearsGear(player) || !gearUsed(player)) {
			BESIDE_POD.remove(id);
			return;
		}
		AABB box = player.getBoundingBox().inflate(REFILL_RANGE);
		boolean near = player.getVehicle() instanceof PodEntity || !player.level().getEntitiesOfClass(PodEntity.class, box).isEmpty();
		if (!near) {
			BESIDE_POD.remove(id);
			return;
		}
		if (BESIDE_POD.merge(id, 1, Integer::sum) >= REFILL_SECONDS) {
			BESIDE_POD.remove(id);
			player.getItemBySlot(EquipmentSlot.CHEST).remove(USED);
			player.getItemBySlot(EquipmentSlot.HEAD).remove(USED);
			player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.6F, 1.5F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.air.filled").withStyle(ChatFormatting.AQUA));
		}
	}

	private static boolean gearUsed(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).has(USED) || player.getItemBySlot(EquipmentSlot.HEAD).has(USED);
	}

	/** A piece of air gear: its bar is the air it has left, and it says how much on its tooltip. */
	public static final class AirGear extends Item {
		final int seconds;
		private final String tooltip;

		AirGear(Item.Properties properties, int seconds, String tooltip) {
			super(properties);
			this.seconds = seconds;
			this.tooltip = tooltip;
		}

		@Override
		public boolean isBarVisible(ItemStack stack) {
			return stack.has(USED) || super.isBarVisible(stack);
		}

		@Override
		public int getBarWidth(ItemStack stack) {
			return stack.has(USED) ? Mth.clamp(Math.round(MAX_BAR_WIDTH * left(stack) / (float) seconds), 0, MAX_BAR_WIDTH) : super.getBarWidth(stack);
		}

		@Override
		public int getBarColor(ItemStack stack) {
			return stack.has(USED) ? 0x7FD0FF : super.getBarColor(stack);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
			int left = left(stack);
			builder.accept(Component.translatable("tooltip.jaymospowers.air_left", left / 60, String.format("%02d", left % 60)).withStyle(ChatFormatting.AQUA));
		}
	}

	/** The pieces of air gear, for the callers that list them. */
	public static List<Item> gear() {
		return List.of(AIR_SKIN, SEALED_HELM);
	}
}
