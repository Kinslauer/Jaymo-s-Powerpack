package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.ManaFolk;
import com.jaymoskingdoms.Sorcery;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.238) The spirits of the folk. Andy answered "Yes" to "Do attuned villagers (court wizards, hermits) have spirits of
 * their own you can see and fight?" A court wizard or hermit carries a spirit ({@link ManaFolk#giveSpirit}: Grimsby or Varro
 * by their id, their power as its level, bond 50). While they have a living target within {@link #SHOW_RANGE} the spirit is
 * out, so everyone sees its arms; with none for {@link #LOSE_TICKS} it is put away, and so it is when they die. With the
 * target within {@link #CAST_RANGE} and their own cooldown ({@link #FOLK_EVERY}) ready, a Grimsby folk lays a flurry on it (or
 * a riposte for a player in melee) and a Varro folk throws a javelin, or a volley when {@link #VOLLEY_FOES} foes are about.
 * A Grimsby folk also parries one blow in {@link #PARRY_ONE_IN} from a player in melee. Every cast ripples their aura and
 * shows its pose. The moves are the spirits' own, cast through {@link Move#castFrom}: no mana, no key.
 */
public final class FolkSpirits {
	/** How far a target brings the spirit out, how far a cast reaches, and how near a blow must be to be parried. */
	static final double SHOW_RANGE = 16.0, CAST_RANGE = 12.0, MELEE = 3.5;
	/** Ticks without a target before the spirit is put away. */
	static final int LOSE_TICKS = 100;
	/** Ticks between one folk's casts, how many foes make a volley, and how often a player's blow is parried (one in this many). */
	static final int FOLK_EVERY = 120, VOLLEY_FOES = 3, PARRY_ONE_IN = 3;

	/** Game time each folk may cast again, and when each last had a target. */
	private static final Map<UUID, Long> READY = new HashMap<>();
	private static final Map<UUID, Long> LAST_TARGET = new HashMap<>();

	private FolkSpirits() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READY.clear();
			LAST_TARGET.clear();
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof Folk folk && source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
				SpiritState state = Spirits.state(folk);
				if (state != null && state.out() && state.kind() == Spirits.GRIMSBY && folk.isAlive() && folk.distanceTo(player) <= MELEE
					&& folk.getRandom().nextInt(PARRY_ONE_IN) == 0) {
					parry(folk, state, player);
					return false;
				}
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Folk folk) {
				SpiritState state = Spirits.state(folk);
				if (state != null && state.out()) {
					folk.setAttached(Spirits.SPIRIT, state.withOut(false));
				}
				READY.remove(folk.getUUID());
				LAST_TARGET.remove(folk.getUUID());
			}
		});
	}

	/** A blow turned aside: the pose, a clang, the aura ripples, and the attacker is shoved back. */
	private static void parry(Folk folk, SpiritState state, ServerPlayer attacker) {
		set(folk, state.withShow(folk.level().getGameTime(), Poses.PARRY));
		folk.level().playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 1.1F);
		Mana.ripple(folk, Mana.WARD);
		net.minecraft.world.phys.Vec3 away = attacker.position().subtract(folk.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.5);
		attacker.push(away.x, 0.2, away.z);
		attacker.syncVelocity = true;
	}

	/** The folk's spirit tick, every 10 ticks from {@link Sorcery#tick}: out while there's a target, a cast when it is near. */
	public static void tick(ServerLevel level, Folk folk, @Nullable LivingEntity target) {
		SpiritState state = Spirits.state(folk);
		if (state == null) {
			ManaFolk.giveSpirit(folk, Mana.power(folk));
			state = Spirits.state(folk);
			if (state == null) {
				return;
			}
		}
		long now = level.getGameTime();
		UUID id = folk.getUUID();
		boolean has = target != null && target.isAlive() && folk.distanceTo(target) <= SHOW_RANGE;
		if (has) {
			LAST_TARGET.put(id, now);
			if (!state.out()) {
				state = state.withOut(true).withShow(now, Poses.IDLE);
				set(folk, state);
			}
		} else {
			if (state.out()) {
				Long seen = LAST_TARGET.get(id);
				if (seen == null || now - seen >= LOSE_TICKS) {
					set(folk, state.withOut(false));
					LAST_TARGET.remove(id);
				}
			}
			return;
		}
		if (folk.distanceTo(target) > CAST_RANGE || now < READY.getOrDefault(id, 0L)) {
			return;
		}
		Move move = pick(level, folk, target, state);
		if (move == null || !move.castFrom(folk, target, state)) {
			return;
		}
		READY.put(id, now + FOLK_EVERY);
		Mana.ripple(folk, Mana.HARM);
		set(folk, state.withShow(now, move.pose()));
	}

	/** Which move this folk's spirit casts now, or null. */
	private static @Nullable Move pick(ServerLevel level, Folk folk, LivingEntity target, SpiritState state) {
		if (state.kind() == Spirits.GRIMSBY) {
			boolean riposte = target instanceof ServerPlayer && folk.distanceTo(target) <= MELEE && folk.getRandom().nextBoolean();
			return Moves.of(riposte ? "riposte" : "flurry");
		}
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, folk.getBoundingBox().inflate(CAST_RANGE), e -> Sorcery.foe(folk, e));
		return Moves.of(foes.size() >= VOLLEY_FOES ? "volley" : "javelin");
	}

	private static void set(Folk folk, SpiritState state) {
		folk.setAttached(Spirits.SPIRIT, state);
	}
}
