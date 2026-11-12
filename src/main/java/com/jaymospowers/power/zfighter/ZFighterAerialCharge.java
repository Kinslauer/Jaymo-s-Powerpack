package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Aerial Charge, a quick technique (G) for a fighter in flight. He flies where he looks at {@link #CHARGE_SPEED} blocks a tick;
 * the first foe in front of him is seized and carried along ahead of him ({@link #CARRY}) until the two hit a wall or the
 * ground, where it takes the impact ({@link #IMPACT_BASE} plus {@link #PER_BLOCK} for every block flown, times Ki Power) and a
 * ring cracks out of the spot. Nothing in the way: he flies the charge out ({@link #MAX_TICKS}) and stops.
 * <p>
 * <b>Power dive</b>: the same press with the look at least {@link #DIVE_PITCH} degrees down is a dive straight down at
 * {@link #DIVE_SPEED}. It lands as a slam whose force grows with the height he fell from ({@link #DIVE_PER_BLOCK} a block, at most
 * {@link #DIVE_CAP}), on the foe he carried down or, if he caught none, on everything round the spot he hits. (The Z Fighter
 * had no ground slam of his own in flight; the Great Ape's smash belongs to the ape.)
 * <p>
 * The flying itself is the client's, as all his flight is: this class tells the owner's client how to steer ({@link #CHARGE},
 * read by {@code ZFighterGammaClient}) and does the grabbing, carrying and landing on the server.
 */
public final class ZFighterAerialCharge {
	static final float KI = 30.0F;
	static final int COOLDOWN = 240;

	static final double CHARGE_SPEED = 1.5, DIVE_SPEED = 2.0;
	static final int MAX_TICKS = 50, DIVE_MAX_TICKS = 80;
	/** The look this far below flat (degrees) makes the press a dive. */
	static final float DIVE_PITCH = 50.0F;
	/** How far ahead of him the carried foe hangs (blocks, centre to centre), and the biggest foe he can carry. */
	static final double CARRY = 1.1, MAX_WIDTH = 3.5, MAX_HEIGHT = 4.0;
	/** How far ahead (beyond a tick's travel) the first foe is looked for, and how wide. */
	static final double GRAB_AHEAD = 1.6, GRAB_WIDTH = 0.7;

	static final float IMPACT_BASE = 8.0F, PER_BLOCK = 0.12F, IMPACT_CAP = 20.0F;
	static final float DIVE_BASE = 8.0F, DIVE_PER_BLOCK = 0.6F, DIVE_CAP = 30.0F;
	/** The most height a dive counts, and the ground slam round the spot when he carried no one (radius base and per block, at most the cap). */
	static final double DIVE_MAX_HEIGHT = 64.0, SLAM_RADIUS = 2.5, SLAM_RADIUS_PER_BLOCK = 0.06, SLAM_RADIUS_CAP = 6.0;
	static final float SLAM_SHARE = 0.6F;
	static final float POISE_WEAR = 25.0F;
	static final float SHAKE = 0.7F, SHAKE_PER_FORCE = 0.02F;
	static final double SHAKE_RANGE = 20.0;

	/** What his client is told while he charges: a dive or a straight charge, and how fast. Not saved; the owner only. */
	public record Run(boolean dive, float speed) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Run> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Run::dive, ByteBufCodecs.FLOAT, Run::speed, Run::new);
	}

	public static final AttachmentType<Run> CHARGE = AttachmentRegistry.create(JaymosPowers.id("zfighter_air_charge"),
		builder -> builder.syncWith(Run.CODEC, AttachmentSyncPredicate.targetOnly()));

	private static final class State {
		final ServerLevel level;
		final boolean dive;
		final double speed;
		final long start;
		final Vec3 from;
		final double height;
		@Nullable UUID foe;

		State(ServerLevel level, boolean dive, double speed, long start, Vec3 from, double height) {
			this.level = level;
			this.dive = dive;
			this.speed = speed;
			this.start = start;
			this.from = from;
			this.height = height;
		}
	}

	private static final Map<UUID, State> RUNS = new HashMap<>();

	private ZFighterAerialCharge() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterAerialCharge::tick);
	}

	static void clear() {
		RUNS.clear();
	}

	/** The way a charge (or a dive) goes. Both sides: the client flies it, the server tests it. */
	public static Vec3 direction(Player player, boolean dive) {
		Vec3 look = player.getLookAngle();
		if (!dive) {
			return look;
		}
		return new Vec3(look.x * 0.4, Math.min(look.y, -0.85), look.z * 0.4).normalize();
	}

	/** G: the charge, or the dive. Returns the cooldown, or 0 if it did not go off. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		if (!live.flying) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_gamma.charge_needs_flight").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (RUNS.containsKey(player.getUUID()) || !ZFighterPower.spend(player, live, KI)) {
			return 0;
		}
		ServerLevel level = player.level();
		boolean dive = player.getXRot() >= DIVE_PITCH;
		double height = 0.0;
		Vec3 at = player.position();
		if (dive) {
			HitResult ground = level.clip(new ClipContext(at, at.add(0.0, -DIVE_MAX_HEIGHT, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			height = ground.getType() == HitResult.Type.MISS ? DIVE_MAX_HEIGHT : at.y - ground.getLocation().y;
		}
		double speed = dive ? DIVE_SPEED : CHARGE_SPEED;
		RUNS.put(player.getUUID(), new State(level, dive, speed, level.getGameTime(), at, height));
		player.setAttached(CHARGE, new Run(dive, (float)speed));
		Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		MagicFx.blast(level, centre, 1.2F, ZFighterPower.kiColor(player));
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.2F, dive ? 0.7F : 1.0F);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.6F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(1.0F);
		}
		return COOLDOWN;
	}

	/** The charge ends (the caller drops it from the table): he keeps no speed, and his client is told. */
	private static void end(ServerPlayer player, State state) {
		player.removeAttached(CHARGE);
		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = true;
		releaseFoe(state);
	}

	private static void releaseFoe(State state) {
		if (state.foe != null && state.level.getEntity(state.foe) instanceof LivingEntity foe) {
			foe.setDeltaMovement(Vec3.ZERO);
			foe.syncVelocity = true;
		}
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, State>> it = RUNS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, State> entry = it.next();
			State state = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level() != state.level || !Powers.has(player, ZFighterPower.INSTANCE)) {
				if (player != null) {
					player.removeAttached(CHARGE);
				}
				releaseFoe(state);
				it.remove();
				continue;
			}
			ZFighterPower.Live live = ZFighterPower.live(player);
			long age = state.level.getGameTime() - state.start;
			if (!live.flying || age > (state.dive ? DIVE_MAX_TICKS : MAX_TICKS) || Powers.silenced(player)) {
				it.remove();
				player.removeAttached(CHARGE);
				releaseFoe(state);
				continue;
			}
			if (step(player, live, state)) {
				it.remove();
			}
		}
	}

	/** One tick; true when it has landed. */
	private static boolean step(ServerPlayer player, ZFighterPower.Live live, State state) {
		ServerLevel level = state.level;
		Vec3 dir = direction(player, state.dive);
		Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		LivingEntity foe = state.foe == null ? null : level.getEntity(state.foe) instanceof LivingEntity living && living.isAlive() ? living : null;
		if (foe == null && state.foe == null) {
			foe = grab(player, state, centre, dir);
		} else if (foe == null) {
			// he lost the one he carried (killed on the way)
			state.foe = null;
		}
		double reach = state.speed + (foe != null ? CARRY + foe.getBbWidth() * 0.5 : 0.0) + 0.4;
		BlockHitResult wall = level.clip(new ClipContext(centre, centre.add(dir.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		int color = ZFighterPower.kiColor(player);
		if (level.getGameTime() % 2 == 0) {
			MagicFx.beam(level, centre.subtract(dir.scale(2.5)), centre, 0.18F, color, 3);
		}
		if (wall.getType() != HitResult.Type.MISS) {
			land(player, live, state, foe, wall);
			return true;
		}
		if (foe != null) {
			carry(player, foe, centre, dir);
		}
		return false;
	}

	/** The first foe in the way is seized, if there is one. */
	private static @Nullable LivingEntity grab(ServerPlayer player, State state, Vec3 centre, Vec3 dir) {
		ServerLevel level = state.level;
		AABB box = player.getBoundingBox().expandTowards(dir.scale(state.speed + GRAB_AHEAD)).inflate(GRAB_WIDTH);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, box, e -> ZFighterArts.valid(player, e))) {
			if (near.getBbWidth() > MAX_WIDTH || near.getBbHeight() > MAX_HEIGHT) {
				continue;
			}
			double distance = near.getBoundingBox().getCenter().distanceToSqr(centre);
			if (distance < bestDistance) {
				best = near;
				bestDistance = distance;
			}
		}
		if (best != null) {
			state.foe = best.getUUID();
			Vec3 at = best.getBoundingBox().getCenter();
			MagicFx.blast(level, at, 1.0F + best.getBbWidth() * 0.5F, ZFighterPower.kiColor(player), 8);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.7F);
			if (best instanceof Mob mob) {
				mob.setTarget(null);
			}
		}
		return best;
	}

	/** The foe is kept ahead of him, as the grab keeps its one: a player's motion is their own client's, so it is steered. */
	private static void carry(ServerPlayer player, LivingEntity foe, Vec3 centre, Vec3 dir) {
		Vec3 want = centre.add(dir.scale(CARRY + foe.getBbWidth() * 0.5 + 0.5));
		if (foe instanceof ServerPlayer) {
			Vec3 v = want.subtract(foe.getBoundingBox().getCenter());
			double speed = v.length();
			double cap = ZFighterAerialCharge.DIVE_SPEED * 1.4;
			foe.setDeltaMovement(speed > cap ? v.scale(cap / speed) : v);
		} else {
			foe.setPos(want.x, want.y - foe.getBbHeight() * 0.5, want.z);
			foe.setDeltaMovement(Vec3.ZERO);
		}
		foe.syncVelocity = true;
		foe.needsSync = true;
		foe.resetFallDistance();
		if (foe instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		}
	}

	/** The wall or ground is reached: the impact, and the ring cracking out of it. */
	private static void land(ServerPlayer player, ZFighterPower.Live live, State state, @Nullable LivingEntity foe, BlockHitResult wall) {
		ServerLevel level = state.level;
		float power = ZFighterPower.kiPower(player, live);
		double travelled = player.position().distanceTo(state.from);
		float force = state.dive
			? Math.min(DIVE_CAP, DIVE_BASE + (float)state.height * DIVE_PER_BLOCK)
			: Math.min(IMPACT_CAP, IMPACT_BASE + (float)travelled * PER_BLOCK);
		Vec3 at = wall.getLocation();
		Vec3 normal = Vec3.atLowerCornerOf(wall.getDirection().getUnitVec3i());
		var source = ZFighterKi.source(player);
		int color = ZFighterPower.kiColor(player);
		if (foe != null) {
			foe.setInvulnerableTime(0);
			foe.hurtServer(level, source, force * power);
			ZFighterPoise.wear(foe, POISE_WEAR);
			foe.setDeltaMovement(normal.scale(0.5).add(0.0, 0.2, 0.0));
			foe.syncVelocity = true;
		} else if (state.dive) {
			// no one caught on the way down: the slam is round the spot
			double radius = Math.min(SLAM_RADIUS_CAP, SLAM_RADIUS + state.height * SLAM_RADIUS_PER_BLOCK);
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> ZFighterArts.valid(player, e))) {
				near.setInvulnerableTime(0);
				near.hurtServer(level, source, force * SLAM_SHARE * power);
				ZFighterKi.push(near, near.position().subtract(at), 0.9, 0.5);
				ZFighterPoise.wear(near, POISE_WEAR * 0.5F);
			}
		}
		float size = 1.5F + force * 0.12F;
		MagicFx.ring(level, at.add(normal.scale(0.05)), normal, size, color, 16);
		MagicFx.ring(level, at.add(normal.scale(0.05)), normal, size * 0.55F, 0xFFFFFF, 10);
		MagicFx.blast(level, at.add(normal.scale(0.4)), size * 0.6F, color);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
		Shakes.around(level, at, SHAKE_RANGE, Mth.clamp(SHAKE + force * SHAKE_PER_FORCE, 0.0F, 1.5F));
		end(player, state);
	}
}
