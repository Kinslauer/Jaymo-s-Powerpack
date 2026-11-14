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
	/** ...or when its asker is further than this (blocks) or in another dimension. */
	static final double LEASH = 16.0;
	/** At most this many in the world at once; summoning one more sends the longest-idle home. */
	static final int MAX_WISPS = 4;
	/** Where it comes in: this far in front of its asker (blocks) and this far above their feet. */
	static final double AHEAD = 2.0, HEIGHT = 1.0;
	/** How far it drifts about its spot while it waits (blocks), and how quickly it follows (share of the gap a tick). */
	static final double DRIFT = 0.6, FOLLOW = 0.12;
	/** How long the vanish animation plays before it is gone (ticks). */
	static final int VANISH_TICKS = 20;
	/** The pause between two idles (ticks, a random length between these). */
	static final int GAP_MIN = 40, GAP_MAX = 120;
	/** Its light: a pale leaf-green (0xRRGGBB), for its motes and its name in chat. */
	static final int COLOUR = 0xBEF0D6;
	/** How often (ticks) a mote drifts off it while it waits. */
	static final int MOTE_EVERY = 12;

	/** The eight idles, as named in its .animation.json, how long each plays (seconds), and how often each is picked. */
	static final String[] IDLES = {"hover", "flutter", "loop", "sit", "stretch", "peer", "twirl", "cup"};
	static final float[] IDLE_SECONDS = {2.4F, 1.6F, 2.0F, 3.2F, 2.4F, 2.6F, 2.4F, 3.0F};
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
	public static YouthWisp summon(ServerPlayer player) {
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
		player.sendSystemMessage(line(text));
	}

	/** {@code player}'s wisp comes (if it isn't about) and gestures as it talks. */
	static void gesture(ServerPlayer player) {
		YouthWisp wisp = summon(player);
		if (wisp != null) {
			wisp.lastAsked = wisp.tickCount;
			wisp.triggerAnim("action", "talk");
		}
	}

	/** One line of Youth's in chat: its name in its own colour, then the words. */
	static Component line(String text) {
		return Component.literal("[").withStyle(ChatFormatting.DARK_GREEN)
			.append(Component.translatable("entity.jaymoskingdoms.youth_wisp").withColor(COLOUR))
			.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GREEN))
			.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
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
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(line(Component.translatable("message.jaymoskingdoms.youth_wisp.how").getString()));
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
