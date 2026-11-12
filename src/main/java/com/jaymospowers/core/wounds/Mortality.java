package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.Lodged;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterAfterlife;
import com.jaymospowers.power.zfighter.ZFighterRealism;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.26) Death by cause, from Jungxvv's ideas (Andy, 2026-11-04: "show me what you can build from this first", then the
 * answers in the plan doc). A player's death is a roll on <b>what put them down</b> ({@link Cause}): lose it and the
 * character is over for good ({@link #FINAL}: their body stays where it fell, and they are born again through the life story,
 * {@link #endLife}); win it and they are <b>left for dead</b>: the death is turned away and they wake out in the wild a little
 * later ({@link #wake}), with an injury that fits the cause. Some of those injuries are timers that kill unless cured
 * ({@link Afflictions}); some stay for life ({@link Scars}).
 * <ul>
 * <li>The cause is the blow that downed them ({@link Downed} hands it over as they die) unless the last blow has a cause of its own
 * (lava, drowning, a fall, the void): crawl into lava after a zombie downed you and it was the lava.</li>
 * <li>Andy's answers: final means born again through the story, with every start unlocked; a second defeat while any injury is
 * still open (an unset break, an arrow still in you, a timer running) is always final ({@link #openInjury}); an execution is final
 * only when a player or a boss does it; a player's kill rolls like anything else.</li>
 * <li>A boss spares one far too weak to matter ({@link #beneath}): they are dumped far off ({@link #SPARED_MIN}).</li>
 * <li>Server setting: {@code death_by_cause} and {@code final_odds} in {@code config/jaymospowers-server.properties}
 * ({@link ZFighterRealism}); off, deaths are as they were.</li>
 * </ul>
 * Survival only (creative never dies here), not one of the dead in the Other World (it holds its own), and a totem in hand is
 * left to do its work.
 */
public final class Mortality {
	// ---- tuning ----
	/** How far (blocks) from where they fell a survivor wakes: most causes, and one a boss spared. */
	static final int NEAR_MIN = 40, NEAR_MAX = 120;
	static final int SPARED_MIN = 300, SPARED_MAX = 800;
	/** How far a drowned one may wash up (blocks), looked for in loaded ground. */
	static final int SHORE_MAX = 160;
	/** No town this near (blocks: no villager) a waking spot. */
	static final double TOWN_CLEAR = 48.0;
	/** Tries at a spot in loaded ground, then far spots loaded for it, before they simply wake where they fell. */
	static final int NEAR_TRIES = 24, FAR_TRIES = 4;
	/** How long they lie out cold before they are moved (ticks), and after waking where they're put. */
	static final int OUT_BEFORE = 60, OUT_AFTER = 100;
	/** The share of their health a survivor wakes with. */
	static final float WAKE_HEALTH = 0.3F;
	/** A boss spares you when your strength ({@link #strength}) is less than this share of its own. */
	static final float MERCY_SHARE = 0.25F;
	/** How long after respawning (ticks) one whose life ended is told the story anew (past the loading screen). */
	static final long REBIRTH_DELAY = 40L;
	/** A blast's chance to take a limb off outright. */
	static final float BLAST_SEVER = 0.25F;
	/**
	 * (0.20.83) Andy: "you should always get a black screen wake up roll for your first two deaths". How many times a life is
	 * always left for dead (or turned) before the cause's roll ({@link Cause#chance}) can end it. Only losing your head, the
	 * void, or an execution by a player or a boss ends it sooner.
	 */
	static final int SURE_WAKES = 2;
	/**
	 * (0.21.7) Andy: "your body should persist in the world until come to, that way players can pick each other up and carry
	 * each other to safety. if you do not press anything, you will not get the respawn roll". One left for dead lies where
	 * they fell until they hold sneak this long (ticks) to come to by themselves (the wake roll), or someone brings them round.
	 */
	static final int COME_TO_TICKS = 40;
	/** (0.21.7) How long the body lies before the wake roll once they've chosen to come to (ticks), and the bar's length. */
	static final int COME_TO_AFTER = 20, COME_TO_BAR = 12;
	/** (0.21.7) How long one left for dead is held out cold at most while lying (ticks): long enough never to end by itself. */
	static final int LIE_TICKS = 20 * 60 * 60 * 24;
	/** (0.21.7) No hostile this near (blocks) the body: safe enough to bring one left for dead round. */
	static final double SAFE_RANGE = 12.0;
	/** (0.21.7) How far round a lying body (blocks) mobs are made to lose interest in it, and how often (ticks). */
	static final double IGNORE_RANGE = 32.0;
	/**
	 * Andy: "I was locked in the revive screen". The most a wake already under way (dying mid-turn, turned, or come to) may
	 * run past its moment before it's done where they lie, whatever was holding it up (ticks).
	 */
	static final int WAKE_LIMIT = 20 * 30;

	/** What put a player down, and its chance (0 to 1) that the death is final. */
	public enum Cause {
		BOSS(0.7F), ZOMBIE(0.9F), SKELETON(0.5F), BLAST(0.2F), VENOM(0.0F), POISON(0.7F), WITHER(0.0F), FIRE(0.5F), DROWNING(0.2F),
		FALL(0.1F), LAVA(1.0F), SEA(1.0F), ENVIRONMENT(0.5F), PLAYER(0.5F), OTHER(0.5F), VOID(1.0F), BEHEADED(1.0F);

		public final float chance;

		Cause(float chance) {
			this.chance = chance;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * The character is over: set as the final death is let through, kept through the respawn, and cleared once they are born
	 * again ({@link #endLife}). The value is the cause's key.
	 */
	public static final AttachmentType<String> FINAL = AttachmentRegistry.create(JaymosPowers.id("mortality_final"),
		// ((0.20.83) told to its player too: their death screen offers to "Reincarnate", not to respawn)
		builder -> builder.persistent(Codec.STRING).copyOnDeath()
			.syncWith(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.targetOnly()));

	/**
	 * (0.20.83) How many times this life has been left for dead (or turned): the first {@link #SURE_WAKES} always are. Kept
	 * through anything but the end of the life ({@link #endLife}).
	 */
	public static final AttachmentType<Integer> WOKEN = AttachmentRegistry.create(JaymosPowers.id("mortality_woken"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/**
	 * Left for dead and not mended yet: while this lasts and an injury is still open ({@link #openInjury}), the next defeat is
	 * final. It goes once nothing is open any more.
	 */
	public static final AttachmentType<Boolean> SURVIVOR = AttachmentRegistry.create(JaymosPowers.id("mortality_survivor"),
		builder -> builder.persistent(Codec.BOOL));

	/**
	 * Other tests of what counts as a boss (Kingdoms adds its kings, warlords, champions, dragons and Giants: its boss bars).
	 * Vanilla's bosses are known here already.
	 */
	public static final List<Predicate<Entity>> BOSS_TESTS = new ArrayList<>();

	/**
	 * (0.20.26) Who takes one left for dead captive instead of leaving them in the wild (Kingdoms: orcs, goblins, ogres, pirates,
	 * the camps' raiders, outlaws; Andy: "if you are kidnapped by your sentient murderers ... theyll have it hidden or even wear
	 * it"). Given the player and whoever downed them; the first to name a place has them there (it takes their gear itself).
	 */
	public static final List<java.util.function.BiFunction<ServerPlayer, Entity, net.minecraft.core.@Nullable GlobalPos>> CAPTORS = new ArrayList<>();

	/**
	 * (0.20.83) Who takes one left for dead in (Kingdoms: a friendly village, fiefdom or kingdom near where they fell; Andy:
	 * "taken in by a village or nearby villagers"), asked after {@link #CAPTORS}. Given the player and whoever downed them (or
	 * null); the first to name a place has them carried there, their wounds dressed and their breaks splinted.
	 */
	public static final List<java.util.function.BiFunction<ServerPlayer, @Nullable Entity, net.minecraft.core.@Nullable GlobalPos>> SHELTERS = new ArrayList<>();

	/**
	 * Where one left for dead wakes in a berth of their own (Kingdoms: a bed aboard a ship, wherever she is now; Andy: "it
	 * should spawn you wherever the boat is when it is still there"), asked after {@link #CAPTORS} and before
	 * {@link #SHELTERS}. Given the player; the first to name a place has them wake there.
	 */
	public static final List<java.util.function.Function<ServerPlayer, net.minecraft.core.@Nullable GlobalPos>> BERTHS = new ArrayList<>();

	/**
	 * (0.20.27) Deaths that turn one instead of ending them (Kingdoms: the Hollow; Andy: "the death method that killed them turns
	 * them into one of those specific hollows, like enthralled by a vampire, or maybe zombified by a witch"). Given the player and
	 * whoever killed them, each may roll and hand back what to do to them as they rise; the first to hand one back has them. A
	 * turned death is never final: they rise where they fell (the drowned on the shore), with no new injury.
	 */
	public static final List<java.util.function.BiFunction<ServerPlayer, Entity, @Nullable Runnable>> TURNINGS = new ArrayList<>();

	/**
	 * (0.21.42) Those already turning when they die (Kingdoms: a Hollow's blight, a vampire's bite before nightfall; Andy:
	 * "players that get infected and are turning into hollow, shouldnt reincarnate the way everyone else does ... no prompt
	 * on death, just a longer black screen, an appropriate vignette, and a wakeup as a full turned hollow"). Given the
	 * player, each may hand back the vignette's colour (RGB) and what finishes the turning as they rise; the first to answer
	 * has them. Asked before {@link #TURNINGS}; such a death is never final (no head, the void, lava or an execution aside),
	 * has no come-to bar, and doesn't use up one of the sure wakes.
	 */
	public static final List<java.util.function.Function<ServerPlayer, java.util.Map.@Nullable Entry<Integer, Runnable>>> MID_TURNS = new ArrayList<>();

	/** (0.21.42) The black before one dying mid-turn rises (ticks; a turned kill's is {@link #OUT_BEFORE}). */
	static final int TURN_DARK = 20 * 14;

	/**
	 * (0.21.66) Andy: "you pass out and then turn as well, make sure the eyes animate closed too". A turning finishing in one
	 * still alive (Kingdoms: the Hollow's blight run its course, a vampire's bite at nightfall): out cold where they stand for
	 * {@code ticks}, the same vignette ({@code tint}, RGB) and a line of its own on the black as dying mid-turn
	 * ({@link #MID_TURNS}). The caller finishes the turning once the black is over. False if they couldn't be put out.
	 */
	public static boolean turnOut(ServerPlayer player, int tint, int ticks) {
		if (!player.isAlive() || WAKES.containsKey(player.getUUID())) {
			return false;
		}
		player.closeContainer();
		if (player.isPassenger()) {
			player.stopRiding();
		}
		if (!Downed.knockOut(player.level(), player, ticks, false, "message.jaymospowers.mortality.turning_out", false)) {
			return false;
		}
		player.setAttached(WAKE_LINE, "message.jaymospowers.mortality.turning_out");
		player.setAttached(TURN_TINT, tint);
		JaymosPowers.LOGGER.info("{} passed out turning", player.getScoreboardName());
		return true;
	}

	/**
	 * (0.21.42) The colour (RGB) of the vignette on the black while one dying mid-turn lies turning ({@link #MID_TURNS}).
	 * Synced to them only; gone once they're up, and not kept over a log-out.
	 */
	public static final AttachmentType<Integer> TURN_TINT = AttachmentRegistry.create(JaymosPowers.id("mortality_turn_tint"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.INT,
			net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.targetOnly()));

	static final Identifier PHASE = JaymosPowers.id("mortality");
	private static final TicketType WAKING = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

	/** What downed each one who is dying now ({@link Downed} hands it over before it forgets), and who was executed. */
	private static final Map<LivingEntity, DamageSource> FELLED = new WeakHashMap<>();
	private static final Map<LivingEntity, Boolean> EXECUTED = new WeakHashMap<>();

	/** A survivor on their way to where they wake. */
	private static final class Wake {
		final Cause cause;
		final @Nullable String sub;
		final ResourceKey<Level> dimension;
		final BlockPos fell;
		final long moveAt;
		final @Nullable Entity by;
		/** (0.20.27) What they rise as (see {@link #TURNINGS}), or null for a plain wake. */
		final @Nullable Runnable turn;
		/** (0.21.42) Died mid-turn ({@link #MID_TURNS}): the longer black, its vignette, and the turned wake line. */
		boolean midTurn;
		int tries;
		/** (0.21.7) Whether they've chosen to come to (the roll goes ahead), and how long they've held sneak towards it. */
		boolean chosen;
		int held;
		/** (0.21.7) Told they're being carried (true) or lying (false), last; null: nothing told yet. */
		@Nullable Boolean carried;
		long moveAtChosen;
		@Nullable BlockPos far;
		@Nullable CompletableFuture<?> loading;

		Wake(Cause cause, @Nullable String sub, ResourceKey<Level> dimension, BlockPos fell, long moveAt, @Nullable Entity by,
			@Nullable Runnable turn) {
			this.by = by;
			this.turn = turn;
			this.cause = cause;
			this.sub = sub;
			this.dimension = dimension;
			this.fell = fell;
			this.moveAt = moveAt;
		}
	}

	private static final Map<UUID, Wake> WAKES = new HashMap<>();
	/** Those whose life ended and the tick to tell their story anew. */
	private static final Map<UUID, Long> REBIRTHS = new HashMap<>();

	private Mortality() {
	}

	static void register() {
		// after the downed state has had its say (it puts players down instead of letting them die)
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Downed.LAST, PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(PHASE, (entity, source, amount) ->
			!(entity instanceof ServerPlayer player) || decide(player, source));
		ServerTickEvents.END_SERVER_TICK.register(Mortality::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			REBIRTHS.remove(handler.player.getUUID());
			// (0.21.7) logging out while lying left for dead: they come to where they lie
			cameToWhereLying(handler.player);
			// and one whose wake was already under way (rising turned) is woken where they lie now, turned, not left to be
			// forgotten with the server's memory of it
			wokeOnLeaving(handler.player);
		});
		// (0.21.7) one who left (or the server stopped) while lying isn't held out cold for a day once back
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> unheld(handler.player));
		// (0.21.7) a body left for dead is dead as far as mobs can tell: none finishes it off. Nor one rising turned: a
		// skeleton kept executing a body in the black of a mid-turn death, and each death began that black again
		MobExecutions.SPARE.add((mob, victim) -> victim instanceof Player player && waking(player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WAKES.clear();
			REBIRTHS.clear();
			FELLED.clear();
			EXECUTED.clear();
		});
		Afflictions.register();
		Scars.register();
		Karma.register();
	}

	// ---- told by the downed state -----------------------------------------------------------------------------------------

	/** {@code entity} is dying of being down: {@code felled} is what put it there (null: nothing known). */
	static void felled(LivingEntity entity, @Nullable DamageSource felled) {
		if (entity instanceof Player && felled != null) {
			FELLED.put(entity, felled);
		}
		Karma.felled(entity, felled);
	}

	/** (0.20.50) Whether {@code entity} is being executed right now (its death let through by Chrono's rewind). */
	public static boolean executing(LivingEntity entity) {
		return EXECUTING.contains(entity);
	}

	/** (0.20.50) {@code entity}'s next death was already decided (and held back a moment): let it through unrolled. */
	public static void letThrough(LivingEntity entity) {
		LET_THROUGH.add(entity);
	}

	/** (0.20.50) Chrono rewound a death decided final: the character goes on (no rebirth). */
	public static void undoFinal(Player player) {
		player.removeAttached(FINAL);
		REBIRTHS.remove(player.getUUID());
		// ((0.20.117) and the black of a new life, if it had begun)
		player.removeAttached(REBORNING);
	}

	/** (0.20.50) Whether {@code player} lies left for dead, waiting to wake somewhere. */
	public static boolean waking(Player player) {
		return WAKES.containsKey(player.getUUID());
	}

	/**
	 * (0.21.7) Whether {@code player} lies left for dead where they fell, not yet come to: their body can be carried, and
	 * brought round once somewhere safe ({@link #unsafe}).
	 */
	public static boolean lying(Player player) {
		Wake wake = WAKES.get(player.getUUID());
		return wake != null && !wake.chosen;
	}

	/** (0.21.7) A hostile is near enough {@code body} that bringing it round here would only get it killed again. */
	public static boolean unsafe(LivingEntity body) {
		return !body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(SAFE_RANGE),
			mob -> mob instanceof net.minecraft.world.entity.monster.Enemy && mob.isAlive() && !Downed.isDowned(mob)
				&& !mob.isNoAi()).isEmpty();
	}

	/**
	 * (0.21.7) A lying body's tick, in place of a knock-out's mashing ({@link Downed}): sneak held fills the bar and, full, they
	 * come to by themselves (the wake roll); carried, they're told so. Nothing to do once the roll goes ahead.
	 */
	static void lie(ServerPlayer player, long now) {
		Wake wake = WAKES.get(player.getUUID());
		if (wake == null || wake.chosen) {
			return;
		}
		boolean dark = now < wake.moveAt;
		boolean carried = Carry.isCarried(player);
		if (!dark && (wake.carried == null || wake.carried != carried)) {
			wake.carried = carried;
			player.setAttached(WAKE_LINE, carried ? "message.jaymospowers.mortality.carried" : "message.jaymospowers.mortality.lying");
		}
		// (0.21.39) let go, the bar stays where it is and the next hold carries on (Andy: "every meter should work this way")
		boolean holding = player.isShiftKeyDown() && !dark;
		if (holding) {
			wake.held++;
		}
		if (wake.held >= COME_TO_TICKS) {
			comeTo(player, wake);
			return;
		}
		if (!dark && (holding ? wake.held % 2 == 0 : now % 20 == 0)) {
			int filled = Math.round(COME_TO_BAR * wake.held / (float) COME_TO_TICKS);
			String bar = "|".repeat(filled) + ".".repeat(COME_TO_BAR - filled);
			player.sendOverlayMessage(Component.translatable(carried ? "message.jaymospowers.mortality.come_to_carried"
				: "message.jaymospowers.mortality.come_to", bar).withStyle(holding ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		}
	}

	/** (0.21.7) They chose to come to by themselves: out of any arms, and the wake roll goes ahead a moment later. */
	private static void comeTo(ServerPlayer player, Wake wake) {
		wake.chosen = true;
		wake.moveAtChosen = player.level().getServer().getTickCount() + COME_TO_AFTER;
		if (Carry.carrierOf(player) instanceof ServerPlayer carrier) {
			Carry.slip(carrier);
		}
		player.setAttached(WAKE_LINE, "message.jaymospowers.mortality.fading");
	}

	/**
	 * (0.21.7) Brought round where they lay (or wherever they were carried): no wake roll, but the cause's injury all the
	 * same, and living on borrowed time until it's mended.
	 */
	private static void broughtRound(ServerPlayer player, Wake wake) {
		injure(player, wake.cause, wake.sub);
		player.setAttached(SURVIVOR, true);
		player.removeAttached(WAKE_LINE);
		JaymosPowers.LOGGER.info("{} was brought round where they lay ({})", player.getScoreboardName(), wake.cause.key());
		player.sendSystemMessage(Component.translatable("message.jaymospowers.mortality.brought_round").withStyle(ChatFormatting.GRAY,
			ChatFormatting.ITALIC));
	}

	/** (0.21.7) Left while lying: they come to where they lie (out cold a moment more once back), hurt as the cause left them. */
	private static void cameToWhereLying(ServerPlayer player) {
		Wake wake = WAKES.get(player.getUUID());
		if (wake == null || wake.chosen) {
			return;
		}
		WAKES.remove(player.getUUID());
		release(player.level().getServer().getLevel(wake.dimension), wake);
		if (Carry.carrierOf(player) instanceof ServerPlayer carrier) {
			Carry.slip(carrier);
		}
		injure(player, wake.cause, wake.sub);
		player.setAttached(SURVIVOR, true);
		player.setAttached(Downed.KNOCKED, (player.level().getGameTime() + OUT_AFTER) << 1);
	}

	/**
	 * Left while their wake was under way (rising turned, or come to and waiting on the roll): woken where they lie now, the
	 * turning done, so the turn isn't lost with the server's memory of the wake and they aren't left out cold once back.
	 */
	private static void wokeOnLeaving(ServerPlayer player) {
		Wake wake = WAKES.get(player.getUUID());
		if (wake == null || !wake.chosen) {
			return;
		}
		WAKES.remove(player.getUUID());
		ServerLevel level = player.level().getServer().getLevel(wake.dimension);
		release(level, wake);
		if (Carry.carrierOf(player) instanceof ServerPlayer carrier) {
			Carry.slip(carrier);
		}
		try {
			wakeAt(player, player.level(), player.blockPosition(), wake, wake.turn != null ? "risen" : wake.cause.key());
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.warn("{} couldn't be woken on leaving", player.getScoreboardName(), e);
		}
	}

	/**
	 * Held out cold with no wake behind it any more (the server forgot it, or they left): a knock-out long enough to be a
	 * lying body's is cut short, so nobody is kept in the black waiting for a wake that will never come.
	 */
	private static void unheld(ServerPlayer player) {
		Long knocked = player.getAttached(Downed.KNOCKED);
		long now = player.level().getGameTime();
		if (knocked != null && !WAKES.containsKey(player.getUUID()) && (knocked >> 1) - now > LIE_TICKS / 2) {
			player.setAttached(Downed.KNOCKED, (now + OUT_AFTER) << 1 | (knocked & 1L));
		}
	}

	/**
	 * Struck dead again while their wake is under way (bled out, or something kept hitting the body): the death is turned away
	 * and the wake goes on as it was, its black not begun again. Without this a body dying again every few seconds never woke.
	 */
	private static void stillWaking(ServerPlayer player, Wake wake) {
		ServerLevel level = player.level();
		player.setHealth(Math.max(1.0F, player.getMaxHealth() * WAKE_HEALTH));
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		player.resetFallDistance();
		Wounds.settle(player);
		long left = Math.max(0L, Math.max(wake.moveAt, wake.moveAtChosen) - level.getServer().getTickCount());
		String line = player.getAttachedOrElse(WAKE_LINE, "message.jaymospowers.mortality.fading");
		Downed.knockOut(level, player, wake.chosen ? (int) Math.min(Integer.MAX_VALUE, left) + OUT_AFTER + 20 * 60 : LIE_TICKS, false, line, false);
		player.setAttached(WAKE_LINE, line);
	}

	/** (0.21.7) Mobs near a lying body lose interest in it: as far as they can tell, it's dead. */
	private static void ignored(ServerPlayer player) {
		for (net.minecraft.world.entity.Mob mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
			player.getBoundingBox().inflate(IGNORE_RANGE), mob -> mob.getTarget() == player)) {
			mob.setTarget(null);
		}
	}

	/** (0.20.50) Chrono rewound a left-for-dead: they won't wake anywhere (the caller stands them up). */
	public static void cancelWake(ServerPlayer player) {
		Wake wake = WAKES.remove(player.getUUID());
		if (wake != null) {
			// ((0.20.83) a death undone doesn't use up one of the sure wakes; (0.21.42) a mid-turn one never did)
			if (!wake.midTurn) {
				player.setAttached(WOKEN, Math.max(0, player.getAttachedOrElse(WOKEN, 0) - 1));
			}
			player.removeAttached(TURN_TINT);
			release(player.level().getServer().getLevel(wake.dimension), wake);
		}
	}

	/** (0.20.50) {@code entity}'s death was undone (Chrono cheating death): what the downed state noted of it is dropped. */
	public static @Nullable DamageSource spare(LivingEntity entity) {
		EXECUTED.remove(entity);
		return FELLED.remove(entity);
	}

	/** {@code entity} is being executed where it lies: {@code finalBlow}, by a player or a boss, ends the character outright. */
	static void executed(LivingEntity entity, boolean finalBlow) {
		if (entity instanceof Player) {
			EXECUTED.put(entity, finalBlow);
			EXECUTING.add(entity);
		}
	}

	/** (0.20.50) The execution {@link #executed} began is over (its death has been dealt). */
	static void doneExecuting(LivingEntity entity) {
		EXECUTING.remove(entity);
	}

	/** (0.20.50) Those being executed this moment (kept past {@link #decide}, for Chrono's rewind, which lets them die). */
	private static final Set<LivingEntity> EXECUTING = Collections.newSetFromMap(new WeakHashMap<>());
	/** (0.20.50) Deaths already decided once (held back by Chrono's rewind, then let go): not rolled again. */
	private static final Set<LivingEntity> LET_THROUGH = Collections.newSetFromMap(new WeakHashMap<>());

	// ---- queries ----------------------------------------------------------------------------------------------------------

	public static boolean on() {
		return ZFighterRealism.deathByCause();
	}

	/** Whether {@code player}'s character is over (died for good, not yet born again). */
	public static boolean isFinal(Player player) {
		return player.hasAttached(FINAL);
	}

	/** Whether {@code entity} is a boss: vanilla's four, or anything another mod says is ({@link #BOSS_TESTS}). */
	public static boolean boss(@Nullable Entity entity) {
		if (entity == null) {
			return false;
		}
		if (entity instanceof EnderDragon || entity instanceof WitherBoss || entity instanceof Warden
			|| entity instanceof net.minecraft.world.entity.monster.ElderGuardian) {
			return true;
		}
		for (Predicate<Entity> test : BOSS_TESTS) {
			try {
				if (test.test(entity)) {
					return true;
				}
			} catch (RuntimeException e) {
				// (a broken test counts for nothing)
			}
		}
		return false;
	}

	/**
	 * One left for dead with an injury still open, which makes the next defeat final (Andy: "every injury"): a break not yet
	 * set, something still stuck in them, or a timer running ({@link Afflictions}). Wounds from the fight that downed them the
	 * first time don't count: only a survivor's ({@link #SURVIVOR}).
	 */
	public static boolean openInjury(Player player) {
		return player.hasAttached(SURVIVOR) && stillOpen(player);
	}

	private static boolean stillOpen(Player player) {
		return Breaks.unset(player) != 0 || !Lodged.stuck(player).isEmpty() || Afflictions.any(player);
	}

	/** A rough strength: health, armour and the weight of a blow. */
	static double strength(LivingEntity entity) {
		double attack = entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? entity.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0;
		return entity.getMaxHealth() + entity.getArmorValue() * 2.0 + attack * 4.0;
	}

	/** A boss sees {@code player}'s life isn't worth taking yet. */
	static boolean beneath(Player player, Entity boss) {
		return boss instanceof LivingEntity living && strength(player) < strength(living) * MERCY_SHARE;
	}

	// ---- the roll ---------------------------------------------------------------------------------------------------------

	/** Whether {@code player}'s death goes on (true, maybe for good) or is turned away (false: left for dead). */
	private static boolean decide(ServerPlayer player, DamageSource source) {
		if (LET_THROUGH.remove(player)) {
			return true;
		}
		DamageSource felled = FELLED.remove(player);
		Boolean executed = EXECUTED.remove(player);
		if (!on() || player.isCreative() || player.isSpectator() || isFinal(player) || ZFighterAfterlife.among(player)
			|| Karma.pending(player)
			|| player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return true;
		}
		if (Karma.below(player)) {
			// on their last chance in the Nether: no roll, no waking, nothing turns it away
			JaymosPowers.LOGGER.info("{} died on their last chance below ({}): final", player.getScoreboardName(), source.getMsgId());
			player.setAttached(FINAL, cause(player, source, felled).key());
			return true;
		}
		Cause cause = cause(player, source, felled);
		Entity by = (felled != null ? felled : source).getEntity();
		// (0.20.27) the dead, a witch, a vampire may make one of their own of them instead
		boolean unturnable = Boolean.TRUE.equals(executed) || cause == Cause.LAVA || cause == Cause.VOID || cause == Cause.BEHEADED;
		// already lying out cold on their way to waking (or rising turned): this death doesn't begin it all again
		Wake waking = WAKES.get(player.getUUID());
		if (waking != null && !unturnable) {
			JaymosPowers.LOGGER.info("{} fell to {} ({}) while waking: the wake goes on", player.getScoreboardName(), cause.key(), source.getMsgId());
			stillWaking(player, waking);
			return false;
		}
		// (0.21.42) already turning: they rise the rest of the way, whatever killed them
		java.util.Map.Entry<Integer, Runnable> mid = unturnable ? null : midTurn(player);
		if (mid != null) {
			JaymosPowers.LOGGER.info("{} fell to {} ({}) mid-turn: rising turned", player.getScoreboardName(), cause.key(), source.getMsgId());
			leftForDead(player, cause, sub(source, felled), by, mid.getValue(), mid.getKey());
			return false;
		}
		Runnable turn = unturnable || by == null ? null : turning(player, by);
		// (0.20.83) the first deaths of a life always wake (no head, the void, or a deliberate execution aside)
		int woken = player.getAttachedOrElse(WOKEN, 0);
		boolean sure = woken < SURE_WAKES && cause != Cause.BEHEADED && cause != Cause.VOID && !Boolean.TRUE.equals(executed);
		if (turn != null) {
			JaymosPowers.LOGGER.info("{} fell to {} ({}): turned", player.getScoreboardName(), cause.key(), source.getMsgId());
			leftForDead(player, cause, sub(source, felled), by, turn, null);
			return false;
		}
		float chance = cause.chance;
		if (cause == Cause.BOSS && beneath(player, by)) {
			chance = 0.0F;
		} else if (chance < 1.0F && chance > 0.0F) {
			chance = Math.min(1.0F, chance * ZFighterRealism.finalOdds());
		}
		boolean ends = !sure && (Boolean.TRUE.equals(executed) || openInjury(player) || chance > 0.0F && player.getRandom().nextFloat() < chance);
		// Heart crystals: if the death would be final but isn't unturnable, burn a heart and turn it away
		if (ends && !unturnable && HeartCrystals.tryBurnHeartOnDeath(player)) {
			JaymosPowers.LOGGER.info("{} burned a heart crystal to survive {}", player.getScoreboardName(), cause.key());
			leftForDead(player, cause, sub(source, felled), by, null, null);
			return false;
		}
		JaymosPowers.LOGGER.info("{} fell to {} ({}): {}{}", player.getScoreboardName(), cause.key(), source.getMsgId(),
			ends ? "final" : "left for dead", sure ? " (sure wake " + (woken + 1) + " of " + SURE_WAKES + ")" : "");
		if (ends) {
			ended(player, cause);
			return true;
		}
		leftForDead(player, cause, sub(source, felled), by, null, null);
		return false;
	}

	/** (0.21.42) What finishes {@code player}'s turning, and the colour of its vignette ({@link #MID_TURNS}), or null. */
	private static java.util.Map.@Nullable Entry<Integer, Runnable> midTurn(ServerPlayer player) {
		for (java.util.function.Function<ServerPlayer, java.util.Map.Entry<Integer, Runnable>> mid : MID_TURNS) {
			try {
				java.util.Map.Entry<Integer, Runnable> turn = mid.apply(player);
				if (turn != null) {
					return turn;
				}
			} catch (RuntimeException e) {
				JaymosPowers.LOGGER.warn("A turning under way failed for {}", player.getScoreboardName(), e);
			}
		}
		return null;
	}

	/** (0.20.27) What {@code by}'s kill turns {@code player} into ({@link #TURNINGS}), or null. */
	private static @Nullable Runnable turning(ServerPlayer player, Entity by) {
		for (java.util.function.BiFunction<ServerPlayer, Entity, Runnable> turning : TURNINGS) {
			try {
				Runnable turn = turning.apply(player, by);
				if (turn != null) {
					return turn;
				}
			} catch (RuntimeException e) {
				JaymosPowers.LOGGER.warn("A turning failed for {}", player.getScoreboardName(), e);
			}
		}
		return null;
	}

	/** The cause of a death by {@code source}, downed earlier by {@code felled} (or null). */
	static Cause cause(Player player, DamageSource source, @Nullable DamageSource felled) {
		// what the last blow says outright comes first
		if (source.is(DamageTypes.LAVA) || player.isInLava() && source.is(DamageTypeTags.IS_FIRE)) {
			return Cause.LAVA;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return Cause.VOID;
		}
		if (source.is(Dismember.DECAPITATION) || Dismember.headless(player)) {
			return Cause.BEHEADED;
		}
		boolean bare = source.is(Wounds.WOUNDS_DAMAGE) || source.is(Bleeding.BLEEDING_DAMAGE) || source.is(DamageTypes.GENERIC)
			|| source.is(DamageTypes.GENERIC_KILL);
		Cause last = bare ? null : of(player, source);
		Cause first = felled == null ? null : of(player, felled);
		if (last != null && (first == null || last == Cause.DROWNING || last == Cause.FIRE || last == Cause.FALL || last == Cause.SEA)) {
			return last;
		}
		if (first != null) {
			return first;
		}
		return last != null ? last : bare && source.getEntity() != null ? of(player, source) : Cause.OTHER;
	}

	private static Cause of(Player player, DamageSource source) {
		Entity by = source.getEntity();
		if (by instanceof Player) {
			return Cause.PLAYER;
		}
		if (sea(by)) {
			return Cause.SEA;
		}
		if (boss(by)) {
			return Cause.BOSS;
		}
		if (by instanceof WitherSkeleton || source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)) {
			return Cause.WITHER;
		}
		if (by instanceof Zombie) {
			return Cause.ZOMBIE;
		}
		if (by instanceof AbstractSkeleton) {
			return Cause.SKELETON;
		}
		if (by instanceof Creeper || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return Cause.BLAST;
		}
		if (by instanceof Spider || named(by, "snake", "cobra", "viper", "adder", "scorpion")) {
			return Cause.VENOM;
		}
		if (by instanceof Witch || (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) && player.hasEffect(MobEffects.POISON)
			|| source.typeHolder().unwrapKey().map(k -> k.identifier().getPath().contains("poison") || k.identifier().getPath().contains("venom"))
				.orElse(false)) {
			return Cause.POISON;
		}
		if (source.is(DamageTypes.LAVA)) {
			return Cause.LAVA;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return Cause.FIRE;
		}
		if (source.is(DamageTypeTags.IS_DROWNING)) {
			return Cause.DROWNING;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return Cause.FALL;
		}
		if (by == null) {
			return Cause.ENVIRONMENT;
		}
		return Cause.OTHER;
	}

	/** The kind of an environmental death, for its lasting mark: frostbite, lightning, wasting, or crushed. */
	private static @Nullable String sub(DamageSource source, @Nullable DamageSource felled) {
		DamageSource s = felled != null && felled.getEntity() == null ? felled : source;
		if (s.is(DamageTypeTags.IS_FREEZING)) {
			return "frostbite";
		}
		if (s.is(DamageTypeTags.IS_LIGHTNING)) {
			return "lightning";
		}
		if (s.is(DamageTypes.STARVE)) {
			return "wasting";
		}
		return null;
	}

	/** Anything of the sea that drowns or eats whoever it kills. */
	static boolean sea(@Nullable Entity by) {
		if (by == null) {
			return false;
		}
		if (by instanceof WaterAnimal || by instanceof AgeableWaterCreature || by instanceof Guardian || by instanceof Drowned) {
			return true;
		}
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(by.getType()).getPath();
		return !path.contains("aer") && named(by, "shark", "whale", "orca", "piranha", "eel", "kraken", "leviathan", "jellyfish", "angler",
			"barracuda", "crocodile", "alligator");
	}

	private static boolean named(@Nullable Entity by, String... words) {
		if (by == null) {
			return false;
		}
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(by.getType()).getPath();
		for (String word : words) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}

	// ---- left for dead ----------------------------------------------------------------------------------------------------

	/**
	 * The death is turned away: health back to a little, and out cold where they fell ({@link Downed#knockOut}: the black screen)
	 * until they are moved to where they wake ({@link #tick}).
	 */
	private static void leftForDead(ServerPlayer player, Cause cause, @Nullable String sub, @Nullable Entity by, @Nullable Runnable turn,
		@Nullable Integer tint) {
		ServerLevel level = player.level();
		boolean midTurn = tint != null && turn != null;
		if (!midTurn) {
			player.setAttached(WOKEN, player.getAttachedOrElse(WOKEN, 0) + 1);
		}
		player.setHealth(Math.max(1.0F, player.getMaxHealth() * WAKE_HEALTH));
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		player.resetFallDistance();
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.WITHER);
		Wounds.settle(player);
		// (no boss stands over them as they lie)
		if (by instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player) {
			mob.setTarget(null);
		}
		// ((0.21.7) out cold where they fell until they come to by themselves, or someone brings them round)
		String fading = midTurn ? "message.jaymospowers.mortality.turning" : "message.jaymospowers.mortality.fading";
		Downed.knockOut(level, player, turn != null ? (midTurn ? TURN_DARK : OUT_BEFORE) + OUT_AFTER + 20 * 60 : LIE_TICKS, false, fading, false);
		player.setAttached(WAKE_LINE, fading);
		if (midTurn) {
			player.setAttached(TURN_TINT, tint);
			level.playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.7F, 0.5F);
		}
		Wake wake = new Wake(cause, sub, level.dimension(), player.blockPosition(),
			level.getServer().getTickCount() + (midTurn ? TURN_DARK : OUT_BEFORE), by, turn);
		wake.midTurn = midTurn;
		// ((0.21.7) the turned rise as they rise: nothing to carry them away from)
		if (turn != null) {
			wake.chosen = true;
			wake.moveAtChosen = wake.moveAt;
		}
		WAKES.put(player.getUUID(), wake);
	}

	private static void tick(MinecraftServer server) {
		if (!WAKES.isEmpty()) {
			long now = server.getTickCount();
			for (Iterator<Map.Entry<UUID, Wake>> it = WAKES.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, Wake> entry = it.next();
				Wake wake = entry.getValue();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				ServerLevel level = server.getLevel(wake.dimension);
				if (player == null || level == null || !player.isAlive()) {
					it.remove();
					release(level, wake);
					continue;
				}
				if (!wake.chosen) {
					// (0.21.7) lying where they fell: brought round by someone (no longer down), or still waiting
					if (!Downed.isDowned(player)) {
						it.remove();
						release(level, wake);
						broughtRound(player, wake);
					} else if (now % 20 == 0) {
						ignored(player);
					}
					continue;
				}
				long moveAt = Math.max(wake.moveAt, wake.moveAtChosen);
				if (now % 20 == 0) {
					// (mobs lose interest in one rising turned as in one lying: they don't stand over the body)
					ignored(player);
				}
				if (now >= moveAt + WAKE_LIMIT) {
					// (whatever held it up, a far spot that never loaded: they wake where they lie)
					JaymosPowers.LOGGER.warn("{}'s wake ran {} ticks over: waking them where they lie", player.getScoreboardName(), now - moveAt);
					it.remove();
					release(level, wake);
					wakeAt(player, player.level(), player.blockPosition(), wake, wake.turn != null ? "risen" : wake.cause.key());
					continue;
				}
				if (now < moveAt || !place(server, player, level, wake)) {
					continue;
				}
				it.remove();
				release(level, wake);
			}
		}
		if (server.getTickCount() % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.hasAttached(SURVIVOR) && player.isAlive()) {
					mending(player);
				}
				// ((0.20.117) not while a Chrono is held back from the death for R: the rewind undoes it. Andy's Chrono pressed R
				// and was born again anyway: the rebirth had started inside the window)
				if (isFinal(player) && player.isAlive() && !ZFighterAfterlife.among(player) && !REBIRTHS.containsKey(player.getUUID())
					&& !com.jaymospowers.power.chrono.ChronoCheatDeath.held(player)) {
					REBIRTHS.put(player.getUUID(), server.getTickCount() + REBIRTH_DELAY);
					// ((0.20.94) and black until the new character is made)
					player.setAttached(REBORNING, (long)server.getTickCount());
				}
				if (player.isAlive()) {
					lighten(server, player);
					unheld(player);
				}
				if (player.hasAttached(WAKE_LINE) && !WAKES.containsKey(player.getUUID()) && !Downed.isDowned(player)) {
					player.removeAttached(WAKE_LINE);
				}
				if (player.hasAttached(TURN_TINT) && !WAKES.containsKey(player.getUUID()) && !Downed.isDowned(player)) {
					player.removeAttached(TURN_TINT);
				}
			}
		}
		if (!REBIRTHS.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Long>> it = REBIRTHS.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, Long> entry = it.next();
				if (entry.getValue() > server.getTickCount()) {
					continue;
				}
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null && com.jaymospowers.power.chrono.ChronoCheatDeath.held(player)) {
					continue; // ((0.20.117) a Chrono may still rewind it)
				}
				it.remove();
				if (player != null && player.isAlive() && isFinal(player) && !ZFighterAfterlife.among(player)) {
					endLife(player);
				}
			}
		}
	}

	/**
	 * A survivor's mending: an arrow left in a leg works free once the leg is set or splinted, and once nothing is open they're
	 * no longer living on borrowed time.
	 */
	private static void mending(ServerPlayer player) {
		int held = Breaks.set(player) | Breaks.splinted(player);
		boolean legHeld = (held & (Breaks.bit(BodyPart.LEFT_LEG) | Breaks.bit(BodyPart.RIGHT_LEG))) != 0;
		List<ItemStack> stuck = Lodged.stuck(player);
		if (legHeld && !stuck.isEmpty() && stuck.stream().allMatch(stack -> stack.is(Items.ARROW))) {
			player.removeAttached(Lodged.LODGED);
			if (!player.getInventory().add(new ItemStack(Items.ARROW))) {
				player.spawnAtLocation(player.level(), new ItemStack(Items.ARROW));
			}
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortality.arrow_out").withStyle(ChatFormatting.YELLOW));
		}
		if (!stillOpen(player) && !Downed.isDowned(player)) {
			player.removeAttached(SURVIVOR);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.mortality.mended").withStyle(ChatFormatting.GREEN));
		}
	}

	private static void release(@Nullable ServerLevel level, Wake wake) {
		if (level != null && wake.far != null) {
			level.getChunkSource().removeTicketWithRadius(WAKING, ChunkPos.containing(wake.far), 0);
		}
	}

	/** Finds where {@code player} wakes and puts them there; false while a far spot is still loading. */
	private static boolean place(MinecraftServer server, ServerPlayer player, ServerLevel level, Wake wake) {
		RandomSource random = player.getRandom();
		BlockPos at = null;
		// (0.20.27) the turned rise where they fell (the drowned wash ashore first, below)
		if (wake.turn != null && wake.cause != Cause.DROWNING) {
			wakeAt(player, level, wake.fell, wake, "risen");
			return true;
		}
		// (0.20.26) taken captive by those who downed them, if they're the kind who would
		if (wake.turn == null && wake.by != null && wake.tries == 0 && wake.far == null) {
			for (java.util.function.BiFunction<ServerPlayer, Entity, net.minecraft.core.GlobalPos> captor : CAPTORS) {
				net.minecraft.core.GlobalPos held;
				try {
					held = captor.apply(player, wake.by);
				} catch (RuntimeException e) {
					JaymosPowers.LOGGER.warn("A captor failed to take {}", player.getScoreboardName(), e);
					held = null;
				}
				ServerLevel to = held == null ? null : server.getLevel(held.dimension());
				if (to != null) {
					wakeAt(player, to, held.pos(), wake, "captured");
					return true;
				}
			}
		}
		// or back in a berth of their own (a bed aboard a ship), if she's still there (not one a boss spared: dumped far off)
		if (wake.turn == null && wake.tries == 0 && wake.far == null && wake.cause != Cause.BOSS) {
			for (java.util.function.Function<ServerPlayer, net.minecraft.core.GlobalPos> berth : BERTHS) {
				net.minecraft.core.GlobalPos in;
				try {
					in = berth.apply(player);
				} catch (RuntimeException e) {
					JaymosPowers.LOGGER.warn("A berth failed to take {} back", player.getScoreboardName(), e);
					in = null;
				}
				ServerLevel to = in == null ? null : server.getLevel(in.dimension());
				if (to != null) {
					wakeAt(player, to, in.pos(), wake, "berth");
					return true;
				}
			}
		}
		// (0.20.83) or carried in by folk who think well enough of them
		if (wake.turn == null && wake.tries == 0 && wake.far == null && wake.cause != Cause.BOSS) {
			for (java.util.function.BiFunction<ServerPlayer, Entity, net.minecraft.core.GlobalPos> shelter : SHELTERS) {
				net.minecraft.core.GlobalPos into;
				try {
					into = shelter.apply(player, wake.by);
				} catch (RuntimeException e) {
					JaymosPowers.LOGGER.warn("A shelter failed to take {} in", player.getScoreboardName(), e);
					into = null;
				}
				ServerLevel to = into == null ? null : server.getLevel(into.dimension());
				if (to != null) {
					wakeAt(player, to, into.pos(), wake, "sheltered");
					return true;
				}
			}
		}
		if (wake.cause == Cause.FALL) {
			// (they wake where they landed)
			at = wake.fell;
		} else if (wake.far == null && wake.loading == null) {
			at = wake.cause == Cause.DROWNING ? shore(level, wake.fell, random) : null;
			for (int i = 0; at == null && wake.cause != Cause.BOSS && i < NEAR_TRIES; i++) {
				at = near(level, wake.fell, NEAR_MIN, NEAR_MAX, random, true);
			}
		}
		if (at == null && wake.cause != Cause.FALL) {
			if (wake.loading != null && !wake.loading.isDone()) {
				return false;
			}
			if (wake.far != null) {
				at = ground(level, wake.far.getX(), wake.far.getZ(), true);
				level.getChunkSource().removeTicketWithRadius(WAKING, ChunkPos.containing(wake.far), 0);
				wake.far = null;
				wake.loading = null;
			}
			if (at == null && wake.tries++ < FAR_TRIES) {
				int min = wake.cause == Cause.BOSS ? SPARED_MIN : NEAR_MIN;
				int max = wake.cause == Cause.BOSS ? SPARED_MAX : NEAR_MAX;
				double angle = random.nextDouble() * Math.PI * 2.0;
				double r = min + random.nextDouble() * (max - min);
				BlockPos far = BlockPos.containing(wake.fell.getX() + Math.cos(angle) * r, wake.fell.getY(), wake.fell.getZ() + Math.sin(angle) * r);
				if (level.getWorldBorder().isWithinBounds(far)) {
					wake.far = far;
					wake.loading = level.getChunkSource().addTicketAndLoadWithRadius(WAKING, ChunkPos.containing(far), 0);
				}
				return false;
			}
			if (at == null) {
				at = ground(level, wake.fell.getX(), wake.fell.getZ(), false);
			}
			if (at == null) {
				at = wake.fell;
			}
		}
		wakeAt(player, level, at, wake, wake.cause.key());
		return true;
	}

	/** A dry spot within {@code min} to {@code max} blocks of {@code from} in ground already loaded, or null. */
	private static @Nullable BlockPos near(ServerLevel level, BlockPos from, int min, int max, RandomSource random, boolean awayFromTowns) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double r = min + random.nextDouble() * (max - min);
		int x = (int)Math.floor(from.getX() + Math.cos(angle) * r);
		int z = (int)Math.floor(from.getZ() + Math.sin(angle) * r);
		if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		return ground(level, x, z, awayFromTowns);
	}

	/** The nearest dry ground beside water (a shore) from {@code from}, in ground already loaded, or null. */
	private static @Nullable BlockPos shore(ServerLevel level, BlockPos from, RandomSource random) {
		for (int r = 8; r <= SHORE_MAX; r += 8) {
			int steps = Math.max(8, r / 2);
			double start = random.nextDouble() * Math.PI * 2.0;
			for (int i = 0; i < steps; i++) {
				double angle = start + Math.PI * 2.0 * i / steps;
				int x = (int)Math.floor(from.getX() + Math.cos(angle) * r);
				int z = (int)Math.floor(from.getZ() + Math.sin(angle) * r);
				if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				BlockPos ground = ground(level, x, z, false);
				if (ground != null && besideWater(level, ground)) {
					return ground;
				}
			}
		}
		return null;
	}

	private static boolean besideWater(ServerLevel level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-4, -2, -4), at.offset(4, 0, 4))) {
			if (level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4) && level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Where someone could lie at column {@code x}, {@code z} (its chunk loaded): on solid ground, out of any liquid and anything
	 * that burns or pricks, with room to stand; {@code awayFromTowns}: and no villager near. Null if not.
	 */
	public static @Nullable BlockPos ground(ServerLevel level, int x, int z, boolean awayFromTowns) {
		if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		BlockPos feet = null;
		if (level.dimensionType().hasCeiling()) {
			int top = Math.min(level.getMaxY() - 8, level.getMinY() + 120);
			for (int y = top; y > level.getMinY() + 4; y--) {
				BlockPos p = new BlockPos(x, y, z);
				if (standable(level, p)) {
					feet = p;
					break;
				}
			}
		} else {
			BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			feet = standable(level, p) ? p : null;
		}
		if (feet == null || !level.getWorldBorder().isWithinBounds(feet)) {
			return null;
		}
		if (awayFromTowns && !level.getEntitiesOfClass(AbstractVillager.class, new AABB(feet).inflate(TOWN_CLEAR)).isEmpty()) {
			return null;
		}
		return feet;
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockState below = level.getBlockState(feet.below());
		if (!below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP) || !level.getFluidState(feet.below()).isEmpty()
			|| below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CACTUS) || below.is(Blocks.CAMPFIRE) || below.is(Blocks.SOUL_CAMPFIRE)
			|| below.is(Blocks.POWDER_SNOW) || below.is(Blocks.BEDROCK)) {
			return false;
		}
		for (int dy = 0; dy < 2; dy++) {
			BlockPos p = feet.above(dy);
			BlockState state = level.getBlockState(p);
			if (!state.getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty() || state.is(Blocks.FIRE)
				|| state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SWEET_BERRY_BUSH)) {
				return false;
			}
		}
		return true;
	}

	/** Put down at {@code at}, out cold a little longer, with the injury the cause leaves; they come round by themselves. */
	private static void wakeAt(ServerPlayer player, ServerLevel level, BlockPos at, Wake wake, String line) {
		if (!at.equals(wake.fell) || player.level() != level) {
			player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), player.getRandom().nextFloat() * 360.0F, 0.0F, true);
		}
		player.resetFallDistance();
		player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		if (wake.turn == null) {
			injure(player, wake.cause, wake.sub);
			player.setAttached(SURVIVOR, true);
			if ("sheltered".equals(line)) {
				// (0.20.83) those who took them in bound their wounds and splinted what was broken
				Treatment.tendAll(player, false);
			}
		}
		// (out cold a while more, wherever they are now: then they come round, still hurt)
		if (Downed.isDowned(player)) {
			Downed.forgetQuietly(player);
		}
		String woke = "message.jaymospowers.mortality.wake." + (wake.midTurn ? "turned" : wake.turn != null ? "risen" : line);
		Downed.knockOut(level, player, OUT_AFTER, false, woke, false);
		player.setAttached(WAKE_LINE, woke);
		level.playSound(null, at, wake.turn != null ? SoundEvents.ZOMBIE_VILLAGER_CURE : SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F,
			wake.turn != null ? 0.6F : 0.8F);
		if (wake.turn != null) {
			try {
				wake.turn.run();
			} catch (RuntimeException e) {
				JaymosPowers.LOGGER.warn("{} couldn't be turned", player.getScoreboardName(), e);
			}
		}
	}

	/** The injury {@code cause} leaves on one who lived. */
	static void injure(ServerPlayer player, Cause cause, @Nullable String sub) {
		RandomSource random = player.getRandom();
		BodyPart leg = random.nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
		BodyPart arm = random.nextBoolean() ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
		switch (cause) {
			case ZOMBIE -> {
				// bitten and torn: a dirty wound that festers at once
				Bleeding.open(player, arm, true);
				Bleeding.open(player, BodyPart.BODY, true);
				Bleeding.festerNow(player, arm);
			}
			case SKELETON -> {
				Lodged.lodge(player, new ItemStack(Items.ARROW));
				Wounds.breakPart(player, leg, null);
			}
			case BLAST -> {
				Wounds.breakPart(player, BodyPart.LEFT_LEG, null);
				Wounds.breakPart(player, BodyPart.RIGHT_LEG, null);
				Wounds.breakPart(player, arm, null);
				if (random.nextFloat() < BLAST_SEVER) {
					int[] limbs = {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.RIGHT_LEG, Dismember.LEFT_LEG};
					Dismember.sever(player, limbs[random.nextInt(limbs.length)], null);
				}
			}
			case FALL -> {
				Wounds.breakPart(player, BodyPart.LEFT_LEG, null);
				Wounds.breakPart(player, BodyPart.RIGHT_LEG, null);
			}
			case VENOM -> Afflictions.start(player, Afflictions.Kind.VENOM);
			case POISON -> Afflictions.start(player, Afflictions.Kind.POISONED);
			case WITHER -> Afflictions.start(player, Afflictions.Kind.WITHERING);
			case DROWNING -> {
				Afflictions.start(player, Afflictions.Kind.WET_LUNGS);
				Scars.add(player, Scars.Kind.WEAK_LUNGS);
			}
			case FIRE -> Scars.add(player, Scars.Kind.BURNS);
			case ENVIRONMENT -> Scars.add(player, "frostbite".equals(sub) ? Scars.Kind.FROSTBITE : "lightning".equals(sub) ? Scars.Kind.LIGHTNING
				: "wasting".equals(sub) ? Scars.Kind.WASTING : Scars.Kind.CRUSHED);
			case PLAYER, OTHER -> Wounds.breakPart(player, random.nextBoolean() ? leg : arm, null);
			default -> {
				// (a boss that spared you left you battered, no more)
			}
		}
	}

	// ---- the end of a life ------------------------------------------------------------------------------------------------

	/**
	 * The life is over, unless its weight earns one more chance ({@link Karma#weigh}): the just are let into the Other World
	 * to win it back, the wicked wake in the Nether, and everyone else is born again ({@link #FINAL}).
	 */
	private static void ended(ServerPlayer player, Cause cause) {
		if (Karma.below(player) || Karma.weigh(player) == Karma.Verdict.EVEN) {
			player.setAttached(FINAL, cause.key());
		}
	}

	/**
	 * A timer ran out ({@link Afflictions}): {@code player} dies of it, for good, with no going down first.
	 */
	static void succumb(ServerPlayer player, Cause cause) {
		if (!player.isAlive()) {
			return;
		}
		ended(player, cause);
		if (Downed.isDowned(player)) {
			Downed.forgetQuietly(player);
		}
		Downed.succumb(player.level(), player);
	}

	/**
	 * The character is over and they are back on their feet (respawned, or let go from the Other World): what was theirs
	 * goes with the dead (their power, their afflictions and scars, their breaks), and the life story is told anew with
	 * every start unlocked (Kingdoms, through {@link ZFighterAfterlife#bornAgain}).
	 */
	public static void endLife(ServerPlayer player) {
		player.removeAttached(FINAL);
		player.removeAttached(SURVIVOR);
		HeartCrystals.onFinalDeath(player);
		Afflictions.clear(player);
		Scars.clear(player);
		Breaks.clearAll(player);
		Wounds.healAll(player);
		player.removeAttached(Lodged.LODGED);
		if (Powers.of(player) != null) {
			Powers.set(player, null);
		}
		// (a new life's power awakens in its own time)
		player.removeAttached(Powers.AWAKEN_IN);
		// (0.20.83) Andy: "EVERYTHING resets": every other mark the old life left on them goes too
		// ((0.20.94) "it should wipe absolutely everything on your character": all they carry, their ender chest, their
		// experience, their spawn point and every effect too)
		long dark = player.getAttachedOrElse(REBORNING, (long)player.level().getServer().getTickCount());
		wipe(player);
		player.setAttached(REBORNING, dark);
		player.getInventory().clearContent();
		player.getEnderChestInventory().clearContent();
		player.setExperienceLevels(0);
		player.setExperiencePoints(0);
		player.removeAllEffects();
		player.setRespawnPosition(null, false);
		player.inventoryMenu.broadcastChanges();
		player.sendSystemMessage(Component.translatable("message.jaymospowers.mortality.ended").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		if (!ZFighterAfterlife.bornAnew(player)) {
			JaymosPowers.LOGGER.info("{}'s life ended, but no life story could be told anew (no Kingdoms, or its screen)", player.getScoreboardName());
		}
	}

	/**
	 * (0.20.83) Andy, on the third death: "you should get reprompted completely, like with a new character creator, new
	 * quests skills, etc. EVERYTHING resets." Takes every attachment our three mods keep on {@code player} (skills and their
	 * trees, levels, quests, reputation, world tier and knowledge, class, heritage, story, patron, curses, mastery, affinity,
	 * the Z Fighter's training, missing limbs...). (0.20.94) Andy: "it should wipe absolutely everything on your character":
	 * nothing is kept any more, not the roads their deeds opened, nor a backpack, baubles or a stash.
	 */
	static void wipe(ServerPlayer player) {
		if (!(player instanceof net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl target)) {
			return;
		}
		Map<net.fabricmc.fabric.api.attachment.v1.AttachmentType<?>, ?> held = target.fabric_getAttachments();
		if (held == null || held.isEmpty()) {
			return;
		}
		int n = 0;
		for (AttachmentType<?> type : List.copyOf(held.keySet())) {
			Identifier id = type.identifier();
			if (OURS.contains(id.getNamespace())) {
				player.removeAttached(type);
				n++;
			}
		}
		JaymosPowers.LOGGER.info("{}'s life ended: {} marks of it wiped", player.getScoreboardName(), n);
	}

	/** (0.20.83) The mods whose attachments a new life wipes ({@link #wipe}). */
	private static final Set<String> OURS = Set.of(JaymosPowers.MOD_ID, "jaymoskingdoms", "jaymoskitchen");

	/**
	 * (0.20.94) Andy: "black screen until it's ready". While this is on a player (from the moment a final death has them back
	 * on their feet until their new character is made: Kingdoms' story, MCA's creator, heritage and class), their screen is
	 * black. Synced to them; lifted by {@link #tick} once nothing is still to choose ({@link #DARK_MAX} at most).
	 */
	public static final AttachmentType<Long> REBORNING = AttachmentRegistry.create(JaymosPowers.id("mortality_reborning"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG,
			net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.targetOnly()));

	/**
	 * (0.20.94) Andy: "this will give you a chance to read what happened as it should say if you were kidnapped or in the
	 * wilderness". What the black of a death turned away says, read large on it (its lang key; synced to them): going dark,
	 * then where and how they wake. Gone once they're up.
	 */
	public static final AttachmentType<String> WAKE_LINE = AttachmentRegistry.create(JaymosPowers.id("mortality_wake_line"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
			net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.targetOnly()));

	/** (0.20.94) The least and most (ticks) the black of a new life lasts. */
	static final long DARK_MIN = 40L, DARK_MAX = 20L * 60 * 10;

	/**
	 * (0.20.94) Lifts {@link #REBORNING} once {@code player}'s new character is made (nothing more to choose), or after
	 * {@link #DARK_MAX}.
	 */
	private static void lighten(MinecraftServer server, ServerPlayer player) {
		Long since = player.getAttached(REBORNING);
		if (since == null || isFinal(player) || REBIRTHS.containsKey(player.getUUID())) {
			return;
		}
		long held = server.getTickCount() - since;
		if (held >= DARK_MAX || held >= DARK_MIN && !com.jaymospowers.power.zfighter.ZFighterAfterlife.making(player)) {
			player.removeAttached(REBORNING);
		}
	}

	/**
	 * (0.20.94) A safe place to stand at or near {@code at} in {@code level}: Andy, "reincarnating just spawned me in a wall".
	 * The spot itself if they fit there, else the nearest that fits a few blocks round (up first), else the ground at the top
	 * of the column, else {@code at} as it was.
	 */
	public static BlockPos safe(ServerLevel level, BlockPos at) {
		level.getChunk(at);
		if (standable(level, at)) {
			return at;
		}
		for (int r = 0; r <= 4; r++) {
			for (int dy = 0; dy <= 8; dy++) {
				for (int sign : new int[] {1, -1}) {
					for (int dx = -r; dx <= r; dx++) {
						for (int dz = -r; dz <= r; dz++) {
							if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
								continue;
							}
							BlockPos p = at.offset(dx, dy * sign, dz);
							if (level.isInWorldBounds(p) && standable(level, p)) {
								return p;
							}
						}
					}
				}
			}
		}
		BlockPos top = ground(level, at.getX(), at.getZ(), false);
		return top != null ? top : at;
	}

	/** Every player whose life ended, waiting to be born again ({@code /wounds} and tests). */
	static Set<UUID> waiting() {
		return Collections.unmodifiableSet(REBIRTHS.keySet());
	}
}
