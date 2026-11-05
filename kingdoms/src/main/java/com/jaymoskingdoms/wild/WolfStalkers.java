package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.ScentTrails;
import com.jaymoskingdoms.Vampires;
import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.595) Wild wolves hunt players the way a wolf would: by nose first, then from cover. Andy: "wolves should stalk
 * you, letting you think you're safe and attacking from cover and concealment, similar to creepers but more of a
 * hunter. they can smell you out."
 * <ul>
 * <li><b>Scent</b> ({@link #scentRange}): a wild wolf smells a player within {@link #SCENT_RANGE} blocks, less for a
 * sneaking player ({@link #SNEAK_SCENT}) or in the rain ({@link #RAIN_SCENT}), more for a wounded one
 * ({@link #WOUNDED_SCENT}). Out in the open the wind ({@link #wind}, which slowly turns through the day) carries it:
 * a wolf downwind smells you from {@link #DOWNWIND} times as far, one upwind only {@link #UPWIND}. Farther than
 * that, a fresh footstep of yours near the wolf ({@link ScentTrails}) is enough to pick up the trail.</li>
 * <li><b>Shadowing</b> ({@link StalkGoal}): a stalking wolf goes quiet and keeps to a ring round you, from
 * {@link #RING_FAR} blocks in to {@link #RING_NEAR}, choosing spots you can't see (behind blocks and trees, in tall
 * grass and bushes, behind your back, downwind). Each hop it isn't seen it creeps {@link #CLOSE_PER_HOP} closer. A
 * pack spreads round you, each wolf on its own side.</li>
 * <li><b>Hanging back</b>: look straight at a stalking wolf and it freezes, then slinks back out of sight and starts
 * again from farther off. Spot one closer than {@link #SPOTTED_RUSH} blocks and it commits.</li>
 * <li><b>The rush</b>: within {@link #RUSH_RANGE} blocks and not looked at, with your back turned, your hands busy
 * (eating, drawing a bow, holding up a shield) or it hidden from you, it breaks cover with one low growl (the tell, like a creeper's hiss) and the
 * whole pack comes at once, quick for {@link #RUSH_TICKS}. Lose them after and they fall back to stalking.</li>
 * </ul>
 * Who stalks: our feral wolves always; the game's wild wolves at night, or by day when you're hurt, and never while
 * you hold a bone (so taming still works). Tamed wolves, pups and leashed wolves never stalk, and nobody stalks on
 * Peaceful or a creative or spectator player. State is kept per wolf in memory, not saved: a reload forgets the hunt.
 */
public final class WolfStalkers {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** Blocks a wolf smells a walking player from in still air, and the shares for sneaking, rain and being hurt. */
	static final double SCENT_RANGE = 36.0, SNEAK_SCENT = 0.6, RAIN_SCENT = 0.5, WOUNDED_SCENT = 1.5;
	/** Below this share of their health a player is wounded (smells stronger; the game's wolves stalk them by day). */
	static final float WOUNDED = 0.5F;
	/** Wind: how much farther a wolf straight downwind smells, how much less one straight upwind. */
	static final double DOWNWIND = 1.6, UPWIND = 0.4;
	/** A footstep this fresh (ticks), this near the wolf (blocks, times the scent shares), puts it on the trail. */
	static final int TRAIL_FRESH = 1200;
	static final double TRAIL_NEAR = 12.0;
	/** How often (ticks) an idle wolf sniffs, and how long (ticks) it keeps on with no scent at all before giving up. */
	static final int SNIFF_EVERY = 20, LOST_TICKS = 400;
	/** After giving up (ticks) before it will stalk again. */
	static final int REST_TICKS = 1200;
	/** The ring it keeps round its quarry: starts here, creeps in this much each unseen hop, no closer than RING_NEAR. */
	static final double RING_FAR = 18.0, RING_NEAR = 7.0, CLOSE_PER_HOP = 2.5, BACK_OFF = 6.0;
	/** Ticks between choosing new cover, and how many spots it weighs each time. */
	static final int HOP_EVERY = 50, SPOTS = 14;
	/** Farther than this beyond the ring, it just follows its nose toward the quarry. */
	static final double CLOSING_SLACK = 8.0;
	/** Speeds (a multiple of its own): slinking, closing a long way, the rush. */
	static final double SLINK_SPEED = 0.75, TRAIL_SPEED = 1.0, RUSH_SPEED = 1.3;
	/** A player is looking at it within this cosine of their look (about 25 degrees), up to WATCH_RANGE blocks. */
	static final double WATCH_COS = 0.9, WATCH_RANGE = 40.0;
	/** In undergrowth or the dark (light at or below DARK), it's only seen this close. */
	static final double HIDDEN_SEEN = 6.0;
	static final int DARK = 4;
	/** Frozen this long when watched before slinking off. */
	static final int FREEZE_TICKS = 30;
	/** It rushes from this close; seen this close, it commits anyway. */
	static final double RUSH_RANGE = 10.0, SPOTTED_RUSH = 6.0;
	/** Stalked this long (ticks), it takes the next unseen chance in reach even if you're facing its way. */
	static final int PATIENCE_TICKS = 2400;
	/** Wolves this near (blocks) stalking the same player are one pack: they join in and rush together. */
	static final double PACK_RANGE = 32.0, JOIN_RANGE = 16.0;
	/** The rush's speed lasts this long (ticks). */
	static final int RUSH_TICKS = 80;
	/** Within this many blocks of an unstalked feral wolf you've stumbled on it: it goes for you (feral ones only). */
	public static final double STUMBLE = 4.0;
	/** Tag that marks a wolf we've hushed for the stalk (so a reload can give its voice back). */
	static final String HUSHED = "jaymoskingdoms.stalk_hushed";

	// ---- state ---------------------------------------------------------------------------------------------------------

	/** One wolf's hunt. */
	static final class Stalk {
		final Mob wolf;
		final UUID quarry;
		long since;
		double ring = RING_FAR;
		/** Game time it last had the scent, may hop again, is frozen until, last seen by its quarry. */
		long scentAt, hopAt, frozenUntil, seenAt = Long.MIN_VALUE;
		/** Its place in the pack (0, 1, 2...): which side of the quarry it keeps to. */
		final int slot;
		boolean rushed;

		Stalk(Mob wolf, UUID quarry, long now, int slot) {
			this.wolf = wolf;
			this.quarry = quarry;
			this.since = now;
			this.scentAt = now;
			this.slot = slot;
		}
	}

	private static final Map<Mob, Stalk> STALKS = new WeakHashMap<>();
	/** Game time each wolf may stalk again after giving up. */
	private static final Map<Mob, Long> RESTING = new WeakHashMap<>();
	private static final Set<Mob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private WolfStalkers() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && kind(mob)) {
				if (mob.entityTags().contains(HUSHED)) {
					// hushed when it was saved mid-stalk: give its voice back
					mob.removeTag(HUSHED);
					mob.setSilent(false);
				}
				if (GIVEN.add(mob)) {
					// no flags on the nose; the stalk wants the legs and the eyes. Before the game's stroll (wolf 8, feral 6).
					((MobAccessor) mob).jaymoskingdoms$goals().addGoal(0, new NoseGoal(mob));
					((MobAccessor) mob).jaymoskingdoms$goals().addGoal(4, new StalkGoal(mob));
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && STALKS.containsKey(mob)) {
				end(mob);
			}
		});
	}

	/** A wolf: the game's or our feral one. */
	static boolean kind(Mob mob) {
		return mob instanceof Wolf || mob instanceof FeralWolf;
	}

	/** A wolf that may stalk at all: wild, grown, free, not on Peaceful. */
	static boolean able(Mob wolf) {
		return wolf.isAlive() && !wolf.isBaby() && !(wolf instanceof TamableAnimal tame && tame.isTame()) && !wolf.isLeashed()
			&& !wolf.isPassenger() && !wolf.isVehicle() && wolf.level().getDifficulty() != Difficulty.PEACEFUL;
	}

	/** Whether {@code wolf} would stalk {@code player} at all (who and when, not whether it can smell them). */
	static boolean quarry(Mob wolf, Player player) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
			return false;
		}
		if (wolf instanceof FeralWolf) {
			return true;
		}
		// the game's wolves: never while you hold out a bone; at night, or when you're hurt
		if (player.getMainHandItem().is(Items.BONE) || player.getOffhandItem().is(Items.BONE)) {
			return false;
		}
		return night((ServerLevel) wolf.level()) || wounded(player);
	}

	static boolean night(ServerLevel level) {
		return Vampires.night(level.getServer().overworld().getOverworldClockTime());
	}

	static boolean wounded(Player player) {
		return player.getHealth() < player.getMaxHealth() * WOUNDED;
	}

	/** Whether {@code wolf} is stalking (any player, not yet rushed). */
	public static boolean stalking(Mob wolf) {
		Stalk s = STALKS.get(wolf);
		return s != null && !s.rushed;
	}

	/** Whether {@code wolf} is stalking {@code player} (aware of them, not yet rushed). */
	public static boolean stalking(Mob wolf, Player player) {
		Stalk s = STALKS.get(wolf);
		return s != null && !s.rushed && s.quarry.equals(player.getUUID());
	}

	/**
	 * For the feral wolf's on-sight targeting: whether it goes for {@code player} now. Only once it has rushed them, or
	 * when they've walked right up on it without it stalking them.
	 */
	public static boolean strikes(Mob wolf, Player player) {
		Stalk s = STALKS.get(wolf);
		if (s != null) {
			return s.rushed && s.quarry.equals(player.getUUID());
		}
		return wolf.distanceTo(player) <= STUMBLE;
	}

	/**
	 * For the encounters' wolves set on a player: takes up the stalk instead of charging in. True if {@code wolf} is now
	 * stalking (or rushing) {@code player}; false if it isn't a wolf that stalks them, and should just be set on them.
	 */
	public static boolean hunt(Mob wolf, Player player) {
		if (!kind(wolf) || !able(wolf) || !quarry(wolf, player)) {
			return false;
		}
		Stalk s = STALKS.get(wolf);
		if (s != null && s.quarry.equals(player.getUUID())) {
			return true;
		}
		if (s != null) {
			end(wolf);
		}
		begin(wolf, player, wolf.level().getGameTime());
		return true;
	}

	// ---- the nose ------------------------------------------------------------------------------------------------------

	/** The way the wind blows (a unit vector, the way the air moves), turning slowly through the day. */
	static Vec3 wind(ServerLevel level) {
		double t = level.getGameTime();
		double angle = t / 24000.0 * Math.PI * 0.7 + Math.sin(t / 5300.0) * 1.1 + level.getSeed() % 7;
		return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
	}

	/** The wind's share for a wolf at {@code wolf} smelling a player at {@code player}: more downwind, less upwind, none under cover. */
	static double windShare(ServerLevel level, Vec3 player, Vec3 wolf) {
		BlockPos at = BlockPos.containing(player);
		if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling() || !level.canSeeSky(at.above())) {
			return 1.0;
		}
		Vec3 off = wolf.subtract(player).multiply(1.0, 0.0, 1.0);
		if (off.lengthSqr() < 1.0E-4) {
			return 1.0;
		}
		double along = off.normalize().dot(wind(level));
		return along >= 0.0 ? 1.0 + (DOWNWIND - 1.0) * along : 1.0 + (1.0 - UPWIND) * along;
	}

	/** The shares of scent {@code player} gives off, before the wind. */
	static double shares(ServerLevel level, Player player) {
		double share = 1.0;
		if (player.isShiftKeyDown()) {
			share *= SNEAK_SCENT;
		}
		if (level.isRainingAt(player.blockPosition().above())) {
			share *= RAIN_SCENT;
		}
		if (wounded(player)) {
			share *= WOUNDED_SCENT;
		}
		return share;
	}

	/** How far (blocks) {@code wolf} smells {@code player} from, as things are. */
	static double scentRange(Mob wolf, Player player) {
		ServerLevel level = (ServerLevel) wolf.level();
		return SCENT_RANGE * shares(level, player) * windShare(level, player.position(), wolf.position());
	}

	/** Whether {@code wolf} has {@code player}'s scent: in the air, or a fresh footstep near it. */
	static boolean smells(Mob wolf, Player player) {
		if (player.level() != wolf.level()) {
			return false;
		}
		double range = scentRange(wolf, player);
		if (wolf.distanceToSqr(player) <= range * range) {
			return true;
		}
		double near = TRAIL_NEAR * shares((ServerLevel) wolf.level(), player);
		return ScentTrails.freshest(player.getUUID(), wolf.level(), wolf.position(), near, TRAIL_FRESH) != null;
	}

	/** Every {@link #SNIFF_EVERY} ticks: an idle wolf takes up a scent; a stalking one keeps or loses its own. */
	static final class NoseGoal extends Goal {
		final Mob wolf;

		NoseGoal(Mob wolf) {
			this.wolf = wolf;
		}

		@Override
		public boolean canUse() {
			return (this.wolf.tickCount + this.wolf.getId()) % SNIFF_EVERY == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			sniff(this.wolf);
		}
	}

	static void sniff(Mob wolf) {
		if (!(wolf.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Stalk s = STALKS.get(wolf);
		if (s != null) {
			Player quarry = level.getPlayerByUUID(s.quarry);
			if (!able(wolf) || quarry == null || !quarry(wolf, quarry) && !s.rushed) {
				end(wolf);
				return;
			}
			if (s.rushed) {
				// the rush is over once it no longer has them: back to stalking
				if (wolf.getTarget() == null) {
					s.rushed = false;
					s.ring = RING_FAR;
					s.since = now;
					hush(wolf);
				}
				return;
			}
			if (wolf.getTarget() == quarry) {
				// struck first (or the game set it on them): the pack comes in now
				rush(level, s, quarry);
				return;
			}
			if (smells(wolf, quarry)) {
				s.scentAt = now;
			} else if (now - s.scentAt > LOST_TICKS) {
				end(wolf);
				RESTING.put(wolf, now + REST_TICKS);
			}
			return;
		}
		if (wolf.getTarget() != null || !able(wolf) || RESTING.getOrDefault(wolf, Long.MIN_VALUE) > now) {
			return;
		}
		Player best = null;
		double bestD = Double.MAX_VALUE;
		for (Player player : level.players()) {
			double d = wolf.distanceToSqr(player);
			if (d < bestD && d < 128.0 * 128.0 && quarry(wolf, player) && smells(wolf, player)) {
				best = player;
				bestD = d;
			}
		}
		if (best != null) {
			begin(wolf, best, now);
			// the pack takes it up too
			for (Mob other : level.getEntitiesOfClass(Mob.class, wolf.getBoundingBox().inflate(JOIN_RANGE),
				o -> o != wolf && o.getType() == wolf.getType() && !STALKS.containsKey(o) && o.getTarget() == null && able(o))) {
				if (quarry(other, best)) {
					begin(other, best, now);
				}
			}
		}
	}

	static void begin(Mob wolf, Player player, long now) {
		int slot = 0;
		for (Stalk o : STALKS.values()) {
			if (o.quarry.equals(player.getUUID()) && o.wolf.distanceToSqr(wolf) < PACK_RANGE * PACK_RANGE) {
				slot++;
			}
		}
		STALKS.put(wolf, new Stalk(wolf, player.getUUID(), now, slot));
		hush(wolf);
	}

	/** Ends {@code wolf}'s stalk and gives it back its voice. */
	static void end(Mob wolf) {
		STALKS.remove(wolf);
		unhush(wolf);
	}

	/** Quiet while it stalks (no panting or whining to give it away), unless it was silenced already. */
	static void hush(Mob wolf) {
		if (!wolf.isSilent()) {
			wolf.setSilent(true);
			wolf.addTag(HUSHED);
		}
	}

	static void unhush(Mob wolf) {
		if (wolf.entityTags().contains(HUSHED)) {
			wolf.removeTag(HUSHED);
			wolf.setSilent(false);
		}
	}

	// ---- being seen ----------------------------------------------------------------------------------------------------

	/** Undergrowth to hide in: grass, ferns, bushes, flowers, vines, or leaves. */
	static boolean undergrowth(ServerLevel level, BlockPos feet) {
		BlockState low = level.getBlockState(feet), high = level.getBlockState(feet.above());
		return low.is(BlockTags.REPLACEABLE_BY_TREES) || high.is(BlockTags.REPLACEABLE_BY_TREES) || high.is(BlockTags.LEAVES);
	}

	/** Whether {@code player} can see {@code wolf} at all (line of sight, not hidden in undergrowth or the dark). */
	static boolean visible(Player player, Mob wolf) {
		double d = player.distanceTo(wolf);
		if (d > WATCH_RANGE || !player.hasLineOfSight(wolf)) {
			return false;
		}
		ServerLevel level = (ServerLevel) wolf.level();
		if (d > HIDDEN_SEEN && (undergrowth(level, wolf.blockPosition()) || level.getMaxLocalRawBrightness(wolf.blockPosition().above()) <= DARK)) {
			return false;
		}
		return true;
	}

	/** Whether {@code player} is looking right at {@code wolf} and can see it. */
	static boolean watched(Player player, Mob wolf) {
		Vec3 to = wolf.getEyePosition().subtract(player.getEyePosition());
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		return to.normalize().dot(player.getViewVector(1.0F)) >= WATCH_COS && visible(player, wolf);
	}

	/** Whether {@code player} has their back to {@code wolf} (it is behind them, sideways). */
	static boolean turnedAway(Player player, Mob wolf) {
		Vec3 to = wolf.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		Vec3 look = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 1.0E-4 || look.lengthSqr() < 1.0E-4) {
			return false;
		}
		return to.normalize().dot(look.normalize()) < 0.0;
	}

	/** Hands busy: eating, drinking, drawing, guarding. */
	static boolean busy(Player player) {
		return player.isUsingItem();
	}

	// ---- the stalk -----------------------------------------------------------------------------------------------------

	/** Shadows the quarry from cover, then rushes. Holds the legs and eyes while it stalks. */
	static final class StalkGoal extends Goal {
		final Mob wolf;

		StalkGoal(Mob wolf) {
			this.wolf = wolf;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Nullable Stalk stalk() {
			Stalk s = STALKS.get(this.wolf);
			return s != null && !s.rushed && this.wolf.getTarget() == null ? s : null;
		}

		@Override
		public boolean canUse() {
			return stalk() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return stalk() != null;
		}

		@Override
		public void start() {
			Stalk s = stalk();
			if (s != null) {
				s.hopAt = 0L;
			}
		}

		@Override
		public void stop() {
			this.wolf.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Stalk s = stalk();
			if (s == null || !(this.wolf.level() instanceof ServerLevel level)) {
				return;
			}
			Player quarry = level.getPlayerByUUID(s.quarry);
			if (quarry == null) {
				return;
			}
			long now = level.getGameTime();
			double d = this.wolf.distanceTo(quarry);
			if (now < s.frozenUntil) {
				// frozen: stock still, eyes on them
				this.wolf.getNavigation().stop();
				this.wolf.getLookControl().setLookAt(quarry, 10.0F, 10.0F);
				if (now + 1 == s.frozenUntil) {
					// then slinks off to start again from farther out
					s.ring = Math.min(RING_FAR + BACK_OFF, Math.max(d, s.ring) + BACK_OFF);
					s.hopAt = 0L;
				}
				return;
			}
			if ((now + this.wolf.getId()) % 5 != 0) {
				return;
			}
			boolean watched = watched(quarry, this.wolf);
			if (watched) {
				s.seenAt = now;
				if (d <= SPOTTED_RUSH) {
					rush(level, s, quarry);
				} else {
					s.frozenUntil = now + FREEZE_TICKS;
				}
				return;
			}
			if (d <= RUSH_RANGE
				&& (turnedAway(quarry, this.wolf) || busy(quarry) || !visible(quarry, this.wolf) || now - s.since > PATIENCE_TICKS)) {
				rush(level, s, quarry);
				return;
			}
			if (d > s.ring + CLOSING_SLACK) {
				// far off: follow the nose (the trail if there is one, else straight for them)
				if (now >= s.hopAt || this.wolf.getNavigation().isDone()) {
					Vec3 to = ScentTrails.freshest(quarry.getUUID(), level, quarry.position(), RING_FAR, TRAIL_FRESH);
					to = to != null ? to : quarry.position();
					this.wolf.getNavigation().moveTo(to.x, to.y, to.z, TRAIL_SPEED);
					s.hopAt = now + HOP_EVERY / 2;
				}
				return;
			}
			if (now >= s.hopAt || this.wolf.getNavigation().isDone() && now >= s.hopAt - HOP_EVERY / 2) {
				if (now - s.seenAt > HOP_EVERY) {
					s.ring = Math.max(RING_NEAR, s.ring - CLOSE_PER_HOP);
				}
				Vec3 spot = cover(level, s, quarry);
				if (spot != null) {
					this.wolf.getNavigation().moveTo(spot.x, spot.y, spot.z, SLINK_SPEED);
				}
				s.hopAt = now + HOP_EVERY;
			}
			if (this.wolf.getNavigation().isDone()) {
				this.wolf.getLookControl().setLookAt(quarry, 10.0F, 10.0F);
			}
		}
	}

	/**
	 * The best spot on {@code s}'s ring round {@code quarry}: one they can't see (behind blocks, in undergrowth), on the
	 * wolf's own side of the pack's spread round their back, downwind, and not far for the wolf to go.
	 */
	static @Nullable Vec3 cover(ServerLevel level, Stalk s, Player quarry) {
		Vec3 eye = quarry.getEyePosition();
		Vec3 look = quarry.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		double back = look.lengthSqr() < 1.0E-4 ? 0.0 : Math.atan2(-look.z, -look.x);
		// the pack fans out: 0 behind, then +-50 degrees, +-100...
		int side = (s.slot + 1) / 2 * (s.slot % 2 == 0 ? 1 : -1);
		double home = back + side * Math.toRadians(50.0);
		Vec3 windTo = wind(level);
		Vec3 best = null;
		double bestScore = -Double.MAX_VALUE;
		for (int i = 0; i < SPOTS; i++) {
			double angle = home + (level.getRandom().nextDouble() - 0.5) * Math.toRadians(160.0);
			double r = s.ring + (level.getRandom().nextDouble() - 0.5) * 4.0;
			double x = quarry.getX() + Math.cos(angle) * r, z = quarry.getZ() + Math.sin(angle) * r;
			BlockPos feet = ground(level, BlockPos.containing(x, quarry.getY(), z));
			if (feet == null) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(feet);
			Vec3 head = at.add(0.0, 0.7, 0.0);
			BlockHitResult hit = level.clip(new ClipContext(eye, head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, quarry));
			boolean hidden = hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(head) > 0.5;
			double score = (hidden ? 4.0 : 0.0) + (undergrowth(level, feet) ? 2.5 : 0.0);
			score += Math.cos(angle - home) * 1.5;
			Vec3 off = at.subtract(quarry.position()).multiply(1.0, 0.0, 1.0);
			if (off.lengthSqr() > 1.0E-4) {
				score += off.normalize().dot(windTo); // downwind of them, so they don't carry its scent... and it keeps theirs
			}
			score -= s.wolf.position().distanceTo(at) / 12.0;
			if (score > bestScore) {
				bestScore = score;
				best = at;
			}
		}
		return best;
	}

	/** Somewhere near {@code from} (a few blocks up or down) a wolf can stand: solid below, room above, no water. */
	static @Nullable BlockPos ground(ServerLevel level, BlockPos from) {
		for (int dy = 4; dy >= -6; dy--) {
			BlockPos feet = from.offset(0, dy, 0);
			BlockPos below = feet.below();
			BlockState floor = level.getBlockState(below);
			if (!floor.isFaceSturdy(level, below, net.minecraft.core.Direction.UP) || !floor.getFluidState().isEmpty()) {
				continue;
			}
			BlockState low = level.getBlockState(feet), high = level.getBlockState(feet.above());
			if (low.getCollisionShape(level, feet).isEmpty() && high.getCollisionShape(level, feet.above()).isEmpty()
				&& low.getFluidState().isEmpty()) {
				return feet;
			}
		}
		return null;
	}

	/** {@code s}'s wolf breaks cover, and every wolf of its pack stalking the same player with it. */
	static void rush(ServerLevel level, Stalk s, Player quarry) {
		List<Stalk> pack = new ArrayList<>();
		for (Stalk o : STALKS.values()) {
			if (!o.rushed && o.quarry.equals(s.quarry) && o.wolf.isAlive() && o.wolf.distanceToSqr(s.wolf) < PACK_RANGE * PACK_RANGE) {
				pack.add(o);
			}
		}
		boolean growled = false;
		for (Stalk o : pack) {
			o.rushed = true;
			unhush(o.wolf);
			o.wolf.setTarget(quarry);
			o.wolf.addEffect(new MobEffectInstance(MobEffects.SPEED, RUSH_TICKS, 1, false, false));
			o.wolf.getNavigation().moveTo(quarry, RUSH_SPEED);
			if (!growled || o.wolf.distanceToSqr(quarry) < 12.0 * 12.0) {
				// one low growl as it breaks cover: the tell
				float pitch = (o.wolf instanceof FeralWolf ? 0.75F : 0.9F) + level.getRandom().nextFloat() * 0.1F;
				level.playSound(null, o.wolf.blockPosition(),
					SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.ANGRY).adultSounds().growlSound().value(),
					SoundSource.HOSTILE, growled ? 0.6F : 1.0F, pitch);
				growled = true;
			}
		}
	}
}
