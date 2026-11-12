package com.jaymoskingdoms.thirst;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.Parties;
import com.jaymoskingdoms.Theft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The bite. A thirsty player who crouches behind an unaware person or animal, empty-handed, and uses it holds it and drinks
 * over {@link #BITE_TICKS}: a drink every {@link #DRINK_EVERY} ticks fills the gauge ({@link Thirst}) and costs the victim a
 * little blood, never the last of it (the victim is weakened, not necessarily killed). The hold ends if the drinker lets go
 * of crouching or the victim is torn away.
 * <ul>
 * <li>A villager or one of the realm's folk bitten is a crime if its people see ({@link Theft#bloodletting}), and the
 * villagers who see it run ({@link #FEAR_RANGE}). Villagers and folk are pickpockets' marks too: the bite takes the hand only
 * while the gauge is under {@link #PICK_BELOW}; fuller, the hand goes to the pocket as ever.</li>
 * <li>A player can be bitten only where player-versus-player is allowed, never a party member, and only when asleep, downed or
 * unaware behind. Biting one who is downed (dying) may pass the thirst on ({@link #PASS_CHANCE}).</li>
 * </ul>
 */
public final class ThirstBite {
	// ---- tuning ----
	/** How long the bite takes (ticks), and how often it drinks. */
	static final int BITE_TICKS = 40, DRINK_EVERY = 10;
	/** How near (blocks) the drinker must stay. */
	static final double REACH = 2.8, STAY = 3.6;
	/** Behind: the victim's facing and the line to the drinker must be at least this far apart (cosine). */
	static final double BEHIND_COS = -0.2;
	/** A villager or folk is only bitten while the gauge is under this (the pocket is the hand's other use). */
	static final int PICK_BELOW = 80;
	/** What each drink takes from the victim, and the health a bite never takes it under (a player; anything else). */
	static final float DRAIN = 1.0F, PLAYER_FLOOR = 6.0F, OTHER_FLOOR = 2.0F;
	/** What each drink gives the gauge: a person, an animal. */
	static final int FEED_PERSON = 9, FEED_BEAST = 6;
	/** How long the victim is weakened and slowed after it (ticks). */
	static final int WEAK_TICKS = 600, SLOW_TICKS = 200;
	/** How long before the same victim can be bitten again (ticks). */
	static final long AGAIN_TICKS = 2400L;
	/** How far (blocks) villagers who see it take fright, and how far they run. */
	static final double FEAR_RANGE = 16.0;
	static final int FEAR_RUN = 12;
	/** The chance a bite on a downed player passes the thirst on. */
	static final float PASS_CHANCE = 0.3F;

	/** One bite: the victim's id, when it began, and whether it already had no mind (to give it back). */
	private record Bite(int prey, long start, boolean hadNoAi) {
	}

	private static final Map<UUID, Bite> BITES = new HashMap<>();
	private static final Map<UUID, Long> BITTEN = new HashMap<>();

	private ThirstBite() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)
				|| !(entity instanceof LivingEntity prey) || !Thirst.thirsty(sp) || !sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty()
				|| !Thirst.form(sp).isEmpty() || BITES.containsKey(sp.getUUID()) || sp.isSpectator() || Thirst.gauge(sp) >= Thirst.FULL) {
				return InteractionResult.PASS;
			}
			if (pocket(prey) && Thirst.gauge(sp) >= PICK_BELOW || !bitable(sp, prey, level)) {
				return InteractionResult.PASS;
			}
			start(sp, level, prey);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(ThirstBite::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BITES.clear();
			BITTEN.clear();
		});
	}

	// ---- who may be bitten ----

	/** A villager or a realm's folk: the pickpocket's marks. */
	private static boolean pocket(LivingEntity prey) {
		return prey instanceof AbstractVillager || prey instanceof Folk;
	}

	private static boolean person(LivingEntity prey) {
		return prey instanceof AbstractVillager || prey instanceof Folk || prey instanceof Player;
	}

	private static boolean bitable(ServerPlayer sp, LivingEntity prey, ServerLevel level) {
		if (!prey.isAlive() || prey instanceof ArmorStand || prey.isInvulnerable() || BossBars.boss(prey) || prey.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)
			|| prey.isPassenger() || sp.distanceTo(prey) > REACH + prey.getBbWidth() * 0.5 || prey.getMaxHealth() < 4.0F) {
			return false;
		}
		Long again = BITTEN.get(prey.getUUID());
		if (again != null && again > level.getGameTime()) {
			return false;
		}
		boolean held = prey.isSleeping() || Downed.isDowned(prey) || prey instanceof Mob m && m.isNoAi();
		if (prey instanceof Player other) {
			if (!level.isPvpAllowed() || other.isCreative() || other.isSpectator() || Parties.allied(sp, (ServerPlayer)other)) {
				return false;
			}
			return held || behind(sp, prey);
		}
		boolean unaware = !(prey instanceof Mob m) || m.getTarget() != sp;
		return held || unaware && behind(sp, prey);
	}

	/** {@code sp} is behind {@code prey}: the way its body faces and the line to them are at least this far apart. */
	private static boolean behind(ServerPlayer sp, LivingEntity prey) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, prey.yBodyRot);
		Vec3 to = sp.position().subtract(prey.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 1.0E-6) {
			return true;
		}
		return facing.dot(to.normalize()) <= BEHIND_COS;
	}

	// ---- the hold ----

	private static void start(ServerPlayer sp, ServerLevel level, LivingEntity prey) {
		boolean hadNoAi = prey instanceof Mob m && m.isNoAi();
		if (prey instanceof Mob mob) {
			mob.setNoAi(true);
			mob.setTarget(null);
		}
		BITES.put(sp.getUUID(), new Bite(prey.getId(), level.getGameTime(), hadNoAi));
		BITTEN.put(prey.getUUID(), level.getGameTime() + AGAIN_TICKS);
		sp.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, prey.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 0.5F);
		if (person(prey) && !(prey instanceof Player)) {
			Theft.bloodletting(sp, level, prey.blockPosition());
			frighten(level, sp, prey);
		}
	}

	/** Villagers who see it run from it. */
	private static void frighten(ServerLevel level, ServerPlayer sp, LivingEntity except) {
		for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class, sp.getBoundingBox().inflate(FEAR_RANGE),
			v -> v != except && v.isAlive() && v.hasLineOfSight(sp))) {
			Vec3 away = DefaultRandomPos.getPosAway(villager, FEAR_RUN, 4, sp.position());
			if (away != null) {
				villager.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (BITES.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Bite> e : new ArrayList<>(BITES.entrySet())) {
			ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
			Bite bite = e.getValue();
			if (sp == null || !(sp.level() instanceof ServerLevel level) || !(level.getEntity(bite.prey()) instanceof LivingEntity prey)) {
				finish(sp, bite, null, false);
				BITES.remove(e.getKey());
				continue;
			}
			long age = level.getGameTime() - bite.start();
			if (!sp.isAlive() || !prey.isAlive() || !sp.isShiftKeyDown() || sp.distanceTo(prey) > STAY || !Thirst.form(sp).isEmpty() || !Thirst.thirsty(sp)) {
				finish(sp, bite, prey, false);
				BITES.remove(e.getKey());
				continue;
			}
			hold(prey);
			if (age > 0 && age % DRINK_EVERY == 0) {
				drink(sp, level, prey);
			}
			if (age >= BITE_TICKS) {
				finish(sp, bite, prey, true);
				BITES.remove(e.getKey());
			}
		}
	}

	/** The victim stays where it is. */
	private static void hold(LivingEntity prey) {
		if (prey instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		} else if (prey instanceof ServerPlayer p) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 6, true, false, false));
		}
		Vec3 v = prey.getDeltaMovement();
		prey.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
	}

	private static void drink(ServerPlayer sp, ServerLevel level, LivingEntity prey) {
		float floor = prey instanceof Player ? PLAYER_FLOOR : OTHER_FLOOR;
		if (prey.getHealth() > floor + DRAIN) {
			prey.hurtServer(level, level.damageSources().magic(), DRAIN);
		}
		Thirst.feed(sp, person(prey) ? FEED_PERSON : FEED_BEAST);
		Vec3 neck = prey.position().add(0.0, prey.getBbHeight() * 0.75, 0.0);
		Thirst.bloodMist(level, neck, 0.6F, 6);
		level.playSound(null, sp.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
	}

	/** The bite is over: the victim is let go, and weakened if it was drunk to the end. */
	private static void finish(@Nullable ServerPlayer sp, Bite bite, @Nullable LivingEntity prey, boolean done) {
		if (prey instanceof Mob mob && !bite.hadNoAi()) {
			mob.setNoAi(false);
		}
		if (prey == null || !done) {
			return;
		}
		prey.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 1, false, true, true));
		prey.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0, false, true, true));
		if (prey instanceof PathfinderMob mob && sp != null && person(prey)) {
			Vec3 away = DefaultRandomPos.getPosAway(mob, FEAR_RUN, 4, sp.position());
			if (away != null) {
				mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
			}
		}
		if (sp != null && prey instanceof ServerPlayer bitten) {
			bitten.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.drinker.bitten").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			if (Downed.isDowned(bitten) && bitten.getRandom().nextFloat() < PASS_CHANCE && Thirst.give(bitten, false)) {
				bitten.heal(4.0F);
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.drinker.passed").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			}
		}
	}

	// ---- queries ----

	/** Whether {@code sp} is drinking now. */
	static boolean biting(ServerPlayer sp) {
		return BITES.containsKey(sp.getUUID());
	}

	/** The bite's own bar, while it lasts. */
	static @Nullable MutableComponent bar(ServerPlayer sp) {
		Bite bite = BITES.get(sp.getUUID());
		if (bite == null) {
			return null;
		}
		long age = sp.level().getGameTime() - bite.start();
		return Thirst.bar((int)age, BITE_TICKS, ChatFormatting.DARK_RED);
	}

	/** Lets go of whoever {@code sp} holds (they logged out, died or were cured). */
	static void release(ServerPlayer sp) {
		Bite bite = BITES.remove(sp.getUUID());
		if (bite != null && sp.level() instanceof ServerLevel level && level.getEntity(bite.prey()) instanceof LivingEntity prey) {
			finish(sp, bite, prey, false);
		}
	}
}
