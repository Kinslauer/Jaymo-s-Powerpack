package com.jaymospowers.power.web;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Patch 2 of the symbiote's 1.13 plan: it saves you. Checked from its instinct ({@link WebSymbiote#INSTINCT_CHECK_TICKS},
 * so every half second) and, for arrows, from the damage event. Each reflex shoves you with a {@link WebNet.Nudge} (the
 * same push its instinct uses against fire and blasts), draws its tendrils (the back ones burst out) and speaks a line
 * ({@code reflex.<act>.N}, see {@code lang/parts/web_reflexes.json}), which darkens your screen like any whisper.
 * <p>
 * None of it happens while the symbiote is dormant, sulking, stunned by a bell ({@link WebSymbioteJar#stunned}), or you
 * are in creative. It hates fire: the lava heave is fast, but costs it satiety and stress and doesn't put you out, so
 * lava is never safe, only survivable. Meat is its own food, so the bag meal it gives you is never meat.
 */
final class WebSymbioteReflexes {
	// ---- tuning ------------------------------------------------------------------------------------------------------
	/** Drowning: air (of 300) under which it hauls you up, how hard, how far above your head it breaks ice, how long a roof is tried. */
	static final int DROWN_AIR = 70;
	static final double DROWN_LIFT = 0.95;
	static final int DROWN_ICE_REACH = 5, DROWN_SCAN = 24;
	static final int DROWN_GIVE_UP = 100;
	/** Buried: the hardest block it will tear through (obsidian and bedrock are past it), and the crack it shows first. */
	static final float BURIED_MAX_HARDNESS = 30.0F;
	static final int BURIED_CRACK_STAGE = 6;
	/** Powder snow: frozen ticks before it acts, how hard it shoves you out. */
	static final int SNOW_FROZEN = 40;
	static final double SNOW_SHOVE = 0.75, SNOW_LIFT = 0.55;
	/** Lava: how far it looks for a shore, the shove, what the heave costs it. */
	static final int LAVA_REACH = 6;
	static final double LAVA_SHOVE = 1.05, LAVA_LIFT_BASE = 0.55, LAVA_LIFT_PER_BLOCK = 0.2, LAVA_LIFT_MAX = 1.15;
	static final int LAVA_SATIETY_COST = 12, LAVA_STRESS = 15;
	/** Ledge pull: the pull, the cooldown, how far ahead of a walker it looks, how far a drop it reads, how far a jump may land. */
	static final double LEDGE_PULL = 0.6, LEDGE_LIFT = 0.12, LEDGE_MIN_MOVE = 0.35, LEDGE_JUMP_LANDING = 5.0;
	static final int LEDGE_COOLDOWN = 40, LEDGE_SCAN = 70;
	/** Items: health (hit points) it counts as about to die, the totem's, how long it waits after a save, how recent a hit counts. */
	static final float SAVE_HEALTH = 5.0F, TOTEM_HEALTH = 3.0F;
	static final int SAVE_COOLDOWN = 600, SAVE_HIT_TICKS = 60;
	static final double SAVE_FOE_RANGE = 6.0;
	/** Arrows: a hit this big is capped at {@code ARROW_CAP}, at a cost; at most once per {@code ARROW_CAP_COOLDOWN}. */
	static final float ARROW_BIG = 8.0F, ARROW_CAP = 4.0F;
	static final int ARROW_CAP_COOLDOWN = 400, ARROW_CAP_SATIETY_COST = 15, ARROW_CAP_STRESS = 12, ARROW_MIN_SATIETY = 20;
	static final int ARROW_FX_TICKS = 14, ARROW_LINE_GAP = 200;
	/** Feeding you: your food level at or under which it eats for you, and the gap between bites. */
	static final int FEED_BELOW = 6, FEED_GAP = 60;
	/** The void: how far under the world's floor it says goodbye. */
	static final int VOID_BELOW = 4;
	/** How many variants each line has. */
	static final int LINES = 3;

	private static final class R {
		Vec3 lastPos = Vec3.ZERO;
		int roofTicks, buriedPulses;
		long ledgeReadyAt, saveReadyAt, capReadyAt, feedReadyAt, bounceLineAt;
		boolean drownSaid, giveUpSaid, voidSaid;
	}

	private static final Map<ServerPlayer, R> STATE = new WeakHashMap<>();
	private static boolean capping;

	private WebSymbioteReflexes() {
	}

	private static R of(ServerPlayer player) {
		return STATE.computeIfAbsent(player, p -> new R());
	}

	static void register() {
		// arrows turn on Living Armor: they bounce, and one very big hit is capped
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (capping || !(entity instanceof ServerPlayer player) || !(source.getDirectEntity() instanceof AbstractArrow)
				|| !WebSymbiote.livingArmor(player) || !acts(player)) {
				return true;
			}
			long now = player.level().getGameTime();
			R r = of(player);
			WebSymbioteState state = WebSymbiote.state(player);
			if (amount >= ARROW_BIG) {
				if (now < r.capReadyAt || state.satiety() < ARROW_MIN_SATIETY) {
					return true;
				}
				// a very big hit: it takes most of it on the shell, and pays
				r.capReadyAt = now + ARROW_CAP_COOLDOWN;
				WebSymbiote.set(player, state.withSatiety(Math.max(0, state.satiety() - ARROW_CAP_SATIETY_COST))
					.withStress(Math.min(WebSymbiote.STRESS_MAX, state.stress() + ARROW_CAP_STRESS)));
				capping = true;
				try {
					player.hurtServer(player.level(), source, ARROW_CAP);
				} finally {
					capping = false;
				}
				say(player, "arrow_big");
				shellFx(player, 1.3F);
				return false;
			}
			shellFx(player, 0.9F);
			WebSymbiote.dread(player, ARROW_FX_TICKS, false);
			if (now >= r.bounceLineAt) {
				r.bounceLineAt = now + ARROW_LINE_GAP;
				say(player, "arrow");
			}
			return false;
		});
	}

	/** Whether its reflexes may act at all right now. */
	private static boolean acts(ServerPlayer player) {
		return WebSymbiote.isBonded(player) && player.isAlive() && !player.isSpectator() && !player.isCreative()
			&& !WebSymbioteBond.dormant(player) && !WebSymbioteJar.stunned(player) && !WebSymbiote.sulking(player)
			&& !WebSymbioteRescue.rescuing(player);
	}

	/**
	 * Called from the instinct (every {@link WebSymbiote#INSTINCT_CHECK_TICKS} ticks, before its other dangers). Returns
	 * {@code state} itself when nothing was done, else the state after (a reflex took satiety or stress).
	 */
	static WebSymbioteState tick(ServerPlayer player, WebSymbioteState state, long now) {
		if (!acts(player)) {
			return state;
		}
		R r = of(player);
		Vec3 here = player.position();
		Vec3 moved = here.subtract(r.lastPos);
		r.lastPos = here;
		ServerLevel level = player.level();
		if (level.getBlockState(BlockPos.containing(player.getEyePosition())).getFluidState().isEmpty() || !player.isUnderWater()) {
			r.roofTicks = 0;
			r.drownSaid = false;
			r.giveUpSaid = false;
		}
		if (player.getY() > level.getMinY()) {
			r.voidSaid = false;
		}
		if (player.getY() < level.getMinY() - VOID_BELOW) {
			if (!r.voidSaid) {
				r.voidSaid = true;
				say(player, "goodbye");
			}
			return state;
		}
		if (saveWithItems(player, r, now)) {
			return WebSymbiote.state(player);
		}
		if (player.isInLava()) {
			return lavaHeave(player, state);
		}
		if (player.isUnderWater() && player.getAirSupply() <= DROWN_AIR) {
			drowning(player, r);
			return state;
		}
		if (buried(player, r)) {
			return state;
		}
		if (inPowderSnow(player)) {
			return state;
		}
		if (state.kind() == WebSymbioteStrain.WHITE && ledgePull(player, r, moved, now)) {
			return state;
		}
		if (feedYou(player, r, now)) {
			return state;
		}
		return state;
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------

	private static void say(ServerPlayer player, String act) {
		WebSymbiote.whisper(player, "reflex." + act + "." + (1 + player.getRandom().nextInt(LINES)));
	}

	private static void out(ServerPlayer player) {
		player.setAttached(WebSymbiote.TENDRILS_OUT, player.level().getGameTime() + WebSymbiote.TENDRILS_OUT_TICKS);
	}

	private static void shove(ServerPlayer player, Vec3 v) {
		ServerPlayNetworking.send(player, new WebNet.Nudge(v));
	}

	/** A line of black motes from {@code from} to {@code to}: a tendril. */
	private static void line(ServerPlayer player, Vec3 from, Vec3 to) {
		ServerLevel level = player.level();
		Vec3 d = to.subtract(from);
		int n = Math.max(2, (int)(d.length() * 4.0));
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(d.scale(i / (double)n));
			level.sendParticles(WebSymbiote.dust(0.9F), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static Vec3 back(ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		return player.position().add(0.0, 1.2, 0.0).subtract(look.scale(0.3));
	}

	private static void shellFx(ServerPlayer player, float scale) {
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 0.9F, 1.5F);
		level.sendParticles(WebSymbiote.dust(scale), player.getX(), player.getY() + 1.1, player.getZ(), 8, 0.3, 0.5, 0.3, 0.0);
	}

	// ---- 1. drowning ------------------------------------------------------------------------------------------------

	private static void drowning(ServerPlayer player, R r) {
		ServerLevel level = player.level();
		BlockPos head = BlockPos.containing(player.getEyePosition());
		// what lies above: open air (a way up), ice (it breaks it), or a solid roof
		boolean open = false;
		for (int i = 1; i <= DROWN_SCAN; i++) {
			BlockPos p = head.above(i);
			BlockState s = level.getBlockState(p);
			if (s.getFluidState().isEmpty() && s.getCollisionShape(level, p).isEmpty()) {
				open = true;
				break;
			}
			if (s.getFluidState().isEmpty()) {
				if (s.is(BlockTags.ICE) && i <= DROWN_ICE_REACH) {
					level.destroyBlock(p, false);
					level.playSound(null, p, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F);
					open = true;
				}
				break;
			}
		}
		if (!r.drownSaid) {
			r.drownSaid = true;
			say(player, "drown");
		}
		if (!open) {
			r.roofTicks += WebSymbiote.INSTINCT_CHECK_TICKS;
			if (r.roofTicks >= DROWN_GIVE_UP) {
				// solid stone over you: it can't haul you through it
				if (!r.giveUpSaid) {
					r.giveUpSaid = true;
					say(player, "drown_roof");
				}
				return;
			}
		} else {
			r.roofTicks = 0;
		}
		out(player);
		shove(player, new Vec3(0.0, DROWN_LIFT, 0.0));
		line(player, back(player), back(player).add(0.0, 2.2, 0.0));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	// ---- 2. buried alive --------------------------------------------------------------------------------------------

	private static boolean buried(ServerPlayer player, R r) {
		ServerLevel level = player.level();
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		BlockState s = level.getBlockState(eye);
		if (s.isAir() || !s.isSuffocating(level, eye)) {
			r.buriedPulses = 0;
			return false;
		}
		float hardness = s.getDestroySpeed(level, eye);
		if (hardness < 0.0F || hardness > BURIED_MAX_HARDNESS) {
			return false;
		}
		out(player);
		line(player, back(player), Vec3.atCenterOf(eye));
		if (r.buriedPulses++ == 0) {
			say(player, "buried");
			WebCracks.crack(level, eye, BURIED_CRACK_STAGE);
			return true;
		}
		r.buriedPulses = 0;
		level.destroyBlock(eye, true);
		BlockPos feet = player.blockPosition();
		BlockState f = level.getBlockState(feet);
		if (!f.isAir() && f.isSuffocating(level, feet) && f.getDestroySpeed(level, feet) >= 0.0F && f.getDestroySpeed(level, feet) <= BURIED_MAX_HARDNESS) {
			level.destroyBlock(feet, true);
		}
		return true;
	}

	// ---- 3. powder snow ---------------------------------------------------------------------------------------------

	private static boolean inPowderSnow(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		if (player.getTicksFrozen() < SNOW_FROZEN
			|| !level.getBlockState(feet).is(Blocks.POWDER_SNOW) && !level.getBlockState(feet.above()).is(Blocks.POWDER_SNOW)) {
			return false;
		}
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 2, 1))) {
			if (level.getBlockState(p).is(Blocks.POWDER_SNOW)) {
				level.destroyBlock(p, false);
			}
		}
		// out toward the nearest open side with no snow, else straight up
		Vec3 out = new Vec3(0.0, 0.0, 0.0);
		double best = Double.MAX_VALUE;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockPos p = feet.offset(dx, 0, dz);
				if ((dx != 0 || dz != 0) && level.getBlockState(p).getCollisionShape(level, p).isEmpty() && !level.getBlockState(p).is(Blocks.POWDER_SNOW)
					&& !level.getBlockState(p.below()).isAir() && dx * dx + dz * dz < best) {
					best = dx * dx + dz * dz;
					out = new Vec3(dx, 0.0, dz).normalize();
				}
			}
		}
		out(player);
		say(player, "snow");
		line(player, back(player), player.position().add(out.scale(1.6)).add(0.0, 1.0, 0.0));
		shove(player, out.scale(SNOW_SHOVE).add(0.0, SNOW_LIFT, 0.0));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 0.9F, 1.0F);
		return true;
	}

	// ---- 4. lava ----------------------------------------------------------------------------------------------------

	private static WebSymbioteState lavaHeave(ServerPlayer player, WebSymbioteState state) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		BlockPos shore = null;
		double best = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-LAVA_REACH, -1, -LAVA_REACH), feet.offset(LAVA_REACH, 3, LAVA_REACH))) {
			BlockState at = level.getBlockState(p);
			BlockState below = level.getBlockState(p.below());
			if (at.getFluidState().isEmpty() && at.getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
				&& level.getBlockState(p.above()).getFluidState().isEmpty() && below.getFluidState().isEmpty() && below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
				&& !below.is(Blocks.MAGMA_BLOCK) && !at.is(BlockTags.FIRE)) {
				double d = p.distSqr(feet);
				if (d < best) {
					best = d;
					shore = p.immutable();
				}
			}
		}
		out(player);
		WebSymbioteState after = state.withSatiety(Math.max(0, state.satiety() - LAVA_SATIETY_COST))
			.withStress(Math.min(WebSymbiote.STRESS_MAX, state.stress() + LAVA_STRESS));
		WebSymbiote.set(player, after);
		if (shore == null) {
			// no shore in reach: it can only throw you up and hope
			say(player, "lava_none");
			shove(player, new Vec3(0.0, LAVA_LIFT_MAX, 0.0));
			return after;
		}
		Vec3 to = Vec3.atBottomCenterOf(shore).subtract(player.position());
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		double lift = Math.min(LAVA_LIFT_MAX, LAVA_LIFT_BASE + LAVA_LIFT_PER_BLOCK * Math.max(0.0, to.y + 1.0));
		say(player, "lava");
		line(player, back(player), Vec3.atBottomCenterOf(shore).add(0.0, 0.5, 0.0));
		shove(player, flat.scale(LAVA_SHOVE).add(0.0, lift, 0.0));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.8F, 1.6F);
		return after;
	}

	// ---- 5. ledge pull (white strain) -------------------------------------------------------------------------------

	private static boolean ledgePull(ServerPlayer player, R r, Vec3 moved, long now) {
		if (now < r.ledgeReadyAt || !player.onGround() || player.isFallFlying() || player.isShiftKeyDown() || WebPower.swingingNow(player)
			|| WebPower.pulling(player) || WebSymbioteWings.out(player) || WebHanging.hanging(player) || player.isPassenger()) {
			return false;
		}
		Vec3 dir = new Vec3(moved.x, 0.0, moved.z);
		double speed = dir.length();
		if (speed < LEDGE_MIN_MOVE) {
			return false;
		}
		dir = dir.normalize();
		ServerLevel level = player.level();
		// it looks about as far ahead as you'll walk before it looks again
		double look = Math.max(1.0, speed * 1.2 + 0.6);
		int deadly = -1;
		for (double d = 0.9; d <= look + 0.01; d += 0.5) {
			Vec3 at = player.position().add(dir.scale(d));
			if (hasFloor(level, at, player.getY())) {
				continue;
			}
			if (deadlyDrop(player, at)) {
				deadly = 1;
			}
			break;
		}
		if (deadly < 0) {
			return false;
		}
		// a running jump to somewhere solid is on purpose
		if (player.isSprinting()) {
			for (double d = look; d <= LEDGE_JUMP_LANDING; d += 0.5) {
				Vec3 at = player.position().add(dir.scale(d));
				if (hasFloor(level, at, player.getY()) || hasFloor(level, at, player.getY() + 1.0) || hasFloor(level, at, player.getY() - 1.0)) {
					return false;
				}
			}
		}
		r.ledgeReadyAt = now + LEDGE_COOLDOWN;
		out(player);
		say(player, "ledge");
		Vec3 anchor = player.position().subtract(dir.scale(2.4)).add(0.0, 1.0, 0.0);
		line(player, back(player), anchor);
		shove(player, dir.scale(-LEDGE_PULL).add(0.0, LEDGE_LIFT, 0.0));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_STEP, SoundSource.PLAYERS, 0.9F, 0.7F);
		return true;
	}

	/** Solid ground within a step of {@code y} at the horizontal spot {@code at}. */
	private static boolean hasFloor(ServerLevel level, Vec3 at, double y) {
		BlockPos p = BlockPos.containing(at.x, y - 0.5, at.z);
		return !level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
	}

	/** A fall from here at {@code at} that would kill you: into the void, lava or fire, or far enough to (where it can't cushion it). */
	private static boolean deadlyDrop(ServerPlayer player, Vec3 at) {
		ServerLevel level = player.level();
		BlockPos p = BlockPos.containing(at.x, player.getY() - 1.0, at.z);
		for (int i = 0; i < LEDGE_SCAN; i++) {
			BlockPos q = p.below(i);
			if (q.getY() < level.getMinY()) {
				return true;
			}
			BlockState s = level.getBlockState(q);
			if (s.is(Blocks.LAVA) || s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS)) {
				return true;
			}
			if (!s.getFluidState().isEmpty()) {
				return false;
			}
			if (!s.getCollisionShape(level, q).isEmpty()) {
				return !WebSymbiote.takesFalls(player) && i + 1 - 3 >= player.getHealth();
			}
		}
		return true;
	}

	// ---- 6. items that save you, and 8. a meal ----------------------------------------------------------------------

	private static boolean saveWithItems(ServerPlayer player, R r, long now) {
		if (now < r.saveReadyAt || player.getHealth() > SAVE_HEALTH) {
			return false;
		}
		ServerLevel level = player.level();
		boolean hitLately = player.getLastDamageSource() != null || player.hurtTime > 0;
		boolean foe = !level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(SAVE_FOE_RANGE),
			m -> m.isAlive() && m.getTarget() == player).isEmpty();
		if (!hitLately && !foe) {
			return false;
		}
		int slot = -1;
		String act = null;
		boolean poisoned = player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WITHER);
		var inv = player.getInventory();
		if (poisoned) {
			slot = find(player, Items.MILK_BUCKET);
			act = "milk";
		}
		if (slot < 0) {
			slot = find(player, Items.ENCHANTED_GOLDEN_APPLE);
			act = "apple";
			if (slot < 0) {
				slot = find(player, Items.GOLDEN_APPLE);
			}
		}
		if (slot < 0 && player.getHealth() <= TOTEM_HEALTH) {
			slot = find(player, Items.TOTEM_OF_UNDYING);
			act = "totem";
		}
		if (slot < 0) {
			return false;
		}
		r.saveReadyAt = now + SAVE_COOLDOWN;
		ItemStack stack = inv.getItem(slot);
		out(player);
		line(player, back(player), player.getEyePosition().add(player.getLookAngle().scale(0.3)).subtract(0.0, 0.25, 0.0));
		say(player, act);
		if ("totem".equals(act)) {
			stack.shrink(1);
			player.setHealth(1.0F);
			player.removeAllEffects();
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
			level.broadcastEntityEvent(player, (byte)35);
		} else {
			ItemStack result = stack.finishUsingItem(level, player);
			if (result != stack) {
				inv.setItem(slot, result);
			}
		}
		return true;
	}

	private static int find(ServerPlayer player, net.minecraft.world.item.Item item) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean feedYou(ServerPlayer player, R r, long now) {
		if (now < r.feedReadyAt || player.getFoodData().getFoodLevel() > FEED_BELOW || player.isUsingItem()) {
			return false;
		}
		var inv = player.getInventory();
		int best = -1;
		int nutrition = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food == null || WebSymbioteBody.isMeat(stack) || spared(stack) || food.nutrition() <= nutrition) {
				continue;
			}
			best = i;
			nutrition = food.nutrition();
		}
		if (best < 0) {
			return false;
		}
		r.feedReadyAt = now + FEED_GAP;
		ServerLevel level = player.level();
		ItemStack stack = inv.getItem(best);
		ItemStack result = stack.finishUsingItem(level, player);
		if (result != stack) {
			inv.setItem(best, result);
		}
		out(player);
		line(player, back(player), player.getEyePosition().add(player.getLookAngle().scale(0.3)).subtract(0.0, 0.25, 0.0));
		say(player, "feed");
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.7F, 1.0F);
		return true;
	}

	/** Foods it keeps back: the ones that hurt, and the apples it saves for the brink. */
	private static boolean spared(ItemStack stack) {
		return stack.is(Items.CHORUS_FRUIT) || stack.is(Items.POISONOUS_POTATO) || stack.is(Items.PUFFERFISH) || stack.is(Items.SPIDER_EYE)
			|| stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.SUSPICIOUS_STEW);
	}
}
