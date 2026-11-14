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
	static final int IDLE_TIMEOUT = 2 * 60 * 20;
	/** Ticks between two right-click hints from the same wisp. */
	static final int HINT_COOLDOWN = 5 * 20;
	/** A chat line that calls Youth: its name anywhere in it, as a word, in any case ("youth, how does Crush work?"). */
	static final java.util.regex.Pattern CALL = java.util.regex.Pattern.compile("\\byouth\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
	/** Its name at the front of a line, and what follows it (the question). */
	static final java.util.regex.Pattern LEAD = java.util.regex.Pattern.compile("^[!*]?\\s*youth\\b[\\s,:!.?-]*(.*)$",
		java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
	/** A cry for help ("HELP", "help me!", "youth, help me"), which carries its crier home if a wisp is in sight. */
	static final java.util.regex.Pattern HELP = java.util.regex.Pattern.compile("^help(\\s+me)?[\\s!.]*$", java.util.regex.Pattern.CASE_INSENSITIVE);
	/** A wisp is in sight for a cry for help this near (blocks), in clear view and within this cosine of where they look. */
	static final double HELP_RANGE = 24.0, HELP_VIEW = 0.45;
	/** The flash of the trip home: leaf green (0xRRGGBB). */
	static final int HOME_COLOUR = 0x5CE07A;
	/** How many lines it says as it carries someone home, and how many villager gossip lines there are of it. */
	static final int HOME_LINES = 3, GOSSIP_LINES = 8;
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

	/** The eight idles, as named in its .animation.json, how long each plays (seconds), and how often each is picked. */
	static final String[] IDLES = {"hover", "crow", "flip", "sit", "swoop", "lounge", "spin", "laugh"};
	static final float[] IDLE_SECONDS = {1.2F, 1.3F, 0.7F, 1.8F, 0.8F, 1.8F, 0.6F, 1.2F};
	static final int[] IDLE_WEIGHT = {3, 1, 1, 1, 1, 1, 1, 1};
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
	/** Summoned for a question whose answer hasn't come yet. */
	private boolean pending;
	private int vanishing = -1;

	// the idle picker's state (client side)
	private @Nullable RandomSource idleRandom;
	private RawAnimation idle = HOVER;
	private int idleIndex;
	private int idleUntil;

	public YouthWisp(EntityType<? extends YouthWisp> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.noPhysics = true;
		this.setPersistenceRequired();
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
			wisp.lastAsked = wisp.tickCount;
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
			wisp.pending = true;
		}
		return wisp;
	}

	/**
	 * A chat line someone said. A cry for help ({@link #HELP}) with a wisp in sight carries them home ({@link #carryHome}).
	 * Otherwise, if it names Youth anywhere, their wisp comes, and the line goes in the server log as
	 * {@code [youth-ask] <account name> <question>} (the words after its name when it leads, else the whole line; empty
	 * when they only said its name). Chat here is proximity chat, which never reaches the log in vanilla's form, and shows
	 * nicknames ("Stranger"), so this fixed line is what Hermes's listener reads. The account name is what
	 * {@code /youth summon} and {@code /youth say} take.
	 */
	public static void heard(ServerPlayer player, String text) {
		java.util.regex.Matcher lead = LEAD.matcher(text);
		String rest = lead.matches() ? lead.group(1) : text;
		if (HELP.matcher(rest.strip()).matches()) {
			YouthWisp seen = inSight(player);
			if (seen != null) {
				seen.carryHome(player);
				return;
			}
		}
		if (CALL.matcher(text).find()) {
			YouthWisp wisp = summon(player);
			if (wisp != null) {
				wisp.lastAsked = wisp.tickCount;
			}
			com.jaymoskingdoms.JaymosKingdoms.LOGGER.info("[youth-ask] {} {}", player.getGameProfile().name(),
				rest.replaceAll("\\s+", " ").strip());
		}
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
	 * in a green flash, leaves and light rising where they stood and falling where they land, and goes home itself. With
	 * no bed to go to, it says so and stays.
	 */
	void carryHome(ServerPlayer player) {
		this.triggerAnim("action", "talk");
		this.lastAsked = this.tickCount;
		net.minecraft.world.level.portal.TeleportTransition home = player.getRespawnConfig() == null ? null
			: player.findRespawnPositionAndUseSpawnBlock(false, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING);
		if (home == null || home.missingRespawnBlock()) {
			player.sendSystemMessage(line(Component.translatable(home == null
				? "message.jaymoskingdoms.youth_wisp.no_bed" : "message.jaymoskingdoms.youth_wisp.bed_gone")));
			return;
		}
		player.sendSystemMessage(line(Component.translatable("message.jaymoskingdoms.youth_wisp.home." + this.random.nextInt(HOME_LINES))));
		if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, HomeFlash.TYPE)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new HomeFlash());
		}
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

	/** {@code player}'s wisp gestures, and {@code text} goes to them in chat as Youth's words. */
	public static void say(ServerPlayer player, String text) {
		gesture(player);
		player.sendSystemMessage(line(Component.literal(text)));
	}

	/** {@code player}'s wisp comes (if it isn't about) and gestures as it talks. */
	static void gesture(ServerPlayer player) {
		YouthWisp wisp = summon(player);
		if (wisp != null) {
			wisp.lastAsked = wisp.tickCount;
			wisp.pending = false;
			wisp.triggerAnim("action", "talk");
		}
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
		if (player == null || player.level() != level || player.distanceToSqr(this) > LEASH * LEASH
			|| this.tickCount - this.lastAsked > IDLE_TIMEOUT || BY_ASKER.get(this.asker) != this) {
			if (player != null && this.pending && this.tickCount - this.lastAsked > IDLE_TIMEOUT) {
				this.triggerAnim("action", "talk");
				player.sendSystemMessage(line(Component.translatable("message.jaymoskingdoms.youth_wisp.hold." + this.random.nextInt(HOLD_LINES))));
			}
			this.vanish();
			return;
		}
		// drift slowly about its spot, and turn to whoever is nearest (its asker, most often)
		double t = this.tickCount + this.getId() * 37;
		Vec3 target = this.spot.add(Math.sin(t / 47.0) * DRIFT, Math.sin(t / 29.0) * DRIFT * 0.3, Math.cos(t / 53.0) * DRIFT);
		this.setPos(this.position().add(target.subtract(this.position()).scale(FOLLOW)));
		Player nearest = level.getNearestPlayer(this, 8.0);
		this.face(nearest != null ? nearest : player);
		if (this.tickCount % MOTE_EVERY == 0) {
			level.sendParticles(Fx.mote(COLOUR, 0.6F), this.getX(), this.getY() + 0.3, this.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
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
			serverPlayer.sendSystemMessage(line(Component.translatable("message.jaymoskingdoms.youth_wisp.how")));
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
		if (this.tickCount < this.idleUntil) {
			return this.idle;
		}
		RandomSource random = this.idleRandom;
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
