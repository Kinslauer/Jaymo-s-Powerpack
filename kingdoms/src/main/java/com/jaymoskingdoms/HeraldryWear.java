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
import net.minecraft.world.level.Level;
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
 * <li><b>Tailors</b> (shepherds) sell the arming doublet, hood and cloak, and in a realm the giornea, the torse and
 * the tabard in the realm's arms; a realm's armourer sells the coat of plates in its colour, and a kingdom's the crests
 * anyone may wear, the horns and the wings.</li>
 * <li>Each realm's knights wear its own make of cuirass (plain, fluted or rounded) where creed and land don't choose
 * one, a gorget, faulds and tassets, and some realms put rondels on their plate spaulders.</li>
 * <li><b>Torn cloth</b>: a bandit leader and his men go in torn hoods and cloaks, and the Banished start with both.</li>
 * </ul>
 */
public final class HeraldryWear {
	/** How often the villagers near players are looked over (ticks), and how far round each player; how often players' gilded plate is checked. */
	static final int SWEEP = 100, GILDED_CHECK = 40;
	static final double SWEEP_RANGE = 48.0;
	/** The share of a bandit leader's men with a torn cloak as well as the hood. */
	static final float BANDIT_CLOAK_SHARE = 0.5F;
	/** Prices (emeralds): the tailor's cloth, and the armourer's crests. */
	static final int DOUBLET_PRICE = 4, HOOD_PRICE = 3, CLOAK_PRICE = 6, GIORNEA_PRICE = 9, TORSE_PRICE = 5, CREST_PRICE = 14;
	/** Prices (emeralds): a realm tailor's tabard in its arms, and a realm armourer's coat of plates (cheaper than plate). */
	static final int TABARD_PRICE = 8, COAT_PRICE = 16;
	/** The makes of cuirass a realm's knights may wear, one to a realm. */
	static final List<String> CUIRASSES = List.of("plate_cuirass", "fluted_cuirass", "rounded_breastplate");
	static final int USES = 3, XP = 5;
	static final float PRICE_MULT = 0.05F;
	/** The lands whose realms field knights in the harness (the others keep their own land's gear). */
		static final Set<Climate> KNIGHT_LANDS = EnumSet.of(Climate.PLAINS, Climate.TAIGA, Climate.SWAMP, Climate.SNOWY, Climate.DESERT, Climate.JUNGLE, Climate.SAVANNA, Climate.CHERRY, Climate.BADLANDS);
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
					unsleeve(player);
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

	/** (GuardKit) The helm shape {@code f}'s realm keeps for its knights. */
	static String realmHelm(Faction f) {
		return helm(realm(f));
	}

	/** (GuardKit) The war arm {@code f}'s knights carry, marked as the realm's make; empty without Powers. */
	static ItemStack warArm(MinecraftServer server, Faction f) {
		return plate(server, f, EquipmentSlot.MAINHAND);
	}

	/** (GuardKit) The finish {@code f}'s knights' plate has in {@code climate}: its creed's, else its land's, else the realm's own. */
	static String knightFinish(@Nullable MinecraftServer server, Faction f, Climate climate) {
		String creed = creedFinish(server, f);
		if (!creed.isEmpty()) {
			return creed;
		}
		String land = landFinish(f, climate);
		return land.isEmpty() ? finish(f) : land;
	}

	/** {@code f}'s crest: its beast on a torse of the realm's colour. */
	static ItemStack crest(@Nullable MinecraftServer server, Faction f) {
		return dyed(HeraldryArmour.stack(beast(f)), Outfits.rgb(Outfits.heraldry(f, server).field()));
	}

