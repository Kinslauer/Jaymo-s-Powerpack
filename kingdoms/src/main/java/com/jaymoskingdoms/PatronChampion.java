package com.jaymoskingdoms;

import com.jaymoskingdoms.champions.Moves;
import com.jaymoskingdoms.champions.Move;
import com.jaymoskingdoms.champions.MoveKit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) A patron's champion (docs/Patrons-C-Plan.md): a multi-phase boss, one class for the eight patrons (one entity type
 * each). Asleep at its altar as a statue, it wakes for a Wildlands player. Its health and blows grow with the players near and
 * with the world tier; its phases open its three moves (see {@code champions/}), each telegraphed so good play can dodge.
 * The hunting form is a weaker champion that hunts a player who turned from its patron, once.
 */
public class PatronChampion extends Monster {
	/** The solo base at Hearth (the tier and the group raise it): health, blow, armour, toughness, speed, knockback resistance, follow range. */
	static final double HEALTH = 600.0, DAMAGE = 14.0, ARMOR = 16.0, TOUGHNESS = 8.0, SPEED = 0.30, KNOCKBACK_RESISTANCE = 0.9, FOLLOW = 40.0;
	/** Each counted player past the first adds this share of the base health and of the blow; at most GROUP_MOST count. */
	static final double HEALTH_PER_PLAYER = 0.6, DAMAGE_PER_PLAYER = 0.08;
	static final int GROUP_MOST = 6;
	/** (0.7.518) The most max health a champion is scaled to. It matches the cap in MaxHealthCapMixin. */
	static final double HEALTH_CAP = 4096.0;
	/** Players this near (alive, not spectating, not creative, not downed) count, rechecked this often; the count only grows in a fight. */
	static final double FIGHT_RANGE = 32.0;
	static final int RECOUNT_TICKS = 100;
	/** From this many players a fourth phase opens: the patron's hand (adds, or the Cinder-Shade's doubles). */
	static final int FOURTH_PHASE_FROM = 3;
	/** Where the phases turn (shares of max health): three phases alone or in pairs, four in a group; the hunting form has two. */
	static final float[] TURNS_3 = {0.66F, 0.33F}, TURNS_4 = {0.75F, 0.5F, 0.25F}, TURNS_HUNT = {0.5F};
	/** A phase turn: this long untouchable (roaring), then a shockwave: its reach, harm (a share of the blow) and push. */
	static final int TURN_TICKS = 40;
	static final double TURN_REACH = 7.0, TURN_PUSH = 1.6;
	static final float TURN_SHARE = 0.4F;
	/** The last phase: move cooldowns times this, and this much faster. */
	static final float LAST_COOLDOWN = 0.7F;
	static final double LAST_SPEED = 0.15;
	/** The least gap between two moves (ticks). */
	static final int MOVE_GAP = 50;
	/** Temple form: how far from its altar it will chase (the great temple: GREAT_HOME), and its rest heal (share of max health a second) once nobody is in FIGHT_RANGE. */
	static final double HOME_RADIUS = 24.0, GREAT_HOME = 12.0;
	static final float REST_HEAL = 0.03F;
	/** With no counted player for this long it goes back to its altar, whole, and falls asleep again. */
	static final int RESET_TICKS = 20 * 30;
	/** No blow landed on its target and no path for this long: it closes the gap (a blink beside the target). */
	static final int STUCK_TICKS = 100;
	/** A smoke double lives this long (ticks); a mob that hurt the champion stays its foe this long (ticks). */
	static final int DECOY_LIFE = 600, FOE_MEMORY = 200;
	/** A sleeping temple champion wakes for a player of at least this tier (an ordinal: Wildlands) within WAKE_RANGE in sight, or who strikes it; the great temple's only when struck. */
	static final int WAKE_TIER = 2;
	static final double WAKE_RANGE = 10.0;
	/** The hunting form: shares of the solo numbers; it gives up after HUNT_LIFE or once its quarry is HUNT_LOSE away. */
	static final double HUNT_HEALTH = 0.4, HUNT_DAMAGE = 0.65, HUNT_LOSE = 96.0;
	static final int HUNT_LIFE = 20 * 60 * 6;
	/** Experience it gives (the temple form; the hunting form gives a third). */
	static final int XP = 250;
	public static final byte WAKE = 96, CAST = 97, TURN = 98;

