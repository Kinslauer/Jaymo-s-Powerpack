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
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
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
 * <li><b>Going down or dying</b> with the sidearm out, the bow or gun goes back to hand first ({@link #sheathe}). The belt is
 * on a downed body's search screen with its pockets, and drops with its gear when it dies, onto the body's loot screen
 * ({@link NpcPockets}).</li>
 * <li><b>Gunners</b> (Andy, 2026-11-27: "gunners should be able to pick up weapons they find and keep at least an extra sword in
 * their inventory. they dont use shields"): a gunner's sidearm is a sword ({@link #GUNNER_IRON}); one without a sword at the
 * belt (none given yet, his taken, only a dagger) walks to a blade lying near when no foe is close and takes it up
 * ({@link #scavenge}: a sword before anything else), what was at the belt going into his pack or at his feet; and a shield in
 * his hand goes into his pack (and isn't shown on him).</li>
 * <li><b>Pillagers</b> (Andy: "they should carry backup weapons too, and use the vindicator's animations for melee"): an
 * iron axe at the belt, drawn as any shooter's is, and fought with by {@link BackupMelee}, in the vindicator's attacking pose
 * (the game draws an aggressive pillager without a crossbow that way).</li>
 * <li><b>On the body</b> ({@link #show}): what an NPC carries but doesn't hold (the belt's, then the weapons and a shield in an
 * MCA villager's pack) is handed to Jaymo's Powers, which hangs up to three of them in their places, as it does a player's
 * (a blade at the hip, a bow or gun on the back); and an archer with arrows wears a real quiver ({@link #QUIVER}: the
 * Archers' quivers for a realm's, Jaymo's Kitchen's for anyone else; {@code client/QuiverLayer}).</li>
 * </ul>
 */
public final class Sidearms {
	/** The weapon at the belt: the sidearm, or (while the sidearm is out) the slung bow or gun. Saved; empty when none. */
	public static final AttachmentType<ItemStack> BELT = AttachmentRegistry.create(JaymosKingdoms.id("sidearm"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC));
	/** On an NPC archer with arrows left: the quiver it wears, synced to everyone (drawn on its back). Its look only. */
	public static final AttachmentType<ItemStack> QUIVER = AttachmentRegistry.create(JaymosKingdoms.id("quiver_worn"),
		builder -> builder.syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** The quivers, first found worn: a realm's archers (big to small, by rank of kit), and anyone else's. */
	static final String[] REALM_QUIVERS = {"archers:medium_quiver", "jaymoskitchen:quiver", "archers:small_quiver"};
	static final String[] PLAIN_QUIVERS = {"jaymoskitchen:quiver", "archers:small_quiver"};
	/** On one who has been given a sidearm (it's never given twice). */
	static final String GIVEN_TAG = "jaymoskingdoms.sidearm";

	/** How often (ticks) the NPCs near each player are looked at, and how near (blocks). */
	static final int LOOK_EVERY = 20;
	static final double NEAR = 64.0;

	/** The sidearms, by kit, each with the ones to fall back on when a mod is missing (first found is given). */
	static final String[] IRON = {"jaymospowers:iron_dagger", "rogues:iron_dagger", "minecraft:iron_sword"};
	static final String[] STONE = {"jaymospowers:stone_dagger", "rogues:flint_dagger", "minecraft:stone_sword"};
	static final String[] FLINT = {"rogues:flint_dagger", "jaymospowers:stone_dagger", "minecraft:stone_sword"};
	/** A gunner's sword: iron in metal, else stone. */
	static final String[] GUNNER_IRON = {"minecraft:iron_sword"}, GUNNER_STONE = {"minecraft:stone_sword"};
	/** A pillager's backup weapon. */
	static final String[] PILLAGER = {"minecraft:iron_axe"};
	/** A gunner looks this far (blocks) for a blade lying about, takes it within reach, and only with no foe nearer than this. */
	static final double SCAVENGE_RANGE = 12.0, SCAVENGE_REACH = 1.8, SCAVENGE_CALM = 16.0;
	static final float SCAVENGE_SPEED = 0.7F;
	static final double FOLK_SCAVENGE_SPEED = 1.1;
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
		if (mob instanceof Pillager) {
			return true;
		} else if (mob instanceof Folk folk) {
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
		String[] kit = mob instanceof Pillager ? PILLAGER : gunner(mob) ? (metal(mob) && !camp ? GUNNER_IRON : GUNNER_STONE)
			: camp ? FLINT : metal(mob) ? IRON : STONE;
		for (String id : kit) {
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
			// (a gunner carries no shield, and a shield already on the back is drawn there)
			boolean backShield = !mob.getItemBySlot(EquipmentSlot.BODY).isEmpty() || gunner(mob);
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
		ItemStack quiver = archer ? quiver(mob) : ItemStack.EMPTY;
		ItemStack worn = mob.getAttached(QUIVER);
		if (quiver.isEmpty()) {
			if (worn != null) {
				mob.removeAttached(QUIVER);
			}
		} else if (worn == null || !worn.is(quiver.getItem())) {
			mob.setAttached(QUIVER, quiver);
		}
	}

	/** The real quiver {@code mob} wears: one of the Archers' for a realm's archer in metal, else Kitchen's; empty without either mod. */
	private static ItemStack quiver(Mob mob) {
		boolean realm = GuardKit.serves(mob) != null || Downed.isGuard(mob) && !Barbarians.isCampVillager(mob);
		String[] ids = realm && metal(mob) ? REALM_QUIVERS : PLAIN_QUIVERS;
		for (String id : ids) {
			var item = RpgKit.item(id);
			if (item.isPresent()) {
				return new ItemStack(item.get());
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean bow(ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem && !gun(stack);
	}

	// ---- gunners ----------------------------------------------------------------------------------------------------------

	/** A gunner: an early gun, a musket, a musketoon in hand, or slung while the sidearm is out. */
	static boolean gunner(LivingEntity mob) {
		return EarlyGuns.gunner(mob) || Musketoon.holds(mob) || mob instanceof Folk f && Muskets.armed(f);
	}

	/** A sword, not a dagger or a knife (both are in the swords tag). */
	static boolean sword(ItemStack stack) {
		if (!stack.is(ItemTags.SWORDS)) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return !path.contains("dagger") && !path.contains("knife") && !path.contains("skewer");
	}

	/** A gunner uses no shield: one in his off hand goes into his pack, else at his feet. */
	private static void unshield(ServerLevel level, Mob mob) {
		ItemStack off = mob.getOffhandItem();
		if (!off.has(DataComponents.BLOCKS_ATTACKS)) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		if (!(mob instanceof Villager v && (packed(mob, off) || v.getInventory().addItem(off).isEmpty()))) {
			mob.spawnAtLocation(level, off);
		}
	}

	/**
	 * A gunner without a sword at his belt, and no foe near, goes for the nearest blade lying in sight (a sword first) and
	 * takes it up there; what was at his belt goes into his pack, or at his feet.
	 */
	private static void scavenge(ServerLevel level, Mob mob) {
		if (drawn(mob) || sword(belt(mob)) || mob.isPassenger() || mob.hasAttached(Ranks.RANK)) {
			return;
		}
		LivingEntity foe = Rulers.target(mob);
		if (foe != null && foe.isAlive() && mob.distanceTo(foe) < SCAVENGE_CALM) {
			return;
		}
		ItemEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(SCAVENGE_RANGE))) {
			ItemStack stack = item.getItem();
			if (!item.isAlive() || !Rearm.isMelee(stack) || item.isInLava() || item.hasPickUpDelay()) {
				continue;
			}
			// (a sword first; a dagger only for an empty belt)
			if (!sword(stack) && !belt(mob).isEmpty()) {
				continue;
			}
			double score = mob.distanceTo(item) - (sword(stack) ? 6.0 : 0.0);
			if (score < bestScore && mob.getSensing().hasLineOfSight(item)) {
				bestScore = score;
				best = item;
			}
		}
		if (best == null) {
			return;
		}
		if (mob.distanceTo(best) > SCAVENGE_REACH) {
			if (mob instanceof Villager) {
				mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(best, SCAVENGE_SPEED, 0));
			} else {
				mob.getNavigation().moveTo(best, FOLK_SCAVENGE_SPEED);
			}
			return;
		}
		ItemStack stack = best.getItem().copy();
		ItemStack one = stack.split(1);
		mob.take(best, 1);
		if (stack.isEmpty()) {
			best.discard();
		} else {
			best.setItem(stack);
		}
		ItemStack old = belt(mob);
		setBelt(mob, one);
		if (!old.isEmpty() && !(mob instanceof Villager v && v.getInventory().addItem(old).isEmpty())) {
			mob.spawnAtLocation(level, old);
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.0F);
	}

	// ---- pillagers --------------------------------------------------------------------------------------------------------

	/**
	 * A pillager with its backup weapon out fights with it: the game's melee goal, which marks it aggressive, so with no
	 * crossbow in hand it is drawn in the vindicator's attacking pose and swing. Only while a blade or axe is in its hand.
	 */
	static final class BackupMelee extends MeleeAttackGoal {
		private final Pillager pillager;

		BackupMelee(Pillager pillager) {
			super(pillager, 1.0, true);
			this.pillager = pillager;
		}

		@Override
		public boolean canUse() {
			return Rearm.isMelee(this.pillager.getMainHandItem()) && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return Rearm.isMelee(this.pillager.getMainHandItem()) && super.canContinueToUse();
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<Mob> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEAR),
					m -> m instanceof Villager || m instanceof Folk || m instanceof Pillager)) {
					if (!seen.add(mob) || !mob.isAlive()) {
						continue;
					}
					if (Downed.isDowned(mob)) {
						sheathe(mob);
					} else {
						equip(mob);
						if (gunner(mob)) {
							unshield(level, mob);
							scavenge(level, mob);
						}
					}
					show(mob);
				}
			}
		}
	}
}
