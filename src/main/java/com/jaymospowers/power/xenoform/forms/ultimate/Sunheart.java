package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.compat.Stacked;
import com.jaymospowers.core.Flames;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.21) Pyre's Ultimate, Sunheart: the living flame grown a quarter bigger, crusted in plates of cooled basalt with white-yellow
 * cracks, a crown of flame-tipped spikes on the head, and a health bar of its own on top of Pyre's. Its model is {@code UltimateSunheart}.
 * Fire and lava roll off it; water and rain hurt it, as they hurt Pyre.
 * <ul>
 * <li>R, Sunray: a held beam of fire; it burns the creature it hits, or lights the block it ends on.</li>
 * <li>G, Sun Jet: the Flame Jet at the Sunheart's streak ({@link XenoformState#FLY_SUN}); everything it passes close by is set alight.</li>
 * <li>V, Blaze Nova: a wider, harder Fire Nova.</li>
 * <li>B, Lava Wake: every step leaves fire and a magma block that turns back after a few seconds; whoever stands in it burns.</li>
 * <li>C, Corona: you flare like a small sun: everything near burns, players are blinded, snow and ice melt, a ring of fireballs flies out.</li>
 * </ul>
 */
public final class Sunheart extends XenoUltimate {
	// ---- tuning ----
	/** Sunray: reach, damage every {@link #BEAM_EVERY} ticks, the burn it leaves, and the food it costs a bite. */
	public static final double BEAM_RANGE = 16.0;
	public static final float BEAM_DAMAGE = 3.0F;
	public static final int BEAM_EVERY = 4, BEAM_BURN_SECONDS = 3;
	/** Sun Jet: slow fall for this long after it ends in the air; everything within this radius of the comet is set alight (seconds) every few ticks while it moves faster than the minimum (blocks a tick). */
	public static final int JET_SLOW_FALL_TICKS = 50, JET_IGNITE_EVERY = 4, JET_BURN_SECONDS = 4;
	public static final double JET_IGNITE_RADIUS = 2.0, JET_IGNITE_MIN_SPEED = 0.3;
	/** Blaze Nova: cooldown, radius, damage, burn, the screen shake (degrees) and how far it is felt. */
	public static final int NOVA_COOLDOWN = 200, NOVA_BURN_SECONDS = 8;
	public static final double NOVA_RADIUS = 7.0, NOVA_SHAKE_RANGE = 18.0;
	public static final float NOVA_DAMAGE = 8.0F, NOVA_SHAKE = 1.2F;
	/** Lava Wake: cooldown, how long it lasts, how long a magma block stays before the old block comes back (all ticks), the burn on whoever stands in it and how often it bites. */
	public static final int WAKE_COOLDOWN = 300, WAKE_TICKS = 160, WAKE_RESTORE_TICKS = 100, WAKE_BURN_SECONDS = 4, WAKE_BURN_EVERY = 10;
	/** The most magma blocks one Sunheart has out at once. */
	public static final int WAKE_MAX_BLOCKS = 400;
	/** Corona: cooldown (30 s), reach, damage, burn, the blindness players get (ticks), how far snow and ice melt, the fireballs in the ring and how fast they fly. */
	public static final int CORONA_COOLDOWN = 600, CORONA_BURN_SECONDS = 8, CORONA_BLIND_TICKS = 40, CORONA_FIREBALLS = 12;
	public static final double CORONA_RADIUS = 10.0, CORONA_MELT_RADIUS = 6.0, CORONA_FIREBALL_SPEED = 0.9, CORONA_FIREBALL_OUT = 1.4;
	public static final float CORONA_DAMAGE = 10.0F;
	/** Screen shake (degrees, and how far it is felt): the change-in and the Corona. */
	public static final float SUN_SHAKE = 1.6F;
	public static final double SUN_SHAKE_RANGE = 24.0;
	/** In water or rain: this much damage every this many ticks. */
	public static final float WET_DAMAGE = 1.0F;
	public static final int WET_EVERY = 20;

	/** A block turned to magma by the wake: what it was, and when it goes back. */
	private static final class Wake {
		final ServerLevel level;
		final BlockPos pos;
		final BlockState old;
		final long until;

