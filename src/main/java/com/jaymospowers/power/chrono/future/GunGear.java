package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.TaczGuns;
import com.jaymospowers.core.fx.MagicFx;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The future's gun gear (Andy's Ragdolls and Cover picks, after Raiiiden's Fracture Point, TaCZ Tracers &amp; Flash and
 * TaCZ Magazines; nothing of theirs copied). Andy (2026-11-17): tracers both ways, the spec's magazine sizes, all three
 * at once, and none of it craftable: it comes only off the future's dead and echoes, like their guns
 * ({@link FutureGuns}), so it's in no creative tab either.
 * <ul>
 * <li><b>Tracers.</b> About one ordinary shot in four from a future gun ({@link #TRACER_CHANCE}) leaves a glowing amber
 * streak along its path and lights the muzzle for a moment; tracer rounds, right-clicked onto a loaded gun in the
 * inventory, take the place of that many of its rounds (which come back to you) and always streak.</li>
 * <li><b>Magazines.</b> One per gun family, holding that family's rounds ({@link Family}). Right-click rounds onto one
 * to load it; right-click it onto a gun of its family to swap: the gun takes its rounds and the magazine takes the
 * gun's. Snipers have none (they're loaded by hand). TaCZ's own reload from loose rounds still works.</li>
 * <li><b>Plate carrier.</b> Chest armour some gunners wear and may drop.</li>
 * </ul>
 */
public final class GunGear {
	/** Andy (2026-11-17): an ordinary round from a future gun streaks about 1 in 4 shots. */
	static final float TRACER_CHANCE = 0.25F;
	/** The streak: amber, this wide, held this long. */
	static final int TRACER_COLOR = 0xF5A623, TRACER_TICKS = 20;
	static final float TRACER_WIDTH = 0.08F;
	/** How far a player's shot is traced when the gun isn't one we know. */
	static final double TRACER_RANGE = 64.0;
	/** The muzzle light a tracer gives: this bright, fading out over this many ticks. */
	static final int LIGHT_LEVEL = 12, LIGHT_TICKS = 8;
	/** Rounds a magazine holds, by family (the spec's sizes, Andy 2026-11-17). */
	static final int HANDGUN_ROUNDS = 17, SMG_ROUNDS = 30, RIFLE_ROUNDS = 30, SHOTGUN_ROUNDS = 8, MG_ROUNDS = 60;
	/** Empty magazines stack this high; tracer rounds this high. */
	static final int MAGAZINE_STACK = 6, TRACER_STACK = 16;
	/** A gunner wears a carrier this often and drops it this often. */
	static final float CARRIER_CHANCE = 0.25F, CARRIER_DROP = 0.1F;
	/** A dead gunner drops a magazine for its gun this often (loaded up to this share); tracer rounds this often (1 up to this many). */
	static final float MAGAZINE_DROP = 0.25F, MAGAZINE_FILL = 0.5F, TRACER_DROP = 0.15F;
	static final int TRACER_DROP_MAX = 4;
	/** The plate carrier: iron's chest armour, a little toughness, durability 18 x 16 = 288. */
	static final int CARRIER_ARMOR = 5, CARRIER_DURABILITY = 18, CARRIER_ENCHANTABILITY = 10;
	static final float CARRIER_TOUGHNESS = 2.0F;

	/** In a gun's custom data: how many of its rounds are tracers (fired first). In a magazine's: its family, ammo, rounds and tracers. */
	static final String TRACERS = "JaymosTracers", FAMILY = "MagFamily", AMMO = "MagAmmo", ROUNDS = "MagRounds";

	public static final ResourceKey<EquipmentAsset> CARRIER_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("plate_carrier"));

	/** The families a magazine is made for, by the kind of gun. */
	enum Family {
		HANDGUN("handgun", HANDGUN_ROUNDS), SMG("smg", SMG_ROUNDS), RIFLE("rifle", RIFLE_ROUNDS), SHOTGUN("shotgun", SHOTGUN_ROUNDS),
		MACHINE_GUN("machine_gun", MG_ROUNDS);

		final String id;
		final int rounds;

		Family(String id, int rounds) {
			this.id = id;
			this.rounds = rounds;
		}

		static @Nullable Family of(FutureGuns.Kind kind) {
			if (kind == FutureGuns.PISTOL) {
				return HANDGUN;
			} else if (kind == FutureGuns.SMG) {
				return SMG;
			} else if (kind == FutureGuns.RIFLE) {
				return RIFLE;
			} else if (kind == FutureGuns.SHOTGUN) {
				return SHOTGUN;
			} else if (kind == FutureGuns.MG) {
				return MACHINE_GUN;
			}
			return null;
		}

		static @Nullable Family byId(String id) {
			for (Family family : values()) {
				if (family.id.equals(id)) {
					return family;
				}
			}
			return null;
		}

		/** Whether some gun of this family takes {@code ammo}. */
		boolean takes(String ammo) {
			for (FutureGuns.Gun gun : FutureGuns.MODERN) {
				if (of(gun.kind()) == this && gun.ammo().equals(ammo)) {
					return true;
				}
			}
			return false;
		}
	}

	public static Item PLATE_CARRIER, MAGAZINE, TRACER_ROUND;
	private static @Nullable ArmorMaterial carrier;

	/** Lit muzzles (dimension and block) and when each was lit. */
	private static final Map<Lit, Long> LIT = new HashMap<>();

	private record Lit(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private GunGear() {
	}

	static void init() {
		PLATE_CARRIER = item("plate_carrier", Item::new, p -> p.humanoidArmor(carrierMaterial(), ArmorType.CHESTPLATE)
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymospowers.plate_carrier.lore")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)))));
		MAGAZINE = item("magazine", Magazine::new, p -> p.stacksTo(MAGAZINE_STACK));
		TRACER_ROUND = item("tracer_round", TracerRound::new, p -> p.stacksTo(TRACER_STACK)
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymospowers.tracer_round.lore")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)))));
		ServerTickEvents.END_WORLD_TICK.register(GunGear::fadeLights);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				LIT.keySet().removeIf(lit -> lit.dimension() == level.dimension() && unlight(level, lit.pos()));
			}
			LIT.clear();
		});
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.apply(new Item.Properties().setId(key))));
	}

	private static ArmorMaterial carrierMaterial() {
		if (carrier == null) {
			Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
			for (ArmorType type : ArmorType.values()) {
				defense.put(type, type == ArmorType.CHESTPLATE ? CARRIER_ARMOR : 0);
			}
			carrier = new ArmorMaterial(CARRIER_DURABILITY, defense, CARRIER_ENCHANTABILITY, SoundEvents.ARMOR_EQUIP_IRON, CARRIER_TOUGHNESS, 0.0F,
				ItemTags.REPAIRS_IRON_ARMOR, CARRIER_ASSET);
		}
		return carrier;
	}

	// ---- loot ----

	/** A gunner just armed ({@link FutureGuns#arm}) may wear a carrier, if its chest is bare. */
	static void dress(Mob mob, RandomSource random) {
		if (random.nextFloat() < CARRIER_CHANCE && mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
			mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PLATE_CARRIER));
			mob.setDropChance(EquipmentSlot.CHEST, CARRIER_DROP);
		}
	}

	/** What a dead gunner carrying {@code gun} may drop besides its gun and rounds: a magazine for it, tracer rounds. */
	static void drops(ServerLevel level, Mob mob, FutureGuns.@Nullable Gun gun) {
		if (gun == null) {
			return;
		}
		RandomSource random = level.getRandom();
		Family family = Family.of(gun.kind());
		if (family != null && !gun.ammo().isEmpty() && random.nextFloat() < MAGAZINE_DROP) {
			mob.spawnAtLocation(level, magazine(family, gun.ammo(), random.nextInt(Math.round(family.rounds * MAGAZINE_FILL) + 1)));
		}
		if (random.nextFloat() < TRACER_DROP) {
			mob.spawnAtLocation(level, new ItemStack(TRACER_ROUND, 1 + random.nextInt(TRACER_DROP_MAX)));
		}
	}

	/** A {@code family} magazine holding {@code rounds} of {@code ammo}. */
	static ItemStack magazine(Family family, String ammo, int rounds) {
		ItemStack stack = new ItemStack(MAGAZINE);
		CompoundTag tag = new CompoundTag();
		tag.putString(FAMILY, family.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		fill(stack, ammo, rounds, 0);
		return stack;
	}

	// ---- tracers ----

	/**
	 * A shot from the future gun {@code gun}, from its muzzle to where it stopped: a loaded tracer round, or about one
	 * ordinary round in four, streaks and lights the muzzle.
	 */
	static void shot(ServerLevel level, ItemStack gun, Vec3 muzzle, Vec3 end) {
		if (TaczGuns.gunId(gun).isEmpty()) {
			return;
		}
		int tracers = intIn(gun, TRACERS);
		if (tracers > 0) {
			CustomData.update(DataComponents.CUSTOM_DATA, gun, tag -> tag.putInt(TRACERS, tracers - 1));
		} else if (level.getRandom().nextFloat() >= TRACER_CHANCE) {
			return;
		}
		MagicFx.stroke(level, muzzle, end, null, TRACER_WIDTH, TRACER_COLOR, TRACER_TICKS);
		light(level, muzzle);
	}

	/** A light at the muzzle for a moment, if there's air there. */
	private static void light(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		Lit lit = new Lit(level.dimension(), pos.immutable());
		if (level.getBlockState(pos).isAir() || LIT.containsKey(lit) && level.getBlockState(pos).is(Blocks.LIGHT)) {
			level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL), Block.UPDATE_ALL);
			LIT.put(lit, level.getGameTime());
		}
	}

	/** The muzzle lights dim and go out. */
	private static void fadeLights(ServerLevel level) {
		long now = level.getGameTime();
		for (Iterator<Map.Entry<Lit, Long>> it = LIT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Lit, Long> entry = it.next();
			if (entry.getKey().dimension() != level.dimension()) {
				continue;
			}
			BlockPos pos = entry.getKey().pos();
			long age = now - entry.getValue();
			if (age >= LIGHT_TICKS || !level.getBlockState(pos).is(Blocks.LIGHT)) {
				unlight(level, pos);
				it.remove();
				continue;
			}
			int bright = Math.max(1, (int)(LIGHT_LEVEL * (LIGHT_TICKS - age) / LIGHT_TICKS));
			if (level.getBlockState(pos).getValue(LightBlock.LEVEL) != bright) {
				level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, bright), Block.UPDATE_ALL);
			}
		}
	}

	private static boolean unlight(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).is(Blocks.LIGHT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		return true;
	}

	// ---- data ----

	private static int intIn(ItemStack stack, String key) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(key, 0);
	}

	private static String stringIn(ItemStack stack, String key) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(key, "");
	}

	/** Sets what a magazine holds; an empty one forgets its ammo and stacks again. */
	private static void fill(ItemStack magazine, String ammo, int rounds, int tracers) {
		CustomData.update(DataComponents.CUSTOM_DATA, magazine, tag -> {
			if (rounds > 0) {
				tag.putString(AMMO, ammo);
				tag.putInt(ROUNDS, rounds);
			} else {
				tag.remove(AMMO);
				tag.remove(ROUNDS);
			}
			if (rounds > 0 && tracers > 0) {
				tag.putInt(TRACERS, Math.min(tracers, rounds));
			} else {
				tag.remove(TRACERS);
			}
		});
		if (rounds > 0) {
			magazine.set(DataComponents.MAX_STACK_SIZE, 1);
		} else {
			magazine.remove(DataComponents.MAX_STACK_SIZE);
		}
	}

	/** The ammo id of loose TaCZ rounds, or "". */
	private static String roundsOf(ItemStack stack) {
		return stack.isEmpty() || !"tacz".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()) ? "" : stringIn(stack, "AmmoId");
	}

	/** Rounds given back to {@code player} (by the server; the client is told). */
	private static void giveBack(Player player, ItemStack stack) {
		if (!stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}

	/** A magazine for one family of guns: load it by hand, swap it into a gun. */
	static final class Magazine extends Item {
		Magazine(Item.Properties properties) {
			super(properties);
		}

		/** Rounds right-clicked onto it go in, up to what it holds. */
		@Override
		public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
			if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
				return false;
			}
			String ammo = roundsOf(other);
			Family family = Family.byId(stringIn(self, FAMILY));
			int rounds = intIn(self, ROUNDS);
			if (ammo.isEmpty() || family == null || !family.takes(ammo) || rounds > 0 && !ammo.equals(stringIn(self, AMMO))) {
				return false;
			}
			int n = Math.min(family.rounds - rounds, other.getCount());
			if (n <= 0) {
				return true;
			}
			ItemStack one = self;
			if (self.getCount() > 1) {
				// one off a stack of empties: it's loaded on its own
				one = self.split(1);
			}
			other.shrink(n);
			fill(one, ammo, rounds + n, intIn(one, TRACERS));
			if (one != self) {
				giveBack(player, one);
			}
			slot.setChanged();
			player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.2F);
			return true;
		}

		/** Right-clicked onto a gun of its family: the gun takes its rounds, it takes the gun's. */
		@Override
		public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
			if (action != ClickAction.SECONDARY || self.getCount() != 1 || !slot.allowModification(player)) {
				return false;
			}
			ItemStack gun = slot.getItem();
			FutureGuns.Gun known = FutureGuns.BY_ID.get(TaczGuns.gunId(gun));
			Family family = Family.byId(stringIn(self, FAMILY));
			int rounds = intIn(self, ROUNDS);
			if (known == null || family == null || Family.of(known.kind()) != family || rounds > 0 && !known.ammo().equals(stringIn(self, AMMO))) {
				return false;
			}
			int had = intIn(gun, "GunCurrentAmmoCount"), hadTracers = intIn(gun, TRACERS), tracers = intIn(self, TRACERS);
			CustomData.update(DataComponents.CUSTOM_DATA, gun, tag -> {
				tag.putInt("GunCurrentAmmoCount", rounds);
				tag.putInt(TRACERS, tracers);
			});
			int kept = Math.min(had, family.rounds);
			fill(self, known.ammo(), kept, hadTracers);
			if (had > kept) {
				giveBack(player, TaczGuns.modernAmmo(known.ammo(), had - kept));
			}
			slot.setChanged();
			player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 1.4F);
			return true;
		}

		@Override
		public Component getName(ItemStack stack) {
			Family family = Family.byId(stringIn(stack, FAMILY));
			return family == null ? super.getName(stack) : Component.translatable("item.jaymospowers.magazine." + family.id);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			Family family = Family.byId(stringIn(stack, FAMILY));
			int rounds = intIn(stack, ROUNDS);
			if (family != null) {
				String ammo = stringIn(stack, AMMO);
				builder.accept(rounds > 0
					? Component.translatable("item.jaymospowers.magazine.rounds", rounds, family.rounds, ammo.substring(ammo.indexOf(':') + 1)).withStyle(ChatFormatting.GRAY)
					: Component.translatable("item.jaymospowers.magazine.empty", family.rounds).withStyle(ChatFormatting.GRAY));
			}
			builder.accept(Component.translatable("item.jaymospowers.magazine.hint").withStyle(ChatFormatting.DARK_GRAY));
			builder.accept(Component.translatable("item.jaymospowers.magazine.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Tracer rounds: right-clicked onto a loaded future gun, they take the place of that many of its rounds. */
	static final class TracerRound extends Item {
		TracerRound(Item.Properties properties) {
			super(properties);
		}

		@Override
		public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
			if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
				return false;
			}
			ItemStack gun = slot.getItem();
			if (TaczGuns.gunId(gun).isEmpty()) {
				return false;
			}
			int tracers = intIn(gun, TRACERS);
			int n = Math.min(self.getCount(), TaczGuns.ammo(gun) - tracers);
			if (n <= 0) {
				return true;
			}
			CustomData.update(DataComponents.CUSTOM_DATA, gun, tag -> tag.putInt(TRACERS, tracers + n));
			self.shrink(n);
			FutureGuns.Gun known = FutureGuns.BY_ID.get(TaczGuns.gunId(gun));
			if (known != null && !known.ammo().isEmpty()) {
				giveBack(player, TaczGuns.modernAmmo(known.ammo(), n));
			}
			slot.setChanged();
			player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.5F);
			return true;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.jaymospowers.tracer_round.hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
