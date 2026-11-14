package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Werewolf;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "werewolves can also eat the corpses and parts." The beast eats the dead whole: any body with flesh on it
 * (Jaymo's Kitchen's corpses, whoever they were and whoever killed them, players' too, rotten or fresh; not bones or
 * iron) and any severed head or limb (Jaymo's Powers' parts). A werewolf's beast with nothing to hunt
 * smells one out and tears into it where it lies ({@link DevourGoal}); a player wearing the beast, great or lean, does
 * it themselves: right-click the body or the part (sneak + right-click still carries it), a bite each time, each
 * filling their belly and closing their wounds. A body eaten is gone, and no longer one of the dead left lying for the
 * Unburied ({@link com.jaymoskingdoms.blossomland.NightThings}) to rise from.
 *
 * <p>Kitchen and Powers are reached by name ({@code Scavengers.devourable/devourBite}, {@code SeveredPart.edible/bite}),
 * so either may be missing: then there's simply nothing of theirs to eat.
 */
public final class Devouring {
	/** How far the beast smells the dead (blocks), and how far above or below it it'll go for them. */
	static final double SMELL = 20.0, SMELL_UP = 6.0;
	/** One look in this many ticks (while it has nothing to hunt). */
	static final int LOOK_ODDS = 8;
	/** Ticks between the beast's bites, and how hard it lunges in at each. */
	static final int BITE_EVERY = 10;
	static final double LUNGE = 0.15;
	/** Bites to eat a body: this many, plus this many per block of the body's width (a scavenger needs 4 + 4). */
	static final int BODY_BITES = 3, BODY_BITES_PER_WIDTH = 2;
	/** The beast's health back per bite, and bodies until it's gorged (then it rests from eating this long, ticks). */
	static final float BEAST_HEAL = 3.0F;
	static final int GORGED_AFTER = 2, GORGED_TICKS = 3600;
	/** Ticks before it gives up on something it can't reach. */
	static final int GIVE_UP = 300;
	/** A player's beast: what each bite gives (food, saturation, health), and the least time between bites (ticks). */
	static final int PLAYER_FOOD = 2;
	static final float PLAYER_SATURATION = 0.4F, PLAYER_HEAL = 1.5F;
	static final int PLAYER_BITE_EVERY = 8;
	/** How far a player's beast reaches to bite. */
	static final double PLAYER_REACH = 3.0;

	/** Runs before Jaymo's Powers' carrying, so a beast's right-click eats instead of picking up. */
	private static final Identifier EARLY = JaymosKingdoms.id("devouring");

	private static boolean looked;
	private static @Nullable MethodHandle devourable, devourBite, edible, bite;
	private static @Nullable Class<?> partClass;

	/** A player's last bite (game time). */
	private static final Map<UUID, Long> LAST_BITE = new HashMap<>();

	private Devouring() {
	}

