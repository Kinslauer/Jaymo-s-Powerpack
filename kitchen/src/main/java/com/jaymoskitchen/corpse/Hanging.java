package com.jaymoskitchen.corpse;

import com.jaymoskitchen.butchery.Butchery;
import com.jaymoskitchen.butchery.MeatHookBlock;
import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollRig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.51) Andy: "add a hanging carcass and a butchery inspired by theirs" (Alex's Butchery, ideas only). A carcass
 * hung on a {@link MeatHookBlock} hangs by its hind legs (a ragdoll held at the hook, so it swings and turns). It's
 * worked a step at a time with right-clicks, each wearing the tool:
 * <ol>
 * <li><b>Bleed it</b> (a cleaver): it drains for a while, into a {@link com.jaymoskitchen.butchery.BloodGrateBlock}
 * under it if there is one, else onto the ground ({@link #setOnSpill}: Jaymo's Powers lays it as lasting stains).</li>
 * <li><b>Take the head</b> (a cleaver), if it has one: the body's drawn without it ({@link #setOnHead}: Powers throws
 * it down as a severed head you can carry off).</li>
 * <li><b>Skin it</b> (a skinning knife), if it has anything to skin or pluck: its hide, wool or feathers.</li>
 * <li><b>Three cuts</b> (a cleaver): meat, fat, bones and the rest, a third at a time, with a proper job's extras
 * (as a butcher table's); gone after the last.</li>
 * </ol>
 * Once it's drained it can be taken down and finished on the ground. Too big to lift (a cow taken off the ground, a
 * horse, a bear): it can be dragged up onto a hook, or bled where it lies with a cleaver and then cut up there.
 */
public final class Hanging {
	/** Its steps ({@link CorpseEntity#butcherStage}). The cuts are {@link #CUTS} steps from {@link #SKINNED}. */
	public static final int FRESH = 0, BLEEDING = 1, DRAINED = 2, HEADLESS = 3, SKINNED = 4, CUTS = 3;
	/** How long it bleeds (ticks): something small, most, and a big carcass. */
	static final int BLEED_SMALL = 200, BLEED_TICKS = 400, BLEED_BIG = 800;
	/** How much blood is in it (millibuckets), the same three ways (Alex's Butchery's 250, 1000, 2000). */
	static final int BLOOD_SMALL = 250, BLOOD = 1000, BLOOD_BIG = 2000;
	/** A drop falls every this many ticks; blood with no grate under it pools on the ground every this many. */
	static final int DRIP_EVERY = 4, SPILL_EVERY = 20;
	/** How far below the carcass a grate can be (blocks), and how far a dragged carcass can be heaved onto a hook. */
	static final int GRATE_DEPTH = 4;
	public static final double DRAG_TO_HOOK = 3.0;
	/** Bones left over after the last cut (at least one, up to this many more). */
	static final int BONES_MORE = 2;
	/** Skinned on the hook: this many more of each skin (a clean, whole one), and leather scraps (at least one, up to this many more). */
	static final int PRIME_EXTRA = 1, SCRAPS_MORE = 1;
	/** Animal fat from anything bigger than a chicken after the last cut (at least one, up to this many more). */
	static final int FAT_MORE = 1;

	/** (Kitchen 0.5.57) Other mods' hooks: extra butcher-table extras for whoever cuts, and a word when a carcass is finished. Kitchen knows nothing of who adds them. */
	private static final List<ToIntFunction<Player>> EXTRA_CUTS = new ArrayList<>();
	private static final List<BiConsumer<Player, Entity>> ON_DONE = new ArrayList<>();

	/** (Kitchen 0.5.57) {@code extra} says how many more times the butcher-table extras are added for this player's hung carcass. */
	public static void addExtraCuts(ToIntFunction<Player> extra) {
		EXTRA_CUTS.add(extra);
	}

	/** (Kitchen 0.5.57) {@code done} is told of the player and the carcass when a hung carcass is finished. */
	public static void addOnDone(BiConsumer<Player, Entity> done) {
		ON_DONE.add(done);
	}
	/** (Jaymo's Powers) A drop of the carcass's blood falls from here. */
	private static @Nullable BiConsumer<Entity, Vec3> onDrip;
	/** (Jaymo's Powers) Blood with nowhere to go pools on the ground under here. */
	private static @Nullable BiConsumer<Entity, Vec3> onSpill;
	/** (Jaymo's Powers) The head comes off here. */
	private static @Nullable BiConsumer<Entity, Vec3> onHead;

	private Hanging() {
	}

	public static void setOnDrip(BiConsumer<Entity, Vec3> hook) {
		onDrip = hook;
	}

	public static void setOnSpill(BiConsumer<Entity, Vec3> hook) {
		onSpill = hook;
	}

	public static void setOnHead(BiConsumer<Entity, Vec3> hook) {
		onHead = hook;
	}

	/** Whether {@code corpse} can go on a hook at all: an animal's body that bleeds (not a fish, nor a machine). */
	static boolean hangable(CorpseEntity corpse) {
		return corpse.kind() == CorpseKind.ANIMAL && !corpse.isFish() && !corpse.isMachine() && !corpse.isHeavy();
	}

	/** {@code player} hangs {@code corpse} (carried or dragged) on the hook at {@code hook}. */
	public static boolean hang(CorpseEntity corpse, Player player, BlockPos hook) {
		if (!hangable(corpse)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.wont_hang"));
			return false;
		}
		if (!(corpse.level() instanceof ServerLevel level)) {
			return false;
		}
		corpse.hangOn(hook);
		level.playSound(null, hook, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		player.sendOverlayMessage(Component.translatable(corpse.butcherStage() == FRESH ? "message.jaymoskitchen.hook.hung"
			: "message.jaymoskitchen.hook.hung_again"));
		return true;
	}

	/** Where the hook holds the legs. */
	static Vec3 holdPoint(BlockPos hook) {
		return new Vec3(hook.getX() + 0.5, hook.getY() + MeatHookBlock.HOLD_AT, hook.getZ() + 0.5);
	}

	/** The points the hook holds it by: both hind legs (both feet, a snake's tail tip, a box's two bottom corners). */
	static int[] held(RagdollRig rig) {
		return switch (rig.shape) {
			case QUADRUPED -> new int[] {RagdollRig.BR_FOOT, RagdollRig.BL_FOOT};
			case HUMANOID -> new int[] {RagdollRig.R_FOOT, RagdollRig.L_FOOT};
			case BOX -> new int[] {RagdollRig.BOTTOM_RIGHT, RagdollRig.BOTTOM_LEFT};
			case CHAIN -> new int[] {rig.tailTip()};
		};
	}

	/** Server, each tick it hangs: held at the hook (let fall if the hook's gone), and bleeding if it's being bled. */
	static void tick(CorpseEntity corpse, ServerLevel level, RagdollBody body) {
		BlockPos hook = corpse.hook();
		if (hook == null) {
			return;
		}
		if (corpse.tickCount % 10 == 0 && !Butchery.isHook(level.getBlockState(hook))) {
			corpse.unhook();
			return;
		}
		Vec3 at = holdPoint(hook);
		int[] points = held(body.rig);
		for (int i = 0; i < points.length; i++) {
			// (the two legs a hair apart on the hook, so it hangs square)
			double side = points.length > 1 ? (i == 0 ? 0.06 : -0.06) : 0.0;
			body.grip(i, points[i], at.x + side, at.y, at.z);
		}
		if (corpse.butcherStage() == BLEEDING) {
			bleed(corpse, level, lowest(body));
		}
	}

	/**
	 * Server, each tick a carcass too big to hang is bled where it lies. (0.5.70) From its throat, beside the body: it
	 * bled from inside its middle, so the drops and the pool were all hidden under it.
	 */
	static void tickGround(CorpseEntity corpse, ServerLevel level) {
		if (corpse.butcherStage() == BLEEDING) {
			bleed(corpse, level, corpse.throatPoint());
		}
	}

	/**
	 * Its lowest point (its head, hanging): where the blood drips from. (0.5.70) The bottom of that part, not its middle,
	 * so the drops start outside the body.
	 */
	private static Vec3 lowest(RagdollBody body) {
		int low = 0;
		for (int i = 1; i < body.y.length; i++) {
			if (body.y[i] - body.rig.radius[i] < body.y[low] - body.rig.radius[low]) {
				low = i;
			}
		}
		return new Vec3(body.x[low], body.y[low] - body.rig.radius[low], body.z[low]);
	}

	/** One tick of bleeding: drops, the blood into a grate or onto the ground, and done when it's drained. */
	private static void bleed(CorpseEntity corpse, ServerLevel level, Vec3 from) {
		int total = corpse.bleedTotal();
		int left = corpse.bleedLeft() - 1;
		corpse.setBleedLeft(left);
		if (corpse.tickCount % DRIP_EVERY == 0) {
			if (onDrip != null) {
				onDrip.accept(corpse, from);
			} else {
				com.jaymoskitchen.KitchenBlood.send(level, corpse, from.x, from.y - 0.1, from.z, 1, 0.03, 0.0, 0.03, 0.0);
			}
		}
		// the blood that's come out by now, in grate levels, against what's been put somewhere already
		int blood = bloodIn(corpse);
		int out = total <= 0 ? 0 : Math.round((float) (total - Math.max(0, left)) / total * blood / Butchery.MB_PER_LEVEL);
		int pour = out - corpse.bloodPoured();
		if (pour > 0) {
			corpse.setBloodPoured(out);
			BlockPos grate = Butchery.grateUnder(level, from, GRATE_DEPTH);
			int spilt = grate == null ? pour : Butchery.pour(level, grate, pour);
			if (grate != null && spilt < pour) {
				level.playSound(null, grate, SoundEvents.HONEY_BLOCK_STEP, SoundSource.BLOCKS, 0.5F, 0.5F);
			}
			corpse.addSpilt(spilt);
		}
		if (corpse.spilt() > 0 && corpse.tickCount % SPILL_EVERY == 0) {
			corpse.addSpilt(-1);
			if (onSpill != null) {
				onSpill.accept(corpse, from);
			}
		}
		if (left <= 0) {
			corpse.setButcherStage(DRAINED);
			level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.NEUTRAL, 1.0F, 0.6F);
			if (corpse.tooBigToLift() && corpse.hook() == null) {
				// bled where it lies: the proper job's extras go on it, and it's cut up there as before
				corpse.addLoot(corpse.blockExtras());
			}
		}
	}

	/** How much blood is in it (millibuckets). */
	static int bloodIn(CorpseEntity corpse) {
		return corpse.tooBigToLift() ? BLOOD_BIG : corpse.oneHanded() ? BLOOD_SMALL : BLOOD;
	}

	private static int bleedTicks(CorpseEntity corpse) {
		return corpse.tooBigToLift() ? BLEED_BIG : corpse.oneHanded() ? BLEED_SMALL : BLEED_TICKS;
	}

	/**
	 * A right-click on {@code corpse} with {@code held}: its next step, if this is a hung carcass (or one that's been
	 * started on: bled, being bled, or one too big to lift held a cleaver on). Null: none of this, it's used as before.
	 */
	static @Nullable InteractionResult use(CorpseEntity corpse, ServerLevel level, Player player, ItemStack held) {
		boolean hung = corpse.hook() != null;
		int stage = corpse.butcherStage();
		boolean cleaver = held.is(Butchery.CLEAVERS), knife = held.is(Butchery.SKINNING_KNIVES);
		if (!hung) {
			if (stage == BLEEDING) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.draining", (corpse.bleedLeft() + 19) / 20));
				return InteractionResult.SUCCESS_SERVER;
			}
			if (stage == FRESH && cleaver && corpse.tooBigToLift() && !player.isShiftKeyDown() && hangable(corpse)) {
				// too big to hang (or not hung): bled where it lies
				startBleeding(corpse, level, player, held);
				return InteractionResult.SUCCESS_SERVER;
			}
			if (stage < DRAINED || corpse.tooBigToLift() || !(cleaver || knife)) {
				return null;
			}
		}
		if (!cleaver && !knife) {
			player.sendOverlayMessage(Component.translatable(stage == FRESH ? "message.jaymoskitchen.hook.need_cleaver_bleed"
				: "message.jaymoskitchen.hook.need_tools"));
			return InteractionResult.SUCCESS_SERVER;
		}
		switch (stage) {
			case FRESH -> {
				if (!cleaver) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.need_cleaver_bleed"));
					return InteractionResult.SUCCESS_SERVER;
				}
				startBleeding(corpse, level, player, held);
				return InteractionResult.SUCCESS_SERVER;
			}
			case BLEEDING -> {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.draining", (corpse.bleedLeft() + 19) / 20));
				return InteractionResult.SUCCESS_SERVER;
			}
			case DRAINED -> {
				if (hasHead(corpse)) {
					if (!cleaver) {
						player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.need_cleaver_head"));
						return InteractionResult.SUCCESS_SERVER;
					}
					takeHead(corpse, level, player, held);
					return InteractionResult.SUCCESS_SERVER;
				}
				corpse.setButcherStage(HEADLESS);
				return use(corpse, level, player, held);
			}
			case HEADLESS -> {
				if (hasSkin(corpse)) {
					if (!knife) {
						player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.need_knife"));
						return InteractionResult.SUCCESS_SERVER;
					}
					skin(corpse, level, player, held);
					return InteractionResult.SUCCESS_SERVER;
				}
				corpse.setButcherStage(SKINNED);
				return use(corpse, level, player, held);
			}
			default -> {
				if (!cleaver) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.need_cleaver_cut"));
					return InteractionResult.SUCCESS_SERVER;
				}
				cut(corpse, level, player, held);
				return InteractionResult.SUCCESS_SERVER;
			}
		}
	}

	private static void startBleeding(CorpseEntity corpse, ServerLevel level, Player player, ItemStack tool) {
		tool.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		int ticks = bleedTicks(corpse);
		corpse.startBleeding(ticks);
		corpse.jolt(true);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.AXE_STRIP.value(), SoundSource.NEUTRAL, 0.8F, 1.4F);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.7F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.bleeding", (ticks + 19) / 20,
			Butchery.grateUnder(level, corpse.position(), GRATE_DEPTH + 3) == null ? Component.translatable("message.jaymoskitchen.hook.no_grate")
				: Component.empty()));
	}

	/** Whether it still has a head to take (a four-legged or two-legged body that hasn't lost it). */
	static boolean hasHead(CorpseEntity corpse) {
		RagdollRig.Shape shape = corpse.rigShape();
		return (shape == RagdollRig.Shape.QUADRUPED || shape == RagdollRig.Shape.HUMANOID) && (corpse.missing() & 1) == 0;
	}

	private static void takeHead(CorpseEntity corpse, ServerLevel level, Player player, ItemStack tool) {
		tool.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		Vec3 at = corpse.headPoint();
		corpse.setMissing(corpse.missing() | 1);
		corpse.setButcherStage(HEADLESS);
		corpse.jolt(true);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AXE_STRIP.value(), SoundSource.NEUTRAL, 1.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.7F, 0.6F);
		if (onHead != null) {
			onHead.accept(corpse, at);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.head_off"));
	}

	static boolean hasSkin(CorpseEntity corpse) {
		for (ItemStack stack : corpse.lootView()) {
			if (stack.is(Butchery.SKINS)) {
				return true;
			}
		}
		return false;
	}

	private static void skin(CorpseEntity corpse, ServerLevel level, Player player, ItemStack tool) {
		tool.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		List<ItemStack> skins = corpse.takeLoot(stack -> stack.is(Butchery.SKINS));
		boolean prime = corpse.hook() != null;
		for (ItemStack skin : skins) {
			if (prime && skin.getMaxStackSize() > 1) {
				// hung and skinned with a knife, it comes off whole: a prime skin, one more than a rough job gets
				skin.grow(PRIME_EXTRA);
			}
			corpse.spawnAtLocation(level, skin, 0.3F);
		}
		// and the trimmings, for mending leather
		corpse.spawnAtLocation(level, new ItemStack(Butchery.LEATHER_SCRAPS, 1 + level.getRandom().nextInt(SCRAPS_MORE + 1)), 0.3F);
		corpse.setButcherStage(SKINNED);
		corpse.jolt(false);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 0.8F, 0.8F);
		level.sendParticles(ParticleTypes.CRIT, corpse.getX(), corpse.getY() + 0.3, corpse.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.skinned"));
	}

	private static void cut(CorpseEntity corpse, ServerLevel level, Player player, ItemStack tool) {
		tool.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		int done = corpse.butcherStage() - SKINNED;
		if (done == 0) {
			// done properly, hung and bled: what a butcher table gets out of it too
			corpse.addLoot(corpse.blockExtras());
			// (Kitchen 0.5.57) and what other mods add for this butcher
			int more = 0;
			for (ToIntFunction<Player> extra : EXTRA_CUTS) {
				more += extra.applyAsInt(player);
			}
			for (int i = 0; i < more; i++) {
				corpse.addLoot(corpse.blockExtras());
			}
		}
		int cutsLeft = CUTS - done;
		corpse.jolt(true);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.AXE_STRIP.value(), SoundSource.NEUTRAL, 0.9F,
			0.8F + done * 0.1F);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.6F, 0.7F);
		if (cutsLeft <= 1) {
			for (ItemStack stack : corpse.takeLoot(stack -> true)) {
				corpse.spawnAtLocation(level, stack, 0.3F);
			}
			corpse.spawnAtLocation(level, new ItemStack(Items.BONE, 1 + level.getRandom().nextInt(BONES_MORE + 1)), 0.3F);
			if (!corpse.oneHanded() && com.jaymoskitchen.tanning.Tanning.ANIMAL_FAT != null) {
				// the fat cut away along the way (for tallow, and the tanner)
				corpse.spawnAtLocation(level, new ItemStack(com.jaymoskitchen.tanning.Tanning.ANIMAL_FAT, 1 + level.getRandom().nextInt(FAT_MORE + 1)), 0.3F);
			}
			level.sendParticles(ParticleTypes.CRIT, corpse.getX(), corpse.getY() + 0.3, corpse.getZ(), 20, 0.4, 0.3, 0.4, 0.2);
			level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.done"));
			for (BiConsumer<Player, Entity> listener : ON_DONE) {
				listener.accept(player, corpse);
			}
			corpse.discard();
			return;
		}
		int pieces = (corpse.piecesLeft() + cutsLeft - 1) / cutsLeft;
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < pieces; i++) {
			ItemStack piece = corpse.nextPiece();
			if (piece.isEmpty()) {
				break;
			}
			out.add(piece);
		}
		for (ItemStack piece : out) {
			corpse.spawnAtLocation(level, piece, 0.3F);
		}
		corpse.setButcherStage(corpse.butcherStage() + 1);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.cut", done + 1, CUTS));
	}
}
