package com.jaymoskingdoms.dragons;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.Heritage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.522) Aetherion, the star dragon of the Aether. Andy sent an Aetherion (a public-domain, CC0 GeckoLib dragon) to
 * add, with its wings and head upgraded: our own model ({@code kingdoms/art/aetherion.bbmodel}, written by
 * {@code kingdoms/tools/gen_aetherion_geo.py}) on its body plan, its numbers and moves after it. It flies over the
 * Aether's islands ({@link Dragons}, Andy: "Aether sky
 * dragon, plus Nulljaw", who is the Aether's boss) and leaves players be until one hurts it. Then, besides the swoop, the
 * hover and the tail sweep every dragon has:
 * <ul>
 * <li><b>Bite</b> ({@link #BITE_MOVE}): the neck darting in, the jaws snapping.</li>
 * <li><b>Breath beam:</b> its breath is a straight beam of starlight ({@link #BEAM_RANGE} blocks, stopped by blocks),
 * hurting everything along it; it never burns anything.</li>
 * <li><b>Cataclysm</b> ({@link #CATACLYSM_MOVE}): worn below half its health, now and then it rears up roaring with its wings
 * flung high, and stars fall round it: each lands where a glowing ring has warned of it, hurting and throwing whoever's
 * there. No block is touched.</li>
 * </ul>
 * Glow berries are its favourite; Nightborn win it over twice as fast; ridden, R is the beam.
 */
public class AetherionDragon extends WindDragon implements GeoEntity {
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	// ---- body ----
	static final double HEALTH = 420.0, ARMOR = 12.0, TOUGHNESS = 5.0, SPEED = 0.25, BITE = 18.0;
	/** Wild: a fast, light flier for its size. */
	static final double CRUISE = 0.7, SWOOP = 1.45;
	/** Its riders' seat and its mouth (the model drawn at 1.25: the back 3.7 blocks up, the head about 5.6). */
	static final double SEAT_HEIGHT = 3.65, SEAT_FRONT = 0.3, SEAT_BACK = -0.6;
	static final double MOUTH_AHEAD_GROUND = 4.4, MOUTH_UP_GROUND = 5.3, MOUTH_AHEAD_AIR = 5.4, MOUTH_UP_AIR = 3.6;
	static final float FLAP_DRAIN = 1.3F, GLIDE_DRAIN = 0.15F;

	// ---- the beam ----
	/** How far it reaches (blocks), how near the line a creature must be to be struck, its damage, and how often it strikes (ticks). */
	static final double BEAM_RANGE = 28.0, BEAM_WIDTH = 1.3;
	static final float BEAM_DAMAGE = 7.0F;
	static final int BEAM_EVERY = 4;

	// ---- the moves ----
	public static final int NONE = 0, BITE_MOVE = 1, CATACLYSM_MOVE = 2;
	/** How long each move lasts (ticks); the Cataclysm's stars begin falling at this tick. */
	static final int[] ACTION_TICKS = {0, 14, 70};
	static final int CATACLYSM_FALL = 38;
	/** The bite: how often it's shown (ticks) when its foe is in reach. */
	static final int BITE_EVERY = 30;
	static final double BITE_REACH = 4.5;
	/** The Cataclysm: below this share of its health; how often (ticks); how many stars, how far round it (blocks), how long each one's warning (ticks), its blast's reach, damage and throw. */
	static final float CATACLYSM_BELOW = 0.5F;
	static final int CATACLYSM_EVERY = 500, STARS = 14, STAR_WARNING = 20;
	static final double STAR_SPREAD = 14.0, STAR_RADIUS = 2.8, STAR_THROW = 1.0;
	static final float STAR_DAMAGE = 14.0F;
	/** How far the Cataclysm's warning reaches (blocks). */
	static final double MESSAGE_RADIUS = 64.0;

	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(AetherionDragon.class, EntityDataSerializers.INT);

	private int move = NONE, moveStart, biteIn, cataclysmIn = CATACLYSM_EVERY / 3;
	private @Nullable LivingEntity moveTarget;
	/** Stars still to land: where and when (ticks from now). */
	private final List<Star> stars = new ArrayList<>();

	private static final class Star {
		final Vec3 at;
		int in;

		Star(Vec3 at, int in) {
			this.at = at;
			this.in = in;
		}
	}

	public AetherionDragon(EntityType<? extends AetherionDragon> type, Level level) {
		super(type, level);
		this.xpReward = 120;
	}

	/** Drawn and animated by GeckoLib from our editable Blockbench model ({@link AetherionAnimations}). */
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AetherionAnimations.register(controllers);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, NONE);
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return stack.is(Items.GLOW_BERRIES);
	}

	@Override
	protected float bondFactor(Player player) {
		return Heritage.is(player, Heritage.Kind.NIGHTBORN) ? BOND_FACTOR : 1.0F;
	}

	/** It leaves players be until one hurts it. */
	@Override
	protected boolean aggressive() {
		return false;
	}

	@Override
	protected boolean windDragon() {
		return false;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	@Override
	protected double cruiseSpeed() {
		return CRUISE;
	}

	@Override
	protected double swoopSpeed() {
		return SWOOP;
	}

	@Override
	protected float flapDrain() {
		return FLAP_DRAIN;
	}

	@Override
	protected float glideDrain() {
		return GLIDE_DRAIN;
	}

	@Override
	protected float flapVolume() {
		return 2.5F;
	}

	@Override
	protected double breathRange() {
		return BEAM_RANGE;
	}

	@Override
	protected double mouthAhead(boolean flying) {
		return flying ? MOUTH_AHEAD_AIR : MOUTH_AHEAD_GROUND;
	}

	@Override
	protected double mouthUp(boolean flying) {
		return flying ? MOUTH_UP_AIR : MOUTH_UP_GROUND;
	}

	@Override
	protected Vec3 seat(int index) {
		return new Vec3(0.0, SEAT_HEIGHT, index == 0 ? SEAT_FRONT : SEAT_BACK);
	}

	@Override
	public int action() {
		return this.entityData.get(DATA_ACTION);
	}

	/** How far through {@code action} its model is after {@code age} ticks (0 to 1). */
	public static float actionShare(int action, float age) {
		int length = action <= 0 || action >= ACTION_TICKS.length ? 1 : ACTION_TICKS[action];
		return Mth.clamp(age / length, 0.0F, 1.0F);
	}

	private void begin(int move, @Nullable LivingEntity target) {
		this.move = move;
		this.moveStart = this.tickCount;
		this.moveTarget = target;
		this.entityData.set(DATA_ACTION, move);
		if (move == BITE_MOVE) {
			this.triggerAnim("action", "bite");
		} else if (move == CATACLYSM_MOVE) {
			this.triggerAnim("action", "cataclysm");
		}
	}

	@Override
	protected void bitten(LivingEntity target) {
		if (this.move == NONE) {
			this.triggerAnim("action", "bite");
		}
	}

	@Override
	protected void swept() {
		this.triggerAnim("action", "sweep");
	}

	// ---- the beam ----

	/** A straight beam of starlight from its mouth along {@code aim}, stopped by the first block. */
	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
		Vec3 far = mouth.add(aim.scale(BEAM_RANGE));
		HitResult hit = level.clip(new ClipContext(mouth, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
		double length = end.distanceTo(mouth);
		for (double d = 0.0; d < length; d += 0.6) {
			Vec3 p = mouth.add(aim.scale(d));
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			if (this.random.nextInt(4) == 0) {
				level.sendParticles(ParticleTypes.GLOW, p.x, p.y, p.z, 1, 0.15, 0.15, 0.15, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 3, 0.2, 0.2, 0.2, 0.08);
		if (this.tickCount % BEAM_EVERY != 0) {
			return;
		}
		if (this.tickCount % (BEAM_EVERY * 4) == 0) {
			level.playSound(null, BlockPos.containing(mouth), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2.5F, 1.6F);
		}
		AABB box = new AABB(mouth, end).inflate(BEAM_WIDTH);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> spareable(e) && !(e instanceof WindDragon))) {
			Vec3 c = e.getBoundingBox().getCenter();
			double along = Mth.clamp(c.subtract(mouth).dot(aim), 0.0, length);
			if (c.distanceTo(mouth.add(aim.scale(along))) < BEAM_WIDTH + e.getBbWidth() * 0.5) {
				e.hurtServer(level, this.damageSources().indirectMagic(this, this), BEAM_DAMAGE);
			}
		}
	}

	// ---- the fight ----

	@Override
	protected void fight(ServerLevel level, LivingEntity target) {
		if (this.move == CATACLYSM_MOVE) {
			// it holds where it is, rearing up (or hovering) while the stars fall
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().scale(0.6));
			this.getLookControl().setLookAt(target);
			return;
		}
		super.fight(level, target);
		double distance = this.distanceTo(target) - this.getBbWidth() * 0.5;
		if (--this.biteIn <= 0 && this.move == NONE && distance < BITE_REACH) {
			this.biteIn = BITE_EVERY;
			begin(BITE_MOVE, target);
		}
		if (--this.cataclysmIn <= 0 && this.move == NONE && this.getHealth() < this.getMaxHealth() * CATACLYSM_BELOW && distance < STAR_SPREAD * 2.0) {
			this.cataclysmIn = CATACLYSM_EVERY + this.random.nextInt(200);
			begin(CATACLYSM_MOVE, target);
			level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 1.3F);
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(this) < MESSAGE_RADIUS * MESSAGE_RADIUS) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.aetherion.cataclysm").withStyle(ChatFormatting.AQUA));
				}
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.move != NONE) {
			int age = this.tickCount - this.moveStart;
			if (this.move == CATACLYSM_MOVE && age == CATACLYSM_FALL) {
				cataclysm(level);
			}
			if (this.move == CATACLYSM_MOVE && age < CATACLYSM_FALL && age % 4 == 0) {
				// gathering: light drawn in from the sky about it
				level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 6.0, this.getZ(), 12, 4.0, 3.0, 4.0, 0.05);
			}
			if (age >= ACTION_TICKS[this.move]) {
				this.move = NONE;
				this.moveTarget = null;
				this.entityData.set(DATA_ACTION, NONE);
			}
		}
		for (int i = this.stars.size() - 1; i >= 0; i--) {
			Star star = this.stars.get(i);
			star.in--;
			if (star.in > 0 && star.in <= STAR_WARNING && star.in % 3 == 0) {
				ring(level, star.at);
			}
			if (star.in == 6) {
				// the star itself, streaking down
				level.sendParticles(ParticleTypes.END_ROD, star.at.x, star.at.y + 12.0, star.at.z, 20, 0.2, 6.0, 0.2, 0.0);
			}
			if (star.in <= 0) {
				land(level, star.at);
				this.stars.remove(i);
			}
		}
	}

	/** The stars of the Cataclysm: on its foe (and those about it), then scattered round it, each a little after the last. */
	private void cataclysm(ServerLevel level) {
		level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.8F);
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(STAR_SPREAD * 1.5),
			e -> spareable(e) && !(e instanceof WindDragon) && (e instanceof Player || e == this.moveTarget || e == this.getTarget()));
		int n = 0;
		for (LivingEntity e : foes) {
			if (n >= STARS / 2) {
				break;
			}
			this.stars.add(new Star(ground(level, e.position()), STAR_WARNING + n * 3));
			n++;
		}
		for (; n < STARS; n++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double dist = 4.0 + this.random.nextDouble() * STAR_SPREAD;
			Vec3 at = this.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
			this.stars.add(new Star(ground(level, at), STAR_WARNING + n * 3));
		}
	}

	/** Where a star lands on the ground under (or about) {@code at}. */
	private static Vec3 ground(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y + 2.0, at.z);
		for (int i = 0; i < 24; i++, pos = pos.below()) {
			if (level.getBlockState(pos).isSolid()) {
				return new Vec3(at.x, pos.getY() + 1.0, at.z);
			}
		}
		return at;
	}

	/** A star's warning: a ring of light closing in where it will land. */
	private static void ring(ServerLevel level, Vec3 at) {
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI * 2.0 / 12.0;
			level.sendParticles(ParticleTypes.GLOW, at.x + Math.cos(a) * STAR_RADIUS, at.y + 0.1, at.z + Math.sin(a) * STAR_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** A star lands: a burst of light, and everyone near hurt and thrown. */
	private void land(ServerLevel level, Vec3 at) {
		level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 1.3F);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.5, 0.3, 0.5, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 30, 0.6, 0.6, 0.6, 0.25);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(STAR_RADIUS, 2.5, STAR_RADIUS),
			e -> spareable(e) && !(e instanceof WindDragon))) {
			if (e.hurtServer(level, this.damageSources().indirectMagic(this, this), STAR_DAMAGE)) {
				Vec3 away = e.position().subtract(at).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
				e.push(away.x * STAR_THROW, 0.5, away.z * STAR_THROW);
				e.syncVelocity = true;
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return super.removeWhenFarAway(distance) && !fighting();
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.15F;
	}
}
