package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

/**
 * What a settlement's guards and archers wear, and what it costs it (Andy, 2026-11-19: "i dont like that one kingdom's
 * guards all wear the same suit of expensive armor. everything should have a real cost giving the economy some weight").
 * <p>
 * Who may wear what is Andy's wardrobe list (his Guard Wardrobe Picks page): each piece by rank (militia, soldier,
 * knight, veteran, leader, {@link Ranks}; archers and gunners apart), by land, and by the kind of settlement (kingdom,
 * fiefdom, village, haven; a realm of The Truth or the Crowned Sun, a Grim keep), with his notes as rules: a barbute is
 * a Coin realm's, a spangenhelm Old Blood's in the cold, a great helm a Chivalry realm's leaders', the grotesque visor
 * Conquest's, the frog-mouth helm only for the joust (so never on the field), a realm keeps one helm shape, plate
 * cuirasses where there is iron, Paladins only in Faith and Chivalry realms, quilted cotton brine-soaked in the jungle.
 * <p>
 * The best piece the guard may wear is bought first, and each is paid for: a piece of the spoils in the stores that is
 * as good ({@link Salvage}), the piece itself from the stores, what it is made of there (iron, copper, leather, wool), or
 * emeralds from the treasury, which keeps {@link #TREASURY_KEPT}. What can't be paid for he goes without, and wears the
 * next piece down. At the bottom is leather and mail (Andy: "the basic fallback should be leather and mail based and
 * all on the harness"): mail if it's paid for, the levy's leather for nothing. Over it every guard wears the harness: a
 * quilted coat at the least, the tabard in the realm's colours, and what his rank may have bought.
 * <p>
 * What a guard was issued is kept on him ({@link #KIT}), so it's paid once and put on again from there (MCA's guards
 * dress for duty many times a day). Once a day ({@link #REVIEW}) he is looked over and better bought if there's money
 * now, what he had going back into the stores. A recruit ({@link Ranks#RANK}) wears what his employer gives him instead.
 */
public final class GuardKit {
	/** Emeralds a treasury keeps back (as it does for horses, {@link Cavalry}). */
	static final long TREASURY_KEPT = 40;
	/** How often the guards round each player are looked at (ticks), and how far; how long between a guard's reviews. */
	static final int SWEEP = 200;
	static final double SWEEP_RANGE = 48.0;
	static final long REVIEW = 24000L;
	/** Emeralds per piece (helm, chest, legs, boots): plain mail, bronze, iron; a Ranger's, an Armory set's, folded lamellar, the Archers' hood and tunic. */
	static final int[] MAIL_PRICE = {2, 4, 3, 2}, BRONZE_PRICE = {2, 4, 3, 2}, IRON_PRICE = {4, 7, 6, 3}, RANGER_PRICE = {8, 14, 12, 7},
		ARMORY_PRICE = {9, 16, 13, 8}, FOLDED_PRICE = {8, 14, 12, 7}, ARCHERS_PRICE = {3, 5, 0, 0};
	/** Blackened Templar and steel Ranger pieces cost this much more than the bright ones. */
	static final int STEEL_EXTRA = 4;
	/** What a piece takes at the anvil (ingots, or leather for the settler's gear): helm, chest, legs, boots; mail takes fewer. */
	static final int[] INGOTS = {5, 8, 7, 4}, MAIL_INGOTS = {2, 4, 3, 2};
	/** Harness helms by face; a plain cuirass, a creed's or a kingdom's, a gilt-banded one; the land's chest pieces; sabatons. */
	static final int OPEN_HELM = 4, VISORED_HELM = 7, FIXED_HELM = 6, CUIRASS = 8, CREED_CUIRASS = 10, GILT_CUIRASS = 14, LAND_CHEST = 5,
		CLOTH_CHEST = 3, SABATONS = 4;
	/** The coat of plates: a poorer realm's guards' chest piece, cheaper than a plate cuirass. */
	static final int COAT_OF_PLATES = 6;
	/** The harness's parts: plate (ingots, emeralds), mail and brigandine, cloth (wool, emeralds); the winged crest. */
	static final int PLATE_INGOTS = 3, PLATE_PART = 5, MAIL_PART = 3, CLOTH_WOOL = 3, CLOTH_PART = 3, CREST = HeraldryWear.CREST_PRICE;
	/** Weapons (sword, spear, axe): ingots, emeralds for iron and for copper; a realm's war arm or a polearm; a shield. */
	static final int[] WEAPON_INGOTS = {2, 1, 3}, IRON_WEAPON = {3, 2, 4}, COPPER_WEAPON = {2, 1, 3};
	static final int WAR_ARM = 6, SHIELD = 2;
	/** A camp raider's torn hood, his torn cloak and his horned crest: the share who have each. */
	static final float RAIDER_HOOD = 0.5F, RAIDER_CLOAK = 0.25F, RAIDER_HORNS = 0.33F;

	/** Who: a rank of the troop ladder, an archer, a gunner. */
	static final int MIL = 1, SOL = 2, KN = 4, VET = 8, LEAD = 16, ARCH = 32, GUN = 64;
	/** The kinds of settlement. */
	static final int KINGDOM = 1, FIEF = 2, VILLAGE = 4, HAVEN = 8;
	static final Set<Climate> DRY = EnumSet.of(Climate.SAVANNA, Climate.DESERT, Climate.BADLANDS), COLD = EnumSet.of(Climate.TAIGA, Climate.SNOWY);

	/** A kit's places: the four armour slots, the hands, then the harness. */
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final ArmorType[] TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
	private static final int MAINHAND = 4, OFFHAND = 5, HARNESS = 6, SIZE = HARNESS + Harness.SLOTS;

	private static final Predicate<ItemStack> IRON = s -> s.is(Items.IRON_INGOT) || s.is(Armory.STEEL_INGOTS), COPPER = s -> s.is(Items.COPPER_INGOT),
		LEATHER = s -> s.is(Items.LEATHER), WOOL = s -> s.is(ItemTags.WOOL);

