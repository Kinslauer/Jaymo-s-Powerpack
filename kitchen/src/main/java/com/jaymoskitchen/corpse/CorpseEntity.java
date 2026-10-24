package com.jaymoskitchen.corpse;

import com.jaymoskitchen.horse.CartEntity;
import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollRig;
import com.jaymoskitchen.ragdoll.RagdollNet;
import com.jaymoskitchen.ragdoll.RagdollShapes;
import com.jaymoskitchen.ragdoll.RagdollSnapshot;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A dead mob's body. It looks like the mob, fallen over (clients rebuild the mob from its saved data, see
 * {@code CorpseRenderer}), and holds the loot the mob would have dropped. How it's disposed of depends on its
 * {@link CorpseKind}.
 */
public class CorpseEntity extends Entity {
	/** How the corpse looks: the dead mob's saved data as SNBT, with its type id. */
	private static final EntityDataAccessor<String> LOOK = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.STRING);
	/** Entity id of the player carrying this corpse in front of them, or -1. Never saved: a reload drops it. */
	private static final EntityDataAccessor<Integer> CARRIER = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.INT);
	/** A player's corpse: whose it is (UUID as text, and name), so clients can draw them. */
	private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.STRING);
	/** How the body lies (clients sprawl it from this); rolled again each time it's set down. */
	/** Dropped from someone's arms and not landed yet: it tumbles limp through the air (see CorpseRenderer). */
	private static final EntityDataAccessor<Boolean> TUMBLING = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.BOOLEAN);
	/** (0.5.23) Nailed to a wall by an arrow: the way it faces out from the wall (degrees, 0-359), or -1 when it isn't. */
	private static final EntityDataAccessor<Integer> PINNED = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.INT);
	/** (0.5.23) How often (ticks) a pinned body checks the wall behind it is still there. */
	private static final int PIN_CHECK_TICKS = 20;
	private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.STRING);
	/** A heavy corpse (an iron golem) falling apart once it's been looted (see {@link #crumble}). */
	private static final EntityDataAccessor<Boolean> CRUMBLING = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.BOOLEAN);

	/** (0.4.37) Parts the body lost before it died (see {@link Corpses#missingParts}): not drawn. */
	private static final EntityDataAccessor<Integer> MISSING = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.INT);
	/** (0.4.39) The game time it died (0 once reloaded): clients let a fresh body fall into place (see Flop). */
	private static final EntityDataAccessor<Long> BORN = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.LONG);
	/**
	 * (0.4.48) Slung over a horse's back ({@link #slingOnto}): the horse's id (-1 for none), which place on its back
	 * (0 just behind the saddle, 1 further back), and when it was thrown up there (game time, for the arc).
	 */
	private static final EntityDataAccessor<Integer> SLUNG = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SLUNG_SLOT = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> SLUNG_AT = SynchedEntityData.defineId(CorpseEntity.class,
		EntityDataSerializers.LONG);
	/**
	 * (0.4.48) Slinging a body over a horse: up to a sheep's size ({@code SLING_WIDTH} by {@code SLING_HEIGHT},
	 * standing), or (0.4.49) a person's (a player, a villager, a zombie: no wider than {@code PERSON_WIDTH}, no taller
	 * than {@code PERSON_HEIGHT}); never a cow or anything bigger. (0.4.49) One to a horse ({@code SLUNG_MAX}).
	 * Over the back it hangs limp as a carried body does, its limbs swinging with the horse's stride. It's thrown up in an arc {@code SLING_ARC} high over
	 * {@code SLING_TICKS}, and lies across the back {@code SLUNG_BACK} behind the middle (the next {@code SLUNG_SPACING}
	 * further back), {@code SLUNG_HEIGHT} of the horse's height up.
	 */
	public static final float SLING_WIDTH = 0.95F, SLING_HEIGHT = 1.35F, PERSON_WIDTH = 0.7F, PERSON_HEIGHT = 2.0F;
	public static final int SLUNG_MAX = 1, SLING_TICKS = 10;
	static final double SLING_ARC = 0.9, SLUNG_BACK = 0.35, SLUNG_SPACING = 0.4, SLUNG_HEIGHT = 0.8;
	/** Where the arc started (each side notes it as it sees the throw), and the horse to find again after a reload. */
	private @Nullable Vec3 slingFrom;
	private java.util.@Nullable UUID slungHorse;

	/** Animals at most this big (wide and tall, after baby scaling) go in a pocket; bigger ones are carried. */
	private static final float POCKET_WIDTH = 0.5F, POCKET_HEIGHT = 0.8F;
	/** Where a carried corpse sits: this far in front of the carrier, at this height (across the arms). */
	private static final double CARRY_FORWARD = 0.55, CARRY_HEIGHT = 0.9;
	/** (0.4.31) The size of carrier the carry spot is set for (a villager); bigger ones hold it further out and higher. */
	private static final double CARRIER_HALF_WIDTH = 0.3, CARRIER_HEIGHT = 1.95;
	/** Further than this from its carrier (teleported, fell, ...) and it's dropped. */
	private static final double CARRY_LEASH_SQ = 4.0 * 4.0;

	/** A zombie or skeleton left this long (5 minutes) gets back up. */
	private static final int REANIMATE_TICKS = 6000;
	/** Everything else rots away (to rotten flesh) after 10 minutes. */
	private static final int ROT_TICKS = 12000;
	/**
	 * Hostile mobs take twice as long to rot (zombies and skeletons get up first); now and then their loot survives.
	 * (0.4.48) Spiders and creepers rot as quickly as anything else ({@code ROT_TICKS}), a creeper into gunpowder
	 * ({@code CREEPER_POWDER_MIN} to {@code CREEPER_POWDER_MAX}) instead of flesh.
	 */
	private static final int HOSTILE_ROT_TICKS = 24000;
	private static final float HOSTILE_LOOT_CHANCE = 0.1F;
	private static final int CREEPER_POWDER_MIN = 1, CREEPER_POWDER_MAX = 2;
	/** Ticks of burning to reduce a corpse to ash. */
	private static final int BURN_TICKS = 300;
	/** A burning ghast corpse wails (its hurt sound) every this many ticks, this loud (1 is normal). */
	private static final int GHAST_WAIL_EVERY = 25;
	private static final float GHAST_WAIL_VOLUME = 4.0F;
	/** In lava it goes much faster: this many ticks and it's gone. */
	private static final int LAVA_BURN_TICKS = 30;
	/** Bodies at least this big standing (the smaller of width and height; a ghast is 4) lie as big as they are. */
	private static final float HUGE = 1.9F;
	/** A burning creeper corpse explodes after this many ticks alight. */
	private static final int CREEPER_FUSE = 160;
	/** A looted heavy corpse falls apart over this many ticks, then it's gone (iron doesn't rot or burn). */
	private static final int CRUMBLE_TICKS = 50;
	/** Axe chops on the butcher block to break an animal down. */
	private static final int CHOPS = 3;

	private CorpseKind kind = CorpseKind.OTHER;
	private List<ItemStack> loot = new ArrayList<>();
	private int bones;
	private boolean searched;
	private int chops;
	private int burnTicks;
	private int age;
	/** Ticks since it started falling apart (both sides count; see {@link #CRUMBLING}). */
	private int crumbleTicks;
	/** A player's corpse: their hotbar when they died, and the slot they had selected. */
	private final List<ItemStack> hotbar = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
	/** Client: the hanging limbs' swing (see {@link #swingLimbs}). */
	private float swingBack, swingBackO, swingBackVel, swingLeft, swingLeftO, swingLeftVel;
	private double lastVx, lastVz;
	/** Client: a falling body's turning over (radians) and how fast it's turning, and how far the air drags its limbs. */
	private boolean tumbling;
	private float tumblePitch, tumblePitchO, tumbleRoll, tumbleRollO, spinPitch, spinRoll, fallTrail;
	/** Client: a jolt from a blow or rummaging, dying away. */
	private static final int JOLT_TICKS = 10;
	private float joltTicks, joltTicksO, joltStrength, joltSide = 1.0F;
	private static final float SWING_STIFFNESS = 0.2F, SWING_DAMPING = 0.86F, SWING_SHOVE = 14.0F;
	/** How far behind the limbs trail per block a tick of speed (a sprint is about 0.28), and each step's jolt. */
	private static final float SWING_TRAIL = 3.2F, SWING_STEP = 0.09F;
	private int selected;
	/** (0.4.35) Who killed it (a UUID, or empty), and whether a player had a hand in it: scavengers leave players' kills alone. */
	private String killer = "";
	private boolean killedByPlayer;
	/** (0.4.35) Bites a scavenger has taken out of it so far (see {@link Scavengers}). */
	private int bites;

	/** Client side: the mob rebuilt from {@link #LOOK}, for drawing. */
	private @Nullable Entity body;

	public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
		super(type, level);
		// a body makes no noise of its own (catching, burning, going out, splashing): only what this class plays
		setSilent(true);
	}

	void setUp(String look, CorpseKind kind, List<ItemStack> loot, int bones) {
		this.entityData.set(POSE, random.nextInt());
		this.entityData.set(LOOK, look);
		this.kind = kind;
		this.loot = new ArrayList<>(loot);
		this.bones = bones;
		refreshDimensions();
	}

	/** A dead player's body, with their hotbar. */
	void setUpPlayer(java.util.UUID owner, String name, List<ItemStack> hotbar, int selected) {
		this.entityData.set(POSE, random.nextInt());
		this.entityData.set(LOOK, "{id:\"minecraft:player\"}");
		this.entityData.set(OWNER, owner.toString());
		this.entityData.set(OWNER_NAME, name);
		this.kind = CorpseKind.PLAYER;
		for (int i = 0; i < this.hotbar.size(); i++) {
			this.hotbar.set(i, i < hotbar.size() ? hotbar.get(i).copy() : ItemStack.EMPTY);
		}
		this.selected = selected;
		refreshDimensions();
	}

	/** (0.5.1) Server: its physics body (null while carried, slung or in a cart: those draw it limp instead). */
	private @Nullable CorpseRagdoll ragdoll;
	/** (0.5.1) Its rig, saved with it: shape id (-1 until known), and the standing size it was built for. */
	private int rigShape = -1;
	private float rigWidth, rigHeight;
	private @Nullable RagdollRig rigCache;
	/** (0.5.1) Points loaded from a save (relative to where it was), put back on the first tick. */
	private float @Nullable [] savedPoints;

	/** A slim-skinned player's body (or their zombie's): it sounds higher. */
	private boolean slim;

	void setSlim(boolean slim) {
		this.slim = slim;
	}

	boolean slim() {
		// a zombie player's body keeps the zombie's saved voice in its look
		return slim || look().contains("Slim:1b");
	}

	/** The pitch its groans are raised by. */
	private float voice() {
		return slim() ? PlayerCorpses.SLIM_PITCH : 1.0F;
	}

	public boolean isPlayerCorpse() {
		return kind == CorpseKind.PLAYER || "{id:\"minecraft:player\"}".equals(look());
	}

	/** In open daylight where monsters burn (as a zombie or skeleton would, alive). */
	private boolean inSunlight(ServerLevel level) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, position())
			&& getLightLevelDependentMagicValue() > 0.5F && !isInWaterOrRain()
			&& level.canSeeSky(BlockPos.containing(getX(), getY() + getBbHeight(), getZ()));
	}

	/** What a body can be wearing or holding, in the order it's taken off. */
	private static final String[] GEAR = {"head", "chest", "legs", "feet", "body", "mainhand", "offhand"};

	/**
	 * Takes one piece of armour (or a held item) off the body and hands it over; it's gone from the body too, since
	 * clients draw the body from its look. Its gear has seen use: a skeleton's weapon comes off at under half its
	 * durability, anything else that wears out somewhere between well-worn and nearly new (see {@link #wear}). False
	 * if it has nothing on.
	 */
	private boolean takeGear(ServerLevel level, Player player) {
		ItemStack stack = takeGearPiece();
		if (stack.isEmpty()) {
			return false;
		}
		if (!player.getInventory().add(stack)) {
			spawnAtLocation(level, stack, 0.3F);
		}
		jolt(false);
		play(level, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.9F);
		robbed(player);
		return true;
	}

	/**
	 * (0.5.9) Takes the next piece of gear off the body (worn as {@link #takeGear} says) and returns it, or an empty stack
	 * if it has nothing on.
	 */
	private ItemStack takeGearPiece() {
		CompoundTag tag = parse(look());
		if (tag == null) {
			return ItemStack.EMPTY;
		}
		CompoundTag gear = tag.getCompoundOrEmpty("equipment");
		for (String slot : GEAR) {
			if (!gear.contains(slot)) {
				continue;
			}
			ItemStack stack = ItemStack.CODEC.parse(registryAccess().createSerializationContext(NbtOps.INSTANCE), gear.get(slot))
				.result().orElse(ItemStack.EMPTY);
			gear.remove(slot);
			if (!stack.isEmpty() && stack.isDamageableItem() && !preserved(tag, slot)) {
				wear(stack, kind == CorpseKind.SKELETON && slot.equals("mainhand"));
			}
			if (gear.isEmpty()) {
				tag.remove("equipment");
			} else {
				tag.put("equipment", gear);
			}
			this.entityData.set(LOOK, tag.toString());
			if (stack.isEmpty()) {
				continue;
			}
			return stack;
		}
		return ItemStack.EMPTY;
	}

	/** (0.5.9) Called when a player takes something off a body (searching it, or a piece of its gear); see {@link #setOnRobbed}. */
	private static java.util.function.@Nullable BiConsumer<Player, Entity> onRobbed;

	/**
	 * (0.5.9) Jaymo's Kingdoms: told whenever a player takes something off a body by hand (its pockets searched, or a piece
	 * of what it wears or holds), so robbing the dead can be a crime. Not for fish, butchering, or a player's own hotbar back.
	 */
	public static void setOnRobbed(java.util.function.BiConsumer<Player, Entity> hook) {
		onRobbed = hook;
	}

	private void robbed(Player player) {
		if (onRobbed != null) {
			try {
				onRobbed.accept(player, this);
			} catch (RuntimeException e) {
				com.jaymoskitchen.JaymosKitchen.LOGGER.warn("A corpse-robbing hook failed: {}", e.toString());
			}
		}
	}

	/**
	 * (0.5.9) Robbed by something that isn't a player (Jaymo's Kingdoms: a looter come for the battlefield's dead): its
	 * pockets and everything it wears or holds, worn as {@link #takeGear} would leave them. Nothing from an animal, a fish,
	 * a player's body (their things stay theirs) or an iron one. Returns what was taken (empty if nothing).
	 */
	public List<ItemStack> robBy(Entity robber) {
		List<ItemStack> taken = new ArrayList<>();
		if (!(level() instanceof ServerLevel level) || isRemoved() || !robbable()) {
			return taken;
		}
		if (!searched) {
			for (ItemStack stack : loot) {
				if (!stack.isEmpty()) {
					taken.add(stack.copy());
				}
			}
			loot.clear();
			searched = true;
		}
		for (ItemStack piece = takeGearPiece(); !piece.isEmpty(); piece = takeGearPiece()) {
			taken.add(piece);
		}
		if (!taken.isEmpty()) {
			jolt(false);
			play(level, SoundEvents.BUNDLE_DROP_CONTENTS, 0.8F);
		}
		return taken;
	}

	/** (0.5.9) A body that can be robbed at all: not an animal's, a fish's, a player's or an iron one. */
	private boolean robbable() {
		return kind != CorpseKind.ANIMAL && kind != CorpseKind.FISH && kind != CorpseKind.PLAYER && !isPlayerCorpse() && !isHeavy() && !isFish();
	}

	/** (0.5.9) The look {@link #hasValuables} last answered for, and its answer. */
	private @Nullable String valuablesLook;
	private boolean lookHasGear;

	/** (0.5.9) Something on it worth a robber's while: unsearched pockets with something in them, or gear still on it. */
	public boolean hasValuables() {
		if (!robbable()) {
			return false;
		}
		if (!searched && !loot.isEmpty()) {
			return true;
		}
		String look = look();
		if (!look.equals(valuablesLook)) {
			CompoundTag tag = parse(look);
			lookHasGear = tag != null && !tag.getCompoundOrEmpty("equipment").isEmpty();
			valuablesLook = look;
		}
		return lookHasGear;
	}

	/** (0.5.9) The dead mob's saved data (its UUID, its name, a Kingdoms folk's realm and role), or null. A fresh copy. */
	public @Nullable CompoundTag lookTag() {
		return parse(look());
	}

	/**
	 * Wears a piece of the body's gear: a skeleton's weapon is left with 10-49% of its durability, anything else with
	 * 15-90%. Never repairs what was already more worn than that.
	 */
	private void wear(ItemStack stack, boolean skeletonWeapon) {
		int max = stack.getMaxDamage();
		float left = skeletonWeapon ? 0.10F + random.nextFloat() * 0.39F : 0.15F + random.nextFloat() * 0.75F;
		int remaining = Math.max(1, Math.round(max * left));
		if (skeletonWeapon) {
			remaining = Math.min(remaining, Math.max(1, (max - 1) / 2));
		}
		stack.setDamageValue(Math.max(stack.getDamageValue(), max - remaining));
	}

	/** Whether the mob's gear in that slot was something it picked up (vanilla keeps those as they were). */
	private static boolean preserved(CompoundTag look, String slot) {
		return look.getCompoundOrEmpty("drop_chances").getFloatOr(slot, 0.0F) > 1.0F;
	}

	// ---- (0.5.1) ragdoll ---------------------------------------------------------------------------------------------

	/** Server: sends its body's points to one player who just came into range. */
	void sendRagdollTo(net.minecraft.server.level.ServerPlayer player) {
		if (ragdoll != null && carrier() == null) {
			RagdollNet.sendTo(player, this, RagdollSnapshot.of(ragdoll.body));
		}
	}

	/** The rig of its body: the dead mob's real standing size (so babies and scaled mobs fit), a player's 0.6 by 1.8. */
	private RagdollRig rig() {
		if (rigCache == null) {
			if (rigShape < 0 || rigWidth <= 0.0F || rigHeight <= 0.0F) {
				EntityType<?> type = isPlayerCorpse() ? EntityTypes.PLAYER : sourceType();
				float width = 0.6F, height = 1.8F;
				if (type != null && type != EntityTypes.PLAYER) {
					// the mob as it was (a baby, a scaled one), else its type's size
					EntityDimensions standing = rebuild() instanceof LivingEntity mob ? mob.getDimensions(Pose.STANDING)
						: type.getDimensions();
					width = standing.width();
					height = standing.height();
				}
				rigCache = type == null ? RagdollRig.of(RagdollRig.Shape.BOX, 0.5F, 0.4F) : RagdollShapes.rig(type, width, height);
				rigShape = rigCache.shape.ordinal();
				rigWidth = rigCache.width;
				rigHeight = rigCache.height;
			} else {
				rigCache = RagdollRig.of(RagdollRig.Shape.byId(rigShape), rigWidth, rigHeight);
			}
		}
		return rigCache;
	}

	/** Server: starts the body from one already made (a dying mob's, a limp player's). The corpse moves to it. */
	void startRagdoll(RagdollBody body) {
		this.rigCache = body.rig;
		this.rigShape = body.rig.shape.ordinal();
		this.rigWidth = body.rig.width;
		this.rigHeight = body.rig.height;
		this.ragdoll = new CorpseRagdoll(this, body);
	}

	/** A body lying where the corpse is, its limbs splayed (face up or down, and how, from its pose seed). */
	RagdollBody lyingBody() {
		boolean faceUp = new java.util.Random(poseSeed()).nextBoolean();
		return RagdollBody.lying(rig(), getX(), getY(), getZ(), getYRot(), faceUp, poseSeed());
	}

	/**
	 * Server: let go of, or set down: the body lies where the corpse is, moving at {@code velocity}. {@code rest}: it
	 * isn't moving at all (set down on a butcher block).
	 */
	private void lieDown(Vec3 velocity, boolean rest) {
		if (!(level() instanceof ServerLevel)) {
			return;
		}
		RagdollBody made = lyingBody();
		if (rest) {
			made.stop();
		} else {
			made.setSpeed(velocity.x, velocity.y, velocity.z);
		}
		this.ragdoll = new CorpseRagdoll(this, made);
	}

	/** Free on the server: the body is stepped (made first if there isn't one: loaded, or let go of some other way). */
	private void tickRagdoll(ServerLevel level) {
		if (ragdoll == null) {
			RagdollBody saved = null;
			if (savedPoints != null) {
				saved = new RagdollBody(rig());
				if (!saved.restore(savedPoints, getX(), getY(), getZ())) {
					saved = null;
				}
				savedPoints = null;
			}
			if (saved != null) {
				ragdoll = new CorpseRagdoll(this, saved);
			} else {
				lieDown(getDeltaMovement(), false);
			}
		}
		ragdoll.tick(this, level);
	}

	void setTumbling(boolean tumbling) {
		this.entityData.set(TUMBLING, tumbling);
	}

	/** Entity events: the body jolts, hard (an axe blow) or lightly (rummaged through). */
	private static final byte JOLT_HARD = 70, JOLT_LIGHT = 71;

	/** Server: the body jolts with the blow (an axe) or the rummaging (looting it). */
	public void jolt(boolean hard) {
		level().broadcastEntityEvent(this, hard ? JOLT_HARD : JOLT_LIGHT);
		if (ragdoll != null && !level().isClientSide()) {
			// (0.5.1) the body moves too
			ragdoll.jolt(hard, random);
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == JOLT_HARD || id == JOLT_LIGHT) {
			joltTicks = joltTicksO = JOLT_TICKS;
			joltStrength = id == JOLT_HARD ? 1.0F : 0.3F;
			joltSide = random.nextBoolean() ? 1.0F : -1.0F;
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** Client: how hard it's jolting this frame (0 when still), and the way the jolt rocks it (+1 or -1). */
	public float jolt(float partialTicks) {
		return Mth.lerp(partialTicks, joltTicksO, joltTicks) / JOLT_TICKS * joltStrength;
	}

	/** Client: ticks since the jolt. */
	public float joltTime(float partialTicks) {
		return JOLT_TICKS - Mth.lerp(partialTicks, joltTicksO, joltTicks);
	}

	/** Client: which way the jolt flops it first (+1 or -1). */
	public float joltSide() {
		return joltSide;
	}

	/** The seed for how the body sprawls where it lies. */
	public int poseSeed() {
		return this.entityData.get(POSE);
	}

	/**
	 * Client: a dropped or thrown body falls like a ragdoll. It starts turning over (the way it was let go), the spin
	 * builds the longer and faster it falls (uneven drag on a limp body), and its limbs are dragged up behind it by the
	 * air as it picks up speed, flailing. Nothing holds it level: a long fall turns it over and over.
	 */
	private void tumble() {
		tumblePitchO = tumblePitch;
		tumbleRollO = tumbleRoll;
		swingBackO = swingBack;
		swingLeftO = swingLeft;
		if (!tumbling) {
			tumbling = true;
			tumblePitch = tumblePitchO = tumbleRoll = tumbleRollO = 0.0F;
			java.util.Random seeded = new java.util.Random(poseSeed());
			spinPitch = (0.05F + seeded.nextFloat() * 0.08F) * (seeded.nextBoolean() ? 1.0F : -1.0F);
			spinRoll = (seeded.nextFloat() - 0.5F) * 0.12F;
			fallTrail = 0.0F;
		}
		float falling = (float) Math.max(0.0, yo - getY());
		// falling faster spins it up; the air slows it a little
		spinPitch = (spinPitch + Math.signum(spinPitch) * falling * 0.035F) * 0.985F;
		spinRoll = (spinRoll + Math.signum(spinRoll) * falling * 0.015F) * 0.985F;
		tumblePitch += Mth.clamp(spinPitch, -0.6F, 0.6F);
		tumbleRoll += Mth.clamp(spinRoll, -0.4F, 0.4F);
		// limbs: dragged up behind the fall as it speeds up, flailing as they go
		fallTrail += (Mth.clamp(falling * 5.0F, 0.0F, 2.6F) - fallTrail) * 0.3F;
		swingLeft = fallTrail + Mth.sin(tickCount * 1.3F) * (0.25F + fallTrail * 0.15F);
		swingBack = Mth.sin(tickCount * 0.9F + 0.7F) * (0.45F + fallTrail * 0.2F);
	}

	/** Client: how far a falling body has turned over, end over end and side to side (degrees). */
	public float tumblePitch(float partialTicks) {
		return Mth.lerp(partialTicks, tumblePitchO, tumblePitch) * Mth.RAD_TO_DEG;
	}

	public float tumbleRoll(float partialTicks) {
		return Mth.lerp(partialTicks, tumbleRollO, tumbleRoll) * Mth.RAD_TO_DEG;
	}

	/** Client: how far the hanging limbs have swung, toward the carrier's back and to their left (radians). */
	public float swingBack(float partialTicks) {
		return Mth.lerp(partialTicks, swingBackO, swingBack);
	}

	public float swingLeft(float partialTicks) {
		return Mth.lerp(partialTicks, swingLeftO, swingLeft);
	}

	/**
	 * Client: the limbs of a carried body swing like pendulums. When the body speeds up, slows or swings round as its
	 * carrier turns, they lag behind and swing back, settling under gravity; each step jogs them a little.
	 */
	private void swingLimbs(Entity carrier) {
		swingBackO = swingBack;
		swingLeftO = swingLeft;
		double vx = getX() - xo, vz = getZ() - zo;
		double ax = vx - lastVx, az = vz - lastVz;
		lastVx = vx;
		lastVz = vz;
		float yaw = carrier.getYRot() * Mth.DEG_TO_RAD;
		double forwardX = -Mth.sin(yaw), forwardZ = Mth.cos(yaw);
		double rightX = -Mth.cos(yaw), rightZ = -Mth.sin(yaw);
		float ahead = (float) (ax * forwardX + az * forwardZ);
		float right = (float) (ax * rightX + az * rightZ);
		float speedAhead = (float) (vx * forwardX + vz * forwardZ);
		float speedRight = (float) (vx * rightX + vz * rightZ);
		float stepBack = 0.0F, stepSide = 0.0F;
		if (carrier instanceof LivingEntity walker) {
			// every footfall jolts them (twice a stride), harder the faster the carrier goes
			float phase = walker.walkAnimation.position() * 0.6662F;
			float stride = Math.min(1.2F, walker.walkAnimation.speed());
			stepBack = Mth.sin(phase * 2.0F) * SWING_STEP * stride;
			stepSide = Mth.sin(phase) * SWING_STEP * 0.7F * stride;
		}
		// a damped pendulum: gravity pulls it back to hanging down, the air pushes it out behind as fast as the body
		// moves (most at a sprint), and every change of pace or direction shoves it the other way
		float trailBack = Mth.clamp(speedAhead * SWING_TRAIL, -1.0F, 1.0F);
		float trailLeft = Mth.clamp(speedRight * SWING_TRAIL, -1.0F, 1.0F);
		swingBackVel += -SWING_STIFFNESS * (swingBack - trailBack) + ahead * SWING_SHOVE + stepBack;
		swingLeftVel += -SWING_STIFFNESS * (swingLeft - trailLeft) + right * SWING_SHOVE + stepSide;
		swingBackVel *= SWING_DAMPING;
		swingLeftVel *= SWING_DAMPING;
		swingBack = Mth.clamp(swingBack + swingBackVel, -1.4F, 1.4F);
		swingLeft = Mth.clamp(swingLeft + swingLeftVel, -1.4F, 1.4F);
	}

	public String ownerId() {
		return this.entityData.get(OWNER);
	}

	public Optional<java.util.UUID> owner() {
		try {
			return Optional.of(java.util.UUID.fromString(ownerId()));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public String ownerName() {
		return this.entityData.get(OWNER_NAME);
	}

	public String look() {
		return this.entityData.get(LOOK);
	}

	/** (0.5.5) Who this was when alive (the dead mob's own id, from its look), so its grave can be found. */
	public Optional<java.util.UUID> deadId() {
		CompoundTag tag = parse(look());
		return tag == null ? Optional.empty() : tag.read("UUID", net.minecraft.core.UUIDUtil.CODEC);
	}

	/** (0.5.5) A villager's body (vanilla's, MCA's, a wandering trader). */
	public boolean isVillager() {
		EntityType<?> type = sourceType();
		return type != null && type.builtInRegistryHolder().is(Corpses.VILLAGERS);
	}

	/** The parts the body lost (bits: head 1, right arm 2, left arm 4, right leg 8, left leg 16). */
	public int missing() {
		return this.entityData.get(MISSING);
	}

	public void setMissing(int missing) {
		this.entityData.set(MISSING, missing);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LOOK, "");
		builder.define(CARRIER, -1);
		builder.define(OWNER, "");
		builder.define(POSE, 0);
		builder.define(TUMBLING, false);
		builder.define(OWNER_NAME, "");
		builder.define(CRUMBLING, false);
		builder.define(MISSING, 0);
		builder.define(BORN, 0L);
		builder.define(SLUNG, -1);
		builder.define(SLUNG_SLOT, 0);
		builder.define(SLUNG_AT, 0L);
		builder.define(PINNED, -1);
	}

	/**
	 * (0.5.23) Jaymo's Kingdoms: an Archer's killing shot nailed this body to the wall behind it, facing out at {@code yaw}.
	 * It hangs there upright, limp, until it's taken down (picked up) or the wall goes.
	 */
	public void pin(float yaw) {
		this.entityData.set(PINNED, Math.floorMod(Math.round(yaw), 360));
		this.ragdoll = null;
		setNoGravity(true);
		setDeltaMovement(Vec3.ZERO);
		setYRot(yaw);
	}

	/** (0.5.23) The way a pinned body faces out from its wall (degrees), or -1 if it isn't pinned. */
	public int pinned() {
		return this.entityData.get(PINNED);
	}

	private void unpin() {
		if (pinned() >= 0) {
			this.entityData.set(PINNED, -1);
			setNoGravity(false);
		}
	}

	/** (0.5.23) Whether the wall a pinned body hangs on is still behind it. */
	private boolean wallBehind(ServerLevel level) {
		float radians = pinned() * net.minecraft.util.Mth.DEG_TO_RAD;
		// facing out (-sin, cos): the wall is the other way, just past the body's half-width
		double reach = getBbWidth() * 0.5 + 0.3;
		BlockPos wall = BlockPos.containing(getX() + net.minecraft.util.Mth.sin(radians) * reach, getY() + 1.0,
			getZ() - net.minecraft.util.Mth.cos(radians) * reach);
		return !level.getBlockState(wall).getCollisionShape(level, wall).isEmpty();
	}

	/** (0.4.39) Server: it has just died, this tick (see {@link #sinceDeath}). */
	public void markJustDied() {
		this.entityData.set(BORN, level().getGameTime());
	}

	/** (0.4.39) Ticks since it died, or a very large number for a body that has been lying a while (or was reloaded). */
	public long sinceDeath() {
		long born = this.entityData.get(BORN);
		return born == 0L ? Long.MAX_VALUE : level().getGameTime() - born;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (LOOK.equals(accessor)) {
			this.body = null;
			this.typeKnown = false;
			refreshDimensions();
		}
		if (SLUNG.equals(accessor)) {
			// (the arc starts from wherever it is as the throw is seen)
			this.slingFrom = this.entityData.get(SLUNG) >= 0 ? position() : null;
		}
	}

	static @Nullable CompoundTag parse(String look) {
		if (look.isEmpty()) {
			return null;
		}
		try {
			return TagParser.parseCompoundFully(look);
		} catch (Exception e) {
			return null;
		}
	}

	static Optional<EntityType<?>> typeOf(@Nullable CompoundTag tag) {
		if (tag == null) {
			return Optional.empty();
		}
		Identifier id = Identifier.tryParse(tag.getStringOr("id", ""));
		return id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
	}

	/** Rebuilds the dead mob from its look (not added to the world). */
	private @Nullable Entity rebuild() {
		if (isPlayerCorpse()) {
			// players can't be rebuilt from saved data: CorpseRenderer draws them itself
			return null;
		}
		CompoundTag tag = parse(look());
		Optional<EntityType<?>> type = typeOf(tag);
		if (type.isEmpty()) {
			return null;
		}
		Entity entity = type.get().create(level(), EntitySpawnReason.LOAD);
		if (entity != null) {
			ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level().registryAccess(), tag);
			entity.load(input);
			// (0.4.50) the game reads attributes only on the server; the client's body needs its size (scale) too
			if (level().isClientSide() && entity instanceof LivingEntity living) {
				input.read("attributes", AttributeInstance.Packed.LIST_CODEC).ifPresent(living.getAttributes()::apply);
			}
		}
		return entity;
	}

	/** Client side: the body to draw, posed where this corpse is. */
	public @Nullable Entity clientBody() {
		return clientBody(getYRot());
	}

	/** Client side: the body to draw, posed where this corpse is, facing {@code yaw}. */
	public @Nullable Entity clientBody(float yaw) {
		if (this.body == null) {
			this.body = rebuild();
			if (this.body != null) {
				// never added to the world, so it has no id; renderers need one (negative: can't clash with real ones)
				this.body.setId(-1000 - getId());
			}
		}
		Entity entity = this.body;
		if (entity != null) {
			placeBody(entity, yaw);
		}
		return entity;
	}

	/**
	 * Client side: puts a body to draw where this corpse is, facing {@code yaw}, fully keeled over. (0.4.44) Also used
	 * for a body this corpse doesn't own (a dead player's villager look, from MCA).
	 */
	public void placeBody(Entity entity, float yaw) {
		entity.setPos(getX(), getY(), getZ());
		entity.xo = this.xo;
		entity.yo = this.yo;
		entity.zo = this.zo;
		entity.xOld = this.xOld;
		entity.yOld = this.yOld;
		entity.zOld = this.zOld;
		entity.setYRot(yaw);
		entity.yRotO = yaw;
		entity.setXRot(0.0F);
		entity.xRotO = 0.0F;
		if (entity instanceof LivingEntity living) {
			living.setYBodyRot(yaw);
			living.yBodyRotO = yaw;
			living.setYHeadRot(yaw);
			living.yHeadRotO = yaw;
			// fully keeled over, as at the end of the death animation
			living.deathTime = 20;
			living.hurtTime = 0;
		}
	}

	/** Client: flames licking up out of the body and embers drifting off it, instead of a sheet of fire. */
	private void smoulder() {
		double w = getBbWidth() * 0.35;
		double h = getBbHeight();
		double x = getX() + (random.nextDouble() * 2.0 - 1.0) * w;
		double z = getZ() + (random.nextDouble() * 2.0 - 1.0) * w;
		double y = getY() + h * (0.2 + random.nextDouble() * 0.5);
		level().addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.02, 0.0);
		level().addParticle(ParticleTypes.SMALL_FLAME,
			getX() + (random.nextDouble() * 2.0 - 1.0) * w, y, getZ() + (random.nextDouble() * 2.0 - 1.0) * w, 0.0, 0.02, 0.0);
		if (random.nextInt(3) == 0) {
			// now and then a real tongue of flame
			level().addParticle(ParticleTypes.FLAME, x, y + h * 0.2, z, 0.0, 0.03, 0.0);
		}
		if (random.nextInt(2) == 0) {
			level().addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
		}
		if (random.nextInt(2) == 0) {
			level().addParticle(random.nextInt(4) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
				x, getY() + h, z, 0.0, 0.05, 0.0);
		}
	}

	/** Lying down: about as long as the mob was tall, and low. */
	@Override
	public EntityDimensions getDimensions(Pose pose) {
		EntityType<?> source = sourceType();
		if (source == null) {
			return super.getDimensions(pose);
		}
		EntityDimensions standing = source.getDimensions();
		float size = Math.min(standing.width(), standing.height());
		if (size >= HUGE) {
			// something huge (a ghast) lies as big as it is, so you can get hold of it
			return EntityDimensions.fixed(size * 0.85F, size * 0.7F);
		}
		// narrow, so it never catches on the edge of a neighbouring block and hangs over the air: it drops to the ground
		float width = Mth.clamp(Math.min(standing.width(), standing.height()) * 0.9F, 0.4F, 0.9F);
		float height = Mth.clamp(Math.min(standing.width(), standing.height()) * 0.6F, 0.2F, 1.0F);
		return EntityDimensions.fixed(width, height);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.entityData.get(CRUMBLING)) {
			tickCrumbling();
			if (isRemoved()) {
				return;
			}
		}
		if (level().isClientSide() && isOnFire()) {
			smoulder();
		}
		if (level().isClientSide()) {
			joltTicksO = joltTicks;
			joltTicks = Math.max(0.0F, joltTicks - 1.0F);
		}
		Entity carrier = carrier();
		if (level().isClientSide()) {
			if (carrier != null) {
				swingLimbs(carrier);
			} else if (isTumbling() && !isPassenger()) {
				tumble();
			} else {
				swingBack = swingBackO = swingLeft = swingLeftO = swingBackVel = swingLeftVel = 0.0F;
				tumbling = false;
			}
		}
		if (carrier != null) {
			this.ragdoll = null;
			// held across the carrier's arms, in front of them
			Vec3 at = carryPosition(carrier, carrier.getYRot(), carrier.getX(), carrier.getY(), carrier.getZ(), getBbWidth());
			setPos(at.x, at.y, at.z);
			setYRot(carrier.getYRot() + 90.0F);
			setDeltaMovement(Vec3.ZERO);
			if (level() instanceof ServerLevel level) {
				if (!carrier.isAlive() || carrier.level() != level() || carrier.isSpectator()
					|| carrier.distanceToSqr(this) > CARRY_LEASH_SQ) {
					setCarrier(null);
				} else if (carrier instanceof Player player && tickCount % 20 == 0) {
					// heavy: slows whoever is carrying it
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, isBigCarcass() ? 2 : 0, false, false, true));
				}
				serverTick(level);
			}
			return;
		}
		if (this.entityData.get(CARRIER) >= 0 && !level().isClientSide()) {
			// the carrier is gone from this level (logged out, changed dimension, unloaded): it drops
			setCarrier(null);
		}
		if (grabCooldown > 0 && !level().isClientSide()) {
			grabCooldown--;
		}
		if (tickSlung()) {
			this.ragdoll = null;
			return;
		}
		if (isPassenger()) {
			this.ragdoll = null;
			// lying in a cart: the cart moves it
			if (level() instanceof ServerLevel level) {
				serverTick(level);
			}
			return;
		}
		if (isTumbling() && !level().isClientSide() && landInCart()) {
			return;
		}
		if (pinned() >= 0) {
			// (0.5.23) nailed to a wall: hangs where it is until the wall goes
			this.ragdoll = null;
			setDeltaMovement(Vec3.ZERO);
			if (level() instanceof ServerLevel level) {
				if (tickCount % PIN_CHECK_TICKS == 0 && !wallBehind(level)) {
					unpin();
				}
				serverTick(level);
			}
			return;
		}
		// (0.5.1) free: the body is a ragdoll, simulated here; the corpse is wherever its middle is (see CorpseRagdoll)
		if (level() instanceof ServerLevel here) {
			tickRagdoll(here);
		}

		if (level() instanceof ServerLevel level) {
			serverTick(level);
		}
	}

	/** (0.4.28) A burning corpse tries to set what it lies by alight this often (ticks), as lava does. */
	private static final int SPREAD_EVERY = 20;

	/**
	 * (0.4.28) Like lava, a burning body lights what's beside it: an empty spot in or around it (a block out, a block up)
	 * next to something that catches (wood, wool, leaves...) takes fire. Follows the fire-spread game rules.
	 */
	private void spreadFire(ServerLevel level) {
		BlockPos at = blockPosition();
		if (!level.canSpreadFireAround(at)) {
			return;
		}
		for (int i = 0; i < 2; i++) {
			BlockPos pos = at.offset(random.nextInt(3) - 1, random.nextInt(2), random.nextInt(3) - 1);
			if (level.isLoaded(pos) && level.getBlockState(pos).isAir() && flammableBeside(level, pos)) {
				level.setBlockAndUpdate(pos, net.minecraft.world.level.block.BaseFireBlock.getState(level, pos));
				return;
			}
		}
	}

	private static boolean flammableBeside(ServerLevel level, BlockPos pos) {
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
			BlockPos next = pos.relative(d);
			if (level.isLoaded(next) && level.getBlockState(next).ignitedByLava()) {
				return true;
			}
		}
		return false;
	}

	/** How often a corpse looks for a flame it's lying in or on (lava, fire, a lit campfire). */
	private static final int FLAME_CHECK_TICKS = 10;

	/** In or on lava, fire, or a lit campfire. */
	private boolean touchesFlame(ServerLevel level) {
		net.minecraft.world.phys.AABB box = getBoundingBox().inflate(0.0, 0.1, 0.0).move(0.0, -0.1, 0.0);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (state.is(net.minecraft.tags.BlockTags.FIRE) || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
				|| state.is(net.minecraft.tags.BlockTags.CAMPFIRES) && state.getValueOrElse(net.minecraft.world.level.block.CampfireBlock.LIT, false)) {
				return true;
			}
		}
		return false;
	}

	private void serverTick(ServerLevel level) {
		age++;
		if (burns() && !isOnFire() && tickCount % FLAME_CHECK_TICKS == 0 && touchesFlame(level)) {
			igniteForSeconds(8.0F);
		}
		if (kind == CorpseKind.ZOMBIE && carrier() == null && tickCount % 20 == 0 && inSunlight(level)) {
			// left out in the daylight, the undead burn as they would alive
			igniteForSeconds(8.0F);
		}
		if (burns() && isOnFire() && (isInWater() || isUnderWater())) {
			// dunked: the flames go out for good (until something lights it again)
			clearFire();
			burnTicks = 0;
			play(level, SoundEvents.FIRE_EXTINGUISH, 0.8F);
		}
		if (burnTicks > 0 && burns() && getRemainingFireTicks() < 10 && !isInWaterOrRain()) {
			// once a body catches, it keeps burning until it's gone (only water or rain puts it out). Topped up before
			// it can go out, or it would fizz out and catch again every second
			setRemainingFireTicks(40);
		}
		if (burns() && burnTicks < BURN_TICKS - LAVA_BURN_TICKS && isInLava()) {
			// lava makes short work of it
			burnTicks = BURN_TICKS - LAVA_BURN_TICKS;
			play(level, SoundEvents.LAVA_EXTINGUISH, 0.8F);
		}
		if (isOnFire() && burns()) {
			burnTicks++;
			if (burnTicks % SPREAD_EVERY == 0 && carrier() == null) {
				spreadFire(level);
			}
			if (isCreeper()) {
				if (burnTicks == 1 || burnTicks == CREEPER_FUSE - 30) {
					// hisses when it catches, and again just before it goes
					play(level, SoundEvents.CREEPER_PRIMED, burnTicks == 1 ? 0.8F : 1.2F);
				}
				if (burnTicks >= CREEPER_FUSE) {
					// the fire reaches whatever made it a creeper
					discard();
					level.explode(this, getX(), getY() + 0.3, getZ(), isChargedCreeper() ? 6.0F : 3.0F,
						Level.ExplosionInteraction.MOB);
					return;
				}
			}
			if (random.nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 1, 0.3, 0.1, 0.3, 0.01);
			}
			if (burnTicks % GHAST_WAIL_EVERY == 1 && sourceType() == net.minecraft.world.entity.EntityTypes.GHAST) {
				// a burning ghast still screams, louder than it ever did alive
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GHAST_HURT, SoundSource.HOSTILE, GHAST_WAIL_VOLUME,
					0.8F + random.nextFloat() * 0.4F);
			}
			if (burnTicks >= BURN_TICKS) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 20, 0.4, 0.2, 0.4, 0.02);
				level.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.2, getZ(), 30, 0.4, 0.2, 0.4, 0.02);
				play(level, SoundEvents.FIRE_EXTINGUISH, 1.0F);
				// whatever wasn't searched off it burns too; a skeleton's bones don't
				if (kind == CorpseKind.SKELETON && bones > 0) {
					spawnAtLocation(level, new ItemStack(Items.BONE, bones), 0.2F);
				}
				discard();
				return;
			}
		}
		if (rises()) {
			if (age > REANIMATE_TICKS - 200 && random.nextInt(40) == 0) {
				// twitching (rattling): it's about to get up
				play(level, kind == CorpseKind.SKELETON ? SoundEvents.SKELETON_AMBIENT : SoundEvents.ZOMBIE_AMBIENT, 0.6F * voice());
				level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.3, getZ(), 2, 0.3, 0.1, 0.3, 0.01);
			}
			if (age >= REANIMATE_TICKS) {
				reanimate(level);
			}
		} else if (isHeavy()) {
			// iron doesn't rot: left long enough, it falls apart where it lies (its loot with it)
			if (age >= ROT_TICKS) {
				startCrumbling(level);
			}
		} else if (age >= (isHostile() && !rotsQuickly() ? HOSTILE_ROT_TICKS : ROT_TICKS)) {
			level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 15, 0.4, 0.1, 0.4, 0.01);
			play(level, SoundEvents.SLIME_SQUISH, 0.8F);
			if (isCreeper()) {
				// (0.4.48) what made it a creeper is all that's left of it
				spawnAtLocation(level, new ItemStack(Items.GUNPOWDER, CREEPER_POWDER_MIN + random.nextInt(CREEPER_POWDER_MAX - CREEPER_POWDER_MIN + 1)), 0.2F);
			} else if (isHostile() && random.nextFloat() < HOSTILE_LOOT_CHANCE) {
				// once in a while what it had survives the rot
				dropLoot(level);
			} else {
				spawnAtLocation(level, new ItemStack(Items.ROTTEN_FLESH, 1 + random.nextInt(2)), 0.2F);
			}
			discard();
		}
	}

	/** Whether it gets back up if left (a zombie, a skeleton with its bones still in it, a player). */
	public boolean rises() {
		return kind == CorpseKind.ZOMBIE || kind == CorpseKind.SKELETON || kind == CorpseKind.PLAYER;
	}

	/** An unburned zombie or skeleton gets back up, empty-handed (its gear was left in the corpse); a player, as a zombie. */
	private void reanimate(ServerLevel level) {
		if (kind == CorpseKind.PLAYER) {
			PlayerCorpses.reanimate(this, level, hotbar, selected);
			level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.5, getZ(), 10, 0.3, 0.4, 0.3, 0.02);
			discard();
			return;
		}
		Entity risen = rebuild();
		if (risen instanceof LivingEntity living) {
			living.setUUID(java.util.UUID.randomUUID());
			living.snapTo(getX(), getY(), getZ(), getYRot(), 0.0F);
			living.setHealth(living.getMaxHealth());
			living.deathTime = 0;
			living.hurtTime = 0;
			living.clearFire();
			if (living instanceof Mob mob) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					mob.setItemSlot(slot, ItemStack.EMPTY);
				}
			}
			level.addFreshEntity(living);
			play(level, kind == CorpseKind.SKELETON ? SoundEvents.SKELETON_AMBIENT : SoundEvents.ZOMBIE_INFECT, voice());
			level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.5, getZ(), 10, 0.3, 0.4, 0.3, 0.02);
		}
		discard();
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (grabCooldown > 0) {
			// just let go of (thrown, dropped, tipped out): not caught again in the same click
			return InteractionResult.PASS;
		}
		if (isTumbling() || falling()) {
			// (0.4.36) caught as it falls: an empty hand takes it into your arms (an iron golem is too heavy to catch)
			if (!player.getItemInHand(hand).isEmpty() || isHeavy()) {
				return InteractionResult.PASS;
			}
			if (!(level() instanceof ServerLevel level)) {
				return InteractionResult.SUCCESS;
			}
			return carry(level, player);
		}
		if (!(level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);

		// a fish is looted in one go, whatever's in your hand: out comes its loot and the body's gone
		if (isFish()) {
			return lootFish(level);
		}
		// anything that burns can be set alight
		if (burns() && (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE))) {
			igniteForSeconds(8.0F);
			if (held.is(Items.FLINT_AND_STEEL)) {
				held.hurtAndBreak(1, player, hand);
				play(level, SoundEvents.FLINTANDSTEEL_USE, 1.0F);
			} else {
				held.consume(1, player);
				play(level, SoundEvents.FIRECHARGE_USE, 1.0F);
			}
			return InteractionResult.SUCCESS_SERVER;
		}

		// solid iron (an iron golem): too heavy to lift by hand, and nothing to butcher. Only a power moves it
		// (Jaymo's Powers' Gravity, through takeBy); it can still be searched
		if (isHeavy() && (held.isEmpty() && player.isShiftKeyDown() || held.is(ItemTags.AXES))) {
			player.sendOverlayMessage(Component.translatable(held.isEmpty()
				? "message.jaymoskitchen.corpse.too_heavy" : "message.jaymoskitchen.corpse.no_butchering"));
			return InteractionResult.SUCCESS_SERVER;
		}
		// sneak with an empty hand: carry any corpse in your arms (a skeleton, a spider, a chicken...)
		if (player.isShiftKeyDown() && held.isEmpty()) {
			return carry(level, player);
		}
		// an empty hand on anything small enough (whatever it was): into a bag in your pocket
		if (held.isEmpty() && kind != CorpseKind.PLAYER && fitsInPocket()) {
			return pickUp(level, player);
		}

		switch (kind) {
			case ANIMAL -> {
				return interactAnimal(level, player, held);
			}
			case PLAYER -> {
				PlayerCorpses.restore(this, player, hotbar, selected);
				return InteractionResult.SUCCESS_SERVER;
			}
			case SKELETON -> {
				// its gear comes off first, then its bones
				if (!takeGear(level, player)) {
					pullBone(level);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
			default -> {
				return search(level, player);
			}
		}
	}

	private InteractionResult interactAnimal(ServerLevel level, Player player, ItemStack held) {
		// an axe butchers it anywhere; on a butcher block it's done properly and pays better
		if (held.is(ItemTags.AXES)) {
			held.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
			chops++;
			jolt(true);
			play(level, SoundEvents.AXE_STRIP.value(), 0.8F + chops * 0.1F);
			level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.3, getZ(), 8, 0.3, 0.1, 0.3, 0.1);
			if (chops >= CHOPS) {
				boolean onBlock = onButcherBlock();
				butcher(level, onBlock);
				if (!onBlock) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.corpse.rough_job"));
				}
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!held.isEmpty()) {
			player.sendOverlayMessage(Component.translatable(onButcherBlock()
				? "message.jaymoskitchen.corpse.need_axe" : "message.jaymoskitchen.corpse.carry"));
			return InteractionResult.SUCCESS_SERVER;
		}
		return pickUp(level, player);
	}

	/**
	 * Takes the corpse: a small animal goes in your pocket; anything else (big animals, and any other corpse) is
	 * carried in front of you, one at a time.
	 */
	private InteractionResult pickUp(ServerLevel level, Player player) {
		if (!fitsInPocket()) {
			return carry(level, player);
		}
		ItemStack pocketed = CorpseItem.of(this);
		if (!player.getInventory().add(pocketed)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.corpse.no_room"));
			return InteractionResult.SUCCESS_SERVER;
		}
		play(level, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F);
		discard();
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * Taken by something other than a click (Jaymo's Powers: Gravity draws a corpse in to you): into a pocket if it
	 * fits (never a player's), otherwise carried in your arms.
	 */
	public void takeBy(Player player) {
		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return;
		}
		if (kind != CorpseKind.PLAYER && fitsInPocket()) {
			pickUp(level, player);
		} else {
			carry(level, player);
		}
	}

	/**
	 * (0.4.28) Taken up in the arms of something that isn't a player (Jaymo's Kingdoms: a guard carrying a monster's body
	 * to the fire). False if it's heavy (iron), already carried, in a cart, or just let go of.
	 */
	public boolean carryBy(Entity carrier) {
		if (!(level() instanceof ServerLevel level) || isRemoved() || isHeavy() || carrier() != null || isPassenger() || grabCooldown > 0) {
			return false;
		}
		setCarrier(carrier);
		this.entityData.set(TUMBLING, false);
		play(level, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F);
		return true;
	}

	/**
	 * (0.4.28) Server: whoever carries it throws it at {@code target} (a fire or a lava pool a few blocks off), tumbling.
	 * Nothing happens if it isn't being carried.
	 */
	public void throwAt(Vec3 target) {
		Entity carrier = carrier();
		if (carrier == null) {
			return;
		}
		setCarrier(null);
		grabCooldown = GRAB_COOLDOWN;
		setOnGround(false);
		this.entityData.set(POSE, random.nextInt());
		this.entityData.set(TUMBLING, true);
		Vec3 off = target.subtract(position()).multiply(1.0, 0.0, 1.0);
		// a lob that comes down about where it was aimed: up at 0.3 against gravity's 0.04 it's in the air ~16 ticks, and
		// air drag (0.91 a tick) leaves about 9 ticks' worth of its starting speed
		double flight = 9.0;
		setDeltaMovement(off.x / flight, 0.3, off.z / flight);
		lieDown(getDeltaMovement(), false);
		if (level() instanceof ServerLevel level) {
			play(level, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F);
		}
	}

	/** Carried in your arms, in front of you (one at a time). */
	private InteractionResult carry(ServerLevel level, Player player) {
		if (carriedBy(player) != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.corpse.one_at_a_time"));
			return InteractionResult.SUCCESS_SERVER;
		}
		setCarrier(player);
		this.entityData.set(TUMBLING, false);
		play(level, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Chopped up on the butcher block: out comes the loot, plus a bit extra for doing it properly. */
	/**
	 * Chopped up. On a butcher block: everything it would have dropped, extra meat, and the block-only extras (see
	 * {@link ButcheringBonus}). On the ground: a rough job, about half the usual.
	 */
	private void butcher(ServerLevel level, boolean onBlock) {
		if (isHeavy()) {
			return;
		}
		if (onBlock) {
			for (ItemStack extra : blockExtras()) {
				spawnAtLocation(level, extra, 0.4F);
			}
		} else {
			List<ItemStack> rough = new ArrayList<>();
			for (ItemStack stack : loot) {
				int count = stack.getCount() / 2;
				if (count == 0 && random.nextBoolean()) {
					count = 1;
				}
				if (count > 0) {
					rough.add(stack.copyWithCount(count));
				}
			}
			loot = rough;
		}
		dropLoot(level);
		level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.3, getZ(), 20, 0.4, 0.2, 0.4, 0.2);
		play(level, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F);
		discard();
	}

	/** What butchering on a block adds to the loot: a little extra meat, and the block-only extras ({@link ButcheringBonus}). */
	private List<ItemStack> blockExtras() {
		List<ItemStack> extras = new ArrayList<>();
		for (ItemStack stack : loot) {
			if (stack.has(DataComponents.FOOD)) {
				extras.add(stack.copyWithCount(1 + random.nextInt(2)));
				break;
			}
		}
		extras.addAll(ButcheringBonus.roll(sourceType(), random));
		return extras;
	}

	/** (0.4.32) Everything a proper job on a butcher block gets out of it (its loot and the extras), without dropping any of it. */
	List<ItemStack> butcheredDrops() {
		List<ItemStack> drops = new ArrayList<>();
		for (ItemStack stack : loot) {
			drops.add(stack.copy());
		}
		drops.addAll(blockExtras());
		return drops;
	}

	/** (0.4.32) A passive animal's body, the only kind a butcher villager will take: no monsters, nothing heavy. */
	public boolean isButcherable() {
		return kind == CorpseKind.ANIMAL && !isHostile() && !isHeavy();
	}

	/** (0.4.35) Records who killed it, for {@link Scavengers}. */
	void setKiller(@Nullable Entity killer, boolean byPlayer) {
		this.killer = killer == null ? "" : killer.getStringUUID();
		this.killedByPlayer = byPlayer;
	}

	/** (0.4.35) Whether {@code mob} made this kill itself. */
	public boolean killedBy(Entity mob) {
		return !killer.isEmpty() && killer.equals(mob.getStringUUID());
	}

	/**
	 * (0.4.35) Whether a scavenger may eat this body: an animal, a fish, or a person, lying still on the ground. Monsters'
	 * bodies, the risen dead, iron, players' bodies and anything burning, carried, or carted are left alone, and so are
	 * players' kills unless the scavenger killed it first itself.
	 */
	public boolean isCarrionFor(Entity mob) {
		if (isRemoved() || kind == CorpseKind.ZOMBIE || kind == CorpseKind.SKELETON || kind == CorpseKind.PLAYER
			|| isPlayerCorpse() || isHostile() || isHeavy() || isOnFire() || carrier() != null || isPassenger()
			|| isTumbling() || isNoGravity() || this.entityData.get(CRUMBLING)) {
			return false;
		}
		return killedBy(mob) || !killedByPlayer;
	}

	/** (0.4.35) A scavenger's bite: the body jerks hard, torn at. Returns the bites taken so far. */
	int bite() {
		jolt(true);
		return ++bites;
	}

	/**
	 * (0.4.35) Torn open at last: what it carried spills on the ground (the meat for the scavengers, leather and the
	 * like for whoever comes by), with a bone or two, and the body is gone. Returns the dropped meat.
	 */
	List<net.minecraft.world.entity.item.ItemEntity> tearApart(ServerLevel level) {
		List<net.minecraft.world.entity.item.ItemEntity> meat = new ArrayList<>();
		List<ItemStack> spill = new ArrayList<>(loot);
		loot.clear();
		if (spill.stream().noneMatch(stack -> stack.has(DataComponents.FOOD))) {
			// nothing that was meat to a person: the flesh itself, then
			spill.add(new ItemStack(Items.ROTTEN_FLESH, 1 + random.nextInt(2)));
		}
		if (kind != CorpseKind.FISH) {
			spill.add(new ItemStack(Items.BONE, 1 + random.nextInt(2)));
		}
		for (ItemStack stack : spill) {
			net.minecraft.world.entity.item.ItemEntity dropped = spawnAtLocation(level, stack.copy(), 0.3F);
			if (dropped != null && stack.has(DataComponents.FOOD)) {
				meat.add(dropped);
			}
		}
		discard();
		return meat;
	}

	/** (0.4.32) Passed straight from whoever holds it into {@code carrier}'s arms (a butcher villager taking it from a player). */
	void handTo(Entity carrier) {
		setCarrier(carrier);
		this.entityData.set(TUMBLING, false);
		grabCooldown = 0;
	}

	private boolean onButcherBlock() {
		BlockPos below = BlockPos.containing(getX(), getY() - 0.2, getZ());
		return Corpses.isButcherBlock(level().getBlockState(below));
	}

	private void pullBone(ServerLevel level) {
		if (bones > 0) {
			bones--;
			jolt(false);
			spawnAtLocation(level, new ItemStack(Items.BONE), 0.3F);
			play(level, SoundEvents.SKELETON_HURT, 1.2F);
		}
		if (bones <= 0) {
			// nothing left holding it together
			dropLoot(level);
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 10, 0.3, 0.1, 0.3, 0.02);
			play(level, SoundEvents.SKELETON_DEATH, 0.9F);
			discard();
		}
	}

	private InteractionResult search(ServerLevel level, Player player) {
		if (isHeavy()) {
			// stripped of what's worth taking, the iron gives way: it falls apart, then it's gone
			dropLoot(level);
			searched = true;
			play(level, SoundEvents.BUNDLE_DROP_CONTENTS, 1.0F);
			startCrumbling(level);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (searched || loot.isEmpty()) {
			searched = true;
			// searched: what it's wearing and holding comes off next, a piece at a time
			if (takeGear(level, player)) {
				return InteractionResult.SUCCESS_SERVER;
			}
			player.sendOverlayMessage(Component.translatable(kind == CorpseKind.ZOMBIE || kind == CorpseKind.SKELETON ? "message.jaymoskitchen.corpse.burn_zombie"
				: isHostile() ? "message.jaymoskitchen.corpse.burn_hostile" : "message.jaymoskitchen.corpse.searched"));
			return InteractionResult.SUCCESS_SERVER;
		}
		dropLoot(level);
		searched = true;
		jolt(false);
		play(level, SoundEvents.BUNDLE_DROP_CONTENTS, 1.0F);
		robbed(player);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** (0.4.29) A fish corpse: its loot drops and it's gone, in one click. */
	private InteractionResult lootFish(ServerLevel level) {
		dropLoot(level);
		level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 12, 0.25, 0.1, 0.25, 0.05);
		play(level, SoundEvents.BUNDLE_DROP_CONTENTS, 1.0F);
		discard();
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * A fish (see {@link CorpseKind#FISH}). Fish corpses left from before 0.4.29 were saved as {@link CorpseKind#OTHER},
	 * so those count by their type too.
	 */
	public boolean isFish() {
		if (kind == CorpseKind.FISH) {
			return true;
		}
		EntityType<?> type = kind == CorpseKind.OTHER ? sourceType() : null;
		return type != null && type.builtInRegistryHolder().is(Corpses.FISH_CORPSES);
	}

	/** (0.5.20) How big the mob was against its kind (a scaled one's scale), 1 if it can't be told. */
	float bodyScale() {
		return rebuild() instanceof LivingEntity mob ? mob.getScale() : 1.0F;
	}

	/** (0.5.20) Its loot, taken out of it (for a body that's gone some other way: see SpiderCrush#crushCorpse). */
	List<ItemStack> takeLoot() {
		List<ItemStack> taken = new ArrayList<>(loot);
		loot.clear();
		return taken;
	}

	private void dropLoot(ServerLevel level) {
		for (ItemStack stack : loot) {
			spawnAtLocation(level, stack.copy(), 0.3F);
		}
		loot.clear();
	}

	private void play(ServerLevel level, SoundEvent sound, float pitch) {
		level.playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, 1.0F, pitch);
	}

	/** What the carried-corpse item needs to put this corpse back down later. */
	CorpseData toData() {
		List<ItemStackTemplate> templates = new ArrayList<>();
		for (ItemStack stack : loot) {
			if (!stack.isEmpty()) {
				templates.add(ItemStackTemplate.fromNonEmptyStack(stack));
			}
		}
		return new CorpseData(look(), templates);
	}

	/** Whether fire takes it: everything but a heavy (iron) corpse. */
	public boolean burns() {
		return kind.burns() && !isHeavy();
	}

	/** Client: how far it has fallen apart, 0 (whole) to 1 (gone). */
	public float crumble(float partialTicks) {
		return this.entityData.get(CRUMBLING) ? Math.min(1.0F, (crumbleTicks + partialTicks) / CRUMBLE_TICKS) : 0.0F;
	}

	/** Starts a heavy corpse falling apart (once). */
	private void startCrumbling(ServerLevel level) {
		if (!this.entityData.get(CRUMBLING)) {
			this.entityData.set(CRUMBLING, true);
			crumbleTicks = 0;
			jolt(true);
			play(level, SoundEvents.IRON_GOLEM_DAMAGE, 0.7F);
		}
	}

	/** Falling apart: plates of iron crack off with a clank, and at the end the last of it collapses and is gone. */
	private void tickCrumbling() {
		crumbleTicks++;
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		net.minecraft.core.particles.BlockParticleOption iron = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
			net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
		float w = getBbWidth() / 2.0F;
		if (crumbleTicks % 8 == 1) {
			jolt(crumbleTicks > CRUMBLE_TICKS / 2);
			level.sendParticles(iron, getX(), getY() + 0.3, getZ(), 10, w, 0.2, w, 0.15);
			level.playSound(null, getX(), getY(), getZ(), random.nextBoolean() ? SoundEvents.CHAIN_BREAK : SoundEvents.IRON_GOLEM_DAMAGE,
				SoundSource.NEUTRAL, 0.8F, 0.6F + random.nextFloat() * 0.3F);
		}
		if (crumbleTicks >= CRUMBLE_TICKS) {
			level.sendParticles(iron, getX(), getY() + 0.3, getZ(), 50, w, 0.3, w, 0.2);
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 12, w, 0.1, w, 0.02);
			play(level, SoundEvents.IRON_GOLEM_DEATH, 0.8F);
			play(level, SoundEvents.ANVIL_LAND, 0.5F);
			discard();
		}
	}

	/** Too heavy to lift by hand and can't be butchered (an iron golem): see {@link Corpses#HEAVY_CORPSES}. */
	public boolean isHeavy() {
		EntityType<?> type = sourceType();
		return type != null && type.builtInRegistryHolder().is(Corpses.HEAVY_CORPSES);
	}

	/** A cow-sized carcass: needs a 2x2 butcher table, and is slower to carry. */
	public boolean isBigCarcass() {
		EntityType<?> type = sourceType();
		return type != null && type.builtInRegistryHolder().is(Corpses.BIG_CARCASSES);
	}

	/**
	 * Where a corpse goes when set down on the butcher block at {@code clicked}: the middle of that block, or for a
	 * big carcass the middle of a 2x2 of butcher blocks including it (the one nearest {@code hit}), or null if
	 * there isn't one.
	 */
	public static @Nullable Vec3 tableSpot(Level level, BlockPos clicked, Vec3 hit, boolean big) {
		if (!big) {
			return new Vec3(clicked.getX() + 0.5, clicked.getY() + 1.0, clicked.getZ() + 0.5);
		}
		Vec3 best = null;
		for (int dx = -1; dx <= 0; dx++) {
			for (int dz = -1; dz <= 0; dz++) {
				BlockPos corner = clicked.offset(dx, 0, dz);
				if (Corpses.isButcherBlock(level.getBlockState(corner))
					&& Corpses.isButcherBlock(level.getBlockState(corner.east()))
					&& Corpses.isButcherBlock(level.getBlockState(corner.south()))
					&& Corpses.isButcherBlock(level.getBlockState(corner.east().south()))) {
					Vec3 spot = new Vec3(corner.getX() + 1.0, clicked.getY() + 1.0, corner.getZ() + 1.0);
					if (best == null || spot.distanceToSqr(hit) < best.distanceToSqr(hit)) {
						best = spot;
					}
				}
			}
		}
		return best;
	}

	/** A hostile mob's corpse (what Jaymo's Kingdoms' guards burn). */
	public boolean isHostile() {
		EntityType<?> type = sourceType();
		return type != null && type.getCategory() == MobCategory.MONSTER;
	}

	private boolean isCreeper() {
		return sourceType() == EntityTypes.CREEPER;
	}

	/** (0.4.48) A hostile body that rots as fast as any other: a spider, a cave spider, a creeper. */
	private boolean rotsQuickly() {
		EntityType<?> type = sourceType();
		return type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER || type == EntityTypes.CREEPER;
	}

	private boolean isChargedCreeper() {
		CompoundTag tag = parse(look());
		return tag != null && tag.getBooleanOr("powered", false);
	}

	/**
	 * What kind of mob the look is, worked out once per look: parsing the look (the mob's whole saved data, gear and
	 * all) is far too slow to do every tick, and the tick asks (is it a creeper? hostile?) every tick. No initial values:
	 * the look is set while the entity is still being built, before field initialisers would run.
	 */
	private @Nullable EntityType<?> sourceTypeCache;
	private boolean typeKnown;

	/** The type of mob this was, for naming the carried corpse. */
	public @Nullable EntityType<?> sourceType() {
		if (!typeKnown) {
			sourceTypeCache = typeOf(parse(look())).orElse(null);
			typeKnown = true;
		}
		return sourceTypeCache;
	}

	/** Puts a carried animal corpse back into the world. */
	void restore(CorpseData data) {
		List<ItemStack> stacks = new ArrayList<>();
		for (ItemStackTemplate template : data.loot()) {
			stacks.add(template.create());
		}
		// a bagged corpse comes back as what it was (a bagged zombie still gets up one day)
		CorpseKind restored = CorpseKind.ANIMAL;
		if (rebuildFrom(data.look()) instanceof LivingEntity body) {
			restored = CorpseKind.of(body);
		}
		setUp(data.look(), restored, stacks, 0);
	}

	private @Nullable Entity rebuildFrom(String look) {
		CompoundTag tag = parse(look);
		Optional<EntityType<?>> type = typeOf(tag);
		if (type.isEmpty()) {
			return null;
		}
		Entity entity = type.get().create(level(), EntitySpawnReason.LOAD);
		if (entity != null) {
			entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level().registryAccess(), tag));
		}
		return entity;
	}

	@Override
	public boolean fireImmune() {
		return !burns() || super.fireImmune();
	}

	@Override
	public boolean isPickable() {
		// a carried corpse is see-through to the crosshair, so you can aim at where to put it down
		return !isRemoved() && carrier() == null;
	}

	/** Small animals (a chicken, a rabbit, any baby) can be pocketed; anything bigger has to be carried. */
	private boolean fitsInPocket() {
		Entity body = rebuild();
		return body != null && body.getBbWidth() <= POCKET_WIDTH && body.getBbHeight() <= POCKET_HEIGHT;
	}

	public @Nullable Entity carrier() {
		int id = this.entityData.get(CARRIER);
		return id < 0 ? null : level().getEntity(id);
	}

	private void setCarrier(@Nullable Entity carrier) {
		if (carrier != null && isPassenger()) {
			// lifted out of a cart
			stopRiding();
		}
		if (carrier != null && this.entityData.get(SLUNG) >= 0) {
			// (0.4.48) lifted off a horse's back
			unsling(false);
		}
		this.entityData.set(CARRIER, carrier == null ? -1 : carrier.getId());
		if (carrier != null) {
			this.ragdoll = null;
			// (0.5.23) pulled down off the wall
			this.entityData.set(PINNED, -1);
		}
		setNoGravity(carrier != null);
		this.noPhysics = carrier != null;
	}

	// ---- (0.4.48) slung over a horse --------------------------------------------------------------------------------

	/** The horse it's slung over, if any (either side). */
	public @Nullable Entity slungOn() {
		int id = this.entityData.get(SLUNG);
		return id < 0 ? null : level().getEntity(id);
	}

	/** Small enough to sling over a horse: up to a sheep's size, or a person's; never a cow, nor anything too heavy to lift. */
	public boolean slingable() {
		if (isHeavy()) {
			return false;
		}
		if (isPlayerCorpse()) {
			return true;
		}
		EntityType<?> type = sourceType();
		if (type == null) {
			return false;
		}
		EntityDimensions standing = type.getDimensions();
		return standing.width() <= SLING_WIDTH && standing.height() <= SLING_HEIGHT
			|| standing.width() <= PERSON_WIDTH && standing.height() <= PERSON_HEIGHT;
	}

	/** (0.4.49) The place on the horse's back it lies in. */
	public int slungSlot() {
		return this.entityData.get(SLUNG_SLOT);
	}

	/**
	 * Server: {@code player} throws the body they're carrying up over {@code horse}'s back (a free place on it), where it
	 * stays as the horse goes. False (with word why) if it's too big or the horse is loaded already.
	 */
	public boolean slingOnto(Player player, net.minecraft.world.entity.animal.equine.AbstractHorse horse) {
		if (!(level() instanceof ServerLevel level) || carrier() != player || !horse.isAlive()) {
			return false;
		}
		if (!slingable()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.corpse.sling_too_big"));
			return false;
		}
		boolean[] taken = new boolean[SLUNG_MAX];
		for (CorpseEntity other : level.getEntitiesOfClass(CorpseEntity.class, horse.getBoundingBox().inflate(3.0),
			c -> c != this && c.entityData.get(SLUNG) == horse.getId())) {
			int slot = other.entityData.get(SLUNG_SLOT);
			if (slot >= 0 && slot < SLUNG_MAX) {
				taken[slot] = true;
			}
		}
		int slot = -1;
		for (int i = 0; i < SLUNG_MAX; i++) {
			if (!taken[i]) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.corpse.sling_full"));
			return false;
		}
		setCarrier(null);
		this.entityData.set(TUMBLING, false);
		this.entityData.set(POSE, random.nextInt());
		this.entityData.set(SLUNG_SLOT, slot);
		this.entityData.set(SLUNG_AT, level.getGameTime());
		this.entityData.set(SLUNG, horse.getId());
		this.slungHorse = horse.getUUID();
		this.slingFrom = position();
		this.ragdoll = null;
		setNoGravity(true);
		this.noPhysics = true;
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		play(level, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F);
		play(level, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.7F);
		return true;
	}

	/** Off the horse's back: lifted off (into someone's arms), or sliding off as it falls (the horse gone or dead). */
	private void unsling(boolean fall) {
		this.entityData.set(SLUNG, -1);
		this.slungHorse = null;
		this.slingFrom = null;
		setNoGravity(false);
		this.noPhysics = false;
		if (fall) {
			grabCooldown = GRAB_COOLDOWN;
			setOnGround(false);
			this.entityData.set(TUMBLING, true);
			setDeltaMovement(0.0, 0.1, 0.0);
			lieDown(getDeltaMovement(), false);
		}
	}

	/** Where a body lies across {@code horse}'s back in place {@code slot}: behind the saddle, across it. */
	public static Vec3 slungPosition(Entity horse, int slot) {
		float yaw = (horse instanceof LivingEntity living ? living.yBodyRot : horse.getYRot()) * Mth.DEG_TO_RAD;
		double back = SLUNG_BACK + slot * SLUNG_SPACING;
		return new Vec3(horse.getX() + Mth.sin(yaw) * back, horse.getY() + horse.getBbHeight() * SLUNG_HEIGHT, horse.getZ() - Mth.cos(yaw) * back);
	}

	/**
	 * Each tick while slung: thrown up in an arc onto the back, then carried there as the horse goes (both sides, so it
	 * moves smoothly). Slides off if the horse dies or goes. True if it's slung (the rest of the tick is skipped).
	 */
	private boolean tickSlung() {
		if (this.entityData.get(SLUNG) < 0) {
			if (this.slungHorse != null && level() instanceof ServerLevel level) {
				// after a reload: find the horse again (ids change), or slide off once it's clearly not coming
				Entity horse = level.getEntity(this.slungHorse);
				if (horse instanceof net.minecraft.world.entity.animal.equine.AbstractHorse && horse.isAlive()) {
					this.entityData.set(SLUNG_AT, 0L);
					this.entityData.set(SLUNG, horse.getId());
					setNoGravity(true);
					this.noPhysics = true;
				} else if (tickCount > 100) {
					unsling(true);
				} else {
					setDeltaMovement(Vec3.ZERO);
					return true;
				}
			} else {
				return false;
			}
		}
		Entity horse = slungOn();
		if (horse == null || !horse.isAlive() || horse.level() != level()) {
			if (level() instanceof ServerLevel) {
				unsling(true);
				return false;
			}
			// (the client waits for the server's word)
			return true;
		}
		Vec3 rest = slungPosition(horse, this.entityData.get(SLUNG_SLOT));
		float t = Mth.clamp((level().getGameTime() - this.entityData.get(SLUNG_AT)) / (float)SLING_TICKS, 0.0F, 1.0F);
		Vec3 at = rest;
		if (t < 1.0F && this.slingFrom != null) {
			float eased = t * t * (3.0F - 2.0F * t);
			at = this.slingFrom.lerp(rest, eased).add(0.0, SLING_ARC * Mth.sin(t * Mth.PI), 0.0);
		}
		setPos(at.x, at.y, at.z);
		setYRot((horse instanceof LivingEntity living ? living.yBodyRot : horse.getYRot()) + 90.0F);
		setDeltaMovement(Vec3.ZERO);
		if (level().isClientSide()) {
			// (0.4.49) limp over the back: its limbs swing with the horse's stride, as a carried body's do
			swingLimbs(horse);
		}
		if (level() instanceof ServerLevel level) {
			serverTick(level);
		}
		return true;
	}

	/** Where a carried corpse sits for a carrier at the given position and facing. */
	public static Vec3 carryPosition(Entity carrier, float yaw, double x, double y, double z) {
		return carryPosition(carrier, yaw, x, y, z, 0.0F);
	}

	/**
	 * The same, for a corpse this wide: a big one is held further out (and lower), in front of you, not around you.
	 * (0.4.31) A carrier bigger than a person (an iron golem) holds it out past its own bulk, up at its own arms.
	 */
	public static Vec3 carryPosition(Entity carrier, float yaw, double x, double y, double z, float width) {
		float radians = yaw * Mth.DEG_TO_RAD;
		double forward = CARRY_FORWARD + Math.max(0.0, width * 0.5 - 0.45) + Math.max(0.0, carrier.getBbWidth() * 0.5 - CARRIER_HALF_WIDTH);
		double height = (width > 1.0F ? 0.3 : CARRY_HEIGHT) * Math.max(1.0, carrier.getBbHeight() / CARRIER_HEIGHT);
		return new Vec3(x - Mth.sin(radians) * forward, y + height, z + Mth.cos(radians) * forward);
	}

	/** The corpse this player is carrying, if any (either side). */
	public static @Nullable CorpseEntity carriedBy(Player player) {
		for (CorpseEntity corpse : player.level().getEntitiesOfClass(CorpseEntity.class,
			player.getBoundingBox().inflate(4.0), corpse -> corpse.entityData.get(CARRIER) == player.getId())) {
			return corpse;
		}
		return null;
	}

	/** Whether it's falling from someone's arms, not landed yet. */
	public boolean isTumbling() {
		return this.entityData.get(TUMBLING);
	}

	/** Server: the carrier lets go. It drops from where it was held, carried on by their momentum, tumbling limp. */
	public void drop(Player player) {
		letGo(player);
		lieDown(getDeltaMovement(), false);
	}

	/** Lets go of the body (see {@link #drop}), without making its physics body yet: toss sets its speed first. */
	private void letGo(Player player) {
		setCarrier(null);
		grabCooldown = GRAB_COOLDOWN;
		setOnGround(false);
		// it lands differently each time
		this.entityData.set(POSE, random.nextInt());
		this.entityData.set(TUMBLING, true);
		Vec3 ahead = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0).normalize().scale(0.12);
		setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0).add(ahead.x, 0.08, ahead.z));
		if (level() instanceof ServerLevel level) {
			play(level, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6F);
		}
	}

	/**
	 * (0.4.36) A body in the air is easy to catch: the crosshair takes it from up to this far off it (blocks), as if it
	 * were pulled toward your grab.
	 */
	private static final float CATCH_RADIUS = 2.0F;

	@Override
	public float getPickRadius() {
		return falling() ? CATCH_RADIUS : super.getPickRadius();
	}

	/**
	 * (0.4.45) A body in the air is drawn however far it has fallen from you: the usual cut-off (64 times its small box,
	 * about 50 blocks) made a long fall vanish part way down.
	 */
	@Override
	public boolean shouldRenderAtSqrDistance(double distanceSq) {
		return isTumbling() || falling() || super.shouldRenderAtSqrDistance(distanceSq);
	}

	/** In the air on its own: dropped, thrown, or falling off something (not held, not in a cart, not lying down). */
	public boolean falling() {
		return !onGround() && carrier() == null && !isPassenger() && !isNoGravity() && this.entityData.get(SLUNG) < 0;
	}

	/** Ticks after being let go before it can be grabbed again. */
	private static final int GRAB_COOLDOWN = 20;
	private int grabCooldown;

	/**
	 * Server: tipped out of a cart. Put down clear of it on the side it tips to ({@code away}, a unit vector), and
	 * flung on outward, tumbling, so it doesn't land under the cart.
	 */
	public void tipOut(CartEntity cart, Vec3 away) {
		stopRiding();
		grabCooldown = GRAB_COOLDOWN;
		setOnGround(false);
		this.entityData.set(POSE, random.nextInt());
		this.entityData.set(TUMBLING, true);
		double out = cart.getBbWidth() * 0.5 + 0.6 + random.nextDouble() * 0.3;
		setPos(cart.getX() + away.x * out, cart.getY() + 0.7, cart.getZ() + away.z * out);
		setDeltaMovement(away.x * (0.14 + random.nextDouble() * 0.06), 0.12, away.z * (0.14 + random.nextDouble() * 0.06));
		lieDown(getDeltaMovement(), false);
	}

	/** Server: thrown a short way ahead of the carrier, tumbling; it lands on the ground or in a cart it hits. */
	public void toss(Player player) {
		letGo(player);
		Vec3 ahead = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0).normalize();
		setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0).add(ahead.x * 0.42, 0.28, ahead.z * 0.42));
		lieDown(getDeltaMovement(), false);
		if (level() instanceof ServerLevel level) {
			play(level, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F);
		}
	}

	/**
	 * Room it takes in a cart: 3 for anything cow-sized or bigger (standing a block and a fifth tall or wide: cows,
	 * sheep, people, zombies), 1 for small ones (chickens, fish, pigs, rabbits); 0 if it won't fit in a cart at all.
	 */
	public int cartRoom() {
		if (isPlayerCorpse()) {
			return 3;
		}
		EntityType<?> type = sourceType();
		if (type == null) {
			return 0;
		}
		EntityDimensions standing = type.getDimensions();
		if (Math.max(standing.width(), standing.height()) > 2.2F) {
			return 0;
		}
		return standing.height() >= 1.2F || standing.width() >= 1.2F ? 3 : 1;
	}

	/** Server: a thrown (or dropped) body that hits an animal or supply cart lands in it, if there's room. */
	private boolean landInCart() {
		for (CartEntity cart : level().getEntitiesOfClass(CartEntity.class, getBoundingBox().inflate(0.25))) {
			if (cart.takesCorpse(this) && startRiding(cart)) {
				this.entityData.set(TUMBLING, false);
				setDeltaMovement(Vec3.ZERO);
				if (level() instanceof ServerLevel level) {
					play(level, SoundEvents.WOOD_FALL, 0.8F);
				}
				return true;
			}
		}
		return false;
	}

	/** Sets a carried corpse down at {@code at} (on top of a butcher block, or on the ground). */
	public void putDown(Vec3 at, float yaw) {
		setCarrier(null);
		this.entityData.set(TUMBLING, false);
		// set down off a ledge (or against the side of a block), it drops to the ground
		setOnGround(false);
		// it lands differently each time
		this.entityData.set(POSE, random.nextInt());
		snapTo(at.x, at.y, at.z, yaw, 0.0F);
		setDeltaMovement(Vec3.ZERO);
		// (0.5.1) lies there; on a butcher block it rests at once
		lieDown(Vec3.ZERO, onButcherBlock());
		if (level() instanceof ServerLevel level) {
			play(level, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.7F);
		}
	}

	/** (0.5.1) A blow knocks the body this hard: a base plus a share of the damage, at most the cap (blocks a tick). */
	private static final double SMACK_BASE = 0.18, SMACK_PER_DAMAGE = 0.04, SMACK_MAX = 0.7;
	/** (0.5.1) A blast of at least this much damage shreds the body; a weaker one only flings it. */
	private static final float SHRED_DAMAGE = Gibs.SHRED_DAMAGE;

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved() || carrier() != null) {
			return false;
		}
		if (!source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
			// (0.5.1) a blow or an arrow: it takes no harm, but the body is knocked about
			smack(source, damage);
			// (0.5.17) and bleeds (Jaymo's Powers' blood), see addOnHit
			hit(source, damage);
			// (0.5.20) a dead spider is still smashed to legs by a crushing blow
			return SpiderCrush.crushCorpse(this, level, source);
		}
		if (isCreeper()) {
			// the blast sets off whatever made it a creeper
			discard();
			level.explode(this, getX(), getY() + 0.3, getZ(), isChargedCreeper() ? 6.0F : 3.0F, Level.ExplosionInteraction.MOB);
			return true;
		}
		if (damage < SHRED_DAMAGE) {
			// (0.5.1) a weak blast: not enough to shred it; the knockback it gave has already flung the body
			return false;
		}
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 12, 0.4, 0.2, 0.4, 0.05);
		if (kind == CorpseKind.SKELETON) {
			// bare bones: they scatter
			play(level, SoundEvents.SKELETON_HURT, 0.6F);
			if (bones > 0) {
				spawnAtLocation(level, new ItemStack(Items.BONE, bones), 0.2F);
			}
			dropLoot(level);
			discard();
			return true;
		}
		spawnAtLocation(level, new ItemStack(Items.ROTTEN_FLESH, 1 + random.nextInt(2)), 0.2F);
		// (0.5.21) blown apart: chunks of it fly, its blood bursts, its loot drops loose
		return Gibs.blowApartCorpse(this, level, source, damage);
	}

	/** (0.5.17) Told of each blow or arrow that lands on a fleshy body; see {@link #addOnHit}. */
	private static final List<java.util.function.BiConsumer<Entity, java.util.Map.Entry<DamageSource, Float>>> ON_HIT = new ArrayList<>();

	/**
	 * (0.5.17) Jaymo's Powers: told of every blow or arrow that lands on a body that still has flesh on it (not a
	 * skeleton's, an iron one or one on fire), with the blow and its damage, so hitting the dead splatters their blood.
	 * Plain Java types, so it can be reached by reflection from a mod built against an older Kitchen.
	 */
	public static void addOnHit(java.util.function.BiConsumer<Entity, java.util.Map.Entry<DamageSource, Float>> hook) {
		ON_HIT.add(hook);
	}

	private void hit(DamageSource source, float damage) {
		if (ON_HIT.isEmpty() || damage <= 0.0F || kind == CorpseKind.SKELETON || isHeavy() || isOnFire()) {
			return;
		}
		java.util.Map.Entry<DamageSource, Float> blow = java.util.Map.entry(source, damage);
		for (var hook : ON_HIT) {
			try {
				hook.accept(this, blow);
			} catch (RuntimeException e) {
				com.jaymoskitchen.JaymosKitchen.LOGGER.warn("A corpse-hit hook failed: {}", e.toString());
			}
		}
	}

	/** (0.5.1) Knocks the body from a melee blow or a projectile: at the part nearest the attacker, along their look or the arrow's flight. */
	private void smack(DamageSource source, float damage) {
		if (ragdoll == null || isPassenger()) {
			return;
		}
		Entity direct = source.getDirectEntity();
		Entity attacker = source.getEntity();
		Entity from = direct != null ? direct : attacker;
		if (from == null) {
			return;
		}
		Vec3 at = direct != null && direct != attacker ? direct.position() : from.getEyePosition();
		Vec3 push = direct instanceof net.minecraft.world.entity.projectile.Projectile && direct != attacker
			? direct.getDeltaMovement() : from.getViewVector(1.0F);
		ragdoll.smack(at, push, Math.min(SMACK_MAX, SMACK_BASE + damage * SMACK_PER_DAMAGE));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(LOOK, input.getStringOr("Look", ""));
		this.kind = CorpseKind.byName(input.getStringOr("Kind", CorpseKind.OTHER.name()));
		this.loot = new ArrayList<>(input.read("Loot", ItemStack.CODEC.listOf()).orElse(List.of()));
		this.bones = input.getIntOr("Bones", 0);
		this.searched = input.getBooleanOr("Searched", false);
		this.chops = input.getIntOr("Chops", 0);
		this.burnTicks = input.getIntOr("Burned", 0);
		this.age = input.getIntOr("Age", 0);
		this.entityData.set(OWNER, input.getStringOr("Owner", ""));
		this.entityData.set(OWNER_NAME, input.getStringOr("OwnerName", ""));
		this.slim = input.getBooleanOr("Slim", false);
		List<ItemStack> savedHotbar = input.read("Hotbar", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
		for (int i = 0; i < this.hotbar.size(); i++) {
			this.hotbar.set(i, i < savedHotbar.size() ? savedHotbar.get(i) : ItemStack.EMPTY);
		}
		this.selected = input.getIntOr("Selected", 0);
		this.killer = input.getStringOr("Killer", "");
		this.killedByPlayer = input.getBooleanOr("KilledByPlayer", false);
		this.bites = input.getIntOr("Bites", 0);
		this.entityData.set(MISSING, input.getIntOr("Missing", 0));
		// (0.5.1) its ragdoll: the rig it was built for and where its points lay (saves without them lie down from the pose seed)
		this.rigShape = input.getIntOr("RigShape", -1);
		this.rigWidth = input.getFloatOr("RigW", 0.0F);
		this.rigHeight = input.getFloatOr("RigH", 0.0F);
		this.rigCache = null;
		List<Float> points = input.read("Points", Codec.FLOAT.listOf()).orElse(List.of());
		if (!points.isEmpty()) {
			this.savedPoints = new float[points.size()];
			for (int i = 0; i < savedPoints.length; i++) {
				this.savedPoints[i] = points.get(i);
			}
		}
		// whatever held it up (a gravity power) is gone after a reload
		setNoGravity(false);
		// (0.4.48) slung over a horse: found again by the horse's id once it's about (tickSlung)
		this.slungHorse = input.getString("SlungOn").map(s -> {
			try {
				return java.util.UUID.fromString(s);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
		if (this.slungHorse != null) {
			setNoGravity(true);
		}
		this.entityData.set(PINNED, input.getIntOr("Pinned", -1));
		if (pinned() >= 0) {
			setNoGravity(true);
		}
		refreshDimensions();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("Look", look());
		output.putString("Kind", kind.name());
		output.store("Loot", ItemStack.CODEC.listOf(), loot);
		output.putInt("Bones", bones);
		output.putBoolean("Searched", searched);
		output.putInt("Chops", chops);
		output.putInt("Burned", burnTicks);
		output.putInt("Age", age);
		output.putString("Owner", ownerId());
		output.putString("OwnerName", ownerName());
		output.store("Hotbar", ItemStack.OPTIONAL_CODEC.listOf(), hotbar);
		output.putInt("Selected", selected);
		output.putString("Killer", killer);
		output.putBoolean("KilledByPlayer", killedByPlayer);
		output.putInt("Bites", bites);
		output.putBoolean("Slim", slim);
		output.putInt("Missing", missing());
		if (rigShape >= 0) {
			output.putInt("RigShape", rigShape);
			output.putFloat("RigW", rigWidth);
			output.putFloat("RigH", rigHeight);
		}
		if (ragdoll != null) {
			float[] relative = ragdoll.body.relative(getX(), getY(), getZ());
			List<Float> points = new ArrayList<>(relative.length);
			for (float value : relative) {
				points.add(value);
			}
			output.store("Points", Codec.FLOAT.listOf(), points);
		}
		if (this.slungHorse != null) {
			output.putString("SlungOn", this.slungHorse.toString());
		}
		if (pinned() >= 0) {
			output.putInt("Pinned", pinned());
		}
	}
}
