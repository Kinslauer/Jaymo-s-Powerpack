package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/**
 * Andy (2026-11-27): "ranged guards and other npcs should carry a dagger or short weapon. i saw a guard with an arquebus
 * slapping a zombie that got too close with it instead of switching. you should be able to see what npcs have equipped VDM
 * style as well."
 * <ul>
 * <li><b>The belt</b> ({@link #BELT}, saved on the mob): one more place an NPC carries a weapon, besides its hands. Every
 * shooter ({@link #shooter}: a guard, archer or gunner, a camp's or a ship's fighter, a forest ranger, a folk fighter, any of
 * them with a bow, crossbow or gun in hand or in their pack) is given a sidearm there once ({@link #GIVEN_TAG}, so one a
 * player takes isn't made again): an iron dagger for one in metal (mail, plate, a realm's Templar legs), a stone dagger for
 * one in leather or cloth, a flint dagger for a camp's raider or a pirate ({@link #issue}). Not a player's recruit: they
 * carry what the player gives them.</li>
 * <li><b>Switching</b> ({@link WeaponSense#pick}): a foe inside reach of a shooter, and the sidearm comes to hand; the bow or
 * gun goes onto the belt (slung) in its place, and comes back once the foe is clear or the fight is over. A gun is never used
 * as a club: a gunner out of shot fights with the sidearm too. A gun with a bayonet fixed keeps its bayonet. The swap trades
 * two stacks between the hand and the belt: nothing is made or lost, and never the very stack an MCA villager's pack holds.</li>
 * <li><b>Going down or dying</b> with the sidearm out, the bow or gun goes back to hand first ({@link #sheathe}); a player
 * taking a downed body's weapons takes the belt's too ({@code Downed}). The sidearm itself is never dropped on death.</li>
 * <li><b>On the body</b> ({@link #show}): what an NPC carries but doesn't hold (the belt's, then the weapons and a shield in an
 * MCA villager's pack) is handed to Jaymo's Powers, which hangs up to three of them in their places, as it does a player's
 * (a blade at the hip, a bow or gun on the back); and an archer with arrows wears a quiver ({@link #QUIVER},
 * {@code client/QuiverLayer}).</li>
 * </ul>
 */
