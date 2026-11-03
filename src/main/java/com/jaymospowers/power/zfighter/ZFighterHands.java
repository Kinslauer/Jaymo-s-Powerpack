package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.19.60) Andy: "the ki blasts from z fighters should originate from their hands or fingers realistically ... the ki wave
 * shouldnt just materialize in front of them, it should form in the hands, and extend forward when they release,
 * animating a push and exclamation, same with the rapid ki blasts, every ball should be formed in hand, and thrown
 * forward with a thrust".
 * <p>
 * What a fighter's hands are doing, for everyone's client ({@code ZFighterHandsPose} draws it, third and first person, and
 * pins the gathering orbs and the beam's root to the drawn hands): the held pose ({@link #POSE}: cupped at the lead hip
 * for a wave, one finger pointed for the Frost Needle, both palms out for the barrage) and each throw ({@link #thrust}:
 * one arm or both driven forward). Players and NPCs (gifted villagers, rogue fighters) alike: it goes by entity.
 * The server's own hand spots here are where the ki is made and leaves; the client draws them where the model's hands are.
 */
public final class ZFighterHands {
	/** Held poses. */
	public static final int NONE = 0, CUP = 1, POINT = 2, PALMS = 3, VOLLEY = 4, ART = 5;
	/** Which hand a throw is from (the right, the left, both). */
	public static final int RIGHT = 0, LEFT = 1, BOTH = 2;
	/** Throws: a tapped blast (drawn back, then thrown), a barrage jab, both hands pushed out (a bomb, an NPC's wave). */
	public static final int THROW = 0, JAB = 1, PUSH = 2;
	/** A tapped Ki Blast forms in the palm this many ticks, the arm drawn back, before it's thrown. */
	public static final int THROW_COCK = 3;

	/** The cupped hands at the lead hip: up from the feet, out to the lead side, ahead (shares of height / blocks). */
	static final double CUP_UP = 0.5, CUP_SIDE = 0.2, CUP_AHEAD = 0.3;
	/** A thrown palm: ahead of the eyes, to its side, down. */
	static final double PALM_AHEAD = 0.75, PALM_SIDE = 0.3, PALM_DOWN = 0.28;
	/** The shout on letting a wave go: how loud, how high (one in this many fighters' voices is pitched lower). */
	static final float SHOUT_VOLUME = 1.4F, SHOUT_PITCH = 1.05F, SHOUT_LOW = 0.85F;
	/** Players this near see a throw. */
	static final double SEEN = 96.0;

	/** The held pose, for everyone's client; absent at {@link #NONE}. Not saved. */
	public static final AttachmentType<Integer> POSE = AttachmentRegistry.create(JaymosPowers.id("zfighter_hands"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Something to let go once the hand has had its moment (a tapped blast after it formed). */
	private record Later(ServerLevel level, int owner, long at, Runnable then) {
	}

	private static final List<Later> LATER = new ArrayList<>();

	private ZFighterHands() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterHands::tick);
	}

	static void clear() {
		LATER.clear();
	}

	private static void tick(MinecraftServer server) {
		if (LATER.isEmpty()) {
			return;
		}
		List<Later> due = new ArrayList<>();
		for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
			Later later = it.next();
			if (later.level.getGameTime() >= later.at) {
				it.remove();
				due.add(later);
			}
		}
		for (Later later : due) {
			if (later.level.getEntity(later.owner) instanceof LivingEntity owner && owner.isAlive()) {
				later.then.run();
			}
		}
	}

	/** Runs {@code then} in {@code ticks} ticks, if {@code owner} is still alive then. */
	static void later(LivingEntity owner, int ticks, Runnable then) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		LATER.add(new Later(level, owner.getId(), level.getGameTime() + ticks, then));
	}

	/** Sets the held pose (only sent when it changes). */
	public static void pose(LivingEntity entity, int pose) {
		Integer now = entity.getAttached(POSE);
		if (pose == NONE) {
			if (now != null) {
				entity.removeAttached(POSE);
			}
		} else if (now == null || now != pose) {
			entity.setAttached(POSE, pose);
		}
	}

	/** Every tick, after the wave and barrage: what a player's hands are holding. */
	static void sync(ServerPlayer player, ZFighterPower.Live live) {
		int pose = NONE;
		if (live.waveFrom >= 0) {
			int type = ZFighterTraining.of(player).charged();
			if (ZFighterArts.charged(type)) {
				pose = ART;
			} else if (type != ZFighterTraining.KI_BOMB && ZFighterKi.signature(live).style() == ZFighterKi.NEEDLE) {
				pose = POINT;
			} else {
				pose = CUP;
			}
		} else if (live.barrageFrom >= 0) {
			long held = player.level().getGameTime() - live.barrageFrom;
			pose = held < 5 ? NONE : held < ZFighterKi.BARRAGE_WINDUP ? PALMS : VOLLEY;
		}
		pose(player, pose);
	}

	/** Out of every pose (the power lost). */
	static void drop(ServerPlayer player) {
		pose(player, NONE);
	}

	/** An arm (or both) driven forward: everyone near sees it, the thrower too. */
	public static void thrust(LivingEntity entity, int hand, int kind) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ZFighterNet.HandThrow fx = new ZFighterNet.HandThrow(entity.getId(), hand, kind);
		for (ServerPlayer viewer : level.players()) {
			if (viewer == entity || viewer.distanceToSqr(entity) <= SEEN * SEEN) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	/** The right hand, or the left, of the main arm's side ({@code main}). */
	public static int hand(LivingEntity entity, boolean main) {
		boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
		return main == right ? RIGHT : LEFT;
	}

	/** Where a thrown palm is ({@code left}: the left one): ahead of the eyes along {@code look}, to its side, a little down. */
	public static Vec3 palm(LivingEntity entity, Vec3 look, boolean left) {
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
		double s = entity.getScale();
		return entity.getEyePosition().add(look.scale(PALM_AHEAD * s)).add(right.scale((left ? -PALM_SIDE : PALM_SIDE) * s))
			.add(0.0, -PALM_DOWN * s, 0.0);
	}

	/** The main hand's palm. */
	public static Vec3 mainPalm(LivingEntity entity, Vec3 look) {
		return palm(entity, look, hand(entity, true) == LEFT);
	}

	/** Where the cupped hands hold a gathering wave: low at the lead hip, a little ahead (by the body, not the look). */
	public static Vec3 cupped(LivingEntity entity) {
		float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		double side = entity.getMainArm() == HumanoidArm.RIGHT ? CUP_SIDE : -CUP_SIDE;
		double s = entity.getScale();
		return entity.position().add(0.0, entity.getBbHeight() * CUP_UP, 0.0).add(right.scale(side * s)).add(ahead.scale(CUP_AHEAD * s));
	}

	/** The cry as the wave leaves the hands, from {@code entity}. */
	public static void shout(LivingEntity entity) {
		// a fighter's own voice: some lower than others, the same one each time
		float pitch = (entity.getUUID().getLeastSignificantBits() & 3) == 0 ? SHOUT_LOW : SHOUT_PITCH;
		SoundSource source = entity instanceof ServerPlayer ? SoundSource.PLAYERS : SoundSource.NEUTRAL;
		entity.level().playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(), SoundEvents.VINDICATOR_CELEBRATE, source, SHOUT_VOLUME,
			pitch);
		entity.level().playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, source, 0.8F, 0.6F);
	}
}
