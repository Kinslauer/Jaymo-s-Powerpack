package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Crushing;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.8) The symbiote's own executions. Andy: "the suits should have their own executions. 18 total, 6 for thin, 6 for
 * living armor, and 6 split between the two when using tendrils." With the suit showing in any form and nothing in your
 * main hand, (0.17.7) attack held on someone downed or crippled for {@link Finishers#HOLD_TICKS} (not the bare hands'
 * neck snap: the suit is the weapon, so it needs no Executioner perk) plays one of this form's moves
 * ({@link Move}), picked at random and never the same one twice running per player ({@link #LAST}):
 * <ul>
 * <li>the thin suit ({@link Pool#THIN}: the suit on, no armour): acrobatic and quick: a knee drop and neck twist, a heel
 * stomp on the head, a lift and slam, a choke-out, a mounted pummel, a throat punch;</li>
 * <li>Living Armor or the big suit ({@link Pool#HEAVY}): heavy and brutal: torn in half (Kitchen's gibs), a bite to the
 * neck (teeth bared, and it feeds you), a ground pound on the chest, a lift overhead and throw, the skull crushed between
 * the palms, a stomp that cracks the ground;</li>
 * <li>with the tendrils out ({@link Pool#TENDRIL_THIN} over the thin suit: a skewer through the chest, a lift and snap, a
 * drag in to the knee; {@link Pool#TENDRIL_HEAVY} over the armour: quartered by four, impaled and slammed, wrapped and
 * crushed): the pool is the form's six plus its three.</li>
 * </ul>
 * Each move has its own length, the beats its blows land on (the last is the kill: {@link Downed#execute}, credited to
 * you, as every finisher's), how they land ({@link Strike}: sound, blood, a jolt through the body), and a line the
 * symbiote growls over it ({@link WebSymbiote#narrate}: {@code say.execute_thin}, {@code say.execute_heavy},
 * {@code say.execute_tendril}, each in every strain's voice). Some hoist the body ({@link #lift}): a mob is held up
 * where the hands (or tendrils) have it, weightless, and set down (or thrown) where the move ends it; a player's motion
 * is their own client's, so they're only rooted. Everything else is as a weapon's finisher: you stand rooted facing
 * them, they lie still for it ({@link Finishers#targeted}), it stops, sparing them, if they get up, are carried off or
 * end up out of reach, and everyone sees it ({@link Execution}, synced; WebExecutionPose draws it, watched from behind).
 */
public final class WebExecutions {
	/** How far ahead of you a hoisted body is held (blocks, in your size), and how far ahead a throw lands it. */
	static final double HOLD_AHEAD = 1.1, THROW_AHEAD = 2.6;
	/** How far they may be from you for it to go on (blocks). */
	static final double MAX_DISTANCE = 4.5;
	/** Screen shake on a blow for you and the one being finished; the kill's for both and those around. */
	static final float BEAT_SHAKE = 0.6F, KILL_SHAKE = 1.5F, AROUND_SHAKE = 0.9F, HEAVY_AROUND_SHAKE = 1.3F;
	static final double AROUND_RANGE = 10.0, HEAVY_AROUND_RANGE = 14.0;
	/** The bite feeds you this much health (on a kill only: it never feeds on the living). */
	static final float BITE_HEAL = 4.0F;
	/** The ground-cracking stomp: cracks to this stage ({@code destroyBlockProgress}) on the block under them and this many round it, fading after this long; this much dust. */
	static final int QUAKE_CRACK_STAGE = 6, QUAKE_RING = 4, QUAKE_CRACK_TICKS = 24, QUAKE_DUST = 36;
	static final double QUAKE_RING_RADIUS = 1.1;
	/** Block dust kicked up where a body is slammed into the ground. */
	static final int SLAM_DUST = 24;
	/** The drag: the body hauled in to this far from you, from this beat to this one. */
	static final double DRAG_TO = 1.2;
	static final int DRAG_FROM_TICK = 10, DRAG_TO_TICK = 24;

	/** Which form a move belongs to. */
	public enum Pool { THIN, HEAVY, TENDRIL_THIN, TENDRIL_HEAVY }

	/** How a blow lands: what it sounds and looks like. */
	public enum Strike { SNAP, PUNCH, CRUSH, SLAM, TEAR, BITE, PIERCE, QUAKE, THROW }

	/**
	 * One move: its pool, how long it plays (ticks), the beats its blows land on (the last is the kill), how each lands,
	 * and the hoist ({@code liftFrom}, {@code liftTo}: the ticks the body is raised over; {@code liftHeight}: how high its
	 * middle goes, blocks in your size; {@code liftHold}: the tick it's let down from, 0 for none). The poses are the
	 * client's (WebExecutionPose), keyed by name.
	 */
	public enum Move {
		// ---- the thin suit --------------------------------------------------------------------------------------------
		/** Knee Drop: a leap onto the chest, both knees, then the head taken in both hands and twisted round. */
		KNEE_TWIST(Pool.THIN, 44, new int[] {16, 36}, Strike.SNAP),
		/** Heel Stomp: a foot raised high and the heel brought down on the head, twice. */
		HEEL_STOMP(Pool.THIN, 30, new int[] {12, 22}, Strike.CRUSH),
		/** Lift and Slam: hauled up by the collar in one hand, held, and slammed into the ground. */
		LIFT_SLAM(Pool.THIN, 40, new int[] {30}, Strike.SLAM, 6, 18, 1.5, 26),
		/** Choke-out: kneeling behind them, an arm locked round the neck, tightened twice, until it goes. */
		CHOKE_OUT(Pool.THIN, 70, new int[] {22, 42, 60}, Strike.SNAP),
		/** Mounted Pummel: astride them, five blows into the face, left and right, the last with everything. */
		PUMMEL(Pool.THIN, 46, new int[] {12, 17, 22, 27, 38}, Strike.PUNCH),
		/** Throat Punch: down on a knee, the fist drawn back, one straight blow into the throat. */
		THROAT_PUNCH(Pool.THIN, 28, new int[] {16}, Strike.PUNCH),
		// ---- Living Armor and the big suit ----------------------------------------------------------------------------
		/** Torn in Half: hauled up in both hands, a grip taken high and low, and pulled apart (Kitchen's gibs). */
		RIP_HALF(Pool.HEAVY, 50, new int[] {16, 36}, Strike.TEAR, 4, 14, 1.4, 0),
		/** Neck Bite: hauled up to the maw, the teeth bared, and bitten through the neck; it feeds you. */
		NECK_BITE(Pool.HEAVY, 40, new int[] {12, 28}, Strike.BITE, 4, 12, 1.3, 34),
		/** Ground Pound: both fists hammered down on the chest, twice, the second with the whole body. */
		GROUND_POUND(Pool.HEAVY, 38, new int[] {14, 30}, Strike.CRUSH),
		/** Overhead Throw: hoisted overhead in both hands and hurled down into the ground ahead. */
		OVERHEAD_THROW(Pool.HEAVY, 46, new int[] {34}, Strike.THROW, 6, 22, 2.3, 0),
		/** Skull Crush: the head taken between the palms, squeezed, strained twice, and cracked. */
		SKULL_CRUSH(Pool.HEAVY, 54, new int[] {18, 34, 46}, Strike.CRUSH),
		/** Quake Stomp: a hop, and the foot brought down on them so hard the ground cracks around. */
		QUAKE_STOMP(Pool.HEAVY, 36, new int[] {22}, Strike.QUAKE),
		// ---- the tendrils, over the thin suit -------------------------------------------------------------------------
		/** Skewer: a shoulder's tendril spears down through the chest, and is twisted. */
		TENDRIL_SKEWER(Pool.TENDRIL_THIN, 34, new int[] {16, 26}, Strike.PIERCE),
		/** Tendril Snap: two tendrils wind round the neck, hoist them off the ground, and snap it. */
		TENDRIL_SNAP(Pool.TENDRIL_THIN, 44, new int[] {12, 34}, Strike.SNAP, 12, 28, 1.6, 38),
		/** Drag In: tendrils hook them and haul them in across the ground, and the knee comes up into the face. */
		DRAG_KNEE(Pool.TENDRIL_THIN, 40, new int[] {10, 28}, Strike.PUNCH),
		// ---- the tendrils, over the armour ----------------------------------------------------------------------------
		/** Quartered: four tendrils take the limbs, hoist them, and pull them apart (Kitchen's gibs). */
		QUARTER(Pool.TENDRIL_HEAVY, 56, new int[] {16, 44}, Strike.TEAR, 16, 32, 1.5, 0),
		/** Impale and Slam: a tendril through the chest, lifted on it, and slammed down into the ground. */
		IMPALE_SLAM(Pool.TENDRIL_HEAVY, 46, new int[] {14, 36}, Strike.SLAM, 14, 26, 1.8, 32),
		/** Wrap and Crush: tendrils wind round the whole body, squeeze twice, and crush it. */
		WRAP_CRUSH(Pool.TENDRIL_HEAVY, 60, new int[] {18, 36, 52}, Strike.CRUSH, 18, 26, 0.9, 56);

		public final Pool pool;
		public final int length;
		public final int[] beats;
		public final Strike strike;
		public final int liftFrom, liftTo, liftHold;
		public final double liftHeight;

		Move(Pool pool, int length, int[] beats, Strike strike) {
			this(pool, length, beats, strike, 0, 0, 0.0, 0);
		}

		Move(Pool pool, int length, int[] beats, Strike strike, int liftFrom, int liftTo, double liftHeight, int liftHold) {
			this.pool = pool;
			this.length = length;
			this.beats = beats;
			this.strike = strike;
			this.liftFrom = liftFrom;
			this.liftTo = liftTo;
			this.liftHeight = liftHeight;
			this.liftHold = liftHold;
		}

		/** The beat its kill lands on. */
		public int kill() {
			return this.beats[this.beats.length - 1];
		}

		/** Whether this move hoists the body off the ground. */
		public boolean lifts() {
			return this.liftHeight > 0.0;
		}

		/** Its name in keys ({@code message.jaymospowers.web_symbiote.execution.<name>}, the client's pose table). */
		public String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}

		public static Move of(int ordinal) {
			Move[] all = values();
			return all[Math.floorMod(ordinal, all.length)];
		}
	}

	/** The execution being played: which move, since when (game time), and on whom (entity id). Synced to everyone, not saved. */
	public record Execution(int move, long start, int target) {
		public static final StreamCodec<ByteBuf, Execution> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Execution::move,
			ByteBufCodecs.VAR_LONG, Execution::start, ByteBufCodecs.VAR_INT, Execution::target, Execution::new);

		public Move type() {
			return Move.of(this.move);
		}
	}

	public static final AttachmentType<Execution> EXECUTING = AttachmentRegistry.create(JaymosPowers.id("web_executing"),
		builder -> builder.syncWith(Execution.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Rooted while it plays (you, and a player or a crippled one being finished). */
	static final Identifier ROOT_ID = JaymosPowers.id("web_execution_root"), JUMP_ID = JaymosPowers.id("web_execution_jump");

	/** An execution playing: who, on whom, whether they were downed (a crippled one is held till it's over), and where the body lay. */
	private record Playing(ServerPlayer player, LivingEntity body, boolean downed, Vec3 from, double bodyYaw) {
	}

	/** A crack the stomp left in the ground, and when it fades. */
	private record Crack(ResourceKey<Level> dimension, BlockPos pos, int id, long until) {
	}

	private static final Map<UUID, Playing> PLAYING = new HashMap<>();
	/** The bodies being finished, by entity UUID. */
	private static final Map<UUID, UUID> TARGETED = new HashMap<>();
	/** The last move each player used, so the next is never the same one. */
	private static final Map<UUID, Integer> LAST = new HashMap<>();
	private static final List<Crack> CRACKS = new ArrayList<>();

	private WebExecutions() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			plays(server);
			fadeCracks(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PLAYING.clear();
			TARGETED.clear();
			LAST.clear();
			CRACKS.clear();
		});
	}

	// ---- queries (both sides: the attachment is synced) ------------------------------------------------------------------

	/** The execution {@code player} is playing, or null. */
	public static @Nullable Execution of(Entity player) {
		return player.getAttached(EXECUTING);
	}

	/** Whether {@code player} is in the middle of one (rooted, hands busy). */
	public static boolean busy(Entity player) {
		return player.hasAttached(EXECUTING);
	}

	/** Server side: whether someone is executing {@code body} just now (it lies still for it). */
	public static boolean targeted(Entity body) {
		return TARGETED.containsKey(body.getUUID());
	}

	/**
	 * Whether {@code player}'s empty hands are the symbiote's: the suit showing in any form (the plain suit, Living Armor
	 * or the big suit; Living Armor at a whole bond wears the big body without the suit flag), and the symbiote not at the
	 * wheel. Both sides.
	 */
	public static boolean applies(Player player) {
		if (!WebSymbiote.isBonded(player) || !(WebSymbiote.suitShown(player) || WebSymbiote.livingArmor(player))) {
			return false;
		}
		return !(player instanceof ServerPlayer sp) || !WebSymbiote.driving(sp);
	}

	/** The form {@code player} is in: heavy with the armour or the big suit, thin otherwise. */
	static boolean heavy(Player player) {
		return WebSymbiote.livingArmor(player);
	}

	/** The moves open to {@code player} right now: the form's six, and its three tendril moves with the tendrils out. */
	static List<Move> pool(Player player) {
		boolean heavy = heavy(player);
		boolean tendrils = WebSymbiote.armored(player);
		List<Move> out = new ArrayList<>();
		for (Move move : Move.values()) {
			boolean in = switch (move.pool) {
				case THIN -> !heavy;
				case HEAVY -> heavy;
				case TENDRIL_THIN -> tendrils && !heavy;
				case TENDRIL_HEAVY -> tendrils && heavy;
			};
			if (in) {
				out.add(move);
			}
		}
		return out;
	}

	/** One of the form's moves, never the one this player used last. */
	static Move pick(ServerPlayer player) {
		List<Move> pool = pool(player);
		int last = LAST.getOrDefault(player.getUUID(), -1);
		pool.removeIf(move -> move.ordinal() == last && pool.size() > 1);
		Move move = pool.get(player.getRandom().nextInt(pool.size()));
		LAST.put(player.getUUID(), move.ordinal());
		return move;
	}

	// ---- playing it out ---------------------------------------------------------------------------------------------------

	/** Use held long enough on {@code body} ({@link Finishers}): the move begins. */
	public static void begin(ServerLevel level, ServerPlayer player, LivingEntity body) {
		if (busy(player) || !applies(player)) {
			return;
		}
		Move move = pick(player);
		player.setAttached(EXECUTING, new Execution(move.ordinal(), level.getGameTime(), body.getId()));
		PLAYING.put(player.getUUID(), new Playing(player, body, Downed.isDowned(body), body.position(), body.yBodyRot));
		TARGETED.put(body.getUUID(), player.getUUID());
		player.stopUsingItem();
		player.setSprinting(false);
		root(player, true);
		if (body instanceof Player || !Downed.isDowned(body)) {
			// (a crippled one is held where it is too)
			root(body, true);
		}
		if (move.lifts() && !(body instanceof Player)) {
			WebTendrils.weightless(body, true);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_symbiote.execution." + move.key(), body.getDisplayName())
			.withStyle(ChatFormatting.DARK_RED));
		// what's heard as it begins: the suit's goo shifting, and the tendrils clicking out for theirs
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F,
			heavy(player) ? 0.45F : 0.7F);
		if (move.pool == Pool.TENDRIL_THIN || move.pool == Pool.TENDRIL_HEAVY) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.9F, 0.7F);
		}
		WebSymbiote.narrate(player, switch (move.pool) {
			case THIN -> "execute_thin";
			case HEAVY -> "execute_heavy";
			default -> "execute_tendril";
		});
	}

	private static void plays(MinecraftServer server) {
		if (PLAYING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Playing p = e.getValue();
			ServerPlayer player = p.player();
			LivingEntity body = p.body();
			Execution execution = of(player);
			boolean online = server.getPlayerList().getPlayer(e.getKey()) == player;
			if (execution == null || !online || !(player.level() instanceof ServerLevel level) || !player.isAlive() || Downed.isDowned(player)
				|| !applies(player)) {
				it.remove();
				end(player, body);
				continue;
			}
			Move move = execution.type();
			int t = (int) (level.getGameTime() - execution.start());
			boolean present = body.isAlive() && !body.isRemoved() && body.level() == level && (!p.downed() || Downed.isDowned(body))
				&& !Carry.isCarried(body) && player.distanceTo(body) <= MAX_DISTANCE;
			if (t < move.kill() && !present) {
				// (up, carried off or gone: spared)
				it.remove();
				end(player, body);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.stopped", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
				continue;
			}
			// rooted where you stand, facing the body
			player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
			if (present && !(body instanceof Player)) {
				hold(player, body, move, t, p);
			}
			if (!p.downed() && body instanceof Mob mob) {
				// (a crippled mob, held: no crawling off, no fighting back)
				mob.getNavigation().stop();
				mob.setTarget(null);
			}
			for (int beat : move.beats) {
				if (t == beat && present) {
					blow(level, player, body, move, beat == move.kill());
				}
			}
			if (t >= move.length) {
				it.remove();
				end(player, body);
			}
		}
	}

	/**
	 * How high the body's middle is held at tick {@code t} of {@code move} (blocks, before your size): nothing until the
	 * hoist begins, rising to {@link Move#liftHeight} by {@code liftTo}, held, and let down over the last ticks from
	 * {@code liftHold} (a move with none keeps it up until the kill, which puts it where it ends). Both sides.
	 */
	public static double lift(Move move, float t) {
		if (!move.lifts() || t < move.liftFrom) {
			return 0.0;
		}
		double up = Mth.clamp((t - move.liftFrom) / (double) Math.max(1, move.liftTo - move.liftFrom), 0.0, 1.0);
		up = up * up * (3.0 - 2.0 * up);
		if (move.liftHold > 0 && t > move.liftHold) {
			double down = Mth.clamp((t - move.liftHold) / (double) Math.max(1, move.length - move.liftHold), 0.0, 1.0);
			up *= 1.0 - down * down * (3.0 - 2.0 * down);
		}
		return move.liftHeight * up;
	}

	/** A mob under the move: hoisted where the hands have it, dragged in, or simply kept still where it lay. */
	private static void hold(ServerPlayer player, LivingEntity body, Move move, int t, Playing p) {
		ServerLevel level = player.level();
		double scale = player.getScale();
		Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
		if (move == Move.DRAG_KNEE && t >= DRAG_FROM_TICK && t <= DRAG_TO_TICK) {
			// hauled in across the ground to your feet
			double k = (t - DRAG_FROM_TICK) / (double) (DRAG_TO_TICK - DRAG_FROM_TICK);
			k = k * k * (3.0 - 2.0 * k);
			Vec3 to = player.position().add(forward.scale(DRAG_TO * scale + body.getBbWidth() * 0.5));
			Vec3 at = p.from().lerp(new Vec3(to.x, p.from().y, to.z), k);
			body.setPos(at.x, at.y, at.z);
			body.setDeltaMovement(Vec3.ZERO);
			body.needsSync = true;
			body.syncVelocity = true;
			if (t == DRAG_FROM_TICK + 2 || t == DRAG_TO_TICK - 6) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.8F, 0.6F);
			}
			return;
		}
		if (!move.lifts()) {
			body.setDeltaMovement(0.0, Math.min(0.0, body.getDeltaMovement().y), 0.0);
			return;
		}
		double up = lift(move, t);
		if (up <= 0.0 && t < move.liftFrom) {
			body.setDeltaMovement(0.0, Math.min(0.0, body.getDeltaMovement().y), 0.0);
			return;
		}
		// hoisted: its middle out ahead of your chest, this high, facing you
		Vec3 at = player.position().add(forward.scale(HOLD_AHEAD * scale + body.getBbWidth() * 0.5)).add(0.0, up * scale, 0.0);
		if (t == move.kill() && (move.strike == Strike.THROW || move.strike == Strike.SLAM)) {
			// slammed into the ground at your feet, or hurled down ahead
			double ahead = move.strike == Strike.THROW ? THROW_AHEAD : HOLD_AHEAD;
			at = player.position().add(forward.scale(ahead * scale + body.getBbWidth() * 0.5));
		}
		body.setPos(at.x, at.y, at.z);
		body.setDeltaMovement(Vec3.ZERO);
		body.needsSync = true;
		body.syncVelocity = true;
		body.resetFallDistance();
		float yaw = player.getYRot() + 180.0F;
		body.setYRot(yaw);
		body.setYBodyRot(yaw);
		body.setYHeadRot(yaw);
	}

	/** One of the move's blows: the sound and the gore, the body jolting; the last one is their death. */
	private static void blow(ServerLevel level, ServerPlayer player, LivingEntity body, Move move, boolean kill) {
		Vec3 middle = body.getBoundingBox().getCenter();
		double x = middle.x, y = middle.y, z = middle.z;
		var random = level.getRandom();
		float pitch = 0.85F + random.nextFloat() * 0.3F;
		boolean heavy = heavy(player);
		switch (move.strike) {
			case SNAP -> {
				if (kill) {
					level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.0F, 0.5F);
					level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 0.6F);
					level.playSound(null, x, y, z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.6F, 0.7F);
				} else {
					// the hold tightens: they struggle, the neck strains
					level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.35F, 0.85F + random.nextFloat() * 0.1F);
					level.playSound(null, x, y, z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.7F, 0.6F);
				}
			}
			case PUNCH -> {
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, kill ? 1.1F : 0.9F, pitch * (kill ? 0.7F : 0.9F));
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.5F, pitch * 0.8F);
				BladeBlood.blunt(body, kill ? 9.0F : 5.0F);
				if (kill) {
					level.playSound(null, x, y, z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.7F, 0.6F);
				}
			}
			case CRUSH -> {
				level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, kill ? 1.0F : 0.7F, pitch * (heavy ? 0.7F : 0.9F));
				level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.6F, pitch * 0.6F);
				dust(level, body, kill ? 30 : 14);
				if (kill) {
					BladeBlood.crushed(body);
				} else {
					BladeBlood.blunt(body, 7.0F);
				}
			}
			case SLAM -> {
				if (kill) {
					level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0F, heavy ? 0.55F : 0.7F);
					level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 0.5F);
					dust(level, body, SLAM_DUST);
					KitchenCompat.jolt(body);
					BladeBlood.crushed(body);
				} else {
					// run through on the way up (the impale)
					level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, pitch * 0.8F);
					level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, pitch * 0.9F);
					BladeBlood.splash(body, 6.0F);
				}
			}
			case THROW -> {
				level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.3F, 1.6F);
				dust(level, body, SLAM_DUST + 10);
				KitchenCompat.jolt(body);
				BladeBlood.crushed(body);
			}
			case TEAR -> {
				if (kill) {
					level.playSound(null, x, y, z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
					level.playSound(null, x, y, z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.4F);
					level.playSound(null, x, y, z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.9F, 0.55F);
					BladeBlood.splash(body, 14.0F);
				} else {
					// the grip taken: the suit's goo over them, the first wrench
					level.playSound(null, x, y, z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.9F, 0.5F);
					level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.4F, 0.8F);
					BladeBlood.blunt(body, 5.0F);
				}
			}
			case BITE -> {
				if (kill) {
					level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.1F, 0.6F);
					level.playSound(null, x, y, z, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
					level.playSound(null, x, y, z, SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 0.6F, 0.7F);
					BladeBlood.splash(body, 12.0F);
				} else {
					// hauled up to the maw, the jaw opening
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.9F, 0.45F);
					level.playSound(null, x, y, z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.7F, 0.55F);
				}
			}
			case PIERCE -> {
				level.playSound(null, x, y, z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.9F, kill ? 0.9F : 1.1F);
				level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, pitch * (kill ? 0.6F : 0.8F));
				level.sendParticles(ParticleTypes.CRIT, x, y, z, 8, 0.2, 0.2, 0.2, 0.2);
				BladeBlood.splash(body, kill ? 10.0F : 6.0F);
			}
			case QUAKE -> {
				level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.5F);
				level.playSound(null, x, y, z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0F, 0.5F);
				quake(level, player, body);
				BladeBlood.crushed(body);
			}
		}
		// (the body jolts with it; no damage: the last blow is the death outright)
		level.broadcastDamageEvent(body, level.damageSources().playerAttack(player));
		Shakes.send(player, kill ? KILL_SHAKE : BEAT_SHAKE, body.position());
		if (body instanceof ServerPlayer victim) {
			Shakes.send(victim, kill ? KILL_SHAKE : BEAT_SHAKE, player.position());
		}
		if (!kill) {
			return;
		}
		switch (move.strike) {
			case CRUSH, SLAM, THROW -> Shakes.around(level, body.position(), AROUND_RANGE, AROUND_SHAKE);
			case QUAKE -> Shakes.around(level, body.position(), HEAVY_AROUND_RANGE, HEAVY_AROUND_SHAKE);
			default -> {
			}
		}
		boolean gibs = move.strike == Strike.TEAR && KitchenCompat.canBlowApart(body);
		if (gibs) {
			// torn apart: chunks, no body (Kitchen)
			KitchenCompat.leaveNoCorpse(body);
		}
		Downed.execute(level, body, player);
		if (move.strike == Strike.BITE) {
			// (fed on the kill, never the living)
			player.heal(BITE_HEAL);
		}
		if (move.strike == Strike.CRUSH || move.strike == Strike.SLAM || move.strike == Strike.THROW || move.strike == Strike.QUAKE) {
			// (a spider under it is crushed flat: legs, no body (Kitchen))
			Crushing.mark(body);
		}
		if (gibs) {
			KitchenCompat.blowApart(body);
		}
	}

	/** The ground's dust kicked up under {@code body}. */
	private static void dust(ServerLevel level, LivingEntity body, int count) {
		BlockState on = body.getBlockStateOn();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, on.isAir() ? Blocks.DIRT.defaultBlockState() : on), body.getX(),
			body.getBoundingBox().minY + 0.05, body.getZ(), count, body.getBbWidth() * 0.6, 0.05, body.getBbWidth() * 0.6, 0.15);
	}

	/**
	 * The stomp cracks the ground: the block under them and a ring of {@link #QUAKE_RING} round it are cracked
	 * ({@code destroyBlockProgress}, as the wall climb's slams), fading after {@link #QUAKE_CRACK_TICKS}, in a burst of
	 * their dust. Nothing breaks.
	 */
	private static void quake(ServerLevel level, ServerPlayer player, LivingEntity body) {
		long now = level.getGameTime();
		BlockPos under = BlockPos.containing(body.getX(), body.getBoundingBox().minY - 0.1, body.getZ());
		List<BlockPos> spots = new ArrayList<>();
		spots.add(under);
		for (int i = 0; i < QUAKE_RING; i++) {
			double angle = Math.PI * 2.0 * i / QUAKE_RING + Math.PI / 4.0;
			spots.add(under.offset(Mth.floor(Math.cos(angle) * QUAKE_RING_RADIUS + 0.5), 0, Mth.floor(Math.sin(angle) * QUAKE_RING_RADIUS + 0.5)));
		}
		int i = 0;
		for (BlockPos pos : spots) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
				continue;
			}
			int id = player.getId() * 37 + 1020 + i++;
			CRACKS.removeIf(crack -> crack.id() == id && crack.dimension() == level.dimension());
			level.destroyBlockProgress(id, pos, QUAKE_CRACK_STAGE);
			CRACKS.add(new Crack(level.dimension(), pos, id, now + QUAKE_CRACK_TICKS));
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
				QUAKE_DUST / spots.size(), 0.3, 0.1, 0.3, 0.18);
			if (pos.equals(under)) {
				level.playSound(null, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.2F, 0.45F);
			}
		}
	}

	/** The stomp's cracks fade once their time is up. */
	private static void fadeCracks(MinecraftServer server) {
		if (CRACKS.isEmpty()) {
			return;
		}
		for (Iterator<Crack> it = CRACKS.iterator(); it.hasNext();) {
			Crack crack = it.next();
			ServerLevel level = server.getLevel(crack.dimension());
			if (level == null) {
				it.remove();
			} else if (level.getGameTime() >= crack.until()) {
				level.destroyBlockProgress(crack.id(), crack.pos(), -1);
				it.remove();
			}
		}
	}

	/** It's over (played out, or stopped): the marks off, the roots gone, the body's weight back. */
	private static void end(ServerPlayer player, LivingEntity body) {
		player.removeAttached(EXECUTING);
		root(player, false);
		TARGETED.remove(body.getUUID());
		// (unrooting one that never was is nothing)
		root(body, false);
		WebTendrils.weightless(body, false);
		if (body instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(Vec3.ZERO));
		}
	}

	private static void root(LivingEntity entity, boolean on) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
		if (on) {
			if (speed != null) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(ROOT_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (jump != null) {
				jump.addOrUpdateTransientModifier(new AttributeModifier(JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else {
			if (speed != null) {
				speed.removeModifier(ROOT_ID);
			}
			if (jump != null) {
				jump.removeModifier(JUMP_ID);
			}
		}
	}
}
