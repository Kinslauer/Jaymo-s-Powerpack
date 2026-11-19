package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.Skills.Perk;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Andy's skill-tree locks: abilities every player used to have from the start are learned in the tree now, so the tree is
 * worth using. A locked ability can't be used at all; trying it puts a line on the action bar naming the perk, with a dull
 * sound, the way the boss-locked anvils refuse ({@link Knowledge}). Only players are locked: villagers, guards and crooks
 * go on doing these things, and a villager near who sees you fumble one says they had to learn it too.
 * <p>
 * A class keeps its own signature thing free: a Rider the whistle and the tricks, a Monk the first boxing style, a Mage
 * reading scrolls, a Thief pickpocketing and lockpicking. Creative players are never stopped.
 * <p>
 * Jaymo's Powers asks by the lock's name ({@link #has(Player, String)}, {@link #refuse(Player, String)}), by reflection;
 * without Kingdoms nothing there is locked. Both sides can ask: the tree's ranks and the class are synced to their player.
 */
public final class TreeLocks {
	// ---- tuning ----
	/** How often the same player is told again (ticks), so a held key doesn't spam the action bar. */
	static final int TELL_EVERY = 40;
	/** How far a villager sees you fumble, and how long before anyone remarks again (ticks). */
	static final double REMARK_RANGE = 8.0;
	static final int REMARK_EVERY = 20 * 90;
	/** The chance a villager who sees it says something. */
	static final float REMARK_CHANCE = 0.5F;
	/** Remark lines (talk.jaymoskingdoms.tree_lock.remark.N). */
	static final int REMARK_LINES = 8;

	/** Each locked ability: the perk (and rank) that opens it, and the classes that have it free. */
	public enum Lock {
		// moving
		SLIDE(Perk.SLIDE, 1),
		LONG_JUMP(Perk.LONG_JUMP, 1),
		CHARGE_JUMP(Perk.CHARGE_JUMP, 1),
		UP_RUN(Perk.WALL_RUNNER, 1),
		TRICK_JUMP(Perk.FREERUNNER, 1),
		SWIM_BURST(Perk.STRONG_SWIMMER, 1),
		BREACH(Perk.STRONG_SWIMMER, 1),
		SHIELD_VAULT(Perk.SHIELD_VAULT, 1),
		PARAGLIDER(Perk.SKYFARER, 1),
		BALLOON(Perk.SKYFARER, 2),
		// fighting
		AIM_PARTS(Perk.AIM_PARTS, 1),
		SHIELD_THROW(Perk.SHIELD_THROW, 1),
		SHIELD_MOVES(Perk.SHIELDBEARER, 1),
		SHOVE(Perk.SHOVE, 1),
		SWAT(Perk.SWAT, 1),
		TWO_BLADES(Perk.TWO_BLADES, 1),
		THROWER(Perk.THROWER, 1),
		SWEEP_KICK(Perk.SWEEP_KICK, 1),
		AIR_COMBO(Perk.AIR_COMBO, 1),
		SECOND_WIND(Perk.SECOND_WIND, 1),
		BATTLE_CRY(Perk.WAR_CRY, 1),
		BATTLE_RHYTHM(Perk.BATTLE_RHYTHM, 1),
		BOXING(Perk.BOXER, 1),
		// carrying, crime and people
		HAUL_BOUND(Perk.HAUL_BOUND, 1),
		PICKPOCKET(Perk.LIGHT_FINGERS, 1, PlayerClass.THIEF),
		LOCKPICK(Perk.LOCKPICKING, 1, PlayerClass.THIEF),
		ORDERS(Perk.COMMAND, 1),
		// healing and magic
		MEDITATE(Perk.MEDITATION, 1),
		READ_SCROLL(Perk.READ_SCROLLS, 1, PlayerClass.MAGE),
		SORCERY(Perk.SORCERY, 1),
		FORBIDDEN(Perk.FORBIDDEN_ARTS, 1),
		// riding
		WHISTLE(Perk.HORSEMAN, 1, PlayerClass.RIDER),
		TRICKS(Perk.HORSEMAN, 2, PlayerClass.RIDER),
		// bloods and the night
		BLOOD(Perk.BLOOD_AWAKENING, 1),
		LIFELEECH(Perk.LIFELEECH, 1),
		NIGHT_SENSE(Perk.NIGHT_SENSE, 1),
		NIGHT_POUND(Perk.NIGHT_POUND, 1),
		BAT_WINGS(Perk.BAT_WINGS, 1),
		// the trades
		SMITH(Perk.SMITH, 1),
		BREWER(Perk.BREWER, 1),
		SAWYER(Perk.SAWYER, 1),
		ANGLER(Perk.ANGLER, 1),
		ALCHEMIST(Perk.ALCHEMIST, 1),
		SMELTER(Perk.SMELTER, 1),
		MILLWRIGHT(Perk.MILLWRIGHT, 1),
		GEMCUTTER(Perk.GEMCUTTER, 1),
		WHALER(Perk.WHALER, 1),
		RIGWRIGHT(Perk.RIGWRIGHT, 1);

		public final Perk perk;
		public final int rank;
		final Set<PlayerClass> free;

		Lock(Perk perk, int rank, PlayerClass... free) {
			this.perk = perk;
			this.rank = rank;
			this.free = free.length == 0 ? Set.of() : EnumSet.of(free[0], free);
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final Map<String, Lock> BY_KEY = new HashMap<>();
	/** When each player was last told, and when anyone near was last remarked to. */
	private static final Map<UUID, Long> TOLD = new HashMap<>(), TOLD_CLIENT = new HashMap<>(), REMARKED = new HashMap<>();

	/** The stations each trade opens (block ids, any mod's; a mod that isn't loaded just never matches). */
	static final Map<String, Lock> STATIONS = new HashMap<>();

	static {
		for (Lock lock : Lock.values()) {
			BY_KEY.put(lock.key(), lock);
		}
		station(Lock.SMITH, "jaymospowers:forge_hearth", "jaymospowers:bellows", "jaymospowers:stone_anvil", "jaymospowers:field_anvil",
			"jaymospowers:smithing_anvil");
		station(Lock.SMELTER, "jaymospowers:seared_melter", "jaymospowers:smeltery_controller", "jaymospowers:foundry_controller",
			"jaymospowers:scorched_alloyer", "jaymospowers:seared_channel", "jaymospowers:seared_faucet", "jaymospowers:seared_drain",
			"jaymospowers:scorched_drain", "jaymospowers:seared_tank", "jaymospowers:casting_table", "jaymospowers:casting_basin");
		station(Lock.ALCHEMIST, "jaymospowers:crucible", "jaymospowers:alembic", "jaymospowers:brewers_alembic", "jaymospowers:infusion_matrix");
		station(Lock.BREWER, "jaymoskitchen:cask", "jaymoskitchen:still");
		station(Lock.MILLWRIGHT, "jaymoskitchen:windmill", "jaymoskitchen:waterwheel", "jaymoskitchen:gearbox", "jaymoskitchen:grindstone",
			"jaymoskitchen:press", "jaymoskitchen:horse_press", "jaymoskitchen:chopper", "jaymoskitchen:bloody_chopper",
			"jaymoskitchen:machine_filler", "jaymoskitchen:machine_part", "jaymoskitchen:power_part");
		station(Lock.GEMCUTTER, "jaymospowers:lapidary_wheel", "jaymospowers:jewellers_bench");
		station(Lock.RIGWRIGHT, "jaymospowers:rig_forge", "jaymospowers:dead_rig", "jaymospowers:fallen_rig");
		station(Lock.WHALER, "jaymoskingdoms:tryworks");
	}

	private static void station(Lock lock, String... ids) {
		for (String id : ids) {
			STATIONS.put(id, lock);
		}
	}

	private TreeLocks() {
	}

	static void register() {
		// a station refuses whoever hasn't learned its trade (anyone may still build one, if they've learned to make it)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSecondaryUseActive() && !player.getItemInHand(hand).isEmpty()) {
				// sneaking with something in hand places it against the station, as ever
				return InteractionResult.PASS;
			}
			Lock lock = STATIONS.get(BuiltInRegistries.BLOCK.getKey(level.getBlockState(hit.getBlockPos()).getBlock()).toString());
			if (lock == null || has(player, lock)) {
				return InteractionResult.PASS;
			}
			refuse(player, lock);
			return InteractionResult.FAIL;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
	}

	/**
	 * (From {@code TreeLockCraftMixin}) whether {@code player} may take {@code result} out of a crafting grid holding
	 * {@code grid}: a trade's stations are made only by those who learned it, and a saw cuts only for a sawyer.
	 */
	public static boolean mayCraft(Player player, ItemStack result, Iterable<ItemStack> grid) {
		Lock lock = STATIONS.get(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
		if (lock == null) {
			for (ItemStack stack : grid) {
				if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("jaymospowers:saw")) {
					lock = Lock.SAWYER;
					break;
				}
			}
		}
		return lock == null || check(player, lock);
	}

	/** The lock named {@code key} ("slide", "pickpocket"), or null. */
	public static @Nullable Lock lock(String key) {
		return BY_KEY.get(key.toLowerCase(Locale.ROOT));
	}

	/** Whether {@code player} has learned what {@code lock} opens (or needs not: creative, or their class has it free). */
	public static boolean has(Player player, Lock lock) {
		if (player.isCreative() || player.isSpectator()) {
			return true;
		}
		if (!lock.free.isEmpty()) {
			for (PlayerClass cls : Classes.both(player)) {
				if (lock.free.contains(cls)) {
					return true;
				}
			}
		}
		return Skills.of(player).rank(lock.perk) >= lock.rank;
	}

	/** {@link #has(Player, Lock)} by name, for Jaymo's Powers (by reflection); an unknown name is never locked. */
	public static boolean has(Player player, String key) {
		Lock lock = lock(key);
		return lock == null || has(player, lock);
	}

	/**
	 * Tells {@code player} {@code lock} isn't learned: a line on the action bar naming the perk, a dull sound (at most every
	 * {@link #TELL_EVERY} ticks), and now and then a villager near remarks on it. On the client it shows the line itself.
	 */
	public static void refuse(Player player, Lock lock) {
		// each side keeps its own (a single-player world runs both in one game)
		Map<UUID, Long> told = player.level().isClientSide() ? TOLD_CLIENT : TOLD;
		long now = player.level().getGameTime();
		Long last = told.get(player.getUUID());
		if (last != null && now - last < TELL_EVERY && now >= last) {
			return;
		}
		told.put(player.getUUID(), now);
		Component perk = Component.translatable("skill.jaymoskingdoms.perk." + lock.perk.key());
		Component line = lock.rank > 1
			? Component.translatable("message.jaymoskingdoms.tree_lock.rank", perk, lock.rank)
			: Component.translatable("message.jaymoskingdoms.tree_lock.locked", perk);
		if (player instanceof ServerPlayer sp) {
			sp.sendOverlayMessage(line);
			sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 0.7F);
			remark(sp);
		} else if (player.level().isClientSide()) {
			player.sendOverlayMessage(line);
			player.playSound(SoundEvents.VILLAGER_NO, 0.5F, 0.7F);
		}
	}

	/** {@link #refuse(Player, Lock)} by name, for Jaymo's Powers (by reflection). */
	public static void refuse(Player player, String key) {
		Lock lock = lock(key);
		if (lock != null) {
			refuse(player, lock);
		}
	}

	/** Tells {@code player} a path wants a path perk first (any pillar's). */
	static void refusePath(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tree_lock.path"));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 0.7F);
	}

	/** Whether {@code player} may use {@code lock}; if not, tells them so. */
	public static boolean check(Player player, Lock lock) {
		if (has(player, lock)) {
			return true;
		}
		refuse(player, lock);
		return false;
	}

	/**
	 * (Andy: "they comment that they had to learn, so canonically everyone has to do these things") a villager or townsman
	 * near who can see you says so, in words anyone follows: they had to learn it too.
	 */
	private static void remark(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = REMARKED.get(player.getUUID());
		if (last != null && now - last < REMARK_EVERY && now >= last || level.getRandom().nextFloat() >= REMARK_CHANCE) {
			return;
		}
		LivingEntity best = null;
		double bestD = REMARK_RANGE * REMARK_RANGE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REMARK_RANGE),
			e -> (e instanceof Villager || e instanceof Folk) && e.isAlive() && !e.isSleeping() && !e.isBaby())) {
			double d = e.distanceToSqr(player);
			if (d < bestD && e.hasLineOfSight(player)) {
				best = e;
				bestD = d;
			}
		}
		if (best == null) {
			return;
		}
		REMARKED.put(player.getUUID(), now);
		Speech.sayPlainly(level, best, player,
			Component.translatable("talk.jaymoskingdoms.tree_lock.remark." + level.getRandom().nextInt(REMARK_LINES)), Speech.WHITE);
	}

	/** Whether {@code perk} is one {@code player} can't see in the tree at all (the Night page, before they're turned). */
	public static boolean hidden(Player player, Perk perk) {
		if (perk.pillar != Skills.Pillar.STEALTH || perk.tier < 20) {
			return false;
		}
		NightGifts.Gift g = NightGifts.gift(player);
		return g == null || !g.blood() && !g.wolf() || perk == Perk.BAT_WINGS && !g.blood();
	}

	static void forget(UUID id) {
		TOLD.remove(id);
		REMARKED.remove(id);
	}
}