	/** What a guard was issued (by place), for which settlement, as who, and when he was last looked over. */
	public record Kit(int realm, int who, long reviewed, List<ItemStack> pieces) {
		static final Codec<Kit> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("realm").forGetter(Kit::realm),
			Codec.INT.fieldOf("who").forGetter(Kit::who),
			Codec.LONG.fieldOf("reviewed").forGetter(Kit::reviewed),
			ItemStack.OPTIONAL_CODEC.listOf().fieldOf("pieces").forGetter(Kit::pieces)).apply(i, Kit::new));

		ItemStack piece(int i) {
			return i < this.pieces.size() ? this.pieces.get(i) : ItemStack.EMPTY;
		}

		Kit with(int i, ItemStack stack) {
			List<ItemStack> now = new ArrayList<>(this.pieces);
			while (now.size() < SIZE) {
				now.add(ItemStack.EMPTY);
			}
			now.set(i, stack);
			return new Kit(this.realm, this.who, this.reviewed, List.copyOf(now));
		}
	}

	/** A guard's kit, as it was paid for. Saved. */
	public static final AttachmentType<Kit> KIT = AttachmentRegistry.create(JaymosKingdoms.id("guard_kit"), builder -> builder.persistent(Kit.CODEC));

	/** What a piece takes: so many of a material from the stores, or emeralds (both 0 and no material: nothing). */
	private record Cost(@Nullable Predicate<ItemStack> material, int count, int emeralds) {
	}

	private record Option(ItemStack stack, Cost cost) {
	}

	/** One guard's settlement, as his kit is chosen for it. */
	private record Ctx(ServerLevel level, Faction f, int who, Climate land, int kind, boolean templar, boolean grim, GreatWars.@Nullable Creed creed,
		boolean ironRich, int field, long hash, @Nullable String tag, String finish) {
		boolean is(int mask) {
			return (this.who & mask) != 0;
		}

		boolean kind(int mask) {
			return (this.kind & mask) != 0;
		}

		boolean in(Set<Climate> lands) {
			return lands.contains(this.land);
		}

		boolean realm() {
			return this.kind(KINGDOM | FIEF);
		}
	}

	private GuardKit() {
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

	// ---- who is kitted -------------------------------------------------------------------------------------------------

	/**
	 * The settlement {@code e} keeps guard for (a guard or archer in its service, not a player's recruit), or null. One
	 * not in a settlement's service is left as they are, so no one pays for a guard just walking through.
	 */
	static @Nullable Faction serves(LivingEntity e) {
		if (!(e instanceof Mob) || e.isBaby() || e.hasAttached(Ranks.RANK) || !(e.level() instanceof ServerLevel level) || role(e) < 0
			|| !(e instanceof Worker w) || w.factionId() < 0) {
			return null;
		}
		Faction f = Factions.get(level.getServer(), w.factionId());
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	/** From {@code McaEquipmentMixin}: {@code villager} is one of a settlement's guards, so their kit is this class's. */
	public static boolean handles(LivingEntity villager) {
		return serves(villager) != null;
	}

	/** {@link Folk#GUARD} or {@link Folk#ARCHER} (MCA's own guards by their trade), or -1. */
	private static int role(LivingEntity e) {
		int role = e instanceof Worker w ? w.role() : -1;
		if (role == Folk.GUARD || role == Folk.ARCHER) {
			return role;
		}
		if (e instanceof PathfinderMob p && McaCompat.isGuard(p)) {
			return e instanceof Villager v && BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath().equals("archer")
				? Folk.ARCHER : Folk.GUARD;
		}
		return -1;
	}

	/** Who {@code m} is in {@code f}'s service: its rank (a village's or haven's guards are militia or soldiers), an archer, a gunner. */
	static int who(LivingEntity m, Faction f) {
		int role = role(m);
		if (role == Folk.ARCHER) {
			return ARCH;
		}
		if (role != Folk.GUARD) {
			return 0;
		}
		MinecraftServer server = m.level().getServer();
		if (Musketoon.holds(m) || EarlyGuns.gunner(m) || m instanceof Folk folk && Muskets.armed(folk)
			|| server != null && m instanceof PathfinderMob p && EarlyGuns.issue(server, f, p) != EarlyGuns.Issue.NONE) {
			return GUN;
		}
		if (f.type().isRealm()) {
			int code = Ranks.of(m);
			return 1 << (code < 0 ? Ranks.MILITIA : Ranks.step(code));
		}
		return Math.floorMod(m.getUUID().hashCode(), 1000) / 1000.0F < Ranks.SHARES[0] ? MIL : SOL;
	}

	private static @Nullable Ctx ctx(ServerLevel level, Mob m, Faction f) {
		int who = who(m, f);
		if (who == 0) {
			return null;
		}
		MinecraftServer server = level.getServer();
		boolean realm = f.type().isRealm();
		int kind = f.type() == FactionType.KINGDOM ? KINGDOM : f.type() == FactionType.FIEFDOM ? FIEF : Havens.is(server, f) ? HAVEN : VILLAGE;
		Holds.Hold hold = Holds.get(server, f.id());
		Climate land = Climate.at(level, f.seat());
		long h = f.id() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
		h = (h ^ h >>> 31) * 0xBF58476D1CE4E5B9L;
		return new Ctx(level, f, who, land, kind, realm && FrontierWear.templar(server, f), hold != null && hold.isGrim(),
			realm ? GreatWars.creed(server, f) : null, IronVeins.near(level, f), Outfits.rgb(Outfits.heraldry(f, server).field()), h ^ h >>> 29,
			realm ? HeraldryWear.realmTag(server, f) : null, realm ? HeraldryWear.knightFinish(server, f, land) : "");
	}

	// ---- putting it on -------------------------------------------------------------------------------------------------

	/** From {@link Folk#setup} and its restyle, after everything else has dressed it: a guard's or archer's kit, as its settlement can pay for it. */
	static void folk(Folk folk, Faction faction, Climate climate) {
		if (!(folk.level() instanceof ServerLevel level) || serves(folk) == null) {
			return;
		}
		Ctx c = ctx(level, folk, faction);
		if (c == null) {
			return;
		}
		ItemStack[] given = given(folk);
		wear(folk, kit(c, folk, given), given);
	}

	/**
	 * From {@code McaEquipmentMixin}: {@code stack} is what {@code villager} (a settlement's guard dressing for duty) would
	 * put in {@code slot}. Their kit's piece goes on instead (kitted out the first time), and with the chest piece the
	 * harness. An order's plate is the order's ({@link FantasyArmorCompat#issue}).
	 */
	public static ItemStack issue(LivingEntity villager, EquipmentSlot slot, ItemStack stack) {
		if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR || !(villager instanceof Mob m) || !(villager.level() instanceof ServerLevel level)) {
			return stack;
		}
		ItemStack order = FantasyArmorCompat.issue(villager, slot);
		if (order != null) {
			return order;
		}
		Faction f = serves(villager);
		Kit kit = villager.getAttached(KIT);
		if (f == null) {
			return stack;
		}
		if (kit == null || kit.realm() != f.id()) {
			Ctx c = ctx(level, m, f);
			if (c == null) {
				return stack;
			}
			ItemStack[] given = given(m);
			given[index(slot)] = stack;
			kit = kit(c, m, given);
		}
		if (slot == EquipmentSlot.CHEST) {
			harness(m, kit);
		}
		return kit.piece(index(slot)).copy();
	}

	/**
	 * From {@code McaEquipmentMixin}: {@code stack} is the blade MCA would put in a kitted guard's hand. Their kit's goes
	 * there instead (bought the first time it's wanted). A bow, a gun, anything not a blade, is left as it is.
	 */
	public static ItemStack weapon(LivingEntity villager, EquipmentSlot slot, ItemStack stack) {
		if (slot != EquipmentSlot.MAINHAND || !(villager instanceof Mob m) || !(villager.level() instanceof ServerLevel level) || !blade(stack)) {
			return stack;
		}
		Faction f = serves(villager);
		Kit kit = villager.getAttached(KIT);
		if (f == null || kit == null || kit.realm() != f.id()) {
			return stack;
		}
		ItemStack paid = kit.piece(MAINHAND);
		if (paid.isEmpty()) {
			Ctx c = ctx(level, m, f);
			List<Option> options = c == null ? null : weapons(c, stack);
			if (options == null) {
				return stack;
			}
			paid = choose(c, options, ItemStack.EMPTY, ItemStack.EMPTY, null, stone(stack));
			villager.setAttached(KIT, kit.with(MAINHAND, paid));
		}
		return paid.copy();
	}

	/** From {@link Ranks}' sweep: the shield {@code m} takes up, {@code offered} unless they're kitted here (then theirs if it was paid for, else none). */
	static ItemStack shield(LivingEntity m, ItemStack offered) {
		if (serves(m) == null) {
			return offered;
		}
		Kit kit = m.getAttached(KIT);
		return kit == null ? ItemStack.EMPTY : kit.piece(OFFHAND).copy();
	}

	/** What {@code m} has on now, by kit place. */
	private static ItemStack[] given(Mob m) {
		ItemStack[] given = new ItemStack[SIZE];
		for (int i = 0; i < ARMOUR.length; i++) {
			given[i] = m.getItemBySlot(ARMOUR[i]).copy();
		}
		given[MAINHAND] = m.getMainHandItem().copy();
		given[OFFHAND] = m.getOffhandItem().copy();
		for (Harness.Slot slot : Harness.Slot.values()) {
			given[HARNESS + slot.ordinal()] = Harness.get(m, slot).copy();
		}
		return given;
	}

	private static int index(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> 0;
			case CHEST -> 1;
			case LEGS -> 2;
			case FEET -> 3;
			case MAINHAND -> MAINHAND;
			default -> OFFHAND;
		};
	}

	/** Puts {@code kit} on {@code m}: armour, a blade it manages, a shield where one was carried, and the harness. */
	private static void wear(Mob m, Kit kit, ItemStack[] given) {
		for (int i = 0; i < ARMOUR.length; i++) {
			ItemStack piece = kit.piece(i);
			if (SeaBodies.fits(m, ARMOUR[i], piece)) {
				m.setItemSlot(ARMOUR[i], piece.copy());
			}
		}
		if (!kit.piece(MAINHAND).isEmpty() && blade(m.getMainHandItem())) {
			m.setItemSlot(EquipmentSlot.MAINHAND, kit.piece(MAINHAND).copy());
		}
		if (given[OFFHAND].has(DataComponents.BLOCKS_ATTACKS) && !(m instanceof Villager)) {
			m.setItemSlot(EquipmentSlot.OFFHAND, kit.piece(OFFHAND).copy());
		}
		harness(m, kit);
	}

	private static void harness(Mob m, Kit kit) {
		ItemStack[] pieces = new ItemStack[Harness.SLOTS];
		for (Harness.Slot slot : Harness.Slot.values()) {
			pieces[slot.ordinal()] = kit.piece(HARNESS + slot.ordinal()).copy();
		}
		Harness.clear(m);
		Harness.dress(m, pieces);
	}

	/** The guards round each player: one not looked over for a day is, and better bought if the money is there now. */
	private static void sweep(ServerLevel level) {
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			for (Worker w : Workers.near(level, player.getBoundingBox().inflate(SWEEP_RANGE), x -> x.role() == Folk.GUARD || x.role() == Folk.ARCHER)) {
				PathfinderMob m = w.mob();
				ungild(m);
				Kit kit = m.getAttached(KIT);
				if (kit == null || now - kit.reviewed() < REVIEW || m.getTarget() != null) {
					continue;
				}
				Faction f = serves(m);
				Ctx c = f == null ? null : ctx(level, m, f);
				if (c == null) {
					continue;
				}
				ItemStack[] given = given(m);
				Kit after = kit(c, m, given);
				// (an MCA guard off duty has put his armour by: he puts the new on when he dresses again)
				if (m instanceof Folk || !m.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
					wear(m, after, given);
				}
			}
		}
	}

	/**
	 * (Andy, 2026-11-20: "the gold helm they wear needs to go") A guard who has a gold-finished helm on, or kitted for him
	 * to put on, has it refinished at once ({@link HeraldryWear#ungilded}): the same helm, its gilt gone.
	 */
	private static void ungild(Mob m) {
		ItemStack worn = m.getItemBySlot(EquipmentSlot.HEAD);
		ItemStack plain = HeraldryWear.ungilded(worn);
		if (plain != worn) {
			m.setItemSlot(EquipmentSlot.HEAD, plain);
		}
		Kit kit = m.getAttached(KIT);
		if (kit != null) {
			ItemStack kitted = kit.piece(0);
			ItemStack refinished = HeraldryWear.ungilded(kitted);
			if (refinished != kitted) {
				m.setAttached(KIT, kit.with(0, refinished));
			}
		}
	}

	// ---- paying for it -------------------------------------------------------------------------------------------------

	/**
	 * Kits {@code m} out for {@code c}'s settlement and saves it on them. A guard already kitted there as the same man keeps
	 * what he has and is bought only better; one who changed (a gunner now, or no longer) gives his old kit back to the
	 * stores and is kitted afresh.
	 */
	private static Kit kit(Ctx c, Mob m, ItemStack[] given) {
		Kit had = m.getAttached(KIT);
		boolean keep = had != null && had.realm() == c.f().id() && had.who() == c.who();
		if (had != null && !keep && had.realm() == c.f().id()) {
			for (ItemStack piece : had.pieces()) {
				if (!piece.isEmpty()) {
					Storage.put(c.level(), c.f(), piece.copy());
				}
			}
		}
		List<ItemStack> spoils = spoils(c);
		List<ItemStack> out = new ArrayList<>(Collections.nCopies(SIZE, ItemStack.EMPTY));
		for (int i = 0; i < SIZE; i++) {
			ItemStack held = keep ? had.piece(i) : ItemStack.EMPTY;
			if (i < ARMOUR.length) {
				ItemStack piece = choose(c, armour(c, i, given[i]), held, given[i], spoils, leather(c, i, given[i]));
				// (no gold helm on a guard, kept or taken from the spoils: HeraldryWear.helmFinish)
				out.set(i, i == 0 ? HeraldryWear.ungilded(piece) : piece);
			} else if (i == MAINHAND) {
				List<Option> options = weapons(c, given[i]);
				out.set(i, options == null ? ItemStack.EMPTY : choose(c, options, held, given[i], null, stone(given[i])));
			} else if (i == OFFHAND) {
				out.set(i, choose(c, shields(c, given[i]), held, given[i], null, null));
			} else {
				Harness.Slot slot = Harness.Slot.values()[i - HARNESS];
				out.set(i, choose(c, harness(c, slot), held, given[i], null, cloth(c, slot)));
			}
		}
		Kit kit = new Kit(c.f().id(), c.who(), c.level().getGameTime(), List.copyOf(out));
		m.setAttached(KIT, kit);
		return kit;
	}

	/**
	 * The piece bought for one place: the best of {@code options} the settlement can pay for (or a piece of {@code spoils}
	 * as good), above what is {@code held} there already (which goes back into the stores when it's bettered, and is kept
	 * when nothing better can be had); else {@code floor}, for nothing.
	 */
	private static ItemStack choose(Ctx c, List<Option> options, ItemStack held, ItemStack given, @Nullable List<ItemStack> spoils, @Nullable ItemStack floor) {
		EquipmentSlot slot = slotOf(given, options, floor);
		int top = held.isEmpty() ? options.size() : place(options, held, slot);
		ItemStack spoil = spoils == null || slot == null ? ItemStack.EMPTY : best(spoils, slot);
		for (int j = 0; j < top; j++) {
			Option o = options.get(j);
			if (!spoil.isEmpty() && worth(spoil, slot) >= worth(o.stack(), slot)) {
				ItemStack taken = takeSpoil(c, spoils, spoil);
				if (!taken.isEmpty()) {
					return back(c, held, taken);
				}
			}
			ItemStack paid = pay(c, o);
			if (paid != null) {
				return back(c, held, paid);
			}
		}
		if (!held.isEmpty()) {
			return held;
		}
		if (!spoil.isEmpty()) {
			ItemStack taken = takeSpoil(c, spoils, spoil);
			if (!taken.isEmpty()) {
				return taken;
			}
		}
		return floor == null ? ItemStack.EMPTY : floor;
	}

	/** The armour slot this place is, from what's in it or offered for it (null for the hands and the harness). */
	private static @Nullable EquipmentSlot slotOf(ItemStack given, List<Option> options, @Nullable ItemStack floor) {
		ItemStack probe = floor != null ? floor : options.isEmpty() ? given : options.get(0).stack();
		Equippable e = probe.get(DataComponents.EQUIPPABLE);
		return e != null && e.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR && Harness.slotOf(probe) == null ? e.slot() : null;
	}

	/** Where {@code held} stands among {@code options}: at its own kind, else where the options stop being better than it. */
	private static int place(List<Option> options, ItemStack held, @Nullable EquipmentSlot slot) {
		for (int j = 0; j < options.size(); j++) {
			if (options.get(j).stack().is(held.getItem())) {
				return j;
			}
		}
		for (int j = 0; j < options.size(); j++) {
			if (worth(options.get(j).stack(), slot) <= worth(held, slot)) {
				return j;
			}
		}
		return options.size();
	}

	/** {@code now} takes the place of {@code held}, which goes back into the stores. */
	private static ItemStack back(Ctx c, ItemStack held, ItemStack now) {
		if (!held.isEmpty()) {
			Storage.put(c.level(), c.f(), held.copy());
		}
		return now;
	}

	/** Pays for {@code o}: the piece from the stores, or what makes it there, or emeralds. The stack to wear, or null. */
	private static @Nullable ItemStack pay(Ctx c, Option o) {
		Cost cost = o.cost();
		if (cost.material() == null && cost.count() == 0 && cost.emeralds() == 0) {
			return o.stack().copy();
		}
		ItemStack own = Storage.take(c.level(), c.f(), s -> s.is(o.stack().getItem()), 1);
		if (!own.isEmpty()) {
			return own;
		}
		if (cost.material() != null && cost.count() > 0 && Storage.count(c.level(), c.f(), cost.material()) >= cost.count()) {
			int left = cost.count();
			while (left > 0) {
				ItemStack taken = Storage.take(c.level(), c.f(), cost.material(), left);
				if (taken.isEmpty()) {
					break;
				}
				left -= taken.getCount();
			}
			return o.stack().copy();
		}
		if (cost.emeralds() > 0) {
			MinecraftServer server = c.level().getServer();
			Faction now = Factions.get(server, c.f().id());
			if (now != null && now.treasury() >= TREASURY_KEPT + cost.emeralds()) {
				Factions.update(server, now.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - cost.emeralds())));
				return o.stack().copy();
			}
		}
		return null;
	}

	/** The pieces of the spoils in the stores that someone may wear (never the crown's tiers, {@link Regalia#reserved}). */
	private static List<ItemStack> spoils(Ctx c) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack stack : Storage.contents(c.level(), c.f())) {
			Equippable e = stack.get(DataComponents.EQUIPPABLE);
			if (Salvage.is(stack) && e != null && e.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !Regalia.reserved(stack)) {
				out.add(stack);
			}
		}
		return out;
	}

	private static ItemStack best(List<ItemStack> spoils, EquipmentSlot slot) {
		ItemStack best = ItemStack.EMPTY;
		for (ItemStack stack : spoils) {
			Equippable e = stack.get(DataComponents.EQUIPPABLE);
			if (e != null && e.slot() == slot && (best.isEmpty() || worth(stack, slot) > worth(best, slot))) {
				best = stack;
			}
		}
		return best;
	}

	private static ItemStack takeSpoil(Ctx c, @Nullable List<ItemStack> spoils, ItemStack spoil) {
		ItemStack taken = Storage.take(c.level(), c.f(), s -> ItemStack.isSameItemSameComponents(s, spoil), 1);
		if (spoils != null) {
			spoils.remove(spoil);
		}
		return taken;
	}

	/** Armour and toughness {@code stack} gives in {@code slot}; a harness piece's own; a weapon's damage. */
	private static double worth(ItemStack stack, @Nullable EquipmentSlot slot) {
		HeraldryArmour.Piece piece = HeraldryArmour.of(stack);
		if (piece != null && piece.slot() != null) {
			return piece.armour() + piece.toughness();
		}
		ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		if (slot == null) {
			return mods.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
		}
		return mods.compute(Attributes.ARMOR, 0.0, slot) + mods.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot);
	}

	// ---- what each place may have, best first (Andy's list) ------------------------------------------------------------

	private static void add(List<Option> out, ItemStack stack, @Nullable Predicate<ItemStack> material, int count, int emeralds) {
		if (!stack.isEmpty()) {
			out.add(new Option(stack, new Cost(material, count, emeralds)));
		}
	}

	private static ItemStack dyed(ItemStack stack, int rgb) {
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	/** {@code item}, or what's {@code given} if it's the same (so the trim and colour it was put on with stay). */
	private static ItemStack plain(ItemStack given, Item item) {
		return given.is(item) ? given.copy() : new ItemStack(item);
	}

	/** Armour slot {@code i} (0 the helm to 3 the boots). */
	private static List<Option> armour(Ctx c, int i, ItemStack given) {
		List<Option> out = new ArrayList<>();
		ArmorType type = TYPES[i];
		// whole sets: the Templars', the gunners' harness, the land's champions', the villages' settler gear
		if (c.is(SOL | KN | VET | LEAD | ARCH) && c.templar()) {
			if (c.grim()) {
				add(out, FrontierArmour.stack("templar_steel", type), IRON, INGOTS[i], FrontierWear.TEMPLAR_PRICE[i] + STEEL_EXTRA);
			}
			add(out, FrontierArmour.stack("templar", type), IRON, INGOTS[i], FrontierWear.TEMPLAR_PRICE[i]);
		}
		if (c.is(GUN)) {
			add(out, FrontierArmour.stack("ranger_steel", type), IRON, INGOTS[i], RANGER_PRICE[i] + STEEL_EXTRA);
			add(out, FrontierArmour.stack("ranger", type), IRON, INGOTS[i], RANGER_PRICE[i]);
		}
		if (c.is(VET | LEAD)) {
			String set = (c.in(COLD) || c.land() == Climate.PLAINS) ? (c.creed() == GreatWars.Creed.FAITH || c.creed() == GreatWars.Creed.CHIVALRY ? "paladin" : null)
				: c.in(DRY) ? "gladiator" : c.land() == Climate.CHERRY || c.land() == Climate.JUNGLE ? "ronin" : null;
			Item piece = set == null ? null : Armory.piece(set, type);
			if (piece != null) {
				add(out, dyed(new ItemStack(piece), c.field()), IRON, INGOTS[i], ARMORY_PRICE[i]);
			}
		}
		if (c.is(SOL | KN) && c.land() == Climate.CHERRY) {
			FantasyArmorCompat.piece(FantasyArmorCompat.FOLDED, i).ifPresent(p -> add(out, new ItemStack(p), IRON, INGOTS[i], FOLDED_PRICE[i]));
		}
		if (c.is(MIL | SOL | ARCH) && c.kind(VILLAGE | HAVEN)) {
			add(out, FrontierArmour.stack("settler", type), LEATHER, INGOTS[i], FrontierWear.SETTLER_PRICE[i]);
		}
		if (c.is(ARCH) && i <= 1) {
			RpgKit.item(i == 0 ? "archers:archer_armor_head" : "archers:archer_armor_chest").ifPresent(p -> add(out, new ItemStack(p), LEATHER, INGOTS[i], ARCHERS_PRICE[i]));
		}
		// the harness's own helms and plate, and the land's chest pieces
		switch (i) {
			case 0 -> {
				String helm = helm(c);
				if (helm != null) {
					HeraldryArmour.Piece piece = HeraldryArmour.PIECES.get(helm);
					ItemStack stack = HeraldryArmour.stack(helm, HeraldryWear.helmFinish(c.finish()));
					if (c.tag() != null && helm.equals(HeraldryWear.realmHelm(c.f()))) {
						stack = PowersCompat.realmMarked(stack, c.tag());
					}
					int price = piece == null || piece.face() == HeraldryArmour.Face.OPEN ? OPEN_HELM : piece.face() == HeraldryArmour.Face.FIXED ? FIXED_HELM : VISORED_HELM;
					add(out, stack, IRON, INGOTS[0], price);
				}
			}
			case 1 -> {
				if (c.is(LEAD) && c.kind(KINGDOM) && c.creed() == GreatWars.Creed.CROWN) {
					add(out, Capes.cape(HeraldryArmour.stack("gilt_banded_cuirass", c.finish()), Outfits.heraldry(c.f(), c.level().getServer())), IRON, CUIRASS, GILT_CUIRASS);
				}
				if (c.is(VET | LEAD) && (c.templar() || c.creed() == GreatWars.Creed.FAITH)) {
					add(out, HeraldryArmour.stack("faith_cuirass", c.finish()), IRON, CUIRASS, CREED_CUIRASS);
				}
				// (Andy: "knights the crown has honoured, not Coin": a kingdom's)
				if (c.is(VET | LEAD) && c.kind(KINGDOM) && c.creed() != GreatWars.Creed.COIN) {
					add(out, HeraldryArmour.stack("etched_cuirass", c.finish()), IRON, CUIRASS, CREED_CUIRASS);
				}
				if (c.is(KN | VET | LEAD) && c.in(COLD)) {
					add(out, HeraldryArmour.stack("fur_lined_cuirass", c.finish()), IRON, CUIRASS, CREED_CUIRASS);
				}
				if (c.is(VET | LEAD) && c.realm() && c.ironRich()) {
					add(out, HeraldryArmour.stack("plate_cuirass", c.finish()), IRON, CUIRASS, CUIRASS);
				}
				// a poorer realm sews its plates inside a coat in its colour
				if (c.is(SOL | KN | VET | LEAD) && c.realm() && !c.ironRich()) {
					add(out, dyed(HeraldryArmour.stack("coat_of_plates"), c.field()), IRON, COAT_OF_PLATES, COAT_OF_PLATES);
				}
				if (c.is(SOL | KN | VET) && c.land() == Climate.CHERRY) {
					add(out, HeraldryArmour.stack("laced_lamellar", c.finish()), IRON, INGOTS[1], LAND_CHEST + 2);
				}
				if (c.is(SOL | KN) && (c.land() == Climate.SAVANNA || c.land() == Climate.DESERT)) {
					add(out, HeraldryArmour.stack("bronze_armor"), COPPER, INGOTS[1], LAND_CHEST);
				}
				if (c.is(KN | VET | LEAD)) {
					add(out, plain(given, Items.IRON_CHESTPLATE), IRON, INGOTS[1], IRON_PRICE[1]);
				}
				if (c.is(MIL | SOL | ARCH) && (c.land() == Climate.JUNGLE || c.land() == Climate.DESERT || c.land() == Climate.SAVANNA)) {
					// (Andy: "lore: brine-soaked quilted cotton in the jungle")
					add(out, HeraldryArmour.stack("quilted_cotton_armor", c.land() == Climate.JUNGLE ? HeraldryArmour.BRINE_SOAKED : ""), WOOL, INGOTS[1], CLOTH_CHEST);
				}
				if (c.is(MIL | SOL | ARCH) && (c.land() == Climate.SAVANNA || c.land() == Climate.SWAMP)) {
					add(out, HeraldryArmour.stack("hide_armor"), LEATHER, INGOTS[1], CLOTH_CHEST);
				}
			}
			case 3 -> {
				if (c.is(VET | LEAD)) {
					add(out, HeraldryArmour.stack("plate_sabatons", c.finish()), IRON, SABATONS, SABATONS + 1);
				}
			}
			default -> {
			}
		}
		Item[] iron = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
		Item[] copper = {Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS};
		Item[] mail = {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
		if (i != 1 && c.is(KN | VET | LEAD)) {
			add(out, plain(given, iron[i]), IRON, INGOTS[i], IRON_PRICE[i]);
		}
		// bronze where the realm has no iron, in the dry lands
		if (c.is(MIL | SOL | ARCH) && c.in(DRY) && !c.ironRich()) {
			add(out, plain(given, copper[i]), COPPER, INGOTS[i], BRONZE_PRICE[i]);
		}
		// and mail, the floor's paid half, for everyone
		add(out, plain(given, mail[i]), IRON, MAIL_INGOTS[i], MAIL_PRICE[i]);
		return out;
	}

	/**
	 * The harness helm {@code c}'s guard may wear: one shape for each rank of a realm. Where his creed has a helm (a Coin
	 * realm's barbute, Old Blood's spangenhelm in the cold, a Chivalry leader's great helm, a Conquest leader's grotesque
	 * visor) it's that; else his land's (the veiled helm in the desert, the morion in dry realms that keep guns); else one
	 * of the rest, the realm's own shape for its veterans and leaders. The frog-mouth helm is for the joust, never issued.
	 */
	private static @Nullable String helm(Ctx c) {
		GreatWars.Creed creed = c.creed();
		List<String> by = new ArrayList<>();
		if (c.is(SOL | KN) && creed == GreatWars.Creed.COIN) {
			by.add("barbute");
		}
		if (c.is(SOL | KN) && c.in(COLD) && creed == GreatWars.Creed.BLOOD) {
			by.add("spangenhelm");
		}
		if (c.is(LEAD) && creed == GreatWars.Creed.CHIVALRY) {
			by.add("great_helm");
		}
		if (c.is(LEAD) && creed == GreatWars.Creed.CONQUEST) {
			by.add("grotesque_visor");
		}
		if (by.isEmpty()) {
			if (c.is(KN | VET | LEAD) && c.land() == Climate.DESERT) {
				by.add("veiled_helm");
			}
			if (c.is(SOL | ARCH | GUN) && c.in(DRY) && c.realm()) {
				by.add("morion");
			}
		}
		if (by.isEmpty()) {
			if (c.is(MIL | SOL | ARCH | GUN) && c.kind(VILLAGE | HAVEN | FIEF)) {
				by.add("kettle_hat");
			}
			if (c.is(KN | VET)) {
				by.add("sallet");
				by.add("burgonet");
			}
			if (c.is(SOL | KN | GUN) && c.realm()) {
				by.add("lobster_tail");
			}
			if (c.is(VET | LEAD)) {
				// (Andy: "each realm keeps one helm shape")
				List<String> shapes = List.of("houndskull_bascinet", "close_helm", "armet");
				String own = HeraldryWear.realmHelm(c.f());
				by.add(shapes.contains(own) ? own : shapes.get((int)Math.floorMod(c.hash() >>> 8, (long)shapes.size())));
			}
			if (c.is(LEAD)) {
				by.add("savoyard_helm");
			}
		}
		return by.isEmpty() ? null : by.get((int)Math.floorMod(c.hash() + Integer.numberOfTrailingZeros(c.who()) * 0x5851F42DL, (long)by.size()));
	}

	/** The free floor for armour slot {@code i}: the levy's leather (what was {@code given}, if it was leather, in its land's colour). */
	private static ItemStack leather(Ctx c, int i, ItemStack given) {
		Item[] leather = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
		if (given.is(leather[i])) {
			return given.copy();
		}
		return Outfits.dyed(leather[i], i == 1 ? c.field() : Ranks.LEVY_BROWN);
	}

	/** A harness place's pieces, best first. */
	private static List<Option> harness(Ctx c, Harness.Slot slot) {
		List<Option> out = new ArrayList<>();
		String fin = c.finish();
		switch (slot) {
			case PADDING -> {
				if (c.is(KN | VET | LEAD)) {
					add(out, dyed(HeraldryArmour.stack("arming_doublet"), c.field()), WOOL, CLOTH_WOOL, HeraldryWear.DOUBLET_PRICE);
				}
			}
			case MAIL -> {
				if (c.is(SOL | KN | VET | LEAD)) {
					add(out, HeraldryArmour.stack("mail_haubergeon"), IRON, PLATE_INGOTS, PLATE_PART);
				}
				if (c.is(MIL | SOL | ARCH | GUN)) {
					add(out, HeraldryArmour.stack("quilted_coat"), WOOL, CLOTH_WOOL + 1, CLOTH_PART);
				}
			}
			case SHOULDERS -> {
				if (c.is(LEAD) && c.creed() == GreatWars.Creed.CONQUEST) {
					add(out, HeraldryArmour.stack("spiked_spaulders", fin), IRON, PLATE_INGOTS, PLATE_PART + 1);
				}
				if (c.is(KN | VET | LEAD) && c.in(COLD)) {
					add(out, HeraldryArmour.stack("fur_lined_spaulders", fin), IRON, PLATE_INGOTS, PLATE_PART);
				}
				if (c.is(VET | LEAD)) {
					add(out, HeraldryArmour.stack("plate_spaulders", fin), IRON, PLATE_INGOTS, PLATE_PART);
				}
				// (Andy: "Coin and poorer realms")
				if (c.is(KN | VET) && (c.creed() == GreatWars.Creed.COIN || !c.ironRich())) {
					add(out, HeraldryArmour.stack("brigandine_spaulders"), IRON, PLATE_INGOTS - 1, MAIL_PART);
				}
				if (c.is(SOL | KN)) {
					add(out, HeraldryArmour.stack("mail_spaulders"), IRON, PLATE_INGOTS - 1, MAIL_PART);
				}
			}
			case ARMS -> {
				if (c.is(VET | LEAD) && c.creed() == GreatWars.Creed.CHIVALRY) {
					add(out, HeraldryArmour.stack("oath_sleeves", fin), IRON, PLATE_INGOTS, PLATE_PART + 1);
				}
				if (c.is(VET | LEAD) && c.in(COLD)) {
					add(out, HeraldryArmour.stack("fur_lined_arm_harness", fin), IRON, PLATE_INGOTS, PLATE_PART);
				}
				if (c.is(VET | LEAD)) {
					add(out, HeraldryArmour.stack("plate_arm_harness", fin), IRON, PLATE_INGOTS, PLATE_PART);
				}
			}
			case HANDS -> {
				if (c.is(KN | VET | LEAD) && c.in(COLD)) {
					add(out, HeraldryArmour.stack("fur_lined_gauntlets", fin), IRON, PLATE_INGOTS - 1, PLATE_PART - 1);
				}
				if (c.is(KN | VET | LEAD)) {
					add(out, HeraldryArmour.stack("plate_gauntlets", fin), IRON, PLATE_INGOTS - 1, PLATE_PART - 1);
				}
			}
			case THIGHS -> {
				if (c.is(VET | LEAD) && c.in(COLD)) {
					add(out, HeraldryArmour.stack("fur_lined_cuisses", fin), IRON, PLATE_INGOTS + 1, PLATE_PART + 1);
				}
				if (c.is(VET | LEAD)) {
					add(out, HeraldryArmour.stack("plate_cuisses", fin), IRON, PLATE_INGOTS + 1, PLATE_PART + 1);
				}
			}
			case SHINS -> {
				if (c.is(KN | VET | LEAD) && c.in(COLD)) {
					add(out, HeraldryArmour.stack("fur_lined_greaves", fin), IRON, PLATE_INGOTS, PLATE_PART);
				}
				if (c.is(KN | VET | LEAD)) {
					add(out, HeraldryArmour.stack("plate_greaves", fin), IRON, PLATE_INGOTS, PLATE_PART);
				}
			}
			case OVER -> {
				if (c.is(LEAD) && c.kind(KINGDOM | FIEF)) {
					add(out, dyed(HeraldryArmour.stack("giornea"), c.field()), WOOL, CLOTH_WOOL + 1, HeraldryWear.GIORNEA_PRICE);
				}
				if (c.is(LEAD | ARCH)) {
					add(out, dyed(HeraldryArmour.stack("cloak"), c.field()), WOOL, CLOTH_WOOL + 1, HeraldryWear.CLOAK_PRICE);
				}
				if (c.is(MIL | SOL | KN | VET | LEAD | ARCH)) {
					add(out, HeraldryWear.withArms(dyed(HeraldryArmour.stack("tabard"), c.field()), c.level().getServer(), c.f()), WOOL, CLOTH_WOOL, CLOTH_PART);
				}
			}
			case HOOD -> {
				if (c.is(MIL | ARCH) && (c.in(COLD) || c.land() == Climate.PLAINS)) {
					add(out, dyed(HeraldryArmour.stack("hood"), c.field()), WOOL, CLOTH_WOOL - 1, HeraldryWear.HOOD_PRICE);
				}
			}
			case CREST -> {
				if (c.is(LEAD) && c.kind(KINGDOM)) {
					add(out, HeraldryArmour.stack("crest_wings"), null, 0, CREST);
				}
			}
		}
		return out;
	}

	/** The harness's free floor: a quilted coat for everyone, and (but for a gunner's lamp) the tabard in the realm's colours. */
	private static @Nullable ItemStack cloth(Ctx c, Harness.Slot slot) {
		return switch (slot) {
			case MAIL -> HeraldryArmour.stack("quilted_coat");
			case OVER -> c.is(GUN) ? null : HeraldryWear.withArms(dyed(HeraldryArmour.stack("tabard"), c.field()), c.level().getServer(), c.f());
			default -> null;
		};
	}

	/** Whether {@code stack} is a blade or a polearm (not a bow, a gun, a banner). */
	private static boolean blade(ItemStack stack) {
		return stack.has(DataComponents.WEAPON) && !(stack.getItem() instanceof ProjectileWeaponItem) && EarlyGuns.Rung.of(stack) == null
			&& !stack.is(Muskets.musket());
	}

	/** 0 a sword, 1 a spear, 2 an axe. */
	private static int kind(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("spear") ? 1 : path.endsWith("_axe") && !path.contains("double") ? 2 : 0;
	}

	/** The blades for {@code given}'s hand, best first, or null to leave the hand alone (a bow, a gun, a gunner's). */
	private static @Nullable List<Option> weapons(Ctx c, ItemStack given) {
		if (!blade(given) || c.is(GUN)) {
			return null;
		}
		List<Option> out = new ArrayList<>();
		if (c.is(VET | LEAD) && c.realm() && c.ironRich()) {
			add(out, HeraldryWear.warArm(c.level().getServer(), c.f()), IRON, WEAPON_INGOTS[2], WAR_ARM);
		}
		if (!BuiltInRegistries.ITEM.getKey(given.getItem()).getNamespace().equals("minecraft")) {
			// (a polearm, a war arm: what it was given, if it can be paid for)
			add(out, given.copy(), IRON, WEAPON_INGOTS[2], WAR_ARM);
		}
		int k = kind(given);
		Item[] iron = {Items.IRON_SWORD, Items.IRON_SPEAR, Items.IRON_AXE}, copper = {Items.COPPER_SWORD, Items.COPPER_SPEAR, Items.COPPER_AXE};
		add(out, plain(given, iron[k]), IRON, WEAPON_INGOTS[k], IRON_WEAPON[k]);
		add(out, plain(given, copper[k]), COPPER, WEAPON_INGOTS[k], COPPER_WEAPON[k]);
		return out;
	}

	/** A blade's free floor: stone. */
	private static ItemStack stone(ItemStack given) {
		Item[] stone = {Items.STONE_SWORD, Items.STONE_SPEAR, Items.STONE_AXE};
		return new ItemStack(stone[kind(given)]);
	}

	/** A shield for the off hand: the one it was given, or the realm's for its militia and soldiers. */
	private static List<Option> shields(Ctx c, ItemStack given) {
		List<Option> out = new ArrayList<>();
		if (given.has(DataComponents.BLOCKS_ATTACKS)) {
			add(out, given.copy(), IRON, 1, SHIELD);
		} else if (c.is(MIL | SOL)) {
			add(out, Ranks.shield(c.level(), Outfits.heraldry(c.f(), c.level().getServer())), IRON, 1, SHIELD);
		}
		return out;
	}

	// ---- camp raiders --------------------------------------------------------------------------------------------------

	/**
	 * From {@link Folk#setup} and its restyle, after the Primalist wrappings: a human camp's raider in Andy's raider pieces,
	 * from the camp's own loot: a torn hood (and some a torn cloak), the horned crest on some helms, hide in the savanna
	 * and the swamp where he'd otherwise go in leather.
	 */
	static void raider(Folk folk, Faction faction, Climate climate) {
		MinecraftServer server = folk.level().getServer();
		if (folk.role() != Folk.BARBARIAN || server == null || faction.type() != FactionType.BARBARIAN || Camps.orc(server, faction.id())
			|| Camps.goblin(server, faction.id()) || Camps.illager(server, faction.id()) || Camps.pirate(server, faction.id())) {
			return;
		}
		int h = folk.getUUID().hashCode();
		float hood = Math.floorMod(h, 1000) / 1000.0F, horns = Math.floorMod(h >>> 10, 1000) / 1000.0F;
		List<ItemStack> on = new ArrayList<>();
		if (hood < RAIDER_HOOD) {
			on.add(HeraldryArmour.stack("torn_hood"));
		}
		if (hood < RAIDER_CLOAK) {
			on.add(HeraldryArmour.stack("torn_cloak"));
		}
		if (horns < RAIDER_HORNS && !folk.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			on.add(HeraldryArmour.stack("crest_horns"));
		}
		if (!on.isEmpty()) {
			Harness.dress(folk, on.toArray(ItemStack[]::new));
		}
		ItemStack chest = folk.getItemBySlot(EquipmentSlot.CHEST);
		if ((climate == Climate.SAVANNA || climate == Climate.SWAMP) && (chest.isEmpty() || chest.is(Items.LEATHER_CHESTPLATE))) {
			folk.setItemSlot(EquipmentSlot.CHEST, HeraldryArmour.stack("hide_armor"));
		}
	}
}
