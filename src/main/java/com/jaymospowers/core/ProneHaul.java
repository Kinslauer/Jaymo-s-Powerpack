package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.web.KitchenCompat;
import io.netty.buffer.ByteBuf;
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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The prone haul up a block, for every player lying on their belly (by the Crawl key, the stance wheel, broken legs or
 * downed). Andy (2026-11-12): "the one block climb while prone should be activated by holding right click and animating a
 * struggle up the block, longer and differently animated if you only have one hand. if you let go or are interuppted during
 * the struggle then you ragdoll. there is a third faster animation if you are perfectly healthy."
 *
 * <p>Lying face down against a rise {@link #RISE_MIN} to {@link #RISE_MAX} high (a full block, give or take) with room to
 * lie on top of it ({@link #find}), hold Use: the hands reach up onto the top, the body is dragged up the face of it with
 * the legs scrabbling, and you slide in on your belly on top. How it goes is the {@link #style}: quick and clean when
 * perfectly healthy ({@link #HEALTHY}: full health, nothing broken or lost, not downed), a hard pull otherwise
 * ({@link #NORMAL}), and with only one working arm ({@link #ONE_HAND}: the other broken or gone) a long one-armed heave that
 * slips back once before it goes over. No working arm, no haul. Downed it all takes {@link #DOWNED_SLOW} longer.
 * Let go of Use part way, or take a hit, and the grip goes: you ragdoll back off the face ({@link Carry#THROWN}, Kitchen's
 * limp body when it's there).
 *
 * <p>This replaces the crawl's old automatic step up a full block: flat on the ground the step is vanilla's again, so a
 * block is only climbed by the haul. The moves are the client's ({@code client/ProneHaulClient}, as the ledge climb's are):
 * it finds the rise, walks the body up the path ({@link #at}) and says when it's let go or over. The server checks it,
 * syncs {@link #HAUL} so everyone draws the struggle ({@code client/ProneHaulPose}), plays the sounds, ragdolls a let go or
 * an interrupted haul, and doesn't kick the player for the rise ({@link #relaxesMovement}).
 */
public final class ProneHaul {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** The rise ahead must top out this far over the feet (blocks): over a step (vanilla walks those) up to a block and a bit. */
	public static final double RISE_MIN = 0.55, RISE_MAX = 1.1;
	/** The rise's face may be at most this far ahead of the body (blocks). */
	public static final double REACH = 0.35;
	/** Facing within this many degrees of straight at the rise. */
	public static final float FACING_DEGREES = 50.0F;
	/** Where the haul ends: the middle of the body this far past the edge (blocks), or {@link #IN_SHORT} when the top is shallow. */
	public static final double IN = 0.6, IN_SHORT = 0.35;
	/** Lying on the top this far over it (blocks). */
	public static final double LIFT = 0.01;
	/** How long each haul takes (ticks): perfectly healthy, the usual, one-armed. */
	public static final int HEALTHY_TICKS = 16, NORMAL_TICKS = 32, ONE_HAND_TICKS = 56;
	/** Downed, a haul takes this many times as long. */
	public static final float DOWNED_SLOW = 1.5F;
	/** Hunger each haul costs, by style (exhaustion: 4 is one hunger point). */
	public static final float[] EXHAUSTION = {0.15F, 0.3F, 0.5F};
	/**
	 * Each style's timeline, as shares of the haul: the reach up onto the top ends at the first, the pull up the face at
	 * the second, and the rest is the slide in on the belly.
	 */
	static final float[][] PHASES = {{0.18F, 0.62F}, {0.3F, 0.72F}, {0.28F, 0.8F}};
	/** One-armed, the pull gets this far up (share of the rise), slips back to the second, and then goes over. */
	static final float SLIP_HIGH = 0.6F, SLIP_LOW = 0.38F;
	/** Let go or knocked off: pushed back off the face this fast (blocks/tick) and up this much, limp. */
	public static final double FALL_PUSH = 0.18, FALL_LIFT = 0.08;
	/** The scrape of the body on the face every this many ticks of the pull. */
	static final int SCRAPE_EVERY = 7;
	/** Server: a haul not said to be over is ended this long after it should have (ticks). */
	static final int SLACK_TICKS = 15;
	/** After a haul, the server goes easy on the player's movement this long (ticks). */
	static final int GRACE_TICKS = 20;
	/** The client waits this long for the server to take the haul up before giving up on it (ticks). */
	public static final int CONFIRM_TICKS = 10;
	// ================================================================================================================

	/** The styles: perfectly healthy, the usual, one working arm. */
	public static final int HEALTHY = 0, NORMAL = 1, ONE_HAND = 2;

	/**
	 * A haul under way, for everyone's client: the rise's side ({@code face}, a horizontal direction's 2D value), when it
	 * began (game time), its {@link #style} and length ({@code ticks}), where the body started ({@code from}) and ends
	 * ({@code to}), and how far the face of the rise was from the body's middle ({@code wall}, blocks).
	 */
	public record Haul(int face, long start, int style, int ticks, Vec3 from, Vec3 to, float wall) {
		public static final StreamCodec<ByteBuf, Haul> STREAM_CODEC = StreamCodec.of((buf, haul) -> {
			buf.writeByte(haul.face);
			buf.writeLong(haul.start);
			buf.writeByte(haul.style);
			buf.writeShort(haul.ticks);
			writeVec(buf, haul.from);
			writeVec(buf, haul.to);
			buf.writeFloat(haul.wall);
		}, buf -> new Haul(buf.readByte(), buf.readLong(), buf.readByte(), buf.readShort(), readVec(buf), readVec(buf), buf.readFloat()));

		public Direction direction() {
			return Direction.from2DDataValue(this.face);
		}

		/** How far through it is (0 to 1) at {@code gameTime + partialTick}. */
		public float progress(long gameTime, float partialTick) {
			return Mth.clamp((gameTime - this.start + partialTick) / Math.max(1, this.ticks), 0.0F, 1.0F);
		}
	}

	private static void writeVec(ByteBuf buf, Vec3 v) {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}

	private static Vec3 readVec(ByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	/** On a player hauling themselves up a block (synced to everyone, for the struggle). */
	public static final AttachmentType<Haul> HAUL = AttachmentRegistry.create(JaymosPowers.id("prone_haul"),
		builder -> builder.syncWith(Haul.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * A rise found ahead: its block ({@code pos}), its side from the player ({@code face}), its top ({@code top}), where the
	 * body starts ({@code from}) and ends on it ({@code to}), and the face's distance from the body's middle ({@code wall}).
	 */
	public record Spot(BlockPos pos, Direction face, double top, Vec3 from, Vec3 to, double wall) {
	}

	/** Client → server: began hauling up the block at {@code pos}, on its side {@code face}, from {@code from} to {@code to}. */
	public record Start(BlockPos pos, int face, Vec3 from, Vec3 to, float wall) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(JaymosPowers.id("prone_haul_start"));
		public static final StreamCodec<ByteBuf, Start> CODEC = StreamCodec.of((buf, start) -> {
			BlockPos.STREAM_CODEC.encode(buf, start.pos);
			buf.writeByte(start.face);
			writeVec(buf, start.from);
			writeVec(buf, start.to);
			buf.writeFloat(start.wall);
		}, buf -> new Start(BlockPos.STREAM_CODEC.decode(buf), buf.readByte(), readVec(buf), readVec(buf), buf.readFloat()));

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	/** Client → server: the haul is over ({@code done}: on top), or Use was let go part way (the grip goes). */
	public record Stop(boolean done) implements CustomPacketPayload {
		public static final Type<Stop> TYPE = new Type<>(JaymosPowers.id("prone_haul_stop"));
		public static final StreamCodec<ByteBuf, Stop> CODEC = ByteBufCodecs.BOOL.map(Stop::new, Stop::done);

		@Override
		public Type<Stop> type() {
			return TYPE;
		}
	}

	/** Server: who's hauling, and the block they're hauling up. */
	private static final Map<UUID, BlockPos> HAULING = new HashMap<>();
	/** Server: until when (game time) a player just done hauling isn't kicked for how they moved. */
	private static final Map<UUID, Long> GRACE = new HashMap<>();

	private ProneHaul() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Start.TYPE, Start.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Stop.TYPE, Stop.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Start.TYPE, (payload, ctx) -> start(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Stop.TYPE, (payload, ctx) -> stop(ctx.player(), payload.done()));
		ServerTickEvents.END_SERVER_TICK.register(ProneHaul::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && !blocked && HAULING.containsKey(player.getUUID())) {
				// a hit mid-struggle: the grip goes
				stop(player, false);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HAULING.clear();
			GRACE.clear();
		});
	}

	// ---- queries (both sides)

	/**
	 * How this player would haul themselves up right now: {@link #HEALTHY}, {@link #NORMAL} or {@link #ONE_HAND}, or -1
	 * with no working arm to pull with.
	 */
	public static int style(Player player) {
		int arms = Breaks.workingArms(player);
		if (arms <= 0) {
			return -1;
		}
		if (arms == 1) {
			return ONE_HAND;
		}
		boolean healthy = player.getHealth() >= player.getMaxHealth() - 0.01F && Breaks.broken(player) == 0
			&& Dismember.missing(player) == 0 && !Downed.isDowned(player);
		return healthy ? HEALTHY : NORMAL;
	}

	/** How long a haul in {@code style} takes this player (ticks). */
	public static int ticks(Player player, int style) {
		int base = style == HEALTHY ? HEALTHY_TICKS : style == ONE_HAND ? ONE_HAND_TICKS : NORMAL_TICKS;
		return Downed.isDowned(player) ? Math.round(base * DOWNED_SLOW) : base;
	}

	/** How far up the face the body is (0 to 1) at {@code p} through a haul in {@code style}: one-armed, it slips back once. */
	public static float rise(int style, float p) {
		float[] phase = PHASES[Mth.clamp(style, 0, 2)];
		float s = Mth.clamp((p - phase[0]) / (phase[1] - phase[0]), 0.0F, 1.0F);
		if (style != ONE_HAND) {
			return smooth(s);
		}
		if (s < 0.45F) {
			return SLIP_HIGH * smooth(s / 0.45F);
		}
		if (s < 0.6F) {
			return Mth.lerp(smooth((s - 0.45F) / 0.15F), SLIP_HIGH, SLIP_LOW);
		}
		return Mth.lerp(smooth((s - 0.6F) / 0.4F), SLIP_LOW, 1.0F);
	}

	/** How far across onto the top the body has slid (0 to 1) at {@code p} through a haul in {@code style}. */
	public static float across(int style, float p) {
		float end = PHASES[Mth.clamp(style, 0, 2)][1];
		return smooth((p - end) / (1.0F - end));
	}

	/** Where the pull up the face ends in a haul in {@code style} (share of it): the slide in on the belly is the rest. */
	public static float pullEnd(int style) {
		return PHASES[Mth.clamp(style, 0, 2)][1];
	}

	/** How far into the reach up onto the top (0 to 1) at {@code p} through a haul in {@code style}. */
	public static float reach(int style, float p) {
		return smooth(p / PHASES[Mth.clamp(style, 0, 2)][0]);
	}

	/** Where the body is at {@code p} through a haul in {@code style} from {@code from} to {@code to}: straight up the face, then in. */
	public static Vec3 at(int style, float p, Vec3 from, Vec3 to) {
		float up = rise(style, p), in = across(style, p);
		return new Vec3(Mth.lerp(in, from.x, to.x), Mth.lerp(up, from.y, to.y), Mth.lerp(in, from.z, to.z));
	}

	private static float smooth(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/**
	 * The rise straight ahead of a player lying flat that they could haul themselves up, or null: a block whose top is
	 * {@link #RISE_MIN} to {@link #RISE_MAX} over the feet, its face within {@link #REACH} of the body, not one the ledge
	 * climb keeps off ({@link Ledge#NO_VAULT}: cactus, magma...), room straight up beside it for the flat body to the top,
	 * and room to lie on the top with something solid under the middle.
	 */
	public static @Nullable Spot find(Player player) {
		Direction face = Direction.fromYRot(player.getYRot());
		if (Math.abs(Mth.wrapDegrees(player.getYRot() - face.toYRot())) > FACING_DEGREES) {
			return null;
		}
		Level level = player.level();
		double half = player.getBbWidth() * 0.5;
		double feet = player.getY();
		double aheadX = player.getX() + face.getStepX() * (half + REACH + 0.01);
		double aheadZ = player.getZ() + face.getStepZ() * (half + REACH + 0.01);
		CollisionContext context = CollisionContext.of(player);
		for (int y = Mth.floor(feet + RISE_MAX); y >= Mth.floor(feet); y--) {
			BlockPos pos = BlockPos.containing(aheadX, y, aheadZ);
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(level, pos, context);
			if (shape.isEmpty()) {
				continue;
			}
			double top = pos.getY() + shape.max(Direction.Axis.Y);
			if (top > feet + RISE_MAX || top < feet + RISE_MIN || state.is(Ledge.NO_VAULT)) {
				return null;
			}
			int sign = face.getAxisDirection().getStep();
			Direction.Axis axis = face.getAxis();
			int base = axis == Direction.Axis.X ? pos.getX() : pos.getZ();
			double wall = base + (sign > 0 ? shape.min(axis) : shape.max(axis));
			double at = axis == Direction.Axis.X ? player.getX() : player.getZ();
			double gap = (wall - at) * sign - half;
			if (gap < -0.05 || gap > REACH) {
				return null;
			}
			Vec3 from = player.position();
			AABB box = player.getBoundingBox().deflate(1.0E-7);
			double up = top + LIFT - feet;
			// straight up beside the face to over the top, then in across it, flat
			if (!level.noCollision(player, box.expandTowards(0.0, up, 0.0))) {
				return null;
			}
			AABB raised = box.move(0.0, up, 0.0);
			for (double in : new double[] {IN, IN_SHORT}) {
				Vec3 to = axis == Direction.Axis.X ? new Vec3(wall + sign * in, top + LIFT, from.z) : new Vec3(from.x, top + LIFT, wall + sign * in);
				if (level.noCollision(player, raised.expandTowards(to.x - from.x, 0.0, to.z - from.z)) && solidUnder(level, to, top)) {
					return new Spot(pos.immutable(), face, top, from, to, (wall - at) * sign);
				}
			}
			return null;
		}
		return null;
	}

	/** The top really holds the body up at {@code at} (not a sliver of a block under the middle). */
	private static boolean solidUnder(Level level, Vec3 at, double top) {
		BlockPos under = BlockPos.containing(at.x, top - 0.01, at.z);
		return !level.getBlockState(under).getCollisionShape(level, under).isEmpty();
	}

	/** Whether this player may begin a haul (lying on their belly, awake, not carried or thrown): both sides. */
	public static boolean canHaul(Player player) {
		return player.isAlive() && Crawl.crawling(player) && Crawl.canCrawl(player) && !Crawl.supine(player)
			&& !player.isInLava() && !Downed.passedOut(player) && !Carry.isCarried(player) && !Carry.isCarrying(player)
			&& !Carry.isThrown(player) && !KitchenCompat.isLimp(player) && style(player) >= 0;
	}

	/** Server: a player hauling up (or just done) moves however the haul puts them, without being kicked. */
	public static boolean relaxesMovement(ServerPlayer player) {
		if (HAULING.containsKey(player.getUUID())) {
			return true;
		}
		Long until = GRACE.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	// ---- server

	private static void start(ServerPlayer player, Start start) {
		ServerLevel level = player.level();
		BlockPos pos = start.pos();
		if (HAULING.containsKey(player.getUUID()) || start.face() < 0 || start.face() > 3 || !canHaul(player)
			|| player.position().distanceToSqr(Vec3.atCenterOf(pos)) > 6.25 || player.position().distanceToSqr(start.from()) > 1.0
			|| start.from().distanceToSqr(start.to()) > 4.0 || level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
			return;
		}
		int style = style(player);
		HAULING.put(player.getUUID(), pos.immutable());
		player.setAttached(HAUL, new Haul(start.face(), level.getGameTime(), style, ticks(player, style), start.from(), start.to(),
			start.wall()));
		player.resetFallDistance();
		// the hands slapping onto the top, the clothes dragging on the face
		sound(level, player, pos, 0.7F, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS,
			0.5F, 0.8F);
	}

	private static void stop(ServerPlayer player, boolean done) {
		BlockPos pos = HAULING.remove(player.getUUID());
		Haul haul = player.getAttached(HAUL);
		player.removeAttached(HAUL);
		if (pos == null || haul == null) {
			return;
		}
		ServerLevel level = player.level();
		GRACE.put(player.getUUID(), level.getGameTime() + GRACE_TICKS);
		player.resetFallDistance();
		if (done) {
			player.causeFoodExhaustion(EXHAUSTION[Mth.clamp(haul.style(), 0, 2)]);
			sound(level, player, pos, 0.6F, 1.0F);
			return;
		}
		// the grip goes: limp, back off the face
		Direction face = haul.direction();
		player.setAttached(Carry.THROWN, level.getGameTime());
		KitchenCompat.knockLimp(player, -face.getStepX() * FALL_PUSH, FALL_LIFT, -face.getStepZ() * FALL_PUSH);
		SoundType sound = level.getBlockState(pos).getSoundType();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound.getFallSound(), SoundSource.PLAYERS,
			sound.getVolume() * 0.8F, sound.getPitch() * 0.8F);
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, BlockPos>> it = HAULING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, BlockPos> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Haul haul = player == null ? null : player.getAttached(HAUL);
			if (player == null || haul == null) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long age = level.getGameTime() - haul.start();
			if (age > haul.ticks() + SLACK_TICKS || !player.isAlive() || !player.hasAttached(Crawl.CRAWLING) || !Crawl.canCrawl(player)
				|| Carry.isCarried(player) || Downed.passedOut(player)) {
				// over (unreported), or taken out of it some other way than a hit
				it.remove();
				GRACE.put(player.getUUID(), level.getGameTime() + GRACE_TICKS);
				player.removeAttached(HAUL);
				continue;
			}
			player.resetFallDistance();
			float p = haul.progress(level.getGameTime(), 0.0F);
			float[] phase = PHASES[Mth.clamp(haul.style(), 0, 2)];
			if (age > 0 && age % SCRAPE_EVERY == 0 && p > phase[0] && p < phase[1]) {
				// scrabbling up the face
				sound(level, player, entry.getValue(), 0.35F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			}
		}
		if (!GRACE.isEmpty() && server.getTickCount() % 100 == 0) {
			long now = server.overworld().getGameTime();
			GRACE.values().removeIf(until -> until < now);
		}
	}

	/** The rise's own sound, as the hands take it or the body scrapes on it. */
	private static void sound(ServerLevel level, ServerPlayer player, BlockPos pos, float volume, float pitch) {
		SoundType sound = level.getBlockState(pos).getSoundType();
		level.playSound(null, player.getX(), player.getY() + 0.5, player.getZ(), sound.getHitSound(), SoundSource.PLAYERS,
			sound.getVolume() * volume, sound.getPitch() * pitch);
	}
}
