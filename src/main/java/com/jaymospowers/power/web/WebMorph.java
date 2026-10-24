package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Fractures;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The weapon arm: in the suit, hold left click with an empty hand and the symbiote gathers ({@link #CHARGE}): tendrils
 * course and bulge through the suit toward the arm ({@link #COURSE_TICKS}), collect in it ({@link #COLLECT_TICKS}) and
 * the hand forms into the weapon ({@link #FORMED_TICKS}). Let go (at a creature, or at the air) and the arm throws out as
 * a tendril; let go early and it strikes the moment the weapon has formed. The weapon is picked at random each time: a <b>mace</b> (blunt: smashes armour, breaks
 * bones), a <b>scythe</b> (slashing: a wide sweep that severs limbs far more often than a blade, and catches what's
 * beside the target too) or a <b>spear</b> (piercing: a straight thrust through armour). Arms alternate, right then
 * left. Everyone sees it ({@link #MORPH}); the client draws the cord and the weapon ({@code WebMorphLayer}).
 *
 * <p>Three more: a <b>whip-blade</b> (a long barbed lash: reaches {@link #WHIP_REACH}, cuts through up to
 * {@link #WHIP_HITS} in a line and drags the last of them to you), a <b>hammer-fist</b> (a great knuckled club: throws
 * what it hits high into the air and stuns it a moment, the ground under it bursting) and a <b>hook claw</b> (a grapple:
 * hooks what it hits, reels it in to you and leaves it bleeding).
 *
 * <p>With Living Armor up ({@link WebSymbiote#armored}: its tendrils out, or the big body), the weapon forms on a
 * tendril instead of the arm ({@link Morph#tendril}): a shoulder tendril (two braided, for the heavy ones) coils back,
 * its tip swells into the weapon head, and the strike lashes through the tendril. Same reach, damage and effects; only
 * the client draws it differently ({@code WebTendrilLayer}).
 *
 * <p>Strains ({@link WebSymbioteStrain}) pick from their own set: the white one only forms blunt shapes (the mace and a
 * flat <b>palm</b> shove: no blades, no severing); the red one only blades (scythe, spear and a heavy <b>axe</b>), harder
 * ({@link WebSymbioteStrains#RED_BLADE_DAMAGE}) and severing more often ({@link WebSymbioteStrains#RED_SEVER}).
 */
public final class WebMorph {
	/** The weapons. */
	public static final int MACE = 0, SCYTHE = 1, SPEAR = 2, WHIP = 3, HAMMER = 4, HOOK = 5, PALM = 6, AXE = 7, WEAPONS = 8;
	/** What each strain forms: black every weapon but the palm and the axe; white blunt only; red blades only. */
	static final int[] BLACK_WEAPONS = {MACE, SCYTHE, SPEAR, WHIP, HAMMER, HOOK};
	static final int[] WHITE_WEAPONS = {MACE, PALM};
	static final int[] RED_WEAPONS = {SCYTHE, SPEAR, AXE};
	/** How far the arm reaches (blocks from the eyes), and the slack the server allows on the client's pick. */
	public static final double REACH = 6.0, REACH_SLACK = 1.5;
	/** The whip-blade and the hook claw reach further than the rest (blocks from the eyes). */
	public static final double WHIP_REACH = 9.0, HOOK_REACH = 8.0;
	/** The whole move (ticks): the arm swells into the weapon until {@code FORM}, swings or thrusts, lands at {@code HIT}. */
	public static final int TICKS = 16, FORM = 4, HIT = 8;
	/**
	 * The gathering, held (ticks from the press): bulges course through the suit until {@code COURSE_TICKS}, run down
	 * into the arm until {@code COLLECT_TICKS}, and the weapon is whole at {@code FORMED_TICKS}. Held longer than
	 * {@code CHARGE_MAX_TICKS}, it lets go of its own accord.
	 */
	public static final int COURSE_TICKS = 8, COLLECT_TICKS = 13, FORMED_TICKS = 18, CHARGE_MAX_TICKS = 600;
	/** Ticks after a strike starts before the next one (a little past {@link #HIT}, so they chain arm over arm). */
	public static final int COOLDOWN = 12;
	/** Damage by weapon: mace, scythe, spear, whip-blade, hammer-fist, hook claw, palm, axe. */
	static final float[] DAMAGE = {10.0F, 8.0F, 9.0F, 7.0F, 9.0F, 6.0F, 5.0F, 11.0F};
	/** Mace and spear wear armour this many times harder than the blow alone would. */
	static final float ARMOR_WEAR = 4.0F;
	/** The spear goes through armour: this share of its damage is dealt again ignoring armour. */
	static final float SPEAR_PIERCE = 0.35F;
	/** The mace: chance (against a humanoid; players {@link Fractures#PLAYER_CHANCE} x2) to break the bone it lands on. */
	static final float MACE_BREAK = 0.35F;
	/** The scythe: chance to take off the limb it lands on (a sword's is {@link Dismember#BLADE_CHANCE}). */
	static final float SCYTHE_SEVER = 0.45F;
	/** The scythe sweeps through everything this close to its target, in front of you, for this share of its damage. */
	static final double SCYTHE_SWEEP = 2.5;
	static final float SCYTHE_SWEEP_SHARE = 0.6F;
	/** The palm (white): a flat open-hand shove, this hard (b/t) and this far up; it breaks nothing. */
	static final double PALM_SHOVE = 1.8, PALM_LIFT = 0.4;
	/** The axe (red): a heavy chop, this chance to take off the limb it lands on (before the red x1.5), this knockback. */
	static final float AXE_SEVER = 0.35F;
	static final double AXE_KNOCK = 0.8;
	/** Knockback: mace (and up), scythe (sideways along the sweep), spear (straight back). */
	static final double MACE_KNOCK = 0.9, MACE_LIFT = 0.45, SCYTHE_KNOCK = 0.7, SPEAR_KNOCK = 1.3;

	// ---- whip-blade ----
	/** It cuts through up to this many in a line (the first the one aimed at), anything this close to the line (blocks). */
	static final int WHIP_HITS = 3;
	static final double WHIP_WIDTH = 0.9;
	/** Each one after the first takes this share of the one before's damage. */
	static final float WHIP_FALLOFF = 0.8F;
	/** The last one it cuts is dragged toward you (at most this fast, b/t, a little up); the rest are flicked aside. */
	static final double WHIP_PULL = 1.1, WHIP_PULL_LIFT = 0.3, WHIP_FLICK = 0.35;

	// ---- hammer-fist ----
	/** Thrown up this hard (b/t), and back this much; stunned (Slowness and Weakness, these levels) this long (ticks). */
	static final double HAMMER_LIFT = 1.15, HAMMER_KNOCK = 0.45;
	static final int HAMMER_STUN_TICKS = 30, HAMMER_SLOW_LEVEL = 3, HAMMER_WEAK_LEVEL = 1;
	/** The ground under it bursts: this many bits of the block it stood on. */
	static final int HAMMER_DUST = 24;

	// ---- hook claw ----
	/** Reeled in: pulled toward you at this share of the distance (b/t), at most this fast, lifted a little. */
	static final double HOOK_YANK = 0.32, HOOK_YANK_MAX = 1.6, HOOK_LIFT = 0.35;
	/** It bleeds: this much damage (through armour) every {@code BLEED_EVERY} ticks, this many times. */
	static final float BLEED_DAMAGE = 1.5F;
	static final int BLEED_EVERY = 10, BLEED_HITS = 4;
	/** The bleed's drops. */
	static final int BLOOD_COLOR = 0x8A0A12;

	/**
	 * A strike under way: which weapon, at whom (-1 the air), when it began (game time), which arm (1 right, -1 left),
	 * and whether it's on a Living Armor tendril (that side's shoulder tendril) instead of the arm.
	 */
	public record Morph(int weapon, int target, long start, int side, boolean tendril) {
		public static final StreamCodec<ByteBuf, Morph> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Morph::weapon,
			ByteBufCodecs.VAR_INT, Morph::target,
			ByteBufCodecs.VAR_LONG, Morph::start,
			ByteBufCodecs.VAR_INT, Morph::side,
			ByteBufCodecs.BOOL, Morph::tendril,
			Morph::new);

		/** Ticks in, at {@code time} (game time + partial tick). */
		public float age(float time) {
			return time - start;
		}
	}

	public static final AttachmentType<Morph> MORPH = AttachmentRegistry.create(JaymosPowers.id("web_morph"),
		builder -> builder.syncWith(Morph.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** The gathering before a strike, held: which weapon it's forming, when the press began (game time), which arm, on a tendril. */
	public record Charge(int weapon, long start, int side, boolean tendril) {
		public static final StreamCodec<ByteBuf, Charge> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Charge::weapon,
			ByteBufCodecs.VAR_LONG, Charge::start,
			ByteBufCodecs.VAR_INT, Charge::side,
			ByteBufCodecs.BOOL, Charge::tendril,
			Charge::new);

		/** Ticks in, at {@code time} (game time + partial tick). */
		public float age(float time) {
			return time - start;
		}
	}

	public static final AttachmentType<Charge> CHARGE = AttachmentRegistry.create(JaymosPowers.id("web_morph_charge"),
		builder -> builder.syncWith(Charge.STREAM_CODEC, AttachmentSyncPredicate.all()));
	private static final AttachmentType<Live> LIVE = AttachmentRegistry.create(JaymosPowers.id("web_morph_live"));

	private static final class Live {
		int lastWeapon = -1;
		int side = -1;
		long readyAt;
		/** Let go before the weapon formed: strike at this creature (-1 the air) once it has; NONE: not let go. */
		int pending = NONE;
		/** Creatures the hook claw left bleeding: {id, game time of the next drop, drops left}. */
		final java.util.List<long[]> bleeds = new java.util.ArrayList<>();
	}

	private static final int NONE = Integer.MIN_VALUE;

	/** Client → server: left click pressed and held: the symbiote starts gathering into the arm. */
	public record Gather() implements CustomPacketPayload {
		public static final Type<Gather> TYPE = new Type<>(JaymosPowers.id("web_morph_gather"));
		public static final StreamCodec<ByteBuf, Gather> CODEC = StreamCodec.unit(new Gather());

		@Override
		public Type<Gather> type() {
			return TYPE;
		}
	}

	/** Client → server: left click let go, the arm goes out (at a creature's id, or -1). */
	public record Strike(int target) implements CustomPacketPayload {
		public static final Type<Strike> TYPE = new Type<>(JaymosPowers.id("web_morph_strike"));
		public static final StreamCodec<ByteBuf, Strike> CODEC = ByteBufCodecs.VAR_INT.map(Strike::new, Strike::target).cast();

		@Override
		public Type<Strike> type() {
			return TYPE;
		}
	}

	private WebMorph() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Strike.TYPE, Strike.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Gather.TYPE, Gather.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Strike.TYPE, (payload, ctx) -> release(ctx.player(), payload.target()));
		ServerPlayNetworking.registerGlobalReceiver(Gather.TYPE, (payload, ctx) -> gather(ctx.player()));
	}

	/**
	 * Whether a left click right now is the weapon arm: the suit showing (or the big body), an empty main hand, nothing
	 * else of the symbiote's using the click. Both sides check it (the client before sending).
	 */
	public static boolean usable(@Nullable Player player) {
		if (player == null || !player.isAlive() || player.isSpectator() || !WebSymbiote.isBonded(player)
			|| !worn(player) || !player.getMainHandItem().isEmpty()
			|| WebPower.ragdolled(player) || player.getAttached(WebCounter.ACTIVE) != null || WebTendrilCombos.active(player)) {
			return false;
		}
		Morph morph = player.getAttached(MORPH);
		return morph == null || player.level().getGameTime() - morph.start() >= COOLDOWN;
	}

	/** The suit, the big body or Living Armor's tendrils: something of the symbiote's to form a weapon from. */
	private static boolean worn(Player player) {
		return WebSymbiote.suitShown(player) || WebSymbiote.livingArmor(player);
	}

	/** Whether the weapon forms on a tendril (Living Armor's are out, or the big body's) rather than the arm. */
	public static boolean onTendril(Player player) {
		return WebSymbiote.armored(player);
	}

	/** How far this weapon reaches (blocks from the eyes). */
	public static double reach(int weapon) {
		return weapon == WHIP ? WHIP_REACH : weapon == HOOK ? HOOK_REACH : REACH;
	}

	/** How far the weapon being gathered now reaches (its charge is synced, so the client knows it too); else {@link #REACH}. */
	public static double reachNow(Player player) {
		Charge charge = player.getAttached(CHARGE);
		return charge == null ? REACH : reach(charge.weapon());
	}

	/** The living thing the arm would go for: under the crosshair within {@link #REACH}. */
	public static @Nullable LivingEntity aim(Player player) {
		return aim(player, REACH);
	}

	/** The living thing the weapon would go for: under the crosshair within {@code reach}. */
	public static @Nullable LivingEntity aim(Player player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(reach));
		LivingEntity best = null;
		double bestDist = reach * reach;
		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0),
			e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e.isPickable())) {
			AABB box = entity.getBoundingBox().inflate(Math.max(0.3, entity.getPickRadius()));
			var hit = box.clip(eye, end);
			if (box.contains(eye)) {
				return (LivingEntity)entity;
			}
			if (hit.isPresent()) {
				double d = eye.distanceToSqr(hit.get());
				if (d < bestDist) {
					bestDist = d;
					best = (LivingEntity)entity;
				}
			}
		}
		if (best != null) {
			// not through a wall
			var block = player.level().clip(new net.minecraft.world.level.ClipContext(eye, best.getBoundingBox().getCenter(),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
			if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS
				&& block.getLocation().distanceToSqr(eye) + 0.25 < best.getBoundingBox().getCenter().distanceToSqr(eye)) {
				return null;
			}
		}
		return best;
	}

	private static Live live(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null) {
			live = new Live();
			player.setAttached(LIVE, live);
		}
		return live;
	}

	/** Pressed: the symbiote starts gathering into the arm, a weapon picked (never the same twice running), the other arm from last time. */
	private static void gather(ServerPlayer player) {
		if (!usable(player) || WebSymbiote.driving(player) || player.getAttached(CHARGE) != null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Live live = live(player);
		if (now < live.readyAt) {
			return;
		}
		int[] pool = switch (WebSymbioteStrains.of(player)) {
			case WHITE -> WHITE_WEAPONS;
			case RED -> RED_WEAPONS;
			default -> BLACK_WEAPONS;
		};
		int weapon;
		do {
			weapon = pool[player.getRandom().nextInt(pool.length)];
		} while (weapon == live.lastWeapon && pool.length > 1);
		live.lastWeapon = weapon;
		live.side = -live.side;
		live.pending = NONE;
		boolean tendril = onTendril(player);
		player.setAttached(CHARGE, new Charge(weapon, now, live.side, tendril));
		// it stirs under the skin (or a tendril rears back and coils)
		sound(level, player, SoundEvents.WARDEN_TENDRIL_CLICKS, 0.8F, tendril ? 0.7F : 0.5F);
		sound(level, player, SoundEvents.SLIME_SQUISH, 0.7F, 0.5F);
	}

	/** Let go: strikes now if the weapon has formed, else as soon as it has. */
	private static void release(ServerPlayer player, int targetId) {
		Charge charge = player.getAttached(CHARGE);
		if (charge == null) {
			return;
		}
		if (player.level().getGameTime() - charge.start() >= FORMED_TICKS) {
			strike(player, charge, targetId);
		} else {
			live(player).pending = targetId;
		}
	}

	/** The arm goes out with the weapon it formed. */
	private static void strike(ServerPlayer player, Charge charge, int targetId) {
		player.removeAttached(CHARGE);
		Live live = live(player);
		live.pending = NONE;
		if (!usable(player) || WebSymbiote.driving(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		LivingEntity target = null;
		double reach = reach(charge.weapon());
		if (targetId >= 0 && level.getEntity(targetId) instanceof LivingEntity living && living.isAlive() && living != player
			&& player.getEyePosition().distanceTo(living.getBoundingBox().getCenter()) <= reach + REACH_SLACK + living.getBbWidth() * 0.5) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
			if (to.dot(player.getLookAngle()) > 0.3) {
				target = living;
			}
		}
		int weapon = charge.weapon();
		live.readyAt = now + COOLDOWN;
		player.setAttached(MORPH, new Morph(weapon, target == null ? -1 : target.getId(), now, charge.side(), charge.tendril()));
		player.resetAttackStrengthTicker();
		sound(level, player, SoundEvents.HONEY_BLOCK_SLIDE, 0.8F, 0.6F);
	}

	/** Every tick, for a web slinger: the gathering, held, then the strike under way lands, then ends. */
	static void tick(ServerPlayer player) {
		tickCharge(player);
		tickBleeds(player);
		Morph morph = player.getAttached(MORPH);
		if (morph == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - morph.start();
		if (t >= TICKS || !player.isAlive() || !worn(player) || morph.tendril() && !onTendril(player)) {
			player.removeAttached(MORPH);
			return;
		}
		if (t == FORM) {
			// the swing (or thrust, or lash) whooshes out
			int w = morph.weapon();
			if (w == WHIP) {
				sound(level, player, SoundEvents.FISHING_BOBBER_THROW, 1.0F, 0.6F);
				sound(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.4F);
			} else if (w == HOOK) {
				sound(level, player, SoundEvents.FISHING_BOBBER_THROW, 1.0F, 0.8F);
				sound(level, player, SoundEvents.CHAIN_PLACE, 0.6F, 0.6F);
			} else if (w == HAMMER) {
				sound(level, player, SoundEvents.MACE_SMASH_AIR, 1.0F, 0.6F);
			} else {
				sound(level, player, w == SPEAR ? SoundEvents.TRIDENT_THROW.value() : SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, w == MACE ? 0.6F : 1.0F);
			}
			if (morph.tendril()) {
				// the tendril cracks out off the back
				sound(level, player, SoundEvents.WARDEN_TENDRIL_CLICKS, 0.9F, 1.2F);
			}
		}
		if (t != HIT) {
			return;
		}
		Entity found = morph.target() < 0 ? null : level.getEntity(morph.target());
		if (found instanceof LivingEntity target && target.isAlive()
			&& player.getEyePosition().distanceTo(target.getBoundingBox().getCenter()) <= reach(morph.weapon()) + REACH_SLACK + 1.0 + target.getBbWidth() * 0.5) {
			land(player, level, morph, target);
		} else {
			// the air: a crack of the whip, nothing more
			sound(level, player, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 0.7F);
		}
	}

	/** The gathering: noises as it courses, collects and forms; a let-go strikes once formed; it gives up if the suit goes. */
	private static void tickCharge(ServerPlayer player) {
		Charge charge = player.getAttached(CHARGE);
		if (charge == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - charge.start();
		if (!player.isAlive() || !worn(player) || !player.getMainHandItem().isEmpty() || charge.tendril() != onTendril(player)
			|| WebPower.ragdolled(player) || t > CHARGE_MAX_TICKS) {
			player.removeAttached(CHARGE);
			live(player).pending = NONE;
			return;
		}
		if (t > 0 && t < COURSE_TICKS && t % 3 == 0) {
			// lumps rolling under the suit
			sound(level, player, SoundEvents.SLIME_SQUISH_SMALL, 0.6F, 0.6F + 0.1F * t / 3.0F);
		} else if (t == COURSE_TICKS) {
			sound(level, player, SoundEvents.HONEY_BLOCK_SLIDE, 0.9F, 0.5F);
		} else if (t == COLLECT_TICKS) {
			// the arm swells and splits into its shape
			sound(level, player, SoundEvents.WARDEN_TENDRIL_CLICKS, 1.0F, 0.7F + 0.15F * charge.weapon());
		} else if (t == FORMED_TICKS) {
			boolean heavy = charge.weapon() == MACE || charge.weapon() == HAMMER || charge.weapon() == AXE;
			sound(level, player, heavy ? SoundEvents.ANVIL_PLACE : SoundEvents.TRIDENT_RETURN, 0.5F, heavy ? (charge.weapon() == HAMMER ? 0.4F : 0.5F) : 0.8F);
			if (charge.weapon() == WHIP || charge.weapon() == HOOK) {
				sound(level, player, SoundEvents.CHAIN_PLACE, 0.6F, charge.weapon() == WHIP ? 1.2F : 0.8F);
			}
		}
		Live live = live(player);
		if (t >= FORMED_TICKS && live.pending != NONE) {
			strike(player, charge, live.pending);
		}
	}

	/** It lands: each weapon its own way. */
	private static void land(ServerPlayer player, ServerLevel level, Morph morph, LivingEntity target) {
		int weapon = morph.weapon();
		boolean red = WebSymbioteStrains.of(player) == WebSymbioteStrain.RED;
		float damage = DAMAGE[Mth.clamp(weapon, 0, DAMAGE.length - 1)] * (red ? WebSymbioteStrains.RED_BLADE_DAMAGE : 1.0F);
		float sever = red ? WebSymbioteStrains.RED_SEVER : 1.0F;
		Vec3 away = away(player, target);
		Vec3 at = target.getBoundingBox().getCenter();
		DamageSource source = level.damageSources().playerAttack(player);
		int part = Dismember.aimedPart(player, target);
		switch (weapon) {
			case MACE -> {
				blow(target, level, source, damage);
				target.hurtArmor(source, damage * ARMOR_WEAR);
				breakBone(player, target, part);
				push(target, away.scale(MACE_KNOCK).add(0.0, target.onGround() ? MACE_LIFT : -0.6, 0.0));
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(WebSymbiote.dust(1.6F), at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.1);
				sound(level, target, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 0.9F);
				sound(level, target, SoundEvents.ANVIL_LAND, 0.4F, 0.7F);
				com.jaymospowers.core.Shakes.send(player, 0.9F, at);
			}
			case SCYTHE -> {
				Vec3 right = new Vec3(-away.z, 0.0, away.x).scale(morph.side());
				Dismember.cut(player, target, part, SCYTHE_SEVER * sever, () -> blow(target, level, source, damage));
				push(target, right.scale(-SCYTHE_KNOCK).add(away.scale(0.3)).add(0.0, 0.25, 0.0));
				// the sweep carries on through what's beside it
				for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SCYTHE_SWEEP),
					e -> e != player && e != target && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative())
						&& e.position().subtract(player.position()).horizontal().dot(player.getLookAngle().horizontal()) > 0.0)) {
					Dismember.cut(player, other, Dismember.aimedPart(player, other), SCYTHE_SEVER * 0.5F * sever,
						() -> blow(other, level, source, damage * SCYTHE_SWEEP_SHARE));
					push(other, right.scale(-SCYTHE_KNOCK * 0.6).add(0.0, 0.2, 0.0));
				}
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(WebSymbiote.dust(1.2F), at.x, at.y, at.z, 12, 0.5, 0.2, 0.5, 0.05);
				sound(level, target, SoundEvents.PLAYER_ATTACK_SWEEP, 1.1F, 0.7F);
				sound(level, target, SoundEvents.TRIDENT_HIT, 0.6F, 1.4F);
			}
			case WHIP -> whip(player, level, target, damage, source);
			case HAMMER -> hammer(player, level, target, damage, source, away);
			case HOOK -> hook(player, level, target, damage, source);
			case PALM -> {
				// flat and open: a shove that sends it reeling, nothing broken
				blow(target, level, source, damage);
				push(target, away.scale(PALM_SHOVE).add(0.0, PALM_LIFT, 0.0));
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.08);
				level.sendParticles(WebSymbiote.dust(1.4F), at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.05);
				sound(level, target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1F, 0.7F);
				sound(level, target, SoundEvents.SLIME_SQUISH, 0.8F, 0.6F);
				com.jaymospowers.core.Shakes.send(player, 0.6F, at);
			}
			case AXE -> {
				// a heavy chop: through armour's edge, and it takes limbs
				target.hurtArmor(source, damage * ARMOR_WEAR * 0.5F);
				Dismember.cut(player, target, part, AXE_SEVER * sever, () -> slash(target, level, source, damage));
				push(target, away.scale(AXE_KNOCK).add(0.0, 0.2, 0.0));
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.2, 0.3, 0.2, 0.3);
				level.sendParticles(WebSymbiote.dust(1.3F), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.05);
				sound(level, target, SoundEvents.PLAYER_ATTACK_STRONG, 1.1F, 0.6F);
				sound(level, target, SoundEvents.ANVIL_LAND, 0.3F, 1.3F);
				com.jaymospowers.core.Shakes.send(player, 0.8F, at);
			}
			default -> {
				float before = target.getHealth();
				blow(target, level, source, damage * (1.0F - SPEAR_PIERCE));
				if (target.isAlive()) {
					// straight through: this much ignores armour
					target.setInvulnerableTime(0);
					target.hurtServer(level, pierce(level, player), damage * SPEAR_PIERCE);
				}
				BladeBlood.splash(target, before - target.getHealth());
				target.hurtArmor(source, damage * ARMOR_WEAR);
				push(target, away.scale(SPEAR_KNOCK).add(0.0, 0.2, 0.0));
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.4);
				level.sendParticles(WebSymbiote.dust(1.2F), at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.1);
				sound(level, target, SoundEvents.TRIDENT_HIT, 1.1F, 0.8F);
				com.jaymospowers.core.Shakes.send(player, 0.5F, at);
			}
		}
		sound(level, target, SoundEvents.SLIME_ATTACK, 0.9F, 0.5F);
	}

	/**
	 * The whip-blade: cuts through everything near the line from your eyes through the target, out to its reach, up to
	 * {@link #WHIP_HITS} (each one after the first a little less); the last one it cuts is dragged in toward you.
	 */
	private static void whip(ServerPlayer player, ServerLevel level, LivingEntity target, float damage, DamageSource source) {
		Vec3 eye = player.getEyePosition();
		Vec3 to0 = target.getBoundingBox().getCenter().subtract(eye);
		Vec3 dir = to0.lengthSqr() < 1.0E-4 ? player.getLookAngle() : to0.normalize();
		double length = WHIP_REACH + REACH_SLACK;
		Vec3 end = eye.add(dir.scale(length));
		java.util.List<LivingEntity> along = new java.util.ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
			new AABB(eye, end).inflate(WHIP_WIDTH),
			e -> e != player && e != target && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative())));
		along.removeIf(e -> {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double ahead = to.dot(dir);
			return ahead <= 0.0 || ahead > length || to.subtract(dir.scale(ahead)).length() > WHIP_WIDTH + e.getBbWidth() * 0.5;
		});
		along.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
		java.util.List<LivingEntity> cut = new java.util.ArrayList<>();
		cut.add(target);
		for (LivingEntity e : along) {
			if (cut.size() >= WHIP_HITS) {
				break;
			}
			cut.add(e);
		}
		cut.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
		Vec3 aside = new Vec3(-dir.z, 0.0, dir.x);
		float share = 1.0F;
		for (int i = 0; i < cut.size(); i++) {
			LivingEntity e = cut.get(i);
			Vec3 at = e.getBoundingBox().getCenter();
			e.setInvulnerableTime(0);
			slash(e, level, source, damage * share);
			share *= WHIP_FALLOFF;
			if (i == cut.size() - 1) {
				// the barbs bite and the lash drags it in
				Vec3 back = player.position().subtract(e.position());
				Vec3 flat = new Vec3(back.x, 0.0, back.z);
				Vec3 pull = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(Math.min(WHIP_PULL, flat.length() * 0.3));
				push(e, pull.add(0.0, WHIP_PULL_LIFT, 0.0));
			} else {
				push(e, aside.scale(aside.dot(at.subtract(eye)) >= 0.0 ? WHIP_FLICK : -WHIP_FLICK).add(0.0, 0.15, 0.0));
			}
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.3);
			sound(level, e, SoundEvents.PLAYER_ATTACK_CRIT, 0.9F, 1.5F);
		}
		// the crack of the lash, and a line of flecks down its length
		double far = cut.get(cut.size() - 1).getBoundingBox().getCenter().distanceTo(eye);
		for (int k = 1; k <= 6; k++) {
			Vec3 p = eye.add(dir.scale(far * k / 6.0));
			level.sendParticles(WebSymbiote.dust(0.9F), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.02);
		}
		sound(level, target, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F, 0.7F);
		sound(level, target, SoundEvents.CHAIN_BREAK, 0.7F, 1.3F);
		com.jaymospowers.core.Shakes.send(player, 0.4F, target.getBoundingBox().getCenter());
	}

	/** The hammer-fist: a huge blow from below, thrown high and stunned, the ground under it bursting. */
	private static void hammer(ServerPlayer player, ServerLevel level, LivingEntity target, float damage, DamageSource source, Vec3 away) {
		Vec3 at = target.getBoundingBox().getCenter();
		blow(target, level, source, damage);
		push(target, away.scale(HAMMER_KNOCK).add(0.0, HAMMER_LIFT, 0.0));
		if (target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			// dazed: it can barely move or hit back for a moment
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HAMMER_STUN_TICKS, HAMMER_SLOW_LEVEL, false, false), player);
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HAMMER_STUN_TICKS, HAMMER_WEAK_LEVEL, false, false), player);
		}
		BlockState ground = level.getBlockState(target.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), target.getX(), target.getY() + 0.1, target.getZ(), HAMMER_DUST,
				0.5, 0.1, 0.5, 0.15);
		}
		level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.1, target.getZ(), 6, 0.4, 0.05, 0.4, 0.04);
		level.sendParticles(WebSymbiote.dust(1.6F), at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.1);
		sound(level, target, SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.6F);
		sound(level, target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
		sound(level, target, SoundEvents.ANVIL_LAND, 0.35F, 0.5F);
		com.jaymospowers.core.Shakes.send(player, 0.8F, at);
	}

	/** The hook claw: hooks in, reels what it caught in to you, and leaves it bleeding a while. */
	private static void hook(ServerPlayer player, ServerLevel level, LivingEntity target, float damage, DamageSource source) {
		Vec3 at = target.getBoundingBox().getCenter();
		slash(target, level, source, damage);
		Vec3 back = player.position().subtract(target.position());
		double dist = back.length();
		if (dist > 1.5) {
			// reeled in: it stops short of you, not through you
			Vec3 pull = back.normalize().scale(Math.min(HOOK_YANK_MAX, (dist - 1.2) * HOOK_YANK));
			push(target, new Vec3(pull.x, Math.max(pull.y, 0.0) + HOOK_LIFT, pull.z));
		}
		if (target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			Live live = live(player);
			live.bleeds.removeIf(b -> b[0] == target.getId());
			live.bleeds.add(new long[] {target.getId(), level.getGameTime() + BLEED_EVERY, BLEED_HITS});
		}
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.3);
		level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(BLOOD_COLOR, 1.2F), at.x, at.y, at.z, 12, 0.2, 0.25, 0.2, 0.05);
		sound(level, target, SoundEvents.TRIDENT_HIT, 1.0F, 1.2F);
		sound(level, target, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F, 0.6F);
		sound(level, target, SoundEvents.CHAIN_BREAK, 0.6F, 0.8F);
		com.jaymospowers.core.Shakes.send(player, 0.5F, at);
	}

	/** {@code target} bleeds {@code hits} drops, as from the hook claw (the red strain's blade burst). */
	static void bleed(ServerPlayer player, LivingEntity target, int hits) {
		if (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		Live live = live(player);
		live.bleeds.removeIf(b -> b[0] == target.getId());
		live.bleeds.add(new long[] {target.getId(), player.level().getGameTime() + BLEED_EVERY, hits});
	}

	/** The hook claw's wounds: a drop of damage now and then, dripping red, until they've bled out (or it died). */
	private static void tickBleeds(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null || live.bleeds.isEmpty()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		java.util.Iterator<long[]> it = live.bleeds.iterator();
		while (it.hasNext()) {
			long[] b = it.next();
			if (b[2] <= 0 || !(level.getEntity((int)b[0]) instanceof LivingEntity bleeding) || !bleeding.isAlive()) {
				it.remove();
				continue;
			}
			if (now % 3 == 0) {
				level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(BLOOD_COLOR, 0.8F), bleeding.getX(), bleeding.getY() + bleeding.getBbHeight() * 0.55,
					bleeding.getZ(), 2, 0.15, 0.2, 0.15, 0.0);
			}
			if (now >= b[1]) {
				bleeding.setInvulnerableTime(0);
				float before = bleeding.getHealth();
				bleeding.hurtServer(level, pierce(level, player), BLEED_DAMAGE);
				BladeBlood.splash(bleeding, before - bleeding.getHealth());
				b[1] = now + BLEED_EVERY;
				b[2]--;
			}
		}
	}

	/** Armour-piercing damage from you (the spear's thrust through). */

	private static DamageSource pierce(ServerLevel level, ServerPlayer player) {
		return new DamageSource(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
			.getOrThrow(net.minecraft.world.damagesource.DamageTypes.MAGIC), player, player);
	}

	private static void blow(LivingEntity target, ServerLevel level, DamageSource source, float damage) {
		target.hurtServer(level, source, damage);
	}

	/** A blade's blow (the whip-blade, the hook claw): it bleeds ({@link BladeBlood}). */
	private static void slash(LivingEntity target, ServerLevel level, DamageSource source, float damage) {
		float before = target.getHealth();
		blow(target, level, source, damage);
		BladeBlood.splash(target, before - target.getHealth());
	}

	/** The mace: the bone it lands on breaks (a skeleton's shatters), at {@link #MACE_BREAK}. */
	private static void breakBone(ServerPlayer player, LivingEntity target, int part) {
		if (!target.isAlive() || !Dismember.canLoseParts(target) || target instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		boolean bones = target.getType().builtInRegistryHolder().is(EntityTypeTags.SKELETONS);
		float chance = target instanceof Player ? Fractures.PLAYER_CHANCE * 2.0F : bones ? Math.min(1.0F, MACE_BREAK * 2.0F) : MACE_BREAK;
		if (target.getRandom().nextFloat() >= chance) {
			return;
		}
		if (bones) {
			Dismember.shatter(target, part, player);
		} else {
			Fractures.breakBone(target, part, player);
		}
	}

	private static Vec3 away(Entity from, Entity to) {
		Vec3 d = to.position().subtract(from.position());
		Vec3 flat = new Vec3(d.x, 0.0, d.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = from.getLookAngle().horizontal();
		}
		return flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
	}

	private static void push(Entity target, Vec3 velocity) {
		target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(velocity));
		target.needsSync = true;
		target.syncVelocity = true;
		if (target instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(other.getDeltaMovement()));
		}
	}

	private static void sound(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch * Mth.nextFloat(level.getRandom(), 0.92F, 1.08F));
	}
}
