package com.jaymoskingdoms;

import com.jaymoskingdoms.beasts.Acting;
import com.jaymoskingdoms.beasts.Beasts;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The ghost at a fresh grave, drawn from the ghost Blockbench model Andy gave us (a person's shape in pale see-through
 * blue, hanging a little off the ground and bobbing). For {@link #LINGER_DAYS} nights after a bearer buries someone
 * ({@link Bearers#GRAVES}), they stand over their own mound from dusk to dawn, while anyone is near enough to see
 * ({@link #SHOW_RANGE}): not yet gone to be weighed. They harm nobody and nothing harms them; they watch whoever comes
 * by, and say a few words if spoken to (right-click). Give one a flower or a candle and it thanks you and goes for good:
 * a little experience, and a little of its settlement's good opinion. Leave it be and it simply stops coming after its
 * last night. Ghosts are never saved with the world: each night's is raised anew from the grave's record.
 */
public class GraveGhost extends PathfinderMob implements Acting {
	/** How many nights after the burial it stands at its grave. */
	static final long LINGER_DAYS = 3;
	/** It rises while a player is this near its grave, and goes when none is within {@link #LEAVE_RANGE}. */
	static final double SHOW_RANGE = 40.0, LEAVE_RANGE = 64.0;
	/** How often the graves are looked over, and a ghost checks whether to go (ticks). */
	static final int CHECK_EVERY = 40;
	/** What laying one to rest earns: experience, and reputation with its settlement. */
	static final int REST_XP = 12, REST_REP = 2;
	/** Lines in the language file: what it says to an empty hand, and its thanks. */
	static final int IDLE_LINES = 5, THANKS_LINES = 3;
	/** Its light, for its words and the motes it leaves. */
	static final int COLOR = 0x8ACEFF;

	/** The graves (their headstones) whose dead have been laid to rest, on the Overworld. */
	static final AttachmentType<List<Long>> RESTED = AttachmentRegistry.create(JaymosKingdoms.id("ghosts_rested"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	/** The ghost standing at each grave (by its headstone) tonight. */
	private static final Map<Long, UUID> STANDING = new HashMap<>();

	/** Its grave's headstone and settlement; with none (a spawn egg's) it only waits for morning. */
	private long grave;
	private int realm = -1;
	private boolean hasGrave;

	public GraveGhost(EntityType<? extends GraveGhost> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
		this.setSilent(true);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GraveGhost::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STANDING.clear());
	}

	// ---- raising them --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (Beasts.GRAVE_GHOST == null || level.getGameTime() % CHECK_EVERY != 0 || !level.isDarkOutside()) {
			return;
		}
		List<Bearers.Grave> graves = level.getAttachedOrElse(Bearers.GRAVES, List.of());
		if (graves.isEmpty()) {
			return;
		}
		long day = Production.day(server);
		List<Long> rested = level.getAttachedOrElse(RESTED, List.of());
		for (Bearers.Grave g : graves) {
			if (day - g.day() > LINGER_DAYS || rested.contains(g.head())) {
				continue;
			}
			BlockPos head = g.headPos();
			UUID standing = STANDING.get(g.head());
			if (standing != null && level.getEntity(standing) instanceof GraveGhost ghost && ghost.isAlive()) {
				continue;
			}
			if (!level.isLoaded(head) || level.getNearestPlayer(head.getX() + 0.5, head.getY(), head.getZ() + 0.5, SHOW_RANGE, false) == null) {
				continue;
			}
			raise(level, g);
		}
	}

	private static void raise(ServerLevel level, Bearers.Grave g) {
		GraveGhost ghost = Beasts.GRAVE_GHOST.create(level, EntitySpawnReason.EVENT);
		if (ghost == null) {
			return;
		}
		BlockPos head = g.headPos(), plot = Bearers.plot(head);
		// over its mound, its back to its own stone
		ghost.snapTo(plot.getX() + 0.5, plot.getY(), plot.getZ() + 0.5, 0.0F, 0.0F);
		ghost.grave = g.head();
		ghost.realm = g.realm();
		ghost.hasGrave = true;
		ghost.setCustomName(g.name());
		level.addFreshEntity(ghost);
		STANDING.put(g.head(), ghost.getUUID());
		level.sendParticles(Fx.mote(COLOR, 1.0F), ghost.getX(), ghost.getY() + 1.0, ghost.getZ(), 12, 0.25, 0.6, 0.25, 0.01);
	}

	// ---- one ghost -----------------------------------------------------------------------------------------------------

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 16.0F, 1.0F));
		this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.setDeltaMovement(Vec3.ZERO);
		if (this.tickCount % CHECK_EVERY != 0) {
			return;
		}
		if (!level.isDarkOutside() || level.getNearestPlayer(this, LEAVE_RANGE) == null || this.hasGrave && !this.graveStands(level)) {
			this.fade(level);
		}
	}

	/** Its grave is still written down. */
	private boolean graveStands(ServerLevel level) {
		for (Bearers.Grave g : Factions.level(level.getServer()).getAttachedOrElse(Bearers.GRAVES, List.of())) {
			if (g.head() == this.grave) {
				return true;
			}
		}
		return false;
	}

	/** It thins out and is gone (until tomorrow night, unless it was laid to rest). */
	private void fade(ServerLevel level) {
		level.sendParticles(Fx.mote(COLOR, 1.0F), this.getX(), this.getY() + 1.0, this.getZ(), 16, 0.25, 0.7, 0.25, 0.02);
		if (this.hasGrave) {
			STANDING.remove(this.grave, this.getUUID());
		}
		this.discard();
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer) || !(this.level() instanceof ServerLevel level)) {
			return this.level().isClientSide() && hand == InteractionHand.MAIN_HAND ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!offering(stack)) {
			Speech.sayTo(level, this, serverPlayer, Component.translatable("speech.jaymoskingdoms.grave_ghost.idle." + this.getRandom().nextInt(IDLE_LINES)), COLOR);
			return InteractionResult.SUCCESS;
		}
		stack.consume(1, player);
		Speech.sayTo(level, this, serverPlayer, Component.translatable("speech.jaymoskingdoms.grave_ghost.thanks." + this.getRandom().nextInt(THANKS_LINES)),
			COLOR);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 0.7F);
		ExperienceOrb.award(level, this.position(), REST_XP);
		if (this.hasGrave) {
			ServerLevel home = Factions.level(level.getServer());
			List<Long> rested = new ArrayList<>(home.getAttachedOrElse(RESTED, List.of()));
			rested.add(this.grave);
			// (only graves still written down need remembering)
			List<Long> known = new ArrayList<>();
			for (Bearers.Grave g : home.getAttachedOrElse(Bearers.GRAVES, List.of())) {
				known.add(g.head());
			}
			rested.retainAll(known);
			home.setAttached(RESTED, List.copyOf(rested));
			Faction settlement = Factions.get(level.getServer(), this.realm);
			if (settlement != null) {
				Reputation.change(serverPlayer, settlement, REST_REP);
			}
		}
		this.fade(level);
		return InteractionResult.SUCCESS;
	}

	/** What lays it to rest: a candle, or any flower. */
	private static boolean offering(ItemStack stack) {
		return stack.is(ItemTags.CANDLES)
			|| stack.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().is(BlockTags.FLOWERS);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// (nothing touches it; only the void and commands unmake it)
		return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	public @Nullable String action() {
		return null;
	}

	@Override
	public int actionAge() {
		return 0;
	}
}
