package com.jaymospowers.fishing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.61, Andy: "i need there to be real fish, as in if you see them in the water, you can cast and potentially get the
 * bite. but they have to ACTUALLY be there.") Every bite is a real fish. A baited hook draws a fish swimming within
 * {@link #DRAW_RANGE}: it turns and swims to the hook, nibbles it a few times (the bobber dips), then takes it, and the
 * fight is with that same fish. Which fish you get is whichever live there; a fish landed is gone, so a spot is fished out
 * when its fish are. Tuna and sharks only go for a metal rod's hook.
 *
 * <p>Track C: each fish's own hours, season and hunger decide how keen it is ({@link Species#activity},
 * {@link WildFish#appetite}): fish feed harder where predators are hunting ({@link FishSchools#frenzy}) and under the ice
 * ({@link IceFishing}); and the bait has a depth ({@link #baitAt}): a float's bait hangs just under the top, a ledger
 * rod's lies on the bottom, and a lure is wherever it has sunk to ({@link LureDepth}), and only fish within
 * {@link #DEPTH_REACH} of it come. A crab or crayfish that finds bait on the bottom picks it off the hook.
 *
 * <p>A line need not be a hook: a rod in a rod pod ({@link RodPod}) fishes the same way through {@link #step}.
 */
public final class FishBites {
	/** A baited hook draws fish this far off (blocks). */
	static final double DRAW_RANGE = 10.0;
	/** Each check (4 a second), chance a fish in range notices the bait (before time and weather). */
	static final float NOTICE_CHANCE = 0.04F;
	/** A fish this close to the hook is at it: it nibbles, then takes it. */
	static final double AT_HOOK = 1.2;
	/** Nibbles before a fish takes the hook, at least and up to this many more. */
	static final int NIBBLES_LEAST = 1, NIBBLES_MORE = 3;
	/** Each check at the hook, chance of a nibble. */
	static final float NIBBLE_CHANCE = 0.35F;
	/** A fish that hasn't reached the hook in this many ticks loses interest. */
	static final int GIVE_UP_TICKS = 400;
	/** How fast a drawn fish swims to the hook (navigation speed, and the nudge when it can't path). */
	static final double SWIM_SPEED = 1.2, NUDGE = 0.04;
	/** How far the bobber dips on a nibble and on the take. */
	static final double NIBBLE_DIP = 0.12, TAKE_DIP = 0.3;
	/** (Track C) A float's bait hangs this far under the bobber; fish further above or below the bait than this don't see it. */
	static final double FLOAT_DEPTH = 0.8, DEPTH_REACH = 3.5;
	/** (Track C) A crab or crayfish this close to bait on the bottom picks it off, this often each check. */
	static final double PICK_RANGE = 2.5;
	static final float PICK_CHANCE = 0.03F;

	/** A fish drawn to a hook. */
	private static final class Drawn {
		final UUID fish;
		final long since;
		int nibbles;
		final int takesAfter;

		Drawn(UUID fish, long since, int takesAfter) {
			this.fish = fish;
			this.since = since;
			this.takesAfter = takesAfter;
		}
	}

	/** A line in the water: whose (a hook's uuid, or a rod pod's slot), where its bait is, on what rod, with what on it. */
	public record Line(Object key, ServerLevel level, Vec3 bait, Tackle.Rod rod, Item onHook, boolean trolling, boolean bottom) {
	}

	/** What a line's fish did this check. */
	public enum Step { NOTHING, NIBBLE, TAKEN, PICKED }

	/** Line key → the fish coming to it. */
	private static final Map<Object, Drawn> DRAWN = new HashMap<>();
	/** The fish that just took each line (read right after {@link Step#TAKEN}). */
	private static final Map<Object, LivingEntity> TOOK = new HashMap<>();

	private FishBites() {
	}

	/** The kind ({@code FishingReal.FISH_*}, or a {@link Species} ordinal) of fish {@code e} is, or -1 if it's no fish for a hook. */
	public static int kindOf(LivingEntity e) {
		if (e instanceof WildFish w) {
			return w.species().kind();
		}
		if (e instanceof Critter c) {
			return c.species().kind();
		}
		if (e instanceof Skipjack) {
			return FishingReal.FISH_TUNA;
		}
		if (e instanceof Cod) {
			return FishingReal.FISH_COD;
		}
		if (e instanceof Salmon) {
			return FishingReal.FISH_SALMON;
		}
		if (e instanceof Pufferfish) {
			return FishingReal.FISH_PUFFERFISH;
		}
		if (e instanceof TropicalFish) {
			return FishingReal.FISH_TROPICAL;
		}
		if (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().contains("shark")) {
			return FishingReal.FISH_SHARK;
		}
		// (Track C) another mod's fish counts too
		Species other = FishCompat.speciesOf(e);
		return other == null ? -1 : other.kind();
	}

	/** Where the bait on {@code hook} is: under the float, or at the hook itself when it lies on the bottom or a lure sank it. */
	static Vec3 baitAt(FishingHook hook, Tackle.Rod rod, @Nullable Item onHook) {
		boolean sunk = rod.bottom() || Tackle.isLure(onHook) || LureDepth.depth(hook) > 0.0F;
		return sunk ? hook.position() : hook.position().subtract(0.0, FLOAT_DEPTH, 0.0);
	}

	/** Called every tick for a hook in water whose angler isn't fighting a fish. */
	public static void tick(ServerLevel level, FishingHook hook, ServerPlayer player) {
		if (level.getGameTime() % 4L != 0L) {
			return;
		}
		Item onHook = HookBait.bait(player);
		if (onHook == null) {
			DRAWN.remove(hook.getUUID());
			return;
		}
		if (onHook == FishingGear.FISHING_MAGNET) {
			// (0.21.64) a magnet draws no fish; it snags scrap now and then
			FishingGear.magnet(level, hook, player);
			return;
		}
		// (Track C) a legend may come to see who's fishing its water
		FishLegends.stir(level, hook.blockPosition(), player);
		Tackle.Rod rod = Tackle.rodOf(player);
		Line line = new Line(hook.getUUID(), level, baitAt(hook, rod, onHook), rod, onHook, FishingGear.trolling(player),
			rod.bottom() || LureDepth.onBottom(hook));
		switch (step(line)) {
			case NIBBLE -> dip(level, hook, NIBBLE_DIP, 0.15F, 1.6F);
			case TAKEN -> {
				LivingEntity fish = TOOK.remove(line.key());
				dip(level, hook, TAKE_DIP, 0.5F, 1.0F);
				level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), hook.getY(), hook.getZ(), 8, 0.2, 0.1, 0.2, 0.1);
				FishingReal.baited(player, onHook);
				taken(player);
				if (fish != null) {
					// (Track A) the strike window: use in time hooks it and starts the fight (FishBite)
					FishingReal.fishAtHook(player, kindOf(fish), fish, hook);
				}
			}
			case PICKED -> {
				HookBait.eaten(player);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.bait_picked").withStyle(ChatFormatting.GRAY));
			}
			default -> {
			}
		}
	}

	/**
	 * One check (call every 4 ticks) of a line: a fish may notice the bait, swim to it, nibble, and take it. On
	 * {@link Step#TAKEN} the fish is in {@link #took}. The caller eats the bait and strikes (or sounds the alarm).
	 */
	public static Step step(Line line) {
		ServerLevel level = line.level();
		Drawn drawn = DRAWN.get(line.key());
		if (drawn == null) {
			if (line.bottom() && picked(level, line.bait())) {
				return Step.PICKED;
			}
			LivingEntity fish = notice(line);
			if (fish != null) {
				long now = level.getGameTime();
				DRAWN.values().removeIf(d -> now - d.since > GIVE_UP_TICKS);
				DRAWN.put(line.key(), new Drawn(fish.getUUID(), now, NIBBLES_LEAST + level.getRandom().nextInt(NIBBLES_MORE + 1)));
			}
			return Step.NOTHING;
		}
		LivingEntity fish = level.getEntity(drawn.fish) instanceof LivingEntity e ? e : null;
		if (fish == null || !fish.isAlive() || !fish.isInWater() || level.getGameTime() - drawn.since > GIVE_UP_TICKS
			|| fish.position().distanceTo(line.bait()) > DRAW_RANGE * 1.5) {
			DRAWN.remove(line.key());
			return Step.NOTHING;
		}
		if (fish.position().distanceTo(line.bait()) > AT_HOOK) {
			swimTo(fish, line.bait());
			return Step.NOTHING;
		}
		if (level.getRandom().nextFloat() >= NIBBLE_CHANCE) {
			return Step.NOTHING;
		}
		if (drawn.nibbles < drawn.takesAfter) {
			drawn.nibbles++;
			return Step.NIBBLE;
		}
		DRAWN.remove(line.key());
		TOOK.put(line.key(), fish);
		return Step.TAKEN;
	}

	/** The fish that just took {@code key}'s line (once). */
	public static @Nullable LivingEntity took(Object key) {
		return TOOK.remove(key);
	}

	/** The hook was pulled out or broke: whatever fish was coming forgets it. */
	public static void forget(FishingHook hook) {
		DRAWN.remove(hook.getUUID());
	}

	/** A line (a rod pod's) is gone. */
	public static void forget(Object key) {
		DRAWN.remove(key);
		TOOK.remove(key);
	}

	/**
	 * A fish in range that notices the bait this check, or null. (0.21.63, Track B) The rod decides which fish come at all
	 * and from how far ({@link Tackle.Rod}), the bait which of them it draws most ({@link Tackle.Bait}; a lure draws
	 * nothing on a rod that can't work it); groundbait and trolling make them keener and draw from farther. (Track C) Each
	 * fish's own hours, season and hunger make it keener or not, and fish only see bait near their own depth.
	 */
	private static @Nullable LivingEntity notice(Line line) {
		ServerLevel level = line.level();
		Tackle.Rod rod = line.rod();
		Tackle.Bait bait = Tackle.baitOf(line.onHook());
		if (bait == null || bait.lure() && !rod.lures) {
			return null;
		}
		BlockPos at = BlockPos.containing(line.bait());
		float keen = rod.keen * FishingGear.groundbait(level, at) * FishSchools.keen(level, at) * IceFishing.keen(level, at);
		if (line.trolling()) {
			keen *= line.onHook() == Tackle.TROLLING_SQUID ? FishingGear.TROLL_LURE_KEEN : FishingGear.TROLL_KEEN;
		}
		double range = DRAW_RANGE * rod.range * (line.trolling() ? FishingGear.TROLL_RANGE : 1.0);
		Vec3 b = line.bait();
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(b, b).inflate(range), e -> {
			int kind = kindOf(e);
			return kind >= 0 && e.isAlive() && e.isInWater() && !taken(e) && rod.takes(kind) && bait.draw(kind) > 0
				&& Math.abs(e.getY() - b.y) <= DEPTH_REACH;
		});
		if (near.isEmpty()) {
			return null;
		}
		// how keen they are, all told, decides whether one notices this check
		double total = 0.0, active = 0.0;
		double[] weight = new double[near.size()];
		for (int i = 0; i < near.size(); i++) {
			LivingEntity e = near.get(i);
			int kind = kindOf(e);
			Species sp = Species.of(kind);
			double mood = (sp == null ? 1.0 : sp.activity(level, e.blockPosition())) * (e instanceof WildFish w ? w.appetite() : 1.0);
			active += mood;
			weight[i] = bait.draw(kind) * mood / (1.0 + e.position().distanceTo(b));
			total += weight[i];
		}
		float mood = (float) Math.min(2.5, active / near.size());
		if (level.getRandom().nextFloat() >= NOTICE_CHANCE * keen * mood || total <= 0.0) {
			return null;
		}
		double roll = level.getRandom().nextDouble() * total;
		for (int i = 0; i < near.size(); i++) {
			roll -= weight[i];
			if (roll <= 0.0) {
				return near.get(i);
			}
		}
		return near.getLast();
	}

	/** A crab or crayfish on the bottom near the bait picks it off now and then. */
	private static boolean picked(ServerLevel level, Vec3 bait) {
		if (level.getRandom().nextFloat() >= PICK_CHANCE) {
			return false;
		}
		return !level.getEntitiesOfClass(Critter.class, new net.minecraft.world.phys.AABB(bait, bait).inflate(PICK_RANGE),
			c -> c.isAlive() && c.species().walker()).isEmpty();
	}

	/** A fish took {@code player}'s hook: natural bait is eaten; a lure stays on, unless it snaps off. */
	private static void taken(ServerPlayer player) {
		Item onHook = HookBait.bait(player);
		Tackle.Bait bait = Tackle.baitOf(onHook);
		if (bait != null && bait.lure()) {
			if (player.getRandom().nextFloat() < bait.snap()) {
				HookBait.eaten(player);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.fishing.lure_snapped",
					new net.minecraft.world.item.ItemStack(onHook).getHoverName()).withStyle(ChatFormatting.RED));
			}
			return;
		}
		HookBait.eaten(player);
	}

	/** {@code e} is already coming to a hook, or on a line. */
	static boolean taken(LivingEntity e) {
		for (Drawn d : DRAWN.values()) {
			if (d.fish.equals(e.getUUID())) {
				return true;
			}
		}
		return FishingReal.onLine(e);
	}

	static void swimTo(LivingEntity fish, Vec3 to) {
		if (fish instanceof Mob mob && !mob.isNoAi()) {
			mob.getNavigation().moveTo(to.x, to.y - 0.3, to.z, SWIM_SPEED);
		}
		Vec3 d = new Vec3(to.x - fish.getX(), to.y - 0.3 - fish.getY(), to.z - fish.getZ());
		if (d.lengthSqr() > 1.0E-4) {
			fish.setDeltaMovement(fish.getDeltaMovement().add(d.normalize().scale(NUDGE)));
			fish.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, to);
		}
	}

	private static void dip(ServerLevel level, FishingHook hook, double depth, float volume, float pitch) {
		hook.setDeltaMovement(hook.getDeltaMovement().add(0.0, -depth, 0.0));
		level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY(), hook.getZ(), 4, 0.1, 0.0, 0.1, 0.0);
		level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, volume, pitch);
	}

	/** Tell an angler with a bare hook in the water, now and then, that nothing will come to it. */
	public static void bare(FishingHook hook, ServerPlayer player) {
		if (hook.tickCount % BARE_EVERY == 0 && HookBait.bait(player) == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.bare_hook").withStyle(ChatFormatting.GRAY));
		}
	}

	/** How often (ticks) a bare hook says it wants bait. */
	static final int BARE_EVERY = 200;
}