	private static final net.minecraft.resources.Identifier HEALTH_ID = JaymosKingdoms.id("champion_health"),
		DAMAGE_ID = JaymosKingdoms.id("champion_damage"), SPEED_ID = JaymosKingdoms.id("champion_last"), BLOW_ID = JaymosKingdoms.id("champion_next_blow");

	private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(PatronChampion.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Byte> CASTING = SynchedEntityData.defineId(PatronChampion.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> TURNING = SynchedEntityData.defineId(PatronChampion.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DECOY = SynchedEntityData.defineId(PatronChampion.class, EntityDataSerializers.BOOLEAN);

	private final Patron patron;
	private final Moves moves;
	private final ServerBossEvent bar;
	private final Set<UUID> hitters = new HashSet<>();
	private final int[] cooldowns = new int[4];
	private int templeId = -1, kind = -1;
	private @Nullable BlockPos home;
	/** The way out it faces when it falls asleep again. */
	private float restYaw;
	private @Nullable UUID quarry;
	private int phase, group, tier;
	private float[] turns = TURNS_3;
	private boolean awakened, armed;
	private int turningLeft, castLeft, castSlot = -1, lastMoveEnd, noFoe, bornAt = -1;
	private @Nullable LivingEntity castTarget;
	private float nextBlow = 1.0F;
	private int nextBlowUntil;
	private int lastBlowAt, lastPathAt, decoyLife = DECOY_LIFE;
	/** (Client) the tick it woke, last cast, and last turned. */
	private int wokeAt = -1000, castAt = -1000, turnAt = -1000;

	public PatronChampion(EntityType<? extends PatronChampion> type, Level level) {
		super(type, level);
		Patron p = PatronChampions.patronOf(type);
		this.patron = p == null ? Patron.HEARTHKEEPER : p;
		this.moves = Moves.create(this.patron);
		this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), PatronChampions.barColour(this.patron), BossEvent.BossBarOverlay.PROGRESS);
		this.bar.setVisible(false);
		this.xpReward = XP;
		if (this.patron == Patron.DEEP_MOTHER) {
			this.setPathfindingMalus(PathType.WATER, 0.0F);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ARMOR, ARMOR).add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DORMANT, false);
		entityData.define(CASTING, (byte) -1);
		entityData.define(TURNING, false);
		entityData.define(DECOY, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				LivingEntity by = PatronChampion.this.getLastHurtByMob();
				return by != null && PatronChampion.this.foe(by) && super.canUse();
			}
		});
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(t, l) -> this.foe(t) && this.targetable(t)));
	}

	/** Whether a would-be target is the one this champion may chase: its quarry only (hunting), or inside its leash. */
	private boolean targetable(LivingEntity t) {
		if (this.quarry != null) {
			return t.getUUID().equals(this.quarry);
		}
		return this.home == null || t.blockPosition().distSqr(this.home) <= this.leash() * this.leash();
	}

	private double leash() {
		return this.kind == Temples.GREAT ? GREAT_HOME : HOME_RADIUS;
	}

	// ---- the public face (for the moves and the rest of the mod) -------------------------------------------------------------

	public Patron patron() {
		return this.patron;
	}

	public int phase() {
		return this.phase;
	}

	public int group() {
		return Math.max(1, this.group);
	}

	public boolean hunting() {
		return this.quarry != null;
	}

	public boolean dormant() {
		return this.entityData.get(DORMANT);
	}

	public boolean decoy() {
		return this.entityData.get(DECOY);
	}

	public boolean turning() {
		return this.entityData.get(TURNING);
	}

	/** The move slot being cast, or -1 (the client reads it too). */
	public int casting() {
		return this.entityData.get(CASTING);
	}

	public int templeId() {
		return this.templeId;
	}

	public int kind() {
		return this.kind;
	}

	public @Nullable UUID quarry() {
		return this.quarry;
	}

	public @Nullable BlockPos home() {
		return this.home;
	}

	public Set<UUID> hitters() {
		return this.hitters;
	}

	public double blow() {
		return this.getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	public void makeDecoy() {
		this.entityData.set(DECOY, true);
		this.xpReward = 0;
		this.bar.setVisible(false);
		AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(1.0);
		}
		this.setHealth(1.0F);
		this.awakened = true;
	}

	/** A living, non-spectator, non-creative, not-downed player; a mob that has hurt it lately; or a player's tamed beast. Never villagers or Folk otherwise. */
	public boolean foe(Entity e) {
		if (e == this || !(e instanceof LivingEntity living) || !living.isAlive() || PatronChampions.isAdd(e) || e instanceof PatronChampion || e instanceof Folk) {
			return false;
		}
		if (e instanceof Player p) {
			return !p.isSpectator() && !p.isCreative() && !Downed.isDowned(p);
		}
		if (e instanceof OwnableEntity o && o.getOwner() instanceof Player) {
			return true;
		}
		return e == this.getLastHurtByMob() && this.tickCount - this.getLastHurtByMobTimestamp() < FOE_MEMORY;
	}

	/** Foes within {@code range}, nearest first. */
	public List<LivingEntity> foes(double range) {
		List<LivingEntity> out = new ArrayList<>(this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range),
			e -> this.foe(e) && this.distanceTo(e) <= range));
		out.sort(java.util.Comparator.comparingDouble(this::distanceToSqr));
		return out;
	}

	public boolean hurtFoe(ServerLevel level, LivingEntity foe, float amount, boolean magic) {
		DamageSource source = magic ? this.damageSources().indirectMagic(this, this) : this.damageSources().mobAttack(this);
		boolean hit = foe.hurtServer(level, source, amount);
		if (hit) {
			this.lastBlowAt = this.tickCount;
		}
		return hit;
	}

	public void wearPosture(float amount) {
		if (this.level() instanceof ServerLevel level) {
			BossPosture.wear(level, this, amount);
		}
	}

	/** Wears {@code share} of its maximum posture (a dazed charge). */
	public void wearPostureShare(float share) {
		if (this.level() instanceof ServerLevel level) {
			BossPosture.wear(level, this, BossPosture.max(this) * share);
		}
	}

	/** The next melee blow is this much times harder, if it lands within {@code ticks}. */
	public void nextBlow(float factor, int ticks) {
		this.nextBlow = factor;
		this.nextBlowUntil = this.tickCount + ticks;
	}

	public void blinkTo(Vec3 to) {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		DustParticleOptions dust = new DustParticleOptions(this.patron.colour & 0xFFFFFF, 1.5F);
		level.sendParticles(dust, this.getX(), this.getY() + 1.2, this.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
		this.getNavigation().stop();
		this.teleportTo(to.x, to.y, to.z);
		level.sendParticles(dust, to.x, to.y + 1.2, to.z, 30, 0.4, 0.8, 0.4, 0.02);
		MoveKit.sound(level, to, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
		this.lastPathAt = this.tickCount;
		this.lastBlowAt = this.tickCount;
	}

	public void speak(String line) {
		if (this.level() instanceof ServerLevel level) {
			Speech.say(level, this, Component.translatable("speech.jaymoskingdoms.patron_champion." + this.patron.key + "." + line),
				this.patron.colour & 0xFFFFFF, 48.0);
		}
	}

	/** Brings an add into the fight (section 5.5). */
	public void spawnAdd(ServerLevel level, Mob mob) {
		PatronChampions.addTo(level, this, mob);
	}

	// ---- being set down ------------------------------------------------------------------------------------------------------

	/** Set down asleep at its altar. */
	public void sleep(int templeId, int kind, BlockPos home, float yaw) {
		this.templeId = templeId;
		this.kind = kind;
		this.home = home;
		this.restYaw = yaw;
		this.entityData.set(DORMANT, true);
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
		this.setYBodyRot(yaw);
		this.setPersistenceRequired();
	}

	/** Set down awake as a hunter of {@code player}. */
	public void hunt(ServerPlayer player) {
		this.quarry = player.getUUID();
		this.xpReward = XP / 3;
		this.wake((ServerLevel) this.level(), false);
	}

	/** An op's or an egg's champion: awake where it stands. */
	public void loose() {
		this.home = this.blockPosition();
		this.kind = -1;
	}

	// ---- waking --------------------------------------------------------------------------------------------------------------

	private void wake(ServerLevel level, boolean show) {
		this.entityData.set(DORMANT, false);
		this.awakened = true;
		this.bornAt = this.tickCount;
		this.lastBlowAt = this.tickCount;
		this.lastPathAt = this.tickCount;
		this.phase = 0;
		this.group = 0;
		this.rescale(level);
		this.turns = this.quarry != null ? TURNS_HUNT : this.group >= FOURTH_PHASE_FROM ? TURNS_4 : TURNS_3;
		this.bar.setVisible(true);
		if (show) {
			PatronChampions.wakeSound(level, this);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.5, this.getZ(), 60, 0.6, 1.2, 0.6, 0.1);
			this.speak("wake");
		}
		level.broadcastEntityEvent(this, WAKE);
	}

	/** Players that count for the fight: near it (the hunting form: near its quarry), alive, not spectating, not creative, not downed. */
	private List<ServerPlayer> counted(ServerLevel level) {
		Vec3 at = this.position();
		if (this.quarry != null) {
			ServerPlayer q = level.getServer().getPlayerList().getPlayer(this.quarry);
			if (q != null && q.level() == level) {
				at = q.position();
			}
		}
		Vec3 centre = at;
		// a temple champion only counts those inside its leash, so a bow from beyond it earns a rest heal and a reset
		BlockPos leashed = this.quarry == null ? this.home : null;
		double leash2 = this.leash() * this.leash();
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && !Downed.isDowned(p) && p.position().distanceTo(centre) <= FIGHT_RANGE
				&& (leashed == null || p.blockPosition().distSqr(leashed) <= leash2)) {
				out.add(p);
			}
		}
		return out;
	}

	/** Group and tier scaling: at waking and every RECOUNT_TICKS while awake. */
	private void rescale(ServerLevel level) {
		List<ServerPlayer> near = this.counted(level);
		if (this.quarry != null) {
			ServerPlayer q = level.getServer().getPlayerList().getPlayer(this.quarry);
			this.tier = q == null ? this.tier : WorldTiers.of(q).ordinal();
			this.group = Math.max(1, Math.min(GROUP_MOST, near.size()));
		} else if (!near.isEmpty()) {
			this.group = Math.max(this.group, Math.min(GROUP_MOST, near.size()));
			int best = 0;
			for (ServerPlayer p : near) {
				best = Math.max(best, WorldTiers.of(p).ordinal());
			}
			this.tier = best;
		}
		int g = Math.max(1, this.group);
		double form = this.quarry != null ? HUNT_HEALTH : 1.0;
		double dform = this.quarry != null ? HUNT_DAMAGE : 1.0;
		float share = this.getMaxHealth() > 0.0F ? this.getHealth() / this.getMaxHealth() : 1.0F;
		// (0.7.518) the scale never takes the health past the cap the max health mixin allows
		double base = this.getAttributeBaseValue(Attributes.MAX_HEALTH);
		double scale = (1.0 + HEALTH_PER_PLAYER * (g - 1)) * (1.0 + WorldTiers.HEALTH[this.tier]) * form;
		modify(Attributes.MAX_HEALTH, HEALTH_ID, (base > 0.0 ? Math.min(scale, HEALTH_CAP / base) : scale) - 1.0);
		modify(Attributes.ATTACK_DAMAGE, DAMAGE_ID, (1.0 + DAMAGE_PER_PLAYER * (g - 1)) * (1.0 + WorldTiers.DAMAGE[this.tier]) * dform - 1.0);
		this.setHealth(this.getMaxHealth() * share);
		// a group that grows to three mid-fight gets the fourth phase, if it has not passed 25% yet
		if (this.quarry == null && this.turns == TURNS_3 && g >= FOURTH_PHASE_FROM && this.getHealth() / this.getMaxHealth() > 0.25F) {
			this.turns = TURNS_4;
			AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null && this.phase < this.turns.length) {
				speed.removeModifier(SPEED_ID);
			}
		}
	}

	private void modify(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, net.minecraft.resources.Identifier id, double amount) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------------

	/** The tick the AI step last ran, so the immobile fallback in aiStep does not run it twice. */
	protected int stepTick = -1;

	/** Being immobile skips the server AI step, so the timers that end those states run here instead. */
	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.isAlive() && this.isImmobile() && this.stepTick != this.tickCount) {
			this.customServerAiStep(level);
		}
	}

	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || this.dormant() || this.castLeft > 0 || this.turningLeft > 0;
	}

	@Override
	public boolean isPushable() {
		return !this.dormant() && super.isPushable();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.stepTick = this.tickCount;
		if (!this.armed) {
			this.armed = true;
			// (0.7.518) a smoke double carries no weapon, so Stare Down has nothing to make it drop
			if (!this.decoy() && this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
				ItemStack weapon = Spoils.powers(PatronChampions.weaponId(this.patron));
				if (!weapon.isEmpty()) {
					// for show only: its attack bonus must not stack on the tuned blow
					weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
					this.setItemSlot(EquipmentSlot.MAINHAND, weapon);
					this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
				}
			}
		}
		if (this.decoy()) {
			if (--this.decoyLife <= 0) {
				this.discard();
			}
			return;
		}
		if (this.quarry != null && !PatronChampions.isHunter(this)) {
			// a hunter left over from before the server stopped
			this.discard();
			return;
		}
		if (this.dormant()) {
			this.sleepTick(level);
			return;
		}
		if (!this.awakened) {
			// loaded awake from disk, or hatched from an egg: it fights fresh from where it is
			this.wake(level, false);
			if (this.home == null && this.quarry == null) {
				this.loose();
			}
		}
		if (this.tickCount % RECOUNT_TICKS == 0) {
			this.rescale(level);
		}
		if (this.tickCount % 10 == 0) {
			this.updateBar(level);
		}
		this.moves.tick(level, this);
		if (this.turningLeft > 0) {
			if (--this.turningLeft == 0) {
				this.endTurn(level);
			} else if (this.turningLeft % 4 == 0) {
				MoveKit.ring(level, this.position(), TURN_REACH * (1.0 - this.turningLeft / (double)TURN_TICKS), this.patron.colour);
			}
			return;
		}
		if (this.phase < this.turns.length && this.getHealth() / this.getMaxHealth() <= this.turns[this.phase] && this.getHealth() > 0.0F) {
			this.startTurn(level);
			return;
		}
		if (BossPosture.kneeling(this)) {
			this.cancelCast();
			this.moves.clear(this);
			return;
		}
		this.aim(level);
		this.cast(level);
		this.tending(level);
		this.stuck(level);
	}

	private void sleepTick(ServerLevel level) {
		if (this.tickCount % 40 == 0) {
			level.sendParticles(new DustParticleOptions(this.patron.colour & 0xFFFFFF, 1.0F), this.getX(), this.getY() + 1.6, this.getZ(), 4, 0.4, 0.9, 0.4, 0.0);
		}
		if (this.tickCount % 10 != 0 || (this.kind != Temples.WILD && this.kind != Temples.RUIN) || this.quarry != null) {
			return;
		}
		Player near = level.getNearestPlayer(this, WAKE_RANGE);
		if (near instanceof ServerPlayer sp && !sp.isSpectator() && !sp.isCreative() && WorldTiers.of(sp).ordinal() >= WAKE_TIER && this.hasLineOfSight(sp)) {
			this.wake(level, true);
		}
	}

	private void updateBar(ServerLevel level) {
		this.bar.setProgress(Math.max(0.0F, Math.min(1.0F, this.getHealth() / this.getMaxHealth())));
		double r2 = BossBars.RANGE * BossBars.RANGE;
		for (ServerPlayer p : level.players()) {
			boolean in = !p.isSpectator() && p.distanceToSqr(this) <= r2;
			boolean has = this.bar.getPlayers().contains(p);
			if (in && !has) {
				this.bar.addPlayer(p);
			} else if (!in && has) {
				this.bar.removePlayer(p);
			}
		}
	}

	/** Its target: the quarry for a hunter; and a leash for the temple form. */
	private void aim(ServerLevel level) {
		if (this.quarry != null) {
			ServerPlayer q = level.getServer().getPlayerList().getPlayer(this.quarry);
			if (q != null && q.isAlive() && q.level() == level && !Downed.isDowned(q)) {
				if (this.getTarget() != q) {
					this.setTarget(q);
				}
			}
		} else if (this.getTarget() != null && !this.targetable(this.getTarget())) {
			this.setTarget(null);
		}
		LivingEntity t = this.getTarget();
		if (t != null && (this.distanceTo(t) <= 3.0 || !this.getNavigation().isDone())) {
			this.lastPathAt = this.tickCount;
		}
		if (t == null) {
			this.lastPathAt = this.tickCount;
			this.lastBlowAt = this.tickCount;
		}
	}

	private int open() {
		return Math.min(this.phase, this.quarry != null ? 1 : 2) + 1;
	}

	private void cast(ServerLevel level) {
		if (this.castLeft > 0) {
			Move move = this.moves.moves().get(this.castSlot);
			LivingEntity target = this.castTarget != null && this.castTarget.isAlive() ? this.castTarget : this.getTarget();
			move.tell().tick(level, this, target, this.castLeft);
			if (--this.castLeft == 0) {
				this.finishCast(level, move, target);
			}
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || this.tickCount - this.lastMoveEnd < MOVE_GAP) {
			return;
		}
		double dist = this.distanceTo(target);
		List<Integer> ready = new ArrayList<>();
		List<Move> all = this.moves.moves();
		for (int i = 0; i < this.open() && i < all.size(); i++) {
			Move m = all.get(i);
			if (this.tickCount >= this.cooldowns[i] && dist >= m.minRange() && dist <= m.maxRange()) {
				ready.add(i);
			}
		}
		if (ready.isEmpty()) {
			return;
		}
		int slot = ready.get(this.random.nextInt(ready.size()));
		Move move = all.get(slot);
		this.castSlot = slot;
		this.castTarget = target;
		this.castLeft = move.windup();
		this.getNavigation().stop();
		this.cooldowns[slot] = this.tickCount + Math.round(move.cooldown() * (this.phase >= this.turns.length ? LAST_COOLDOWN : 1.0F));
		this.entityData.set(CASTING, (byte)slot);
		level.broadcastEntityEvent(this, CAST);
		if (this.castLeft <= 0) {
			this.finishCast(level, move, target);
		}
	}

	private void finishCast(ServerLevel level, Move move, @Nullable LivingEntity target) {
		this.castLeft = 0;
		this.castTarget = null;
		this.entityData.set(CASTING, (byte)-1);
		this.lastMoveEnd = this.tickCount;
		move.act().land(level, this, target);
	}

	private void cancelCast() {
		this.castLeft = 0;
		this.castTarget = null;
		this.entityData.set(CASTING, (byte)-1);
		this.lastMoveEnd = this.tickCount;
	}

	private void startTurn(ServerLevel level) {
		this.cancelCast();
		this.moves.clear(this);
		this.turningLeft = TURN_TICKS;
		this.entityData.set(TURNING, true);
		this.getNavigation().stop();
		level.broadcastEntityEvent(this, TURN);
		MoveKit.sound(level, this.position(), SoundEvents.RAVAGER_ROAR, 2.0F, 0.6F);
		this.speak("phase." + (this.phase + 2));
	}

	private void endTurn(ServerLevel level) {
		this.entityData.set(TURNING, false);
		for (LivingEntity e : this.foes(TURN_REACH)) {
			e.hurtServer(level, this.damageSources().mobAttack(this), (float)(this.blow() * TURN_SHARE));
			MoveKit.push(e, this.position(), TURN_PUSH, 0.4);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		MoveKit.sound(level, this.position(), SoundEvents.GENERIC_EXPLODE, 1.5F, 0.8F);
		this.phase++;
		this.lastMoveEnd = this.tickCount;
		if (this.phase >= this.turns.length) {
			AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.addOrReplacePermanentModifier(new AttributeModifier(SPEED_ID, LAST_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
		if (this.turns == TURNS_4 && this.phase == 3) {
			this.moves.hand(level, this, this.group());
		}
	}

	/** Leash, rest heal and reset (the temple form). */
	private void tending(ServerLevel level) {
		if (this.home == null || this.quarry != null) {
			return;
		}
		if (this.tickCount % 20 != 0) {
			return;
		}
		if (this.getTarget() == null && this.blockPosition().distSqr(this.home) > 9.0 && this.counted(level).isEmpty() == false) {
			this.getNavigation().moveTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5, 1.0);
		}
		if (this.kind < 0) {
			return;
		}
		if (!this.counted(level).isEmpty()) {
			this.noFoe = 0;
			return;
		}
		this.noFoe += 20;
		if (this.getHealth() < this.getMaxHealth()) {
			this.heal(this.getMaxHealth() * REST_HEAL);
		}
		if (this.noFoe >= RESET_TICKS) {
			this.resetFight(level);
		}
	}

	/** Back to its altar, whole, and asleep. */
	private void resetFight(ServerLevel level) {
		this.cancelCast();
		this.moves.clear(this);
		PatronChampions.clearAdds(level, this);
		for (var attr : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE)) {
			AttributeInstance i = this.getAttribute(attr);
			if (i != null) {
				i.removeModifier(HEALTH_ID);
				i.removeModifier(DAMAGE_ID);
			}
		}
		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_ID);
		}
		this.setHealth(this.getMaxHealth());
		this.phase = 0;
		this.group = 0;
		this.tier = 0;
		this.turns = TURNS_3;
		this.turningLeft = 0;
		this.entityData.set(TURNING, false);
		this.hitters.clear();
		this.awakened = false;
		this.noFoe = 0;
		this.setTarget(null);
		this.bar.removeAllPlayers();
		this.bar.setVisible(false);
		if (this.home != null) {
			this.teleportTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5);
			this.setYRot(this.restYaw);
			this.setYHeadRot(this.restYaw);
			this.setYBodyRot(this.restYaw);
		}
		for (int i = 0; i < this.cooldowns.length; i++) {
			this.cooldowns[i] = 0;
		}
		this.entityData.set(DORMANT, true);
	}

	/** No blow and no path for a while: it blinks beside its target. */
	private void stuck(ServerLevel level) {
		LivingEntity t = this.getTarget();
		if (t == null || this.castLeft > 0) {
			return;
		}
		if (this.tickCount - Math.max(this.lastBlowAt, this.lastPathAt) > STUCK_TICKS) {
			Vec3 spot = MoveKit.blinkSpot(level, t, 2.0, 4.0);
			if (spot != null) {
				this.blinkTo(spot);
			}
			this.lastBlowAt = this.tickCount;
			this.lastPathAt = this.tickCount;
		}
	}

	// ---- blows ---------------------------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.decoy()) {
			if (source.getEntity() != null || amount > 0.0F) {
				this.moves.clear(this);
				PatronChampions.decoyBurst(level, this);
				this.discard();
				return true;
			}
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		if (this.dormant()) {
			if (source.getEntity() instanceof ServerPlayer sp) {
				if (WorldTiers.of(sp).ordinal() >= WAKE_TIER) {
					this.wake(level, true);
				} else {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron_champion.unworthy"));
					level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 0.6F, 0.5F);
				}
			}
			return false;
		}
		if (this.turningLeft > 0) {
			return false;
		}
		float scaled = this.moves.hurt(this, source, amount);
		if (scaled <= 0.0F) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, scaled);
		if (hurt) {
			this.moves.afterHurt(this, source);
		}
		if (hurt && source.getEntity() instanceof ServerPlayer sp) {
			this.hitters.add(sp.getUUID());
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (this.dormant() || this.turningLeft > 0) {
			return false;
		}
		AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
		boolean boosted = this.nextBlow != 1.0F && this.tickCount <= this.nextBlowUntil && damage != null;
		if (boosted) {
			damage.addOrUpdateTransientModifier(new AttributeModifier(BLOW_ID, this.nextBlow - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		boolean hit = super.doHurtTarget(level, target);
		if (boosted) {
			damage.removeModifier(BLOW_ID);
			this.nextBlow = 1.0F;
		}
		if (hit) {
			this.lastBlowAt = this.tickCount;
		}
		return hit;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == WAKE) {
			this.wokeAt = this.tickCount;
		} else if (id == CAST) {
			this.castAt = this.tickCount;
		} else if (id == TURN) {
			this.turnAt = this.tickCount;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		this.bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bar.removePlayer(player);
	}

	/** Its moves' lingering effects end (it fell). */
	void clearMoves() {
		this.moves.clear(this);
		this.bar.removeAllPlayers();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return this.patron == Patron.DEEP_MOTHER || super.canBreatheUnderwater();
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
	}

	// ---- saving --------------------------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("temple", this.templeId);
		output.putInt("kind", this.kind);
		output.putBoolean("decoy", this.decoy());
		output.putBoolean("dormant", this.dormant());
		if (this.home != null) {
			output.putInt("home_x", this.home.getX());
			output.putInt("home_y", this.home.getY());
			output.putInt("home_z", this.home.getZ());
			output.putFloat("rest_yaw", this.restYaw);
		}
		if (this.quarry != null) {
			output.putString("quarry", this.quarry.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.templeId = input.getIntOr("temple", -1);
		this.kind = input.getIntOr("kind", -1);
		this.entityData.set(DECOY, input.getBooleanOr("decoy", false));
		this.entityData.set(DORMANT, input.getBooleanOr("dormant", false));
		if (input.getIntOr("home_y", Integer.MIN_VALUE) != Integer.MIN_VALUE) {
			this.home = new BlockPos(input.getIntOr("home_x", 0), input.getIntOr("home_y", 0), input.getIntOr("home_z", 0));
			this.restYaw = input.getFloatOr("rest_yaw", this.getYRot());
		}
		String q = input.getStringOr("quarry", "");
		if (!q.isEmpty()) {
			try {
				this.quarry = UUID.fromString(q);
			} catch (IllegalArgumentException e) {
				this.quarry = null;
			}
		}
		if (this.decoy()) {
			this.awakened = true;
		}
		this.bar.setVisible(false);
	}
}
