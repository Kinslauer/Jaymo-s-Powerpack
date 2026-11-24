package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The crow of a hanging tree ({@link TreeSites}): a carrion bird in the parrot's frame (the familiar raven's coat, drawn by
 * {@code CrowRenderer}). It follows the three rules of living things:
 * <ul>
 * <li>Sympathy: it perches on the bough over the hanged and now and then flutters down to peck at the remains; its call
 * is the parrot's, pitched low; it is wary, and flies off from any player it doesn't know who comes within
 * {@link #WARY_RANGE} (a hand held out with seeds or meat is no threat: within {@link #BAITED_RANGE} it stays); it never
 * dances, copies other mobs' voices or takes to anyone.</li>
 * <li>Feedback: it lives off the hanged. When the remains are gone (taken down, buried or burned) it has nothing to
 * stay for: it flies off for good and is gone once out of sight.</li>
 * <li>Stewardship: a player who gives it seeds or meat by hand is known to it from then on (saved with it), is no longer
 * fled from, and once a day, when near its tree, is brought a small trinket and dropped at their feet. Nobody who leaves
 * it alone is punished.</li>
 * </ul>
 */
public final class Crow extends Parrot {
	// ---- tuning ----
	/** A stranger this near (blocks) puts it to flight; with food in hand, this near. */
	static final double WARY_RANGE = 8.0, BAITED_RANGE = 2.5;
	/** How far it flies from its perch when put to flight (blocks across, up). */
	static final double FLEE_REACH = 12.0, FLEE_RISE = 6.0;
	/** A friend this near its perch (blocks) is brought a gift (once a day). */
	static final double GIFT_RANGE = 14.0;
	/** How often it pecks at the hanged (1 in this many ticks while it sits), the pecks each time and their spacing (ticks). */
	static final int PECK_ODDS = 400, PECKS = 3, PECK_SPACING = 9;
	/** How often (ticks) it looks for the remains, and how long (ticks) it takes to leave once they're gone. */
	static final int CHECK_TICKS = 40, LEAVE_TICKS = 400;
	/** Out of sight: no player this near (blocks) once it has left for this many ticks. */
	static final double SIGHT_RANGE = 48.0;
	static final int LEAVE_MIN_TICKS = 60;
	/** What it brings a friend, and the chance it's an emerald instead. */
	static final Item[] TRINKETS = {Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.STONE_BUTTON, Items.OAK_BUTTON, Items.STRING, Items.FEATHER};
	static final float EMERALD_CHANCE = 0.12F;
	/** A call is the parrot's, pitched low. */
	static final float PITCH_MIN = 0.55F, PITCH_SPREAD = 0.15F;
	/** Between its calls (ticks; the parrot's is 80). */
	static final int CALL_INTERVAL = 200;

	private static final Codec<Map<String, Long>> FRIENDS = Codec.unboundedMap(Codec.STRING, Codec.LONG);
	public static EntityType<Crow> TYPE;

	/** The remains it lives off (the skull), where it sits, and the players it knows (id to the day it last brought them something). */
	private @Nullable BlockPos hanged, perch;
	private final Map<UUID, Long> friends = new HashMap<>();
	private boolean leaving;
	private int leftTicks;
	/** Set while the parrot's own step runs (it would copy a mob's voice), so nothing is said in it. */
	private boolean mute;
	private float nextFlap = 1.0F;

	public Crow(EntityType<? extends Crow> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("crow"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.of(Crow::new, MobCategory.CREATURE).sized(0.5F, 0.9F).clientTrackingRange(8).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, Parrot.createAttributes());
	}

	/** A crow on {@code perch}, living off the remains at {@code hanged}. */
	void settle(BlockPos hanged, BlockPos perch) {
		this.hanged = hanged;
		this.perch = perch;
		this.setPersistenceRequired();
	}

	// ---- goals ----

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new Leave());
		this.goalSelector.addGoal(2, new Flee());
		this.goalSelector.addGoal(3, new Bring());
		this.goalSelector.addGoal(4, new Peck());
		this.goalSelector.addGoal(5, new ReturnToPerch());
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, WARY_RANGE));
	}

	private static boolean offering(ItemStack stack) {
		return stack.is(ItemTags.PARROT_FOOD) || stack.is(ItemTags.MEAT);
	}

	/** {@code player} is someone it would fly from. */
	private boolean wary(Player player) {
		if (player.isSpectator() || !player.isAlive() || this.friends.containsKey(player.getUUID())) {
			return false;
		}
		boolean baited = offering(player.getMainHandItem()) || offering(player.getOffhandItem());
		return this.distanceTo(player) < (baited ? BAITED_RANGE : WARY_RANGE);
	}

	private @Nullable Player stranger() {
		for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(WARY_RANGE))) {
			if (this.wary(player)) {
				return player;
			}
		}
		return null;
	}

	private Vec3 perchSpot() {
		BlockPos at = this.perch != null ? this.perch : this.blockPosition();
		return Vec3.atBottomCenterOf(at);
	}

	private boolean remains() {
		return this.hanged == null || this.level().getBlockState(this.hanged).is(Blocks.SKELETON_SKULL);
	}

	/** Flying off for good, once the remains are gone. */
	private final class Leave extends Goal {
		private Vec3 away = Vec3.ZERO;

		Leave() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return Crow.this.leaving;
		}

		@Override
		public void start() {
			double angle = Crow.this.getRandom().nextDouble() * Math.PI * 2.0;
			this.away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Vec3 to = Crow.this.position().add(this.away.scale(20.0)).add(0.0, 8.0, 0.0);
			Crow.this.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.3);
		}
	}

	/** Away from a stranger: up and round the tree. */
	private final class Flee extends Goal {
		private @Nullable Player from;

		Flee() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (Crow.this.leaving || Crow.this.tickCount % 5 != 0) {
				return false;
			}
			this.from = Crow.this.stranger();
			return this.from != null;
		}

		@Override
		public boolean canContinueToUse() {
			return !Crow.this.leaving && Crow.this.stranger() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Player p = this.from;
			if (p == null || !Crow.this.wary(p)) {
				this.from = Crow.this.stranger();
				p = this.from;
				if (p == null) {
					return;
				}
			}
			Vec3 home = Crow.this.perchSpot();
			Vec3 dir = home.subtract(p.position()).multiply(1.0, 0.0, 1.0);
			dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : dir.normalize();
			Vec3 to = home.add(dir.scale(FLEE_REACH)).add(0.0, FLEE_RISE, 0.0);
			Crow.this.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.2);
		}
	}

	/** To a friend near its tree who hasn't had anything today: a trinket at their feet. */
	private final class Bring extends Goal {
		private @Nullable Player to;
		private boolean given;

		Bring() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		private long today() {
			return Math.floorDiv(Crow.this.level().getOverworldClockTime(), 24000L);
		}

		@Override
		public boolean canUse() {
			if (Crow.this.leaving || Crow.this.tickCount % 20 != 0 || Crow.this.friends.isEmpty()) {
				return false;
			}
			Vec3 home = Crow.this.perchSpot();
			long today = this.today();
			for (Player player : Crow.this.level().getEntitiesOfClass(Player.class, Crow.this.getBoundingBox().inflate(GIFT_RANGE * 2.0))) {
				Long last = Crow.this.friends.get(player.getUUID());
				if (last != null && last < today && !player.isSpectator() && player.isAlive() && player.position().distanceTo(home) <= GIFT_RANGE) {
					this.to = player;
					this.given = false;
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return !this.given && !Crow.this.leaving && this.to != null && this.to.isAlive() && Crow.this.distanceTo(this.to) < GIFT_RANGE * 2.0;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Player p = this.to;
			if (p == null) {
				return;
			}
			Crow.this.getLookControl().setLookAt(p);
			Crow.this.getMoveControl().setWantedPosition(p.getX(), p.getY() + 1.6, p.getZ(), 1.1);
			if (Crow.this.distanceTo(p) < 2.6 && Crow.this.level() instanceof ServerLevel level) {
				Crow.this.bring(level, p);
				Crow.this.friends.put(p.getUUID(), this.today());
				this.given = true;
			}
		}
	}

	/** Now and then, down to the remains for a few pecks. */
	private final class Peck extends Goal {
		private int pecked, age;

		Peck() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !Crow.this.leaving && Crow.this.hanged != null && Crow.this.onGround() && Crow.this.getRandom().nextInt(PECK_ODDS) == 0
				&& Crow.this.remains() && Crow.this.stranger() == null;
		}

		@Override
		public boolean canContinueToUse() {
			return !Crow.this.leaving && this.pecked < PECKS && this.age < 160 && Crow.this.remains() && Crow.this.stranger() == null;
		}

		@Override
		public void start() {
			this.pecked = 0;
			this.age = 0;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			BlockPos skull = Crow.this.hanged;
			if (skull == null) {
				return;
			}
			this.age++;
			Vec3 to = new Vec3(skull.getX() + 0.5, skull.getY() + 0.7, skull.getZ() + 0.5);
			Crow.this.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.0);
			if (Crow.this.position().distanceToSqr(to) < 3.0 && this.age % PECK_SPACING == 0) {
				this.pecked++;
				Crow.this.level().playSound(null, skull, SoundEvents.BONE_BLOCK_HIT, Crow.this.getSoundSource(), 0.7F, 1.6F + Crow.this.getRandom().nextFloat() * 0.3F);
			}
		}
	}

	/** Home to its perch when nothing else is asked of it. */
	private final class ReturnToPerch extends Goal {
		ReturnToPerch() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !Crow.this.leaving && Crow.this.perch != null && Crow.this.position().distanceToSqr(Crow.this.perchSpot()) > 1.5;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Vec3 at = Crow.this.perchSpot();
			Crow.this.getMoveControl().setWantedPosition(at.x, at.y, at.z, 1.0);
		}
	}

	// ---- the step ----

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.perch == null) {
			this.perch = this.blockPosition();
		}
		if (this.leaving) {
			this.leftTicks++;
			if (this.leftTicks > LEAVE_TICKS || this.leftTicks > LEAVE_MIN_TICKS && !level.hasNearbyAlivePlayer(this.getX(), this.getY(), this.getZ(), SIGHT_RANGE)) {
				this.discard();
			}
			return;
		}
		if (this.tickCount % CHECK_TICKS == 0 && this.hanged != null && Loaded.made(level, this.hanged) && !this.remains()) {
			// (nothing left to live off: it goes, and doesn't come back)
			this.leaving = true;
			this.leftTicks = 0;
		}
	}

	/** A hand held out with seeds or meat: a new friend. */
	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (this.leaving || !offering(held) || this.friends.containsKey(player.getUUID())) {
			return InteractionResult.PASS;
		}
		if (this.level() instanceof ServerLevel level) {
			if (!player.isCreative()) {
				held.shrink(1);
			}
			this.friends.put(player.getUUID(), -1L);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PARROT_EAT, this.getSoundSource(), 0.8F, 0.7F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.7, this.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		}
		return InteractionResult.SUCCESS;
	}

	/** What a friend is given, dropped at their feet. */
	private void bring(ServerLevel level, Player to) {
		Item item = level.getRandom().nextFloat() < EMERALD_CHANCE ? Items.EMERALD : TRINKETS[level.getRandom().nextInt(TRINKETS.length)];
		level.addFreshEntity(new ItemEntity(level, to.getX(), to.getY() + 0.3, to.getZ(), new ItemStack(item)));
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PARROT_AMBIENT, this.getSoundSource(), 0.8F, PITCH_MIN);
	}

	// ---- voice: the parrot's, pitched low, and never another mob's ----

	@Override
	public void aiStep() {
		// (the parrot's own step now and then copies a nearby mob's voice; nothing is said while it runs)
		this.mute = true;
		try {
			super.aiStep();
		} finally {
			this.mute = false;
		}
	}

	@Override
	public boolean isSilent() {
		return this.mute || super.isSilent();
	}

	private void say(SoundEvent sound, float volume, float pitch) {
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
	}

	@Override
	protected boolean isFlapping() {
		return this.flyDist > this.nextFlap;
	}

	@Override
	protected void onFlap() {
		this.say(SoundEvents.PARROT_FLY, 0.15F, 0.7F);
		this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.say(SoundEvents.PARROT_STEP, 0.15F, 0.8F);
	}

	@Override
	public @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.PARROT_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return CALL_INTERVAL;
	}

	@Override
	public float getVoicePitch() {
		return PITCH_MIN + this.random.nextFloat() * PITCH_SPREAD;
	}

	@Override
	public void setRecordPlayingNearby(BlockPos jukebox, boolean playing) {
		// (it doesn't dance)
	}

	// ---- not anyone's pet ----

	@Override
	public boolean canSitOnShoulder() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---- saving ----

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("Hanged", BlockPos.CODEC, this.hanged);
		output.storeNullable("Perch", BlockPos.CODEC, this.perch);
		Map<String, Long> known = new HashMap<>();
		this.friends.forEach((id, day) -> known.put(id.toString(), day));
		output.store("Friends", FRIENDS, known);
		output.putBoolean("Leaving", this.leaving);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.hanged = input.read("Hanged", BlockPos.CODEC).orElse(null);
		this.perch = input.read("Perch", BlockPos.CODEC).orElse(null);
		this.friends.clear();
		input.read("Friends", FRIENDS).orElse(Map.of()).forEach((id, day) -> {
			try {
				this.friends.put(UUID.fromString(id), day);
			} catch (IllegalArgumentException e) {
				// (a mangled id is just not remembered)
			}
		});
		this.leaving = input.getBooleanOr("Leaving", false);
	}
}
