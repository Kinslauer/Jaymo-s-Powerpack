package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Stacked;
import com.jaymoskingdoms.VillagerHeritage;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The sand worm, the old Stoneborn legend: it moves under the desert's ruins. One wakes, once, under each desert
 * pyramid somebody goes into ({@link #WOKEN}), out in the sand beside it.
 * <p>
 * Under the sand it can't be seen or hurt, only read: the ground churns and hisses along its path as it makes for its
 * quarry, and it can only go through loose ground ({@link #loose}: sand, gravel, earth), so stone and the pyramid's
 * own floors are safe footing. Under its quarry it stops, the sand boils for {@link #WARN_TICKS} ticks, and it
 * breaches: everyone over it is hurt and thrown ({@link #BREACH_DAMAGE}). Up, it stands a tower of rings with its
 * maw open for {@link #UP_TICKS} ticks, the only time it can be hurt, and strikes down along a line at whoever is in
 * reach ({@link #BITE_REACH}), with a tell. Hurt enough in one showing ({@link #SINK_AFTER} of its health) it sinks
 * early. Then it goes under and comes round again.
 * <p>
 * A Stoneborn who is there when it comes up, player or villager, faints at the sight, once a fight (Powers' knock-out).
 */
public class SandWorm extends ActingMonster {
	static final double HEALTH = 380.0, ARMOR = 8.0;
	/** Under the sand: its speed (blocks a tick), how long at least before it breaches (ticks), how near it must get under its quarry. */
	static final double BURROW_SPEED = 0.42, UNDER = 1.6;
	static final int BURROW_LEAST = 70;
	/** How much the ground may rise or fall in one step of its path. */
	static final int BURROW_STEP = 2;
	/** The breach: the sand boils this long first (ticks), the rise takes this long, its reach, harm and throw. */
	static final int WARN_TICKS = 28, RISE_TICKS = 10;
	static final double BREACH_REACH = 3.2, BREACH_UP = 1.15;
	static final float BREACH_DAMAGE = 16.0F;
	/** Up: how long it stands (ticks), the share of its health that sends it down early, how long sinking takes. */
	static final int UP_TICKS = 190, SINK_TICKS = 20;
	static final float SINK_AFTER = 0.2F;
	/** Its strike: reach, the tell (ticks), the whole move, how often, the line's width, harm and shove. */
	static final double BITE_REACH = 7.0, BITE_WIDTH = 1.9, BITE_KNOCK = 1.0;
	static final int BITE_TELL = 16, BITE_TICKS = 30, BITE_EVERY = 46;
	static final float BITE_DAMAGE = 13.0F;
	/** How far a Stoneborn sees it come up and faints (blocks), and for how long (ticks). */
	static final double FAINT_RANGE = 24.0;
	static final int FAINT_TICKS = 20 * 12;
	/** Waking: how often players are looked at (ticks), and how far from the pyramid's middle it starts. */
	static final int LOOK_EVERY = 20 * 5, WAKE_OUT = 22, WAKE_TRIES = 16;

	/** Its moves, synced by their place here (the client's SandWormModel poses each; none of them: under the sand). */
	public static final String[] ACTIONS = {"warn", "rise", "up", "bite", "sink"};

	/** On the Overworld: the pyramids whose worm has woken, by their start chunk. */
	public static final AttachmentType<List<Long>> WOKEN = AttachmentRegistry.create(JaymosKingdoms.id("sand_worms_woken"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private int burrowed, upLeft, biteIn;
	private float hurtThisShowing;
	private @Nullable Vec3 biteAt;
	private final Set<UUID> fainted = new HashSet<>();
	private @Nullable ServerBossEvent bar;

	public SandWorm(EntityType<? extends SandWorm> type, Level level) {
		super(type, level);
		this.xpReward = 120;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SandWorm::wake);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	/** Under the sand: no move running. */
	public boolean under() {
		return this.action() == null;
	}

	/** Not to be seen, struck or aimed at: under the sand, or still under it while the sand boils. */
	public boolean hidden() {
		String action = this.action();
		return action == null || "warn".equals(action);
	}

	// ---- waking --------------------------------------------------------------------------------------------------------

	/** Whoever goes into a desert pyramid wakes its worm, once, out in the sand beside it. */
	private static void wake(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0 || Beasts.SAND_WORM == null) {
			return;
		}
		ServerLevel level = Stacked.level(server, Level.OVERWORLD);
		if (level == null) {
			return;
		}
		for (ServerPlayer player : Stacked.players(server, Level.OVERWORLD)) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), holder -> holder.is(BuiltinStructures.DESERT_PYRAMID));
			if (!start.isValid()) {
				continue;
			}
			long key = start.getChunkPos().pack();
			List<Long> woken = level.getAttachedOrElse(WOKEN, List.of());
			if (woken.contains(key)) {
				continue;
			}
			BlockPos middle = start.getBoundingBox().getCenter();
			RandomSource random = level.getRandom();
			for (int i = 0; i < WAKE_TRIES; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				int x = middle.getX() + (int) Math.round(Math.cos(angle) * WAKE_OUT), z = middle.getZ() + (int) Math.round(Math.sin(angle) * WAKE_OUT);
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, middle.getY(), z));
				if (!level.isLoaded(top) || !loose(level.getBlockState(top.below()))) {
					continue;
				}
				SandWorm worm = Beasts.SAND_WORM.create(level, EntitySpawnReason.EVENT);
				if (worm == null) {
					return;
				}
				worm.snapTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0.0F, 0.0F);
				worm.finalizeSpawn(level, level.getCurrentDifficultyAt(top), EntitySpawnReason.EVENT, null);
				level.addFreshEntity(worm);
				List<Long> all = new ArrayList<>(woken);
				all.add(key);
				level.setAttached(WOKEN, List.copyOf(all));
				JaymosKingdoms.LOGGER.info("A sand worm woke at {}", top);
				break;
			}
		}
	}

	/** Ground it can move through. */
	static boolean loose(BlockState state) {
		return state.is(BlockTags.SAND) || state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.SUSPICIOUS_SAND)
			|| state.is(Blocks.SUSPICIOUS_GRAVEL) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.CLAY);
	}

	// ---- its round -----------------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.hidden()) {
			if (this.bar == null) {
				this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
			}
			this.bar.setProgress(this.getHealth() / this.getMaxHealth());
			BossBars.show(level, this, this.bar);
		}
		this.setDeltaMovement(Vec3.ZERO);
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(null);
			target = null;
		}
		if (this.under()) {
			this.burrow(level, target);
		} else if (this.acting("warn")) {
			this.churn(level, 6, 1.4);
			if (this.actionRun() % 6 == 0) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 2.0F, 0.4F);
			}
		} else if (this.acting("rise") && this.actionRun() == 2) {
			this.breach(level);
		} else if (this.acting("up")) {
			this.standing(level, target);
		} else if (this.acting("bite") && this.actionRun() == BITE_TELL) {
			this.biteLands(level);
		}
	}

	@Override
	protected void onActionEnd() {
		String next = switch (this.phase) {
			case "warn" -> "rise";
			case "rise", "bite" -> "up";
			case "up" -> "sink";
			default -> null;
		};
		if (next == null) {
			// under again
			this.phase = "";
			this.burrowed = 0;
			return;
		}
		this.begin(next, switch (next) {
			case "rise" -> RISE_TICKS;
			case "up" -> Math.max(1, this.upLeft);
			default -> SINK_TICKS;
		});
		if ("rise".equals(next)) {
			this.upLeft = UP_TICKS;
			this.hurtThisShowing = 0.0F;
			this.biteIn = BITE_EVERY / 2;
		}
	}

	private String phase = "";

	private void begin(String action, int ticks) {
		this.phase = action;
		this.act(action, ticks);
	}

	/** Under the sand: toward its quarry through loose ground, the surface churning over it; under them, the warning. */
	private void burrow(ServerLevel level, @Nullable LivingEntity target) {
		this.burrowed++;
		if (target == null) {
			return;
		}
		Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		double flat = to.length();
		if (flat <= UNDER && this.burrowed >= BURROW_LEAST && Math.abs(target.getY() - this.getY()) < 4.0) {
			this.begin("warn", WARN_TICKS);
			return;
		}
		if (flat > 0.05) {
			Vec3 step = to.scale(Math.min(BURROW_SPEED, flat) / flat);
			double x = this.getX() + step.x, z = this.getZ() + step.z;
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, this.getY(), z));
			if (level.isLoaded(top) && loose(level.getBlockState(top.below())) && Math.abs(top.getY() - this.getY()) <= BURROW_STEP) {
				this.setPos(x, top.getY(), z);
				this.setYRot((float) (Math.atan2(step.z, step.x) * 180.0 / Math.PI) - 90.0F);
			}
		}
		if (this.tickCount % 2 == 0) {
			this.churn(level, 3, 0.7);
		}
		if (this.tickCount % 9 == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SAND_STEP, SoundSource.HOSTILE, 1.6F, 0.4F);
		}
	}

	/** The ground over it thrown about. */
	private void churn(ServerLevel level, int count, double spread) {
		BlockState ground = level.getBlockState(this.blockPosition().below());
		if (ground.isAir()) {
			ground = Blocks.SAND.defaultBlockState();
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.1, this.getZ(), count, spread, 0.05, spread, 0.12);
	}

	/** It comes up: everyone over it is hurt and thrown, and a Stoneborn who sees it faints. */
	private void breach(ServerLevel level) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(BREACH_REACH, 1.0, BREACH_REACH),
			e -> e != this && e.isAlive() && !(e instanceof SandWorm) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			if (e.position().subtract(this.position()).horizontalDistance() <= BREACH_REACH && e.hurtServer(level, this.damageSources().mobAttack(this), BREACH_DAMAGE)) {
				e.setDeltaMovement(e.getDeltaMovement().add(0.0, BREACH_UP, 0.0));
				e.needsSync = true;
			}
		}
		this.churn(level, 120, 1.6);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4F, 0.5F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5F, 0.4F);
		PowersCompat.impactFrame(level, this.position());
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(FAINT_RANGE), e -> e.isAlive() && e != this)) {
			Heritage.Kind blood = e instanceof Player player ? Heritage.of(player) : VillagerHeritage.of(e);
			if (blood == Heritage.Kind.STONEBORN && this.fainted.add(e.getUUID()) && e.hasLineOfSight(this)) {
				PowersCompat.knockOut(level, e, FAINT_TICKS);
			}
		}
	}

	/** Up: it turns to its quarry and strikes at whoever is in reach; hurt enough, or its time up, it sinks. */
	private void standing(ServerLevel level, @Nullable LivingEntity target) {
		this.upLeft--;
		if (this.hurtThisShowing >= this.getMaxHealth() * SINK_AFTER) {
			this.begin("sink", SINK_TICKS);
			return;
		}
		if (target == null) {
			return;
		}
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		Vec3 to = target.position().subtract(this.position());
		this.setYRot((float) (Math.atan2(to.z, to.x) * 180.0 / Math.PI) - 90.0F);
		this.yBodyRot = this.getYRot();
		if (--this.biteIn <= 0 && this.upLeft > BITE_TICKS && to.horizontalDistance() <= BITE_REACH && this.hasLineOfSight(target)) {
			this.biteIn = BITE_EVERY;
			this.biteAt = target.position();
			this.upLeft -= BITE_TICKS;
			this.begin("bite", BITE_TICKS);
			PowersCompat.tellIncoming(this, target, BITE_TELL, true);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.0F, 0.5F);
		}
	}

	/** The strike comes down: everyone along the line from its foot to where its quarry stood. */
	private void biteLands(ServerLevel level) {
		if (this.biteAt == null) {
			return;
		}
		Vec3 from = this.position(), line = this.biteAt.subtract(from).multiply(1.0, 0.0, 1.0);
		double length = Math.max(0.1, line.length());
		Vec3 dir = line.scale(1.0 / length);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(BITE_REACH + 2.0, 2.0, BITE_REACH + 2.0),
			e -> e != this && e.isAlive() && !(e instanceof SandWorm) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			Vec3 rel = e.position().subtract(from).multiply(1.0, 0.0, 1.0);
			double along = rel.dot(dir);
			if (along < 0.0 || along > length + 1.5 || rel.subtract(dir.scale(along)).length() > BITE_WIDTH + e.getBbWidth() * 0.5) {
				continue;
			}
			if (e.hurtServer(level, this.damageSources().mobAttack(this), BITE_DAMAGE)) {
				e.knockback(BITE_KNOCK, -dir.x, -dir.z, this.damageSources().mobAttack(this), BITE_DAMAGE);
			}
		}
		Vec3 at = from.add(dir.scale(length));
		BlockState ground = level.getBlockState(BlockPos.containing(at).below());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.SAND.defaultBlockState() : ground), at.x, at.y + 0.2, at.z, 50, 1.0,
			0.1, 1.0, 0.15);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9F, 0.7F);
		this.biteAt = null;
	}

	// ---- what it is ----------------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.hidden() && !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return false;
		}
		float before = this.getHealth();
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			this.hurtThisShowing += before - this.getHealth();
		}
		return hurt;
	}

	@Override
	public boolean isPickable() {
		return !this.hidden() && super.isPickable();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		super.remove(reason);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	/** (Entity) seen by nothing while it is under the sand. */
	@Override
	public boolean isInvisibleTo(Player player) {
		return this.hidden() || super.isInvisibleTo(player);
	}
}
