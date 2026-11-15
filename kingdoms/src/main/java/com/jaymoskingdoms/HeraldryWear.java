package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * Who wears and sells the {@link HeraldryArmour} harness.
 * <ul>
 * <li><b>Realm knights</b>: the veteran guards of a kingdom or fiefdom with iron to spare, in temperate country, ride
 * out in the full harness: their realm's helm (one of the eight, the same for all its knights), cuirass and sabatons,
 * and under and round them the arming doublet in the realm's colour, the haubergeon, spaulders of the realm's make
 * (plate, mail or brigandine), arm harness, gauntlets, cuisses and greaves. Bright in most realms, blackened in some.
 * A realm whose faith or armoury already dresses its guards keeps that. (medieval picks) Their helm and the war arm they
 * carry are the realm's own make (Powers' {@code RealmPatterns}), and a knight slain by a player sometimes leaves a
 * captured pattern of one ({@link #KNIGHT_PATTERN_CHANCE}).</li>
 * <li><b>Kings and lords</b> wear their realm's crest beast over the crown, its torse in the realm's colour, and gilded
 * spaulders. The beast comes from the realm as its arms do. A player who takes a crown is given theirs.</li>
 * <li><b>Gilded plate</b> is for royals and lords: anyone else who puts a gilded piece on has it come off again.</li>
 * <li><b>Tailors</b> (shepherds) sell the arming doublet, hood and cloak, and in a realm the giornea and the torse;
 * a kingdom's armourer sells the crests anyone may wear, the horns and the wings.</li>
 * <li><b>Torn cloth</b>: a bandit leader and his men go in torn hoods and cloaks, and the Banished start with both.</li>
 * </ul>
 */
public final class HeraldryWear {
	/** How often the villagers near players are looked over (ticks), and how far round each player; how often players' gilded plate is checked. */
	static final int SWEEP = 100, GILDED_CHECK = 40;
	static final double SWEEP_RANGE = 48.0;
	/** One realm in this many blackens its knights' plate. */
	static final int BLACKENED_REALMS = 3;
	/** The share of a bandit leader's men with a torn cloak as well as the hood. */
	static final float BANDIT_CLOAK_SHARE = 0.5F;
	/** Prices (emeralds): the tailor's cloth, and the armourer's crests. */
	static final int DOUBLET_PRICE = 4, HOOD_PRICE = 3, CLOAK_PRICE = 6, GIORNEA_PRICE = 9, TORSE_PRICE = 5, CREST_PRICE = 14;
	static final int USES = 3, XP = 5;
	static final float PRICE_MULT = 0.05F;
	/** The lands whose realms field knights in the harness (the others keep their own land's gear). */
	static final Set<Climate> KNIGHT_LANDS = EnumSet.of(Climate.PLAINS, Climate.TAIGA, Climate.SWAMP);
	/** (medieval picks) The chance a realm knight a player slays leaves a captured pattern of his helm or war arm. */
	static final float KNIGHT_PATTERN_CHANCE = 0.2F;

	private HeraldryWear() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob && source.getEntity() instanceof ServerPlayer && mob.level() instanceof ServerLevel level
				&& level.getRandom().nextFloat() < KNIGHT_PATTERN_CHANCE) {
				fallenKnight(level, mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % GILDED_CHECK == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					ungild(player);
				}
			}
			if (server.getTickCount() % SWEEP == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					sweep(level);
				}
			}
		});
	}

	// ---- what a realm's own are ----------------------------------------------------------------------------------------

	/** The realm whose harness {@code f}'s people wear: a fiefdom's is its overlord's, as its arms are. */
	private static int realm(Faction f) {
		return f.type() == FactionType.FIEFDOM && f.overlord() >= 0 ? f.overlord() : f.id();
	}

	private static long hash(int realm) {
		long z = realm * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL;
		z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
		z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
		return z ^ z >>> 31;
	}

	private static String pick(List<String> from, long h) {
		return from.get((int)Math.floorMod(h, (long)from.size()));
	}

	/** The crest beast of {@code f}'s realm (an item name of {@link HeraldryArmour#BEASTS}). */
	public static String beast(Faction f) {
		return pick(HeraldryArmour.BEASTS, hash(realm(f)));
	}

	/** {@code f}'s crest: its beast on a torse of the realm's colour. */
	static ItemStack crest(@Nullable MinecraftServer server, Faction f) {
		return dyed(HeraldryArmour.stack(beast(f)), Outfits.rgb(Outfits.heraldry(f, server).field()));
	}

	private static ItemStack dyed(ItemStack stack, int rgb) {
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	// ---- knights -------------------------------------------------------------------------------------------------------

	/** Whether {@code mob}, a guard of {@code f} in {@code climate}, is one of its knights: a veteran of a realm with iron to spare that has no other issue for him. */
	private static boolean knightly(Mob mob, Faction f, Climate climate) {
		if (!f.type().isRealm() || f.fallen() || !KNIGHT_LANDS.contains(climate)) {
			return false;
		}
		float rank = Math.floorMod(mob.getUUID().hashCode(), 1000) / 1000.0F;
		return rank >= 1.0F - Outfits.GUARD_VETERAN_SHARE && Outfits.ironRich(mob, f) && !Outfits.heraldry(f, mob.level().getServer()).armoury();
	}

	private static String finish(Faction f) {
		return Math.floorMod(hash(realm(f)) >>> 24, (long)BLACKENED_REALMS) == 0 ? HeraldryArmour.BLACKENED : "";
	}

	/** What {@code f}'s knights wear in {@code slot} (the realm's helm, cuirass or sabatons) or carry (its war arm; the cuirass carries the realm's cape), or empty for the other slots. */
	private static ItemStack plate(@Nullable MinecraftServer server, Faction f, EquipmentSlot slot) {
		String finish = finish(f);
		String tag = server == null ? null : realmTag(server, f);
		String arm = arm(realm(f));
		return switch (slot) {
			case HEAD -> {
				ItemStack helm = HeraldryArmour.stack(helm(realm(f)), finish);
				yield tag == null ? helm : PowersCompat.realmMarked(helm, tag);
			}
			case MAINHAND -> tag == null || arm == null ? ItemStack.EMPTY : PowersCompat.realmArm(arm, tag);
			// (gb-capes) a knight's cuirass carries the realm's cape
			case CHEST -> server == null ? HeraldryArmour.stack("plate_cuirass", finish)
				: Capes.cape(HeraldryArmour.stack("plate_cuirass", finish), Outfits.heraldry(f, server));
			case FEET -> HeraldryArmour.stack("plate_sabatons", finish);
			default -> ItemStack.EMPTY;
		};
	}

	/** The harness under and round a knight of {@code f}'s plate. */
	private static void harness(Mob mob, Faction f) {
		String finish = finish(f);
		Harness.dress(mob, dyed(HeraldryArmour.stack("arming_doublet"), Outfits.rgb(Outfits.heraldry(f, mob.level().getServer()).field())),
			HeraldryArmour.stack("mail_haubergeon"), HeraldryArmour.stack(pick(HeraldryArmour.SPAULDERS, hash(realm(f)) >>> 16), finish),
			HeraldryArmour.stack("plate_arm_harness", finish), HeraldryArmour.stack("plate_gauntlets", finish),
			HeraldryArmour.stack("plate_cuisses", finish), HeraldryArmour.stack("plate_greaves", finish));
	}

	/** From {@link Folk#setup} and its restyle, after the realm's own gear and {@link FrontierWear}'s: its knights, and its rulers' crests. */
	static void folk(Folk folk, Faction faction, Climate climate) {
		int role = folk.role();
		if (role == Folk.KING || role == Folk.LORD) {
			ruler(folk, faction);
		} else if (role == Folk.GUARD && FrontierArmour.setOf(folk.getItemBySlot(EquipmentSlot.CHEST)) == null && knightly(folk, faction, climate)) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
				ItemStack piece = plate(folk.level().getServer(), faction, slot);
				if (!piece.isEmpty()) {
					folk.setItemSlot(slot, piece);
				}
			}
			harness(folk, faction);
		} else if (folk.hasAttached(Harness.WORN)) {
			Harness.clear(folk);
		}
	}

	/** {@code mob} rules {@code faction} (a king, a lord; from {@link Rulers#fit} too): the realm's crest, and gilded spaulders. */
	static void ruler(Mob mob, Faction faction) {
		Harness.dress(mob, crest(mob.level().getServer(), faction), HeraldryArmour.stack("plate_spaulders", HeraldryArmour.GILDED));
		// (gb-capes) and the realm's cape on whatever chest piece they wear
		Capes.ensure(mob, faction, true);
	}

	/** Off the throne ({@link Rulers#unfit}): the crest and the gilt go. */
	static void unfit(Mob mob) {
		if (mob.hasAttached(Harness.WORN)) {
			Harness.clear(mob);
		}
	}

	/**
	 * From {@code McaEquipmentMixin}: {@code stack} is what {@code villager} (an armed guard dressing for duty) is about to
	 * put in {@code slot}. A realm's knight puts on the realm's plate in its place, and the harness with the cuirass.
	 */
	public static ItemStack issue(LivingEntity villager, EquipmentSlot slot, ItemStack stack) {
		if (!(villager instanceof Mob mob) || !(villager.level() instanceof ServerLevel level) || FrontierArmour.setOf(stack) != null) {
			return stack;
		}
		MinecraftServer server = level.getServer();
		Faction f = villager instanceof Worker w && w.factionId() >= 0 ? Factions.get(server, w.factionId()) : Factions.at(server, level, villager.blockPosition());
		if (f == null || !knightly(mob, f, Climate.at(level, f.seat()))) {
			return stack;
		}
		ItemStack plate = plate(server, f, slot);
		if (plate.isEmpty()) {
			return stack;
		}
		if (slot == EquipmentSlot.CHEST) {
			harness(mob, f);
			// (and he takes up the realm's war arm)
			ItemStack arm = plate(server, f, EquipmentSlot.MAINHAND);
			if (!arm.isEmpty()) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, arm);
			}
		}
		return plate;
	}

	/** The villagers round each player: a knight off duty (his cuirass put by) puts the rest of the harness by with it. */
	private static void sweep(ServerLevel level) {
		List<Villager> seen = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SWEEP_RANGE), v -> v.isAlive() && v.hasAttached(Harness.WORN))) {
				if (!seen.contains(v)) {
					seen.add(v);
				}
			}
		}
		for (Villager v : seen) {
			if (!Harness.get(v, Harness.Slot.ARMS).isEmpty() && HeraldryArmour.of(v.getItemBySlot(EquipmentSlot.CHEST)) == null) {
				Harness.clear(v);
			}
		}
	}

	// ---- players -------------------------------------------------------------------------------------------------------

	/** Whether {@code player} is a royal or a lord: wears a realm's crown, or holds a lord's rank in one. */
	public static boolean noble(MinecraftServer server, UUID player) {
		Faction f = Factions.of(server, player);
		return f != null && (Crowns.rules(server, f, player) || Wars.isLord(f, player));
	}

	// ---- (medieval picks) a realm's own designs ------------------------------------------------------------------------

	/** The helm {@code realm}'s knights wear (an item name of {@link HeraldryArmour#HELMS}). */
	private static String helm(int realm) {
		return pick(HeraldryArmour.HELMS, hash(realm) >>> 8);
	}

	/** The war arm {@code realm}'s knights carry (a Powers weapon head, of those in the game), or null without Powers. */
	private static @Nullable String arm(int realm) {
		List<String> arms = PowersCompat.realmArms();
		return arms.isEmpty() ? null : pick(arms, hash(realm) >>> 40);
	}

	/** {@code f}'s realm as Powers' {@code RealmPatterns} passes it round ("id|name"; a fiefdom's is its overlord's), or null. */
	static @Nullable String realmTag(MinecraftServer server, Faction f) {
		Faction realm = realm(f) == f.id() ? f : Factions.get(server, realm(f));
		return realm == null ? null : realm.id() + "|" + realm.name();
	}

	/**
	 * The realm {@code smith} is sworn to (Powers' {@code RealmPatterns.sworn}): a standing kingdom's or fiefdom's people to
	 * its realm (a fiefdom's to its overlord's); null for anyone else. Its smiths are offered the realm's own make.
	 */
	public static @Nullable String sworn(MinecraftServer server, UUID smith) {
		Faction mine = Factions.of(server, smith);
		if (mine == null || !mine.type().isRealm() || mine.fallen()) {
			return null;
		}
		Faction realm = realm(mine) == mine.id() ? mine : Factions.get(server, realm(mine));
		return realm == null || realm.fallen() ? null : realmTag(server, realm);
	}

	/** Whether the realm (a tag) keeps {@code shape} (Powers' {@code Blueprints.shape}) as its own: its knights' helm and its war arm. */
	public static boolean keeps(String tag, String shape) {
		int bar = tag.indexOf('|');
		int realm;
		try {
			realm = Integer.parseInt(bar < 0 ? tag : tag.substring(0, bar));
		} catch (NumberFormatException e) {
			return false;
		}
		String arm = arm(realm);
		return shape.equals("heraldry/" + helm(realm)) || arm != null && shape.equals("weapon/" + arm);
	}

	/** A realm knight slain by a player: a captured pattern of his helm or his war arm (if they're the realm's make). */
	private static void fallenKnight(ServerLevel level, Mob mob) {
		boolean helmFirst = level.getRandom().nextBoolean();
		ItemStack pattern = PowersCompat.realmPattern(mob.getItemBySlot(helmFirst ? EquipmentSlot.HEAD : EquipmentSlot.MAINHAND));
		if (pattern.isEmpty()) {
			pattern = PowersCompat.realmPattern(mob.getItemBySlot(helmFirst ? EquipmentSlot.MAINHAND : EquipmentSlot.HEAD));
		}
		if (!pattern.isEmpty()) {
			mob.spawnAtLocation(level, pattern);
		}
	}

	/** Whether {@code player} may wear {@code stack}: anything but gilded plate, which is a royal's or a lord's. (Always true on the client: the server decides.) */
	public static boolean mayWear(Player player, ItemStack stack) {
		MinecraftServer server = player.level().getServer();
		return server == null || !HeraldryArmour.gilded(stack) || player.getAbilities().instabuild || noble(server, player.getUUID());
	}

	/** Gilded plate on one who is no royal or lord comes off into the inventory. */
	private static void ungild(ServerPlayer player) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET}) {
			ItemStack worn = player.getItemBySlot(slot);
			if (!mayWear(player, worn)) {
				player.setItemSlot(slot, ItemStack.EMPTY);
				player.getInventory().placeItemBackInInventory(worn, Prediction.SERVER_ONLY);
				refused(player);
			}
		}
	}

	/** Tells {@code player} why the gilded piece came off. */
	static void refused(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.gilded_refused"));
	}

	/** From {@link Crowns#crown}: {@code player} now wears {@code f}'s crown, and is given its crest (once). */
	static void crowned(MinecraftServer server, Faction f, UUID player) {
		ServerPlayer p = server.getPlayerList().getPlayer(player);
		if (p == null) {
			return;
		}
		ItemStack crest = crest(server, f);
		if (!Harness.get(p, Harness.Slot.CREST).is(crest.getItem()) && !p.getInventory().contains(s -> s.is(crest.getItem()))) {
			Harness.give(p, crest);
		}
	}

	/** The Banished start: the torn cloak and hood they were turned out in. */
	static void banished(ServerPlayer player) {
		Harness.give(player, HeraldryArmour.stack("torn_cloak"));
		Harness.give(player, HeraldryArmour.stack("torn_hood"));
	}

	/** From {@link Masterless}: a bandit leader, or one of his men, in torn cloth. */
	static void bandit(Mob mob, boolean leader) {
		if (leader || mob.getRandom().nextFloat() < BANDIT_CLOAK_SHARE) {
			Harness.dress(mob, HeraldryArmour.stack("torn_hood"), HeraldryArmour.stack("torn_cloak"));
		} else {
			Harness.dress(mob, HeraldryArmour.stack("torn_hood"));
		}
	}

	// ---- shops ---------------------------------------------------------------------------------------------------------

	/** (from {@code VillagerPriceMixin}, when {@code player} opens a trade with {@code v}) the tailor's cloth and the armourer's crests. */
	public static void offers(Villager v, Player player) {
		if (!(v.level() instanceof ServerLevel level) || v.isBaby()) {
			return;
		}
		Faction f = Quests.settlement(level, v);
		if (f == null) {
			return;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		MerchantOffers offers = v.getOffers();
		if (job.equals("shepherd")) {
			offer(offers, "arming_doublet", DOUBLET_PRICE);
			offer(offers, "hood", HOOD_PRICE);
			offer(offers, "cloak", CLOAK_PRICE);
			if (f.type().isRealm()) {
				offer(offers, "giornea", GIORNEA_PRICE);
				offer(offers, "torse", TORSE_PRICE);
			}
		} else if (job.equals("armorer") && f.type() == FactionType.KINGDOM) {
			offer(offers, "crest_horns", CREST_PRICE);
			offer(offers, "crest_wings", CREST_PRICE);
		}
	}

	private static void offer(MerchantOffers offers, String name, int price) {
		ItemStack stack = HeraldryArmour.stack(name);
		if (!stack.isEmpty() && offers.stream().noneMatch(o -> o.getResult().is(stack.getItem()))) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), stack, USES, XP, PRICE_MULT));
		}
	}
}
