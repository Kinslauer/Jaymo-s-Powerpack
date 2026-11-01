package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.85) A guard who loses their weapon notices and does something about it. Folk fighters (guards, archers, raiders,
 * rulers, not casters) and villagers in a guard's or archer's job:
 * <ol>
 * <li>cry out ({@code talk.jaymoskingdoms.disarmed.*}) the moment their hand is empty;</li>
 * <li>go back for their own weapon if it's lying near;</li>
 * <li>otherwise swap: a weapon in the off hand comes to hand, and folk draw the
 * sidearm at their belt ({@link #SIDEARM}, once a life);</li>
 * <li>otherwise go for the nearest weapon on the ground ({@link #SEARCH_RANGE}), an archer preferring a bow;</li>
 * <li>and with none, fight with their fists (an archer with nothing to shoot closes in instead of standing at range).</li>
 * </ol>
 * Only someone seen armed is ever "disarmed": a fighter whose kit is empty-handed is left alone. A weapon picked up off
 * the ground drops again when they fall, so a player's own blade isn't lost to them.
 */
public final class Rearm {
	/** How often each fighter's hands are looked at (ticks). */
	static final int CHECK_EVERY = 10;
	/** How far they look for a weapon on the ground, and how near they must get to take it (blocks). */
	static final double SEARCH_RANGE = 16.0, REACH = 1.8;
	/** Walking speed to a weapon: folk (a multiple of their speed) and villagers (their brain's walk speed). */
	static final double FOLK_SPEED = 1.2;
	static final float VILLAGER_SPEED = 0.7F;
	/** Folk's sidearm, drawn once a life when nothing better is to hand. */
	static final Item SIDEARM = Items.STONE_SWORD;
	/** How many lines there are of each kind (lang {@code talk.jaymoskingdoms.disarmed.N}, {@code .rearmed.N}, ...). */
	static final int DISARMED_LINES = 4, SIDEARM_LINES = 2, PICKUP_LINES = 3;
	static final double SPEECH_RANGE = 16.0;

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private static final class State {
		/** Seen holding a weapon (so an empty hand means it was lost). */
		boolean armed;
		/** The hand is empty and they've cried out about it. */
		boolean lost;
		boolean sidearmDrawn;
		/** What they held last (to go back for it first). */
		@Nullable Item lastWeapon;
		/** The weapon on the ground they're making for. */
		@Nullable ItemEntity seeking;
	}

	private Rearm() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_EVERY != 0) {
				return;
			}
			for (ServerLevel level : server.getAllLevels()) {
				// gathered first: taking a weapon off the ground removes an entity, never while the level's are walked
				List<Mob> fighters = new ArrayList<>();
				for (Entity entity : level.getAllEntities()) {
					if (entity instanceof Mob mob && fighter(mob)) {
						fighters.add(mob);
					}
				}
				for (Mob mob : fighters) {
					tick(level, mob);
				}
			}
		});
	}

	/** One who fights with a weapon in hand: a folk fighter (not a caster), or a villager guard or archer. */
	static boolean fighter(Mob mob) {
		if (mob instanceof Folk folk) {
			return folk.combatant() && !Sorcery.isCaster(folk.role()) && !folk.isCarrying();
		}
		return mob instanceof Villager && Downed.isGuard(mob);
	}

	/** Whether {@code stack} is something to fight with: a blade, axe, spear, mace, trident, bow or crossbow. */
	public static boolean isWeapon(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof ProjectileWeaponItem || isMelee(stack) || stack.is(ItemTags.WEAPON_ENCHANTABLE));
	}

	/** A hand-to-hand weapon (an archer holding one fights hand to hand). */
	public static boolean isMelee(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
	}

	private static void tick(ServerLevel level, Mob mob) {
		if (!mob.isAlive() || Downed.isDowned(mob)) {
			return;
		}
		State state = STATES.computeIfAbsent(mob, m -> new State());
		ItemStack hand = mob.getMainHandItem();
		if (!hand.isEmpty()) {
			if (isWeapon(hand)) {
				state.armed = true;
				state.lastWeapon = hand.getItem();
			}
			state.lost = false;
			state.seeking = null;
			return;
		}
		if (!state.armed) {
			// never seen with a weapon: empty-handed is how they are
			return;
		}
		if (mob instanceof Villager villager && packed(villager)) {
			// (0.7.472) MCA puts a guard's weapon away in their pack between fights and draws it again for the next: it isn't
			// lost (taking it out here only had MCA put it back, crying "My weapon!" and "Not done yet!" over and over)
			state.lost = false;
			state.seeking = null;
			return;
		}
		if (!state.lost) {
			state.lost = true;
			say(level, mob, "disarmed", DISARMED_LINES, Speech.RED);
		}
		if (state.seeking != null && (!state.seeking.isAlive() || state.seeking.distanceToSqr(mob) > SEARCH_RANGE * SEARCH_RANGE * 2.25)) {
			state.seeking = null;
		}
		if (state.seeking == null) {
			// their own weapon first, if it's lying near
			state.seeking = nearest(level, mob, state.lastWeapon);
		}
		if (state.seeking == null && swap(level, mob, state)) {
			return;
		}
		if (state.seeking == null && !mob.isPassenger()) {
			state.seeking = nearest(level, mob, null);
		}
		if (state.seeking == null) {
			return;
		}
		if (mob.distanceToSqr(state.seeking) <= REACH * REACH) {
			take(level, mob, state);
		} else if (!(mob instanceof Folk)) {
			// a villager's brain walks it ({@link Fetch} walks folk)
			mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(state.seeking, VILLAGER_SPEED, 0));
		}
	}

	/** A weapon waits in {@code villager}'s pack. */
	private static boolean packed(Villager villager) {
		SimpleContainer pack = villager.getInventory();
		for (int i = 0; i < pack.getContainerSize(); i++) {
			if (isWeapon(pack.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	/** A weapon they already have comes to hand: the off hand's, a villager's pack's, or folk's sidearm. */
	private static boolean swap(ServerLevel level, Mob mob, State state) {
		ItemStack off = mob.getOffhandItem();
		if (isWeapon(off)) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, off);
			mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			drawn(level, mob);
			return true;
		}
		if (mob instanceof Villager) {
			// (a weapon in a villager's pack is MCA's to draw: see tick)
			return false;
		}
		if (!state.sidearmDrawn) {
			state.sidearmDrawn = true;
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(SIDEARM));
			mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			drawn(level, mob);
			return true;
		}
		return false;
	}

	private static void drawn(ServerLevel level, Mob mob) {
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.8F, 1.2F);
		say(level, mob, "rearmed.sidearm", SIDEARM_LINES, Speech.YELLOW);
	}

	/** The nearest weapon lying in reach of them ({@code only} that kind, if given); an archer's bow counts as nearer. */
	private static @Nullable ItemEntity nearest(ServerLevel level, Mob mob, @Nullable Item only) {
		boolean ranged = mob instanceof Folk folk && folk.ranged();
		ItemEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(SEARCH_RANGE))) {
			ItemStack stack = item.getItem();
			if (!item.isAlive() || !isWeapon(stack) || only != null && !stack.is(only) || item.isInLava()) {
				continue;
			}
			double score = mob.distanceTo(item);
			if (ranged == stack.getItem() instanceof ProjectileWeaponItem) {
				score -= 6.0;
			}
			if (score < bestScore && mob.getSensing().hasLineOfSight(item)) {
				bestScore = score;
				best = item;
			}
		}
		return best;
	}

	/** Takes up the weapon they reached: one of the stack, into their hand, to drop again if they fall. */
	private static void take(ServerLevel level, Mob mob, State state) {
		ItemEntity item = state.seeking;
		state.seeking = null;
		if (item == null || !item.isAlive()) {
			return;
		}
		ItemStack stack = item.getItem().copy();
		ItemStack one = stack.split(1);
		mob.take(item, 1);
		if (stack.isEmpty()) {
			item.discard();
		} else {
			item.setItem(stack);
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, one);
		mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.0F);
		say(level, mob, "rearmed.pickup", PICKUP_LINES, Speech.YELLOW);
	}

	private static void say(ServerLevel level, Mob mob, String kind, int lines, int color) {
		Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms." + kind + "." + mob.getRandom().nextInt(lines)), color, SPEECH_RANGE);
	}

	/** Whether {@code folk} is on its way to a weapon (its other goals that move it wait). */
	static boolean fetching(Folk folk) {
		State state = STATES.get(folk);
		return state != null && state.seeking != null && state.seeking.isAlive() && !folk.isPassenger();
	}

	/** Folk walk to the weapon they're making for (before fighting or shooting). */
	static final class Fetch extends Goal {
		private final Folk folk;
		private int repath;

		Fetch(Folk folk) {
			this.folk = folk;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return fetching(this.folk);
		}

		@Override
		public void start() {
			this.repath = 0;
		}

		@Override
		public void stop() {
			this.folk.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			State state = STATES.get(this.folk);
			ItemEntity item = state == null ? null : state.seeking;
			if (item == null) {
				return;
			}
			this.folk.getLookControl().setLookAt(item, 30.0F, 30.0F);
			if (this.folk.distanceToSqr(item) <= REACH * REACH && this.folk.level() instanceof ServerLevel level) {
				take(level, this.folk, state);
			} else if (--this.repath <= 0) {
				this.repath = 10;
				this.folk.getNavigation().moveTo(item, FOLK_SPEED);
			}
		}
	}
}
