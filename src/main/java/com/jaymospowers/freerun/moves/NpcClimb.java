package com.jaymospowers.freerun.moves;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * NPCs climb as players do. Andy (2026-11-22): "NPCs should have all the same capabilities as the player, meaning they
 * should be able to climb up one and two block heights". A player gets up a one-block step with a jump and up a wall about
 * two blocks high with the ledge pull-up ({@link Ledge}), and over a fence or wall with the crouch grab. Every mob built
 * like a person ({@link BodyPlan#HUMANOID}: villagers, guards, illagers, zombies, skeletons, piglins; Andy: "all humanoid
 * mobs") now does the same:
 * <ul>
 * <li><b>Planning a way</b>: their pathfinding takes edges up to {@link #CLIMB_HEIGHT} over their feet, not vanilla's one
 * block and a bit, and goes over fences and walls (Andy: "always"), so a 2-high wall no longer keeps a zombie out: only a
 * 3-high one does ({@code WalkNodeClimbMixin}, {@code NodeEvaluatorFenceMixin}).</li>
 * <li><b>The pull-up</b>: where the next step of the way is higher than a jump reaches ({@link #JUMP_REACH}), the body
 * goes up its own side of the wall and over onto the top in {@link Ledge#CLIMB_TICKS}, the player's mantle, instead of
 * jumping at it ({@code MoveControlClimbMixin}). One step or less is still the plain jump.</li>
 * <li><b>Limbs</b> (Andy's pick): a broken or lost arm or leg means no pull-up and no way planned up a wall (the jump
 * still works), and nor while downed. Babies are too small to reach the edge.</li>
 * </ul>
 * The climb is the server's (a mob's movement is); {@link #PULL} is synced so everyone sees the pose, the player's mantle
 * clip on the body ({@code client/NpcClimbPose}).
 */
public final class NpcClimb {
	/** The highest edge over their feet a climber plans a way up (blocks): two blocks and a little give, as the player's catch. */
	public static final double CLIMB_HEIGHT = 2.05;
	/** A step up more than this (blocks) is the pull-up; this or less, the plain jump (vanilla's own jump height, 1.125, and a little). */
	public static final double JUMP_REACH = 1.15;
	/** The way's next step must be within this far across (blocks) to start the pull-up toward it. */
	public static final double REACH = 1.5;
	/** Ticks after a climb before another can start (so one blocked one doesn't stutter). */
	public static final int COOLDOWN_TICKS = 10;

	/** On a mob pulling itself up over an edge (synced, for the pose): the wall's side from it, the start, the length (ticks). */
	public record Pull(int face, long start, int ticks) {
		public static final StreamCodec<ByteBuf, Pull> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Pull::face,
			ByteBufCodecs.VAR_LONG, Pull::start,
			ByteBufCodecs.VAR_INT, Pull::ticks,
			Pull::new);

		public Direction direction() {
			return Direction.from2DDataValue(this.face);
		}
	}

	public static final AttachmentType<Pull> PULL = AttachmentRegistry.create(JaymosPowers.id("npc_climb"),
		builder -> builder.syncWith(Pull.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** The climbs under way (server): where each one started and where it ends. */
	private record Path(Vec3 from, Vec3 to, long start, int ticks) {
	}

	private static final Map<Mob, Path> CLIMBING = new IdentityHashMap<>();
	private static final Map<Mob, Long> COOLDOWN = new IdentityHashMap<>();

	private NpcClimb() {
	}

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(NpcClimb::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CLIMBING.clear();
			COOLDOWN.clear();
		});
	}

	/** Whether {@code mob} climbs as a player does (built like a person, grown, up, every arm and leg whole). */
	public static boolean climber(Mob mob) {
		if (mob instanceof Player || mob.isBaby() || BodyPlan.of(mob) != BodyPlan.HUMANOID || mob.isPassenger() || Downed.isDowned(mob)) {
			return false;
		}
		if ((Dismember.missing(mob) & (Dismember.RIGHT_ARM | Dismember.LEFT_ARM | Dismember.RIGHT_LEG | Dismember.LEFT_LEG)) != 0) {
			return false;
		}
		return !Breaks.isBroken(mob, BodyPart.LEFT_ARM) && !Breaks.isBroken(mob, BodyPart.RIGHT_ARM)
			&& !Breaks.isBroken(mob, BodyPart.LEFT_LEG) && !Breaks.isBroken(mob, BodyPart.RIGHT_LEG);
	}

	/** Mid-climb (the server's own; the move control leaves it be). */
	public static boolean climbing(Mob mob) {
		return CLIMBING.containsKey(mob);
	}

	/**
	 * The move control is about to jump {@code mob} toward the way's next step at {@code x, y, z} (y its floor): if that's
	 * higher than a jump reaches and there's room, the pull-up starts instead. True if it did.
	 */
	public static boolean tryStart(Mob mob, double x, double y, double z) {
		if (!(mob.level() instanceof ServerLevel level) || CLIMBING.containsKey(mob) || !mob.onGround()) {
			return false;
		}
		double rise = y - mob.getY();
		double dx = x - mob.getX(), dz = z - mob.getZ();
		if (rise <= JUMP_REACH || rise > CLIMB_HEIGHT || dx * dx + dz * dz > REACH * REACH || !climber(mob)) {
			return false;
		}
		Long until = COOLDOWN.get(mob);
		long now = level.getGameTime();
		if (until != null && until > now) {
			return false;
		}
		// room to rise up its own side of the wall, and to stand on the top
		AABB box = mob.getBoundingBox();
		Vec3 from = mob.position();
		Vec3 to = new Vec3(x, y + 0.01, z);
		if (!level.noCollision(mob, box.move(0.0, to.y - from.y, 0.0)) || !level.noCollision(mob, box.move(to.subtract(from)))) {
			return false;
		}
		Direction face = Direction.getApproximateNearest(dx, 0.0, dz);
		int ticks = Ledge.CLIMB_TICKS;
		CLIMBING.put(mob, new Path(from, to, now, ticks));
		mob.setAttached(PULL, new Pull(face.get2DDataValue(), now, ticks));
		mob.resetFallDistance();
		sound(level, mob, BlockPos.containing(x, y - 0.5, z), 0.8F);
		return true;
	}

	private static void tick(ServerLevel level) {
		if (CLIMBING.isEmpty()) {
			if (!COOLDOWN.isEmpty()) {
				COOLDOWN.values().removeIf(until -> until <= level.getGameTime());
			}
			return;
		}
		long now = level.getGameTime();
		Iterator<Map.Entry<Mob, Path>> it = CLIMBING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Mob, Path> entry = it.next();
			Mob mob = entry.getKey();
			if (mob.level() != level) {
				continue;
			}
			Path path = entry.getValue();
			float p = (now - path.start()) / (float)path.ticks();
			if (mob.isRemoved() || !mob.isAlive() || !climber(mob) || p >= 1.0F) {
				// done, or let go (dead, downed, a limb broken on the way): the body drops from where it is
				it.remove();
				mob.removeAttached(PULL);
				COOLDOWN.put(mob, now + COOLDOWN_TICKS);
				if (p >= 1.0F && mob.isAlive()) {
					mob.setPos(path.to());
					sound(level, mob, BlockPos.containing(path.to().x, path.to().y - 0.5, path.to().z), 0.6F);
				}
				mob.resetFallDistance();
				continue;
			}
			// up its own side of the wall first (the player's mantle split), then forward onto the top
			float rise = smooth(p / Ledge.CLIMB_RISE_SHARE);
			float over = smooth((p - Ledge.CLIMB_RISE_SHARE) / (1.0F - Ledge.CLIMB_RISE_SHARE));
			Vec3 from = path.from(), to = path.to();
			mob.setPos(Mth.lerp(over, from.x, to.x), Mth.lerp(rise, from.y, to.y), Mth.lerp(over, from.z, to.z));
			mob.setDeltaMovement(Vec3.ZERO);
			mob.resetFallDistance();
			Pull pull = mob.getAttached(PULL);
			if (pull != null) {
				float yRot = pull.direction().toYRot();
				mob.setYRot(yRot);
				mob.setYBodyRot(yRot);
			}
		}
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static void sound(ServerLevel level, Mob mob, BlockPos pos, float pitch) {
		SoundType sound = level.getBlockState(pos).getSoundType();
		level.playSound(null, mob.getX(), mob.getY() + 1.6, mob.getZ(), sound.getHitSound(), SoundSource.HOSTILE,
			sound.getVolume() * 0.7F, sound.getPitch() * pitch);
	}
}
