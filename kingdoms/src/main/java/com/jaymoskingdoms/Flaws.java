package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * (0.7.420) Choosing a harder life, from the last page of the life story: up to {@link #MAX} flaws, each paying one free
 * attribute point ({@link LifeStory#freePoints}); three hard-road switches ({@link #WARY}, {@link #HUNTED}, {@link #RAGS})
 * applied once as the road lands; and "start from nothing" ({@link #NOTHING}), which gives up the story's attribute points
 * and gets them back doubled when the life's aim is finished ({@link LifeAims}). All of it is chosen on the client and
 * checked on the server ({@link #choose}); the flaws and the points given up are kept through death and cleared with the
 * story ({@link #clear}).
 */
public final class Flaws {
	// ---- tuning ----
	/** The most flaws one life takes, and the free attribute points each pays. */
	public static final int MAX = 2, POINTS_EACH = 1;
	/** Light sleeper: how far (blocks) a hostile wakes them, and how often it is looked for (ticks). */
	public static final double SLEEPER_RANGE = 16.0;
	static final int SLEEPER_EVERY = 20;
	/** Glass jaw: the share of a melee blow they take. */
	public static final float JAW_TAKEN = 1.25F;
	/** Poor swimmer: speed lost in water (a share), and one extra breath lost every this many ticks under water. */
	public static final double SWIM_SLOW = -0.25;
	public static final int SWIM_BREATH_EVERY = 2;
	/** Afraid of the dark: the light level at or under which it grips, the share of mining speed lost, and how long its effects last (ticks). */
	public static final int DARK_LIGHT = 3, DARK_EFFECT = 60, DARK_EVERY = 10;
	public static final double DARK_MINING = -0.25;
	/** Everyone is wary: reputation lost with every realm. */
	public static final int WARY_DROP = 15;
	/** Hunted: how many heavy crimes the first bounty is made of (each is {@link Bounties#PLAYER_HEAVY} emeralds). */
	public static final int HUNTED_CRIMES = 3;
	/** Start from nothing: how many times the points given up are paid back by a finished aim. */
	public static final int REPAY = 2;

	/** The four flaws; a bit each in the message sent from the screen. */
	public enum Flaw {
		LIGHT_SLEEPER, GLASS_JAW, POOR_SWIMMER, DARK_FEAR;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public int bit() {
			return 1 << ordinal();
		}
	}

	/** The switches, a bit each. */
	public static final int WARY = 1, HUNTED = 2, RAGS = 4, NOTHING = 8;
	private static final int ALL_SWITCHES = WARY | HUNTED | RAGS | NOTHING;
	private static final int ALL_FLAWS = (1 << Flaw.values().length) - 1;
	/** The attribute slots of {@link #GIVEN_UP}: one a stat, then the foundling's free point. */
	private static final int SLOTS = Stats.Stat.values().length + 1;

	/** The flaws taken (keys). Saved, kept through death, synced to its player (the stats screen counts the points). */
	public static final AttachmentType<List<String>> TAKEN = AttachmentRegistry.create(JaymosKingdoms.id("story_flaws"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));
	/** The points the story gave that "start from nothing" gave up, by {@link #SLOTS}; empty: none given up. Saved, kept through death, synced. */
	public static final AttachmentType<List<Integer>> GIVEN_UP = AttachmentRegistry.create(JaymosKingdoms.id("story_given_up"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));
	/** The hard-road switches still to be applied as the road lands ({@link #WARY} | {@link #HUNTED} | {@link #RAGS}). Saved, kept through death. */
	public static final AttachmentType<Integer> PENDING = AttachmentRegistry.create(JaymosKingdoms.id("story_hard_switches"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	private static final Identifier SWIM_ID = JaymosKingdoms.id("flaw_swimmer");
	private static final Identifier DARK_ID = JaymosKingdoms.id("flaw_dark");

	private Flaws() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Flaws::tick);
	}

	// ---- choosing ----------------------------------------------------------------------------------------------------

	/**
	 * Whether the choices sent from the screen are allowed: only known bits, at most {@link #MAX} flaws. The server's check;
	 * the client is not trusted.
	 */
	public static boolean valid(int flaws, int switches) {
		return flaws >= 0 && switches >= 0 && (flaws & ~ALL_FLAWS) == 0 && (switches & ~ALL_SWITCHES) == 0 && Integer.bitCount(flaws) <= MAX;
	}

	/**
	 * (From {@link LifeStory#live}, once the story is set down and checked) Takes the flaws, keeps the hard-road switches for
	 * the road to land, and (start from nothing) gives up the story's points, remembering how many. Call after the story is saved.
	 */
	static void choose(ServerPlayer player, int flaws, int switches) {
		List<String> taken = new ArrayList<>();
		for (Flaw flaw : Flaw.values()) {
			if ((flaws & flaw.bit()) != 0) {
				taken.add(flaw.key());
			}
		}
		if (taken.isEmpty()) {
			player.removeAttached(TAKEN);
		} else {
			player.setAttached(TAKEN, List.copyOf(taken));
		}
		int hard = switches & (WARY | HUNTED | RAGS);
		if (hard == 0) {
			player.removeAttached(PENDING);
		} else {
			player.setAttached(PENDING, hard);
		}
		player.removeAttached(GIVEN_UP);
		if ((switches & NOTHING) != 0) {
			// the exact points, counted before they are given up
			List<Integer> given = new ArrayList<>();
			for (Stats.Stat stat : Stats.Stat.values()) {
				given.add(LifeStory.storyPoints(player, stat));
			}
			given.add(LifeStory.storyFreePoints(player));
			player.setAttached(GIVEN_UP, List.copyOf(given));
		}
	}

	/** (From {@link LifeStory#road}) A road told after the creator can add points of its own to what was given up. */
	static void giveUpMore(ServerPlayer player, Stats.Stat stat, int n) {
		List<Integer> given = player.getAttached(GIVEN_UP);
		if (given == null || given.size() != SLOTS || n <= 0) {
			return;
		}
		List<Integer> now = new ArrayList<>(given);
		now.set(stat.ordinal(), now.get(stat.ordinal()) + n);
		player.setAttached(GIVEN_UP, List.copyOf(now));
	}

	/** The /kingdom story reset: no flaws, no switches, nothing given up. */
	static void clear(ServerPlayer player) {
		player.removeAttached(TAKEN);
		player.removeAttached(GIVEN_UP);
		player.removeAttached(PENDING);
		dropModifiers(player);
	}

	// ---- reading -----------------------------------------------------------------------------------------------------

	/** Whether {@code player} took {@code flaw}. */
	public static boolean has(Player player, Flaw flaw) {
		List<String> taken = player.getAttached(TAKEN);
		return taken != null && taken.contains(flaw.key());
	}

	/** The free attribute points {@code player}'s flaws paid. */
	public static int points(Player player) {
		List<String> taken = player.getAttached(TAKEN);
		return taken == null ? 0 : Math.min(MAX, taken.size()) * POINTS_EACH;
	}

	/** Whether {@code player} gave up the story's points. */
	public static boolean gaveUp(Player player) {
		List<Integer> given = player.getAttached(GIVEN_UP);
		return given != null && !given.isEmpty();
	}

	/** (From {@link LifeStory#points}) What a finished aim pays back in {@code slot} ({@link Stats.Stat#ordinal}, or the free-point slot): double what was given up. */
	static int repaid(Player player, int slot) {
		List<Integer> given = player.getAttached(GIVEN_UP);
		if (given == null || slot < 0 || slot >= given.size() || !LifeAims.finished(player)) {
			return 0;
		}
		return given.get(slot) * REPAY;
	}

	/** The free-point slot of {@link #GIVEN_UP}. */
	static int freeSlot() {
		return SLOTS - 1;
	}

	// ---- the hard-road switches, as the road lands --------------------------------------------------------------------

	/** Whether {@code player} is to land with nothing but rags (the road's kit is skipped). */
	static boolean rags(ServerPlayer player) {
		Integer pending = player.getAttached(PENDING);
		return pending != null && (pending & RAGS) != 0;
	}

	/** (From {@link LifeStory#arrive}, once they have landed) Everyone is wary; a price on their head. Done once. */
	static void landed(ServerPlayer player) {
		Integer pending = player.getAttached(PENDING);
		if (pending == null) {
			return;
		}
		player.removeAttached(PENDING);
		MinecraftServer server = player.level().getServer();
		if ((pending & WARY) != 0) {
			for (Faction realm : Factions.all(server)) {
				if (realm.type().isRealm() && !realm.fallen()) {
					Reputation.change(player, realm, -WARY_DROP);
				}
			}
		}
		if ((pending & HUNTED) != 0) {
			// a bounty from the nearest realm's court, and a knife looking for them (a contract, laid on from now)
			Faction court = Factions.realmNear(server, player.blockPosition(), 1.0E6);
			if (court == null) {
				for (Faction f : Factions.all(server)) {
					if (f.type().isRealm() && !f.fallen()) {
						court = f;
						break;
					}
				}
			}
			if (court != null) {
				for (int i = 0; i < HUNTED_CRIMES; i++) {
					Bounties.crimeBy(player, player.level(), court, true, "story");
				}
			}
			Assassins.laidOn(player, "rogue");
		}
	}

	// ---- the flaws' effects -------------------------------------------------------------------------------------------

	/** (From the hurt mixin) A glass jaw takes a quarter more from a blow struck in melee. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(target instanceof ServerPlayer player) || !has(player, Flaw.GLASS_JAW)) {
			return amount;
		}
		if (source.getEntity() instanceof LivingEntity && source.getEntity() == source.getDirectEntity() && !source.is(DamageTypeTags.IS_PROJECTILE)) {
			return amount * JAW_TAKEN;
		}
		return amount;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<String> taken = player.getAttached(TAKEN);
			if (taken == null || taken.isEmpty()) {
				dropModifiers(player);
				continue;
			}
			if (taken.contains(Flaw.LIGHT_SLEEPER.key()) && now % SLEEPER_EVERY == 0) {
				lightSleeper(player);
			}
			swimmer(player, taken.contains(Flaw.POOR_SWIMMER.key()), now);
			if (now % DARK_EVERY == 0) {
				dark(player, taken.contains(Flaw.DARK_FEAR.key()), now);
			}
		}
	}

	/** Light sleeper: a hostile within {@link #SLEEPER_RANGE} wakes them. */
	private static void lightSleeper(ServerPlayer player) {
		if (!player.isSleeping() || player.isCreative() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		// (only one that could come for them: awake, and after them or with a clear line to the bed; not a caged or tame one behind walls)
		List<Monster> near = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(SLEEPER_RANGE),
			m -> m.isAlive() && !m.isNoAi() && (m.getTarget() == player || m.hasLineOfSight(player)));
		if (!near.isEmpty()) {
			player.stopSleepInBed(true, true);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.flaw.light_sleeper"));
		}
	}

	/** Poor swimmer: slower in water, and the breath goes faster under it. */
	private static void swimmer(ServerPlayer player, boolean flawed, long now) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean slow = flawed && player.isInWater() && !player.isCreative() && !player.isSpectator();
		if (!slow) {
			if (speed.getModifier(SWIM_ID) != null) {
				speed.removeModifier(SWIM_ID);
			}
			return;
		}
		if (speed.getModifier(SWIM_ID) == null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SWIM_ID, SWIM_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (player.isUnderWater() && now % SWIM_BREATH_EVERY == 0 && player.getAirSupply() > 0 && player.getAirSupply() < player.getMaxAirSupply()) {
			player.setAirSupply(player.getAirSupply() - 1);
		}
	}

	/** Afraid of the dark: in the dark, with no light in hand, slower, a darkened screen and slower mining. */
	private static void dark(ServerPlayer player, boolean flawed, long now) {
		AttributeInstance mining = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
		boolean afraid = flawed && !player.isCreative() && !player.isSpectator()
			&& player.level().getMaxLocalRawBrightness(player.blockPosition()) <= DARK_LIGHT && !holdsLight(player);
		if (!afraid) {
			if (mining != null && mining.getModifier(DARK_ID) != null) {
				mining.removeModifier(DARK_ID);
			}
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DARK_EFFECT, 0, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARK_EFFECT, 0, true, false, false));
		if (mining != null && mining.getModifier(DARK_ID) == null) {
			mining.addOrUpdateTransientModifier(new AttributeModifier(DARK_ID, DARK_MINING, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Whether a lit block (a torch, a lantern) is in either hand. */
	private static boolean holdsLight(ServerPlayer player) {
		for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (stack.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().getLightEmission() > 0) {
				return true;
			}
		}
		return false;
	}

	/** Takes the flaws' attribute modifiers off. */
	private static void dropModifiers(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.getModifier(SWIM_ID) != null) {
			speed.removeModifier(SWIM_ID);
		}
		AttributeInstance mining = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
		if (mining != null && mining.getModifier(DARK_ID) != null) {
			mining.removeModifier(DARK_ID);
		}
	}
}
