package com.jaymoskingdoms.inhabitants;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Youth, the wood-wisp: a small, half-there Sylvan with fae wings and a trail of light, which comes to a player who asks
 * a question and answers it in chat. It has no mind of its own: the {@code /youth} commands ({@link YouthCommand}) call
 * it and give it its words, and Hermes (Andy's bot, YOUTH) sends those over the server console. While it waits it
 * hovers by its asker, turns to whoever is nearest and wanders between eight idle animations, never the same one twice
 * running. It can't be hurt or pushed, never fights, is never saved with the world, and goes back to the trees when its
 * asker leaves, walks off, or stops asking.
 *
 * <p>Model, texture and animations: kingdoms/art/youth_wisp.bbmodel (our own, made by kingdoms/tools/gen_youth_wisp.py).
 */
public class YouthWisp extends PathfinderMob implements GeoEntity {
	/** It vanishes after this long with no question (ticks). */
	static final int IDLE_TIMEOUT = 15 * 20;
	/** ...but while an answer is coming it waits this long, then says "be right back" ({@link #HOLD_LINES}) and goes. */
	static final int ANSWER_TIMEOUT = 2 * 60 * 20;
	/**
	 * Sending it off (Andy: "leave anytime you tell him to"): "go away", "bye", "thanks", "leave", "go home"... with its
	 * name, or bare while its wisp is beside you.
	 */
	static final java.util.regex.Pattern BYE = java.util.regex.Pattern.compile(
		"^(go away|go home|leave|you can go|off you go|shoo|dismissed|bye|goodbye|good bye|bye bye|see ya|see you|later|cya|thanks|thank you|thx|ty|cheers|that's all|thats all|never ?mind|nvm)\\b[\\s,!.]*(youth)?[\\s!.]*$",
		java.util.regex.Pattern.CASE_INSENSITIVE);
	/** How many goodbye lines it has (message.jaymoskingdoms.youth_wisp.bye.0 ...). */
	static final int BYE_LINES = 4;
	/** Ticks between two right-click hints from the same wisp. */
	static final int HINT_COOLDOWN = 5 * 20;
	/** A chat line that calls Youth: its name anywhere in it, as a word, in any case ("youth, how does Crush work?"). */
	static final java.util.regex.Pattern CALL = java.util.regex.Pattern.compile("\\byouth\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
	/** Its name at the front of a line, and what follows it (the question). */
	static final java.util.regex.Pattern LEAD = java.util.regex.Pattern.compile("^[!*]?\\s*youth\\b[\\s,:!.?-]*(.*)$",
		java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
	/**
	 * A follow-up while its wisp is beside you: a line that asks something ("what are these things that show when i
	 * sneak?") needs no "youth" in it. A question mark anywhere, or one of these words first.
	 */
	static final java.util.regex.Pattern QUESTION = java.util.regex.Pattern.compile(
		"^.*\\?.*$|^(what|whats|what's|how|hows|how's|why|where|when|who|which|can|could|is|are|does|do|did|should|will|would|wheres|where's)\\b.*$",
		java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
	/** "youth, look!": what follows "look" is the question about the thing it is shown (often nothing). */
	static final java.util.regex.Pattern LOOK = java.util.regex.Pattern.compile("^look\\b[\\s,:!.?-]*(.*)$",
		java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
	/** A yes to "This?" ("yes", "yeah", "that one!", "mhm"). */
	static final java.util.regex.Pattern YES = java.util.regex.Pattern.compile(
		"^(y|ya|ye|yes|yeah|yea|yep|yup|mhm|uh huh|sure|correct|right|exactly|that|that one|this|this one|thats it|that's it|there)\\b[\\s!.,]*$",
		java.util.regex.Pattern.CASE_INSENSITIVE);
	/** A no to "This?" ("no", "nope, that", "not that"): it looks again at whatever the player points at now. */
	static final java.util.regex.Pattern NO = java.util.regex.Pattern.compile("^(n|no|nah|nope|not that|not that one|wrong|other one)\\b.*$",
		java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
	/** How far it will go to look at what it's shown (blocks), and how long it waits by it for a yes (ticks). */
	static final double LOOK_RANGE = 48.0;
	static final int LOOK_WAIT = IDLE_TIMEOUT;
	/** How many "This?" lines it has (message.jaymoskingdoms.youth_wisp.look.0 ...). */
	static final int LOOK_LINES = 4;
	/** A cry for help ("HELP", "help me!", "youth, help me"), which carries its crier home if a wisp is in sight. */
	static final java.util.regex.Pattern HELP = java.util.regex.Pattern.compile("^help(\\s+me)?[\\s!.]*$", java.util.regex.Pattern.CASE_INSENSITIVE);
	/** A wisp is in sight for a cry for help this near (blocks), in clear view and within this cosine of where they look. */
	static final double HELP_RANGE = 24.0, HELP_VIEW = 0.45;
	/** The flash of the trip home: leaf green (0xRRGGBB). */
	static final int HOME_COLOUR = 0x5CE07A;
	/** How many lines it says as it carries someone home, and how many villager gossip lines there are of it. */
	static final int HOME_LINES = 3, GOSSIP_LINES = 8;
	/** How many jokes it makes when someone cries for help again the same day (a trip home is once a day). */
	static final int JOKE_LINES = 8;
	/** On a player: the day (the Overworld's clock, in days) they were last carried home. Kept, and kept through death. */
	static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Long> CARRIED_DAY = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
		.create(com.jaymoskingdoms.JaymosKingdoms.id("youth_carried_day"), builder -> builder.persistent(com.mojang.serialization.Codec.LONG).copyOnDeath());
	/** How often a villager's gossip is of the wisps (Overworld only). */
	static final float GOSSIP_CHANCE = 0.03F;

	/** How many "be right back" lines it has (message.jaymoskingdoms.youth_wisp.hold.0 ...). */
	static final int HOLD_LINES = 6;
	/** ...or when its asker is further than this (blocks) or in another dimension. */
	static final double LEASH = 16.0;
	/** At most this many in the world at once; summoning one more sends the longest-idle home. */
	static final int MAX_WISPS = 4;
	/** Where it comes in: this far in front of its asker (blocks) and this far above their feet. */
	static final double AHEAD = 2.0, HEIGHT = 1.0;
	/** How far it drifts about its spot while it waits (blocks), and how quickly it follows (share of the gap a tick). */
	static final double DRIFT = 0.6, FOLLOW = 0.12;
	/** How long the vanish animation plays before it is gone (ticks). */
	static final int VANISH_TICKS = 10;
	/** The pause between two idles (ticks, a random length between these): short, it's never still for long. */
	static final int GAP_MIN = 10, GAP_MAX = 40;
	/** Its light: a pale leaf-green (0xRRGGBB), for its motes and its name in chat. */
	static final int COLOUR = 0xBEF0D6;
	/** How often (ticks) a mote drifts off it while it waits. */
	static final int MOTE_EVERY = 12;

	/** How often (ticks) the wisp may switch to a new idle behavior mode. */
	static final int BEHAVIOR_SWITCH_MIN = 20 * 20; // 20 seconds
	static final int BEHAVIOR_SWITCH_MAX = 60 * 20; // 60 seconds

	/** Idle behavior modes the wisp can exhibit. */
	enum BehaviorMode {
		/** Drifts slowly about its summon spot (original behavior). */
		DRIFT,
		/** Stays put at its summon spot with minimal movement. */
		STAY,
		/** Follows its asker, staying in front at head height. */
		FOLLOW,
		/** Avoids the asker's gaze playfully. */
		AVOID_GAZE,
		/** Circles around the asker at a distance. */
		CIRCLE
	}

	/** The eight idles, as named in its .animation.json, how long each plays (seconds), and how often each is picked. */
	static final String[] IDLES = {"hover", "crow", "flip", "sit", "swoop", "lounge", "spin", "laugh"};
	static final float[] IDLE_SECONDS = {1.2F, 1.3F, 0.7F, 1.8F, 0.8F, 1.8F, 0.6F, 1.2F};
	static final int[] IDLE_WEIGHT = {3, 1, 1, 1, 1, 1, 1, 1};
	/**
	 * The four it wanders between while it thinks (an answer is coming, {@link #THINKING}): chin in hand, pacing the air,
	 * scratching its head, hanging upside down. Never the same one twice running, and only a short hover between them,
	 * so it is plainly busy, not frozen. How long each plays (seconds).
	 */
	static final String[] THINKS = {"ponder", "pace", "scratch", "upside"};
	static final float[] THINK_SECONDS = {2.0F, 2.4F, 1.4F, 2.0F};
	/** The hover between two thinking animations (ticks, a random length between these). */
	static final int THINK_GAP_MIN = 4, THINK_GAP_MAX = 12;
	/** While it thinks, three dots of light wink on over its head in turn, one every this many ticks. */
	static final int DOT_EVERY = 6;
	/** Thinking: summoned for a question whose answer hasn't come yet. Synced, so the client plays the thinking set. */
	static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> THINKING = net.minecraft.network.syncher.SynchedEntityData
		.defineId(YouthWisp.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
	static final RawAnimation HOVER = RawAnimation.begin().thenLoop("hover");
	static final RawAnimation APPEAR = RawAnimation.begin().thenPlay("appear");
	static final RawAnimation VANISH = RawAnimation.begin().thenPlayAndHold("vanish");
	static final RawAnimation TALK = RawAnimation.begin().thenPlay("talk");

	/** The wisps in the world, by their asker (server side). */
	static final Map<UUID, YouthWisp> BY_ASKER = new HashMap<>();

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private @Nullable UUID asker;
	private Vec3 spot = Vec3.ZERO;
	private int lastAsked;
	private int lastHint = -HINT_COOLDOWN - 1;
	private int vanishing = -1;
	/** What it was shown ("youth, look!"), while it hovers there asking "This?"; null when it isn't looking at anything. */
	private @Nullable Shown shown;
	/** The current idle behavior mode. */
	private BehaviorMode behaviorMode = BehaviorMode.DRIFT;
	/** Tick count when the behavior mode may next switch. */
	private int behaviorSwitchAt;

	// the idle picker's state (client side)
	private @Nullable RandomSource idleRandom;
	private RawAnimation idle = HOVER;
	private int idleIndex;
	private int idleUntil;
	private boolean wasThinking;

	public YouthWisp(EntityType<? extends YouthWisp> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.noPhysics = true;
		this.setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(THINKING, false);
	}

	/** Summoned for a question whose answer hasn't come yet. */
	boolean pending() {
		return this.entityData.get(THINKING);
	}

	void setPending(boolean pending) {
		this.entityData.set(THINKING, pending);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	// ---------------------------------------------------------------- calling it, sending it home

	/** Brings {@code player}'s wisp in beside them, or moves it there if it's already about. */
	public static @Nullable YouthWisp summon(ServerPlayer player) {
		YouthWisp wisp = BY_ASKER.get(player.getUUID());
		if (wisp != null && (wisp.isRemoved() || wisp.vanishing >= 0 || wisp.level() != player.level())) {
			if (!wisp.isRemoved()) {
				wisp.discard();
			}
			wisp = null;
		}
		Vec3 spot = spotBeside(player);
		if (wisp != null) {
			wisp.spot = spot;
			wisp.shown = null;
			wisp.lastAsked = wisp.tickCount;
			wisp.behaviorMode = randomBehaviorMode(player.getRandom());
			wisp.behaviorSwitchAt = wisp.tickCount + BEHAVIOR_SWITCH_MIN + player.getRandom().nextInt(BEHAVIOR_SWITCH_MAX - BEHAVIOR_SWITCH_MIN);
			wisp.faceAsker();
			return wisp;
		}
		if (BY_ASKER.size() >= MAX_WISPS) {
			BY_ASKER.values().stream().filter(w -> w.vanishing < 0).min((a, b) -> Integer.compare(a.lastAsked - a.tickCount, b.lastAsked - b.tickCount))
				.ifPresent(YouthWisp::vanish);
		}
		ServerLevel level = player.level();
		wisp = Inhabitants.YOUTH_WISP.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (wisp == null) {
			return null;
		}
		wisp.asker = player.getUUID();
		wisp.spot = spot;
		wisp.setPos(spot);
		wisp.behaviorMode = randomBehaviorMode(player.getRandom());
		wisp.behaviorSwitchAt = wisp.tickCount + BEHAVIOR_SWITCH_MIN + player.getRandom().nextInt(BEHAVIOR_SWITCH_MAX - BEHAVIOR_SWITCH_MIN);
		wisp.faceAsker();
		level.addFreshEntity(wisp);
		BY_ASKER.put(player.getUUID(), wisp);
		wisp.triggerAnim("action", "appear");
		Fx.burst(level, spot.x, spot.y + 0.5, spot.z, COLOUR, 0.8F, 24, 0.35, 0.45, 0.35);
		return wisp;
	}

	/**
	 * {@code player}'s wisp comes for a question (what {@code /youth summon} does: Hermes summons it the moment someone
	 * asks). If the answer takes so long that the wisp would fade, it says a "be right back" line first ("Studying
	 * that!"); the answer, when it comes ({@link #say}), brings it back.
	 */
	public static @Nullable YouthWisp hold(ServerPlayer player) {
		YouthWisp wisp = summon(player);
		if (wisp != null) {
			wisp.setPending(true);
		}
		return wisp;
	}

	/**
	 * A chat line someone said. A cry for help ({@link #HELP}) with a wisp in sight carries them home ({@link #carryHome}).
	 * Otherwise, if it names Youth anywhere, or it is a question ({@link #QUESTION}) while their own wisp is beside them
	 * (a follow-up), their wisp comes, and the line goes in the server log as
	 * {@code [youth-ask] <account name> <question>} (the words after its name when it leads, else the whole line; empty
	 * when they only said its name). Chat here is proximity chat, which never reaches the log in vanilla's form, and shows
	 * nicknames ("Stranger"), so this fixed line is what Hermes's listener reads. The account name is what
	 * {@code /youth summon} and {@code /youth say} take.
	 */
	public static void heard(ServerPlayer player, String text) {
		java.util.regex.Matcher lead = LEAD.matcher(text);
		String rest = lead.matches() ? lead.group(1) : text;
		// sent off: says goodbye and goes (with its name, or bare while it's beside them)
		if ((lead.matches() || has(player)) && BYE.matcher(rest.strip()).matches()) {
			YouthWisp wisp = BY_ASKER.get(player.getUUID());
			if (wisp != null && has(player)) {
				wisp.triggerAnim("action", "talk");
				wisp.speak(player, Component.translatable("message.jaymoskingdoms.youth_wisp.bye." + wisp.random.nextInt(BYE_LINES)));
				wisp.vanish();
			}
			return;
		}
		if (looked(player, lead.matches(), rest.strip())) {
			return;
		}
		if (HELP.matcher(rest.strip()).matches()) {
			YouthWisp seen = inSight(player);
			if (seen != null) {
				seen.carryHome(player);
				return;
			}
		}
		// its name calls it; with its wisp already beside them, a plain question is asked of it too
		if (CALL.matcher(text).find() || has(player) && QUESTION.matcher(text.strip()).matches()) {
			YouthWisp wisp = summon(player);
			String question = rest.replaceAll("\\s+", " ").strip();
			if (wisp != null) {
				wisp.lastAsked = wisp.tickCount;
				// a question (not only its name) and it starts thinking at once, till the answer comes
				if (!question.isEmpty()) {
					wisp.setPending(true);
				}
			}
			com.jaymoskingdoms.JaymosKingdoms.LOGGER.info("[youth-ask] {} {}", player.getGameProfile().name(), question);
		}
	}

	/** What a wisp was shown: an entity (followed as it moves) or a spot on a block, what to call it, and the question. */
	record Shown(@Nullable Entity entity, Vec3 at, String what, String question, int until) {
	}

	/**
	 * Showing it something (Andy): "youth, look!" (or "youth, look, what's this?") and the wisp flies to whatever the
	 * player's crosshair is on, hovers round it and asks "This?". A yes ({@link #YES}), or any question, and the ask goes
	 * to Hermes with what it is: {@code [youth-ask] <name> <question> (pointing at: <id> "<name>" at x y z)}, and it thinks
	 * there till the answer comes. A no ({@link #NO}) and it looks again at wherever they point now. Unanswered, it comes
	 * back after {@link #LOOK_WAIT}. True when the line was one of these (nothing else is done with it).
	 */
	static boolean looked(ServerPlayer player, boolean named, String rest) {
		YouthWisp wisp = has(player) ? BY_ASKER.get(player.getUUID()) : null;
		java.util.regex.Matcher look = LOOK.matcher(rest);
		if (look.matches() && (named || wisp != null)) {
			wisp = wisp != null ? wisp : summon(player);
			if (wisp != null) {
				wisp.show(player, look.group(1).strip());
			}
			return true;
		}
		if (wisp == null || wisp.shown == null) {
			return false;
		}
		Shown shown = wisp.shown;
		if (NO.matcher(rest).matches()) {
			wisp.show(player, shown.question());
			return true;
		}
		boolean yes = YES.matcher(rest).matches();
		if (yes && wisp.pending()) {
			// already asked about it: a second yes changes nothing
			return true;
		}
		if (!yes && !QUESTION.matcher(rest).matches()) {
			return false;
		}
		String question = yes ? shown.question() : rest;
		if (question.isEmpty()) {
			question = "what is this?";
		}
		BlockPos pos = BlockPos.containing(shown.at());
		wisp.lastAsked = wisp.tickCount;
		wisp.setPending(true);
		wisp.shown = new Shown(shown.entity(), shown.at(), shown.what(), question, wisp.tickCount + LOOK_WAIT);
		wisp.triggerAnim("action", "talk");
		com.jaymoskingdoms.JaymosKingdoms.LOGGER.info("[youth-ask] {} {} (pointing at: {} at {} {} {})", player.getGameProfile().name(),
			question.replaceAll("\\s+", " "), shown.what(), pos.getX(), pos.getY(), pos.getZ());
		return true;
	}

	/** Flies to what {@code player} is pointing at and asks "This?" (or says it can't see anything there). */
	void show(ServerPlayer player, String question) {
		this.lastAsked = this.tickCount;
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle(), end = eye.add(look.scale(LOOK_RANGE));
		net.minecraft.world.phys.AABB box = player.getBoundingBox().expandTowards(look.scale(LOOK_RANGE)).inflate(1.0);
		net.minecraft.world.phys.EntityHitResult mob = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> !(e instanceof YouthWisp) && !e.isSpectator() && e.isPickable(), LOOK_RANGE * LOOK_RANGE);
		net.minecraft.world.phys.HitResult block = player.pick(LOOK_RANGE, 1.0F, false);
		double blockSq = block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? block.getLocation().distanceToSqr(eye) : Double.MAX_VALUE;
		if (mob != null && mob.getLocation().distanceToSqr(eye) <= blockSq && player.hasLineOfSight(mob.getEntity())) {
			Entity entity = mob.getEntity();
			String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
			this.shown = new Shown(entity, entity.position(), id + " \"" + entity.getName().getString() + "\"", question, this.tickCount + LOOK_WAIT);
		} else if (block instanceof net.minecraft.world.phys.BlockHitResult hit && block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(hit.getBlockPos());
			String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
			this.shown = new Shown(null, Vec3.atCenterOf(hit.getBlockPos()), id + " \"" + state.getBlock().getName().getString() + "\"", question,
				this.tickCount + LOOK_WAIT);
		} else {
			this.shown = null;
			this.triggerAnim("action", "talk");
			this.speak(player, Component.translatable("message.jaymoskingdoms.youth_wisp.look_miss"));
			return;
		}
		this.setPending(false);
		this.triggerAnim("action", "talk");
		this.speak(player, Component.translatable("message.jaymoskingdoms.youth_wisp.look." + this.random.nextInt(LOOK_LINES)));
	}

	/** Hovers round what it was shown, facing its asker; false once that's over (gone, or no answer in time). */
	private boolean hoverShown(ServerPlayer player) {
		Shown shown = this.shown;
		if (shown == null) {
			return false;
		}
		if (this.tickCount > shown.until() || shown.entity() != null && (shown.entity().isRemoved() || shown.entity().level() != this.level())) {
			this.shown = null;
			return false;
		}
		// above the thing, a little toward the asker, circling it
		Vec3 base = shown.entity() != null ? shown.entity().position().add(0, shown.entity().getBbHeight() + 0.4, 0) : shown.at().add(0, 0.9, 0);
		Vec3 toward = player.position().subtract(base).multiply(1, 0, 1);
		toward = toward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toward.normalize().scale(0.5);
		double t = (this.tickCount + this.getId() * 37) / 14.0;
		Vec3 target = base.add(toward).add(Math.cos(t) * 0.6, Math.sin(t * 1.7) * 0.15, Math.sin(t) * 0.6);
		this.setPos(this.position().add(target.subtract(this.position()).scale(0.2)));
		this.face(player);
		return true;
	}

	/** The nearest wisp {@code player} can see on their screen (near, in clear view, before them), or null. */
	static @Nullable YouthWisp inSight(ServerPlayer player) {
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		YouthWisp best = null;
		double bestSq = HELP_RANGE * HELP_RANGE;
		for (YouthWisp wisp : BY_ASKER.values()) {
			if (wisp.isRemoved() || wisp.vanishing >= 0 || wisp.level() != player.level()) {
				continue;
			}
			Vec3 to = wisp.getBoundingBox().getCenter().subtract(eye);
			double sq = to.lengthSqr();
			if (sq < bestSq && (sq < 1.0 || to.normalize().dot(look) >= HELP_VIEW) && player.hasLineOfSight(wisp)) {
				best = wisp;
				bestSq = sq;
			}
		}
		return best;
	}

	/**
	 * {@code player} cried for help where this wisp could see them: it carries them to their bed (or wherever they'd wake),
	 * in a green flash, leaves and light rising where they stood and falling where they land, and goes home itself. Once
	 * a day (Andy): cry again the same day and it only makes a joke. With no bed to go to, it says so and stays (and the
	 * day's trip isn't spent).
	 */
	void carryHome(ServerPlayer player) {
		this.triggerAnim("action", "talk");
		this.lastAsked = this.tickCount;
		long day = player.level().getOverworldClockTime() / 24000L;
		Long last = player.getAttached(CARRIED_DAY);
		if (last != null && last == day) {
			this.speak(player, Component.translatable("message.jaymoskingdoms.youth_wisp.joke." + this.random.nextInt(JOKE_LINES)));
			return;
		}
		net.minecraft.world.level.portal.TeleportTransition home = player.getRespawnConfig() == null ? null
			: player.findRespawnPositionAndUseSpawnBlock(false, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING);
		if (home == null || home.missingRespawnBlock()) {
			this.speak(player, Component.translatable(home == null
				? "message.jaymoskingdoms.youth_wisp.no_bed" : "message.jaymoskingdoms.youth_wisp.bed_gone"));
			return;
		}
		this.speak(player, Component.translatable("message.jaymoskingdoms.youth_wisp.home." + this.random.nextInt(HOME_LINES)));
		if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, HomeFlash.TYPE)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new HomeFlash());
		}
		player.setAttached(CARRIED_DAY, day);
		homeLight(player.level(), player.position(), true);
		player.stopRiding();
		if (player.teleport(home) != null) {
			homeLight(home.newLevel(), home.position(), false);
		}
		this.vanish();
	}

	/** The layered light of a trip home at {@code at}: a sigil and ring at the feet, a blast, and leaves and motes rising (or falling). */
	private static void homeLight(ServerLevel level, Vec3 at, boolean leaving) {
		Fx.sigil(level, at.add(0, 0.05, 0), 1.6F, HOME_COLOUR, 30);
		Fx.ring(level, at, 2.5F, HOME_COLOUR);
		Fx.blast(level, at.add(0, 1.0, 0), 1.2F, COLOUR);
		Fx.burst(level, at.x, at.y + 1.0, at.z, HOME_COLOUR, 0.9F, 30, 0.4, 0.8, 0.4);
		double rise = leaving ? 0.12 : -0.08;
		level.sendParticles(Fx.wisp(COLOUR, 0.8F), at.x, at.y + (leaving ? 0.2 : 2.2), at.z, 16, 0.35, 0.3, 0.35, 0.02);
		level.sendParticles(Fx.mote(HOME_COLOUR, 0.7F), at.x, at.y + 1.0, at.z, 0, 0.0, rise, 0.0, 1.0);
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6.0;
			level.sendParticles(Fx.mote(i % 2 == 0 ? HOME_COLOUR : COLOUR, 0.6F), at.x + Math.cos(a) * 0.8, at.y + 0.2 + i * 0.15,
				at.z + Math.sin(a) * 0.8, 0, 0.0, rise, 0.0, 1.0);
		}
	}

	/** Server to the one carried home: flash the screen green. */
	public record HomeFlash() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
		public static final Type<HomeFlash> TYPE = new Type<>(com.jaymoskingdoms.JaymosKingdoms.id("youth_home_flash"));
		public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, HomeFlash> CODEC =
			net.minecraft.network.codec.StreamCodec.unit(new HomeFlash());

		@Override
		public Type<HomeFlash> type() {
			return TYPE;
		}
	}

	/** Now and then, a villager's word of the wisps: just say its name; or, sideways, what it does. Null most times. */
	public static @Nullable Component gossip(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD || level.getRandom().nextFloat() >= GOSSIP_CHANCE) {
			return null;
		}
		return Component.translatable("talk.jaymoskingdoms.gossip.youth." + level.getRandom().nextInt(GOSSIP_LINES));
	}

	/** Sends {@code player}'s wisp home; false if they had none. */
	public static boolean dismiss(ServerPlayer player) {
		YouthWisp wisp = BY_ASKER.get(player.getUUID());
		if (wisp == null || wisp.isRemoved()) {
			return false;
		}
		wisp.vanish();
		return true;
	}

	/** {@code player}'s wisp gestures, and {@code text} goes to them in chat as Youth's words, and floats over the wisp. */
	public static void say(ServerPlayer player, String text) {
		YouthWisp wisp = gesture(player);
		Component words = Component.literal(text);
		if (wisp != null) {
			wisp.speak(player, words);
		} else {
			player.sendSystemMessage(line(words));
		}
	}

	/**
	 * This wisp says {@code words} to {@code player}: in chat (so the whole answer is kept) and in a speech bubble over the
	 * wisp in its own colour, which anyone near sees too ({@link com.jaymoskingdoms.Speech#sayPlainly}).
	 */
	void speak(ServerPlayer player, Component words) {
		player.sendSystemMessage(line(words));
		if (this.level() instanceof ServerLevel level) {
			com.jaymoskingdoms.Speech.sayPlainly(level, this, player, words, COLOUR);
		}
	}

	/** {@code player}'s wisp comes (if it isn't about) and gestures as it talks. */
	static @Nullable YouthWisp gesture(ServerPlayer player) {
		YouthWisp wisp = summon(player);
		if (wisp != null) {
			wisp.lastAsked = wisp.tickCount;
			wisp.setPending(false);
			wisp.triggerAnim("action", "talk");
		}
		return wisp;
	}

	/** One line of Youth's in chat: its name in its own colour, then the words. */
	static Component line(Component text) {
		return Component.literal("[").withStyle(ChatFormatting.DARK_GREEN)
			.append(Component.translatable("entity.jaymoskingdoms.youth_wisp").withColor(COLOUR))
			.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GREEN))
			.append(text.copy().withStyle(ChatFormatting.WHITE));
	}

	/** Whether {@code player} has a wisp about. */
	static boolean has(ServerPlayer player) {
		YouthWisp wisp = BY_ASKER.get(player.getUUID());
		return wisp != null && !wisp.isRemoved() && wisp.vanishing < 0;
	}

	/** In front of the player at about head height, in open air (above them if the way ahead is blocked). */
	static Vec3 spotBeside(ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
		Vec3 ahead = player.position().add(look.scale(AHEAD)).add(0, HEIGHT, 0);
		ServerLevel level = player.level();
		if (level.noCollision(Inhabitants.YOUTH_WISP.getSpawnAABB(ahead.x, ahead.y, ahead.z))) {
			return ahead;
		}
		return player.position().add(0, player.getBbHeight() + 0.3, 0);
	}

	void vanish() {
		if (this.vanishing < 0) {
			this.vanishing = VANISH_TICKS;
			this.triggerAnim("action", "vanish");
			if (this.level() instanceof ServerLevel level) {
				Fx.burst(level, this.getX(), this.getY() + 0.5, this.getZ(), COLOUR, 0.8F, 18, 0.3, 0.4, 0.3);
			}
		}
	}

	private @Nullable ServerPlayer askerPlayer() {
		if (this.asker == null || !(this.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getServer().getPlayerList().getPlayer(this.asker);
	}

	private void faceAsker() {
		ServerPlayer player = this.askerPlayer();
		if (player != null) {
			this.face(player);
		}
	}

	private void face(Entity target) {
		double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
		double dy = target.getEyeY() - this.getEyeY();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
		this.setYBodyRot(yaw);
		this.setXRot(Mth.clamp(pitch, -40.0F, 40.0F));
	}

	/** Picks a random behavior mode, weighted so DRIFT and STAY are more common. */
	private static BehaviorMode randomBehaviorMode(RandomSource random) {
		int roll = random.nextInt(10);
		if (roll < 3) return BehaviorMode.DRIFT;
		if (roll < 5) return BehaviorMode.STAY;
		if (roll < 7) return BehaviorMode.FOLLOW;
		if (roll < 9) return BehaviorMode.AVOID_GAZE;
		return BehaviorMode.CIRCLE;
	}

	/** Whether the given player is currently looking at this wisp (within the HELP_VIEW cone). */
	private boolean isPlayerLookingAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		Vec3 toWisp = this.getBoundingBox().getCenter().subtract(eye);
		double sq = toWisp.lengthSqr();
		if (sq < 1.0E-4) {
			return true;
		}
		return toWisp.normalize().dot(look) >= HELP_VIEW;
	}

	// ---------------------------------------------------------------- each tick

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.vanishing >= 0) {
			if (--this.vanishing <= 0) {
				this.discard();
			}
			return;
		}
		ServerPlayer player = this.askerPlayer();
		double leash = this.shown != null ? LOOK_RANGE + LEASH : LEASH;
		if (player == null || player.level() != level || player.distanceToSqr(this) > leash * leash
			|| this.tickCount - this.lastAsked > (this.pending() ? ANSWER_TIMEOUT : IDLE_TIMEOUT) || BY_ASKER.get(this.asker) != this) {
			if (player != null && this.pending() && this.tickCount - this.lastAsked > ANSWER_TIMEOUT) {
				this.triggerAnim("action", "talk");
				this.speak(player, Component.translatable("message.jaymoskingdoms.youth_wisp.hold." + this.random.nextInt(HOLD_LINES)));
			}
			this.vanish();
			return;
		}
		if (this.hoverShown(player)) {
			this.motes(level);
			return;
		}
		// switch to a new behavior mode if it's time
		if (this.tickCount >= this.behaviorSwitchAt) {
			this.behaviorMode = randomBehaviorMode(this.random);
			this.behaviorSwitchAt = this.tickCount + BEHAVIOR_SWITCH_MIN + this.random.nextInt(BEHAVIOR_SWITCH_MAX - BEHAVIOR_SWITCH_MIN);
		}

		double t = this.tickCount + this.getId() * 37;
		Player nearest = level.getNearestPlayer(this, 8.0);

		switch (this.behaviorMode) {
			case STAY:
				// stay at the summon spot with tiny drift
				Vec3 stayTarget = this.spot.add(Math.sin(t / 80.0) * DRIFT * 0.2, Math.sin(t / 60.0) * DRIFT * 0.05, Math.cos(t / 80.0) * DRIFT * 0.2);
				this.setPos(this.position().add(stayTarget.subtract(this.position()).scale(FOLLOW * 0.8)));
				this.face(nearest != null ? nearest : player);
				break;
			case FOLLOW:
				// drift in front of the asker at head height, moving with them
				Vec3 look = player.getLookAngle().multiply(1, 0, 1);
				look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
				Vec3 followSpot = player.position().add(look.scale(AHEAD * 0.9)).add(0, HEIGHT + 0.15, 0);
				this.spot = followSpot;
				Vec3 followTarget = followSpot.add(Math.sin(t / 47.0) * 0.3, Math.sin(t / 29.0) * 0.1, Math.cos(t / 53.0) * 0.3);
				this.setPos(this.position().add(followTarget.subtract(this.position()).scale(0.15)));
				this.face(player);
				break;
			case AVOID_GAZE:
				// if the asker is looking at the wisp, drift away; otherwise drift back
				Vec3 toPlayer = player.position().subtract(this.position());
				toPlayer = toPlayer.multiply(1, 0, 1);
				double dist = toPlayer.length();
				toPlayer = dist < 1.0E-4 ? new Vec3(0, 0, 1) : toPlayer.normalize();
				Vec3 avoidSpot;
				if (this.isPlayerLookingAt(player)) {
					// slip out of the asker's gaze to its side (a fixed spot beside the asker, so it can't run off)
					Vec3 gaze = player.getLookAngle().multiply(1, 0, 1);
					gaze = gaze.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : gaze.normalize();
					Vec3 side = new Vec3(-gaze.z, 0, gaze.x);
					if (side.dot(toPlayer) > 0) {
						side = side.scale(-1); // stay on the side it is already on
					}
					avoidSpot = player.position().add(side.scale(AHEAD)).add(0, HEIGHT + 0.2, 0);
				} else {
					// drift back toward the asker's POV
					Vec3 lookAway = player.getLookAngle().multiply(1, 0, 1);
					lookAway = lookAway.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : lookAway.normalize();
					avoidSpot = player.position().add(lookAway.scale(AHEAD * 0.7)).add(0, HEIGHT, 0);
				}
				this.spot = avoidSpot;
				this.setPos(this.position().add(avoidSpot.subtract(this.position()).scale(0.12)));
				this.face(nearest != null ? nearest : player);
				break;
			case CIRCLE:
				// circle the asker at a distance
				double angle = t / 80.0;
				Vec3 circleSpot = player.position().add(Math.sin(angle) * 3.0, HEIGHT + 0.3, Math.cos(angle) * 3.0);
				this.spot = circleSpot;
				this.setPos(this.position().add(circleSpot.subtract(this.position()).scale(0.1)));
				this.face(player);
				break;
			case DRIFT:
			default:
				// drift slowly about its spot, and turn to whoever is nearest (its asker, most often)
				Vec3 target = this.spot.add(Math.sin(t / 47.0) * DRIFT, Math.sin(t / 29.0) * DRIFT * 0.3, Math.cos(t / 53.0) * DRIFT);
				this.setPos(this.position().add(target.subtract(this.position()).scale(FOLLOW)));
				this.face(nearest != null ? nearest : player);
				break;
		}
		this.motes(level);
	}

	/** Its motes, and the thinking dots over its head while an answer is coming. */
	private void motes(ServerLevel level) {
		if (this.tickCount % MOTE_EVERY == 0) {
			level.sendParticles(Fx.mote(COLOUR, 0.6F), this.getX(), this.getY() + 0.3, this.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
		}
		if (this.pending() && this.tickCount % DOT_EVERY == 0) {
			// "...": a dot over its head, left to right across where it faces, then again
			int dot = this.tickCount / DOT_EVERY % 3 - 1;
			float yaw = this.getYRot() * Mth.DEG_TO_RAD;
			double sx = Math.cos(yaw) * 0.18 * dot, sz = Math.sin(yaw) * 0.18 * dot;
			level.sendParticles(Fx.mote(COLOUR, 0.5F), this.getX() + sx, this.getY() + this.getBbHeight() + 0.25, this.getZ() + sz,
				1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		if (this.asker != null && BY_ASKER.get(this.asker) == this) {
			BY_ASKER.remove(this.asker);
		}
	}

	// ---------------------------------------------------------------- a harmless thing of light

	@Override
	protected void registerGoals() {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.discard(); // /kill, or the void: just gone
			return true;
		}
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		// one hint per click (not one per hand), and not again within a few seconds
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
			&& this.tickCount - this.lastHint > HINT_COOLDOWN) {
			this.lastHint = this.tickCount;
			this.speak(serverPlayer, Component.translatable("message.jaymoskingdoms.youth_wisp.how"));
		}
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- animation

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<YouthWisp>("idle", 6, test -> test.setAndContinue(test.animatable().nextIdle())));
		controllers.add(new AnimationController<YouthWisp>("action", test -> PlayState.STOP)
			.triggerableAnim("appear", APPEAR).triggerableAnim("vanish", VANISH).triggerableAnim("talk", TALK));
	}

	/**
	 * Which idle plays now (client side, every frame): when one ends it picks the next by weight, never the one just
	 * played, and hovers a while in between. Each wisp has its own dice, so two side by side don't move in step.
	 */
	RawAnimation nextIdle() {
		if (this.idleRandom == null) {
			this.idleRandom = RandomSource.create(this.getId() * 0x9E3779B97F4A7C15L);
			this.idleUntil = this.tickCount + gap(this.idleRandom);
		}
		boolean thinking = this.pending();
		if (thinking != this.wasThinking) {
			// starts (or stops) thinking: the next animation now, from the other set
			this.wasThinking = thinking;
			this.idleIndex = -1;
			this.idleUntil = this.tickCount;
		}
		if (this.tickCount < this.idleUntil) {
			return this.idle;
		}
		RandomSource random = this.idleRandom;
		if (thinking) {
			int pick = random.nextInt(this.idleIndex >= 0 && this.idleIndex < THINKS.length ? THINKS.length - 1 : THINKS.length);
			if (this.idleIndex >= 0 && pick >= this.idleIndex) {
				pick++;
			}
			this.idleIndex = pick;
			this.idle = RawAnimation.begin().thenPlay(THINKS[pick]).thenLoop("hover");
			this.idleUntil = this.tickCount + Math.round(THINK_SECONDS[pick] * 20) + THINK_GAP_MIN
				+ random.nextInt(THINK_GAP_MAX - THINK_GAP_MIN + 1);
			return this.idle;
		}
		int total = 0;
		for (int i = 0; i < IDLES.length; i++) {
			total += i == this.idleIndex ? 0 : IDLE_WEIGHT[i];
		}
		int roll = random.nextInt(total), pick = 0;
		for (int i = 0; i < IDLES.length; i++) {
			if (i == this.idleIndex) {
				continue;
			}
			roll -= IDLE_WEIGHT[i];
			if (roll < 0) {
				pick = i;
				break;
			}
		}
		this.idleIndex = pick;
		this.idle = pick == 0 ? HOVER : RawAnimation.begin().thenPlay(IDLES[pick]).thenLoop("hover");
		this.idleUntil = this.tickCount + (pick == 0 ? 0 : Math.round(IDLE_SECONDS[pick] * 20)) + gap(random);
		return this.idle;
	}

	private static int gap(RandomSource random) {
		return GAP_MIN + random.nextInt(GAP_MAX - GAP_MIN + 1);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	// ---------------------------------------------------------------- for /youth status

	/**
	 * The online players as one line of JSON: name, the display name chat shows (a nickname can differ from the account
	 * name, and Hermes reads chat from the log), dimension, block position, and whether their wisp is about.
	 */
	static String status(List<ServerPlayer> players) {
		List<String> rows = new ArrayList<>();
		for (ServerPlayer p : players) {
			rows.add(String.format("{\"name\":\"%s\",\"display\":\"%s\",\"dim\":\"%s\",\"x\":%d,\"y\":%d,\"z\":%d,\"wisp\":%b}",
				json(p.getName().getString()), json(p.getDisplayName().getString()), p.level().dimension().identifier(),
				p.getBlockX(), p.getBlockY(), p.getBlockZ(), has(p)));
		}
		return "{\"players\":[" + String.join(",", rows) + "]}";
	}

	/** Text made safe inside a JSON string: quotes and backslashes escaped, control characters dropped. */
	private static String json(String text) {
		return text.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("\\p{Cntrl}", "");
	}
}
