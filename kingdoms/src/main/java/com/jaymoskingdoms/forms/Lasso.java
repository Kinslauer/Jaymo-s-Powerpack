package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.Captives;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Parties;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Temperaments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The lasso, a rope for anyone, beside the bolas: leather and string at the crafting grid. Andy: "the lasso should animate an
 * overhead rampup for speed before you can throw, then you are tethered to the target if you connect, dragging them to you if
 * you back up, and winding the lasso if you move forward, like red dead redemption 2. when you get to the target you can
 * hogtie them if they are humanoid, and pick them up if you are strong enough. otherwise you grab and drag it. they can
 * struggle out at any time. by spamming shift (controllably) made harder by if you hit them, bare fist, dealing no damage
 * just stalling the breakout."
 * <ul>
 * <li><b>The throw.</b> Hold use: the loop is whirled over your head, faster and wider the longer you hold
 * ({@link #SPIN_FULL}); let go once it has come up to speed ({@link #SPIN_MIN}) and the loop ({@link Loop}) flies where you
 * look, faster and flatter for a longer spin. Let go sooner and it only flops. It settles round the first living thing it
 * meets; the rope runs out at {@link #ROPE_LENGTH}.</li>
 * <li><b>The tether.</b> Caught, it is on the rope at the length paid out. Walk toward it and the rope is wound in (it never
 * pays out again); back away, or let it run, and the taut rope drags it after you. Something much bigger than a person drags
 * back ({@link #HEAVY_SHARE}). Using the lasso with it farther than reach hauls it a step closer; sneak and use lets go.</li>
 * <li><b>At reach</b> ({@link #TIE_REACH}): a humanoid is hogtied by holding use ({@link #TIE_TICKS}). Hogtied ({@link Tie}),
 * it lies on its side, wrists and ankles roped behind it, and can't walk, strike or use anything. The rope stays on it, and
 * walking off drags it along the ground; let the rope go and it stays tied. A tied body is lifted like someone downed (Powers'
 * Carry: sneak and hold use on it), if you're strong enough: its armour against your brawn, a piece coming off when it's too
 * heavy. Anything else is grabbed with a tap of use: pinned on a short rope ({@link #GRAB_GAP}) and dragged after you.</li>
 * <li><b>Breaking out.</b> Anything held, grabbed or tied can struggle out at any time. A mob works at it all along, a stronger
 * one sooner; a great one (a boss, or {@value #GREAT_HEALTH} health and more) shrugs the loop off. A player mashes sneak, each
 * press counting once in {@link #PRESS_GAP} ticks however fast it's mashed, {@link #PLAYER_HOLD_PRESSES} on the rope and
 * {@link #PLAYER_TIE_PRESSES} tied. A bare-fisted punch on anyone roped deals no damage: it knocks their struggle back
 * ({@link #STALL_SHARE}) and stalls it for {@link #STALL_TICKS}.</li>
 * </ul>
 * Fighting players must be allowed on the server, and party members are never roped. The rope is drawn by every client from
 * the synced {@link #HOLDER} attachment on the held one ({@code LassoRope}), the spin from the holder's use of the lasso, and the
 * hogtie from {@link #TIED} ({@code LassoPose}). The lasso itself stays in your pack; lose it from the pack and the rope is lost
 * too. Nothing here is saved: a rope or a hogtie ends with the server.
 */
public final class Lasso {
	// ---- tuning ----
	/** The spin: ticks of use before a throw can go, and before the loop is at full speed. */
	public static final int SPIN_MIN = 10, SPIN_FULL = 36;
	/** The loop's speed (blocks per tick) after the shortest and the fullest spin, its fall, and the most ticks it flies. */
	static final double LOOP_SPEED_MIN = 0.85, LOOP_SPEED_MAX = 1.7, LOOP_GRAVITY = 0.03;
	static final int LOOP_FLIGHT = 30, THROW_COOLDOWN = 20;
	/** How much rope there is (blocks): the loop goes no farther, and a held one is never let further out. */
	static final double ROPE_LENGTH = 20.0;
	/** The rope winds in no shorter than this (blocks); past its length it pulls this hard per block over (and the most). */
	static final double ROPE_LEAST = 1.5, PULL_PER_BLOCK = 0.3, PULL_MOST = 0.5;
	/** Strained this far past its length (blocks) by something it can't hold, the rope snaps. */
	static final double SNAP_OVER = 6.0;
	/** Something bigger than a person (its box's volume over a person's) pulls the holder too: this share per size over, at most this. */
	static final double HEAVY_SHARE = 0.3, HEAVY_MOST = 0.6, PERSON_VOLUME = 0.6 * 0.6 * 1.8;
	/** How close you must be to hogtie or grab (blocks), how long use is held to hogtie (ticks), and the haul a press gives (blocks). */
	static final double TIE_REACH = 3.0, HAUL_STEP = 0.9;
	static final int TIE_TICKS = 30;
	/** A grabbed one is kept on this much rope (blocks); a tied one is dragged on this much. */
	static final double GRAB_GAP = 1.6, TIED_GAP = 2.0;
	/** Dragging something grabbed or tied, the holder walks this much slower (share of their speed). */
	static final double DRAG_WEIGHT = 0.2;
	/** A mob's breakout (ticks) at 30 health: on the rope, grabbed, tied; scaled by 30 over its health between these shares. */
	static final int HOLD_BREAK = 300, GRAB_BREAK = 500, TIE_BREAK = 900;
	static final double BREAK_HEALTH = 30.0, BREAK_MIN = 0.25, BREAK_MAX = 1.5;
	/** A player's breakout: sneak presses on the rope, and tied; a press counts once in this many ticks at most. */
	static final int PLAYER_HOLD_PRESSES = 8, PLAYER_TIE_PRESSES = 20, PRESS_GAP = 4;
	/** A bare-fisted punch on someone roped: their struggle knocked back this share of the whole, and stalled this long (ticks). */
	static final double STALL_SHARE = 0.2;
	static final int STALL_TICKS = 30;
	/** A mob this strong shrugs the loop off. */
	static final float GREAT_HEALTH = 100.0F;
	/** Ticks between checks that you still carry a lasso, and how long a pinned mob's slowness is topped up for. */
	static final int PACK_CHECK = 20, BOUND_SLOW_TICKS = 12;

	/** On a held one: the entity id of whoever holds the rope. Synced to everyone (they draw the rope), not saved. */
	public static final AttachmentType<Integer> HOLDER = AttachmentRegistry.create(JaymosKingdoms.id("lasso_holder"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On a holder: the entity id of what's on their rope. Synced to everyone (a held rope isn't spun), not saved. */
	public static final AttachmentType<Integer> HOLDING = AttachmentRegistry.create(JaymosKingdoms.id("lasso_holding"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On a hogtied one: how far their struggle has got (0-100; -1 while they're downed). Synced to everyone (the tied pose, and its squirming), not saved. */
	public static final AttachmentType<Integer> TIED = AttachmentRegistry.create(JaymosKingdoms.id("lasso_tied"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** The modifiers a pinned or tied body (no walking, no jumping) and a dragging holder carry. */
	static final Identifier PINNED = JaymosKingdoms.id("lasso_pinned"), DRAGGING = JaymosKingdoms.id("lasso_dragging");

	/** Runs ahead of the other use-on-entity listeners. */
	static final Identifier EARLY = JaymosKingdoms.id("lasso_early");

	public static EntityType<Loop> TYPE;
	public static Item ITEM;

	/** A struggle against a rope: how much is left of it, of how much, and the sneak presses and punches that work on it. */
	private static class Struggle {
		final MinecraftServer server;
		double left, total;
		long stalledUntil, lastStall, lastPress = -PRESS_GAP;
		Input lastInput = Input.EMPTY;

		Struggle(MinecraftServer server, double total) {
			this.server = server;
			this.left = total;
			this.total = total;
		}

		void restart(double total) {
			this.left = total;
			this.total = total;
		}

		double share() {
			return Mth.clamp(1.0 - this.left / this.total, 0.0, 1.0);
		}
	}

	/** What a rope has hold of: the length paid out, and whether it's only round them, grabbed short, or on a tied body. */
	private static final class Hold extends Struggle {
		static final int TETHER = 0, GRAB = 1, TIED = 2;
		final UUID holder, held;
		double length;
		int mode = TETHER;
		/** When use began to be held near them to tie them (game time), or -1. */
		long tying = -1;

		Hold(MinecraftServer server, UUID holder, UUID held, double total, double length) {
			super(server, total);
			this.holder = holder;
			this.held = held;
			this.length = length;
		}
	}

	/** A hogtie on someone: it lasts until they struggle out of it, whoever holds the rope (if anyone). */
	private static final class Tie extends Struggle {
		final UUID tied;
		int shown = -1;

		Tie(MinecraftServer server, UUID tied, double total) {
			super(server, total);
			this.tied = tied;
		}
	}

	private static final Map<UUID, Hold> BY_HOLDER = new HashMap<>();
	private static final Map<UUID, Hold> BY_HELD = new HashMap<>();
	private static final Map<UUID, Tie> TIES = new HashMap<>();

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
		// (whirling it, or tying someone, you walk at a fair pace, though you can't run)
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new LassoItem(new Item.Properties().setId(itemKey).stacksTo(8)
			.component(DataComponents.USE_EFFECTS, new UseEffects(false, true, 0.6F))));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.WIND_CHARGE, ITEM));
		ServerTickEvents.END_SERVER_TICK.register(Lasso::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> releaseAll());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BY_HOLDER.clear();
			BY_HELD.clear();
			TIES.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Hold hold = BY_HOLDER.get(handler.getPlayer().getUUID());
			if (hold != null) {
				release(hold, null);
			}
			Tie tie = TIES.get(handler.getPlayer().getUUID());
			if (tie != null) {
				untie(tie);
			}
		});
		// a bare-fisted punch on anyone roped: no damage, their struggle knocked back; and the tied strike at nothing
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hogtied(player)) {
				return InteractionResult.FAIL;
			}
			if (level.isClientSide()) {
				return InteractionResult.PASS;
			}
			return entity instanceof LivingEntity target && player.getMainHandItem().isEmpty() && punch((ServerLevel)level, player, target)
				? InteractionResult.FAIL : InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> hogtied(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> hogtied(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> hogtied(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		// use on the one on your rope works the rope, before anything else they'd do with a click (a villager's talk)
		UseEntityCallback.EVENT.addPhaseOrdering(EARLY, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(EARLY, Lasso::useOnHeld);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> hogtied(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		// lifted in someone's arms: the rope comes off (the hogtie stays)
		PowersCompat.listen("com.jaymospowers.core.Carry", "PICKED_UP_LISTENERS", (ServerPlayer carrier, Entity body) -> drop(body, false));
		// a tied body is lifted as someone downed is, and a tied player can't jump out of the arms (Powers' Carry)
		PowersCompat.boundCheck(Lasso::hogtied);
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
			Entity owner = this.getOwner();
			if (++this.flight > LOOP_FLIGHT || !level.getFluidState(BlockPos.containing(pos)).isEmpty()
				|| owner == null || owner.level() != level || owner.distanceToSqr(pos) > ROPE_LENGTH * ROPE_LENGTH) {
				// (fallen short, in water, or the rope has run out)
				this.discard();
				return;
			}
			Vec3 move = this.getDeltaMovement().add(0.0, -LOOP_GRAVITY, 0.0);
			Vec3 next = pos.add(move);
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

	/** The lasso: hold use to whirl it and let go to throw; with something on the rope, use hauls, ties or grabs; sneak lets go. */
	static final class LassoItem extends Item {
		LassoItem(Properties properties) {
			super(properties);
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity entity) {
			return 72000;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.NONE;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			boolean holding = player.getAttached(HOLDING) != null;
			if (holding && player.isShiftKeyDown()) {
				if (level instanceof ServerLevel && player instanceof ServerPlayer holder) {
					Hold hold = BY_HOLDER.get(holder.getUUID());
					if (hold != null && holder.level().getEntity(hold.held) instanceof LivingEntity held) {
						release(hold, Component.translatable("message.jaymoskingdoms.lasso.let_go", held.getDisplayName()));
					} else if (hold != null) {
						release(hold, null);
					}
				}
				return InteractionResult.SUCCESS;
			}
			player.startUsingItem(hand);
			if (level instanceof ServerLevel server && player instanceof ServerPlayer holder) {
				Hold hold = BY_HOLDER.get(holder.getUUID());
				if (hold != null) {
					work(server, holder, hold);
				}
			}
			return InteractionResult.CONSUME;
		}

		@Override
		public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
			if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) {
				return;
			}
			Hold hold = BY_HOLDER.get(player.getUUID());
			if (hold != null) {
				tying(server, player, hold);
				return;
			}
			// the whirl's whoosh, once a turn, the turns coming quicker as it speeds up
			int spun = this.getUseDuration(stack, entity) - ticksRemaining;
			int turn = Math.max(3, Math.round(Mth.lerp(Math.min(1.0F, spun / (float)SPIN_FULL), 9.0F, 4.0F)));
			if (spun % turn == 0) {
				float speed = Math.min(1.0F, spun / (float)SPIN_FULL);
				server.playSound(null, player.getX(), player.getEyeY() + 0.5, player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
					0.12F + 0.2F * speed, 0.6F + 0.7F * speed);
			}
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int ticksRemaining) {
			if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer thrower)) {
				return true;
			}
			Hold hold = BY_HOLDER.get(thrower.getUUID());
			if (hold != null) {
				// (let go of use before the knots were done)
				hold.tying = -1;
				return true;
			}
			int spun = this.getUseDuration(stack, entity) - ticksRemaining;
			if (spun < SPIN_MIN) {
				// not up to speed: the loop only flops
				server.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.5F, 1.3F);
				return true;
			}
			double speed = Mth.lerp(Math.min(1.0, (spun - SPIN_MIN) / (double)(SPIN_FULL - SPIN_MIN)), LOOP_SPEED_MIN, LOOP_SPEED_MAX);
			Loop loop = new Loop(TYPE, server);
			loop.setOwner(thrower);
			// (from where it was whirling, over the head)
			loop.setPos(thrower.getX(), thrower.getEyeY() + 0.4, thrower.getZ());
			loop.setDeltaMovement(thrower.getLookAngle().scale(speed).add(thrower.getDeltaMovement().multiply(1.0, 0.0, 1.0)));
			server.addFreshEntity(loop);
			server.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8F, 0.5F);
			thrower.getCooldowns().addCooldown(stack, THROW_COOLDOWN);
			return true;
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

	/** Whether {@code entity} is held fast on a rope: hogtied, or grabbed (a Blood Sorcerer's rite may take such a captive). */
	public static boolean wrapped(Entity entity) {
		if (hogtied(entity)) {
			return true;
		}
		Hold hold = BY_HELD.get(entity.getUUID());
		return hold != null && hold.mode == Hold.GRAB;
	}

	/** Whether {@code entity} is hogtied (either side: the synced {@link #TIED}). */
	public static boolean hogtied(Entity entity) {
		return entity.getAttached(TIED) != null;
	}

	/** Whether {@code entity} is pinned by a rope (hogtied or grabbed) and can't strike at anyone (server). */
	public static boolean pinned(Entity entity) {
		return !entity.level().isClientSide() && wrapped(entity);
	}

	/** Takes the rope (and any hogtie) off {@code entity} without a word (it is going elsewhere). */
	public static void drop(Entity entity) {
		drop(entity, true);
	}

	private static void drop(Entity entity, boolean andTie) {
		Hold hold = BY_HELD.get(entity.getUUID());
		if (hold != null) {
			release(hold, null);
		}
		Tie tie = TIES.get(entity.getUUID());
		if (andTie && tie != null) {
			untie(tie);
		}
	}

	private static boolean great(LivingEntity target) {
		return BossBars.boss(target) || target.getMaxHealth() >= GREAT_HEALTH;
	}

	/** Built like a person, to be hogtied: a player, the folk, illagers and the like, the walking dead, anything Powers trips as a person. */
	static boolean humanoid(LivingEntity entity) {
		return entity instanceof Player || Temperaments.humanoid(entity) || entity instanceof Zombie || entity instanceof AbstractSkeleton
			|| PowersCompat.trippable(entity);
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
		Hold hold = new Hold(level.getServer(), thrower.getUUID(), target.getUUID(), breakTotal(target, Hold.TETHER),
			Mth.clamp(thrower.distanceTo(target), ROPE_LEAST, ROPE_LENGTH));
		hold.mode = hogtied(target) ? Hold.TIED : Hold.TETHER;
		BY_HOLDER.put(thrower.getUUID(), hold);
		BY_HELD.put(target.getUUID(), hold);
		target.setAttached(HOLDER, thrower.getId());
		thrower.setAttached(HOLDING, target.getId());
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.0F);
		Fx.ring(level, target.position().add(0.0, 0.1, 0.0), 1.0F, 0xC4A56A);
		thrower.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.held", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
		if (target instanceof ServerPlayer other) {
			other.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.roped").withStyle(ChatFormatting.RED));
		}
	}

	/** How much struggle it takes {@code target} to get out of a rope of {@code mode}: ticks for a mob (stronger ones sooner), presses for a player. */
	private static double breakTotal(LivingEntity target, int mode) {
		if (target instanceof Player) {
			return mode == Hold.TIED ? PLAYER_TIE_PRESSES : PLAYER_HOLD_PRESSES;
		}
		double factor = Mth.clamp(BREAK_HEALTH / Math.max(1.0F, target.getMaxHealth()), BREAK_MIN, BREAK_MAX);
		return (mode == Hold.TIED ? TIE_BREAK : mode == Hold.GRAB ? GRAB_BREAK : HOLD_BREAK) * factor;
	}

	// ---- working the rope ----------------------------------------------------------------------------------------------

	/**
	 * Use (not sneaking) on the one on your own rope with the lasso in hand: the lasso's use, as if at the air (hauled, tied,
	 * grabbed), not whatever a click on them does otherwise. Sneak and use on them is left to the rest (lifting a tied body).
	 */
	private static InteractionResult useOnHeld(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		Integer holding = player.getAttached(HOLDING);
		if (holding == null || holding != entity.getId() || player.isShiftKeyDown() || !player.getItemInHand(hand).is(ITEM) || player.isUsingItem()) {
			return InteractionResult.PASS;
		}
		player.startUsingItem(hand);
		if (level instanceof ServerLevel server && player instanceof ServerPlayer holder) {
			Hold hold = BY_HOLDER.get(holder.getUUID());
			if (hold != null) {
				work(server, holder, hold);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Using the lasso with someone on it: far, a haul; near, a humanoid's hogtie begins (use held) and anything else is grabbed. */
	private static void work(ServerLevel level, ServerPlayer holder, Hold hold) {
		if (!(level.getEntity(hold.held) instanceof LivingEntity held)) {
			release(hold, null);
			return;
		}
		double dist = holder.distanceTo(held);
		if (dist > TIE_REACH) {
			Vec3 to = holder.position().subtract(held.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 1.0E-6) {
				Vec3 step = to.normalize().scale(HAUL_STEP * 0.5);
				held.setDeltaMovement(held.getDeltaMovement().add(step.x, hold.mode == Hold.TIED ? 0.05 : 0.15, step.z));
				held.syncVelocity = true;
			}
			hold.length = Math.max(ROPE_LEAST, Math.min(hold.length, dist - HAUL_STEP));
			level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.6F, 1.3F);
			return;
		}
		if (hold.mode != Hold.TETHER) {
			return;
		}
		if (humanoid(held)) {
			// the knots: use held a moment at their side (tying)
			hold.tying = level.getGameTime();
			level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
			return;
		}
		// anything else is grabbed: pinned on a short rope and dragged
		hold.mode = Hold.GRAB;
		hold.length = Math.min(hold.length, GRAB_GAP);
		hold.restart(breakTotal(held, Hold.GRAB));
		level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.7F);
		Fx.blast(level, held.position().add(0.0, held.getBbHeight() * 0.5, 0.0), 0.8F, 0xC4A56A);
		holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.grabbed", held.getDisplayName()).withStyle(ChatFormatting.GOLD));
		if (held instanceof Mob mob) {
			mob.setTarget(null);
		}
	}

	/** Use held at a humanoid's side: the knots are tied a little more, and done, they're hogtied. */
	private static void tying(ServerLevel level, ServerPlayer holder, Hold hold) {
		if (hold.tying < 0 || hold.mode != Hold.TETHER) {
			return;
		}
		if (!(level.getEntity(hold.held) instanceof LivingEntity held) || holder.distanceTo(held) > TIE_REACH + 0.5) {
			hold.tying = -1;
			return;
		}
		long tied = level.getGameTime() - hold.tying;
		if (tied < TIE_TICKS) {
			if (tied % 6 == 0) {
				level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.5F, 1.4F + level.getRandom().nextFloat() * 0.3F);
			}
			int bar = 10, filled = Math.round(bar * tied / (float)TIE_TICKS);
			holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.tying", held.getDisplayName(),
				"|".repeat(filled) + ".".repeat(bar - filled)).withStyle(ChatFormatting.GRAY));
			return;
		}
		hold.tying = -1;
		holder.stopUsingItem();
		hogtie(level, held);
		hold.mode = Hold.TIED;
		hold.length = Math.min(hold.length, TIED_GAP);
		holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.hogtied", held.getDisplayName()).withStyle(ChatFormatting.GOLD));
	}

	/** {@code body} is tied hand and foot: it lies on its side and can't walk, strike or use anything until it struggles free. */
	private static void hogtie(ServerLevel level, LivingEntity body) {
		Tie tie = new Tie(level.getServer(), body.getUUID(), breakTotal(body, Hold.TIED));
		TIES.put(body.getUUID(), tie);
		body.setAttached(TIED, 0);
		pin(body, true);
		if (body instanceof Player player) {
			player.stopUsingItem();
		}
		if (body instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.you_hogtied").withStyle(ChatFormatting.RED));
		}
		if (body instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.6F);
		Fx.blast(level, body.position().add(0.0, body.getBbHeight() * 0.3, 0.0), 0.8F, 0xC4A56A);
	}

	/** The hogtie comes off. */
	private static void untie(Tie tie) {
		TIES.remove(tie.tied, tie);
		for (ServerLevel level : tie.server.getAllLevels()) {
			if (level.getEntity(tie.tied) instanceof LivingEntity body) {
				body.removeAttached(TIED);
				pin(body, false);
				// (out of the arms of whoever had them)
				PowersCompat.dropFromArms(body);
			}
		}
		Hold hold = BY_HELD.get(tie.tied);
		if (hold != null && hold.mode == Hold.TIED) {
			// (still on the rope, but only round them now)
			hold.mode = Hold.TETHER;
		}
	}

	/** No walking or jumping for a pinned body (or that again). */
	private static void pin(LivingEntity body, boolean on) {
		AttributeInstance speed = body.getAttribute(Attributes.MOVEMENT_SPEED), jump = body.getAttribute(Attributes.JUMP_STRENGTH);
		for (AttributeInstance attribute : new AttributeInstance[] {speed, jump}) {
			if (attribute == null) {
				continue;
			}
			if (on) {
				if (!attribute.hasModifier(PINNED)) {
					attribute.addOrUpdateTransientModifier(new AttributeModifier(PINNED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			} else {
				attribute.removeModifier(PINNED);
			}
		}
	}

	/** A holder dragging something grabbed or tied walks slower (or not, {@code on} false). */
	private static void weigh(ServerPlayer holder, boolean on) {
		AttributeInstance speed = holder.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (on == speed.hasModifier(DRAGGING)) {
			return;
		}
		if (on) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(DRAGGING, -DRAG_WEIGHT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			speed.removeModifier(DRAGGING);
		}
	}

	/**
	 * A bare-fisted punch from {@code by} on {@code target}: if it's roped (held, grabbed or tied), no damage, and its struggle is
	 * knocked back and stalled. True if it was.
	 */
	private static boolean punch(ServerLevel level, Player by, LivingEntity target) {
		Tie tie = TIES.get(target.getUUID());
		Hold hold = BY_HELD.get(target.getUUID());
		Struggle struggle = tie != null ? tie : hold;
		if (struggle == null || by == target) {
			return false;
		}
		long now = level.getGameTime();
		if (now - struggle.lastStall >= 10) {
			struggle.left = Math.min(struggle.total, struggle.left + struggle.total * STALL_SHARE);
			struggle.lastStall = now;
		}
		struggle.stalledUntil = now + STALL_TICKS;
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.9F, 0.8F);
		Fx.burst(level, target.getX(), target.getY(0.6), target.getZ(), 0xC4A56A, 0.5F, 2, 0.2, 0.2, 0.2);
		if (target instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.stalled").withStyle(ChatFormatting.RED));
		}
		return true;
	}

	/** The rope comes off: {@code why} is said to the holder. A hogtie stays on. */
	private static void release(Hold hold, @Nullable Component why) {
		BY_HOLDER.remove(hold.holder, hold);
		BY_HELD.remove(hold.held, hold);
		MinecraftServer server = hold.server;
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(hold.held) instanceof LivingEntity held) {
				held.removeAttached(HOLDER);
				if (hold.mode == Hold.GRAB) {
					pin(held, false);
				}
			}
		}
		ServerPlayer holder = server.getPlayerList().getPlayer(hold.holder);
		if (holder != null) {
			holder.removeAttached(HOLDING);
			weigh(holder, false);
			if (why != null) {
				holder.sendOverlayMessage(why.copy().withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static void releaseAll() {
		for (Hold hold : new ArrayList<>(BY_HOLDER.values())) {
			release(hold, null);
		}
		for (Tie tie : new ArrayList<>(TIES.values())) {
			untie(tie);
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

	/**
	 * One tick of a struggle: a mob works at it all along; a player's sneak presses count, one in {@link #PRESS_GAP} ticks at
	 * most. Nothing counts while a punch has it stalled. True when they're out.
	 */
	private static boolean struggle(Struggle struggle, LivingEntity body, ServerLevel level) {
		long now = level.getGameTime();
		boolean stalled = now < struggle.stalledUntil;
		if (body instanceof ServerPlayer player) {
			Input input = player.getLastClientInput();
			if (input.shift() && !struggle.lastInput.shift() && !stalled && now - struggle.lastPress >= PRESS_GAP) {
				struggle.left -= 1.0;
				struggle.lastPress = now;
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.4F, 0.7F
					+ level.getRandom().nextFloat() * 0.3F);
			}
			struggle.lastInput = input;
			if (now % 5 == 0 || struggle.lastPress == now) {
				int bar = 10, filled = (int)Math.round(bar * struggle.share());
				player.sendOverlayMessage(Component.translatable(stalled ? "message.jaymoskingdoms.lasso.struggle_stalled" : "message.jaymoskingdoms.lasso.struggle",
					"|".repeat(filled) + ".".repeat(bar - filled)).withStyle(stalled ? ChatFormatting.RED : ChatFormatting.GRAY));
			}
		} else if (!stalled) {
			struggle.left -= 1.0;
		}
		if (body.tickCount % 20 == 0 && struggle.share() > 0.7) {
			Fx.burst(level, body.getX(), body.getY(0.5), body.getZ(), 0xC4A56A, 0.6F, 3, 0.3, 0.3, 0.3);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.3F, 1.6F);
		}
		return struggle.left <= 0.0;
	}

	/** A body that can't move or strike: its way and its aim forgotten each tick. */
	private static void hush(LivingEntity body) {
		if (body instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
			mob.setAggressive(false);
			body.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BOUND_SLOW_TICKS, 5, false, false));
		}
		if (body.isUsingItem()) {
			body.stopUsingItem();
		}
	}

	private static void tick(MinecraftServer server) {
		if (!TIES.isEmpty()) {
			ties(server);
		}
		if (BY_HOLDER.isEmpty()) {
			return;
		}
		for (Hold hold : new ArrayList<>(BY_HOLDER.values())) {
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
			if (dist > hold.length + SNAP_OVER || dist > ROPE_LENGTH + SNAP_OVER || (holder.tickCount % PACK_CHECK == 0 && !carries(holder))) {
				level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
				release(hold, Component.translatable("message.jaymoskingdoms.lasso.snapped"));
				continue;
			}
			weigh(holder, hold.mode != Hold.TETHER);
			if (PowersCompat.carried(held)) {
				// (in someone's arms: the rope is slack)
				hold.length = Math.max(ROPE_LEAST, Math.min(ROPE_LENGTH, dist));
				continue;
			}
			// walking in winds the rope up; it never pays out again
			if (dist < hold.length) {
				hold.length = Math.max(hold.mode == Hold.TETHER ? ROPE_LEAST : Math.min(ROPE_LEAST, hold.length), dist);
			}
			if (hold.mode == Hold.GRAB) {
				hush(held);
				pin(held, true);
			}
			// past its length it's taut: whatever's on it is dragged after you (and something big drags back)
			double over = dist - hold.length;
			Vec3 to = holder.position().subtract(held.position());
			Vec3 flat = to.multiply(1.0, 0.0, 1.0);
			if (over > 0.0 && flat.lengthSqr() > 1.0E-6) {
				double pull = Math.min(PULL_MOST, over * PULL_PER_BLOCK);
				double size = held.getBbWidth() * held.getBbWidth() * held.getBbHeight() / PERSON_VOLUME;
				double back = size > 1.0 && hold.mode == Hold.TETHER ? Math.min(HEAVY_MOST, (size - 1.0) * HEAVY_SHARE) : 0.0;
				Vec3 step = flat.normalize().scale(pull);
				// (a body dragged lying stays down on the ground; one on its feet is hauled up a step when it must climb)
				double lift = hold.mode != Hold.TIED && to.y > 0.5 && held.onGround() ? 0.2 : 0.0;
				held.setDeltaMovement(held.getDeltaMovement().add(step.x * (1.0 - back), lift, step.z * (1.0 - back)));
				held.syncVelocity = true;
				if (back > 0.0) {
					holder.setDeltaMovement(holder.getDeltaMovement().subtract(step.x * back, 0.0, step.z * back));
					holder.syncVelocity = true;
				}
				if (hold.mode == Hold.TIED && held.tickCount % 8 == 0 && held.onGround()) {
					level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, 0.4F, 0.6F);
				}
			}
			if (hold.mode == Hold.TIED) {
				// (the tie's own struggle is ticked with the tie)
				continue;
			}
			if (struggle(hold, held, level)) {
				level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
				Fx.blast(level, held.position().add(0.0, held.getBbHeight() * 0.5, 0.0), 0.8F, 0xC4A56A);
				release(hold, Component.translatable("message.jaymoskingdoms.lasso.broke_out", held.getDisplayName()));
			}
		}
	}

	/** Each hogtie: the tied lie still (or are dragged), and struggle; out of it, they're free. */
	private static void ties(MinecraftServer server) {
		for (Tie tie : new ArrayList<>(TIES.values())) {
			LivingEntity body = null;
			ServerLevel at = null;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(tie.tied) instanceof LivingEntity found) {
					body = found;
					at = level;
					break;
				}
			}
			if (body == null || !body.isAlive() || body instanceof Player player && (player.isCreative() || player.isSpectator())) {
				TIES.remove(tie.tied, tie);
				if (body != null) {
					body.removeAttached(TIED);
					pin(body, false);
				}
				continue;
			}
			hush(body);
			pin(body, true);
			if (!PowersCompat.carried(body) && !BY_HELD.containsKey(tie.tied)) {
				// (no rope on them: they lie where they are)
				Vec3 v = body.getDeltaMovement();
				body.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
			}
			boolean out = struggle(tie, body, at);
			// (down, they lie as the downed do: -1 shows no hogtie pose, though the knots stay on)
			int shown = Downed.isDowned(body) ? -1 : (int)Math.round(tie.share() * 100.0);
			if (shown != tie.shown && (Math.abs(shown - tie.shown) >= 5 || shown < 0 || tie.shown < 0 || body.tickCount % 10 == 0)) {
				tie.shown = shown;
				body.setAttached(TIED, shown);
			}
			if (out) {
				at.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
				Fx.blast(at, body.position().add(0.0, body.getBbHeight() * 0.3, 0.0), 0.8F, 0xC4A56A);
				Hold hold = BY_HELD.get(tie.tied);
				untie(tie);
				if (hold != null) {
					// (out of the knots, and out of the rope with them)
					release(hold, Component.translatable("message.jaymoskingdoms.lasso.broke_out", body.getDisplayName()));
				}
				if (body instanceof ServerPlayer player) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lasso.free").withStyle(ChatFormatting.GREEN));
				}
			}
		}
	}
}
