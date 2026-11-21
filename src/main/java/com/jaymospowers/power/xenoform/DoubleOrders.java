package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Duplex's doubles take spoken orders (Andy's Buder's Lanterns pick, ideas only). A line its owner says aloud that starts with an
 * order (or with "doubles," and then one) goes to their doubles within {@link #HEAR_RANGE}:
 * <ul>
 * <li><b>follow</b> (come, heel): back at their side, every other order dropped.</li>
 * <li><b>wait</b> (stay, hold): they stand where they are till told otherwise.</li>
 * <li><b>scout</b>: the nearest runs {@link #SCOUT_RANGE} blocks the way its owner faces, looks about and comes back telling how
 * many foes it saw.</li>
 * <li><b>collect</b> (gather, pick up): they pick up the drops lying near and bring them to their owner.</li>
 * <li><b>mine</b> (dig): the nearest digs out the open block its owner looks at, then up to {@link #MINE_MOST} more of the same
 * that touch it and lie open, and the drops fall there. Never one its owner couldn't break themselves.</li>
 * <li><b>fetch &lt;thing&gt;</b> (bring, get): the nearest goes to a chest or barrel within {@link #FETCH_RANGE} holding it and
 * brings one stack back; with no words, the stack in the first slot of the container its owner looks at.</li>
 * <li><b>fight</b> (attack): all of them go for what their owner looks at, or the nearest foe.</li>
 * </ul>
 * The doubles answer over the hotbar. The words are said aloud as well (their owner is talking, after all).
 */
public final class DoubleOrders {
	// ---- tuning ----
	/** Doubles this near their owner hear an order (blocks). */
	static final double HEAR_RANGE = 32.0;
	/** Scouting: how far ahead (blocks), how long at most (ticks), and how far round the spot it looks for foes. */
	static final double SCOUT_RANGE = 20.0, SCOUT_LOOK = 16.0;
	static final int SCOUT_TICKS = 300;
	/** Collecting: how far they look for drops, how many stacks one carries. */
	static final double COLLECT_RANGE = 12.0;
	static final int COLLECT_MOST = 9;
	/** Mining: how far its owner's look reaches, the hardest block it digs, how many in a row. */
	static final double MINE_REACH = 6.0;
	static final float MINE_HARDNESS = 5.0F;
	static final int MINE_MOST = 8;
	/** Fetching: how far round its owner it looks for a container (blocks). */
	static final int FETCH_RANGE = 16;
	/** Fighting: how far its owner's look picks a foe; how far the nearest foe may be. */
	static final double FIGHT_LOOK = 24.0, FIGHT_NEAR = 16.0;
	/** How close counts as there (blocks), and how often the way is worked out again (ticks). */
	static final double REACH = 2.2;
	static final int REPATH = 10;

	public enum Order {
		FOLLOW, WAIT, SCOUT, COLLECT, MINE, FETCH, FIGHT
	}

	private DoubleOrders() {
	}

	static void init() {
		// heard before Jaymo's Kingdoms' proximity chat takes the line (it stops it going further)
		Identifier early = JaymosPowers.id("double_orders");
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(early, (message, sender, params) -> {
			heard(sender, message.signedContent());
			return true;
		});
	}

	// ---------------------------------------------------------------- hearing

	/** {@code player} said {@code text}: if it's an order and they have doubles near, it's given. */
	static void heard(ServerPlayer player, String text) {
		String[] words = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").strip().split("\\s+");
		int at = 0;
		if (words.length > 1 && (words[0].equals("doubles") || words[0].equals("double") || words[0].equals("all"))) {
			at = 1;
		}
		if (words.length <= at) {
			return;
		}
		Order order = switch (words[at]) {
			case "follow", "come", "heel" -> Order.FOLLOW;
			case "wait", "stay", "hold" -> Order.WAIT;
			case "scout" -> Order.SCOUT;
			case "collect", "gather", "pick" -> Order.COLLECT;
			case "mine", "dig" -> Order.MINE;
			case "fetch", "bring", "get" -> Order.FETCH;
			case "fight", "attack" -> Order.FIGHT;
			default -> null;
		};
		if (order == null) {
			return;
		}
		StringBuilder rest = new StringBuilder();
		for (int i = at + 1; i < words.length; i++) {
			if (!words[i].equals("up") && !words[i].equals("me") && !words[i].equals("the") && !words[i].equals("some") && !words[i].equals("a")) {
				rest.append(rest.isEmpty() ? "" : " ").append(words[i]);
			}
		}
		// (the work runs on the server thread)
		player.level().getServer().execute(() -> give(player, order, rest.toString()));
	}

	private static void give(ServerPlayer player, Order order, String words) {
		ServerLevel level = player.level();
		List<XenoDouble> doubles = new ArrayList<>(level.getEntitiesOfClass(XenoDouble.class, player.getBoundingBox().inflate(HEAR_RANGE),
			d -> d.isAlive() && !d.fake() && d.isDoubleOf(player)));
		if (doubles.isEmpty()) {
			return;
		}
		doubles.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		String k = "message.jaymospowers.double_orders.";
		switch (order) {
			case FOLLOW, WAIT, COLLECT -> {
				for (XenoDouble d : doubles) {
					d.order(order, d.blockPosition(), null);
				}
			}
			case SCOUT -> {
				Vec3 ahead = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(SCOUT_RANGE);
				doubles.getFirst().order(order, BlockPos.containing(player.position().add(ahead)), null);
			}
			case MINE -> {
				BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(MINE_REACH)),
					ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
				if (hit.getType() != HitResult.Type.BLOCK || !mineable(level, player, hit.getBlockPos())) {
					player.sendOverlayMessage(Component.translatable(k + "mine_none").withStyle(ChatFormatting.GRAY));
					return;
				}
				doubles.getFirst().order(order, hit.getBlockPos(), null);
			}
			case FETCH -> {
				BlockPos chest = words.isEmpty() ? lookedAtContainer(level, player) : containerWith(level, player, words);
				if (chest == null) {
					player.sendOverlayMessage(Component.translatable(k + "fetch_none").withStyle(ChatFormatting.GRAY));
					return;
				}
				doubles.getFirst().order(order, chest, words);
			}
			case FIGHT -> {
				LivingEntity foe = foe(level, player);
				if (foe == null) {
					player.sendOverlayMessage(Component.translatable(k + "fight_none").withStyle(ChatFormatting.GRAY));
					return;
				}
				for (XenoDouble d : doubles) {
					d.order(Order.FOLLOW, d.blockPosition(), null);
					d.setTarget(foe);
				}
			}
		}
		player.sendOverlayMessage(Component.translatable(k + order.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GOLD));
	}

	/** {@code pos} holds an open block (air beside it) no harder than {@link #MINE_HARDNESS} that {@code player} could break. */
	static boolean mineable(ServerLevel level, ServerPlayer player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		float hardness = state.getDestroySpeed(level, pos);
		if (state.isAir() || hardness < 0.0F || hardness > MINE_HARDNESS || level.getBlockEntity(pos) != null
			|| !player.mayInteract(level, pos) || player.blockActionRestricted(level, pos, player.gameMode())) {
			return false;
		}
		for (Direction side : Direction.values()) {
			if (level.getBlockState(pos.relative(side)).isAir()) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable BlockPos lookedAtContainer(ServerLevel level, ServerPlayer player) {
		BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(MINE_REACH)),
			ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && level.getBlockEntity(hit.getBlockPos()) instanceof Container c && !c.isEmpty()
			&& player.mayInteract(level, hit.getBlockPos()) ? hit.getBlockPos() : null;
	}

	/** The nearest container round {@code player} holding a stack whose name has {@code words} in it. */
	private static @Nullable BlockPos containerWith(ServerLevel level, ServerPlayer player, String words) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int cx = player.blockPosition().getX() >> 4, cz = player.blockPosition().getZ() >> 4, reach = (FETCH_RANGE >> 4) + 1;
		for (int x = cx - reach; x <= cx + reach; x++) {
			for (int z = cz - reach; z <= cz + reach; z++) {
				if (!level.hasChunk(x, z)) {
					continue;
				}
				for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values()) {
					double d = be.getBlockPos().distSqr(player.blockPosition());
					if (d < FETCH_RANGE * FETCH_RANGE && d < bestD && be instanceof Container c && slotWith(c, words) >= 0
						&& player.mayInteract(level, be.getBlockPos())) {
						bestD = d;
						best = be.getBlockPos();
					}
				}
			}
		}
		return best;
	}

	/** The first slot of {@code c} whose stack's name has {@code words} in it (any stack, with no words), or -1. */
	static int slotWith(Container c, @Nullable String words) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack stack = c.getItem(i);
			if (!stack.isEmpty() && (words == null || words.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(words))) {
				return i;
			}
		}
		return -1;
	}

	/** What {@code player} looks at (a living thing not theirs), or the nearest foe. */
	private static @Nullable LivingEntity foe(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(FIGHT_LOOK));
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
			player.getBoundingBox().expandTowards(player.getLookAngle().scale(FIGHT_LOOK)).inflate(1.0),
			e -> e instanceof LivingEntity l && l.isAlive() && !(e instanceof XenoDouble twin && twin.isDoubleOf(player)), FIGHT_LOOK * FIGHT_LOOK);
		if (hit != null && hit.getEntity() instanceof LivingEntity looked) {
			return looked;
		}
		LivingEntity best = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(FIGHT_NEAR), e -> e.isAlive() && e instanceof Enemy)) {
			if (best == null || e.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = e;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- carrying out

	/** A double's spoken order at work: everything but following (and fighting, which the swarm does). */
	static final class OrderGoal extends Goal {
		private final XenoDouble mob;
		private int repath, ticks, mined;

		OrderGoal(XenoDouble mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return this.mob.order() != Order.FOLLOW && this.mob.getTarget() == null && !this.mob.busy();
		}

		@Override
		public void start() {
			this.repath = 0;
			this.ticks = 0;
			this.mined = 0;
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			ServerPlayer owner = this.mob.ownerPlayer();
			if (owner == null || !(this.mob.level() instanceof ServerLevel level)) {
				return;
			}
			this.ticks++;
			BlockPos pos = this.mob.orderPos();
			switch (this.mob.order()) {
				case WAIT -> {
					if (this.mob.distanceToSqr(Vec3.atBottomCenterOf(pos)) > 2.0) {
						this.go(Vec3.atBottomCenterOf(pos));
					} else {
						this.mob.getNavigation().stop();
					}
				}
				case SCOUT -> {
					if (this.ticks > SCOUT_TICKS || this.mob.distanceToSqr(Vec3.atBottomCenterOf(pos)) < REACH * REACH * 4) {
						int foes = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(SCOUT_LOOK), e -> e.isAlive() && e instanceof Enemy).size();
						owner.sendOverlayMessage(Component.translatable(foes == 0 ? "message.jaymospowers.double_orders.scout_clear"
							: "message.jaymospowers.double_orders.scout_foes", foes).withStyle(foes == 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
						this.mob.order(Order.FOLLOW, pos, null);
					} else {
						this.go(Vec3.atBottomCenterOf(pos));
					}
				}
				case COLLECT -> this.collect(level, owner);
				case MINE -> this.mine(level, owner, pos);
				case FETCH -> this.fetch(level, owner, pos);
				default -> this.mob.order(Order.FOLLOW, pos, null);
			}
		}

		private void go(Vec3 to) {
			this.mob.getLookControl().setLookAt(to.x, to.y + 0.5, to.z);
			if (--this.repath <= 0) {
				this.repath = REPATH;
				this.mob.getNavigation().moveTo(to.x, to.y, to.z, XenoDouble.FOLLOW_SPEED);
			}
		}

		/** Picks up the drops near, then brings them home. */
		private void collect(ServerLevel level, ServerPlayer owner) {
			List<ItemStack> held = this.mob.held();
			ItemEntity drop = null;
			if (held.size() < COLLECT_MOST) {
				for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, this.mob.getBoundingBox().inflate(COLLECT_RANGE), e -> e.isAlive() && !e.hasPickUpDelay())) {
					if (drop == null || e.distanceToSqr(this.mob) < drop.distanceToSqr(this.mob)) {
						drop = e;
					}
				}
			}
			if (drop != null) {
				if (this.mob.distanceToSqr(drop) < REACH * REACH) {
					held.add(drop.getItem().copy());
					drop.discard();
				} else {
					this.go(drop.position());
				}
				return;
			}
			this.bringHome(owner);
		}

		/** Gives what it holds to its owner once beside them; then it follows again. */
		private void bringHome(ServerPlayer owner) {
			if (this.mob.distanceToSqr(owner) > REACH * REACH) {
				this.go(owner.position());
				return;
			}
			for (ItemStack stack : this.mob.held()) {
				if (!owner.getInventory().add(stack)) {
					owner.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			this.mob.held().clear();
			this.mob.order(Order.FOLLOW, this.mob.blockPosition(), null);
		}

		/** Digs the block at {@code pos}, then the next of its kind beside it that lies open. */
		private void mine(ServerLevel level, ServerPlayer owner, BlockPos pos) {
			BlockState state = level.getBlockState(pos);
			if (!mineable(level, owner, pos) || this.ticks > XenoDouble.ERRAND_TICKS * 2) {
				this.mob.order(Order.FOLLOW, pos, null);
				return;
			}
			if (this.mob.position().add(0.0, this.mob.getBbHeight() * 0.5, 0.0).distanceToSqr(Vec3.atCenterOf(pos)) > XenoDouble.ERRAND_REACH * XenoDouble.ERRAND_REACH) {
				this.go(Vec3.atBottomCenterOf(pos));
				return;
			}
			this.mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.destroyBlock(pos, true, this.mob);
			this.mined++;
			BlockPos next = null;
			if (this.mined < MINE_MOST) {
				for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
					if (level.getBlockState(p).is(state.getBlock()) && mineable(level, owner, p)) {
						next = p.immutable();
						break;
					}
				}
			}
			this.ticks = 0;
			this.mob.order(next != null ? Order.MINE : Order.FOLLOW, next != null ? next : pos, null);
		}

		/** Goes to the container, takes the stack, and brings it home. */
		private void fetch(ServerLevel level, ServerPlayer owner, BlockPos pos) {
			if (!this.mob.held().isEmpty()) {
				this.bringHome(owner);
				return;
			}
			if (!(level.getBlockEntity(pos) instanceof Container c) || this.ticks > XenoDouble.ERRAND_TICKS * 2) {
				this.mob.order(Order.FOLLOW, pos, null);
				return;
			}
			if (this.mob.position().add(0.0, this.mob.getBbHeight() * 0.5, 0.0).distanceToSqr(Vec3.atCenterOf(pos)) > XenoDouble.ERRAND_REACH * XenoDouble.ERRAND_REACH) {
				this.go(Vec3.atBottomCenterOf(pos));
				return;
			}
			int slot = slotWith(c, this.mob.orderWords());
			if (slot < 0) {
				owner.sendOverlayMessage(Component.translatable("message.jaymospowers.double_orders.fetch_gone").withStyle(ChatFormatting.GRAY));
				this.mob.order(Order.FOLLOW, pos, null);
				return;
			}
			this.mob.held().add(c.removeItemNoUpdate(slot));
			c.setChanged();
		}
	}
}
