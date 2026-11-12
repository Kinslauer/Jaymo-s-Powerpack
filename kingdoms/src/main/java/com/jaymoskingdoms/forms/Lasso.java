package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.Captives;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Parties;
import com.jaymoskingdoms.Temperaments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The lasso, a rope for anyone, beside the bolas: leather and string at the crafting grid. Use it to throw a loop
 * ({@link Loop}) that settles round the first living thing it meets, and that thing is held near you on the rope
 * ({@link #HOLD_RANGE} blocks, pulled back when it strays) until it breaks out. A stronger one breaks out sooner; a great one
 * (a boss, or {@value #GREAT_HEALTH} health and more) shrugs the loop off. While you hold something:
 * <ul>
 * <li>use the lasso with it near ({@link #WRAP_RANGE}) to wrap it up: bound, it cannot move or attack, and a humanoid is led
 * after you on the rope; it breaks out of a wrapping far later ({@link #WRAP_BREAK});</li>
 * <li>use it with the held one farther away to haul it a step closer;</li>
 * <li>sneak and use to let go.</li>
 * </ul>
 * A player is only held, never wrapped, unless they are downed, and fights the rope by mashing sideways and jump
 * ({@link #STRUGGLE_BITE}); fighting players must be allowed on the server, and party members are never roped. The rope is
 * drawn by every client from the synced {@link #HOLDER} attachment on the held one ({@code LassoRope}). The lasso itself stays in
 * your pack; lose it from the pack and the rope is lost too.
 */
public final class Lasso {
	// ---- tuning ----
	/** The loop's speed (blocks per tick), its fall, the most ticks it flies, and the wait between throws (ticks). */
	static final double LOOP_SPEED = 1.5, LOOP_GRAVITY = 0.03;
	static final int LOOP_FLIGHT = 24, THROW_COOLDOWN = 20;
	/** How far (blocks) a held one may stray before the rope pulls it back, how hard the pull is per block past that (and the most), and where the rope snaps. */
	static final double HOLD_RANGE = 6.0, PULL_PER_BLOCK = 0.06, PULL_MOST = 0.35, SNAP_RANGE = 18.0;
	/** A wrapped humanoid is led: it keeps this close (blocks), and is pulled this hard per block past it (and the most). */
	static final double LEAD_GAP = 2.5, LEAD_PER_BLOCK = 0.12, LEAD_MOST = 0.3;
	/** How close you must be to wrap one up (blocks), and how far a haul brings it in a step. */
	static final double WRAP_RANGE = 3.5, HAUL_STEP = 0.9;
	/** A mob's breakout: the ticks a hold lasts, and a wrapping, at 30 health; scaled by 30 over its health between these shares. */
	static final int HOLD_BREAK = 300, WRAP_BREAK = 900;
	static final double BREAK_HEALTH = 30.0, BREAK_MIN = 0.25, BREAK_MAX = 1.5;
	/** A player's breakout: the ticks a hold lasts, a downed player's wrapping, and what each struggle press takes off. */
	static final int PLAYER_HOLD_BREAK = 240, PLAYER_WRAP_BREAK = 600, STRUGGLE_BITE = 10;
	/** A mob this strong shrugs the loop off. */
	static final float GREAT_HEALTH = 100.0F;
	/** Ticks between checks that you still carry a lasso, and how long a wrapped mob's slowness is topped up for. */
	static final int PACK_CHECK = 20, BOUND_SLOW_TICKS = 12;

	/** On a held one: the entity id of whoever holds the rope. Synced to everyone (they draw the rope), not saved. */
	public static final AttachmentType<Integer> HOLDER = AttachmentRegistry.create(JaymosKingdoms.id("lasso_holder"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	public static EntityType<Loop> TYPE;
	public static Item ITEM;

	/** One rope: who holds it, whom it holds, and how long the hold has left. */
	private static final class Hold {
		final MinecraftServer server;
		final UUID holder, held;
		boolean wrapped;
		double left;
		double total;
		Input lastInput = Input.EMPTY;

		Hold(MinecraftServer server, UUID holder, UUID held, double total) {
			this.server = server;
			this.holder = holder;
			this.held = held;
			this.left = total;
			this.total = total;
		}
	}

	private static final Map<UUID, Hold> BY_HOLDER = new HashMap<>();
	private static final Map<UUID, Hold> BY_HELD = new HashMap<>();

	private Lasso() {
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("lasso_loop"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Loop>of(Loop::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.3F, 0.3F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("lasso"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new LassoItem(new Item.Properties().setId(itemKey).stacksTo(8)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.WIND_CHARGE, ITEM));
		ServerTickEvents.END_SERVER_TICK.register(Lasso::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> releaseAll());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BY_HOLDER.clear();
			BY_HELD.clear();
		});
	}

	// ---- the loop ---------------------------------------------------------------------------------------------------------

	/** The loop of rope in flight: it settles on the first living thing it meets, and is gone if it meets none. */
	public static final class Loop extends Projectile implements ItemSupplier {
		private int flight;

		public Loop(EntityType<? extends Loop> type, Level level) {
			super(type, level);
			this.noPhysics = true;
			this.setNoGravity(true);
		}

		@Override
		protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		}

		@Override
		public ItemStack getItem() {
			return new ItemStack(ITEM);
		}

		@Override
		public void tick() {
			super.tick();
			if (!(this.level() instanceof ServerLevel level)) {
				return;
			}
			Vec3 pos = this.position();
			if (++this.flight > LOOP_FLIGHT || !level.getFluidState(BlockPos.containing(pos)).isEmpty()) {
				this.discard();
				return;
			}
			Vec3 move = this.getDeltaMovement().add(0.0, -LOOP_GRAVITY, 0.0);
			Vec3 next = pos.add(move);
			Entity owner = this.getOwner();
			EntityHitResult struck = ProjectileUtil.getEntityHitResult(level, this, pos, next, new AABB(pos, next).inflate(0.5),
				e -> e != owner && e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), 0.3F);
			HitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (struck != null && struck.getEntity() instanceof LivingEntity target && owner instanceof ServerPlayer thrower) {
				catchOn(level, thrower, target);
				this.discard();
				return;
			}
			if (hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK) {
				this.discard();
				return;
			}
			this.setPos(next);
			this.setDeltaMovement(move);
			Fx.burst(level, next.x, next.y, next.z, 0xC4A56A, 0.5F, 1, 0.0, 0.0, 0.0);
		}

		@Override
		public boolean isPickable() {
			return false;
		}

		@Override
		public boolean shouldRenderAtSqrDistance(double distance) {
			return distance < 96.0 * 96.0;
		}
	}

	/** The lasso: use it to throw, haul, wrap, or (sneaking) let go. */
	static final class LassoItem extends Item {
		LassoItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer thrower)) {
				return InteractionResult.SUCCESS;
			}
			Hold hold = BY_HOLDER.get(thrower.getUUID());
			if (hold != null) {
				work(server, thrower, hold);
				return InteractionResult.SUCCESS;
			}
			Loop loop = new Loop(TYPE, server);
			loop.setOwner(thrower);
			loop.setPos(thrower.getX(), thrower.getEyeY() - 0.2, thrower.getZ());
			loop.setDeltaMovement(thrower.getLookAngle().scale(LOOP_SPEED).add(thrower.getDeltaMovement().multiply(1.0, 0.0, 1.0)));
			server.addFreshEntity(loop);
			server.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8F, 0.5F);
			thrower.getCooldowns().addCooldown(thrower.getItemInHand(hand), THROW_COOLDOWN);
			return InteractionResult.SUCCESS;
		}
	}

	// ---- catching ---------------------------------------------------------------------------------------------------------

	/** Whether the rope may take hold of {@code target} for {@code thrower}; says why not otherwise. */
	private static boolean mayHold(ServerLevel level, ServerPlayer thrower, LivingEntity target) {
		if (BY_HELD.containsKey(target.getUUID()) || BY_HOLDER.containsKey(thrower.getUUID()) || Captives.bound(target)) {
			return false;
		}
		if (target instanceof ServerPlayer other) {
			return level.isPvpAllowed() && !other.isCreative() && !other.isSpectator() && !Parties.allied(thrower, other);
		}
		return !(target instanceof Player);
	}

	private static boolean great(LivingEntity target) {
		return BossBars.boss(target) || target.getMaxHealth() >= GREAT_HEALTH;
	}

	private static void catchOn(ServerLevel level, ServerPlayer thrower, LivingEntity target) {
		if (!mayHold(level, thrower, target)) {
			return;
		}
		if (great(target)) {
			Fx.ring(level, target.position().add(0.0, 0.1, 0.0), 1.2F, 0xC4A56A);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
			thrower.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.shrugged", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		Hold hold = new Hold(level.getServer(), thrower.getUUID(), target.getUUID(), breakTicks(target, false));
		BY_HOLDER.put(thrower.getUUID(), hold);
		BY_HELD.put(target.getUUID(), hold);
		target.setAttached(HOLDER, thrower.getId());
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.0F);
		Fx.ring(level, target.position().add(0.0, 0.1, 0.0), 1.0F, 0xC4A56A);
		thrower.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.held", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
		if (target instanceof ServerPlayer other) {
			other.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.roped").withStyle(ChatFormatting.RED));
		}
	}

	/** How long the rope holds {@code target}: stronger ones break out sooner. */
	private static double breakTicks(LivingEntity target, boolean wrapped) {
		if (target instanceof Player) {
			return wrapped ? PLAYER_WRAP_BREAK : PLAYER_HOLD_BREAK;
		}
		double factor = Mth.clamp(BREAK_HEALTH / Math.max(1.0F, target.getMaxHealth()), BREAK_MIN, BREAK_MAX);
		return (wrapped ? WRAP_BREAK : HOLD_BREAK) * factor;
	}

	// ---- working the rope ----------------------------------------------------------------------------------------------

	/** Using the lasso while holding someone: sneak lets go; near, wraps them; far, hauls them in a step. */
	private static void work(ServerLevel level, ServerPlayer holder, Hold hold) {
		if (!(level.getEntity(hold.held) instanceof LivingEntity held)) {
			release(hold, null);
			return;
		}
		if (holder.isShiftKeyDown()) {
			release(hold, Component.translatable("message.jaymoskingdoms.lasso.let_go", held.getDisplayName()));
			return;
		}
		double dist = holder.distanceTo(held);
		if (hold.wrapped) {
			holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.already", held.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (dist > WRAP_RANGE) {
			Vec3 to = holder.position().subtract(held.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 1.0E-6) {
				Vec3 step = to.normalize().scale(HAUL_STEP * 0.5);
				held.setDeltaMovement(held.getDeltaMovement().add(step.x, 0.15, step.z));
				held.syncVelocity = true;
			}
			level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.6F, 1.3F);
			return;
		}
		if (held instanceof Player player && !Downed.isDowned(player)) {
			holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.player_up").withStyle(ChatFormatting.GRAY));
			return;
		}
		hold.wrapped = true;
		hold.total = breakTicks(held, true);
		hold.left = hold.total;
		level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.7F);
		Fx.blast(level, held.position().add(0.0, held.getBbHeight() * 0.5, 0.0), 0.8F, 0xC4A56A);
		holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.wrapped", held.getDisplayName()).withStyle(ChatFormatting.GOLD));
		if (held instanceof Mob mob) {
			mob.setTarget(null);
		}
	}

	/** The rope comes off: {@code why} is said to the holder. */
	private static void release(Hold hold, @Nullable Component why) {
		BY_HOLDER.remove(hold.holder, hold);
		BY_HELD.remove(hold.held, hold);
		MinecraftServer server = hold.server;
		for (ServerLevel level : server.getAllLevels()) {
			Entity held = level.getEntity(hold.held);
			if (held != null) {
				held.removeAttached(HOLDER);
			}
		}
		ServerPlayer holder = server.getPlayerList().getPlayer(hold.holder);
		if (holder != null && why != null) {
			holder.sendOverlayMessage(why.copy().withStyle(ChatFormatting.GRAY));
		}
	}

	private static void releaseAll() {
		for (Hold hold : new java.util.ArrayList<>(BY_HOLDER.values())) {
			release(hold, null);
		}
	}

	private static boolean carries(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(ITEM)) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (BY_HOLDER.isEmpty()) {
			return;
		}
		for (Hold hold : new java.util.ArrayList<>(BY_HOLDER.values())) {
			ServerPlayer holder = server.getPlayerList().getPlayer(hold.holder);
			if (holder == null || !holder.isAlive() || !(holder.level() instanceof ServerLevel level) || Downed.isDowned(holder)) {
				release(hold, null);
				continue;
			}
			if (!(level.getEntity(hold.held) instanceof LivingEntity held) || !held.isAlive() || held.level() != level) {
				release(hold, Component.translatable("message.jaymoskingdoms.lasso.lost"));
				continue;
			}
			double dist = holder.distanceTo(held);
			if (dist > SNAP_RANGE || (holder.tickCount % PACK_CHECK == 0 && !carries(holder))) {
				level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
				release(hold, Component.translatable("message.jaymoskingdoms.lasso.snapped"));
				continue;
			}
			// (the rope pulls: back to within reach, or, for a wrapped person, along behind you)
			Vec3 to = holder.position().subtract(held.position()).multiply(1.0, 0.0, 1.0);
			boolean led = hold.wrapped && (held instanceof Player || Temperaments.humanoid(held));
			double over = led ? dist - LEAD_GAP : hold.wrapped ? 0.0 : dist - HOLD_RANGE;
			if (over > 0.0 && to.lengthSqr() > 1.0E-6) {
				double pull = Math.min(led ? LEAD_MOST : PULL_MOST, over * (led ? LEAD_PER_BLOCK : PULL_PER_BLOCK));
				Vec3 step = to.normalize().scale(pull);
				held.setDeltaMovement(held.getDeltaMovement().add(step.x, 0.0, step.z));
				held.syncVelocity = true;
			}
			if (hold.wrapped && held instanceof Mob mob) {
				// (bound: it can't move or strike)
				mob.setTarget(null);
				mob.getNavigation().stop();
				mob.setAggressive(false);
				held.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BOUND_SLOW_TICKS, 5, false, false));
			}
			hold.left -= 1.0;
			if (held instanceof ServerPlayer player) {
				Input now = player.getLastClientInput();
				if (now.left() && !hold.lastInput.left() || now.right() && !hold.lastInput.right() || now.jump() && !hold.lastInput.jump()) {
					hold.left -= STRUGGLE_BITE;
				}
				hold.lastInput = now;
			}
			if (held.tickCount % 20 == 0) {
				double share = hold.left / hold.total;
				if (share < 0.3) {
					Fx.burst(level, held.getX(), held.getY(0.5), held.getZ(), 0xC4A56A, 0.6F, 3, 0.3, 0.3, 0.3);
					level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.3F, 1.6F);
				}
			}
			if (hold.left <= 0.0) {
				level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
				Fx.blast(level, held.position().add(0.0, held.getBbHeight() * 0.5, 0.0), 0.8F, 0xC4A56A);
				release(hold, Component.translatable("message.jaymoskingdoms.lasso.broke_out", held.getDisplayName()));
			}
		}
	}
}
