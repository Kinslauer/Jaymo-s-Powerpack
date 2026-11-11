package com.jaymoskingdoms;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-04), after Special Golems (ideas only, none of its files): "a magically attuned kingdom using golems
 * like these". A town golem is a rune golem of our own, each kind with a body of its own (GeckoLib models, paint and
 * animations from {@code kingdoms/tools/gen_town_golems.py}, drawn by {@code client/TownGolemRenderer}; Andy, 2026-11-11:
 * "The village golems all need redesigns, the throne golem NEEDS to turn into a throne"):
 * <ul>
 * <li>{@link Kind#GILT} the <b>lamp warden</b>, an iron golem's ally: a sandstone lamplighter whose head is a lantern. It
 * glows through the night and burns monsters (undead twice over) with a beam from the lantern.</li>
 * <li>{@link Kind#VERDANT} the <b>mender</b>: a mossy boulder with emerald crystals on its back. It holds out its hands
 * and knits the hurt of those it looks after, and once in a while keeps one from dying
 * ({@link Golemworks#sparedByMender}).</li>
 * <li>{@link Kind#DIAMOND} the <b>throne golem</b>: a walking keep of dark masonry set with diamond that on its ruler's
 * word sits and folds itself into a great throne (its thighs the seat and its shins the step before it, its keep slid
 * back and a plinth let down under it as the back, its forearms the armrests, a crenellated crest raised behind its head
 * as the high back). Andy: "I want the diamond one to be a throne, that takes the damage from the seat holder ... it just
 * needs to transform into a giant throne", then: "this should only answer to the realm's king, so noone else can use it.
 * and it will always absorb the damage from the king or queen, not just while they sit. If it takes more than 20 damage,
 * it will transform into the guard mode, and attack whoever was attacking the royal." So it serves its realm's ruler
 * alone ({@link #ruler}): only they bid it kneel or rise, sit in it, or fold it up; every blow the ruler takes within
 * {@link #GUARD_RANGE} of it, the golem takes instead ({@link #shieldRuler}); and once it has taken more than
 * {@link #RISE_AT} so, it rises out of its throne and goes for whoever struck, kneeling back into its throne at home once
 * the fight is done.</li>
 * </ul>
 * An attuned realm keeps its own ({@link Golemworks}); anyone who knows Golemancy can make one on the infusion altar and set
 * it down where it should work (a throne golem serves the realm its maker rules, else the realm it's set down in, and stands
 * up nowhere else). Its maker sneaks and uses a lamp warden or mender with an empty hand to fold it back into its item; a
 * throne golem, only its ruler: use it to have it kneel into its throne, use the throne to sit (sneak to get up), sneak and
 * use the throne to have it stand, sneak and use it standing to fold it up.
 *
 * <p>Animations: a "move" controller picks the loop from what the golem is doing (idle, walk, the throne and the fold into
 * or out of it, death); the server starts the one-off moves as they happen (the warden's beam, the mender's mending, the
 * throne golem's blow, a flinch when hurt).
 */
public class TownGolem extends AbstractGolem implements GeoEntity {
	public static EntityType<TownGolem> TYPE;

	public enum Kind {
		GILT("gilt", 160.0, 0.18, 1.15F, 0.86F),
		VERDANT("verdant", 90.0, 0.24, 0.95F, 0.45F),
		DIAMOND("diamond", 300.0, 0.16, 1.25F, 0.68F);

		public final String id;
		final double health, speed;
		final float scale;
		/** Where its light leaves it, as a share of its height: the warden's lantern, the mender's hands, the throne golem's core. */
		final float beamFrom;
		public Item item;

		Kind(String id, double health, double speed, float scale, float beamFrom) {
			this.id = id;
			this.health = health;
			this.speed = speed;
			this.scale = scale;
			this.beamFrom = beamFrom;
		}

		public static Kind byId(int i) {
			Kind[] all = values();
			return all[Math.floorMod(i, all.length)];
		}

		/** What mends it, a handful at a time. */
		Item mend() {
			return switch (this) {
				case GILT -> Items.GOLD_INGOT;
				case VERDANT -> Items.EMERALD;
				case DIAMOND -> Items.DIAMOND;
			};
		}
	}

	// ---- tuning ----
	/** How far from its home it works: a realm's golem round its seat, one set down round where it was set. */
	static final int REALM_RANGE = 40, SET_RANGE = 16;
	/** Lamp warden: its beam's reach, harm (doubled on the undead), and how often. */
	static final double BEAM_RANGE = 16.0;
	static final float BEAM_HARM = 6.0F;
	static final int BEAM_EVERY = 30;
	/** Mender: hurt folk this near get Regeneration II this long, each at most this often (ticks). */
	static final double MEND_RANGE = 10.0;
	static final int MEND_TICKS = 100, MEND_EVERY = 400;
	/** Throne golem: its armour, blow; how long kneeling into the throne or rising out of it takes (ticks). */
	static final double THRONE_ARMOR = 14.0, THRONE_DAMAGE = 14.0;
	public static final int FOLD_TICKS = 30;
	/** Where a sitter sits, in the throne's own blocks before its scale: above its feet (the seat's top, 13 of the model's
	 * 16ths), and forward of its middle. */
	static final double SEAT_Y = 0.8125, SEAT_Z = 0.1;
	/** Throne golem: it shields its ruler this near; rises to fight once it has taken more than this for them; the tally
	 * clears after this long with no blow (ticks); and with nothing to fight this long it goes home and kneels again. */
	static final double GUARD_RANGE = 48.0;
	static final float RISE_AT = 20.0F;
	static final int TALLY_CLEARS = 600, CALM_TICKS = 200;
	/** Rulers kept by a throne golem (entity id → game time a golem last vouched for them), so a blow is checked quickly. */
	private static final Map<UUID, Long> GUARDED = new HashMap<>();

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TownGolem.class, EntityDataSerializers.INT);
	/** Synced: a lamp warden's light is up (night). */
	private static final EntityDataAccessor<Boolean> LIT = SynchedEntityData.defineId(TownGolem.class, EntityDataSerializers.BOOLEAN);
	/** Synced: a throne golem is folded into its throne. */
	private static final EntityDataAccessor<Boolean> THRONE = SynchedEntityData.defineId(TownGolem.class, EntityDataSerializers.BOOLEAN);

	/** Its realm (-1: one set down by a player). */
	private int faction = -1;
	private @Nullable UUID owner;
	private int cooldown;
	/** (Client) the tick it last knelt or rose. */
	private int changedAt = -1000;

	public TownGolem(EntityType<? extends TownGolem> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("town_golem"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<TownGolem>of(TownGolem::new, MobCategory.MISC)
			.sized(1.0F, 2.2F).clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createGolemAttributes());
		for (Kind kind : Kind.values()) {
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(kind.id + "_golem"));
			kind.item = Registry.register(BuiltInRegistries.ITEM, itemKey, new Builder(new Item.Properties().setId(itemKey).stacksTo(1)
				.rarity(kind == Kind.DIAMOND ? Rarity.RARE : Rarity.UNCOMMON), kind));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Kind kind : Kind.values()) {
				output.accept(kind.item);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(TownGolem::shieldRuler);
	}

	public static AttributeSupplier.Builder createGolemAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.MOVEMENT_SPEED, 0.25)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.ARMOR, 4.0);
	}

	/** A new golem of {@code kind}, working round {@code home}: for realm {@code faction} (-1 none), set down by {@code owner}. */
	static @Nullable TownGolem make(ServerLevel level, Kind kind, BlockPos home, int faction, @Nullable UUID owner) {
		TownGolem golem = TYPE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (golem == null) {
			return null;
		}
		golem.setKind(kind);
		golem.faction = faction;
		golem.owner = owner;
		golem.setHomeTo(home, faction >= 0 ? REALM_RANGE : SET_RANGE);
		golem.setPersistenceRequired();
		return golem;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(KIND, 0);
		entityData.define(LIT, false);
		entityData.define(THRONE, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
			@Override
			public boolean canUse() {
				return TownGolem.this.kind() == Kind.DIAMOND && TownGolem.this.afoot() && super.canUse();
			}
		});
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return TownGolem.this.afoot() && super.canUse();
			}
		});
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F) {
			@Override
			public boolean canUse() {
				return !TownGolem.this.throne() && super.canUse();
			}
		});
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !TownGolem.this.throne() && super.canUse();
			}
		});
		// the throne golem fights what threatens those it keeps, as an iron golem does
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
			(target, level) -> this.kind() == Kind.DIAMOND && !this.throne() && target instanceof Enemy));
	}

	public Kind kind() {
		return Kind.byId(this.entityData.get(KIND));
	}

	void setKind(Kind kind) {
		this.entityData.set(KIND, kind.ordinal());
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(kind.health);
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(kind.speed);
		this.getAttribute(Attributes.SCALE).setBaseValue(kind.scale);
		if (kind == Kind.DIAMOND) {
			this.getAttribute(Attributes.ARMOR).setBaseValue(THRONE_ARMOR);
			this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(THRONE_DAMAGE);
			this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		}
		this.setHealth(this.getMaxHealth());
		this.refreshDimensions();
	}

	public boolean lit() {
		return this.entityData.get(LIT);
	}

	public boolean throne() {
		return this.entityData.get(THRONE);
	}

	/** On its feet and done unfolding: free to walk and strike. */
	boolean afoot() {
		return !this.throne() && this.tickCount - this.risenAt >= FOLD_TICKS;
	}

	/** (Client) how long since it last knelt into its throne or rose from it (ticks). */
	public int sinceChanged() {
		return this.tickCount - this.changedAt;
	}

	public int faction() {
		return this.faction;
	}

	public @Nullable UUID owner() {
		return this.owner;
	}

	/** Kneels into its throne ({@code true}) or rises out of it, putting off whoever sat in it. */
	void setThrone(boolean throne) {
		if (this.kind() != Kind.DIAMOND || this.throne() == throne) {
			return;
		}
		this.entityData.set(THRONE, throne);
		if (throne) {
			this.kneltAt = this.tickCount;
		} else {
			this.risenAt = this.tickCount;
		}
		this.getNavigation().stop();
		this.setTarget(null);
		if (!throne) {
			this.ejectPassengers();
		}
		this.level().broadcastEntityEvent(this, throne ? KNEEL_EVENT : RISE_EVENT);
		this.playSound(throne ? SoundEvents.DEEPSLATE_BRICKS_PLACE : SoundEvents.DEEPSLATE_BRICKS_BREAK, 1.4F, 0.6F);
		this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, throne ? 0.7F : 0.9F);
	}

	public static final byte KNEEL_EVENT = 96, RISE_EVENT = 97;

	@Override
	public void handleEntityEvent(byte id) {
		if (id == KNEEL_EVENT || id == RISE_EVENT) {
			this.changedAt = this.tickCount;
			this.sawKneel = id == KNEEL_EVENT;
			this.sawRise = id == RISE_EVENT;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean isPushable() {
		return !this.throne() && super.isPushable();
	}

	// ---- the throne ----------------------------------------------------------------------------------------------

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.throne() && this.getPassengers().isEmpty() && this.isRuler(passenger);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0.0, SEAT_Y * scale, SEAT_Z * scale).yRot(-this.yBodyRot * Mth.DEG_TO_RAD);
	}

	/** The ruler this throne golem serves: its realm's king or queen (null if it has none now). Looked up now and then. */
	@Nullable UUID ruler() {
		if (this.faction < 0 || !(this.level() instanceof ServerLevel level)) {
			return null;
		}
		if (this.rulerCheckedAt == 0 || this.tickCount - this.rulerCheckedAt >= 100) {
			this.rulerCheckedAt = this.tickCount;
			Faction f = Factions.get(level.getServer(), this.faction);
			this.cachedRuler = f == null || f.fallen() ? null : f.leader().orElse(null);
		}
		return this.cachedRuler;
	}

	private @Nullable UUID cachedRuler;
	private int rulerCheckedAt;
	/** What it has taken for its ruler lately, when it last took a blow, whether it rose from its throne to fight, how long calm. */
	private float tally;
	private long lastBlow;
	private boolean roseToFight;
	private int calm;

	boolean isRuler(@Nullable Entity e) {
		UUID r = this.ruler();
		return e != null && r != null && r.equals(e.getUUID());
	}

	/** A blow meant for a ruler with a throne golem near: the golem takes it, and past {@link #RISE_AT} rises to fight. */
	private static boolean shieldRuler(LivingEntity victim, DamageSource source, float amount) {
		if (GUARDED.isEmpty() || !GUARDED.containsKey(victim.getUUID()) || amount <= 0.0F || !(victim.level() instanceof ServerLevel level)
			|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.DROWN)) {
			return true;
		}
		TownGolem guard = level.getEntitiesOfClass(TownGolem.class, victim.getBoundingBox().inflate(GUARD_RANGE),
				g -> g.isAlive() && g.kind() == Kind.DIAMOND && g.isRuler(victim))
			.stream().min(java.util.Comparator.comparingDouble(g -> g.distanceToSqr(victim))).orElse(null);
		if (guard == null) {
			return true;
		}
		guard.takeFor(level, victim, source, amount);
		return false;
	}

	private void takeFor(ServerLevel level, LivingEntity ruler, DamageSource source, float amount) {
		long now = level.getGameTime();
		if (now - this.lastBlow > TALLY_CLEARS) {
			this.tally = 0.0F;
		}
		this.lastBlow = now;
		this.tally += amount;
		this.hurtServer(level, source, amount);
		this.beam(level, ruler, 0x7FEFE0);
		level.playSound(null, ruler.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 1.0F, 0.6F);
		LivingEntity by = source.getEntity() instanceof LivingEntity l && l != ruler && l != this ? l : null;
		if (this.tally > RISE_AT && this.isAlive()) {
			if (this.throne()) {
				this.roseToFight = true;
				this.setThrone(false);
			}
			if (by != null) {
				this.setTarget(by);
			}
			this.calm = 0;
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.throne()) {
			// a throne doesn't turn or walk
			this.setYRot(this.yBodyRot);
			this.yHeadRot = this.yBodyRot;
		}
	}

	// ---- work ----------------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.cooldown > 0) {
			this.cooldown--;
		}
		switch (this.kind()) {
			case GILT -> {
				this.entityData.set(LIT, level.isDarkOutside());
				this.lamp(level);
			}
			case VERDANT -> this.mender(level);
			case DIAMOND -> this.guard(level);
		}
	}

	private void guard(ServerLevel level) {
		UUID r = this.ruler();
		if (r != null && this.tickCount % 20 == 0) {
			GUARDED.put(r, level.getGameTime());
		}
		if (this.tickCount % 200 == 0) {
			long now = level.getGameTime();
			GUARDED.values().removeIf(t -> now - t > 200);
		}
		if (!this.afoot()) {
			this.getNavigation().stop();
			return;
		}
		// risen to fight: once it's over, home and kneel again
		if (!this.roseToFight || this.getTarget() != null && this.getTarget().isAlive()) {
			this.calm = 0;
			return;
		}
		if (++this.calm < CALM_TICKS) {
			return;
		}
		BlockPos home = this.hasHome() ? this.getHomePosition() : this.blockPosition();
		if (this.distanceToSqr(Vec3.atBottomCenterOf(home)) > 16.0) {
			if (this.tickCount % 20 == 0) {
				this.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
			}
			return;
		}
		this.roseToFight = false;
		this.tally = 0.0F;
		this.setThrone(true);
	}

	private void lamp(ServerLevel level) {
		if (this.lit() && this.tickCount % 8 == 0) {
			level.sendParticles(Fx.LIGHT, this.getX(), this.getY() + this.getBbHeight() * 0.62, this.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
		}
		Mob foe = this.nearestFoe(level, BEAM_RANGE);
		if (foe == null || this.cooldown > 0) {
			return;
		}
		this.cooldown = BEAM_EVERY;
		this.getLookControl().setLookAt(foe);
		this.triggerAnim("action", "beam");
		boolean undead = foe.is(EntityTypeTags.UNDEAD);
		this.beam(level, foe, 0xFFE36A);
		foe.hurtServer(level, this.damageSources().indirectMagic(this, this), undead ? BEAM_HARM * 2.0F : BEAM_HARM);
		if (undead) {
			foe.igniteForSeconds(3.0F);
		}
		level.playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.6F, 1.6F);
	}

	private void mender(ServerLevel level) {
		if (this.tickCount % 40 != 0) {
			return;
		}
		long now = level.getGameTime();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(MEND_RANGE),
			e -> e.isAlive() && e.getHealth() < e.getMaxHealth() && this.watches(e))) {
			Long last = e.getAttached(Golemworks.MENDED);
			if (last != null && now - last < MEND_EVERY) {
				continue;
			}
			e.setAttached(Golemworks.MENDED, now);
			this.getLookControl().setLookAt(e);
			this.triggerAnim("action", "mend");
			e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEND_TICKS, 1), this);
			this.beam(level, e, 0x7FF09A);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
			return;
		}
	}

	private @Nullable Mob nearestFoe(ServerLevel level, double range) {
		List<Mob> foes = level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(range),
			m -> m.isAlive() && m instanceof Enemy && !m.isInvisible() && this.hasLineOfSight(m) && !(m instanceof TownGolem));
		return foes.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
	}

	/** A line of motes from its core to {@code to}. */
	private void beam(ServerLevel level, Entity to, int colour) {
		Vec3 from = this.position().add(0, this.getBbHeight() * this.kind().beamFrom, 0);
		Fx.beam(level, from, to.position().add(0, to.getBbHeight() * 0.5, 0), Sorcery.BEAM_RADIUS * 1.3F, colour, Sorcery.BEAM_TICKS);
	}

	/** Whom it looks after: its maker and their party and pets, the town's folk, other golems, and players in good standing with its realm. */
	boolean watches(LivingEntity e) {
		if (e == this || e instanceof Enemy) {
			return false;
		}
		if (e instanceof TownGolem || e instanceof net.minecraft.world.entity.animal.golem.IronGolem || Deeds.friendly(e)) {
			return true;
		}
		if (e instanceof OwnableEntity pet && pet.getOwnerReference() != null && this.owner != null
			&& this.owner.equals(pet.getOwnerReference().getUUID())) {
			return true;
		}
		if (e instanceof ServerPlayer player) {
			if (this.owner != null) {
				ServerPlayer maker = player.level().getServer().getPlayerList().getPlayer(this.owner);
				return player.getUUID().equals(this.owner) || maker != null && Parties.allied(maker, player);
			}
			return this.faction >= 0 && Reputation.get(player, this.faction) >= 0;
		}
		return false;
	}

	// ---- hands-on ------------------------------------------------------------------------------------------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(this.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		if (this.kind() == Kind.DIAMOND) {
			return this.throneInteract(level, player, stack, hand);
		}
		boolean maker = this.owner != null && this.owner.equals(player.getUUID());
		if (stack.isEmpty() && maker && player.isShiftKeyDown()) {
			this.foldUp(level, player);
			return InteractionResult.SUCCESS;
		}
		if (this.mend(player, stack)) {
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	/** A throne golem answers its ruler alone; anyone may mend it. */
	private InteractionResult throneInteract(ServerLevel level, Player player, ItemStack stack, InteractionHand hand) {
		if (this.mend(player, stack)) {
			return InteractionResult.SUCCESS;
		}
		if (!stack.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!this.isRuler(player)) {
			if (player instanceof ServerPlayer sp) {
				sp.sendSystemMessage(Component.translatable("golem.jaymoskingdoms.throne.not_yours"), true);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			if (this.throne()) {
				this.setThrone(false);
			} else {
				this.foldUp(level, player);
			}
		} else if (!this.throne()) {
			this.roseToFight = false;
			this.setThrone(true);
		} else if (this.getPassengers().isEmpty() && this.sinceKnelt() >= FOLD_TICKS) {
			player.startRiding(this);
		}
		return InteractionResult.SUCCESS;
	}

	private void foldUp(ServerLevel level, Player player) {
		ItemStack item = new ItemStack(this.kind().item);
		if (!player.getInventory().add(item)) {
			this.spawnAtLocation(level, item);
		}
		level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 0.8F);
		this.discard();
	}

	private boolean mend(Player player, ItemStack stack) {
		if (!stack.is(this.kind().mend()) || this.getHealth() >= this.getMaxHealth()) {
			return false;
		}
		this.heal((float)(this.kind().health / 8.0));
		stack.consume(1, player);
		this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.2F);
		return true;
	}

	/** (Server) ticks since it knelt; it can be sat in once it's down. */
	private int sinceKnelt() {
		return this.tickCount - this.kneltAt;
	}

	private int kneltAt = -1000, risenAt = -1000;

	// ---- body ------------------------------------------------------------------------------------------------------

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		Item bits = switch (this.kind()) {
			case GILT -> Items.GOLD_NUGGET;
			case VERDANT -> Items.EMERALD;
			case DIAMOND -> Items.DIAMOND;
		};
		int n = this.kind() == Kind.DIAMOND ? 1 + this.random.nextInt(3) : 2 + this.random.nextInt(4);
		this.spawnAtLocation(level, new ItemStack(bits, n));
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.kind() == Kind.DIAMOND ? SoundEvents.DEEPSLATE_BRICKS_HIT : SoundEvents.STONE_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.kind() == Kind.DIAMOND ? SoundEvents.DEEPSLATE_BRICKS_BREAK : SoundEvents.STONE_BREAK;
	}

	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		this.playSound(this.kind() == Kind.DIAMOND ? SoundEvents.DEEPSLATE_BRICKS_STEP : SoundEvents.STONE_STEP, 0.8F, 0.7F);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		this.triggerAnim("action", "smash");
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.4, 0.0));
			this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.7F);
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive() && !this.throne()) {
			this.triggerAnim("action", "hurt");
		}
		return hurt;
	}

	// ---- animations (GeckoLib) -------------------------------------------------------------------------------------

	/** The move controller's cross-fade (ticks). */
	static final int ANIMATION_BLEND = 4;
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
	private static final RawAnimation THRONE_HELD = RawAnimation.begin().thenLoop("throne");
	private static final RawAnimation KNEEL = RawAnimation.begin().thenPlay("kneel").thenLoop("throne");
	private static final RawAnimation RISE = RawAnimation.begin().thenPlay("rise").thenLoop("idle");
	private static final RawAnimation BEAM = RawAnimation.begin().thenPlay("beam");
	private static final RawAnimation MEND = RawAnimation.begin().thenPlay("mend");
	private static final RawAnimation SMASH = RawAnimation.begin().thenPlay("smash");
	private static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** (Client) it was seen to kneel or rise (one that comes into view already a throne is drawn folded at once). */
	private boolean sawKneel, sawRise;

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<TownGolem>("move", ANIMATION_BLEND, test -> {
			TownGolem golem = test.animatable();
			if (golem.isDeadOrDying()) {
				return test.setAndContinue(DEATH);
			}
			if (golem.throne()) {
				return test.setAndContinue(golem.sawKneel ? KNEEL : THRONE_HELD);
			}
			if (golem.sawRise && golem.sinceChanged() < FOLD_TICKS) {
				return test.setAndContinue(RISE);
			}
			return test.setAndContinue(test.isMoving() ? WALK : IDLE);
		}));
		controllers.add(new AnimationController<TownGolem>("action", 2, test -> PlayState.STOP).triggerableAnim("beam", BEAM)
			.triggerableAnim("mend", MEND).triggerableAnim("smash", SMASH).triggerableAnim("hurt", HURT));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("kind", this.kind().ordinal());
		output.putInt("faction", this.faction);
		output.putBoolean("throne", this.throne());
		if (this.owner != null) {
			output.putString("owner", this.owner.toString());
		}
		if (this.hasHome()) {
			output.putLong("home", this.getHomePosition().asLong());
			output.putInt("home_range", this.getHomeRadius());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(KIND, input.getIntOr("kind", 0));
		// one saved at an older size takes its kind's size as it is now
		this.getAttribute(Attributes.SCALE).setBaseValue(this.kind().scale);
		this.entityData.set(THRONE, input.getBooleanOr("throne", false));
		this.faction = input.getIntOr("faction", -1);
		this.owner = input.getString("owner").map(UUID::fromString).orElse(null);
		input.getLong("home").ifPresent(h -> this.setHomeTo(BlockPos.of(h), input.getIntOr("home_range", SET_RANGE)));
	}

	static final class Builder extends Item {
		final Kind kind;

		Builder(Item.Properties properties, Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			Player player = context.getPlayer();
			int realm = -1;
			if (this.kind == Kind.DIAMOND) {
				// it serves a crown: the realm its maker rules, else the realm it's set down in
				realm = throneRealm(level, player, at);
				if (realm < 0) {
					if (player != null) {
						if (player instanceof ServerPlayer sp) {
							sp.sendSystemMessage(Component.translatable("golem.jaymoskingdoms.throne.no_crown"), true);
						}
					}
					return InteractionResult.FAIL;
				}
			}
			TownGolem golem = make(level, this.kind, at, realm, player != null ? player.getUUID() : null);
			if (golem == null) {
				return InteractionResult.FAIL;
			}
			float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
			golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
			golem.yHeadRot = yaw;
			golem.yBodyRot = yaw;
			if (!level.noCollision(golem)) {
				return InteractionResult.FAIL;
			}
			level.addFreshEntity(golem);
			level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.sendParticles(Fx.GLYPH, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.5);
			context.getItemInHand().consume(1, player);
			return InteractionResult.SUCCESS;
		}

		private static int throneRealm(ServerLevel level, @Nullable Player player, BlockPos at) {
			if (player != null) {
				for (Faction f : Factions.all(level.getServer())) {
					if (f.type().isRealm() && !f.fallen() && f.leader().map(player.getUUID()::equals).orElse(false)) {
						return f.id();
					}
				}
			}
			Faction here = Factions.around(level.getServer(), level, at, Golemworks.COUNT_RANGE);
			return here != null && here.type().isRealm() && !here.fallen() ? here.id() : -1;
		}
	}
}
