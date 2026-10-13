package com.jaymoskingdoms;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A faction's people: its {@link #KING} (a boss: heavy armour, a great sword, a boss bar, and he hits back hard, with
 * phases as he weakens) or a fiefdom's {@link #LORD} (the same, a little weaker); its {@link #GUARD}s, who keep to the
 * seat, cut down monsters, and attack anyone the faction counts as hostile ({@link Reputation#HOSTILE}) or who strikes
 * one of their own side; its {@link #ARCHER}s; and its builders, a {@link #FOREMAN} and his {@link #WORKER}s, who don't
 * fight (their work is {@link Builders}'). Each role is a {@link Role} ({@link #defineRole} adds more). They dress in
 * their faction's style ({@link #style()}: its type) and colour ({@link #colorId()}: a fiefdom's overlord's). Never
 * despawn.
 */
public class Folk extends PathfinderMob implements net.minecraft.world.entity.monster.RangedAttackMob, Worker, Aging {
	/**
	 * Guards fight hand to hand; archers keep their posts on walls and towers and shoot; the king (and a fiefdom's lord)
	 * is the boss; the foreman and workers build. {@link #BARBARIAN} and {@link #CHIEF} are reserved for camps (their
	 * agent defines them with {@link #defineRole}).
	 */
	public static final int GUARD = 0, KING = 1, ARCHER = 2, LORD = 3, FOREMAN = 4, WORKER = 5, BARBARIAN = 6, CHIEF = 7;
	/** (0.5.1) A settlement's farmer: tends its fields and herds and carries what they give to its storage ({@link Farmers}). */
	public static final int FARMER = 8;
	/** (0.5.2) A settlement's miner and woodcutter: they work its mine and its trees and carry what they get to its storage ({@link Gatherers}). */
	public static final int MINER = 9, WOODCUTTER = 10;
	/** (0.5.14) A fiefdom's carter (a caravan's) or a king's coachman ({@link Caravans}, {@link Carriages}). */
	public static final int CARTER = 11;
	/** (0.5.26) A settlement's rickshaw puller: hauls its gatherers' loads, runs errands, carries its people and paying players ({@link Rickshaws}). */
	public static final int PULLER = 12;
	/** (0.5.47) A stable's stablehand (boards and sells horses) and farrier (shoes them, sells tack) ({@link Stables}). */
	public static final int STABLEHAND = 13, FARRIER = 14;

	/**
	 * A role: its saved name, whether it fights ({@code combatant}: melee unless it's an archer; non-combatants flee
	 * when hurt), whether it leads its faction ({@code leader}: a boss bar of {@code bar}'s colour, a rally), and how it's
	 * set up ({@code outfit}: its gear, health and name).
	 */
	public record Role(int id, String name, boolean combatant, boolean leader, BossEvent.@org.jspecify.annotations.Nullable BossBarColor bar,
		java.util.function.BiConsumer<Folk, Faction> outfit) {
	}

	private static final java.util.Map<Integer, Role> ROLES = new java.util.HashMap<>();

	/** Adds (or replaces) a role. */
	public static void defineRole(Role role) {
		ROLES.put(role.id(), role);
	}

	public static @org.jspecify.annotations.Nullable Role role(int id) {
		return ROLES.get(id);
	}

	/** A role's saved name (rosters are keyed by it); "folk_N" for one not defined. */
	public static String roleName(int id) {
		Role role = ROLES.get(id);
		return role == null ? "folk_" + id : role.name();
	}

	public static boolean isBuilder(int role) {
		return role == FOREMAN || role == WORKER;
	}

	/** The king's and the lord's health, and their blows. */
	static final double KING_HEALTH = 300.0, KING_DAMAGE = 9.0, LORD_HEALTH = 220.0, LORD_DAMAGE = 7.5;
	/** Builders: plain folk. */
	static final double BUILDER_HEALTH = 20.0, BUILDER_DAMAGE = 2.0;
	/** How fast a non-combatant flees when hurt. */
	static final double PANIC_SPEED = 1.3;
	/** (0.5.21) How near a monster comes before a non-combatant moves off. */
	static final float AVOID_RANGE = 8.0F;
	/** An archer's reach, and how often they loose (ticks). */
	static final double ARCHER_RANGE = 28.0;
	static final int ARCHER_INTERVAL = 30;
	/** How far from the capital they wander. */
	static final int HOME_RADIUS = 24;
	/** The king's second wind: at this share of his health he rallies (strength, resistance, his guards called in). */
	static final float RALLY_AT = 0.4F;

	private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> KINGDOM = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.INT);
	/** Its faction's type (ordinal) and the colour it wears (a dye id, -1 for none): what it looks like. */
	private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.INT);
	/** The land its faction's seat is in ({@link Climate} ordinal): its gear and the skins the client picks. */
	private static final EntityDataAccessor<Integer> CLIMATE = SynchedEntityData.defineId(Folk.class, EntityDataSerializers.INT);

	static {
		defineRole(new Role(GUARD, "guard", true, false, null, (folk, faction) -> {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			folk.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
			folk.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.guard_of", faction.name()));
		}));
		defineRole(new Role(ARCHER, "archer", true, false, null, (folk, faction) -> {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
			folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
			folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(ARCHER_RANGE + 4.0);
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.archer_of", faction.name()));
		}));
		defineRole(new Role(KING, "king", true, true, BossEvent.BossBarColor.YELLOW, (folk, faction) -> {
			folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(KING_HEALTH);
			folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(KING_DAMAGE);
			folk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
			folk.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(4.0);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
			folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			folk.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
			folk.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.king_of", faction.name()));
		}));
		defineRole(new Role(LORD, "lord", true, true, BossEvent.BossBarColor.PURPLE, (folk, faction) -> {
			folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(LORD_HEALTH);
			folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(LORD_DAMAGE);
			folk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
			folk.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2.0);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
			folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			folk.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
			folk.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
			// (0.5.9) a fiefdom's ruler is its baron, vassal or lord; a kingdom's lords are lords
			String title = faction.type() == FactionType.FIEFDOM ? Names.fiefTitle(faction.id() * 0x9E3779B97F4A7C15L) : "lord";
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms." + title + "_of", faction.name()));
		}));
		defineRole(new Role(FARMER, "farmer", false, false, null, (folk, faction) -> {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.farmer_of", faction.name()));
		}));
		defineRole(new Role(MINER, "miner", false, false, null, (folk, faction) -> {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.miner_of", faction.name()));
		}));
		defineRole(new Role(WOODCUTTER, "woodcutter", false, false, null, (folk, faction) -> {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.woodcutter_of", faction.name()));
		}));
		defineRole(new Role(PULLER, "puller", false, false, null, (folk, faction) -> {
			folk.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.puller_of", faction.name()));
		}));
		defineRole(new Role(STABLEHAND, "stablehand", false, false, null, (folk, faction) -> {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRUSH));
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.stablehand_of", faction.name()));
		}));
		defineRole(new Role(FARRIER, "farrier", false, false, null, (folk, faction) -> {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(com.jaymoskingdoms.equestrian.Tackroom.HORSESHOES));
			folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.farrier_of", faction.name()));
		}));
		defineRole(new Role(FOREMAN, "foreman", false, false, null, (folk, faction) -> {
			folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BUILDER_HEALTH);
			folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BUILDER_DAMAGE);
			folk.setItemSlot(EquipmentSlot.MAINHAND, folk.tool());
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.foreman_of", faction.name()));
		}));
		defineRole(new Role(WORKER, "worker", false, false, null, (folk, faction) -> {
			folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BUILDER_HEALTH);
			folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BUILDER_DAMAGE);
			folk.setItemSlot(EquipmentSlot.MAINHAND, folk.tool());
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.builder_of", faction.name()));
		}));
	}

	private ServerBossEvent bossBar;
	private boolean rallied;
	/** (0.5.21) Keeping out of a monster's way (its work waits). */
	private boolean shying;
	/** A builder carrying a load (it's in his main hand, his tool put away). */
	private boolean carrying;
	/** A builder's work: where (a site), and what (free text for {@link Builders}' use). Saved. */
	private @org.jspecify.annotations.Nullable BlockPos site;
	private String task = "";
	/** (0.5.1) The ruler it walks behind (a guard of his escort, {@link Royals}), its place in the column, and the post it goes back to. Saved. */
	private java.util.@org.jspecify.annotations.Nullable UUID escort;
	private int escortSlot;
	private @org.jspecify.annotations.Nullable BlockPos post;
	private int postRadius;
	/** (0.5.37) A caster's spells' cooldowns ({@link Sorcery}; not saved). */
	private final int[] spellTimers = new int[Sorcery.TIMERS];
	/** Which {@link Outfits#VERSION} dressed it (0: before faction looks, so it's dressed again on its next tick). Saved. */
	private int outfit;
	/** (0.5.54) When they came of age ({@link OldAge}). */
	private long grownSince = CourtWizards.UNKNOWN;

	public Folk(EntityType<? extends Folk> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		// (0.5.1: through doors, so a ruler can leave his hall and his people come and go)
		this.getNavigation().setCanOpenDoors(true);
	}

	public static AttributeSupplier.Builder guardAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 4.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ROLE, GUARD);
		entityData.define(KINGDOM, -1);
		entityData.define(STYLE, FactionType.KINGDOM.ordinal());
		entityData.define(COLOR, -1);
		entityData.define(CLIMATE, Climate.PLAINS.ordinal());
	}

	public int role() {
		return this.entityData.get(ROLE);
	}

	/** The faction it belongs to. */
	public int factionId() {
		return this.entityData.get(KINGDOM);
	}

	/** (0.5.59) Belongs to {@code id} (-1: to no one, as an assassin after {@link #setup}). */
	void setFactionId(int id) {
		this.entityData.set(KINGDOM, id);
	}

	/** (0.2's name for {@link #factionId}) */
	public int kingdomId() {
		return this.factionId();
	}

	/** Its faction's type: how it dresses. */
	public FactionType style() {
		int i = this.entityData.get(STYLE);
		return i >= 0 && i < FactionType.values().length ? FactionType.values()[i] : FactionType.KINGDOM;
	}

	/** The dye it wears (its faction's colour, or a fiefdom's overlord's), or -1. */
	public int colorId() {
		return this.entityData.get(COLOR);
	}

	/** The land its faction's seat is in. */
	public Climate climate() {
		return Climate.byId(this.entityData.get(CLIMATE));
	}

	public boolean combatant() {
		Role role = ROLES.get(this.role());
		return role == null || role.combatant();
	}

	/** An archer (or a goblin slinger, or a caster): it keeps its post and shoots. */
	public boolean ranged() {
		return this.role() == ARCHER || Goblins.ranged(this.role()) || Orcs.ranged(this.role()) || Sorcery.isCaster(this.role());
	}

	/** (0.5.37) A caster's spells' cooldowns (ticks left, by {@link Sorcery}'s slots). */
	int[] spellTimers() {
		return this.spellTimers;
	}

	public boolean leader() {
		Role role = ROLES.get(this.role());
		return role != null && role.leader();
	}

	/** The ruler it escorts, or null. */
	public java.util.@org.jspecify.annotations.Nullable UUID escort() {
		return this.escort;
	}

	int escortSlot() {
		return this.escortSlot;
	}

	/** Falls in behind {@code leader} at place {@code slot} of his escort; its post is kept to go back to. */
	void follow(java.util.UUID leader, int slot) {
		if (this.escort == null) {
			this.post = this.getHomePosition();
			this.postRadius = this.getHomeRadius();
		}
		this.escort = leader;
		this.escortSlot = slot;
	}

	/** Back to its post. */
	void unfollow() {
		if (this.escort == null) {
			return;
		}
		this.escort = null;
		if (this.post != null) {
			this.setHomeTo(this.post, this.postRadius);
		}
		this.post = null;
	}

	@Override
	public long grownSince() {
		return this.grownSince;
	}

	@Override
	public void setGrownSince(long time) {
		this.grownSince = time;
	}

	@Override
	public PathfinderMob mob() {
		return this;
	}

	/** A builder's tool, or a farmer's, miner's or woodcutter's (back in hand when he isn't carrying). */
	@Override
	public ItemStack tool() {
		return Worker.toolFor(this.role(), this.style() == FactionType.BARBARIAN);
	}

	/** What it's carrying (the load in its hands, not a copy), or empty. */
	public ItemStack carried() {
		return this.carrying ? this.getMainHandItem() : ItemStack.EMPTY;
	}

	public boolean isCarrying() {
		return this.carrying && !this.getMainHandItem().isEmpty();
	}

	/**
	 * Shows {@code load} in its hands (everyone sees it), its tool put away; empty puts the tool back. The load is the
	 * real items: it's dropped if the carrier dies.
	 */
	public void setCarried(ItemStack load) {
		this.carrying = !load.isEmpty();
		this.setItemSlot(EquipmentSlot.MAINHAND, this.carrying ? load : isBuilder(this.role()) || this.role() == FARMER || Gatherers.isGatherer(this.role()) ? this.tool() : ItemStack.EMPTY);
	}

	public @org.jspecify.annotations.Nullable BlockPos site() {
		return this.site;
	}

	public void setSite(@org.jspecify.annotations.Nullable BlockPos site) {
		this.site = site == null ? null : site.immutable();
	}

	public String task() {
		return this.task;
	}

	public void setTask(String task) {
		this.task = task == null ? "" : task;
	}

	/** Set up as {@code role} of {@code faction}: its look, gear and name, and the post it keeps to ({@code radius} of {@code home}). */
	public void setup(int role, Faction faction, BlockPos home, int radius) {
		this.entityData.set(ROLE, role);
		this.entityData.set(KINGDOM, faction.id());
		this.entityData.set(STYLE, faction.type().ordinal());
		net.minecraft.server.MinecraftServer server = this.level().getServer();
		this.entityData.set(COLOR, server == null ? faction.color().getId() : faction.styleColor(server).getId());
		Climate climate = this.level() instanceof ServerLevel level ? Climate.at(level, faction.seat()) : Climate.PLAINS;
		this.entityData.set(CLIMATE, climate.ordinal());
		this.setHomeTo(home, radius);
		Role spec = ROLES.getOrDefault(role, ROLES.get(GUARD));
		spec.outfit().accept(this, faction);
		// the faction's arms, its rank's and its land's gear (vanilla), then Epic Knights' (and Slavic Armory's) when loaded
		Outfits.dress(this, faction, climate);
		KnightsCompat.dress(this, faction, climate);
		this.outfit = Outfits.VERSION;
		this.setHealth(this.getMaxHealth());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0F);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new Shoot(this));
		this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.PanicGoal(this, PANIC_SPEED) {
			@Override
			public boolean canUse() {
				return !Folk.this.combatant() && super.canUse();
			}
		});
		// (0.5.21) the working folk keep out of monsters' way, as villagers do
		this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(this, net.minecraft.world.entity.monster.Monster.class, AVOID_RANGE, 0.8, PANIC_SPEED) {
			@Override
			public boolean canUse() {
				return !Folk.this.combatant() && super.canUse();
			}

			@Override
			public void start() {
				super.start();
				Folk.this.shying = true;
			}

			@Override
			public void stop() {
				super.stop();
				Folk.this.shying = false;
			}
		});
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
			@Override
			public boolean canUse() {
				return Folk.this.combatant() && !Folk.this.ranged() && super.canUse();
			}
		});
		// (0.5.1) a ruler's day out and his escort at his back; doors opened (and shut) on the way
		this.goalSelector.addGoal(3, new Royals.Round(this));
		this.goalSelector.addGoal(3, new Royals.Follow(this));
		this.goalSelector.addGoal(3, new Retinue.Harass(this));
		// (0.5.14) a caravan's carters and guards on the road
		this.goalSelector.addGoal(3, new Caravans.Drive(this));
		this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// whoever strikes one of them: only their own side (their faction and its allies) answers the call
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Folk.class) {
			@Override
			protected void alertOther(net.minecraft.world.entity.Mob other, LivingEntity target) {
				if (other instanceof Folk folk && folk.combatant() && Folk.this.sameSide(folk)) {
					super.alertOther(other, target);
				}
			}
		}.setAlertOthers(Folk.class));
		// anyone the faction counts as hostile; (0.6.4) and a player changed into an alien they don't know (Aliens)
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> this.combatant() && target instanceof ServerPlayer player && !player.isCreative()
				&& (Reputation.get(player, this.factionId()) <= Reputation.HOSTILE || Aliens.feared(player, this.factionId()))));
		// and monsters on their land
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.Mob.class, true,
			(target, level) -> this.combatant() && target instanceof Enemy && !(target instanceof net.minecraft.world.entity.monster.Creeper)));
		// barbarians hunt everyone else's people, and realms' folk hunt barbarians
		Barbarians.addGoals(this, this.goalSelector, this.targetSelector);
		// (0.5.27) goblins hunt everyone but those passing for one of them
		Goblins.addGoals(this, this.goalSelector, this.targetSelector);
		// (0.5.47) orcs hunt everyone but the other camps
		Orcs.addGoals(this, this.goalSelector, this.targetSelector);
		// realms at war: each other's folk (and sworn players) on sight
		Wars.addGoals(this, this.targetSelector);
	}

	/** {@code other} is on its side: the same faction, or one allied with it. */
	public boolean sameSide(Folk other) {
		if (other.factionId() == this.factionId()) {
			return true;
		}
		net.minecraft.server.MinecraftServer server = this.level().getServer();
		return server != null && Relations.between(server, this.factionId(), other.factionId()) == Relation.ALLIED;
	}

	/** Asleep, it lies still (its goals wait too). */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || this.isSleeping();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.isSleeping() && this.level() instanceof ServerLevel level) {
			Homes.asleep(level, this);
		}
		if (this.bossBar != null) {
			this.bossBar.setProgress(this.getHealth() / this.getMaxHealth());
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.role() == Assassins.ASSASSIN) {
			// (0.5.59) an assassin about his business, and nothing else
			Assassins.act(level, this);
			return;
		}
		if (this.outfit < Outfits.VERSION) {
			this.restyle(level);
		}
		if (this.tickCount % 20 == 0 && Worker.isWork(this.role()) && McaCompat.becomeVillager(level, this)) {
			// (0.5.21) with MCA, a realm's workers are its villagers: this one is one now
			return;
		}
		if (this.tickCount % 20 == 0 && this.role() == Sorcery.WIZARD && CourtWizards.retire(level, this)) {
			// (0.5.53) with MCA, a kingdom's court wizard is one of its elders: this one steps down
			return;
		}
		if (this.tickCount % 20 == 0 && OldAge.folk(level, this)) {
			// (0.5.54) they grow old, and at last die of it
			return;
		}
		if (!Rickshaws.onDuty(this) && Homes.tick(level, this)) {
			// (0.5.5) bedtime: its work waits till morning
		} else if (!this.shying) {
			Workers.work(level, this);
		}
		// (0.5.37) a caster's timers, closing in on its foe, its spells for its side
		if (Sorcery.isCaster(this.role())) {
			Sorcery.tick(level, this);
		}
		// (0.5.47) an orc's blood rage and war cries, a warchief's slam
		Orcs.tick(level, this);
		// (0.5.12) javelins at a foe out of reach; attackers fight harder, break through and burn
		// (0.5.64) the richest kingdoms' musketeers
		Muskets.tick(level, this);
		Javelins.tick(level, this);
		Assault.tick(level, this);
		if (this.leader() && this.tickCount % 5 == 0) {
			// (0.5.19) his bar: only for the players who've hit him, while they stay near
			BossBars.show(level, this, this.bar());
		}
		if (this.leader() && !this.rallied && this.getHealth() <= this.getMaxHealth() * RALLY_AT) {
			// his second wind: he rallies, and calls his guards in
			this.rallied = true;
			this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 60, 1));
			this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 1));
			this.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 60, 0));
			LivingEntity foe = this.getTarget();
			for (Folk guard : level.getEntitiesOfClass(Folk.class, this.getBoundingBox().inflate(48.0), f -> f.factionId() == this.factionId() && f != this && f.combatant())) {
				if (foe != null) {
					guard.setTarget(foe);
				}
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.RAID_HORN.value(),
				net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 1.0F);
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(32.0))) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.king_rallies", this.getDisplayName()).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** (0.5.64) Its gear put on again from its faction's arms (a musketeer's musket sold back). */
	void rearm(ServerLevel level) {
		this.restyle(level);
	}

	/** Dressed before its faction's looks (an older save): its land read and its gear put on again; a builder's or raider's hands kept. */
	private void restyle(ServerLevel level) {
		this.outfit = Outfits.VERSION;
		Faction faction = Factions.get(level.getServer(), this.factionId());
		if (faction == null) {
			return;
		}
		Climate climate = Climate.at(level, faction.seat());
		this.entityData.set(CLIMATE, climate.ordinal());
		ItemStack hand = this.getMainHandItem().copy();
		Outfits.dress(this, faction, climate);
		KnightsCompat.dress(this, faction, climate);
		if (!this.combatant() || this.role() == BARBARIAN || this.role() == CHIEF || Orcs.isOrc(this)) {
			this.setItemSlot(EquipmentSlot.MAINHAND, hand);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0F);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		if (this.bossBar != null) {
			this.bossBar.removePlayer(player);
		}
	}

	private ServerBossEvent bar() {
		if (this.bossBar == null) {
			Role role = ROLES.get(this.role());
			BossEvent.BossBarColor color = role != null && role.bar() != null ? role.bar() : BossEvent.BossBarColor.YELLOW;
			this.bossBar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		return this.bossBar;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			// (0.5.59) a stranger spoken to; a poisoned ruler cured; a ruler warned of the whispers
			if (Assassins.interact(serverPlayer, this, player.getItemInHand(hand))) {
				return InteractionResult.SUCCESS;
			}
			if (Goblins.isGoblin(this) && this.level() instanceof ServerLevel level) {
				// (0.5.27) a goblin grunts back at one of its own
				Disguise.greet(level, serverPlayer, this);
			}
			KingdomEvents.audience(serverPlayer, this);
			// (0.5.29) a king also has work for you
			if (this.role() == KING) {
				Quests.open(serverPlayer, this);
			}
			// (0.5.52) a court wizard can unmake what you've learned, for a price
			if (CourtWizards.is(this)) {
				Skills.wizard(serverPlayer, this);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level) {
			// (0.5.47) a knight's (or a raider's) horse goes wild when a player cuts him down
			Stables.riderFell(level, this, source);
			// (0.5.59) an assassin's purse and contract
			Assassins.died(level, this, source);
		}
		if (this.level() instanceof ServerLevel level && this.isCarrying()) {
			// whatever he was hauling falls where he does
			this.spawnAtLocation(level, this.takeCarried());
		}
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			KingdomEvents.folkDied(level, this, source);
			if (this.bossBar != null) {
				this.bossBar.removeAllPlayers();
			}
		}
	}

	/** An archer's shot (as a skeleton looses one); a caster's spell. */
	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		if (Sorcery.isCaster(this.role())) {
			Sorcery.cast(this, target);
			return;
		}
		ItemStack bow = this.getMainHandItem();
		ItemStack arrowStack = new ItemStack(Items.ARROW);
		net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(this, arrowStack, power, bow);
		double xd = target.getX() - this.getX();
		double yd = target.getY(0.3333333333333333) - arrow.getY();
		double zd = target.getZ() - this.getZ();
		double distance = Math.sqrt(xd * xd + zd * zd);
		if (this.level() instanceof ServerLevel level) {
			net.minecraft.world.entity.projectile.Projectile.spawnProjectileUsingShoot(arrow, level, arrowStack, xd, yd + distance * 0.2F, zd, 1.6F, 4.0F);
		}
		this.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
	}

	/** An archer at its post: it stays put, faces its target, and looses an arrow every so often while it can see it. */
	static final class Shoot extends net.minecraft.world.entity.ai.goal.Goal {
		private final Folk archer;
		private int cooldown;

		Shoot(Folk archer) {
			this.archer = archer;
			this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.archer.getTarget();
			if (Sorcery.isCaster(this.archer.role())) {
				// (0.5.37) a caster casts from where it stands while it can see its foe in reach; else it closes in (Sorcery.tick)
				return target != null && target.isAlive() && this.archer.distanceTo(target) <= Sorcery.RANGE && this.archer.getSensing().hasLineOfSight(target);
			}
			if (this.archer.getVehicle() instanceof Cannon && target != null && this.archer.distanceTo(target) >= Cannon.NEAREST) {
				// (0.5.64) a gunner fires his cannon (Cannon.tick); only a foe too close for it gets his arrows
				return false;
			}
			return this.archer.ranged() && target != null && target.isAlive() && this.archer.distanceTo(target) <= ARCHER_RANGE;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.archer.getTarget();
			if (target == null) {
				return;
			}
			this.archer.getNavigation().stop();
			this.archer.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (--this.cooldown <= 0 && this.archer.getSensing().hasLineOfSight(target)) {
				this.cooldown = Sorcery.isCaster(this.archer.role()) ? Sorcery.CAST_TICKS : ARCHER_INTERVAL;
				this.archer.performRangedAttack(target, 1.0F);
			}
		}
	}

	/**
	 * (0.5.14) A rider (in the saddle, in a carriage) or a caravan's man can't smother in a wall: a horse's head and a
	 * carriage brush through leaves and eaves, and a stuck caravan is set down further along its road.
	 */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return super.isInvulnerableTo(level, source) || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
			&& (this.isPassenger() || Caravans.isCaravanFolk(this));
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("role", this.role());
		output.putInt("kingdom", this.kingdomId());
		output.putBoolean("rallied", this.rallied);
		output.putInt("style", this.entityData.get(STYLE));
		output.putInt("color", this.colorId());
		output.putBoolean("carrying", this.carrying);
		if (this.site != null) {
			output.putLong("site", this.site.asLong());
		}
		output.putString("task", this.task);
		output.putInt("climate", this.entityData.get(CLIMATE));
		output.putInt("outfit", this.outfit);
		if (this.grownSince != CourtWizards.UNKNOWN) {
			output.putLong("grown_since", this.grownSince);
		}
		if (this.escort != null) {
			output.store("escort", net.minecraft.core.UUIDUtil.CODEC, this.escort);
			output.putInt("escort_slot", this.escortSlot);
		}
		if (this.post != null) {
			output.putLong("post", this.post.asLong());
			output.putInt("post_radius", this.postRadius);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(ROLE, input.getIntOr("role", GUARD));
		this.entityData.set(KINGDOM, input.getIntOr("kingdom", -1));
		this.rallied = input.getBooleanOr("rallied", false);
		this.entityData.set(STYLE, input.getIntOr("style", FactionType.KINGDOM.ordinal()));
		this.entityData.set(COLOR, input.getIntOr("color", -1));
		this.carrying = input.getBooleanOr("carrying", false);
		this.site = input.getLong("site").map(BlockPos::of).orElse(null);
		this.task = input.getStringOr("task", "");
		this.entityData.set(CLIMATE, input.getIntOr("climate", Climate.PLAINS.ordinal()));
		this.outfit = input.getIntOr("outfit", 0);
		this.grownSince = input.getLongOr("grown_since", CourtWizards.UNKNOWN);
		this.escort = input.read("escort", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		this.escortSlot = input.getIntOr("escort_slot", 0);
		this.post = input.getLong("post").map(BlockPos::of).orElse(null);
		this.postRadius = input.getIntOr("post_radius", HOME_RADIUS);
	}
}