public final class Sidearms {
	/** The weapon at the belt: the sidearm, or (while the sidearm is out) the slung bow or gun. Saved; empty when none. */
	public static final AttachmentType<ItemStack> BELT = AttachmentRegistry.create(JaymosKingdoms.id("sidearm"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC));
	/** On an NPC archer with arrows left: true, synced to everyone (a quiver is drawn on its back). */
	public static final AttachmentType<Boolean> QUIVER = AttachmentRegistry.create(JaymosKingdoms.id("quiver_shown"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** On one who has been given a sidearm (it's never given twice). */
	static final String GIVEN_TAG = "jaymoskingdoms.sidearm";

	/** How often (ticks) the NPCs near each player are looked at, and how near (blocks). */
	static final int LOOK_EVERY = 20;
	static final double NEAR = 64.0;

	/** The sidearms, by kit, each with the ones to fall back on when a mod is missing (first found is given). */
	static final String[] IRON = {"jaymospowers:iron_dagger", "rogues:iron_dagger", "minecraft:iron_sword"};
	static final String[] STONE = {"jaymospowers:stone_dagger", "rogues:flint_dagger", "minecraft:stone_sword"};
	static final String[] FLINT = {"rogues:flint_dagger", "jaymospowers:stone_dagger", "minecraft:stone_sword"};
	/** Armour or harness pieces whose ids name one of these are metal: one wearing any is given the iron dagger. */
	static final String[] METAL = {"iron", "chain", "mail", "plate", "templar", "steel", "copper", "scale", "lamellar", "brigandine", "knight"};

	private Sidearms() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Sidearms::tick);
		// (before the death's drops: the bow or gun is back in hand, and drops, or not, as it would have)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Mob mob) {
				sheathe(mob);
			}
			return true;
		});
	}

	// ---- the belt ----------------------------------------------------------------------------------------------------------

	/** What is at {@code mob}'s belt (empty when nothing). */
	public static ItemStack belt(LivingEntity mob) {
		ItemStack belt = mob.getAttached(BELT);
		return belt == null ? ItemStack.EMPTY : belt;
	}

	static void setBelt(LivingEntity mob, ItemStack stack) {
		if (stack.isEmpty()) {
			mob.removeAttached(BELT);
		} else {
			mob.setAttached(BELT, stack);
		}
	}

	/** A bow, a crossbow or a gun (an early gun, a musket, a musketoon). */
	static boolean ranged(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || gun(stack));
	}

	/** An early gun, a musket, or a caravan's musketoon. */
	static boolean gun(ItemStack stack) {
		return !stack.isEmpty() && (EarlyGuns.Rung.of(stack) != null || stack.is(Muskets.musket()) || Musketoon.ITEM != null && stack.is(Musketoon.ITEM));
	}

	/** The sidearm is out: the belt holds the slung bow or gun. */
	public static boolean drawn(LivingEntity mob) {
		return ranged(belt(mob));
	}

	/** The sidearm is at the belt, ready to draw. */
	static boolean ready(LivingEntity mob) {
		return Rearm.isMelee(belt(mob));
	}

	/**
	 * The sidearm goes back to the belt and the slung bow or gun to hand (going down, dying, the fight over). The hand's stack
	 * is kept as it is, its drop chance with it.
	 */
	static void sheathe(Mob mob) {
		if (!drawn(mob)) {
			return;
		}
		ItemStack slung = belt(mob);
		ItemStack hand = mob.getMainHandItem();
		setBelt(mob, Rearm.isMelee(hand) && !packed(mob, hand) ? hand : ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.MAINHAND, slung);
	}

	/**
	 * From {@link WeaponSense}: {@code blade} (in hand) goes back to an empty belt instead of into the pack, if it is one of the
	 * sidearms given ({@link #sidearm}) to one given one. True if it went.
	 */
	static boolean stow(Mob mob, ItemStack blade) {
		if (!belt(mob).isEmpty() || !mob.entityTags().contains(GIVEN_TAG) || !sidearm(blade) || packed(mob, blade)) {
			return false;
		}
		setBelt(mob, blade);
		return true;
	}

	/** One of the sidearms given ({@link #IRON}, {@link #STONE}, {@link #FLINT}): the only blades put back on an empty belt. */
	static boolean sidearm(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		for (String[] kit : new String[][] {IRON, STONE, FLINT}) {
			for (String given : kit) {
				if (given.equals(id)) {
					return true;
				}
			}
		}
		return false;
	}

	/** {@code stack} is the very stack in {@code mob}'s MCA pack (it would be in two places on the belt). */
	static boolean packed(Mob mob, ItemStack stack) {
		if (mob instanceof Villager v) {
			SimpleContainer pack = v.getInventory();
			for (int i = 0; i < pack.getContainerSize(); i++) {
				if (pack.getItem(i) == stack) {
					return true;
				}
			}
		}
		return false;
	}

	/** From {@code Downed}: a player takes what is at a downed body's belt; empty when nothing. */
	static ItemStack take(Mob body) {
		ItemStack belt = belt(body);
		setBelt(body, ItemStack.EMPTY);
		return belt;
	}

	// ---- who carries one, and which -----------------------------------------------------------------------------------------

	/** One who fights with a bow, crossbow or gun, and so carries a sidearm (see the class comment). */
	static boolean shooter(Mob mob) {
		if (!mob.isAlive() || mob.isBaby() || mob.hasAttached(Ranks.RANK) || Downed.isDowned(mob)) {
			return false;
		}
		boolean fighter;
		if (mob instanceof Folk folk) {
			fighter = folk.combatant() && !Sorcery.isCaster(folk.role());
		} else if (mob instanceof Villager v) {
			fighter = Downed.isGuard(v) || Barbarians.isCampVillager(v) || v instanceof Worker w && Pirates.isPirate(w.role())
				|| v.entityTags().contains(ForestRangers.TAG);
		} else {
			return false;
		}
		return fighter && (ranged(mob.getMainHandItem()) || ranged(mob.getOffhandItem()) || drawn(mob) || packs(mob, Sidearms::ranged)
			|| mob instanceof Folk f && f.ranged());
	}

	/** Something {@code what} likes is in {@code mob}'s MCA pack. */
	private static boolean packs(Mob mob, java.util.function.Predicate<ItemStack> what) {
		return mob instanceof Villager v && v.getInventory().hasAnyMatching(what);
	}

	/** The sidearm {@code mob} is given: flint for a camp's raider or a pirate, iron for one in metal, else stone. */
	static ItemStack issue(Mob mob) {
		boolean camp = Barbarians.isCampVillager(mob) || mob instanceof Worker w && Pirates.isPirate(w.role())
			|| mob instanceof Folk f && f.role() != Folk.GUARD && f.role() != Folk.ARCHER;
		for (String id : camp ? FLINT : metal(mob) ? IRON : STONE) {
			var item = RpgKit.item(id);
			if (item.isPresent()) {
				return new ItemStack(item.get());
			}
		}
		return ItemStack.EMPTY;
	}

	/** Wearing any metal: armour or a harness piece whose id names one ({@link #METAL}). */
	static boolean metal(Mob mob) {
		List<ItemStack> worn = new ArrayList<>();
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			worn.add(mob.getItemBySlot(slot));
		}
		for (Harness.Slot slot : Harness.Slot.values()) {
			worn.add(Harness.get(mob, slot));
		}
		for (ItemStack piece : worn) {
			if (piece.isEmpty()) {
				continue;
			}
			String path = BuiltInRegistries.ITEM.getKey(piece.getItem()).getPath().toLowerCase(Locale.ROOT);
			for (String metal : METAL) {
				if (path.contains(metal)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Gives {@code mob} its sidearm, once, if it is a shooter with nothing at its belt. */
	static void equip(Mob mob) {
		if (mob.entityTags().contains(GIVEN_TAG) || !belt(mob).isEmpty() || !shooter(mob)) {
			return;
		}
		ItemStack sidearm = issue(mob);
		if (!sidearm.isEmpty()) {
			setBelt(mob, sidearm);
			mob.addTag(GIVEN_TAG);
		}
	}

	// ---- the look ----------------------------------------------------------------------------------------------------------

	/**
	 * What {@code mob} carries but doesn't hold, for Powers to hang on its body: the belt's, then the weapons and a shield in
	 * an MCA villager's pack (not the stacks in its hands, which are the pack's own). A shield isn't shown while one rides in
	 * the body slot (drawn on the back already, {@code BackGearLayer}). And whether it wears a quiver.
	 */
	static void show(Mob mob) {
		List<ItemStack> carried = new ArrayList<>();
		ItemStack main = mob.getMainHandItem(), off = mob.getOffhandItem();
		ItemStack belt = belt(mob);
		if (!belt.isEmpty()) {
			carried.add(belt);
		}
		if (mob instanceof Villager v) {
			boolean backShield = !mob.getItemBySlot(EquipmentSlot.BODY).isEmpty();
			SimpleContainer pack = v.getInventory();
			for (int i = 0; i < pack.getContainerSize(); i++) {
				ItemStack stack = pack.getItem(i);
				if (stack.isEmpty() || stack == main || stack == off || stack.getItem() instanceof ShieldItem && backShield) {
					continue;
				}
				if (Rearm.isWeapon(stack) || stack.getItem() instanceof ShieldItem) {
					carried.add(stack);
				}
			}
		}
		PowersCompat.carry(mob, carried);
		boolean archer = MobAmmo.has(mob, MobAmmo.Kind.ARROWS) && (bow(main) || bow(off) || bow(belt(mob)) || packs(mob, Sidearms::bow));
		if (archer != Boolean.TRUE.equals(mob.getAttached(QUIVER))) {
			if (archer) {
				mob.setAttached(QUIVER, true);
			} else {
				mob.removeAttached(QUIVER);
			}
		}
	}

	private static boolean bow(ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem && !gun(stack);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<Mob> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEAR), m -> m instanceof Villager || m instanceof Folk)) {
					if (!seen.add(mob) || !mob.isAlive()) {
						continue;
					}
					if (Downed.isDowned(mob)) {
						sheathe(mob);
					} else {
						equip(mob);
					}
					show(mob);
				}
			}
		}
	}
}