	private static ItemStack dyed(ItemStack stack, int rgb) {
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	/** A heraldic tabard in {@code f}'s realm's arms: dyed its field, its ordinary laid front and back in the charge's colour. */
	static ItemStack tabard(@Nullable MinecraftServer server, Faction f) {
		return withArms(dyed(HeraldryArmour.stack("tabard"), Outfits.rgb(Outfits.heraldry(f, server).field())), server, f);
	}

	/** {@code tabard} (already dyed) with {@code f}'s realm's ordinary laid on it in the charge's colour. */
	static ItemStack withArms(ItemStack tabard, @Nullable MinecraftServer server, Faction f) {
		Outfits.Heraldry arms = Outfits.heraldry(f, server);
		tabard.set(HeraldryArmour.ARMS, new HeraldryArmour.Arms(arms.ordinary(), Outfits.rgb(arms.charge())));
		return tabard;
	}

	/** The coat of plates in {@code f}'s realm's colour, as its armourers sell it. */
	static ItemStack coatOfPlates(@Nullable MinecraftServer server, Faction f) {
		return dyed(HeraldryArmour.stack("coat_of_plates"), Outfits.rgb(Outfits.heraldry(f, server).field()));
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
			long h = hash(realm(f)) >>> 24;
			long r = Math.floorMod(h, 7L);
			return switch ((int)r) {
				case 0 -> HeraldryArmour.BLACKENED;
				case 1 -> HeraldryArmour.RUSSETED;
				case 2 -> HeraldryArmour.BLUED;
				case 3 -> HeraldryArmour.PAINTED;
				case 4 -> HeraldryArmour.ETCHED;
				case 5 -> HeraldryArmour.RUSTED;
				default -> "";
			};
		}

		/** The creed-specific finish for {@code f}'s knights, or empty for the default (bright/blackened). */
		private static String creedFinish(@Nullable MinecraftServer server, Faction f) {
			if (server == null) {
				return "";
			}
			GreatWars.Creed creed = GreatWars.creed(server, f);
			return switch (creed) {
				case FAITH -> HeraldryArmour.ETCHED;
				case CHIVALRY -> "";
				case CONQUEST -> HeraldryArmour.BLACKENED;
				case COIN -> HeraldryArmour.ETCHED;
				case CROWN -> HeraldryArmour.GILT_BANDED;
				case BLOOD -> HeraldryArmour.BLACKENED;
				default -> "";
			};
		}

		/** The land-specific finish for {@code f}'s knights, or empty for the default. */
		private static String landFinish(Faction f, Climate climate) {
			return switch (climate) {
				case SNOWY -> HeraldryArmour.FUR_LINED;
				case DESERT -> HeraldryArmour.QUILTED;
				case JUNGLE -> HeraldryArmour.BRINE_SOAKED;
				case SAVANNA -> HeraldryArmour.HIDE;
				case CHERRY -> HeraldryArmour.LAMELLAR;
				case BADLANDS -> HeraldryArmour.BRONZE;
				default -> "";
			};
		}

	/** What {@code f}'s knights wear in {@code slot} (the realm's helm, cuirass or sabatons) or carry (its war arm; the cuirass carries the realm's cape), or empty for the other slots. */
		private static ItemStack plate(@Nullable MinecraftServer server, Faction f, EquipmentSlot slot) {
			Climate climate = server == null ? Climate.PLAINS : Climate.at(server.getLevel(Level.OVERWORLD), f.seat());
			String baseFinish = finish(f);
			String cFinish = creedFinish(server, f);
			String lFinish = landFinish(f, climate);
			String tag = server == null ? null : realmTag(server, f);
			String arm = arm(realm(f));
		
			// Determine the effective finish: creed > land > base
			String effectiveFinish = !cFinish.isEmpty() ? cFinish : (!lFinish.isEmpty() ? lFinish : baseFinish);
		
			// Determine creed/land-specific piece names
			String helmName = helmName(f, climate, server);
			String cuirassName = cuirassName(f, climate, server);
		
			return switch (slot) {
				case HEAD -> {
					ItemStack helm = HeraldryArmour.stack(helmName, effectiveFinish);
					yield tag == null ? helm : PowersCompat.realmMarked(helm, tag);
				}
				case MAINHAND -> tag == null || arm == null ? ItemStack.EMPTY : PowersCompat.realmArm(arm, tag);
				// (gb-capes) a knight's cuirass carries the realm's cape
				case CHEST -> server == null ? HeraldryArmour.stack(cuirassName, effectiveFinish)
					: Capes.cape(HeraldryArmour.stack(cuirassName, effectiveFinish), Outfits.heraldry(f, server));
				case FEET -> HeraldryArmour.stack("plate_sabatons", effectiveFinish);
				default -> ItemStack.EMPTY;
			};
		}
	
		/** The helm name for {@code f}'s knights based on creed and climate. */
		private static String helmName(Faction f, Climate climate, @Nullable MinecraftServer server) {
			if (server == null) {
				return helm(realm(f));
			}
			GreatWars.Creed creed = GreatWars.creed(server, f);
			// Creed-specific helms
			if (creed == GreatWars.Creed.COIN) {
				return "barbute"; // Coin realms hire men-at-arms in barbutes
			}
			if (creed == GreatWars.Creed.CONQUEST) {
				return "veiled_helm"; // Conquest champions wear grotesque visors
			}
			// Land-specific helms
			if (climate == Climate.DESERT) {
				return "veiled_helm"; // Spiked helm with mail veil
			}
			return helm(realm(f));
		}
	
		/** The cuirass name for {@code f}'s knights based on creed and climate. */
		private static String cuirassName(Faction f, Climate climate, @Nullable MinecraftServer server) {
			if (server == null) {
				return realmCuirass(f);
			}
			GreatWars.Creed creed = GreatWars.creed(server, f);
			// Creed-specific cuirasses
			if (creed == GreatWars.Creed.FAITH) {
				return "faith_cuirass";
			}
			if (creed == GreatWars.Creed.COIN) {
				return "etched_cuirass";
			}
			if (creed == GreatWars.Creed.CROWN) {
				return "gilt_banded_cuirass";
			}
			// Land-specific cuirasses
			if (climate == Climate.SNOWY) {
				return "fur_lined_cuirass";
			}
			if (climate == Climate.JUNGLE) {
				return "quilted_cotton_armor";
			}
			if (climate == Climate.SAVANNA) {
				return "hide_armor";
			}
			if (climate == Climate.CHERRY) {
				return "laced_lamellar";
			}
			if (climate == Climate.BADLANDS) {
				return "bronze_armor";
			}
			return realmCuirass(f);
		}

		/** The make of cuirass {@code f}'s realm's own armourers beat out for its knights: plain, fluted or rounded. */
		private static String realmCuirass(Faction f) {
			return pick(CUIRASSES, hash(realm(f)) >>> 40);
		}

		/** The realm's make of spaulders; a realm in plain plate spaulders may add rondels to them. */
		private static String realmSpaulders(Faction f) {
			String make = pick(HeraldryArmour.SPAULDERS, hash(realm(f)) >>> 16);
			return make.equals("plate_spaulders") && (hash(realm(f)) >>> 48 & 1L) == 0L ? "rondel_spaulders" : make;
		}

	/** The harness under and round a knight of {@code f}'s plate. */
		private static void harness(Mob mob, Faction f) {
			MinecraftServer server = mob.level().getServer();
			Climate climate = server == null ? Climate.PLAINS : Climate.at(server.getLevel(Level.OVERWORLD), f.seat());
			String baseFinish = finish(f);
			String cFinish = creedFinish(server, f);
			String lFinish = landFinish(f, climate);
			String effectiveFinish = !cFinish.isEmpty() ? cFinish : (!lFinish.isEmpty() ? lFinish : baseFinish);
		
			// Determine creed/land-specific harness pieces
			String spauldersName = spauldersName(f, climate, server);
			String armHarnessName = armHarnessName(f, climate, server);
			String gauntletsName = gauntletsName(f, climate, server);
			String cuissesName = cuissesName(f, climate, server);
			String greavesName = greavesName(f, climate, server);
			String doubletName = doubletName(f, climate, server);
		
			Harness.dress(mob, 
				dyed(HeraldryArmour.stack(doubletName), Outfits.rgb(Outfits.heraldry(f, server).field())),
				HeraldryArmour.stack("mail_haubergeon"),
				HeraldryArmour.stack(spauldersName, effectiveFinish),
				HeraldryArmour.stack(armHarnessName, effectiveFinish),
				HeraldryArmour.stack(gauntletsName, effectiveFinish),
				HeraldryArmour.stack(cuissesName, effectiveFinish),
				HeraldryArmour.stack(greavesName, effectiveFinish),
				HeraldryArmour.stack("gorget", effectiveFinish),
				HeraldryArmour.stack("faulds_and_tassets", effectiveFinish)
			);
		}
	
		/** The spaulders name for {@code f}'s knights based on creed and climate. */
		private static String spauldersName(Faction f, Climate climate, @Nullable MinecraftServer server) {
			if (server == null) {
				return realmSpaulders(f);
			}
			GreatWars.Creed creed = GreatWars.creed(server, f);
			if (creed == GreatWars.Creed.CONQUEST) {
				return "spiked_spaulders";
			}
			if (creed == GreatWars.Creed.CROWN) {
				return "gilt_banded_spaulders";
			}
			if (climate == Climate.SNOWY) {
				return "fur_lined_spaulders";
			}
			return realmSpaulders(f);
		}
	
		/** The arm harness name for {@code f}'s knights based on creed and climate. */
		private static String armHarnessName(Faction f, Climate climate, @Nullable MinecraftServer server) {
			if (server == null) {
				return "plate_arm_harness";
			}
			GreatWars.Creed creed = GreatWars.creed(server, f);
			if (creed == GreatWars.Creed.CHIVALRY) {
				return "oath_sleeves";
			}
			if (climate == Climate.SNOWY) {
				return "fur_lined_arm_harness";
			}
			return "plate_arm_harness";
		}
	
		/** The gauntlets name for {@code f}'s knights based on creed and climate. */
		private static String gauntletsName(Faction f, Climate climate, @Nullable MinecraftServer server) {
			if (climate == Climate.SNOWY) {
				return "fur_lined_gauntlets";
			}
			return "plate_gauntlets";
		}
	
		/** The cuisses name for {@code f}'s knights based on creed and climate. */
		private static String cuissesName(Faction f, Climate climate, @Nullable MinecraftServer server) {
			if (climate == Climate.SNOWY) {
				return "fur_lined_cuisses";
			}
			return "plate_cuisses";
		}
	
		/** The greaves name for {@code f}'s knights based on creed and climate. */
		private static String greavesName(Faction f, Climate climate, @Nullable MinecraftServer server) {
			if (climate == Climate.SNOWY) {
				return "fur_lined_greaves";
			}
			return "plate_greaves";
		}
	
		/** The doublet name for {@code f}'s knights based on creed and climate. */
		private static String doubletName(Faction f, Climate climate, @Nullable MinecraftServer server) {
			if (climate == Climate.DESERT) {
				return "quilted_coat";
			}
			return "arming_doublet";
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

	/**
	 * (medieval picks, troop ladders: {@link Ranks}) {@code mob}, risen to knight in {@code f}'s tree, is put in its knights'
	 * plate, helm, war arm and harness, as they wear it. False (nothing put on) where the realm fields no knights in harness.
	 */
	static boolean knight(Mob mob, Faction f) {
		if (!(mob.level() instanceof ServerLevel level) || !f.type().isRealm() || f.fallen() || !KNIGHT_LANDS.contains(Climate.at(level, f.seat()))
			|| !Outfits.ironRich(mob, f) || Outfits.heraldry(f, level.getServer()).armoury()) {
			return false;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
			ItemStack piece = plate(level.getServer(), f, slot);
			if (!piece.isEmpty()) {
				mob.setItemSlot(slot, piece);
			}
		}
		harness(mob, f);
		return true;
	}

	/** {@code mob} rules {@code faction} (a king, a lord; from {@link Rulers#fit} too): the realm's crest, and gilded spaulders. */
		static void ruler(Mob mob, Faction faction) {
			MinecraftServer server = mob.level().getServer();
			if (server != null && GreatWars.creed(server, faction) == GreatWars.Creed.CROWN) {
				// King's Guard livery for Crown creed
				Harness.dress(mob, 
					crest(server, faction), 
					HeraldryArmour.stack("gilt_banded_spaulders", HeraldryArmour.GILT_BANDED),
					tabard(server, faction)
				);
				Capes.ensure(mob, faction, true);
			} else {
				Harness.dress(mob, crest(mob.level().getServer(), faction), HeraldryArmour.stack("plate_spaulders", HeraldryArmour.GILDED));
				// (gb-capes) and the realm's cape on whatever chest piece they wear
				Capes.ensure(mob, faction, true);
			}
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
			// (a guard kitted by GuardKit keeps his harness over whatever chest piece was bought him, and puts it by with his armour)
			ItemStack chest = v.getItemBySlot(EquipmentSlot.CHEST);
			if (!Harness.get(v, Harness.Slot.ARMS).isEmpty() && HeraldryArmour.of(chest) == null && (!v.hasAttached(GuardKit.KIT) || chest.isEmpty())) {
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

	/** Andy: oath sleeves are "lost when the oath breaks": a Paladin whose oath is broken can't keep them on till it's atoned for. */
	private static void unsleeve(ServerPlayer player) {
		ItemStack arms = Harness.get(player, Harness.Slot.ARMS);
		HeraldryArmour.Piece sleeves = HeraldryArmour.PIECES.get("oath_sleeves");
		if (sleeves != null && arms.is(sleeves.item()) && player.getAttachedOrElse(Paladins.BROKEN, false)) {
			Harness.set(player, Harness.Slot.ARMS, ItemStack.EMPTY);
			player.getInventory().placeItemBackInInventory(arms, Prediction.SERVER_ONLY);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.oath_sleeves_off"));
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
				offer(offers, tabard(level.getServer(), f), TABARD_PRICE);
			}
		} else if (job.equals("armorer") && f.type().isRealm()) {
			offer(offers, coatOfPlates(level.getServer(), f), COAT_PRICE);
			if (f.type() == FactionType.KINGDOM) {
				offer(offers, "crest_horns", CREST_PRICE);
				offer(offers, "crest_wings", CREST_PRICE);
			}
		}
	}

	private static void offer(MerchantOffers offers, String name, int price) {
		offer(offers, HeraldryArmour.stack(name), price);
	}

	private static void offer(MerchantOffers offers, ItemStack stack, int price) {
		if (!stack.isEmpty() && offers.stream().noneMatch(o -> o.getResult().is(stack.getItem()))) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), stack, USES, XP, PRICE_MULT));
		}
	}
}
