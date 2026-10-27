package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.UltimateFilm;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.68) Andy: "Giants and apes need a grab attack that is telegraphed and fairly slow, but deals massive damage to a
 * single target, with a letterboxed animation". The Great Ape's grab, the twin of the Aether Giant's ({@code
 * kingdoms/remnants/AetherGiant}), in ticks: he rears back with a roar, his right hand drawn up behind him
 * ({@link #WINDUP}; get clear of his reach and it closes on nothing), then the hand comes down on his catch
 * ({@link #SNATCH}), lifts it to his chest and squeezes ({@link #HOLD}, a {@link #SQUEEZE} of his blow every
 * {@link #SQUEEZE_EVERY}), and hurls it into the ground ({@link #SLAM}, {@link #SLAM_DAMAGE} blows' worth, thrown
 * {@link #SLAM_THROW}); then he straightens up ({@link #RECOVER}).
 * <p>
 * A Z Fighter's ape grabs by holding the punch ({@link ApeSmash}) {@link #HOLD_TO_GRAB} ticks: let go and his hand
 * closes on the nearest thing in front of him it can reach, or if there's nothing, the punch lands as usual. The wild
 * apes (a Warrior-born's, a raging villager's, {@link VillagerApe}) grab whatever they're brawling with every
 * {@link #WILD_GRAB_EVERY} or so. His catch, a player, sees the ape's face come down on them (an {@link UltimateFilm}
 * push-in); the ape's own player watches over his shoulder; everyone within {@link #FILM_RANGE} gets the bars.
 */
public final class ApeGrab {
	public static final int WINDUP = 30, SNATCH = 8, HOLD = 40, SLAM = 8, RECOVER = 20;
	public static final int TOTAL = WINDUP + SNATCH + HOLD + SLAM + RECOVER;
	/** How long the catch takes to come up to his chest, and how often it's squeezed (ticks). */
	public static final int LIFT_TICKS = 10;
	static final int SQUEEZE_EVERY = 10;
	/** The punch held this long (ticks) is a grab instead. */
	public static final int HOLD_TO_GRAB = 50;
	/** The wild apes grab this often (ticks, plus up to half again). */
	static final int WILD_GRAB_EVERY = 300;
	/** His hand reaches this much further than his fists ({@link ZFighterLineage#inReach}), and this far up or down. */
	static final double REACH_FACTOR = 1.5, CLIMB = 4.0;
	/** The squeeze: this share of his blow; the slam: this many blows (a player's attack damage, a villager's fist). */
	static final float SQUEEZE = 0.3F, SLAM_DAMAGE = 4.0F;
	static final double SLAM_THROW = 1.2, FILM_RANGE = 48.0;
	static final float SLAM_SHAKE = 1.2F;
	static final double SLAM_SHAKE_RANGE = 40.0;
	/** Where his right hand holds its catch: shares of his width ahead and to the side, of his height up. */
	static final double HAND_AHEAD = 0.55, HAND_SIDE = 0.45, HAND_UP = 0.55;

	/** The grab being played: since when (game time) and whom he holds (entity id, -1: nobody yet, or nothing). Synced to everyone. */
	public record Grab(long start, int held) {
		public static final StreamCodec<ByteBuf, Grab> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_LONG, Grab::start,
			ByteBufCodecs.VAR_INT, Grab::held, Grab::new);
	}

	public static final AttachmentType<Grab> GRAB = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_grab"),
		builder -> builder.syncWith(Grab.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private ApeGrab() {
	}

	/** Whether this ape is in its grab (either side). */
	public static boolean grabbing(Entity ape) {
		return ape.hasAttached(GRAB);
	}

	/** (Client) How far into his grab he is, in ticks ({@link #TOTAL} long), or -1 when he isn't grabbing. */
	public static float phase(Entity ape, float partialTicks) {
		Grab grab = ape.getAttached(GRAB);
		if (grab == null) {
			return -1.0F;
		}
		float t = ape.level().getGameTime() - grab.start() + partialTicks;
		return t >= 0.0F && t < TOTAL ? t : -1.0F;
	}

	/** Whether he could grab {@code e}: alive, no bigger than him, not another ape, not a creative or spectating player. */
	static boolean canGrab(LivingEntity ape, LivingEntity e) {
		return e != ape && e.isAlive() && !e.isRemoved() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
			&& !(e instanceof Player other && (other.isCreative() || other.isSpectator())) && !ape.isPassengerOfSameVehicle(e)
			&& !Boolean.TRUE.equals(e.getAttached(ZFighterLineage.GREAT_APE)) && e.getBbHeight() <= ape.getBbHeight();
	}

	/** Whether {@code prey} stands within his hand's reach: round him, and near his own level. */
	static boolean inHandsReach(LivingEntity ape, ZFighterPower.Live live, LivingEntity prey) {
		double reach = ape.getBbWidth() * 0.5 + ZFighterLineage.BERSERK_REACH * REACH_FACTOR * Math.max(0.25F, live.apeSize);
		double dx = prey.getX() - ape.getX(), dz = prey.getZ() - ape.getZ();
		return dx * dx + dz * dz <= reach * reach && Math.abs(prey.getY() - ape.getY()) <= CLIMB * Math.max(0.5F, live.apeSize);
	}

	/** The nearest thing in front of him his hand can reach (a player's grab), or null. */
	static @Nullable LivingEntity ahead(LivingEntity ape, ZFighterPower.Live live, ServerLevel level) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, ape.getYRot());
		double reach = ape.getBbWidth() * 0.5 + ZFighterLineage.BERSERK_REACH * REACH_FACTOR * Math.max(0.25F, live.apeSize);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, ape.getBoundingBox().inflate(reach, CLIMB, reach), e -> canGrab(ape, e))) {
			Vec3 to = e.position().subtract(ape.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(facing) < 0.3 || !inHandsReach(ape, live, e)) {
				continue;
			}
			double d = e.distanceToSqr(ape);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/**
	 * He begins his grab on {@code target} (a wild ape's quarry; null for a player's, who takes whatever's in front of him):
	 * the roar and the rearing back. False if there's nothing there to grab, or he's already grabbing.
	 */
	static boolean begin(LivingEntity ape, ZFighterPower.Live live, ServerLevel level, @Nullable LivingEntity target, long now) {
		if (grabbing(ape) || ZFighterLineage.crippled(ape)) {
			return false;
		}
		if (target == null) {
			target = ahead(ape, live, level);
		}
		if (target == null || !canGrab(ape, target)) {
			return false;
		}
		ape.setAttached(GRAB, new Grab(now, -1));
		live.grabTarget = target.getId();
		live.grabReadyAt = now + TOTAL + WILD_GRAB_EVERY + ape.getRandom().nextInt(WILD_GRAB_EVERY / 2);
		live.grabSnatchAt = null;
		live.grabSlamAt = null;
		if (ape instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		ape.setDeltaMovement(ape.getDeltaMovement().multiply(0.0, 1.0, 0.0));
		SoundSource source = ape instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
		level.playSound(null, ape.getX(), ape.getEyeY(), ape.getZ(), SoundEvents.WARDEN_ROAR, source, 3.0F, 0.5F);
		level.playSound(null, ape.getX(), ape.getEyeY(), ape.getZ(), SoundEvents.RAVAGER_ROAR, source, 2.5F, 0.45F);
		return true;
	}

	/**
	 * From the ape's tick, every tick: plays his grab if he's in one (true: the caller leaves the rest of his tick be, he
	 * stands where he is), the wind-up, the snatch, the hold and squeeze, the slam, and straightening up.
	 */
	static boolean tick(LivingEntity ape, ZFighterPower.Live live, ServerLevel level, long now) {
		Grab grab = ape.getAttached(GRAB);
		if (grab == null) {
			return false;
		}
		int t = (int)(now - grab.start());
		if (t < 0 || t >= TOTAL || ZFighterLineage.crippled(ape) || !ape.isAlive()) {
			end(ape, live);
			return false;
		}
		if (ape instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		// he stands his ground while he grabs
		ape.setDeltaMovement(ape.getDeltaMovement().multiply(0.0, 1.0, 0.0));
		LivingEntity prey = held(ape, level, grab);
		if (t < WINDUP) {
			// drawn back, watching his target: the warning
			Entity known = live.grabTarget >= 0 ? level.getEntity(live.grabTarget) : null;
			LivingEntity target = known instanceof LivingEntity living ? living : null;
			if (target != null && ape instanceof Mob mob) {
				mob.lookAt(target, 60.0F, 60.0F);
				mob.setYBodyRot(mob.getYHeadRot());
			}
			if (t % 3 == 0) {
				Vec3 hand = hand(ape, 0.0);
				level.sendParticles(new DustParticleOptions(0xC03020, 2.5F), true, false, hand.x, hand.y, hand.z, 6, 0.6, 0.6, 0.6, 0.05);
			}
			if (t == WINDUP - 1) {
				snatch(ape, live, level, grab, target);
			}
			return true;
		}
		if (prey == null) {
			// his hand closed on nothing (or his catch is gone): he straightens up
			if (t < WINDUP + SNATCH + HOLD + SLAM) {
				ape.setAttached(GRAB, new Grab(now - (WINDUP + SNATCH + HOLD + SLAM), -1));
			}
			return true;
		}
		Vec3 from = live.grabSnatchAt != null ? live.grabSnatchAt : prey.position();
		int held = t - WINDUP - SNATCH;
		if (held < 0) {
			// the hand coming down: his catch is pinned where it stood
			carry(ape, prey, from);
			return true;
		}
		if (held < HOLD) {
			// lifted to his chest and squeezed
			double lift = Math.min(1.0, held / (double)LIFT_TICKS);
			lift = lift * lift * (3.0 - 2.0 * lift);
			carry(ape, prey, from.lerp(hand(ape, 0.0), lift));
			if (held > 0 && held % SQUEEZE_EVERY == 0) {
				prey.hurtServer(level, ZFighterLineage.blow(level, ape), fist(ape, live) * SQUEEZE);
				level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.RAVAGER_STEP, source(ape), 2.0F, 0.3F);
				level.sendParticles(ParticleTypes.CRIT, true, false, prey.getX(), prey.getY() + prey.getBbHeight() * 0.5, prey.getZ(), 12, 0.6, 0.6, 0.6, 0.1);
			}
			if (held == HOLD - 1) {
				live.grabSlamAt = groundAhead(ape, level);
			}
			return true;
		}
		int slam = held - HOLD;
		if (slam < SLAM) {
			// hurled down into the ground
			Vec3 to = live.grabSlamAt != null ? live.grabSlamAt : groundAhead(ape, level);
			double drop = Math.min(1.0, (slam + 1) / (double)SLAM);
			carry(ape, prey, hand(ape, 0.0).lerp(to, drop * drop));
			if (slam == SLAM - 1) {
				slam(ape, live, level, grab, prey, to);
			}
			return true;
		}
		// straightening up: his catch is let go
		if (grab.held() >= 0) {
			ape.setAttached(GRAB, new Grab(grab.start(), -1));
		}
		return true;
	}

	/** His hand closes: on his target if it's still within reach of the hand, else on nothing. */
	private static void snatch(LivingEntity ape, ZFighterPower.Live live, ServerLevel level, Grab grab, @Nullable LivingEntity target) {
		if (target == null || !canGrab(ape, target) || !inHandsReach(ape, live, target)) {
			Vec3 miss = hand(ape, ape.getBbWidth() * 0.3);
			level.playSound(null, miss.x, miss.y, miss.z, SoundEvents.RAVAGER_STEP, source(ape), 2.0F, 0.5F);
			level.sendParticles(ParticleTypes.POOF, true, false, miss.x, miss.y - ape.getBbHeight() * HAND_UP + 0.5, miss.z, 20, 1.5, 0.3, 1.5, 0.05);
			return;
		}
		ape.setAttached(GRAB, new Grab(grab.start(), target.getId()));
		live.grabSnatchAt = target.position();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(), source(ape), 1.2F, 0.6F);
		if (target instanceof Player p) {
			p.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ape_grabbed", ape.getDisplayName()).withStyle(ChatFormatting.RED));
		}
		// the film: his catch (a player) sees the ape's face come down on them, the ape's own player watches over his
		// shoulder, and everyone near gets the bars. The catch's own film first, so the bars that follow don't take it over.
		int ticks = SNATCH + HOLD + SLAM;
		if (target instanceof ServerPlayer preyPlayer) {
			UltimateFilm.play(preyPlayer, UltimateFilm.PUSH_IN, ticks, ape.position(), ape, 0.0);
		}
		if (ape instanceof ServerPlayer self) {
			UltimateFilm.play(self, UltimateFilm.PUSH_IN, ticks, target.position(), target, FILM_RANGE);
		} else {
			UltimateFilm.witnessed(ape, UltimateFilm.PUSH_IN, ticks, ape.position(), target, FILM_RANGE);
		}
	}

	/** His catch slammed into the ground at {@code at}: the blow, the ground thrown up, and his catch flung clear. */
	private static void slam(LivingEntity ape, ZFighterPower.Live live, ServerLevel level, Grab grab, LivingEntity prey, Vec3 at) {
		prey.resetFallDistance();
		prey.hurtServer(level, ZFighterLineage.blow(level, ape), fist(ape, live) * SLAM_DAMAGE);
		Vec3 away = Vec3.directionFromRotation(0.0F, ape.getYRot()).scale(SLAM_THROW);
		prey.setDeltaMovement(away.x, 0.5, away.z);
		prey.needsSync = true;
		prey.syncVelocity = true;
		ape.setAttached(GRAB, new Grab(grab.start(), -1));
		ZFighterSenses.impactFrame(level, at);
		BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
		BlockState ground = level.getBlockState(under);
		double dust = Math.max(1.5, ape.getBbWidth() * 0.5);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, false, at.x, at.y + 0.2, at.z, 80, dust, 0.3, dust, 0.3);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y + 0.5, at.z, 2, 1.0, 0.2, 1.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), source(ape), 2.5F, 0.5F);
		com.jaymospowers.core.GroundCracks.crack(level, under, level.getRandom(), Math.max(2, (int)Math.round(dust)));
		ZFighterLineage.shake(ape, at, SLAM_SHAKE_RANGE, SLAM_SHAKE * Math.max(0.5F, live.apeSize));
	}

	/** Whom he holds, if it's still there to hold. */
	private static @Nullable LivingEntity held(LivingEntity ape, ServerLevel level, Grab grab) {
		if (grab.held() < 0) {
			return null;
		}
		Entity e = level.getEntity(grab.held());
		if (e instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) {
			return living;
		}
		ape.setAttached(GRAB, new Grab(grab.start(), -1));
		return null;
	}

	/** His catch carried to {@code to} (its feet), held still and facing him. */
	private static void carry(LivingEntity ape, LivingEntity prey, Vec3 to) {
		if (prey instanceof ServerPlayer player) {
			player.teleportTo((ServerLevel)ape.level(), to.x, to.y, to.z, Relative.ROTATION, 0.0F, 0.0F, false);
		} else {
			prey.setPos(to.x, to.y, to.z);
			Vec3 face = ape.getEyePosition().subtract(prey.getBoundingBox().getCenter());
			float yaw = (float)(Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
			prey.setYRot(yaw);
			prey.setYBodyRot(yaw);
			prey.setYHeadRot(yaw);
		}
		prey.setDeltaMovement(Vec3.ZERO);
		prey.needsSync = true;
		prey.syncVelocity = true;
		prey.resetFallDistance();
	}

	/** Where his right hand holds its catch (the catch's feet), {@code ahead} blocks further forward. */
	private static Vec3 hand(LivingEntity ape, double ahead) {
		float yaw = ape.getYRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		double w = ape.getBbWidth(), forward = w * HAND_AHEAD + ahead, side = w * HAND_SIDE;
		return new Vec3(ape.getX() + fx * forward - fz * side, ape.getY() + ape.getBbHeight() * HAND_UP, ape.getZ() + fz * forward + fx * side);
	}

	/** The ground ahead of him, under his right hand's side, where his catch is slammed. */
	private static Vec3 groundAhead(LivingEntity ape, ServerLevel level) {
		Vec3 over = hand(ape, ape.getBbWidth() * 0.2);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(over.x, 0.0, over.z));
		double y = top.getY();
		// under a roof, or up on something: his own feet's level does
		if (Math.abs(y - ape.getY()) > CLIMB) {
			y = ape.getY();
		}
		return new Vec3(over.x, y, over.z);
	}

	/** One blow of his: a player's attack damage, a villager ape's fist. */
	private static float fist(LivingEntity ape, ZFighterPower.Live live) {
		return ape instanceof Player player ? (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) : VillagerApe.FIST * Math.max(0.25F, live.apeSize);
	}

	private static SoundSource source(LivingEntity ape) {
		return ape instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
	}

	/** The grab is over, or the ape is: whatever he held is let go. */
	static void end(LivingEntity ape, ZFighterPower.Live live) {
		if (ape.hasAttached(GRAB)) {
			ape.removeAttached(GRAB);
		}
		live.grabTarget = -1;
		live.grabSnatchAt = null;
		live.grabSlamAt = null;
	}
}
