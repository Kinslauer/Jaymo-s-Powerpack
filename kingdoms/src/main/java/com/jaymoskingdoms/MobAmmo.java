package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-12): "mobs should also have finite ammunition, meaning skeletons should carry tattered quivers, and a mob
 * who throws their weapon loses it until they pick it up or replenish otherwise." Nothing in the pack shoots or throws
 * for free any more (the players' side is Powers' {@code compat/SpellThrows}).
 * <ul>
 * <li><b>What they carry</b> ({@link #AMMO}, saved on the mob): arrows (skeletons in their {@link #TATTERED_QUIVER},
 * pillagers, piglins, illusioners, the folk's archers, crewmen, Scalebane archers, the Gorgon), javelins (a guard's
 * javelins, tridents or rocks) and shot (muskets and pistols). Each kind is filled once, the first time it's asked
 * for ({@link #full}), and every shot or throw spends one ({@link #spend}). With none left the mob can't loose that kind:
 * skeletons and pillagers fight with what's in their hands, the folk draw their blades.</li>
 * <li><b>Getting them back</b>: what they shoot and throw can be picked up now, by anyone. A mob short of something
 * picks up what it walks over (its own arrows, a player's, a dropped stack), and one that's run dry goes looking for
 * them within {@link #FETCH_RANGE} once it has no foe ({@link #tick}). The folk and the villages' guards also restock
 * from their stores, one every {@link #RESTOCK_TICKS} while nothing is after them; shot only comes back that way.</li>
 * <li><b>Thrown weapons</b>: a drowned's trident and an orc captain's axe leave their hands ({@link #lost}) and lie where
 * they land, until the mob (or someone else) picks them up.</li>
 * <li><b>Thrown things that aren't arrows</b>: a witch carries a few splash potions ({@link Kind#POTIONS}) and brews one
 * back now and then while nothing is after her; with none left she backs away from her foe. A Hornkin hurler carries
 * a few globs of magma ({@link Kind#BOMBS}) and scoops more only while calm, fighting hand to hand when out. A captain who
 * throws rocks carries a few ({@link Kind#ROCKS}); each lands as a cobblestone that he (or anyone) can pick up again.
 * The Ogre King tears his boulders out of the ground beside him ({@code OgreHolds}), leaving the hole.</li>
 * <li><b>Knives</b>: the Cinder-Shade's Ember Knives are real daggers she carries ({@link Kind#KNIVES}, Andy 2026-11-12:
 * they count), heated in her hands so they burn what they hit; each lands as the dagger it was, and she gathers them
 * up again once nothing is after her. With none left she can't cast the fan. Rogues (a Knife chief, an assassin) throw a
 * Fan of Knives of up to three of theirs the same way ({@link FanOfKnives}).</li>
 * </ul>
 * Not counted: powers and sorcery.
 */
public final class MobAmmo {
	/** What a mob shoots or throws. */
	public enum Kind {
		ARROWS("arrows"), JAVELINS("javelins"), SHOT("shot"), POTIONS("potions"), BOMBS("bombs"), ROCKS("rocks"), KNIVES("knives");

		final String key;

		Kind(String key) {
			this.key = key;
		}
	}

	/** Arrows in a skeleton's tattered quiver, a pillager's, a piglin's, an illusioner's: least and most. */
	static final int[] SKELETON_ARROWS = {6, 12}, PILLAGER_BOLTS = {8, 14}, PIGLIN_BOLTS = {6, 10}, ILLUSIONER_ARROWS = {12, 18};
	/** The folk's archers and crewmen, a Scalebane archer, the Gorgon (her volleys come out of it too), anyone else. */
	static final int[] FOLK_ARROWS = {12, 20}, CREW_ARROWS = {10, 16}, SCALEBANE_ARROWS = {16, 24}, GORGON_ARROWS = {60, 80},
		OTHER_ARROWS = {8, 14};
	/** A guard's javelins (or tridents, or rocks), and a gunman's shot. */
	static final int[] JAVELINS = {2, 3}, SHOT = {6, 10};
	/** A witch's splash potions, a Hornkin hurler's magma, a rock-throwing captain's rocks. */
	static final int[] POTIONS = {4, 7}, BOMBS = {5, 8}, ROCKS = {3, 5};
	/** The Cinder-Shade's throwing knives (her Ember Knives throw five at a time). */
	static final int[] KNIVES = {10, 15};
	/** A rogue's throwing knives (a Knife chief's, an assassin's): his Fan of Knives throws up to three ({@link FanOfKnives}). */
	static final int[] ROGUE_KNIVES = {3, 6};
	/** How often (ticks) carriers are looked over, how near (blocks) a thing must be to be picked up, how far one goes for it. */
	static final int CHECK_TICKS = 10;
	static final double PICKUP_RANGE = 1.6, FETCH_RANGE = 16.0, FETCH_SPEED = 1.0;
	/** Folk and guards restock one of each kind short this often (ticks) while nothing is after them. */
	static final int RESTOCK_TICKS = 600;
	/** A resting arrow or javelin (one this slow) can be picked up. */
	static final double RESTING = 0.01;

	/** What each kind of ammo a mob has left, and what it started with ({@code <kind>_full}). */
	public static final AttachmentType<Map<String, Integer>> AMMO = AttachmentRegistry.create(JaymosKingdoms.id("ammo"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

	/** Worn (and drawn) on every skeleton's back: the client's look only (no recipe, not dropped). */
	public static Item TATTERED_QUIVER;

	/** Mobs that are short of something: looked over every {@link #CHECK_TICKS}. */
	private static final Set<Mob> SHORT = java.util.Collections.newSetFromMap(new WeakHashMap<>());
	/** A weapon a mob threw and must pick up again: the item it was. */
	private static final Map<Mob, Item> LOST = new WeakHashMap<>();
	/** A thrown thing that breaks where it lands (a captain's axe thrown as a snowball) and what it leaves there. */
	private static final Map<Projectile, ItemStack> LEAVES = new WeakHashMap<>();
	private static final Map<Projectile, Vec3> LAST = new WeakHashMap<>();
	/** When each restocking mob last restocked (game time). */
	private static final Map<Mob, Long> RESTOCKED = new WeakHashMap<>();

	private MobAmmo() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("tattered_quiver"));
		TATTERED_QUIVER = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
		ServerEntityEvents.ENTITY_LOAD.register(MobAmmo::loaded);
		ServerTickEvents.END_SERVER_TICK.register(MobAmmo::tick);
	}

	// ---- counting ----------------------------------------------------------------------------------------------------------

	/** How many of {@code kind} {@code mob} has left (filling it the first time). */
	public static int left(Mob mob, Kind kind) {
		Map<String, Integer> ammo = mob.getAttached(AMMO);
		Integer left = ammo == null ? null : ammo.get(kind.key);
		if (left != null) {
			return left;
		}
		int full = full(mob, kind, mob.getRandom());
		set(mob, kind, full, full);
		return full;
	}

	/** Whether {@code mob} has any of {@code kind} left. */
	public static boolean has(Mob mob, Kind kind) {
		return left(mob, kind) > 0;
	}

	/** Spends one of {@code kind} for a shot or throw; false (and nothing spent) when there's none left. */
	public static boolean spend(Mob mob, Kind kind) {
		int left = left(mob, kind);
		if (left <= 0) {
			return false;
		}
		set(mob, kind, left - 1, fullOf(mob, kind));
		SHORT.add(mob);
		if (left == 1 && mob instanceof AbstractSkeleton skeleton) {
			// the bow goes down: it fights with it in its hands
			skeleton.reassessWeaponGoal();
		}
		return true;
	}

	/** Gives {@code mob} {@code count} more of {@code kind}, never past what it started with. */
	static void give(Mob mob, Kind kind, int count) {
		int left = left(mob, kind), full = fullOf(mob, kind);
		set(mob, kind, Math.min(full, left + count), full);
		if (left == 0 && mob instanceof AbstractSkeleton skeleton) {
			skeleton.reassessWeaponGoal();
		}
	}

	private static int fullOf(Mob mob, Kind kind) {
		Map<String, Integer> ammo = mob.getAttached(AMMO);
		Integer full = ammo == null ? null : ammo.get(kind.key + "_full");
		return full == null ? left(mob, kind) : full;
	}

	private static void set(Mob mob, Kind kind, int left, int full) {
		Map<String, Integer> ammo = new HashMap<>(mob.getAttachedOrElse(AMMO, Map.of()));
		ammo.put(kind.key, left);
		ammo.put(kind.key + "_full", full);
		mob.setAttached(AMMO, ammo);
	}

	/** What {@code mob} starts with of {@code kind}. */
	static int full(Mob mob, Kind kind, RandomSource random) {
		int[] range = switch (kind) {
			case JAVELINS -> JAVELINS;
			case SHOT -> SHOT;
			case POTIONS -> POTIONS;
			case BOMBS -> BOMBS;
			case ROCKS -> ROCKS;
			case KNIVES -> mob instanceof PatronChampion ? KNIVES : ROGUE_KNIVES;
			case ARROWS -> {
				if (mob instanceof AbstractSkeleton) {
					yield SKELETON_ARROWS;
				} else if (mob instanceof Pillager) {
					yield PILLAGER_BOLTS;
				} else if (mob instanceof Piglin) {
					yield PIGLIN_BOLTS;
				} else if (mob instanceof Illusioner) {
					yield ILLUSIONER_ARROWS;
				} else if (mob instanceof Folk) {
					yield FOLK_ARROWS;
				} else if (mob instanceof ScalebaneArcher) {
					yield SCALEBANE_ARROWS;
				} else if (mob instanceof com.jaymoskingdoms.beasts.Gorgon) {
					yield GORGON_ARROWS;
				}
				yield mob instanceof Villager ? CREW_ARROWS : OTHER_ARROWS;
			}
		};
		return range[0] + random.nextInt(range[1] - range[0] + 1);
	}

	/** Whether a stack is {@code kind}: arrows are any arrow, javelins any javelin, trident or rock; shot is never found. */
	static boolean is(ItemStack stack, Kind kind) {
		return switch (kind) {
			case ARROWS -> stack.is(ItemTags.ARROWS);
			case JAVELINS -> stack.is(Items.TRIDENT) || stack.is(Items.COBBLESTONE)
				|| BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("javelin");
			case POTIONS -> stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
			case ROCKS -> stack.is(Items.COBBLESTONE) || stack.is(Items.MOSSY_COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE);
			case KNIVES -> knife(stack);
			case SHOT, BOMBS -> false;
		};
	}

	/** A knife or dagger anyone could throw (never Sora's Dagger, Ashfang or the Hidden Blade, which aren't for throwing). */
	public static boolean knife(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return (path.contains("knife") || path.contains("dagger")) && !path.contains("soras") && !path.contains("ashfang")
			&& !path.contains("hidden_blade") && !path.endsWith("_blade");
	}

	// ---- thrown weapons ----------------------------------------------------------------------------------------------------

	/** {@code mob} has thrown the weapon it held ({@code item}): its hand is empty until it picks one up again. */
	public static void lost(Mob mob, Item item) {
		mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		LOST.put(mob, item);
		SHORT.add(mob);
	}

	/** {@code thrown} breaks where it lands (a snowball standing in for a weapon) and leaves {@code stack} there. */
	public static void leaves(Projectile thrown, ItemStack stack) {
		LEAVES.put(thrown, stack);
		LAST.put(thrown, thrown.position());
	}

	// ---- shots in the world ------------------------------------------------------------------------------------------------

	/** Pillagers that have been given a fight for when their bolts run out. */
	private static final Set<Mob> ARMED = java.util.Collections.newSetFromMap(new WeakHashMap<>());

	/**
	 * The game's own bowmen's arrows and the drowned's tridents lie where they land to be picked up again; a pillager out
	 * of bolts clubs with its crossbow (a skeleton out of arrows already fights with its bow: {@code SkeletonQuiverMixin}).
	 */
	private static void loaded(Entity entity, ServerLevel level) {
		if (entity instanceof Pillager pillager && ARMED.add(pillager)) {
			((com.jaymoskingdoms.mixin.MobAccessor) pillager).jaymoskingdoms$goals().addGoal(3,
				new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(pillager, 1.0, false) {
					@Override
					public boolean canUse() {
						return !has(pillager, Kind.ARROWS) && super.canUse();
					}

					@Override
					public boolean canContinueToUse() {
						return !has(pillager, Kind.ARROWS) && super.canContinueToUse();
					}
				});
		}
		if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof Mob owner && (owner instanceof AbstractSkeleton
			|| owner instanceof Pillager || owner instanceof Piglin || owner instanceof Illusioner || owner instanceof Drowned)) {
			arrow.pickup = AbstractArrow.Pickup.ALLOWED;
		}
	}

	/** A bow goal that only shoots while {@code mob} has arrows (a Scalebane archer's, the Gorgon's). */
	public static <T extends net.minecraft.world.entity.monster.Monster & net.minecraft.world.entity.monster.RangedAttackMob>
		net.minecraft.world.entity.ai.goal.RangedBowAttackGoal<T> bowGoal(T mob, double speed, int interval, float range) {
		return new net.minecraft.world.entity.ai.goal.RangedBowAttackGoal<>(mob, speed, interval, range) {
			@Override
			public boolean canUse() {
				return has(mob, Kind.ARROWS) && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return has(mob, Kind.ARROWS) && super.canContinueToUse();
			}
		};
	}

	/** Fighting hand to hand once {@code mob}'s arrows are spent. */
	public static net.minecraft.world.entity.ai.goal.MeleeAttackGoal meleeWhenDry(net.minecraft.world.entity.PathfinderMob mob) {
		return new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(mob, 1.1, true) {
			@Override
			public boolean canUse() {
				return !has(mob, Kind.ARROWS) && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !has(mob, Kind.ARROWS) && super.canContinueToUse();
			}
		};
	}

	/** A shot of a mob's that should lie where it lands for anyone to pick up (the folk's, crewmen's, Scalebane's). */
	public static void canBePickedUp(Projectile shot) {
		if (shot instanceof AbstractArrow arrow) {
			arrow.pickup = AbstractArrow.Pickup.ALLOWED;
		}
	}

	private static void tick(MinecraftServer server) {
		if (!LEAVES.isEmpty()) {
			for (Iterator<Map.Entry<Projectile, ItemStack>> it = LEAVES.entrySet().iterator(); it.hasNext();) {
				Map.Entry<Projectile, ItemStack> e = it.next();
				Projectile thrown = e.getKey();
				if (!thrown.isRemoved()) {
					LAST.put(thrown, thrown.position());
					continue;
				}
				it.remove();
				Vec3 at = LAST.remove(thrown);
				if (at != null && thrown.level() instanceof ServerLevel level) {
					ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, e.getValue());
					item.setDefaultPickUpDelay();
					level.addFreshEntity(item);
				}
			}
		}
		if (SHORT.isEmpty() || server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (Iterator<Mob> it = SHORT.iterator(); it.hasNext();) {
			Mob mob = it.next();
			if (mob.isRemoved() || !mob.isAlive()) {
				it.remove();
				continue;
			}
			if (!(mob.level() instanceof ServerLevel level) || Downed.isDowned(mob)) {
				continue;
			}
			if (!look(level, mob)) {
				it.remove();
			}
		}
	}

	/** One short mob: picks up what's at its feet, restocks, goes looking. False once it wants for nothing. */
	private static boolean look(ServerLevel level, Mob mob) {
		Item lost = LOST.get(mob);
		if (lost != null && !mob.getMainHandItem().isEmpty()) {
			// it has armed itself some other way
			LOST.remove(mob);
			lost = null;
		}
		Map<String, Integer> ammo = mob.getAttachedOrElse(AMMO, Map.of());
		boolean wants = lost != null;
		boolean dry = lost != null;
		for (Kind kind : Kind.values()) {
			Integer left = ammo.get(kind.key);
			if (left != null && left < fullOf(mob, kind)) {
				wants = true;
				dry |= left == 0 && kind != Kind.SHOT && kind != Kind.BOMBS;
			}
		}
		if (!wants) {
			return false;
		}
		LivingEntity foe = Rulers.target(mob);
		boolean calm = foe == null || !foe.isAlive();
		restock(level, mob, ammo, calm);
		Entity nearest = null;
		double best = Double.MAX_VALUE;
		AABB around = mob.getBoundingBox().inflate(dry && calm ? FETCH_RANGE : PICKUP_RANGE);
		for (Entity e : level.getEntities(mob, around, e -> e instanceof ItemEntity || e instanceof AbstractArrow)) {
			ItemStack stack = stackOf(e);
			if (stack == null || !wanted(mob, ammo, lost, stack)) {
				continue;
			}
			double d = mob.distanceToSqr(e);
			if (d <= PICKUP_RANGE * PICKUP_RANGE) {
				take(level, mob, e, stack, lost);
				return true;
			}
			if (d < best) {
				best = d;
				nearest = e;
			}
		}
		if (nearest != null && mob.getNavigation().isDone()) {
			mob.getNavigation().moveTo(nearest, FETCH_SPEED);
		}
		return true;
	}

	/** The stack an item or a resting arrow/javelin/trident would give, or null if it can't be picked up. */
	private static @Nullable ItemStack stackOf(Entity e) {
		if (e instanceof ItemEntity item) {
			return item.hasPickUpDelay() ? null : item.getItem();
		}
		if (e instanceof AbstractArrow arrow && arrow.pickup == AbstractArrow.Pickup.ALLOWED
			&& arrow.getDeltaMovement().lengthSqr() < RESTING) {
			return arrow.getPickupItemStackOrigin();
		}
		return null;
	}

	private static boolean wanted(Mob mob, Map<String, Integer> ammo, @Nullable Item lost, ItemStack stack) {
		if (lost != null && stack.is(lost)) {
			return true;
		}
		for (Kind kind : Kind.values()) {
			Integer left = ammo.get(kind.key);
			if (left != null && left < fullOf(mob, kind) && is(stack, kind)) {
				return true;
			}
		}
		return false;
	}

	private static void take(ServerLevel level, Mob mob, Entity e, ItemStack stack, @Nullable Item lost) {
		if (lost != null && stack.is(lost)) {
			mob.setItemInHand(InteractionHand.MAIN_HAND, stack.copyWithCount(1));
			LOST.remove(mob);
			used(e, stack, 1);
		} else {
			for (Kind kind : Kind.values()) {
				Map<String, Integer> ammo = mob.getAttachedOrElse(AMMO, Map.of());
				Integer left = ammo.get(kind.key);
				if (left == null || !is(stack, kind)) {
					continue;
				}
				int room = fullOf(mob, kind) - left;
				int count = Math.min(room, e instanceof ItemEntity ? stack.getCount() : 1);
				if (count > 0) {
					give(mob, kind, count);
					used(e, stack, count);
				}
				break;
			}
		}
		mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.2F, 1.4F);
	}

	private static void used(Entity e, ItemStack stack, int count) {
		if (e instanceof ItemEntity item && stack.getCount() > count) {
			ItemStack rest = stack.copy();
			rest.shrink(count);
			item.setItem(rest);
		} else {
			e.discard();
		}
	}

	/**
	 * Folk and a village's guards fetch more from their stores while nothing is after them; a witch brews a potion back
	 * and a Hornkin scoops up magma the same way.
	 */
	private static void restock(ServerLevel level, Mob mob, Map<String, Integer> ammo, boolean calm) {
		if (!calm || !(mob instanceof Folk || mob instanceof Villager || mob instanceof net.minecraft.world.entity.monster.Witch
			|| mob instanceof com.jaymoskingdoms.nether.Hornkin)) {
			return;
		}
		long now = level.getGameTime();
		Long last = RESTOCKED.get(mob);
		if (last == null) {
			RESTOCKED.put(mob, now);
			return;
		}
		if (now - last < RESTOCK_TICKS) {
			return;
		}
		RESTOCKED.put(mob, now);
		for (Kind kind : List.of(Kind.values())) {
			if (kind == Kind.ROCKS || kind == Kind.KNIVES) {
				// rocks and knives are only ever picked up again
				continue;
			}
			Integer left = ammo.get(kind.key);
			if (left != null && left < fullOf(mob, kind)) {
				give(mob, kind, 1);
			}
		}
	}
}
