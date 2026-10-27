package com.jaymospowers.freerun;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.power.web.WebParkour;
import com.jaymospowers.power.web.WebWallRun;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.14.3) Freerunning, the moves of Jaymo's Kingdoms' Stealth tree, second page (its perks live there; we read the
 * player's ranks by reflection on {@code com.jaymoskingdoms.Skills.rank(Player, String)}, as {@code Meditate} does, so
 * without Kingdoms nobody has them). Open to everyone whatever their power; a web slinger keeps their own web parkour
 * ({@code WebParkour}, {@code WebWallRun}) and these stand aside while one of those moves is on.
 * <ul>
 * <li><b>Climbing</b> (everyone since 0.17.73; Climber's 3 ranks lengthen the grip, 2 and 3 also climb faster): jump
 * at a tall wall holding forward (any solid wall but ice, glass and leaves) and you cling to it; hold forward to climb ({@link #CLIMB_SPEED}), strafe to shimmy, Sneak lets go. The grip lasts
 * {@link #GRIP_TICKS}, then you slip down it. At the top the ledge grab ({@link Ledge}) takes over and you pull up and
 * over.</li>
 * <li><b>Freerunner</b> (2 ranks): rank 1, a sprinting jump at something waist-high (at most 1.5 blocks: a fence, a
 * wall, a block) vaults it at full speed; rank 2, a wall kick: in the air beside or facing a wall, Jump kicks off it
 * ({@link #KICK_UP} up, {@link #KICK_OUT} out), each wall face once until you land.</li>
 * <li><b>Sure Footing</b>: (0.15.67) a dodge made as you land rolls the fall off: no fall damage
 * ({@link LandingRoll}, with a forgiving window either side of touchdown). Narrow tops ({@link #NARROW}) are no longer
 * its: anyone walks and runs them, arms out to balance.</li>
 * <li><b>Leap of Faith</b>: from any height, landing in a hay bale, water or leaves ({@link #SOFT_LANDING}) takes no
 * fall damage (Andy's one exception to "fall damage always applies"); falling looking down, you dive head first.</li>
 * <li><b>Bar Swing</b>: jump at a fence, iron bar or chain lying above your head to catch it and hang, swinging; Jump
 * lets go forward ({@link #RELEASE_FORWARD}, {@link #RELEASE_UP}), Sneak drops.</li>
 * <li><b>Wall Runner</b> (2 ranks): a sprinting jump alongside a wall runs you along it for {@link #RUN_TICKS}, light
 * on gravity; then you drop, or kick off it with Freerunner 2.</li>
 * </ul>
 * The moves are the client's (player movement is client-authoritative; see {@code client/freerun/FreerunClient}): it
 * tells the server each one as it starts ({@link Move}). The server checks the rank and the geometry (as
 * {@code WebParkour.move} and {@code Ledge.grab} do), charges the hunger, keeps a cling, a hang or a wall run from
 * counting as a fall (a vault or a kick does not), lets them hang in the air without being kicked
 * ({@link #relaxesMovement}), and syncs {@link #MOVE}, {@link #HANG} and {@link #WALL} so everyone sees the poses
 * ({@code WebParkourPose}, {@code BarHangPose}, {@code WebWallRunPose}). Each move tags the player
 * {@link #XP_TAG} for Kingdoms, which turns it into a little Stealth experience.
 */
public final class Freerun {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/**
	 * Climbing (everyone, 0.17.73): climbing speed (blocks/tick) and how long the grip holds before you slip (ticks: 3,
	 * 6, 8, 10 s), by Climber rank 0 to 3. Rank 1 only lengthens the grip; ranks 2 and 3 also climb faster.
	 */
	public static final double[] CLIMB_SPEED = {0.05, 0.05, 0.07, 0.09};
	public static final int[] GRIP_TICKS = {60, 120, 160, 200};
	/** Climber: shimmying along the wall, and slipping down it once the grip is gone (blocks/tick); how long you slip before you fall off. */
	public static final double SHIMMY_SPEED = 0.06, SLIP_SPEED = 0.07;
	public static final int SLIP_TICKS = 60;
	/** Climber: the wall must still be there this high over your eyes (blocks) to cling; lower ones are the ledge grab's. */
	public static final double CLING_WALL_ABOVE_EYES = 1.2;
	/** Climber: falling no faster than this (blocks/tick) to catch a wall. */
	public static final double CLING_CATCH_FALL = 0.6;
	/** Climber: letting go pushes you this fast off the wall (blocks/tick). */
	public static final double LET_GO_PUSH = 0.08;
	/** Blocks no one climbs (with ice, glass and leaves, which are checked anyway). */
	public static final TagKey<Block> NO_CLIMB = TagKey.create(Registries.BLOCK, JaymosPowers.id("freerun_no_climb"));

	/** Freerunner 1: the vault clears things up to these heights (blocks over your feet), found this far ahead (blocks). */
	public static final double[] VAULT_HEIGHTS = {1.0, 1.5};
	public static final double VAULT_REACH = 1.0;
	/** Freerunner 1: the vault's upward kick for a low (to 1 block) and a high (to 1.5) obstacle, and the least speed ahead it keeps (blocks/tick). */
	public static final double VAULT_UP_LOW = 0.45, VAULT_UP_HIGH = 0.6, VAULT_FORWARD = 0.42;
	public static final int VAULT_COOLDOWN = 10;
	/** Freerunner 2: the wall kick, up and out from the wall (blocks/tick), and the share of your speed along the wall it keeps. */
	public static final double KICK_UP = 0.5, KICK_OUT = 0.45, KICK_KEEP = 0.5;

	/** Narrow tops: balancing on these. */
	public static final TagKey<Block> NARROW = TagKey.create(Registries.BLOCK, JaymosPowers.id("freerun_narrow"));

	/** Leap of Faith: landing on these (or in water) takes no fall damage. */
	public static final TagKey<Block> SOFT_LANDING = TagKey.create(Registries.BLOCK, JaymosPowers.id("freerun_soft_landing"));
	/** Leap of Faith: falling looking at least this far down (degrees), you dive head first. */
	public static final float DIVE_PITCH = 50.0F;

	/** Bar Swing: the bar's underside may be at most this far over your head (blocks) to catch, and this far ahead. */
	public static final double BAR_REACH_ABOVE = 0.9, BAR_REACH_AHEAD = 0.5;
	/** Bar Swing: hanging, your feet are this far under the bar's underside (blocks: the head just under it). */
	public static final double BAR_HANG_DROP = 1.85;
	/** Bar Swing: how long the grip holds (ticks), and letting go with Jump: forward and up (blocks/tick). */
	public static final int BAR_MAX_TICKS = 200;
	/** (review) How near (squared, from the eyes to the bar's middle) a bar must be to catch it and to stay on it. */
	static final double BAR_REACH_SQR = 9.0;
	public static final double RELEASE_FORWARD = 0.9, RELEASE_UP = 0.5;
	/** What can be swung from (horizontal chains only). */
	public static final TagKey<Block> BARS = TagKey.create(Registries.BLOCK, JaymosPowers.id("freerun_bars"));

	/** Wall Runner: how long a run lasts (ticks: 1.5 and 2.5 s), by rank. */
	public static final int[] RUN_TICKS = {30, 50};

	/** (0.15.12) Vertical wall run: how high it climbs (blocks) with no Wall Runner, rank 1 and rank 2; the rise per tick at the start and the end of it. */
	public static final double[] UP_HEIGHT = {2.5, 3.0, 4.0};
	public static final double UP_START = 0.62, UP_END = 0.32;
	/** (0.15.67) Vertical wall run: let go of forward and you slide down the wall this fast (blocks/tick), at most this long (ticks). */
	public static final double UP_SLIDE = 0.16;
	public static final int UP_SLIDE_TICKS = 60;
	/** (0.15.67) Vertical wall run: Jump ejects you backwards off the wall, out and up (blocks/tick). */
	public static final double EJECT_OUT = 0.6, EJECT_UP = 0.5;
	/** Vertical wall run: the wall may be this far ahead when you jump (blocks), and you must face it within this many degrees. */
	public static final double UP_REACH = 1.2;
	public static final float UP_FACING_DEGREES = 35.0F;
	/** Wall transitions: a wall hop reaches a wall this far off (blocks), flies at most this long (ticks), and its upward kick. */
	public static final double HOP_RANGE = 3.0, HOP_UP = 0.42;
	public static final int HOP_TICKS = 14;
	/** Wall transitions: sneaking forward at an edge this long (ticks) catches it into a ledge hang below. */
	public static final int EDGE_HOLD_TICKS = 6;
	/** Pole climb: climbing speed (blocks/tick) by Climber rank 0 to 3, sliding down, and leaping off (sideways/up, blocks/tick). */
	public static final double[] POLE_CLIMB = {0.07, 0.085, 0.10, 0.12};
	public static final double POLE_SLIDE = 0.3, POLE_LEAP_OUT = 0.55, POLE_LEAP_UP = 0.42;
	/** Pole climb: the longest hold (ticks: 2 minutes). */
	public static final int POLE_MAX_TICKS = 2400;
	/** (review) Server: a cling may have no wall against it this many ticks while the client crosses a corner. */
	public static final int CORNER_GRACE_TICKS = 5;

	/** Hunger (exhaustion; 4 is a hunger point): each vault, kick, catch of a wall or bar, wall run and swing off. */
	public static final float MOVE_EXHAUSTION = 0.3F;
	/** Hunger every tick you cling, hang or run, and for every block climbed. */
	public static final float HOLD_EXHAUSTION = 0.008F, CLIMB_EXHAUSTION = 0.3F;

	/** The entity tag Kingdoms reads (and takes off) to give the Stealth experience for a move. */
	public static final String XP_TAG = "jaymospowers_freerun";
	/** Server: a held move not heard to end runs this long past its own end (ticks); moves are checked against walls this far off (blocks). */
	static final int SERVER_SLACK_TICKS = 20;
	static final double SERVER_REACH = 0.8;
	/** After a held move ends, the server goes easy on the player's movement this long (ticks). */
	static final int RELEASE_GRACE_TICKS = 20;
	/** How often (ticks) each player's ranks are read from Kingdoms again and synced. */
	static final int PERKS_REFRESH_TICKS = 20;
	// ================================================================================================================

	/** Moves (as in {@link Move}): the first four are {@link WebParkour}'s own kinds, so its pose draws them. */
	public static final int NONE = 0, VAULT = WebParkour.VAULT, CLING = WebParkour.CLING, WALL_JUMP = WebParkour.WALL_JUMP, BAR = 5, WALL_RUN = 6,
		RELEASE = 7, UP_RUN = 8, SLIDE = 9, POLE = 10, HOP = 11;

	/** The perks, in the order they're packed into {@link #PERKS} (4 bits each), with their Kingdoms keys. */
	public enum Perk {
		CLIMBER, FREERUNNER, SURE_FOOTING, LEAP_OF_FAITH, BAR_SWING, WALL_RUNNER;

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * Client → server: started move {@code kind} against wall side {@code face} (a horizontal direction's 3D index, or
	 * -1), at the bar {@code pos} for {@link #BAR}; {@link #NONE} ends a held one, {@link #RELEASE} swings off a bar.
	 */
	public record Move(int kind, int face, BlockPos pos) implements CustomPacketPayload {
		public static final Type<Move> TYPE = new Type<>(JaymosPowers.id("freerun_move"));
		public static final StreamCodec<ByteBuf, Move> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Move::kind, ByteBufCodecs.VAR_INT, Move::face, BlockPos.STREAM_CODEC, Move::pos, Move::new);

		@Override
		public Type<Move> type() {
			return TYPE;
		}
	}

	/** The player's freerun ranks, packed 4 bits a perk ({@link Perk} order), for every client (poses, and their own moves). */
	public static final AttachmentType<Integer> PERKS = AttachmentRegistry.create(JaymosPowers.id("freerun_perks"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** A vault, cling or wall kick, in {@link WebParkour#MOVE}'s own form, for {@code WebParkourPose}. */
	public static final AttachmentType<Integer> MOVE = AttachmentRegistry.create(JaymosPowers.id("freerun_move"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** Hanging from a bar: the way the body faces (a horizontal direction's 2D value), for {@code BarHangPose}. */
	public static final AttachmentType<Integer> HANG = AttachmentRegistry.create(JaymosPowers.id("freerun_hang"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** Running along a wall: its side (a horizontal direction's 3D index), as {@link WebWallRun#WALL}, for {@code WebWallRunPose}. */
	public static final AttachmentType<Integer> WALL = AttachmentRegistry.create(JaymosPowers.id("freerun_wall"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));


	private static final boolean KINGDOMS = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static final Method KINGDOMS_RANK;

	static {
		Method rank = null;
		if (KINGDOMS) {
			try {
				rank = Class.forName("com.jaymoskingdoms.Skills").getMethod("rank", Player.class, String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				rank = null;
			}
		}
		KINGDOMS_RANK = rank;
	}

	/** Server: a move in progress. */
	private static final class Doing {
		final int kind;
				int face;
		final BlockPos pos;
		int ticks;
		double lastY;
		/** (review) Where it began (height), and ticks the wall has been missing (a corner is crossed in a few). */
		double startY;
		int missing;

		Doing(int kind, int face, BlockPos pos, double y) {
			this.kind = kind;
			this.face = face;
			this.pos = pos;
			this.lastY = y;
			this.startY = y;
			}
	}

	private static final Map<UUID, Doing> DOING = new HashMap<>();
	/** Server: wall faces kicked off and run along since the player last touched the ground (bits by 3D index). */
	private static final Map<UUID, Integer> KICKED = new HashMap<>(), RAN = new HashMap<>(), UP_RAN = new HashMap<>();
	/** (review) Player → ticks spent clinging since last on the ground: a new cling doesn't renew the grip. */
	private static final Map<UUID, Integer> GRIPPED = new HashMap<>();
	/** Server: until when (game time) a player just off a wall or bar isn't kicked for how they move. */
	private static final Map<UUID, Long> GRACE = new HashMap<>();
	/** Server: the last vault look, so the next differs. */
	private static final Map<UUID, Integer> LAST_VARIANT = new HashMap<>();

	private Freerun() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Move.TYPE, Move.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Move.TYPE, (payload, ctx) -> move(ctx.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(Freerun::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL) && rank(player, Perk.LEAP_OF_FAITH) > 0 && softLanding(player)) {
				leapOfFaith(player);
				return false;
			}
			return true;
		});
		LandingRoll.init();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		FastRun.init();
		Slide.init();
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DOING.clear();
			KICKED.clear();
			RAN.clear();
			UP_RAN.clear();
			GRIPPED.clear();
			GRACE.clear();
			LAST_VARIANT.clear();
		});
	}

	private static void forget(UUID id) {
		DOING.remove(id);
		KICKED.remove(id);
		RAN.remove(id);
		UP_RAN.remove(id);
		GRIPPED.remove(id);
		GRACE.remove(id);
		LAST_VARIANT.remove(id);
	}

	// ---- ranks (both sides) -----------------------------------------------------------------------------------------

	/** Server: the player's rank in {@code perk}, read from Kingdoms now (0 without it). */
	public static int rank(Player player, Perk perk) {
		if (KINGDOMS_RANK == null) {
			return 0;
		}
		try {
			return Math.max(0, Math.min(15, (int)KINGDOMS_RANK.invoke(null, player, perk.key())));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	/** The player's rank in {@code perk} as last synced ({@link #PERKS}): any side, any player. */
	public static int perk(Player player, Perk perk) {
		Integer packed = player.getAttached(PERKS);
		return packed == null ? 0 : packed >> (perk.ordinal() * 4) & 0xF;
	}

	/** Whether Kingdoms (the perks' home) is loaded. */
	public static boolean kingdoms() {
		return KINGDOMS;
	}

	// ---- rules and geometry (both sides) ----------------------------------------------------------------------------

	/**
	 * Whether this player may freerun right now: on their own feet or in the air (not flying, gliding, swimming,
	 * riding, carried, carrying, crawling, meditating, in a power's or the web's own movement: {@link Ledge#canHang}),
	 * and not on a ledge. (0.14.5) Nor on a broken leg out of a splint, nor with both arms broken (Wounds'
	 * {@link Breaks#legsUsable}, {@link Breaks#armsUsable}: synced, so both sides agree).
	 */
	public static boolean canMove(Player player) {
		return Ledge.canHang(player) && !Ledge.onLedge(player) && Breaks.legsUsable(player) && Breaks.armsUsable(player);
	}

	/**
	 * (0.14.5) {@link #canMove}, and for the moves that hang on the hands and feet (a cling and climb, a bar swing) a
	 * sound grip too: one broken arm or a sprained foot rules those out ({@link Breaks#gripUsable}).
	 */
	public static boolean canMove(Player player, int kind) {
		return canMove(player) && (!gripMove(kind) || Breaks.gripUsable(player));
	}

	/** (0.14.5) The moves that hang on both hands and the feet: {@link #CLING} (the climb) and {@link #BAR}. */
	public static boolean gripMove(int kind) {
		return kind == CLING || kind == BAR || kind == POLE;
	}

	/** (0.15.12) The moves the player stays in for a while, in the air or on a wall (they arrest a fall and are let hang without being kicked). */
	static boolean held(int kind) {
		return kind == CLING || kind == BAR || kind == WALL_RUN || kind == UP_RUN || kind == POLE;
	}

	/** Server: the player is in one of the {@link #held} moves right now. */
	static boolean holdingMove(ServerPlayer player) {
		Doing doing = DOING.get(player.getUUID());
		return doing != null && held(doing.kind);
	}

	/** The height (blocks over the feet) of something waist-high just ahead a vault would clear ({@link #VAULT_HEIGHTS}), or 0. */
	public static double vaultHeight(Level level, Player player, Vec3 dir, double reach) {
		AABB box = player.getBoundingBox();
		AABB knee = new AABB(box.minX, box.minY + 0.3, box.minZ, box.maxX, box.minY + 0.9, box.maxZ).expandTowards(dir.x * reach, 0.0, dir.z * reach);
		if (level.noCollision(player, knee)) {
			return 0.0;
		}
		for (double h : VAULT_HEIGHTS) {
			// room straight up to clear it, and over it along the way to beyond it
			AABB over = box.move(0.0, h + 0.05, 0.0).expandTowards(dir.x * (reach + 0.7), 0.0, dir.z * (reach + 0.7));
			if (level.noCollision(player, over)) {
				return h;
			}
		}
		return 0.0;
	}

	/** Climber: the wall on side {@code face} is one you can cling to (and tall enough that it isn't the ledge grab's). */
	public static boolean climbable(Level level, Player player, Direction face, double reach) {
		if (!WebWallRun.wallAt(level, player, face, reach)) {
			return false;
		}
		AABB box = player.getBoundingBox();
		double eyes = player.getEyeY();
		AABB above = new AABB(box.minX, eyes + CLING_WALL_ABOVE_EYES - 0.2, box.minZ, box.maxX, eyes + CLING_WALL_ABOVE_EYES, box.maxZ)
			.move(face.getStepX() * reach, 0.0, face.getStepZ() * reach);
		if (level.noCollision(player, above)) {
			return false;
		}
		boolean any = false;
		for (double up : new double[] {0.5, 1.2}) {
			BlockPos pos = BlockPos.containing(player.getX() + face.getStepX() * (player.getBbWidth() * 0.5 + reach),
				player.getY() + up, player.getZ() + face.getStepZ() * (player.getBbWidth() * 0.5 + reach));
			BlockState state = level.getBlockState(pos);
			if (state.getCollisionShape(level, pos).isEmpty()) {
				continue;
			}
			if (state.is(NO_CLIMB) || state.is(BlockTags.ICE) || state.is(BlockTags.LEAVES) || state.getSoundType() == SoundType.GLASS) {
				return false;
			}
			any = true;
		}
		return any;
	}

	/** Bar Swing: whether one can hang from this block (horizontal chains only). */
	public static boolean isBar(BlockState state) {
		return state.is(BARS) && (!state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y);
	}

	/** Bar Swing: a bar just over the player's head (over them, or a little ahead) with room to hang under it, or null. */
	public static @Nullable BlockPos barAbove(Level level, Player player) {
		AABB box = player.getBoundingBox();
		double head = box.maxY;
		Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(BAR_REACH_AHEAD);
		for (Vec3 at : new Vec3[] {player.position(), player.position().add(ahead)}) {
			for (int y = Mth.floor(head - 0.05); y <= Mth.floor(head + BAR_REACH_ABOVE); y++) {
				BlockPos pos = BlockPos.containing(at.x, y, at.z);
				double bottom = barBottom(level, pos);
				if (Double.isNaN(bottom) || bottom < head - 0.05 || bottom > head + BAR_REACH_ABOVE) {
					continue;
				}
				AABB hang = player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(hangSpot(pos, bottom)).deflate(1.0E-7);
				if (level.noCollision(player, hang)) {
					return pos.immutable();
				}
			}
		}
		return null;
	}

	/** The underside of the bar at {@code pos} (its collision's bottom), or NaN if it isn't one. */
	public static double barBottom(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!isBar(state)) {
			return Double.NaN;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		return shape.isEmpty() ? Double.NaN : pos.getY() + shape.min(Direction.Axis.Y);
	}

	/** Where the feet hang under the bar at {@code pos} whose underside is at {@code bottom}. */
	public static Vec3 hangSpot(BlockPos pos, double bottom) {
		return new Vec3(pos.getX() + 0.5, bottom - BAR_HANG_DROP, pos.getZ() + 0.5);
	}

	/** Standing on the top of one of {@code tag}'s blocks (under the middle of the feet; tall ones like fences too). */
	public static boolean standingOn(Level level, Player player, TagKey<Block> tag, double x, double z) {
		double feet = player.getY();
		BlockPos pos = BlockPos.containing(x, feet - 0.05, z);
		for (BlockPos p : new BlockPos[] {pos, pos.below()}) {
			BlockState state = level.getBlockState(p);
			if (!state.is(tag)) {
				continue;
			}
			VoxelShape shape = state.getCollisionShape(level, p);
			if (!shape.isEmpty() && Math.abs(p.getY() + shape.max(Direction.Axis.Y) - feet) < 0.1) {
				return true;
			}
		}
		return false;
	}

	/** On the narrow top of a fence, wall, pane, bar, chain or rod ({@link #NARROW}). */
	public static boolean onNarrowTop(Player player) {
		return player.onGround() && !player.isPassenger() && !player.isSpectator() && standingOn(player.level(), player, NARROW, player.getX(), player.getZ());
	}

	/** (0.15.67) Running up a wall (or sliding back down it), as last synced: any side, any player. */
	public static boolean upRunning(Player player) {
		Integer move = player.getAttached(MOVE);
		return move != null && move / 8 == UP_RUN;
	}

	/** Balancing on a narrow top, arms out (any side, any player). (0.15.67) Everyone now, not only Sure Footing. */
	public static boolean balancing(Player player) {
		return onNarrowTop(player) && !player.isShiftKeyDown();
	}

	/** Leap of Faith: falling looking down, head first (any side, any player). */
	public static boolean dives(Player player) {
		return perk(player, Perk.LEAP_OF_FAITH) > 0 && player.getXRot() >= DIVE_PITCH;
	}

	/** Landing in water, or on a hay bale or leaves (under the middle or a corner of the feet). */
	public static boolean softLanding(Player player) {
		if (player.isInWater()) {
			return true;
		}
		AABB box = player.getBoundingBox();
		double[][] spots = {{player.getX(), player.getZ()}, {box.minX, box.minZ}, {box.minX, box.maxZ}, {box.maxX, box.minZ}, {box.maxX, box.maxZ}};
		for (double[] at : spots) {
			if (standingOn(player.level(), player, SOFT_LANDING, at[0], at[1])) {
				return true;
			}
		}
		return false;
	}

	/** Server: a player clinging, hanging or wall running (or just off one) moves however it puts them, without being kicked. */
	public static boolean relaxesMovement(ServerPlayer player) {
		Doing doing = DOING.get(player.getUUID());
		if (doing != null && held(doing.kind)) {
			return true;
		}
		Long until = GRACE.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	// ---- server -----------------------------------------------------------------------------------------------------

	private static void move(ServerPlayer player, Move move) {
		int kind = move.kind();
		Doing current = DOING.get(player.getUUID());
		if (kind == NONE) {
			Slide.end(player);
			stop(player);
			return;
		}
		if (kind == SLIDE) {
			if (Slide.valid(player) && !Ledge.powerMoving(player)) {
				stop(player);
				Slide.start(player);
			} else {
				Slide.end(player);
			}
			return;
		}
		ServerLevel level = player.level();
		if (kind == RELEASE) {
			if (current != null && current.kind == BAR) {
				player.causeFoodExhaustion(MOVE_EXHAUSTION);
				level.playSound(null, player.getX(), player.getY() + 1.8, player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4F, 1.5F);
				moved(player);
			}
			stop(player);
			return;
		}
		Direction side = move.face() >= 0 && move.face() < Direction.values().length ? Direction.from3DDataValue(move.face()) : null;
		boolean wall = side != null && side.getAxis().isHorizontal();
		if (!canMove(player, kind) || Ledge.powerMoving(player) || !valid(level, player, kind, wall ? side : null, move.pos(), current)) {
			stop(player);
			return;
		}
		stop(player);
		BlockPos pos = kind == BAR ? move.pos().immutable() : BlockPos.ZERO;
		DOING.put(player.getUUID(), new Doing(kind, wall ? side.get3DDataValue() : -1, pos, player.getY()));
		player.causeFoodExhaustion(MOVE_EXHAUSTION);
		moved(player);
		switch (kind) {
			case VAULT -> {
				int last = LAST_VARIANT.getOrDefault(player.getUUID(), -1);
				int variant = player.getRandom().nextInt(last < 0 ? WebParkour.VARIANTS : WebParkour.VARIANTS - 1);
				if (last >= 0 && variant >= last) {
					variant++;
				}
				LAST_VARIANT.put(player.getUUID(), variant);
				player.setAttached(MOVE, (variant * 8 + VAULT) * 8);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3F, 1.6F);
				WebParkour.dust(level, player, null, 4);
			}
			case CLING -> {
				player.setAttached(MOVE, CLING * 8 + side.get3DDataValue() + 1);
				player.resetFallDistance();
				WebParkour.dust(level, player, side, 5);
			}
			case WALL_JUMP -> {
				KICKED.merge(player.getUUID(), 1 << side.get3DDataValue(), (a, b) -> a | b);
				player.setAttached(MOVE, WALL_JUMP * 8 + side.get3DDataValue() + 1);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6F, 0.8F);
				WebParkour.dust(level, player, side, 8);
			}
			case UP_RUN -> {
				UP_RAN.merge(player.getUUID(), 1 << side.get3DDataValue(), (a, b) -> a | b);
				// (0.15.67) its own pose now (UpRunPose), not the climb's
				player.setAttached(MOVE, UP_RUN * 8 + side.get3DDataValue() + 1);
				player.resetFallDistance();
				WebParkour.dust(level, player, side, 6);
			}
			case POLE -> {
				player.setAttached(MOVE, CLING * 8 + side.get3DDataValue() + 1);
				player.resetFallDistance();
			}
			case HOP -> {
				player.setAttached(MOVE, WALL_JUMP * 8 + side.get3DDataValue() + 1);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6F, 1.0F);
				WebParkour.dust(level, player, side, 6);
			}
			case WALL_RUN -> {
			RAN.merge(player.getUUID(), 1 << side.get3DDataValue(), (a, b) -> a | b);
				player.setAttached(WALL, side.get3DDataValue());
				player.resetFallDistance();
			}
			case BAR -> {
				player.setAttached(HANG, Direction.fromYRot(player.getYRot()).get2DDataValue());
				player.resetFallDistance();
				SoundType sound = level.getBlockState(pos).getSoundType();
				level.playSound(null, player.getX(), player.getY() + 1.9, player.getZ(), sound.getHitSound(), SoundSource.PLAYERS, sound.getVolume() * 0.8F,
					sound.getPitch() * 0.8F);
			}
			default -> {
			}
		}
	}

	/** Server: whether move {@code kind} (against wall {@code side}, at bar {@code pos}) checks out: the rank, and the wall, obstacle or bar really there. */
	private static boolean valid(ServerLevel level, ServerPlayer player, int kind, @Nullable Direction side, BlockPos pos, @Nullable Doing current) {
		switch (kind) {
			case VAULT:
				return rank(player, Perk.FREERUNNER) >= 1
					&& vaultHeight(level, player, Vec3.directionFromRotation(0.0F, player.getYRot()), VAULT_REACH + SERVER_REACH) > 0.0;
			case CLING:
				return side != null && GRIPPED.getOrDefault(player.getUUID(), 0) < grip(player)
					&& climbable(level, player, side, SERVER_REACH);
			case WALL_JUMP:
				return side != null && rank(player, Perk.FREERUNNER) >= 2 && WebWallRun.wallAt(level, player, side, SERVER_REACH)
					&& (current != null && current.kind == CLING || (KICKED.getOrDefault(player.getUUID(), 0) & 1 << side.get3DDataValue()) == 0);
			case WALL_RUN:
				return side != null && rank(player, Perk.WALL_RUNNER) >= 1 && WebWallRun.wallAt(level, player, side, SERVER_REACH)
					&& (RAN.getOrDefault(player.getUUID(), 0) & 1 << side.get3DDataValue()) == 0;
			case UP_RUN:
				// (review) it starts from a jump: off the ground and rising
				return side != null && !player.onGround() && player.getY() > player.yo
					&& (FastRun.active(player) || player.isSprinting() && rank(player, Perk.WALL_RUNNER) >= 1)
					&& FreerunWalls.runnableNear(level, player, side)
					&& (UP_RAN.getOrDefault(player.getUUID(), 0) & 1 << side.get3DDataValue()) == 0;
			case POLE:
				return side != null && FreerunWalls.poleAt(level, player, side, SERVER_REACH);
			case HOP:
				// (review) from a cling, at the wall it holds, off the ground
				return side != null && current != null && current.kind == CLING && !player.onGround()
					&& WebWallRun.wallAt(level, player, side, SERVER_REACH);
			case BAR:
			// (rank and reach first: the client's position mustn't make the server look at, or load, a far block)
				if (rank(player, Perk.BAR_SWING) < 1 || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) >= BAR_REACH_SQR) {
					return false;
				}
				double bottom = barBottom(level, pos);
				return !Double.isNaN(bottom) && bottom > player.getEyeY() - 0.6;
			default:
				return false;
		}
	}

	/** The ticks {@code player}'s grip lasts on a wall (Climber's rank, 0 for anyone), before they start to slip. */
	public static int grip(Player player) {
		return GRIP_TICKS[Mth.clamp(perk(player, Perk.CLIMBER), 0, GRIP_TICKS.length - 1)];
	}

	/** {@code player}'s climbing speed up a wall (blocks/tick), by Climber's rank (0 for anyone). */
	public static double climbSpeed(Player player) {
		return CLIMB_SPEED[Mth.clamp(perk(player, Perk.CLIMBER), 0, CLIMB_SPEED.length - 1)];
	}

	/** Tells Kingdoms a move was made (it gives the Stealth experience, at most so often). */
	static void moved(ServerPlayer player) {
		player.addTag(XP_TAG);
	}

	private static void stop(ServerPlayer player) {
		Doing doing = DOING.remove(player.getUUID());
		if (doing != null && held(doing.kind)) {
		GRACE.put(player.getUUID(), player.level().getGameTime() + RELEASE_GRACE_TICKS);
		}
		player.removeAttached(MOVE);
		player.removeAttached(HANG);
		player.removeAttached(WALL);
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Doing>> it = DOING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Doing> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Doing doing = entry.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			Direction side = doing.face >= 0 ? Direction.from3DDataValue(doing.face) : null;
			int max = switch (doing.kind) {
				case VAULT -> WebParkour.VAULT_TICKS;
				case WALL_JUMP -> WebParkour.WALL_JUMP_TICKS;
				case HOP -> HOP_TICKS + SERVER_SLACK_TICKS;
				case UP_RUN -> 24 + UP_SLIDE_TICKS + SERVER_SLACK_TICKS;
				case POLE -> POLE_MAX_TICKS + SERVER_SLACK_TICKS;
				case CLING -> grip(player) + SLIP_TICKS + SERVER_SLACK_TICKS;
				case WALL_RUN -> RUN_TICKS[Mth.clamp(perk(player, Perk.WALL_RUNNER), 1, RUN_TICKS.length) - 1] + SERVER_SLACK_TICKS;
				default -> BAR_MAX_TICKS + SERVER_SLACK_TICKS;
			};
			// (review) the climbs are the client's, but the server holds them to their own speeds and heights
			double rise = player.getY() - doing.lastY;
			boolean overrun = switch (doing.kind) {
				case UP_RUN -> player.getY() > doing.startY + UP_HEIGHT[Mth.clamp(perk(player, Perk.WALL_RUNNER), 0, UP_HEIGHT.length - 1)] + 0.5
					|| -rise > UP_SLIDE * 2.0 + 0.1;
				case POLE -> rise > POLE_CLIMB[Mth.clamp(perk(player, Perk.CLIMBER), 0, POLE_CLIMB.length - 1)] * 2.0 + 0.1 || -rise > POLE_SLIDE * 2.0 + 0.1;
				case CLING -> rise > climbSpeed(player) * 2.0 + 0.1;
				default -> false;
			};
			boolean lost = overrun || switch (doing.kind) {
			case UP_RUN -> doing.ticks > 2 && player.onGround() || side == null	|| !FreerunWalls.wallNear(level, player, side);
			case POLE -> doing.ticks > 3 && player.onGround() || side == null || !FreerunWalls.poleAt(level, player, side, SERVER_REACH);
			case CLING -> player.onGround() || side == null || !wallOrCorner(level, player, doing, side)
					|| GRIPPED.getOrDefault(player.getUUID(), 0) > grip(player) + SLIP_TICKS + SERVER_SLACK_TICKS;
				case WALL_RUN -> doing.ticks > 2 && player.onGround() || side == null || !WebWallRun.wallAt(level, player, side, SERVER_REACH);
				case BAR -> player.onGround() || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(doing.pos)) >= BAR_REACH_SQR
					|| Double.isNaN(barBottom(level, doing.pos));
				default -> false;
			};
			if (!player.isAlive() || doing.ticks >= max || lost || !canMove(player, doing.kind)) {
				it.remove();
				if (held(doing.kind)) {
				GRACE.put(player.getUUID(), level.getGameTime() + RELEASE_GRACE_TICKS);
				}
				player.removeAttached(MOVE);
				player.removeAttached(HANG);
				player.removeAttached(WALL);
				continue;
			}
			doing.ticks++;
			if (doing.kind == CLING) {
				GRIPPED.merge(player.getUUID(), 1, Integer::sum);
			}
			if (held(doing.kind)) {
			// holding on isn't falling, but it is work
				player.resetFallDistance();
				player.causeFoodExhaustion(HOLD_EXHAUSTION);
			}
			if (doing.kind == CLING || doing.kind == POLE || doing.kind == UP_RUN) {
			double climbed = player.getY() - doing.lastY;
				if (climbed > 0.0) {
					player.causeFoodExhaustion((float)(climbed * CLIMB_EXHAUSTION));
				}
				if (doing.kind == CLING && doing.ticks == grip(player)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.freerun.slipping").withStyle(ChatFormatting.GRAY));
				}
			}
			doing.lastY = player.getY();
			if (doing.kind == WALL_RUN && doing.ticks % 4 == 0 && side != null) {
				WebWallRun.footstep(player, side);
			}
		}
		boolean refresh = server.getTickCount() % PERKS_REFRESH_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (refresh) {
				refreshPerks(player);
			}
			if (player.onGround() || player.isInWater() || player.onClimbable()) {
				KICKED.remove(player.getUUID());
				RAN.remove(player.getUUID());
				UP_RAN.remove(player.getUUID());
				GRIPPED.remove(player.getUUID());
			}
		}
		if (!GRACE.isEmpty() && server.getTickCount() % 100 == 0) {
			long now = server.overworld().getGameTime();
			GRACE.values().removeIf(until -> until < now);
		}
	}

	/**
	 * (0.15.12) A cling follows its wall round a corner: when the wall it holds is gone it takes whichever other wall
	 * is against the player (the client turns the body; the pose follows {@link #MOVE}). False when there is none.
	 */
	private static boolean wallOrCorner(ServerLevel level, ServerPlayer player, Doing doing, Direction side) {
		if (WebWallRun.wallAt(level, player, side, SERVER_REACH)) {
			doing.missing = 0;
			return true;
		}
		for (Direction other : Direction.Plane.HORIZONTAL) {
			if (other != side && climbable(level, player, other, SERVER_REACH)) {
				doing.face = other.get3DDataValue();
				doing.missing = 0;
				player.setAttached(MOVE, CLING * 8 + other.get3DDataValue() + 1);
				return true;
			}
		}
		// the client's corner step takes a few ticks: the wall may be missing that long
		return ++doing.missing <= CORNER_GRACE_TICKS;
	}

	/** Reads the player's ranks from Kingdoms and syncs them (only when they change). */
	private static void refreshPerks(ServerPlayer player) {
		int packed = 0;
		for (Perk perk : Perk.values()) {
			packed |= rank(player, perk) << (perk.ordinal() * 4);
		}
		Integer was = player.getAttached(PERKS);
		if (packed == 0 && was != null) {
			player.removeAttached(PERKS);
		} else if (packed != 0 && (was == null || was != packed)) {
			player.setAttached(PERKS, packed);
		}
	}

	/** Leap of Faith: a landing in hay, water or leaves that hurts nothing, with a soft crunch of what took it. */
	private static void leapOfFaith(ServerPlayer player) {
		ServerLevel level = player.level();
		boolean big = player.fallDistance > 6.0;
		player.resetFallDistance();
		BlockPos pos = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
		BlockState state = level.getBlockState(pos);
		if (player.isInWater()) {
			level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.1, player.getZ(), 20, 0.4, 0.05, 0.4, 0.1);
		} else if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), player.getX(), player.getY() + 0.1, player.getZ(), 16, 0.35, 0.1, 0.35, 0.1);
			SoundType sound = state.getSoundType();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), sound.getBreakSound(), SoundSource.PLAYERS, sound.getVolume(), sound.getPitch() * 0.8F);
		}
		if (big) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.freerun.leap_of_faith").withStyle(ChatFormatting.GRAY));
		}
		moved(player);
	}

	/** Keeps one of our modifiers on ({@code amount} non-zero) or off. */
	static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount != 0.0) {
			AttributeModifier current = instance.getModifier(id);
			if (current == null || current.amount() != amount) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
			}
		} else if (instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
	}
}
