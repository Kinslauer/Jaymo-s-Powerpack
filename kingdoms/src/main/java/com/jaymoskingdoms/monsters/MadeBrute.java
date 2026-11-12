package com.jaymoskingdoms.monsters;

import com.jaymoskingdoms.Fx;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A captive made into a great foe by a Blood Sorcerer's rite ({@code BruteRite}): the ogre's great body, in the colours of
 * what it was (a villager, an illager or a witch; its variant). It keeps the name of who it was (as its hidden custom name,
 * which its boss bar shows) and who made it. For a short while after the rite it stands dazed; then it obeys its maker for a
 * little ({@link #OBEY_TICKS}), going for the monsters near them; then it turns, and is hostile to everyone, the maker
 * first among them. A blow from the maker ends both early. It is a monster of legend ({@link Monsters#legends}), so it
 * has a great foe's entrance and bar but never counts toward a world tier. Each maker may have one alive at a time.
 */
public class MadeBrute extends Ogre {
	/** What it was. */
	public static final int VILLAGER = 0, ILLAGER = 1, WITCH = 2;
	// ---- tuning ----
	static final double HEALTH = 120.0, ARMOUR = 6.0, TOUGHNESS = 2.0, SPEED = 0.23, BLOW = 11.0, FOLLOW = 32.0;
	/** The daze after the rite and the time it then obeys its maker (ticks); how far from its maker it looks for their foes (blocks). */
	public static final int DAZE_TICKS = 20 * 5, OBEY_TICKS = 20 * 20;
	static final double OBEY_RANGE = 16.0;
	/** Its bar shows to players this near (blocks); how often the bar is looked at (ticks). */
	static final double BAR_RANGE = 40.0;
	static final int BAR_EVERY = 10;
	/** What it drops: bones (least, most), and the little of what it was. */
	static final int BONES_LEAST = 2, BONES_MOST = 4;

	private @Nullable UUID maker;
	private int origin;
	private int daze, obey;
	private @Nullable ServerBossEvent bar;

	public MadeBrute(EntityType<? extends MadeBrute> type, Level level) {
		super(type, level, BRUTE);
		this.xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, BLOW)
			.add(Attributes.ARMOR, ARMOUR).add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.ATTACK_KNOCKBACK, 1.5).add(Attributes.STEP_HEIGHT, 1.0);
	}

	/**
	 * Sets up a new one: who made it, what it was, who it was ({@code who}: null for no name) and the daze it stands in.
	 */
	public void make(@Nullable UUID maker, int origin, @Nullable Component who) {
		this.maker = maker;
		this.origin = Math.floorMod(origin, 3);
		this.daze = DAZE_TICKS;
		this.obey = OBEY_TICKS;
		this.setVariant(this.origin);
		if (who != null) {
			this.setCustomName(who);
			this.setCustomNameVisible(false);
		}
	}

	public @Nullable UUID maker() {
		return this.maker;
	}

	/** What it was ({@link #VILLAGER}, {@link #ILLAGER}, {@link #WITCH}). */
	public int origin() {
		return this.origin;
	}

	/** Dazed: it stands and does nothing. */
	public boolean dazed() {
		return this.daze > 0;
	}

	/** Obeying its maker (daze over, not yet turned). */
	public boolean obeying() {
		return this.daze <= 0 && this.obey > 0;
	}

	@Override
	protected int variants() {
		return 3;
	}

	@Override
	protected int pickVariant(ServerLevelAccessor level) {
		this.setTwoHeads(false);
		return this.origin;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// everyone's foe: the realm's own people too
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, com.jaymoskingdoms.Folk.class, true));
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// dazed it picks no one; obeying it picks only the monsters round its maker
		if (target != null && (this.dazed() || this.obeying() && (!(target instanceof Enemy) || target instanceof Ogre))) {
			return;
		}
		super.setTarget(target);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && (this.dazed() || this.obeying()) && source.getEntity() instanceof Player striker) {
			// struck by anyone, the spell is over: it turns on them (their maker's blow too)
			this.daze = 0;
			this.obey = 0;
			super.setTarget(striker);
		}
		return hurt;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.daze > 0) {
			this.daze--;
			this.getNavigation().stop();
			this.setDeltaMovement(0.0, Math.min(0.0, this.getDeltaMovement().y), 0.0);
			if (this.tickCount % 8 == 0) {
				level.sendParticles(Fx.miasma(0x8A0F14, 1.2F), this.getX(), this.getY(0.7), this.getZ(), 4, 0.5, 0.8, 0.5, 0.01);
			}
			this.setTarget(null);
		} else if (this.obey > 0) {
			this.obey--;
			this.obeyStep(level);
			if (this.obey == 0) {
				this.turn(level);
			}
		}
		super.customServerAiStep(level);
		this.updateBar(level);
	}

	/** While it obeys: it goes for the monsters near its maker, not them. */
	private void obeyStep(ServerLevel level) {
		Player owner = this.maker == null ? null : level.getPlayerByUUID(this.maker);
		if (owner == null || !owner.isAlive()) {
			return;
		}
		LivingEntity current = this.getTarget();
		if (current != null && current.isAlive() && !current.getUUID().equals(this.maker)) {
			return;
		}
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(OBEY_RANGE),
			e -> e != this && e.isAlive() && e instanceof Enemy && !(e instanceof Ogre));
		LivingEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : foes) {
			double d = e.distanceToSqr(this);
			if (d < best) {
				best = d;
				nearest = e;
			}
		}
		this.setTarget(nearest);
		if (nearest == null) {
			// nothing to fight: it stays by its maker
			this.getNavigation().moveTo(owner, 0.9);
		}
	}

	/** Its time of obeying is over: it roars, and the nearest of anyone is its foe. */
	private void turn(ServerLevel level) {
		this.playSound(net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, 2.0F, 0.6F);
		Fx.ring(level, this.position().add(0.0, 0.1, 0.0), 4.0F, 0x8A0F14);
		this.setTarget(null);
	}

	// ---- its bar ----------------------------------------------------------------------------------------------------------

	private void updateBar(ServerLevel level) {
		if (this.tickCount % BAR_EVERY != 0) {
			return;
		}
		if (this.bar == null) {
			this.bar = new ServerBossEvent(UUID.nameUUIDFromBytes(("made_brute" + this.getUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
		}
		this.bar.setName(this.getDisplayName());
		this.bar.setProgress(Math.max(0.0F, this.getHealth() / this.getMaxHealth()));
		double r2 = BAR_RANGE * BAR_RANGE;
		for (ServerPlayer player : List.copyOf(this.bar.getPlayers())) {
			if (player.level() != level || player.isSpectator() || player.distanceToSqr(this) > r2) {
				this.bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(this) <= r2 && !this.bar.getPlayers().contains(player)) {
				this.bar.addPlayer(player);
			}
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
		}
		super.remove(reason);
	}

	// ---- its drops --------------------------------------------------------------------------------------------------------

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(Items.BONE, BONES_LEAST + this.random.nextInt(BONES_MOST - BONES_LEAST + 1)));
		switch (this.origin) {
			case VILLAGER -> this.spawnAtLocation(level, new ItemStack(Items.EMERALD, 2 + this.random.nextInt(3)));
			case ILLAGER -> this.spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, 6 + this.random.nextInt(7)));
			default -> this.spawnAtLocation(level, new ItemStack(Items.GLASS_BOTTLE, 1 + this.random.nextInt(3)));
		}
	}

	// ---- saving -----------------------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.maker != null) {
			output.putString("maker", this.maker.toString());
		}
		output.putInt("origin", this.origin);
		output.putInt("daze", this.daze);
		output.putInt("obey", this.obey);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.maker = input.getString("maker").map(s -> {
			try {
				return UUID.fromString(s);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
		this.origin = input.getIntOr("origin", 0);
		this.daze = input.getIntOr("daze", 0);
		this.obey = input.getIntOr("obey", 0);
	}

	/** Its place in a world: where it stands now. */
	public Vec3 centre() {
		return this.position().add(0.0, this.getBbHeight() * 0.5, 0.0);
	}
}
