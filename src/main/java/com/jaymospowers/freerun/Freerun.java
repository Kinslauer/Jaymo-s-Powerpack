package com.jaymospowers.freerun;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Ledge;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * (0.13.36) Freerunning, the moves of Jaymo's Kingdoms' Stealth tree, second page (its perks live there; we read the
 * player's ranks by reflection on {@code com.jaymoskingdoms.Skills.rank(Player, String)}, as {@code Meditate} does, so
 * without Kingdoms nobody has them). Open to everyone whatever their power; a web slinger keeps their own web parkour
 * ({@code WebParkour}, {@code WebWallRun}) and these stand aside while one of those moves is on.
 * <ul>
 * <li><b>Climber</b> (3 ranks): jump at a tall wall holding forward (any solid wall but ice, glass and leaves) and you
 * cling to it; hold forward to climb ({@link #CLIMB_SPEED}), strafe to shimmy, Sneak lets go. The grip lasts
 * {@link #GRIP_TICKS}, then you slip down it. At the top the ledge grab ({@link Ledge}) takes over and you pull up and
 * over.</li>
 * <li><b>Freerunner</b> (2 ranks): rank 1, a sprinting jump at something waist-high (at most 1.5 blocks: a fence, a
 * wall, a block) vaults it at full speed; rank 2, a wall kick: in the air beside or facing a wall, Jump kicks off it
 * ({@link #KICK_UP} up, {@link #KICK_OUT} out), each wall face once until you land.</li>
 * <li><b>Sure Footing</b>: on narrow tops (fences, walls, panes, bars, chains, rods; {@link #NARROW}) you walk and
 * sprint at full speed ({@link #UNSTEADY_SLOW}, off for now, would slow those without it), knocked back
 * {@link #FOOTING_KNOCKBACK} less, arms out to balance.</li>
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
	/** Climber: climbing speed (blocks/tick) and how long the grip holds before you slip (ticks: 3, 6, 10 s), by rank. */
	public static final double[] CLIMB_SPEED = {0.05, 0.07, 0.09};
	public static final int[] GRIP_TICKS = {60, 120, 200};
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

	/** Sure Footing: knockback taken away on a narrow top (0.75 = 75% less). */
	public static final double FOOTING_KNOCKBACK = 0.75;
	/**
	 * Without Sure Footing (and only with Kingdoms loaded): on a narrow top you're this much slower and can't sprint.
	 * Off (0) until Andy says otherwise: it would slow everyone who walks a fence or wall top.
	 */
	public static final double UNSTEADY_SLOW = 0.0;
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
		RELEASE = 7;

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

	private static final Identifier FOOTING_ID = JaymosPowers.id("freerun_footing"), UNSTEADY_ID = JaymosPowers.id("freerun_unsteady");

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
		final int kind, face;
		final BlockPos pos;
		int ticks;
		double lastY;

		Doing(int kind, int face, BlockPos pos, double y) {
			this.kind = kind;
			this.face = face;
			this.pos = pos;
			this.lastY = y;
		}
	}

	private static final Map<UUID, Doing> DOING = new HashMap<>();
	/** Server: wall faces kicked off and run along since the player last touched the ground (bits by 3D index). */
	private static final Map<UUID, Integer> KICKED = new HashMap<>(), RAN = new HashMap<>();
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
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DOING.clear();
			KICKED.clear();
			RAN.clear();
			GRIPPED.clear();
			GRACE.clear();
			LAST_VARIANT.clear();
		});
	}

	private static void forget(UUID id) {
		DOING.remove(id);
		KICKED.remove(id);
		RAN.remove(id);
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
	 * and not on a ledge.
	 */
	public static boolean canMove(Player player) {
		return Ledge.canHang(player) && !Ledge.onLedge(player);
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

	/** Sure Footing: balancing on a narrow top, arms out (any side, any player, from the synced ranks). */
	public static boolean balancing(Player player) {
		return perk(player, Perk.SURE_FOOTING) > 0 && onNarrowTop(player) && !player.isShiftKeyDown();
	}

	/** Without Sure Footing on a narrow top: slower, no sprint (only where Kingdoms, and so the perk, exists). */
	public static boolean unsteady(Player player) {
		return KINGDOMS && UNSTEADY_SLOW > 0.0 && perk(player, Perk.SURE_FOOTING) == 0 && onNarrowTop(player);
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
		if (doing != null && (doing.kind == CLING || doing.kind == BAR || doing.kind == WALL_RUN)) {
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
			stop(player);
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
		if (!canMove(player) || Ledge.powerMoving(player) || !valid(level, player, kind, wall ? side : null, move.pos(), current)) {
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
				return side != null && rank(player, Perk.CLIMBER) >= 1 && GRIPPED.getOrDefault(player.getUUID(), 0) < grip(player)
					&& climbable(level, player, side, SERVER_REACH);
			case WALL_JUMP:
				return side != null && rank(player, Perk.FREERUNNER) >= 2 && WebWallRun.wallAt(level, player, side, SERVER_REACH)
					&& (current != null && current.kind == CLING || (KICKED.getOrDefault(player.getUUID(), 0) & 1 << side.get3DDataValue()) == 0);
			case WALL_RUN:
				return side != null && rank(player, Perk.WALL_RUNNER) >= 1 && WebWallRun.wallAt(level, player, side, SERVER_REACH)
					&& (RAN.getOrDefault(player.getUUID(), 0) & 1 << side.get3DDataValue()) == 0;
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

	/** The ticks {@code player}'s grip lasts on a wall (Climber's rank), before they start to slip. */
	private static int grip(Player player) {
		return GRIP_TICKS[Mth.clamp(perk(player, Perk.CLIMBER), 1, GRIP_TICKS.length) - 1];
	}

	/** Tells Kingdoms a move was made (it gives the Stealth experience, at most so often). */
	private static void moved(ServerPlayer player) {
		player.addTag(XP_TAG);
	}

	private static void stop(ServerPlayer player) {
		Doing doing = DOING.remove(player.getUUID());
		if (doing != null && (doing.kind == CLING || doing.kind == BAR || doing.kind == WALL_RUN)) {
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
				case CLING -> grip(player) + SLIP_TICKS + SERVER_SLACK_TICKS;
				case WALL_RUN -> RUN_TICKS[Mth.clamp(perk(player, Perk.WALL_RUNNER), 1, RUN_TICKS.length) - 1] + SERVER_SLACK_TICKS;
				default -> BAR_MAX_TICKS + SERVER_SLACK_TICKS;
			};
			boolean lost = switch (doing.kind) {
				case CLING -> player.onGround() || side == null || !WebWallRun.wallAt(level, player, side, SERVER_REACH)
					|| GRIPPED.getOrDefault(player.getUUID(), 0) > grip(player) + SLIP_TICKS + SERVER_SLACK_TICKS;
				case WALL_RUN -> doing.ticks > 2 && player.onGround() || side == null || !WebWallRun.wallAt(level, player, side, SERVER_REACH);
				case BAR -> player.onGround() || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(doing.pos)) >= BAR_REACH_SQR
					|| Double.isNaN(barBottom(level, doing.pos));
				default -> false;
			};
			if (!player.isAlive() || doing.ticks >= max || lost || !canMove(player)) {
				it.remove();
				if (doing.kind == CLING || doing.kind == BAR || doing.kind == WALL_RUN) {
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
			if (doing.kind == CLING || doing.kind == BAR || doing.kind == WALL_RUN) {
				// holding on isn't falling, but it is work
				player.resetFallDistance();
				player.causeFoodExhaustion(HOLD_EXHAUSTION);
			}
			if (doing.kind == CLING) {
				double climbed = player.getY() - doing.lastY;
				if (climbed > 0.0) {
					player.causeFoodExhaustion((float)(climbed * CLIMB_EXHAUSTION));
				}
				if (doing.ticks == GRIP_TICKS[Mth.clamp(perk(player, Perk.CLIMBER), 1, GRIP_TICKS.length) - 1]) {
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
				GRIPPED.remove(player.getUUID());
			}
			boolean narrow = onNarrowTop(player);
			set(player, Attributes.KNOCKBACK_RESISTANCE, FOOTING_ID, narrow && perk(player, Perk.SURE_FOOTING) > 0 ? FOOTING_KNOCKBACK : 0.0,
				AttributeModifier.Operation.ADD_VALUE);
			set(player, Attributes.MOVEMENT_SPEED, UNSTEADY_ID, narrow && unsteady(player) ? -UNSTEADY_SLOW : 0.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (!GRACE.isEmpty() && server.getTickCount() % 100 == 0) {
			long now = server.overworld().getGameTime();
			GRACE.values().removeIf(until -> until < now);
		}
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
	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
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
