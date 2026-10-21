package com.jaymoskingdoms.dragons;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.141) Aeolus, father of the winds: the last and the greatest of the wind dragons ({@link WindDragon}), the final
 * boss. Huge, ancient, grey-white, with the colour of each of his four sons' winds in his markings. He is never found
 * in the wild: he walks the world as a villager ({@link Disguises}) and shows himself to someone who has beaten three
 * kinds of dragon. He cannot be worn down or tamed ({@link #tameable}); he fights to the end, in three phases:
 * <ol>
 * <li>Above two thirds of his health: as the others, on the ground and in the air, but slow to circle and brutal in a
 * swoop (a slam where he strikes).</li>
 * <li>Below two thirds: gusts that knock players back and blow out fires about him.</li>
 * <li>Below a third: every so often a gale (with a warning) that throws everything near him up and outward, and his
 * breath turns through the winds twice as fast.</li>
 * </ol>
 * His breath turns through the four winds in turn, a different one each burst: fire (Notus, {@link DragonBreath}), frost
 * (Boreas, {@link DragonFrost}), acid (Zephyrus, {@link DragonAcid}) and storm (Eurus, {@link DragonStorm}). When he
 * falls every wild wind dragon about is calmed for a day and he leaves the four lesser dragons' eggs ({@link DragonEggs}).
 * With no one to fight for a long while he flies up and is gone.
 */
public class AeolusDragon extends WindDragon {
	// ---- body ----
	static final double HEALTH = 1600.0, ARMOR = 24.0, TOUGHNESS = 14.0, SPEED = 0.25, BITE = 26.0;
	/** Where his mouth is (his model is drawn this much bigger than Notus'), and how far his breath reaches. */
	static final double MOUTH_AHEAD_GROUND = 8.0, MOUTH_UP_GROUND = 5.3, MOUTH_AHEAD_AIR = 8.6, MOUTH_UP_AIR = 3.1;
	static final double BREATH_RANGE = 18.0;
	/** Slower to circle than the lesser ones, but a harder dive. */
	static final double CRUISE = 0.4, SWOOP = 1.55;
	/** The slam where a swoop lands: reach (blocks), damage, throw out and up. */
	static final double SLAM_RADIUS = 9.0, SLAM_THROW = 1.4, SLAM_UP = 0.5;
	static final float SLAM_DAMAGE = 14.0F;
	/** The share of ground fights that become another swoop, in the first two phases and in the last. */
	static final float SWOOP_BIAS = 0.35F, SWOOP_BIAS_LAST = 0.65F;

	// ---- the winds ----
	/** Which wind is which: the order they turn in. */
	static final int FIRE = 0, FROST = 1, ACID = 2, STORM = 3;
	/** A breath this long since the last tick of one is a new burst; in the last phase the wind turns every this many ticks inside a burst too. */
	static final int BURST_GAP = 4, FAST_TURN = 14;

	// ---- the phases ----
	/** The share of his health below which the second and third phases begin. */
	static final float PHASE_2 = 2.0F / 3.0F, PHASE_3 = 1.0F / 3.0F;
	/** Gusts (phase 2): how often (ticks), how far they reach, how hard they throw players back and up. */
	static final int GUST_EVERY = 150;
	static final double GUST_RADIUS = 22.0, GUST_THROW = 1.5, GUST_UP = 0.4;
	/** Fires put out about him (blocks) by each gust. */
	static final int GUST_SNUFF = 12;
	/** The gale (phase 3): how often, how long the warning, how far it reaches, how hard it throws, its damage. */
	static final int GALE_EVERY = 320, GALE_WARNING = 40;
	static final double GALE_RADIUS = 30.0, GALE_OUT = 2.0, GALE_UP = 1.0;
	static final float GALE_DAMAGE = 6.0F;

	// ---- leaving ----
	/** With no one near for this long (ticks) he flies up and is gone; how far he looks for someone (blocks); how long the leaving takes. */
	static final int IDLE_LEAVE = 9600, LEAVE_TICKS = 120;
	static final double ENGAGED_RANGE = 64.0;
	/** How far the calm of his fall reaches (blocks), and for how long (game ticks: one day). */
	static final double CALM_RADIUS = 192.0, MESSAGE_RADIUS = 96.0;
	static final long CALM_TICKS = 24000L;

	private final DragonAcid acid = new DragonAcid();
	private int wind = STORM, windTick = -100, burstStart;
	private int phase = -1;
	private int gustIn = GUST_EVERY, galeIn = GALE_EVERY, idle, leaving;

	public AeolusDragon(EntityType<? extends AeolusDragon> type, Level level) {
		super(type, level);
		this.xpReward = 600;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return false;
	}

	/** He fights to the end: no crash, no taming. */
	@Override
	protected boolean tameable() {
		return false;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
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
	protected float flapVolume() {
		return 3.5F;
	}

	@Override
	protected double breathRange() {
		return BREATH_RANGE;
	}

	@Override
	protected double mouthAhead(boolean flying) {
		return flying ? MOUTH_AHEAD_AIR : MOUTH_AHEAD_GROUND;
	}

	@Override
	protected double mouthUp(boolean flying) {
		return flying ? MOUTH_UP_AIR : MOUTH_UP_GROUND;
	}

	/** (The base reads this once, when the bar is made, so it can't follow the wind: old gold, his colour in Disguises.) */
	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.YELLOW;
	}

	/** The wind his next breath is: 0 fire, 1 frost, 2 acid, 3 storm. */
	public int wind() {
		return this.wind;
	}

	/** Never leaves a fight; otherwise leaves by himself ({@link #IDLE_LEAVE}), not by the despawner. */
	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---- the breath ----

	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
		int now = this.tickCount;
		int last = this.windTick;
		this.windTick = now;
		if (now - last > BURST_GAP) {
			// a new burst: the next wind
			this.wind = (this.wind + 1) % 4;
			this.burstStart = now;
		} else if (this.phase >= 3 && (now - this.burstStart) >= FAST_TURN) {
			this.wind = (this.wind + 1) % 4;
			this.burstStart = now;
		}
		switch (this.wind) {
			case FIRE -> DragonBreath.breathe(level, this, mouth, aim, now, griefing);
			case FROST -> DragonFrost.breathe(level, this, mouth, aim, now, griefing);
			case ACID -> this.acid.breathe(level, this, mouth, aim, now);
			default -> DragonStorm.breathe(level, this, mouth, aim, now, griefing && level.isThundering());
		}
	}

	// ---- the fight ----

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.level() instanceof ServerLevel level) {
			// the gobs fly whether he's breathing or not
			this.acid.step(level, this);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.leaving > 0) {
			if (!engaged(level) && this.hurtTime == 0) {
				leave(level);
				return;
			}
			// called back: someone came, or struck him
			this.leaving = 0;
			this.idle = 0;
		}
		super.customServerAiStep(level);
		boolean engaged = engaged(level);
		if (this.tickCount % 20 == 0) {
			this.idle = engaged ? 0 : this.idle + 20;
			if (this.idle >= IDLE_LEAVE) {
				this.leaving = LEAVE_TICKS;
				return;
			}
		}
		if (!engaged) {
			return;
		}
		phases(level);
	}

	/** A player to fight is about (or he has a target). */
	private boolean engaged(ServerLevel level) {
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive()) {
			return true;
		}
		Player near = level.getNearestPlayer(this, ENGAGED_RANGE);
		return near != null && !near.isCreative() && !near.isSpectator();
	}

	private int phaseNow() {
		float share = this.getHealth() / this.getMaxHealth();
		return share < PHASE_3 ? 3 : share < PHASE_2 ? 2 : 1;
	}

	private void phases(ServerLevel level) {
		int now = phaseNow();
		if (now != this.phase) {
			boolean first = this.phase < 0;
			this.phase = now;
			if (!first && now > 1) {
				level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.5F);
				announce(level, now == 2 ? "message.jaymoskingdoms.aeolus.phase2" : "message.jaymoskingdoms.aeolus.phase3");
				this.gustIn = 40;
				this.galeIn = GALE_EVERY / 2;
			}
		}
		if (this.phase >= 2) {
			this.clearFire();
			if (--this.gustIn <= 0) {
				this.gustIn = GUST_EVERY;
				gust(level);
			}
		}
		if (this.phase >= 3) {
			this.galeIn--;
			if (this.galeIn == GALE_WARNING) {
				level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0F, 0.4F);
				announce(level, "message.jaymoskingdoms.aeolus.gale");
			}
			if (this.galeIn <= GALE_WARNING && this.galeIn > 0 && this.tickCount % 3 == 0) {
				ring(level, this.galeIn);
			}
			if (this.galeIn <= 0) {
				this.galeIn = GALE_EVERY;
				gale(level);
			}
		}
	}

	/** A line on the screen of everyone near. */
	private void announce(ServerLevel level, String key) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) < MESSAGE_RADIUS * MESSAGE_RADIUS) {
				player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** Gusts: players near are knocked back, and the fires about him blown out. */
	private void gust(ServerLevel level) {
		level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 6.0F, 0.5F);
		level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 2.0, this.getZ(), 80, 5.0, 1.5, 5.0, 0.4);
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(GUST_RADIUS),
			p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
			throwAway(player, GUST_THROW, GUST_UP);
		}
		BlockPos centre = this.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-GUST_SNUFF, -4, -GUST_SNUFF), centre.offset(GUST_SNUFF, 4, GUST_SNUFF))) {
			if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	/** The gale's warning: a ring of cloud closing in. */
	private void ring(ServerLevel level, int left) {
		double radius = GALE_RADIUS * left / GALE_WARNING + 2.0;
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2.0 / 24.0 + this.tickCount * 0.1;
			level.sendParticles(ParticleTypes.CLOUD, this.getX() + Math.cos(a) * radius, this.getY() + 1.0, this.getZ() + Math.sin(a) * radius, 1, 0.1, 0.5, 0.1, 0.0);
		}
	}

	/** The gale: everything of his enemies near is thrown up and outward. */
	private void gale(ServerLevel level) {
		level.playSound(null, this.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8.0F, 0.6F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.5, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.5, this.getZ(), 200, 8.0, 2.0, 8.0, 0.6);
		for (LivingEntity e : foes(level, GALE_RADIUS)) {
			e.hurtServer(level, this.damageSources().mobAttack(this), GALE_DAMAGE);
			throwAway(e, GALE_OUT, GALE_UP);
		}
	}

	/** Everything alive within {@code radius} that isn't on his side. */
	private List<LivingEntity> foes(ServerLevel level, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius),
			e -> e != this && e.isAlive() && !(e instanceof WindDragon) && !DragonBreath.spared(this, e)
				&& !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
	}

	private void throwAway(LivingEntity e, double out, double up) {
		Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		e.push(away.x * out, up, away.z * out);
		e.syncVelocity = true;
	}

	/** Where a swoop lands, the ground shakes and everything about is thrown. */
	private void slam(ServerLevel level) {
		level.playSound(null, this.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 4.0F, 0.6F);
		level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 1.0, this.getZ(), 6, 3.0, 0.5, 3.0, 0.0);
		for (LivingEntity e : foes(level, SLAM_RADIUS)) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), SLAM_DAMAGE)) {
				throwAway(e, SLAM_THROW, SLAM_UP);
			}
		}
	}

	@Override
	protected void next(ServerLevel level, LivingEntity target) {
		boolean dived = this.wild == Wild.SWOOP_DIVE;
		super.next(level, target);
		if (dived && this.distanceTo(target) < 8.0) {
			slam(level);
		}
		float bias = this.phase >= 3 ? SWOOP_BIAS_LAST : SWOOP_BIAS;
		if (this.wild == Wild.GROUND && this.random.nextFloat() < bias) {
			this.wild = Wild.SWOOP_RISE;
			if (this.onGround()) {
				takeOff();
			}
		}
	}

	// ---- leaving, and the end ----

	/** No one to fight for a long while: he rises into the sky and is gone. */
	private void leave(ServerLevel level) {
		setFlying(true);
		this.getNavigation().stop();
		this.setDeltaMovement(this.getDeltaMovement().lerp(new Vec3(0.0, 0.9, 0.0), 0.1));
		if (this.leaving % 20 == 0) {
			level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 6.0F, 0.6F);
		}
		level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(), 6, 3.0, 1.0, 3.0, 0.05);
		if (--this.leaving <= 0) {
			level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 2.0, this.getZ(), 80, 3.0, 2.0, 3.0, 0.1);
			level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 1.2F);
			this.discard();
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// every wild wind dragon about is calmed for a day
		long until = level.getGameTime() + CALM_TICKS;
		for (WindDragon dragon : level.getEntitiesOfClass(WindDragon.class, this.getBoundingBox().inflate(CALM_RADIUS), d -> d != this && !d.isTamed())) {
			dragon.calmUntil(until);
		}
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) < MESSAGE_RADIUS * MESSAGE_RADIUS) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.aeolus.fallen").withStyle(ChatFormatting.GOLD));
			}
		}
		// the four eggs, where he fell
		for (String species : DragonEggs.SPECIES) {
			ItemEntity egg = new ItemEntity(level, this.getX(), this.getY() + 1.0, this.getZ(), DragonEggs.egg(species));
			egg.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.4, 0.3, (this.random.nextDouble() - 0.5) * 0.4);
			egg.setUnlimitedLifetime();
			egg.setPermanentlyInvulnerable(true);
			level.addFreshEntity(egg);
		}
		level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 2.0, this.getZ(), 120, 4.0, 2.0, 4.0, 0.2);
	}

	/** His phases come from his health, so a loaded one carries on from where it stood; only his idle time is saved. */
	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("aeolus_idle", this.idle);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.idle = input.getIntOr("aeolus_idle", 0);
		this.phase = -1;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}
}