	static void register() {
		UseEntityCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(EARLY, (player, level, hand, target, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server) || player.isShiftKeyDown()
				|| !WerewolfPlayers.beastUp(sp) || !(isBody(target) || isPart(target))) {
				return InteractionResult.PASS;
			}
			if (hand == InteractionHand.MAIN_HAND && sp.distanceToSqr(target) <= PLAYER_REACH * PLAYER_REACH) {
				playerBite(server, sp, target);
			}
			// either hand: the claws don't pick it up
			return InteractionResult.SUCCESS;
		});
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			try {
				Class<?> scavengers = Class.forName("com.jaymoskitchen.corpse.Scavengers");
				devourable = lookup.findStatic(scavengers, "devourable", MethodType.methodType(boolean.class, Entity.class));
				devourBite = lookup.findStatic(scavengers, "devourBite",
					MethodType.methodType(boolean.class, ServerLevel.class, Entity.class, Entity.class, int.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for werewolves to eat the dead: {}", e.toString());
				devourable = null;
				devourBite = null;
			}
		}
		if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			try {
				partClass = Class.forName("com.jaymospowers.core.SeveredPart");
				edible = lookup.findVirtual(partClass, "edible", MethodType.methodType(boolean.class));
				bite = lookup.findVirtual(partClass, "bite", MethodType.methodType(boolean.class, ServerLevel.class, Entity.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Powers is too old for werewolves to eat severed parts: {}", e.toString());
				partClass = null;
				edible = null;
				bite = null;
			}
		}
	}

	/** Whether {@code entity} is a body the beast may eat. */
	static boolean isBody(Entity entity) {
		look();
		try {
			return devourable != null && (boolean) devourable.invokeExact(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/** Whether {@code entity} is a severed part the beast may eat. */
	static boolean isPart(Entity entity) {
		look();
		try {
			return partClass != null && edible != null && partClass.isInstance(entity) && (boolean) edible.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/** Whether there's anything at all to eat (Kitchen or Powers is here). */
	static boolean anything() {
		look();
		return devourable != null || edible != null;
	}

	/** One bite into {@code food} (a body or a part) by {@code eater}. True when that was the last of it. */
	static boolean bite(ServerLevel level, Entity eater, Entity food) {
		try {
			if (isBody(food) && devourBite != null) {
				int needed = BODY_BITES + Mth.floor(food.getBbWidth() * BODY_BITES_PER_WIDTH);
				return (boolean) devourBite.invokeExact(level, eater, food, needed);
			}
			if (isPart(food) && bite != null) {
				return (boolean) bite.invoke(food, level, eater);
			}
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("A werewolf couldn't eat {}: {}", food, e.toString());
		}
		return false;
	}

	/** A player's beast bites into {@code food}: a swipe and a mouthful, food and health for it. */
	private static void playerBite(ServerLevel level, ServerPlayer player, Entity food) {
		long now = level.getGameTime();
		Long last = LAST_BITE.get(player.getUUID());
		if (last != null && now - last < PLAYER_BITE_EVERY && now >= last) {
			return;
		}
		LAST_BITE.put(player.getUUID(), now);
		WerewolfPlayers.clawAt(player);
		boolean gone = bite(level, player, food);
		player.getFoodData().eat(PLAYER_FOOD, PLAYER_SATURATION);
		player.heal(PLAYER_HEAL);
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 0.55F + player.getRandom().nextFloat() * 0.15F);
		if (gone) {
			level.playSound(null, player.blockPosition(), SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value(), SoundSource.PLAYERS, 0.7F, 0.6F);
		}
	}

	/** The beast with nothing to hunt goes to the nearest of the dead (a body or a part) and eats it. */
	public static final class DevourGoal extends Goal {
		private final Werewolf wolf;
		private @Nullable Entity food;
		private int ticks, giveUp, eaten;
		private long gorgedUntil;

		public DevourGoal(Werewolf wolf) {
			this.wolf = wolf;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		private boolean busy() {
			return wolf.isHuman() || wolf.changing() || wolf.isNoAi() || wolf.getTarget() != null || wolf.isPassenger() || wolf.isLeashed();
		}

		@Override
		public boolean canUse() {
			if (busy() || wolf.level().getGameTime() < gorgedUntil || wolf.getRandom().nextInt(LOOK_ODDS) != 0 || !anything()) {
				return false;
			}
			List<Entity> near = wolf.level().getEntities(wolf, wolf.getBoundingBox().inflate(SMELL, SMELL_UP, SMELL),
				e -> isBody(e) || isPart(e));
			food = null;
			double best = Double.MAX_VALUE;
			for (Entity e : near) {
				double distance = e.distanceToSqr(wolf);
				if (distance < best) {
					best = distance;
					food = e;
				}
			}
			return food != null;
		}

		@Override
		public void start() {
			ticks = 0;
			giveUp = GIVE_UP;
			if (food != null) {
				wolf.getNavigation().moveTo(food.getX(), food.getY(), food.getZ(), 1.2);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return food != null && (isBody(food) || isPart(food)) && giveUp > 0 && !busy();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (food == null || !(wolf.level() instanceof ServerLevel level)) {
				return;
			}
			double r = wolf.getBbWidth() / 2.0 + food.getBbWidth() / 2.0 + 0.9;
			if (wolf.distanceToSqr(food) > r * r) {
				giveUp--;
				ticks = 0;
				wolf.getLookControl().setLookAt(food, 30.0F, 30.0F);
				if (wolf.getNavigation().isDone()) {
					wolf.getNavigation().moveTo(food.getX(), food.getY(), food.getZ(), 1.2);
				}
				return;
			}
			wolf.getNavigation().stop();
			ticks++;
			// hunched over it, head down, worrying it
			Vec3 to = food.position().subtract(wolf.position()).multiply(1.0, 0.0, 1.0);
			Vec3 side = to.lengthSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(-to.z, 0.0, to.x).normalize();
			double shake = Mth.sin(ticks * 1.5F) * 0.6;
			wolf.getLookControl().setLookAt(food.getX() + side.x * shake, food.getY() - 0.3, food.getZ() + side.z * shake, 90.0F, 90.0F);
			int phase = ticks % BITE_EVERY;
			if (phase == 1) {
				if (to.lengthSqr() > 1.0E-4) {
					wolf.setDeltaMovement(wolf.getDeltaMovement().add(to.normalize().scale(LUNGE)));
				}
				wolf.claw();
			} else if (phase == 4) {
				if (wolf.getRandom().nextInt(3) == 0) {
					wolf.playAmbientSound();
				}
				wolf.heal(BEAST_HEAL);
				if (bite(level, wolf, food)) {
					if (++eaten >= GORGED_AFTER) {
						eaten = 0;
						gorgedUntil = level.getGameTime() + GORGED_TICKS;
					}
					food = null;
				}
			}
		}

		@Override
		public void stop() {
			food = null;
			wolf.getNavigation().stop();
		}
	}
}