		Wake(ServerLevel level, BlockPos pos, BlockState old, long until) {
			this.level = level;
			this.pos = pos;
			this.old = old;
			this.until = until;
		}
	}

	/** What each Sunheart has under way (server thread only): its magma blocks, and where it was last tick. */
	private static final class Work {
		final List<Wake> wakes = new ArrayList<>();
		Vec3 last;
	}

	private static final Map<UUID, Work> WORK = new HashMap<>();
	/** (0.20.21) Each wake magma block's caster, so the block's burn can be held back from anyone the caster may not hurt. */
	private static final Map<net.minecraft.core.GlobalPos, UUID> MAGMA = new HashMap<>();
	/** (0.20.21) Marks the Corona's fireballs. */
	private static final String CORONA_TAG = "jaymospowers_corona";

	static {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// (0.20.21) the wake's magma and the Corona's fireballs never hurt pets, teammates or a player the caster may not hurt
			if (source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR) && !MAGMA.isEmpty() && entity.level() instanceof ServerLevel level) {
				UUID owner = MAGMA.get(net.minecraft.core.GlobalPos.of(level.dimension(), entity.blockPosition().below()));
				ServerPlayer caster = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
				if (owner != null && (caster == null || entity == caster || spared(caster, entity) || !harmable(caster, entity))) {
					return false;
				}
			} else if (source.getDirectEntity() instanceof SmallFireball ball && ball.entityTags().contains(CORONA_TAG)
				&& ball.getOwner() instanceof ServerPlayer caster && (entity == caster || spared(caster, entity) || !harmable(caster, entity))) {
				return false;
			}
			return true;
		});
		// the server going down mid-wake must not leave magma behind
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Work work : WORK.values()) {
				restoreAll(work);
			}
			WORK.clear();
			MAGMA.clear();
		});
	}

	public Sunheart(XenoForm base) {
		super(base, 1.25F, 20.0F, 6.0, 0.0, 0.0, 0.0);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.BLAZE_ROD, Items.FIRE_CHARGE, Items.MAGMA_CREAM, Items.MAGMA_BLOCK, Items.GLOWSTONE};
	}

	/** The change-in film zooms on these in turn (model part names): the arms, the sun in the chest, the hair, the crown. */
	@Override
	public String[] morphParts() {
		return new String[] {"right_arm", "left_arm", "core", "hair", "crown"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.trailTicks = 0; // (0.20.21) Pyre's Magma Trail does not carry over as a free Lava Wake
		if (!player.onGround()) {
			// (0.20.21) a change-in mid-jet must not drop you out of the sky
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, JET_SLOW_FALL_TICKS, 0, false, false));
		}
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		ring(level, ParticleTypes.FLAME, at, 2.0, 24);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.0);
		sound(player, SoundEvents.BLAZE_AMBIENT, 2.0F, 0.6F);
		sound(player, SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
		Shakes.heavy(player, at, SUN_SHAKE_RANGE, SUN_SHAKE);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.jet = false;
		live.beam = false;
		live.trailTicks = 0;
		player.removeEffect(MobEffects.LEVITATION);
		if (!player.onGround()) {
			// (0.20.21) dropping or breaking mid-jet gives a soft landing like ending the jet does
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, JET_SLOW_FALL_TICKS, 0, false, false));
		}
		Work work = WORK.remove(player.getUUID());
		if (work != null) {
			restoreAll(work);
		}
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_1) {
			// Sunray: held
			if (down != live.beam) {
				live.beam = down;
				if (down) {
					sound(player, SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
					cooldown(player, live, R, 1);
				}
			}
			return;
		}
		if (key == PowerKey.ABILITY_2) {
			// a toggle, steered on the client like Pyre's: G takes off, G again ends it
			if (down) {
				if (live.jet) {
					endJet(player, live);
				} else {
					live.jet = true;
					cooldown(player, live, G, 1);
					sound(player, SoundEvents.BLAZE_SHOOT, 0.8F, 0.5F);
				}
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					nova(player);
					cooldown(player, live, V, NOVA_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.trailTicks = WAKE_TICKS;
					sound(player, SoundEvents.BLAZE_BURN, 1.0F, 0.5F);
					cooldown(player, live, B, WAKE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: Corona. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		if (!ready(player, live, C)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (LivingEntity living : around(player, CORONA_RADIUS)) {
			// (0.20.21) burn and blind only when the hit landed (pvp off or a blocked hit does neither)
			if (living.hurtServer(level, player.damageSources().playerAttack(player), CORONA_DAMAGE)) {
				living.igniteForSeconds(CORONA_BURN_SECONDS);
				if (living instanceof ServerPlayer hit) {
					hit.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, CORONA_BLIND_TICKS, 0));
				}
			}
		}
		Flames.scare(level, new AABB(centre, centre).inflate(CORONA_RADIUS));
		melt(player, CORONA_MELT_RADIUS);
		// a flat ring of fireballs
		for (int i = 0; i < CORONA_FIREBALLS; i++) {
			double a = i / (double)CORONA_FIREBALLS * Math.PI * 2.0;
			Vec3 out = new Vec3(Math.cos(a), 0.0, Math.sin(a));
			if (!player.mayBuild() || !level.mayInteract(player, player.blockPosition())) {
				break; // (0.20.21) no fire-lighting ring in adventure mode or spawn protection
			}
			SmallFireball ball = new SmallFireball(level, player, out.scale(CORONA_FIREBALL_SPEED));
			ball.addTag(CORONA_TAG);
			Vec3 from = centre.add(out.scale(CORONA_FIREBALL_OUT));
			ball.setPos(from.x, from.y, from.z);
			level.addFreshEntity(ball);
		}
		MagicFx.blast(level, centre.x, centre.y, centre.z, MagicFx.FLASH_RADIUS, 0xFFC040);
		level.sendParticles(ParticleTypes.FLAME, centre.x, centre.y, centre.z, 80, 1.2, 1.0, 1.2, 0.25);
		ring(level, ParticleTypes.FLAME, player.position(), CORONA_RADIUS * 0.5, 32);
		ring(level, ParticleTypes.FLAME, player.position(), CORONA_RADIUS, 48);
		ring(level, ParticleTypes.LAVA, player.position(), CORONA_MELT_RADIUS, 20);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
		sound(player, SoundEvents.BLAZE_SHOOT, 1.5F, 0.4F);
		sound(player, SoundEvents.FIRECHARGE_USE, 1.2F, 0.6F);
		Shakes.heavy(player, centre, SUN_SHAKE_RANGE, SUN_SHAKE);
		cooldown(player, live, C, CORONA_COOLDOWN);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Work work = WORK.get(player.getUUID());
		// the jet: the client flies it (PyreFlightClient); everyone's client draws the comet (PyreComet)
		if (live.jet) {
			player.resetFallDistance();
			if (player.onGround() && player.isShiftKeyDown()) {
				endJet(player, live);
			} else {
				if (player.tickCount % 8 == 0) {
					sound(player, SoundEvents.FIRE_AMBIENT, 0.8F, 1.0F);
				}
				if (!player.onGround() && player.tickCount % JET_IGNITE_EVERY == 0) {
					Vec3 here = player.position();
					Work w = work != null ? work : new Work();
					boolean fast = w.last != null && here.distanceTo(w.last) / JET_IGNITE_EVERY > JET_IGNITE_MIN_SPEED;
					w.last = here;
					if (work == null) {
						work = w;
						WORK.put(player.getUUID(), w);
					}
					if (fast) {
						Flames.area(level, player.position().add(0.0, player.getBbHeight() * 0.5, 0.0), JET_IGNITE_RADIUS, JET_BURN_SECONDS, player,
							e -> e instanceof LivingEntity && !(e instanceof ArmorStand) && !spared(player, e) && harmable(player, e));
					}
				}
			}
		}
		if (live.beam) {
			beam(player);
		}
		// the wake
		if (live.trailTicks > 0) {
			live.trailTicks--;
			if (player.onGround() && player.tickCount % 2 == 0) {
				if (work == null) {
					work = new Work();
					WORK.put(player.getUUID(), work);
				}
				lay(player, work);
			}
		}
		if (work != null) {
			if (!work.wakes.isEmpty()) {
				wakeTick(player, work, now);
			}
			if (work.wakes.isEmpty() && !live.jet) {
				WORK.remove(player.getUUID());
			}
		}
		// the sun inside: it glows, and sheds flame as it walks
		if (player.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 1.2, player.getZ(), 2, 0.45, 0.7, 0.45, 0.02);
		}
		if (player.onGround() && player.getKnownMovement().horizontalDistanceSqr() > 0.004 && player.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.25, 0.0, 0.25, 0.02);
		}
		if (player.isInWaterOrRain() && !player.isCreative()) {
			if (player.tickCount % 5 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
			}
			if (player.tickCount % WET_EVERY == 0) {
				player.hurtServer(level, player.damageSources().drown(), WET_DAMAGE);
				sound(player, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.2F);
			}
		}
	}

	private static void endJet(ServerPlayer player, XenoformPower.Live live) {
		live.jet = false;
		player.removeEffect(MobEffects.LEVITATION);
		if (!player.onGround()) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, JET_SLOW_FALL_TICKS, 0, false, false));
		}
	}

	/** Fire and lava roll off it. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			player.clearFire();
			return false;
		}
		return true;
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.jet || player.hasEffect(MobEffects.SLOW_FALLING);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.jet;
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return !live.jet || player.onGround() ? XenoformState.FLY_NONE : XenoformState.FLY_SUN;
	}

	// ---- R: Sunray ----

	/** The beam, every tick it is held: a line of fire from your hands to the first thing it meets. */
	private static void beam(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(BEAM_RANGE));
		var blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() == HitResult.Type.BLOCK) {
			end = blockHit.getLocation();
		}
		var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, player, eye, end,
			player.getBoundingBox().expandTowards(look.scale(BEAM_RANGE)).inflate(1.0),
			e -> e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator() && !(e instanceof ArmorStand) && !spared(player, e), 0.5F);
		if (entityHit != null) {
			end = entityHit.getLocation();
		}
		double length = end.distanceTo(eye);
		for (double d = 0.5; d < length; d += 0.5) {
			Vec3 p = eye.add(look.scale(d));
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.0);
		}
		if (player.tickCount % BEAM_EVERY == 0) {
			if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
				if (target.hurtServer(level, player.damageSources().playerAttack(player), BEAM_DAMAGE)) {
					target.igniteForSeconds(BEAM_BURN_SECONDS); // (0.20.21) only when the hit landed
				}
				target.hurtTime = 0;
			} else if (blockHit.getType() == HitResult.Type.BLOCK) {
				light(player, level, blockHit.getBlockPos().relative(blockHit.getDirection()));
			}
			player.causeFoodExhaustion(0.02F);
		}
		if (player.tickCount % 6 == 0) {
			sound(player, SoundEvents.BLAZE_BURN, 0.6F, 1.5F);
		}
	}

	/** (0.20.21) A player the attacker may not hurt (pvp off) is never set alight. */
	private static boolean harmable(ServerPlayer player, Entity e) {
		return !(e instanceof ServerPlayer other) || player.canHarmPlayer(other);
	}

	/** Sets {@code pos} alight if fire can stand there. */
	private static void light(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!player.mayBuild() || !level.mayInteract(player, pos)) {
			return; // (0.20.21) no fire in adventure mode or spawn protection
		}
		if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.NORTH)) {
			level.setBlock(pos, BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
		}
	}

	// ---- V: Blaze Nova ----

	private static void nova(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 0.5, 0.0);
		for (LivingEntity living : around(player, NOVA_RADIUS)) {
			if (living.hurtServer(level, player.damageSources().playerAttack(player), NOVA_DAMAGE) || !(living instanceof ServerPlayer)) { // (0.20.21) no shove for a player the hit missed (pvp off)
				shove(living, player.position(), 0.9, 0.4);
			}
		}
		Flames.area(level, at, NOVA_RADIUS, NOVA_BURN_SECONDS, player,
			e -> e != player && !spared(player, e) && harmable(player, e));
		ring(level, ParticleTypes.FLAME, at, NOVA_RADIUS * 0.4, 24);
		ring(level, ParticleTypes.FLAME, at, NOVA_RADIUS * 0.7, 36);
		ring(level, ParticleTypes.FLAME, at, NOVA_RADIUS, 48);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 30, 1.4, 0.6, 1.4, 0.0);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.2F);
		sound(player, SoundEvents.BLAZE_SHOOT, 1.2F, 0.5F);
		Shakes.heavy(player, at, NOVA_SHAKE_RANGE, NOVA_SHAKE);
	}

	// ---- B: Lava Wake ----

	/** Under your feet: fire above, and the block itself turns to magma until its time is up. */
	private static void lay(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
		BlockPos above = below.above();
		BlockState aboveState = level.getBlockState(above);
		boolean clearAbove = aboveState.isAir() || aboveState.is(BlockTags.FIRE); // (0.20.21) never pop plants off the ground
		light(player, level, above);
		level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.25, 0.0, 0.25, 0.0);
		BlockState state = level.getBlockState(below);
		if (!clearAbove || work.wakes.size() >= WAKE_MAX_BLOCKS || state.is(Blocks.MAGMA_BLOCK) || state.hasBlockEntity() || !state.getFluidState().isEmpty()
			|| !softGround(state) || !state.isCollisionShapeFullBlock(level, below) || !level.mayInteract(player, below) || !player.mayBuild()) {
			return;
		}
		level.setBlock(below, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		work.wakes.add(new Wake(level, below, state, level.getGameTime() + WAKE_RESTORE_TICKS));
		MAGMA.put(net.minecraft.core.GlobalPos.of(level.dimension(), below), player.getUUID());
	}

	/** (0.20.21) Only plain ground turns to magma: dirt, sand, stone and the like, never ores, metal blocks or anything worth keeping. */
	private static boolean softGround(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
			|| state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.SANDSTONE)
			|| state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.NETHERRACK);
	}

	/** Old blocks come back as their time is up; anything standing in the wake burns. */
	private static void wakeTick(ServerPlayer player, Work work, long now) {
		ServerLevel level = player.level();
		boolean burn = player.tickCount % WAKE_BURN_EVERY == 0;
		for (Iterator<Wake> it = work.wakes.iterator(); it.hasNext();) {
			Wake wake = it.next();
			if (now >= wake.until) {
				restore(wake);
				it.remove();
			} else if (burn && wake.level == level) {
				Flames.area(level, new AABB(wake.pos.above()).expandTowards(0.0, 0.5, 0.0), WAKE_BURN_SECONDS, player,
					e -> e instanceof LivingEntity && !(e instanceof ArmorStand) && !spared(player, e) && harmable(player, e));
			}
		}
	}

	private static void restore(Wake wake) {
		MAGMA.remove(net.minecraft.core.GlobalPos.of(wake.level.dimension(), wake.pos));
		if (wake.level.getBlockState(wake.pos).is(Blocks.MAGMA_BLOCK)) {
			wake.level.setBlock(wake.pos, wake.old, Block.UPDATE_ALL);
		}
	}

	private static void restoreAll(Work work) {
		for (Wake wake : work.wakes) {
			restore(wake);
		}
		work.wakes.clear();
	}

	// ---- C: Corona ----

	/** Snow and ice within {@code radius} melt: snow goes, ice turns to water (to nothing in the Nether). */
	private static void melt(ServerPlayer player, double radius) {
		ServerLevel level = player.level();
		BlockPos centre = player.blockPosition();
		int r = (int)Math.ceil(radius);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dy * dy + dz * dz > radius * radius) {
						continue;
					}
					pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					BlockState state = level.getBlockState(pos);
					boolean snow = state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
					boolean ice = state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE);
					if ((snow || ice) && level.mayInteract(player, pos) && player.mayBuild()) {
						level.setBlock(pos, ice && !Stacked.at(level, pos, Level.NETHER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
							Block.UPDATE_ALL);
						if (level.getRandom().nextInt(3) == 0) {
							level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.02);
						}
					}
				}
			}
		}
	}
}
