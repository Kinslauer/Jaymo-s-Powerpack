package com.jaymoskingdoms;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.jaymoskingdoms.mixin.MobAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.103) Mob senses, the Stealth tree's answer to being seen by monsters (the ideas of Dynamic Stealth, written afresh).
 * <ul>
 * <li><b>Sight</b> ({@link #factor}, from {@code LivingEntityVisibilityMixin}): how far a hostile or neutral mob notices a
 * player is its follow range times this. Vanilla's own sight (invisibility, mob heads) and Darktide's veil are applied
 * first and this multiplies on top. It is the light at the eyes (down to {@link #LIGHT_MIN}), sneaking ({@link #SNEAK}, or
 * Quiet Step's), standing still ({@link #STILL}), where the mob looks ({@link #BEHIND} outside its {@link #VIEW_COS} cone,
 * unless within {@link #BEHIND_CLOSE}), a hood ({@link Identities#HOOD_SIGHT}), glowing or burning ({@link #LIT}). A mob
 * already after you, with you in sight, sees you at full range. Creative and spectator players are untouched.</li>
 * <li><b>Losing track</b> ({@link WatchGoal}, {@link SearchGoal}): a mob that has not had line of sight to its player target
 * for {@link #LOSE_TRACK_TICKS} drops them, walks to where they were last seen and looks around for
 * {@link #LOOK_TICKS}. Bosses and the kingdoms' own folk are exempt. State lives in {@link #TRACKS}, one per mob (not an attachment: Fabric would copy it onto a converted mob). Spiders keep their own memory ({@code wild.SpiderMinds}).</li>
 * <li><b>The gauge</b> ({@link Gauge}): the nearest mob's state, sent a few times a second only when it changes.</li>
 * <li><b>Unaware blows</b> ({@link #dealt}): a melee hit on a monster that isn't after you and can't see you (behind it, or
 * it is searching) is a sneak attack, {@link #SNEAK_ATTACK_MULT}; Sneak Attack's ranks make it more. The hidden blade's
 * full-strength stab on an unaware one is an assassination ({@link HiddenBlade}, Assassinate).</li>
 * </ul>
 */
public final class Senses {
	// ---- tuning ----
	/** Light at the eyes: sight in the dark (0) and in the light (15), as a share of a mob's range. */
	static final double LIGHT_MIN = 0.25, LIGHT_MAX = 1.0;
	/** Sneaking, standing still, glowing or burning, as shares of a mob's range. */
	static final double SNEAK = 0.6, STILL = 0.8, LIT = 1.5;
	/** Quiet Step: sneaking by rank 1 and rank 2 instead of {@link #SNEAK}. */
	static final double[] QUIET_STEP_SNEAK = {0.5, 0.4};
	/** Shadowed: per rank, this much less sight, in proportion to how dark it is where you stand. */
	static final double SHADOWED_PER_RANK = 0.1;
	/** A mob sees in front of it within this cosine of its look (0.5: a 120 degree cone); outside, this share, unless closer than {@link #BEHIND_CLOSE} blocks. */
	static final double VIEW_COS = 0.5, BEHIND = 0.3, BEHIND_CLOSE = 2.0;
	/** The least a mob's sight may be cut to. */
	static final double MIN_FACTOR = 0.05;
	/** Horizontal movement (blocks squared per tick) above which a player is moving, not standing still. */
	static final double MOVING_SQR = 0.0004;

	/** Ticks without line of sight before a mob drops its target; Slip the Net divides it by this. */
	static final int LOSE_TRACK_TICKS = 100, SLIP_THE_NET_DIVISOR = 2;
	/** Searching: the most ticks to walk to the last-seen spot, ticks spent looking around once there (5 s), and the radius of the looking (blocks). */
	static final int WALK_TICKS = 200, LOOK_TICKS = 100;
	static final double LOOK_RADIUS = 5.0, SEARCH_SPEED = 1.0;
	/** How near (blocks) is arrived. */
	static final double ARRIVED = 2.0;

	/** A sneak attack multiplies the blow by this; Sneak Attack rank 1 and rank 2 by these instead. */
	static final float SNEAK_ATTACK_MULT = 2.0F;
	static final float[] SNEAK_ATTACK_RANK_MULT = {2.5F, 3.0F};

	/** The gauge: how often it is worked out (ticks), how far it looks (blocks), and how many steps a "noticing" fill has. */
	static final int GAUGE_TICKS = 5;
	public static final int FILL_STEPS = 10;
	static final double GAUGE_RANGE = 40.0;

	/** The gauge's states. */
	public static final int IDLE = 0, NOTICING = 1, SEARCHING = 2, SPOTTED = 3;

	private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));

	/** What a mob remembers of the player it lost. Not saved: a reload forgets the hunt. */
	static final class Track {
		/** Ticks without line of sight to the target. */
		int blind;
		/** The player it was after, and where it last saw them. */
		@Nullable UUID who;
		@Nullable Vec3 seen;
		/** Searching, the spot, ticks left to get there (-1: arrived and looking), and ticks left looking. */
		boolean searching, started;
		@Nullable Vec3 spot;
		int walkLeft, lookLeft;
	}

	/** Every hostile or neutral mob's, made when it loads together with its goals. */
	private static final Map<Mob, Track> TRACKS = new WeakHashMap<>();

	/** Server → player: what to show on the awareness gauge (state, and for {@link #NOTICING} how full, in {@link #FILL_STEPS}). */
	public record Gauge(int state, int fill) implements CustomPacketPayload {
		public static final Type<Gauge> TYPE = new Type<>(JaymosKingdoms.id("senses_gauge"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Gauge> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeByte(p.state << 4 | p.fill),
			buf -> {
				int b = buf.readByte() & 0xFF;
				return new Gauge(b >> 4, b & 15);
			});

		@Override
		public Type<Gauge> type() {
			return TYPE;
		}
	}

	/** Player → where they were last tick, and whether they moved. */
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	private static final Map<UUID, Boolean> MOVING = new HashMap<>();
	/** Player → the gauge last sent. */
	private static final Map<UUID, Integer> SENT = new HashMap<>();
	/** Player → {target entity id, game time} of their last sneak attack (one blow, hurt twice, counts once). */
	private static final Map<UUID, long[]> STRUCK = new HashMap<>();

	private Senses() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Gauge.TYPE, Gauge.CODEC);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && minded(mob) && !(mob instanceof Spider) && !TRACKS.containsKey(mob)) {
				Track track = new Track();
				TRACKS.put(mob, track);
				var goals = ((MobAccessor) mob).jaymoskingdoms$goals();
				// no flags: counts beside everything else; the search wants the legs and the eyes
				goals.addGoal(0, new WatchGoal(mob, track));
				goals.addGoal(1, new SearchGoal(mob, track));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Senses::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_POS.clear();
			MOVING.clear();
			SENT.clear();
			STRUCK.clear();
			TRACKS.clear();
		});
	}

	private static void forget(UUID id) {
		LAST_POS.remove(id);
		MOVING.remove(id);
		SENT.remove(id);
		STRUCK.remove(id);
	}

	/** A mob that senses: hostile or neutral, not a boss, not one of the kingdoms' own folk. */
	static boolean minded(Mob mob) {
		return (mob instanceof Enemy || mob instanceof NeutralMob) && !boss(mob) && !(mob instanceof Folk);
	}

	static boolean boss(Mob mob) {
		EntityType<?> type = mob.getType();
		return type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN
			|| type.builtInRegistryHolder().is(BOSSES);
	}

	private static boolean touched(Entity player) {
		return player instanceof ServerPlayer p && (p.isCreative() || p.isSpectator());
	}

	// ---- sight -------------------------------------------------------------------------------------------------------

	/** Whether {@code looker} is after {@code player} and has them in sight (it keeps seeing them at full range). */
	private static boolean locked(Mob looker, ServerPlayer player) {
		return looker.getTarget() == player && looker.hasLineOfSight(player);
	}

	/** {@code player} is in front of {@code looker}, inside its view cone. */
	static boolean inCone(LivingEntity looker, LivingEntity player) {
		Vec3 to = player.getEyePosition().subtract(looker.getEyePosition());
		double len = to.length();
		if (len < 1.0E-4) {
			return true;
		}
		return looker.getViewVector(1.0F).dot(to.scale(1.0 / len)) >= VIEW_COS;
	}

	/**
	 * What a mob's range for noticing {@code player} is multiplied by, on top of vanilla's (see the class comment). 1 for a
	 * creative or spectator player, a boss, or anything that isn't a hostile or neutral mob.
	 */
	public static double factor(LivingEntity self, ServerLevel level, Entity looking) {
		if (!(self instanceof ServerPlayer player) || !(looking instanceof Mob looker) || touched(player) || !minded(looker) || locked(looker, player)) {
			return 1.0;
		}
		BlockPos eyes = BlockPos.containing(player.getEyePosition());
		double light = level.getMaxLocalRawBrightness(eyes) / 15.0;
		double f = LIGHT_MIN + (LIGHT_MAX - LIGHT_MIN) * light;
		f *= 1.0 - SHADOWED_PER_RANK * Skills.rank(player, Skills.Perk.SHADOWED) * (1.0 - light);
		if (player.isCrouching()) {
			int quiet = Skills.rank(player, Skills.Perk.QUIET_STEP);
			f *= Skills.sneakFactor(player, quiet <= 0 ? SNEAK : QUIET_STEP_SNEAK[Math.min(quiet, QUIET_STEP_SNEAK.length) - 1]);
		}
		if (!MOVING.getOrDefault(player.getUUID(), true)) {
			f *= STILL;
		}
		if (player.distanceTo(looker) > BEHIND_CLOSE && !inCone(looker, player)) {
			f *= BEHIND;
		}
		f *= Identities.hoodSight(player);
		if (player.isOnFire() || player.hasEffect(MobEffects.GLOWING)) {
			f *= LIT;
		}
		return Math.max(MIN_FACTOR, f);
	}

	// ---- losing track and searching ----------------------------------------------------------------------------------

	/** Counts the ticks a mob goes without seeing its target, and drops them when it has gone too long. Runs beside everything. */
	static final class WatchGoal extends Goal {
		private final Mob mob;
		private final Track track;

		WatchGoal(Mob mob, Track track) {
			this.mob = mob;
			this.track = track;
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.mob.getTarget();
			if (target instanceof ServerPlayer player && !touched(player)) {
				this.track.searching = false;
				if (this.track.who == null || !this.track.who.equals(player.getUUID())) {
					// (a new target: where it is now counts as seen, whether or not the mob has a clear line to it yet)
					this.track.who = player.getUUID();
					this.track.seen = player.position();
					this.track.blind = 0;
				}
				if (this.mob.tickCount % 2 != 0) {
					return;
				}
				if (this.mob.hasLineOfSight(player)) {
					this.track.blind = 0;
					this.track.who = player.getUUID();
					this.track.seen = player.position();
					return;
				}
				this.track.blind += 2;
				int limit = LOSE_TRACK_TICKS / (Skills.rank(player, Skills.Perk.SLIP_THE_NET) > 0 ? SLIP_THE_NET_DIVISOR : 1);
				if (this.track.blind >= limit && this.track.seen != null && this.track.who != null && this.track.who.equals(player.getUUID())) {
					lose(player);
				}
				return;
			}
			this.track.blind = 0;
			if (target != null) {
				this.track.searching = false;
			}
			if (this.track.searching) {
				search();
			}
		}

		/** Drops {@code player}; the mob goes to where it last saw them. */
		private void lose(ServerPlayer player) {
			this.mob.setTarget(null);
			if (this.mob instanceof NeutralMob neutral) {
				neutral.stopBeingAngry();
			}
			this.mob.setLastHurtByMob(null);
			this.mob.getNavigation().stop();
			this.track.blind = 0;
			this.track.searching = true;
			this.track.started = false;
			this.track.spot = this.track.seen;
			this.track.walkLeft = WALK_TICKS;
			this.track.lookLeft = -1;
		}

		/** The walk and the looking round count down here, so that a goal that outranks the search can't make it last for ever. */
		private void search() {
			if (this.track.lookLeft < 0) {
				boolean near = this.track.spot == null || this.mob.position().distanceToSqr(this.track.spot) < ARRIVED * ARRIVED;
				if (--this.track.walkLeft <= 0 || near || this.track.started && this.mob.getNavigation().isDone() && this.mob.tickCount % 10 == 0) {
					this.track.lookLeft = LOOK_TICKS;
				}
			} else if (--this.track.lookLeft <= 0) {
				this.track.searching = false;
				this.track.spot = null;
			}
		}
	}

	/** Walks to where the target was last seen, then looks about. */
	static final class SearchGoal extends Goal {
		private final Mob mob;
		private final Track track;

		SearchGoal(Mob mob, Track track) {
			this.mob = mob;
			this.track = track;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return this.track.searching && this.track.spot != null && this.mob.getTarget() == null;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			this.track.started = true;
			Vec3 s = this.track.spot;
			if (this.track.lookLeft < 0 && s != null) {
				this.mob.getNavigation().moveTo(s.x, s.y, s.z, SEARCH_SPEED);
			}
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			Vec3 s = this.track.spot;
			if (s == null) {
				return;
			}
			if (this.track.lookLeft < 0) {
				if (this.mob.tickCount % 20 == 0 && this.mob.getNavigation().isDone()) {
					this.mob.getNavigation().moveTo(s.x, s.y, s.z, SEARCH_SPEED);
				}
				this.mob.getLookControl().setLookAt(s.x, s.y + 1.0, s.z);
				return;
			}
			this.mob.getNavigation().stop();
			if (this.mob.tickCount % 20 == 0) {
				double angle = this.mob.getRandom().nextDouble() * Mth.TWO_PI;
				this.mob.getLookControl().setLookAt(this.mob.getX() + Math.cos(angle) * LOOK_RADIUS, this.mob.getEyeY() + this.mob.getRandom().nextDouble() - 0.5,
					this.mob.getZ() + Math.sin(angle) * LOOK_RADIUS);
			}
		}
	}

	/** {@code mob} is hunting for {@code player}, who slipped away. */
	static boolean searchingFor(Mob mob, Player player) {
		Track t = TRACKS.get(mob);
		return t != null && t.searching && player.getUUID().equals(t.who);
	}

	// ---- the tick: movement, the gauge ---------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		boolean gauge = server.getTickCount() % GAUGE_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Vec3 now = player.position();
			Vec3 was = LAST_POS.put(player.getUUID(), now);
			double dx = was == null ? 0.0 : now.x - was.x, dz = was == null ? 0.0 : now.z - was.z;
			MOVING.put(player.getUUID(), dx * dx + dz * dz > MOVING_SQR);
			if (gauge) {
				sync(player);
			}
		}
	}

	/** Works out what {@code player}'s gauge shows, and sends it if it changed. */
	private static void sync(ServerPlayer player) {
		int state = IDLE, fill = 0;
		if (!touched(player) && player.isAlive() && player.level() instanceof ServerLevel level) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(GAUGE_RANGE), m -> m.isAlive() && minded(m))) {
				if (mob.getTarget() == player) {
					state = SPOTTED;
					break;
				}
				if (searchingFor(mob, player)) {
					state = Math.max(state, SEARCHING);
				} else if (state <= NOTICING && mob instanceof Enemy) {
					int f = noticing(level, mob, player);
					if (f > 0) {
						state = NOTICING;
						fill = Math.max(fill, f);
					}
				}
			}
		}
		if (state != NOTICING) {
			fill = 0;
		}
		int packed = state << 4 | fill;
		Integer last = SENT.put(player.getUUID(), packed);
		if ((last == null ? 0 : last) != packed) {
			ServerPlayNetworking.send(player, new Gauge(state, fill));
		}
	}

	/** 0 if {@code mob} takes no notice of {@code player}; else 1 to {@link #FILL_STEPS}, as they come from where it could just see them to where it would spot them. */
	private static int noticing(ServerLevel level, Mob mob, ServerPlayer player) {
		double follow = mob.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE) ? mob.getAttributeValue(Attributes.FOLLOW_RANGE) : 16.0;
		if (mob.getTarget() != null || mob.distanceTo(player) >= follow || !mob.hasLineOfSight(player)) {
			return 0;
		}
		double spot = Math.max(2.0, follow * player.getVisibilityPercent(level, mob));
		double dist = mob.distanceTo(player);
		if (dist >= follow || spot >= follow) {
			return dist < spot ? FILL_STEPS : 0;
		}
		double share = dist <= spot ? 1.0 : (follow - dist) / (follow - spot);
		return Math.max(1, (int) Math.round(share * FILL_STEPS));
	}

	// ---- unaware blows -------------------------------------------------------------------------------------------------

	/** {@code target} is a hostile or neutral mob that isn't after {@code player} and can't see them: they're behind it, or it is searching. */
	static boolean unaware(LivingEntity target, ServerPlayer player) {
		if (!(target instanceof Mob mob) || !minded(mob) || mob instanceof TamableAnimal tame && tame.isTame() || !mob.isAlive()
			|| mob.getTarget() == player) {
			return false;
		}
		return searchingFor(mob, player) || !inCone(mob, player);
	}

	/** {@code target} is still in its hurt cooldown and would turn this blow away (vanilla's {@code hurtServer}): no bonus, no feedback. */
	static boolean cooling(LivingEntity target, DamageSource source) {
		return target.getInvulnerableTime() > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN);
	}

	/** Whether {@code player}'s hidden blade can assassinate {@code target} (Assassinate, and it is unaware of them). */
	static boolean assassinable(LivingEntity target, ServerPlayer player) {
		return Skills.rank(player, Skills.Perk.ASSASSINATE) > 0 && unaware(target, player);
	}

	/**
	 * What a blow deals, from {@code amount}: a player's melee blow on a monster that is unaware of them is a sneak attack
	 * ({@link #SNEAK_ATTACK_MULT}, or Sneak Attack's). Never on a player, or one of the Combat Arts' own blows.
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (CombatArts.striking() || target instanceof Player || !(source.getEntity() instanceof ServerPlayer sp)
			|| source.getDirectEntity() != sp || !source.is(DamageTypes.PLAYER_ATTACK) || sp.isSpectator() || target == sp
			|| HiddenBlade.is(sp.getMainHandItem()) && !HiddenBlade.skilled(sp) || !unaware(target, sp)) {
			return amount;
		}
		if (cooling(target, source)) {
			return amount;
		}
		long now = sp.level().getGameTime();
		long[] last = STRUCK.get(sp.getUUID());
		if (last != null && last[0] == target.getId() && last[1] == now) {
			return amount;
		}
		STRUCK.put(sp.getUUID(), new long[] {target.getId(), now});
		int rank = Skills.rank(sp, Skills.Perk.SNEAK_ATTACK);
		float mult = rank <= 0 ? SNEAK_ATTACK_MULT : SNEAK_ATTACK_RANK_MULT[Math.min(rank, SNEAK_ATTACK_RANK_MULT.length) - 1];
		if (sp.level() instanceof ServerLevel level) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 0.8F);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.25, 0.3, 0.25, 0.2);
		}
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.senses.sneak_attack").withStyle(ChatFormatting.GRAY));
		Skills.sneakAttack(sp, now);
		return amount * mult;
	}
}
