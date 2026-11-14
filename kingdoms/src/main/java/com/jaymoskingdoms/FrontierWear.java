package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * Who wears and sells the {@link FrontierArmour} sets (Andy's notes on his picks, 2026-11-14).
 * <ul>
 * <li><b>Templar plate</b>: the guards of every kingdom and fiefdom whose state patron is The Truth or the Crowned Sun
 * ({@link RealmFaiths#patron}); a fiefdom in a Grim keep wears the blackened steel one. Those realms' armourers sell the
 * four pieces and the Templar schematic, to buyers who follow either faith.</li>
 * <li><b>Settler's gear</b>: a village's guards wear all of it, the kettle helm under the hat; farmers and carters wear the
 * hat without the helm, the coat and the trousers ("only the guards should wear the full helm"). Village armourers sell
 * the hat, coat, trousers and boots, and the schematic.</li>
 * <li><b>Ranger's harness</b>: every musketeer and every caravan guard with a {@link Musketoon}; a kingdom rich enough
 * for muskets has the schematic at its armourer's.</li>
 * <li><b>Hedge garb</b>: court wizards wear the wine-red one; tailors sell the plain one, a kingdom's the wine-red too,
 * and the schematic the ashen one is forged from.</li>
 * <li><b>Primalist wrappings</b>: the human raiders of camps in dry country (desert, badlands, savanna), who drop a piece now and then.</li>
 * <li><b>Juggernaut plate</b>: never made. A barbarian chief drops one piece when he falls, and a patron's champion one from Dread on.</li>
 * <li><b>Scoundrel's wrappings</b>: a speakeasy's fence and the smugglers wear them and the fence sells them; a pickpocket has the hood up at night.</li>
 * <li><b>Sanguine coat</b>: tailors sell it, each in the colour they stock.</li>
 * <li><b>Despot's plate</b>: an orc warchief's; his breastplate is his killer's trophy. Only greenskins can keep it on ({@link FrontierPerks}).</li>
 * <li><b>Foxshade mantle</b>: a bandit leader's (he drops it) and the hunting lodge's fletcher's.</li>
 * </ul>
 * Minecraft Comes Alive's villagers dress themselves ({@code McaEquipmentMixin}): a guard's issue comes through
 * {@link #issue}, and what anyone else was given here is never taken off them ({@link #keeps}).
 */
public final class FrontierWear {
	/** How often the villagers near players are looked over (ticks), and how far round each player. */
	static final int SWEEP = 100;
	static final double SWEEP_RANGE = 48.0;
	/** Chance each Primalist piece on a raider is left when he dies; each further Despot piece beyond the breastplate. */
	static final float PRIMALIST_DROP = 0.085F, DESPOT_DROP = 0.25F;
	/** Prices (emeralds, then the town's coin): settler pieces, Templar, the Hedge garb plain and wine-red, Sanguine, Scoundrel; a schematic. */
	static final int[] SETTLER_PRICE = {3, 6, 5, 3}, TEMPLAR_PRICE = {12, 22, 18, 10}, HEDGE_PRICE = {7, 12, 10, 6}, HEDGE_ELITE_PRICE = {12, 20, 16, 10},
		SANGUINE_PRICE = {5, 9, 7, 4}, SCOUNDREL_PRICE = {6, 11, 9, 5};
	static final int SCHEMATIC_PRICE = 24;
	/** A kingdom with this much in its treasury keeps the Ranger schematic at its armourer's (the muskets' own bar). */
	static final long RANGER_TREASURY = 150;
	static final int USES = 3, XP = 5;
	static final float PRICE_MULT = 0.05F;

	private static final ArmorType[] TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
	private static final String[] SANGUINE = {"sanguine", "sanguine_blue", "sanguine_purple"};

	private FrontierWear() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SWEEP == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					sweep(level);
				}
			}
		});
	}

	// ---- what a settlement's guards wear -----------------------------------------------------------------------------

	/** The look {@code f}'s guards are issued, or null for the gear they always had. */
	static @Nullable String guardLook(MinecraftServer server, @Nullable Faction f) {
		if (f == null || f.fallen()) {
			return null;
		}
		if (f.type() == FactionType.VILLAGE) {
			return "settler";
		}
		if (f.type().isRealm() && templar(server, f)) {
			Holds.Hold hold = Holds.get(server, f.id());
			return f.type() == FactionType.FIEFDOM && hold != null && hold.isGrim() ? "templar_steel" : "templar";
		}
		return null;
	}

	/** {@code f} holds to The Truth or the Crowned Sun: its guards are Templars. */
	static boolean templar(MinecraftServer server, Faction f) {
		Patron p = RealmFaiths.patron(server, f);
		return p == Patron.TRUTH || p == Patron.CROWNED_SUN;
	}

	/** {@code player} follows The Truth or the Crowned Sun (the patron they are sworn to). */
	static boolean templar(Player player) {
		Patron sworn = Patrons.of(player);
		return sworn == Patron.TRUTH || sworn == Patron.CROWNED_SUN;
	}

	/** The look {@code mob} (a guard or archer of {@code f}) is in: the Ranger's harness with a gun in hand, else its settlement's. */
	private static @Nullable String lookOf(LivingEntity mob, @Nullable Faction f) {
		if (Musketoon.holds(mob) || mob instanceof Folk folk && Muskets.armed(folk)) {
			return "ranger";
		}
		MinecraftServer server = mob.level().getServer();
		return server == null ? null : guardLook(server, f);
	}

	// ---- our own folk --------------------------------------------------------------------------------------------------

	/** From {@link Folk#setup} and its restyle, after the faction's own gear and before anything is made undroppable. */
	static void folk(Folk folk, Faction faction, Climate climate) {
		int role = folk.role();
		if (role == Folk.GUARD || role == Folk.ARCHER) {
			String look = lookOf(folk, faction);
			if (look != null) {
				FrontierArmour.dress(folk, look, false);
			}
		} else if (role == Folk.FARMER || role == Folk.CARTER) {
			if (faction.type() != FactionType.BARBARIAN) {
				hatAndCoat(folk);
			}
		} else if (role == Sorcery.WIZARD) {
			FrontierArmour.dress(folk, "hedge_elite", false);
		} else if (role == Orcs.WARCHIEF) {
			FrontierArmour.dress(folk, "despot", false);
		}
	}

	/** After {@link Folk#setup} has made its gear undroppable: a dry-country raider's Primalist wrappings, which he may leave behind. */
	static void raider(Folk folk, Faction faction, Climate climate) {
		MinecraftServer server = folk.level().getServer();
		if (folk.role() != Folk.BARBARIAN || server == null || !dry(climate) || Camps.orc(server, faction.id()) || Camps.goblin(server, faction.id())
			|| Camps.illager(server, faction.id()) || Camps.pirate(server, faction.id())) {
			return;
		}
		FrontierArmour.dress(folk, "primalist", false);
		for (ArmorType type : TYPES) {
			folk.setDropChance(type.getSlot(), PRIMALIST_DROP);
		}
	}

	private static boolean dry(Climate climate) {
		return climate == Climate.DESERT || climate == Climate.BADLANDS || climate == Climate.SAVANNA;
	}

	/** A farmhand's or carter's wear: the wide hat (no helm under it), the grey coat and the green trousers. */
	static void hatAndCoat(Mob mob) {
		wear(mob, EquipmentSlot.HEAD, new ItemStack(FrontierArmour.SETTLER_HAT));
		wear(mob, EquipmentSlot.CHEST, FrontierArmour.stack("settler", ArmorType.CHESTPLATE));
		wear(mob, EquipmentSlot.LEGS, FrontierArmour.stack("settler", ArmorType.LEGGINGS));
	}

	/** Puts {@code stack} on {@code mob} if that slot is bare (or holds another of ours), never to be dropped. */
	private static void wear(Mob mob, EquipmentSlot slot, ItemStack stack) {
		ItemStack worn = mob.getItemBySlot(slot);
		if (stack.isEmpty() || worn.is(stack.getItem()) || !worn.isEmpty() && FrontierArmour.setOf(worn) == null) {
			return;
		}
		mob.setItemSlot(slot, stack);
		mob.setDropChance(slot, 0.0F);
	}

	private static void wearAll(Mob mob, String look) {
		for (ArmorType type : TYPES) {
			wear(mob, type.getSlot(), FrontierArmour.stack(look, type));
		}
	}

	// ---- Minecraft Comes Alive's villagers -----------------------------------------------------------------------------

	/**
	 * From {@code McaEquipmentMixin}: {@code stack} is what MCA would put in {@code slot} of {@code villager} (an armed
	 * guard or archer dressing for duty). Their settlement's issue goes on in its place.
	 */
	public static ItemStack issue(LivingEntity villager, EquipmentSlot slot, ItemStack stack) {
		MinecraftServer server = villager.level().getServer();
		if (server == null || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
			return stack;
		}
		Faction f = villager instanceof Worker w && w.factionId() >= 0 ? Factions.get(server, w.factionId())
			: Factions.at(server, villager.level(), villager.blockPosition());
		String look = lookOf(villager, f);
		FrontierArmour.Look l = look == null ? null : FrontierArmour.LOOKS.get(look);
		if (l == null) {
			return stack;
		}
		for (ArmorType type : TYPES) {
			if (type.getSlot() == slot && l.pieces().containsKey(type)) {
				return new ItemStack(l.pieces().get(type));
			}
		}
		return stack;
	}

	/**
	 * From {@code McaEquipmentMixin}: MCA is about to change what {@code villager} has in {@code slot} (putting a sword
	 * in a guard's hand, taking the armour off someone off duty). True if what's there stays: a {@link Musketoon} and the
	 * hand beside it, and any piece of ours on a villager who isn't a guard by trade (a carter's hat, a fence's hood).
	 */
	public static boolean keeps(LivingEntity villager, EquipmentSlot slot, boolean guard) {
		if (slot.getType() == EquipmentSlot.Type.HAND) {
			return Musketoon.holds(villager);
		}
		return !guard && FrontierArmour.setOf(villager.getItemBySlot(slot)) != null;
	}

	/** The villagers round each player: those whose trade has a look of ours put it on. */
	private static void sweep(ServerLevel level) {
		MinecraftServer server = level.getServer();
		boolean night = level.isDarkOutside();
		List<Villager> seen = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SWEEP_RANGE), v -> v.isAlive() && !v.isBaby())) {
				if (!seen.contains(v)) {
					seen.add(v);
				}
			}
		}
		for (Villager v : seen) {
			Worker w = (Object) v instanceof Worker worker ? worker : null;
			int role = w == null ? -1 : w.role();
			if (v.entityTags().contains(Underworld.FENCE_TAG) || Smuggling.runOf(server, v) != null) {
				wearAll(v, "scoundrel");
			} else if (role == Sorcery.WIZARD) {
				wearAll(v, "hedge_elite");
			} else if (role == Folk.CARTER) {
				hatAndCoat(v);
			} else if (role == Folk.GUARD || role == Folk.ARCHER) {
				continue;
			} else if (Crooks.isCrook(v)) {
				// a pickpocket's hood is up after dark, and down again by day
				ItemStack head = v.getItemBySlot(EquipmentSlot.HEAD);
				if (night) {
					wear(v, EquipmentSlot.HEAD, FrontierArmour.stack("scoundrel", ArmorType.HELMET));
				} else if ("scoundrel".equals(FrontierArmour.setOf(head))) {
					v.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
				}
			} else {
				String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
				if (job.equals("farmer")) {
					hatAndCoat(v);
				} else if (job.equals("fletcher")) {
					wear(v, EquipmentSlot.CHEST, FrontierArmour.stack("foxshade", ArmorType.CHESTPLATE));
				}
			}
		}
	}

	// ---- spoils --------------------------------------------------------------------------------------------------------

	/** From {@link Camps#chiefSlain}: a human chief leaves one piece of Juggernaut plate; an orc warchief his tusked breastplate, and maybe more of it. */
	static void chiefFell(ServerLevel level, LivingEntity chief) {
		if (chief instanceof Worker w && w.role() == Orcs.WARCHIEF) {
			for (ArmorType type : TYPES) {
				if (type == ArmorType.CHESTPLATE || level.getRandom().nextFloat() < DESPOT_DROP) {
					drop(level, chief, FrontierArmour.stack("despot", type));
				}
			}
		} else if (chief instanceof Worker w && w.role() == Folk.CHIEF) {
			drop(level, chief, juggernautPiece(level));
		}
	}

	/** One piece of Juggernaut plate, any of the four. */
	static ItemStack juggernautPiece(ServerLevel level) {
		return FrontierArmour.stack("juggernaut", TYPES[level.getRandom().nextInt(TYPES.length)]);
	}

	static void drop(ServerLevel level, LivingEntity from, ItemStack stack) {
		if (!stack.isEmpty()) {
			ItemEntity item = new ItemEntity(level, from.getX(), from.getY() + 0.5, from.getZ(), stack);
			item.setExtendedLifetime();
			level.addFreshEntity(item);
		}
	}

	// ---- shops ---------------------------------------------------------------------------------------------------------

	/** (from {@code VillagerPriceMixin}, when {@code player} opens a trade with {@code villager}) what their settlement's people wear, on their counter. */
	public static void offers(Villager v, Player player) {
		if (!(v.level() instanceof ServerLevel level) || v.isBaby()) {
			return;
		}
		MinecraftServer server = level.getServer();
		MerchantOffers offers = v.getOffers();
		if (v.entityTags().contains(Underworld.FENCE_TAG)) {
			sell(offers, "scoundrel", SCOUNDREL_PRICE, TYPES);
			return;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		Faction f = Quests.settlement(level, v);
		if (f == null) {
			return;
		}
		if (job.equals("armorer")) {
			if (f.type() == FactionType.VILLAGE) {
				offer(offers, new ItemStack(FrontierArmour.SETTLER_HAT), SETTLER_PRICE[0]);
				sell(offers, "settler", SETTLER_PRICE, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS);
				schematic(offers, "settler");
			}
			// the Templar's harness is the faith's: on the counter only for one who follows it
			boolean templar = f.type().isRealm() && templar(server, f);
			if (templar && templar(player)) {
				sell(offers, "templar", TEMPLAR_PRICE, TYPES);
				schematic(offers, "templar");
			} else {
				offers.removeIf(o -> "templar".equals(FrontierArmour.setOf(o.getResult())) || schematicOf(o.getResult(), "templar"));
			}
			if (f.type() == FactionType.KINGDOM && f.treasury() >= RANGER_TREASURY) {
				schematic(offers, "ranger");
			}
		} else if (job.equals("shepherd")) {
			sell(offers, SANGUINE[Math.floorMod(v.getUUID().hashCode(), SANGUINE.length)], SANGUINE_PRICE, TYPES);
			sell(offers, "hedge", HEDGE_PRICE, TYPES);
			if (f.type() == FactionType.KINGDOM) {
				sell(offers, "hedge_elite", HEDGE_ELITE_PRICE, TYPES);
				schematic(offers, "hedge");
			}
		}
	}

	private static void sell(MerchantOffers offers, String look, int[] prices, ArmorType... types) {
		for (ArmorType type : types) {
			offer(offers, FrontierArmour.stack(look, type), prices[type.ordinal()]);
		}
	}

	private static void schematic(MerchantOffers offers, String set) {
		ItemStack stack = PowersCompat.blueprint("frontier/" + set);
		if (!stack.isEmpty() && offers.stream().noneMatch(o -> schematicOf(o.getResult(), set))) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, SCHEMATIC_PRICE), stack, 1, XP, PRICE_MULT));
		}
	}

	/** {@code stack} is the schematic of {@code set} (a Powers blueprint, known by its name's argument). */
	private static boolean schematicOf(ItemStack stack, String set) {
		return !stack.isEmpty() && PowersCompat.blueprintShape(stack).equals("frontier/" + set);
	}

	private static void offer(MerchantOffers offers, ItemStack stack, int price) {
		Item item = stack.getItem();
		if (!stack.isEmpty() && offers.stream().noneMatch(o -> o.getResult().is(item))) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), stack, USES, XP, PRICE_MULT));
		}
	}
}
