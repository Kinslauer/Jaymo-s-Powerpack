package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import com.jaymospowers.core.fx.MagicFx;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "Arsenal can pull from literally anywhere, that is why he can grab guns from the future, items from players
 * inventories, or even hazardous material that harms himself, until he masters the power he can't command it at will."
 * <p>
 * Until the power is mastered (ascended, when the Armory's pin lets the holder choose), a Reach sometimes closes on
 * something that isn't a weapon from the pocket:
 * <ul>
 * <li><b>Someone's pack</b> ({@link #PICK_CHANCE}): a weapon, tool or piece of armour out of a nearby player's
 * inventory. The holder gets it as a summon like any other; its owner gets the real one back when that summon's time is
 * up (online or not: it waits for them), whatever happened to the summon meanwhile. Only damageable things are taken, so
 * nothing borrowed can be placed, eaten or poured out and kept.</li>
 * <li><b>A hazard</b> ({@link #HAZARD_CHANCE}): something that hurts the holder (embers, a pufferfish, a wither rose,
 * a cactus, powder snow, a lit charge that goes off in the hand). Nothing is kept.</li>
 * </ul>
 * The future handguns are the third kind ({@link ArsenalFutureGun}). Once ascended, none of this happens by accident,
 * and the holder can choose (Andy: "they can choose to grab a random item, by looking at them and holding reach, once
 * mastered"): look at a player within {@link #GRAB_RANGE} and hold R for {@link #GRAB_HOLD_TICKS}, and the Reach goes
 * into their pack for a random one of their things (the same return). Let go sooner and it's an ordinary Reach.
 */
public final class ArsenalAnywhere {
	/** The chance a Reach goes into a nearby player's pack, when someone is near (1 in 25). */
	static final float PICK_CHANCE = 1.0F / 25.0F;
	/** How near (blocks) that player has to be. */
	static final double PICK_RANGE = 24.0;
	/** The chance a Reach closes on something that hurts (1 in 30). */
	static final float HAZARD_CHANCE = 1.0F / 30.0F;
	/** A hazard's harm. */
	static final float EMBER_DAMAGE = 2.0F, CACTUS_DAMAGE = 3.0F;
	static final int EMBER_FIRE_SECONDS = 4, POISON_TICKS = 160, NAUSEA_TICKS = 120, WITHER_TICKS = 120, FROZEN_EXTRA_TICKS = 140;
	static final float CHARGE_POWER = 1.5F;
	/** A mastered grab: how far the player you look at may be, and how long R is held. */
	static final double GRAB_RANGE = 16.0;
	static final int GRAB_HOLD_TICKS = 20;

	/** A grab being held: whose pack, and since when. Server memory only. */
	private record Grab(java.util.UUID target, long start) {
	}

	private static final java.util.Map<java.util.UUID, Grab> GRABS = new java.util.HashMap<>();

	/** Something that hurts, and the thing seen coming out of the rift with it. */
	enum Hazard {
		EMBER(Items.MAGMA_BLOCK), PUFFER(Items.PUFFERFISH), WITHER(Items.WITHER_ROSE), CACTUS(Items.CACTUS),
		FROST(Items.POWDER_SNOW_BUCKET), CHARGE(Items.TNT);

		final Item shown;

		Hazard(Item shown) {
			this.shown = shown;
		}

		String key() {
			return "message.jaymospowers.arsenal.hazard." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** A thing taken from a player's pack, waiting to go home at game time {@code due}. */
	record Lent(ItemStack stack, long due) {
		static final Codec<Lent> CODEC = RecordCodecBuilder.create(i -> i.group(
				ItemStack.CODEC.fieldOf("stack").forGetter(Lent::stack),
				Codec.LONG.fieldOf("due").forGetter(Lent::due))
			.apply(i, Lent::new));
	}

	/** On the owner: what an Arsenal took from their pack and still owes them. Saved, kept through death. */
	public static final AttachmentType<List<Lent>> LENT = AttachmentRegistry.create(JaymosPowers.id("arsenal_lent"),
		builder -> builder.persistent(Lent.CODEC.listOf()).copyOnDeath());

	/** What a wild Reach brings: a hazard, or a stack out of someone's pack (already taken from them). */
	record Wild(@Nullable Hazard hazard, ItemStack taken, @Nullable ServerPlayer from) {
		ItemStack shown() {
			return hazard != null ? new ItemStack(hazard.shown) : taken.copy();
		}
	}

	private ArsenalAnywhere() {
	}

	static void init() {
		// (the attachment registers itself when this class loads)
	}

	/** Not yet mastered: the rift still picks for you. */
	static boolean untamed(ServerPlayer player) {
		return !Fate.isAscended(player);
	}

	/**
	 * Rolled when a Reach opens: null nearly always. A pack pick takes the stack from its owner now (so everyone sees
	 * the real thing come out) and books its return for when the summon would expire, {@code life} ticks after the pull.
	 */
	static @Nullable Wild roll(ServerPlayer player, ServerLevel level, RandomSource random, int life) {
		if (!untamed(player)) {
			return null;
		}
		float r = random.nextFloat();
		if (r < HAZARD_CHANCE) {
			Hazard[] all = Hazard.values();
			return new Wild(all[random.nextInt(all.length)], ItemStack.EMPTY, null);
		}
		if (r < HAZARD_CHANCE + PICK_CHANCE) {
			return pick(player, level, random, life);
		}
		return null;
	}

	private static @Nullable Wild pick(ServerPlayer player, ServerLevel level, RandomSource random, int life) {
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer other : level.players()) {
			if (other != player && other.isAlive() && !other.isSpectator() && other.distanceToSqr(player) <= PICK_RANGE * PICK_RANGE) {
				near.add(other);
			}
		}
		if (near.isEmpty()) {
			return null;
		}
		return take(player, near.get(random.nextInt(near.size())), level, random, life);
	}

	/** Takes a random takeable thing out of {@code from}'s pack and books its return; null if there's nothing to take. */
	static @Nullable Wild take(ServerPlayer player, ServerPlayer from, ServerLevel level, RandomSource random, int life) {
		Inventory inventory = from.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (slot != inventory.getSelectedSlot() && takeable(stack)) {
				slots.add(slot);
			}
		}
		if (slots.isEmpty()) {
			return null;
		}
		int slot = slots.get(random.nextInt(slots.size()));
		ItemStack taken = inventory.getItem(slot).copy();
		inventory.setItem(slot, ItemStack.EMPTY);
		List<Lent> owed = new ArrayList<>(from.getAttachedOrElse(LENT, List.of()));
		owed.add(new Lent(taken.copy(), level.getGameTime() + ArsenalReach.PULL_END + life));
		from.setAttached(LENT, owed);
		from.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.pack_taken", taken.getHoverName())
			.withStyle(ChatFormatting.DARK_PURPLE));
		level.playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.3F, 1.6F);
		return new Wild(null, taken, from);
	}

	/** Weapons, tools and armour only: nothing that could be placed, eaten or poured out and so kept twice. */
	private static boolean takeable(ItemStack stack) {
		return !stack.isEmpty() && stack.isDamageableItem() && stack.getCount() == 1 && !Summoned.is(stack) && !ArsenalRelics.is(stack);
	}

	/** R pressed: a mastered holder looking at another player starts a grab. True when it did (no Reach yet). */
	static boolean startGrab(ServerPlayer player) {
		if (untamed(player) || player.isShiftKeyDown()) {
			return false;
		}
		ServerPlayer target = lookedAt(player);
		if (target == null) {
			return false;
		}
		GRABS.put(player.getUUID(), new Grab(target.getUUID(), player.level().getGameTime()));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.grab_holding", target.getDisplayName())
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.1F, 2.0F);
		return true;
	}

	/** R let go: true when a grab was still being held (the caller makes it an ordinary Reach). */
	static boolean release(ServerPlayer player) {
		return GRABS.remove(player.getUUID()) != null;
	}

	/**
	 * Every tick: the player whose pack a finished grab goes into, or null. Looking away (or them going) ends the grab
	 * with nothing; the R release that follows then does nothing.
	 */
	static @Nullable ServerPlayer tickGrab(ServerPlayer player) {
		Grab grab = GRABS.get(player.getUUID());
		if (grab == null) {
			return null;
		}
		ServerLevel level = player.level();
		ServerPlayer target = lookedAt(player);
		if (!player.isAlive() || target == null || !target.getUUID().equals(grab.target())) {
			GRABS.remove(player.getUUID());
			return null;
		}
		long held = level.getGameTime() - grab.start();
		if (held % 4 == 0) {
			level.sendParticles(MagicFx.VOID, target.getX(), target.getY() + 0.9, target.getZ(), 4, 0.25, 0.3, 0.25, 0.3);
		}
		if (held < GRAB_HOLD_TICKS) {
			return null;
		}
		GRABS.remove(player.getUUID());
		return target;
	}

	/** The other player in the holder's sights within {@link #GRAB_RANGE} (walls in the way stop it), or null. */
	private static @Nullable ServerPlayer lookedAt(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(GRAB_RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB search = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, search,
			e -> e instanceof ServerPlayer other && other != player && other.isAlive() && !other.isSpectator(), eye.distanceToSqr(end));
		return hit != null && hit.getEntity() instanceof ServerPlayer other ? other : null;
	}

	/** The summon the holder gets for a pack pick, and the line they see. */
	static List<ItemStack> stacksFor(Wild wild) {
		return new ArrayList<>(List.of(wild.taken().copy()));
	}

	static void announcePick(ServerPlayer player, Wild wild) {
		if (wild.from() != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.pack_pulled", wild.taken().getHoverName(),
				wild.from().getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	/** The hazard reaches the holder as the hand comes back out. */
	static void strike(ServerPlayer player, Vec3 rift, Hazard hazard) {
		ServerLevel level = player.level();
		switch (hazard) {
			case EMBER -> {
				player.igniteForSeconds(EMBER_FIRE_SECONDS);
				player.hurtServer(level, level.damageSources().hotFloor(), EMBER_DAMAGE);
				level.sendParticles(ParticleTypes.FLAME, rift.x, rift.y, rift.z, 16, 0.2, 0.2, 0.2, 0.03);
				level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
			}
			case PUFFER -> {
				player.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0));
				level.sendParticles(ParticleTypes.SPLASH, rift.x, rift.y, rift.z, 20, 0.2, 0.2, 0.2, 0.1);
				level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.PUFFER_FISH_STING, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			case WITHER -> {
				player.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0));
				level.sendParticles(ParticleTypes.SMOKE, rift.x, rift.y, rift.z, 14, 0.15, 0.15, 0.15, 0.02);
				level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.3F, 1.6F);
			}
			case CACTUS -> {
				player.hurtServer(level, level.damageSources().cactus(), CACTUS_DAMAGE);
				level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
			case FROST -> {
				player.setTicksFrozen(player.getTicksRequiredToFreeze() + FROZEN_EXTRA_TICKS);
				level.sendParticles(ParticleTypes.SNOWFLAKE, rift.x, rift.y, rift.z, 24, 0.25, 0.25, 0.25, 0.05);
				level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
			case CHARGE -> {
				level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.0F, 1.4F);
				// it goes off in the hand: hurts the holder (and anyone close), breaks nothing
				level.explode(null, rift.x, rift.y, rift.z, CHARGE_POWER, Level.ExplosionInteraction.NONE);
			}
		}
		player.sendOverlayMessage(Component.translatable(hazard.key()).withStyle(ChatFormatting.RED));
	}

	/** Once a second for every player online: give back what's due from their pack. */
	static void tickLender(ServerPlayer player, long now) {
		List<Lent> owed = player.getAttached(LENT);
		if (owed == null || owed.isEmpty()) {
			return;
		}
		List<Lent> still = new ArrayList<>();
		ItemStack first = ItemStack.EMPTY;
		for (Lent lent : owed) {
			if (now < lent.due()) {
				still.add(lent);
				continue;
			}
			ItemStack back = lent.stack().copy();
			if (first.isEmpty()) {
				first = back.copy();
			}
			player.getInventory().placeItemBackInInventory(back, Prediction.SERVER_ONLY);
		}
		if (first.isEmpty()) {
			return;
		}
		if (still.isEmpty()) {
			player.removeAttached(LENT);
		} else {
			player.setAttached(LENT, still);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.pack_returned", first.getHoverName())
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		Summoned.dissolveEffects(player.level(), player.position().add(0, 1.0, 0));
	}
}
