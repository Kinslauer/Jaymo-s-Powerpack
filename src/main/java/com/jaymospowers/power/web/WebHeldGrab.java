package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.7) Living Armor's G, close up (Andy: "change Living armor G move to a close range grab, that holds the mob (up to
 * cow size) in the left hand and left clicking plays 1 of 6 attack animations. bite, stab, weapon arm."): with the
 * tendrils out, G within {@link #GRAB_REACH} of something living no bigger than a cow seizes it in the LEFT hand. It
 * hangs there, pinned at the hand, facing you, weightless, unable to move or hurt anyone, and comes along as you walk.
 * Left click mauls it: one of six attacks, never the same one twice running, each with its own damage, sound and mess
 * ({@link #BITE}: the head lunges and the teeth close on it, feeding you a little; {@link #STAB}: the right hand
 * sharpens into a spike; {@link #WEAPON}: the right arm forms the strain's weapon; {@link #SLAM}: it's smashed into the
 * ground in front of you, stunned; {@link #CRUSH}: both hands squeeze, through its armour; {@link #SKEWER}: the right
 * shoulder's tendril spears it). G again drops it, as does the hold running out ({@link #HELD_MAX_TICKS}), the tendrils
 * going in, or its death (the body falls where it was held). Anything bigger than a cow, or further off, G still takes
 * with the far tendril grab and hurls, as before. Everyone sees the hold and the attacks (the {@link Held} attachment is
 * synced; WebHeldGrabPose draws them).
 */
public final class WebHeldGrab {
	/** How close (blocks from the eyes, plus half its width) something must be for the hand to take it. */
	public static final double GRAB_REACH = 3.0;
	/** The biggest the hand takes: a bounding box no bigger than a cow's (0.9 x 1.4 x 0.9 blocks), with a little slack. */
	public static final double GRAB_MAX_VOLUME = EntityTypes.COW.getWidth() * EntityTypes.COW.getHeight() * EntityTypes.COW.getWidth() * 1.05;
	/** Longest the hand holds before it drops what it has. */
	public static final int HELD_MAX_TICKS = 400;
	/** Where it's held, from the eyes, in your size: this far ahead (plus half its width), this far to the left, this far down. */
	public static final double HOLD_FORWARD = 0.75, HOLD_LEFT = 0.55, HOLD_DROP = 0.3;
	/** Pinned: each tick it closes this share of the gap to the hand (at most {@code PIN_MAX_SPEED}); a gap past {@code PIN_SNAP} it's just put there. */
	static final double PIN_STEER = 0.8, PIN_MAX_SPEED = 1.6, PIN_SNAP = 1.5;
	/** Further than this from you (something moved it) and the hand has lost it. */
	static final double LOSE_RANGE = 6.0;

	/** The eight attacks, by index; the last two are the big suit's ({@link #SWING}, {@link #POUND}). */
	public static final int BITE = 0, STAB = 1, WEAPON = 2, SLAM = 3, CRUSH = 4, SKEWER = 5, SWING = 6, POUND = 7, ATTACKS = 8;
	/** How many of them any suit with the hand can play (the first six). */
	public static final int BASE_ATTACKS = 6;
	/** The six short attacks are animated for {@code ANIM_TICKS} and land {@code HIT_TICK} ticks in; the big suit's two run longer ({@link #animTicks}). */
	public static final int ATTACK_TICKS = 18, ANIM_TICKS = 18;
	public static final int[] HIT_TICK = {7, 6, 9, 10, 8, 7, 30, 8};
	/** The ankle swing: the whole animation, and the ticks of wind-up before the circle starts. */
	public static final int SWING_ANIM_TICKS = 34, SWING_WINDUP = 4;
	/** The ground and pound: the whole animation, and the ticks the three blows land (the first is {@code HIT_TICK[POUND]}). */
	public static final int POUND_ANIM_TICKS = 30;
	public static final int[] POUND_HITS = {8, 14, 20};
	/** The ankle swing: how wide the circle is (blocks from you to the swung body's middle), how high its middle swings, and how far from the swung body things are caught. */
	static final double SWING_ARM = 1.7, SWING_HEIGHT = 0.9, SWING_CATCH = 1.7;
	/** Everything else the swing catches takes this much and is knocked away this hard (once each); the held one takes {@code SWING_DAMAGE} as it's let go. */
	static final float SWING_HIT_DAMAGE = 6.0F, SWING_DAMAGE = 8.0F;
	static final double SWING_KNOCK = 1.3, SWING_KNOCK_UP = 0.35;
	/** Let go, it flies the way you look at this speed, and this much upward. */
	static final double SWING_THROW = 1.9, SWING_THROW_UP = 0.3;
	/** The ground and pound: each blow, and the stun (Slowness) it leaves; how far ahead it's driven into the ground, and how high it's lifted between the blows. */
	static final float POUND_DAMAGE = 4.0F;
	static final int POUND_STUN_TICKS = 24, POUND_STUN_LEVEL = 2, POUND_DUST = 12;
	static final double POUND_FORWARD = 1.4, POUND_LIFT = 0.9;

	// ---- carrying a friend over the shoulders ----
	/** What the hold is: something in the hand, or a friend over the shoulders. */
	public static final int MODE_HAND = 0, MODE_SHOULDERS = 1;
	/** Over the shoulders: how far their feet are above yours (in your size), and how far back from you they sit. */
	static final double CARRY_RISE = 1.45, CARRY_BACK = 0.15;
	/** They can let go by sneaking again once this many ticks have passed (and they have let go of sneaking since). */
	public static final int CARRY_GRACE_TICKS = 30;
	/** The throw: how hard the way you look, how far up. */
	static final double CARRY_THROW = 2.3, CARRY_THROW_UP = 0.35;
	/** The thrown friend takes no fall damage on that landing for this long (ticks). */
	static final int SOFT_LAND_TICKS = 200;
	/** The carry's reach: how close a sneaking friend must be (blocks from the eyes, plus half their width). */
	static final double CARRY_REACH = 3.0;
	/** The bite: heavy, and it feeds you this much health. */
	static final float BITE_DAMAGE = 9.0F, BITE_HEAL = 2.0F;
	/** The stab. */
	static final float STAB_DAMAGE = 7.0F;
	/** The weapon arm (the red strain's blades hit WebSymbioteStrains.RED_BLADE_DAMAGE harder, as the weapon arm's do). */
	static final float WEAPON_DAMAGE = 8.0F;
	/** The slam: into the ground this far ahead of you, stunned (Slowness) this long at this level. */
	static final float SLAM_DAMAGE = 6.0F;
	static final double SLAM_FORWARD = 1.4;
	static final int SLAM_STUN_TICKS = 30, SLAM_STUN_LEVEL = 2, SLAM_DUST = 18;
	/** The crush: lighter, but it ignores armour. */
	static final float CRUSH_DAMAGE = 5.5F;
	/** The skewer. */
	static final float SKEWER_DAMAGE = 10.0F;
	/** How hard your view shakes as an attack lands. */
	static final float SHAKE = 0.6F;

	/**
	 * What the left hand holds (an entity id), since when (game time), and the attack under way on it ({@link #BITE}...,
	 * -1 for none) and when it began. Synced to everyone, who draw the hold and the attack (WebHeldGrabPose).
	 */
	public record Held(int entity, long since, int attack, long attackStart, int mode) {
		public static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Held::entity, ByteBufCodecs.VAR_LONG, Held::since,
			ByteBufCodecs.VAR_INT, Held::attack, ByteBufCodecs.VAR_LONG, Held::attackStart,
			ByteBufCodecs.VAR_INT, Held::mode, Held::new);

		/** A friend over the shoulders, not something in the hand. */
		public boolean carried() {
			return mode == MODE_SHOULDERS;
		}
	}

	public static final AttachmentType<Held> HELD = AttachmentRegistry.create(JaymosPowers.id("web_held_grab"),
		builder -> builder.syncWith(Held.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** The last attack each player used, so the next is never the same one. */
	private static final Map<UUID, Integer> LAST = new HashMap<>();
	/** Everything the current swing has already hit (by entity id), per swinger. */
	private static final Map<UUID, java.util.Set<Integer>> SWUNG = new HashMap<>();
	/** Carried friends who have let go of sneaking since they were picked up (so sneaking again means "put me down"). */
	private static final java.util.Set<UUID> CARRIED_RELEASED = new java.util.HashSet<>();
	/** Thrown friends: until when (game time) their next landing costs them no fall damage. */
	private static final Map<UUID, Long> SOFT_LANDING = new HashMap<>();

	private WebHeldGrab() {
	}

	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(WebNet.HeldSeize.TYPE, (payload, ctx) -> {
			if (!WebSymbiote.driving(ctx.player())) {
				seize(ctx.player(), payload.entityId());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(WebNet.HeldAttack.TYPE, (payload, ctx) -> {
			if (!WebSymbiote.driving(ctx.player())) {
				attack(ctx.player());
			}
		});
		// a thrown friend lands softly, once
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (SOFT_LANDING.isEmpty() || !source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
				return true;
			}
			Long until = SOFT_LANDING.get(entity.getUUID());
			if (until == null) {
				return true;
			}
			SOFT_LANDING.remove(entity.getUUID());
			return entity.level().getGameTime() > until;
		});
		// held up in the hand, it can't hurt anyone
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Entity attacker = source.getEntity();
			Entity direct = source.getDirectEntity();
			return !(attacker != null && heldByAnyone(attacker) || direct != null && direct != attacker && heldByAnyone(direct));
		});
	}

	/** The hand is holding something. Both sides. */
	public static boolean holding(Player player) {
		return player.getAttached(HELD) != null;
	}

	/** What the hand holds (null for nothing, or something gone). Both sides. */
	public static @Nullable LivingEntity heldEntity(Player player) {
		Held held = player.getAttached(HELD);
		return held != null && player.level().getEntity(held.entity()) instanceof LivingEntity living ? living : null;
	}

	/** Some player's hand (anyone's) holds this. Both sides. */
	public static boolean heldByAnyone(Entity entity) {
		for (Player other : entity.level().players()) {
			Held held = other.getAttached(HELD);
			if (held != null && held.entity() == entity.getId()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the hand would take this: with the tendrils out, something living the tendrils could grab, no bigger than a
	 * cow ({@link #GRAB_MAX_VOLUME}), within {@link #GRAB_REACH}, in nobody's grip. Both sides (the client decides between
	 * this and the far tendril grab before it sends).
	 */
	public static boolean canSeize(Player player, @Nullable Entity target) {
		if (!(target instanceof LivingEntity living) || !WebSymbiote.tendrilKeys(player) || !WebTendrils.grabbable(player, living)
			|| heldByAnyone(living) || WebTendrils.held(living)) {
			return false;
		}
		AABB box = living.getBoundingBox();
		if (box.getXsize() * box.getYsize() * box.getZsize() > GRAB_MAX_VOLUME) {
			return false;
		}
		return box.getCenter().distanceTo(player.getEyePosition()) <= GRAB_REACH + living.getBbWidth() * 0.5 + 0.5;
	}

	/**
	 * Whether {@code player} would carry this friend over the shoulders: another player, sneaking (their consent), no one
	 * else's captive, within {@link #CARRY_REACH}, with Living Armor or the big suit on. Both sides.
	 */
	public static boolean canCarry(Player player, @Nullable Entity target) {
		if (!(target instanceof Player friend) || friend == player || !friend.isShiftKeyDown() || !friend.isAlive() || friend.isSpectator()
			|| !WebSymbiote.livingArmor(player) || heldByAnyone(friend) || WebTendrils.held(friend) || WebLatch.latched(player)
			|| friend.getVehicle() != null || friend.isVehicle()) {
			return false;
		}
		return friend.getBoundingBox().getCenter().distanceTo(player.getEyePosition()) <= CARRY_REACH + friend.getBbWidth() * 0.5 + 0.5;
	}

	/** G: the hand takes entity {@code id} (or lets go, with -1 or whenever it already holds something). */
	static void seize(ServerPlayer player, int id) {
		if (WebLatch.latched(player)) {
			WebLatch.release(player, true);
			return;
		}
		if (holding(player) || id < 0) {
			drop(player);
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		Entity target = player.level().getEntity(id);
		if (WebLatch.start(player, target)) {
			return;
		}
		boolean carry = canCarry(player, target);
		if (!carry && !canSeize(player, target) || !(target instanceof LivingEntity living)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebTendrils.weightless(living, true);
		CARRIED_RELEASED.remove(living.getUUID());
		if (carry && living instanceof ServerPlayer friend) {
			friend.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.web_brute.carried", player.getDisplayName()));
		}
		player.setAttached(HELD, new Held(living.getId(), now, -1, 0L, carry ? MODE_SHOULDERS : MODE_HAND));
		if (living instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 0.55F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.8F, 0.8F);
		com.jaymospowers.core.Shakes.send(player, 0.3F, living.position());
		WebSymbiote.narrate(player, "seize");
	}

	/** The hand opens: whatever it held falls where it is. */
	static void drop(ServerPlayer player) {
		Held held = player.getAttached(HELD);
		if (held == null) {
			return;
		}
		player.removeAttached(HELD);
		SWUNG.remove(player.getUUID());
		if (player.level().getEntity(held.entity()) instanceof LivingEntity living) {
			CARRIED_RELEASED.remove(living.getUUID());
			WebTendrils.weightless(living, false);
			living.setDeltaMovement(Vec3.ZERO);
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(Vec3.ZERO));
			}
		}
	}

	/** How long an attack's animation runs (ticks), and so how soon after it the next may start. */
	public static int animTicks(int attack) {
		return attack == SWING ? SWING_ANIM_TICKS : attack == POUND ? POUND_ANIM_TICKS : ANIM_TICKS;
	}

	/** Whether an attack's blow lands {@code t} ticks into it. */
	private static boolean hitsAt(int attack, long t) {
		if (attack == POUND) {
			for (int hit : POUND_HITS) {
				if (t == hit) {
					return true;
				}
			}
			return false;
		}
		return t == HIT_TICK[Mth.clamp(attack, 0, ATTACKS - 1)];
	}

	/**
	 * Left click: a latched wearer bites ({@link WebLatch}); a friend over the shoulders is thrown; otherwise one of the
	 * attacks on what's held (the first six, plus the big suit's two), never the one before, no sooner than the last has played out.
	 */
	static void attack(ServerPlayer player) {
		if (WebLatch.latched(player)) {
			WebLatch.bite(player);
			return;
		}
		Held held = player.getAttached(HELD);
		LivingEntity target = heldEntity(player);
		if (held == null || target == null || !target.isAlive() || !player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (held.carried()) {
			if (now - held.since() >= 2) {
				throwFriend(player, target);
			}
			return;
		}
		if (!WebSymbiote.tendrilKeys(player)) {
			return;
		}
		if (held.attack() >= 0 && now - held.attackStart() < Math.max(ATTACK_TICKS, animTicks(held.attack())) || now - held.since() < 2) {
			return;
		}
		int pool = WebSymbiote.bulked(player) ? ATTACKS : BASE_ATTACKS;
		int last = LAST.getOrDefault(player.getUUID(), -1);
		if (last >= pool) {
			last = -1;
		}
		int next = player.getRandom().nextInt(last < 0 ? pool : pool - 1);
		if (last >= 0 && next >= last) {
			next++;
		}
		LAST.put(player.getUUID(), next);
		SWUNG.remove(player.getUUID());
		player.setAttached(HELD, new Held(held.entity(), held.since(), next, now, held.mode()));
		// the wind-up, heard by everyone
		switch (next) {
			case BITE -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.9F, 0.5F);
			case STAB, WEAPON -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.6F);
			case SLAM, POUND -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.9F, 0.5F);
			case SWING -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.9F, 0.5F);
			case CRUSH -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0F, 0.5F);
			default -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.5F);
		}
	}

	/** Left click on a friend over the shoulders: thrown the way you look, hard, and they land without fall damage. */
	private static void throwFriend(ServerPlayer player, LivingEntity friend) {
		ServerLevel level = player.level();
		Vec3 velocity = player.getLookAngle().scale(CARRY_THROW).add(0.0, CARRY_THROW_UP, 0.0);
		drop(player);
		WebHaul.push(friend, velocity);
		SOFT_LANDING.put(friend.getUUID(), level.getGameTime() + SOFT_LAND_TICKS);
		if (friend instanceof ServerPlayer thrown) {
			thrown.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.web_brute.thrown"));
		}
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		com.jaymospowers.core.Shakes.send(player, 0.4F, friend.position());
	}

	/** Where the hold has it right now (the middle of its box): at the left hand, or down on the ground ahead through a slam. */
	static Vec3 point(ServerPlayer player, LivingEntity target, Held held, long now) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = look.horizontal();
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		double scale = player.getScale();
		if (held.carried()) {
			// over the shoulders: their feet at your shoulders, a little behind you
			return player.position().add(flat.scale(-CARRY_BACK * scale)).add(0.0, CARRY_RISE * scale + target.getBbHeight() * 0.5, 0.0);
		}
		Vec3 hand = player.getEyePosition().add(flat.scale(HOLD_FORWARD * scale + target.getBbWidth() * 0.5)).subtract(right.scale(HOLD_LEFT * scale))
			.add(0.0, -HOLD_DROP * scale, 0.0);
		if (held.attack() == SLAM) {
			// down onto the ground in front, held there a moment, then hauled back up
			long t = now - held.attackStart();
			int hit = HIT_TICK[SLAM];
			double down = t < hit ? Mth.clamp((t - hit * 0.5) / (hit * 0.5), 0.0, 1.0) : t < hit + 4 ? 1.0 : Mth.clamp(1.0 - (t - hit - 4) / 4.0, 0.0, 1.0);
			down = down * down * (3.0 - 2.0 * down);
			Vec3 ground = player.position().add(flat.scale(SLAM_FORWARD * scale + target.getBbWidth() * 0.5)).add(0.0, target.getBbHeight() * 0.5, 0.0);
			return hand.lerp(ground, down);
		}
		if (held.attack() == SWING) {
			// swung by the ankles in a full circle round you: out from the hand over the wind-up, then once round (left, ahead, right, behind)
			long t = now - held.attackStart();
			double u = Mth.clamp((t - SWING_WINDUP) / (double)(HIT_TICK[SWING] - SWING_WINDUP), 0.0, 1.0);
			u = 0.5 * u + 0.5 * u * u * (3.0 - 2.0 * u);
			double angle = -Math.PI * 0.5 + u * Math.PI * 2.0;
			double reach = Mth.lerp(Mth.clamp(t / (double)SWING_WINDUP, 0.0, 1.0), HOLD_FORWARD * scale, SWING_ARM * scale);
			Vec3 spot = player.position().add(flat.scale(Math.cos(angle) * reach)).add(right.scale(Math.sin(angle) * reach))
				.add(0.0, SWING_HEIGHT * scale, 0.0);
			return t < SWING_WINDUP ? hand.lerp(spot, t / (double)SWING_WINDUP) : spot;
		}
		if (held.attack() == POUND) {
			// down onto the ground in front, lifted between the three blows, then hauled back up
			long t = now - held.attackStart();
			Vec3 ground = player.position().add(flat.scale(POUND_FORWARD * scale + target.getBbWidth() * 0.5)).add(0.0, target.getBbHeight() * 0.5, 0.0);
			int first = POUND_HITS[0], last = POUND_HITS[POUND_HITS.length - 1];
			if (t < first) {
				double down = Mth.clamp((t - first * 0.4) / (first * 0.6), 0.0, 1.0);
				down = down * down * (3.0 - 2.0 * down);
				return hand.lerp(ground, down);
			}
			if (t <= last) {
				double nearest = Double.MAX_VALUE;
				for (int hit : POUND_HITS) {
					nearest = Math.min(nearest, Math.abs(t - hit));
				}
				double lift = Mth.clamp(nearest / 3.0, 0.0, 1.0);
				return ground.add(0.0, POUND_LIFT * scale * lift, 0.0);
			}
			double back = Mth.clamp((t - last - 2) / 6.0, 0.0, 1.0);
			back = back * back * (3.0 - 2.0 * back);
			return ground.lerp(hand, back);
		}
		return hand;
	}

	/** Every tick, per web slinger (from {@link WebTendrils#tick}): what's held hangs at the hand, facing you; the attacks land. */
	static void tick(ServerPlayer player) {
		WebLatch.tick(player);
		Held held = player.getAttached(HELD);
		if (held == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		LivingEntity target = heldEntity(player);
		boolean carried = held.carried();
		if (!player.isAlive() || player.isSpectator() || !(carried ? WebSymbiote.livingArmor(player) : WebSymbiote.tendrilKeys(player))
			|| target == null || !target.isAlive() || target.level() != level || target.distanceTo(player) > LOSE_RANGE
			|| now - held.since() > (carried ? Integer.MAX_VALUE : HELD_MAX_TICKS) || carried && (WebLatch.latched(player) || WebSymbiote.driving(player))) {
			// (dead, it falls where it was held)
			drop(player);
			return;
		}
		if (carried) {
			// the friend can put themselves down: sneak again, once the grace has passed and they've let go of sneaking
			if (target.isShiftKeyDown()) {
				if (now - held.since() > CARRY_GRACE_TICKS && CARRIED_RELEASED.contains(target.getUUID())) {
					drop(player);
					return;
				}
			} else if (now - held.since() > 2) {
				CARRIED_RELEASED.add(target.getUUID());
			}
		} else if (held.attack() == SWING) {
			swingHits(player, target, now - held.attackStart());
		}
		if (held.attack() >= 0 && hitsAt(held.attack(), now - held.attackStart())) {
			land(player, target, held.attack());
			if (player.getAttached(HELD) == null) {
				// (the swing let go of it)
				return;
			}
			if (!target.isAlive()) {
				drop(player);
				return;
			}
		}
		Vec3 point = point(player, target, held, now);
		Vec3 to = point.subtract(target.getBoundingBox().getCenter());
		if (to.length() > PIN_SNAP && !(target instanceof Player)) {
			// (a player's motion is their own client's: they're only ever steered)
			target.setPos(point.x, point.y - target.getBbHeight() * 0.5, point.z);
			target.setDeltaMovement(Vec3.ZERO);
		} else {
			Vec3 v = to.scale(PIN_STEER);
			double speed = v.length();
			if (speed > PIN_MAX_SPEED) {
				v = v.scale(PIN_MAX_SPEED / speed);
			}
			target.setDeltaMovement(v);
		}
		target.needsSync = true;
		target.syncVelocity = true;
		target.resetFallDistance();
		// facing you, held still
		Vec3 face = player.getEyePosition().subtract(target.getBoundingBox().getCenter());
		float yaw = (float)(Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
		target.setYRot(yaw);
		target.setYBodyRot(yaw);
		target.setYHeadRot(yaw);
		target.setXRot(0.0F);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (target instanceof ServerPlayer other && now % 2 == 0) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(target.getDeltaMovement()));
		}
	}

	/** An attack lands on what's held: its damage, sound and mess. */
	private static void land(ServerPlayer player, LivingEntity target, int attack) {
		ServerLevel level = player.level();
		Vec3 at = target.getBoundingBox().getCenter();
		switch (attack) {
			case BITE -> {
				target.hurtServer(level, level.damageSources().playerAttack(player), BITE_DAMAGE);
				player.heal(BITE_HEAL);
				blood(level, at, 24, target.getBbWidth() * 0.5);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.1F, 0.6F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 0.5F, 0.8F);
			}
			case STAB -> {
				target.hurtServer(level, level.damageSources().playerAttack(player), STAB_DAMAGE);
				blood(level, at, 14, 0.2);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.15);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.9F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 0.8F);
			}
			case WEAPON -> {
				// TODO WebMorph: a public strike entry point that takes a held entity (something like
				// WebMorph.strikeHeld(player, target)) would form the strain's own weapon here and hit with its damage; until it
				// exists this is a heavy blow with a blade formed on the arm (WebHeldGrabPose, WebTendrilLayer.heldGrab).
				boolean red = WebSymbioteStrains.of(player) == WebSymbioteStrain.RED;
				target.hurtServer(level, level.damageSources().playerAttack(player), WEAPON_DAMAGE * (red ? WebSymbioteStrains.RED_BLADE_DAMAGE : 1.0F));
				blood(level, at, 18, 0.3);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.2);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.5F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.8F, 0.7F);
			}
			case SLAM -> {
				target.hurtServer(level, level.damageSources().playerAttack(player), SLAM_DAMAGE);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLAM_STUN_TICKS, SLAM_STUN_LEVEL), player);
				KitchenCompat.jolt(target);
				Vec3 foot = new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ());
				BlockPos under = BlockPos.containing(foot.x, foot.y - 0.1, foot.z);
				BlockState state = level.getBlockState(under);
				if (!state.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), foot.x, foot.y + 0.1, foot.z, SLAM_DUST,
						target.getBbWidth() * 0.6, 0.1, target.getBbWidth() * 0.6, 0.12);
					level.playSound(null, foot.x, foot.y, foot.z, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.5F);
				}
				level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 0.5F);
			}
			case SWING -> {
				// let go at the end of the circle: it flies the way you look, and is hurt as it goes
				target.hurtServer(level, level.damageSources().playerAttack(player), SWING_DAMAGE);
				Vec3 velocity = player.getLookAngle().scale(SWING_THROW).add(0.0, SWING_THROW_UP, 0.0);
				drop(player);
				WebHaul.push(target, velocity);
				if (target.isAlive()) {
					WebTendrils.markThrown(player, target);
				}
				blood(level, at, 14, 0.3);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.1F, 0.5F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
			case POUND -> {
				// one of three blows into the ground in front; it stays held
				target.hurtServer(level, level.damageSources().playerAttack(player), POUND_DAMAGE);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, POUND_STUN_TICKS, POUND_STUN_LEVEL), player);
				KitchenCompat.jolt(target);
				Vec3 foot = new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ());
				BlockState state = level.getBlockState(BlockPos.containing(foot.x, foot.y - 0.1, foot.z));
				if (!state.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), foot.x, foot.y + 0.1, foot.z, POUND_DUST,
						target.getBbWidth() * 0.6, 0.1, target.getBbWidth() * 0.6, 0.12);
					level.playSound(null, foot.x, foot.y, foot.z, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.5F);
				}
				level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0F, 0.7F);
				level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.5F);
			}
			case CRUSH -> {
				// straight through the armour
				target.hurtServer(level, level.damageSources().indirectMagic(player, player), CRUSH_DAMAGE);
				com.jaymospowers.core.Crushing.mark(target);
				blood(level, at, 10, 0.25);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.4F);
			}
			default -> {
				target.hurtServer(level, level.damageSources().playerAttack(player), SKEWER_DAMAGE);
				blood(level, at, 20, 0.3);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.2);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.9F, 1.1F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
		}
		com.jaymospowers.core.Shakes.send(player, SHAKE, at);
		WebSymbiote.narrate(player, "maul");
	}

	/** The ankle swing's sweep: everything else living near the swung body is hit and knocked away, once each. */
	private static void swingHits(ServerPlayer player, LivingEntity swung, long t) {
		if (t < SWING_WINDUP || t >= HIT_TICK[SWING]) {
			return;
		}
		ServerLevel level = player.level();
		java.util.Set<Integer> struck = SWUNG.computeIfAbsent(player.getUUID(), k -> new java.util.HashSet<>());
		Vec3 middle = swung.getBoundingBox().getCenter();
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, swung.getBoundingBox().inflate(SWING_CATCH),
			e -> e != player && e != swung && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative())
				&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !heldByAnyone(e) && !struck.contains(e.getId())
				&& e.getBoundingBox().getCenter().distanceTo(middle) <= SWING_CATCH + e.getBbWidth() * 0.5)) {
			struck.add(other.getId());
			other.hurtServer(level, level.damageSources().playerAttack(player), SWING_HIT_DAMAGE);
			Vec3 away = other.position().subtract(player.position());
			Vec3 flat = new Vec3(away.x, 0.0, away.z);
			flat = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : flat.normalize();
			WebHaul.push(other, flat.scale(SWING_KNOCK).add(0.0, SWING_KNOCK_UP, 0.0));
			Vec3 at = other.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
			com.jaymospowers.core.Shakes.send(player, SHAKE * 0.6F, at);
		}
	}

	private static void blood(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(WebTendrilCombos.BLOOD, 1.2F), at.x, at.y, at.z, count, spread, spread, spread, 0.0);
	}

	static void forget(ServerPlayer player) {
		LAST.remove(player.getUUID());
		SWUNG.remove(player.getUUID());
		SOFT_LANDING.remove(player.getUUID());
		CARRIED_RELEASED.remove(player.getUUID());
	}
}
