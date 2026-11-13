package com.jaymoskingdoms;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Spells of the mind (fifth edition's charm, command, suggestion and hold), cast at the person you look at, or touched
 * by a familiar you are looking through ({@link Familiars#touched}). Each asks the target for a save and their boss
 * resistances ({@link Saves}, {@link Legendary}); a spell that fails still costs the caster, with a shrug in the air.
 * <ul>
 * <li><b>Charm:</b> {@link Charm#charm}; held, so a broken concentration frees them.</li>
 * <li><b>Hold:</b> {@link HeldFast#afflict} on a person; held the same way.</li>
 * <li><b>Command:</b> one word, the one picked on the Magic screen: <i>flee</i> frightens, <i>drop</i> makes them let go of what
 * they hold, <i>grovel</i> drops them to the ground, stunned for a moment.</li>
 * <li><b>Suggestion:</b> a short, harmless thing a villager or mob then does: come, wait, leave, sleep or follow.</li>
 * </ul>
 * Zone of truth, geas and modify memory are {@link Truth}, {@link Geas} and {@link Forgetting}.
 */
public final class MindSpells {
	public static final Set<String> SPELLS = Set.of("charm", "command", "suggestion", "hold", "zone_of_truth", "geas", "modify_memory");
	/** How far the gaze reaches for these spells (blocks). */
	static final double REACH = 16.0;
	/** Charm and hold last this long (ticks); a held spell the caster must keep up. */
	static final int CHARM_TICKS = 20 * 60, HOLD_TICKS = 20 * 20;
	/** A command: the fright's length, how long a groveller is stunned, how long a dropped thing cannot be picked up again (ticks). */
	static final int FLEE_TICKS = 20 * 8, GROVEL_TICKS = 20 * 3, PICKUP_DELAY = 20 * 3;
	/** A suggestion lasts this long (ticks); a sleeper sleeps this long. */
	static final int SUGGEST_TICKS = 20 * 30, FOLLOW_TICKS = 20 * 60, SLEEP_TICKS = 20 * 20;
	/** How far "leave" sends them, how near "come" and "follow" bring them (blocks), and the pace. */
	static final double LEAVE_DISTANCE = 24.0, CLOSE = 2.5;
	static final float PACE = 0.9F;
	/** How often (ticks) a suggestion is nudged along. */
	static final int EVERY = 10;
	static final int COLOUR = 0xE0609A;

	private static final class Suggestion {
		final String action;
		final UUID caster;
		final long until;
		@Nullable Vec3 goal;

		Suggestion(String action, UUID caster, long until) {
			this.action = action;
			this.caster = caster;
			this.until = until;
		}
	}

	private static final Map<LivingEntity, Suggestion> SUGGESTED = new WeakHashMap<>();

	private MindSpells() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MindSpells::tick);
	}

	public static boolean ours(String spell) {
		return SPELLS.contains(spell);
	}

	static boolean cast(String spell, ServerLevel level, Player player, float power) {
		if (!(player instanceof ServerPlayer caster)) {
			return false;
		}
		return switch (spell) {
			case "charm" -> charm(level, caster, power);
			case "hold" -> hold(level, caster, power);
			case "command" -> command(level, caster);
			case "suggestion" -> suggestion(level, caster);
			case "zone_of_truth" -> Truth.cast(level, caster, power);
			case "geas" -> Geas.cast(level, caster, power);
			case "modify_memory" -> Forgetting.cast(level, caster);
			default -> false;
		};
	}

	/** Whether {@code e} is a person, the sort of thing these spells work on (charm and hold are person-only spells). */
	static boolean person(LivingEntity e) {
		return Temperaments.humanoid(e) || e instanceof Player;
	}

	/** Who the caster means: the one a familiar touches, else the one they look at. */
	static @Nullable LivingEntity target(ServerPlayer caster) {
		LivingEntity touched = Familiars.touched(caster);
		if (touched != null) {
			return touched;
		}
		LivingEntity looked = Arcana.sighted(caster);
		return looked != null && looked.distanceTo(caster) <= REACH ? looked : null;
	}

	/** A spell shrugged off: a puff at the target's eyes and a dull note. */
	static void shrugged(ServerLevel level, LivingEntity target) {
		level.sendParticles(Fx.GLYPH, target.getX(), target.getEyeY(), target.getZ(), 6, 0.2, 0.15, 0.2, 0.02);
		level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.5F, 0.6F);
	}

	private static void sign(ServerLevel level, ServerPlayer caster, LivingEntity target, int colour) {
		Fx.beam(level, caster.getEyePosition().add(0.0, -0.3, 0.0), target.getBoundingBox().getCenter(), 0.12F, colour, 8);
		caster.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
	}

	// ---- charm and hold -----------------------------------------------------------------------------------------------------------

	private static boolean charm(ServerLevel level, ServerPlayer caster, float power) {
		LivingEntity target = target(caster);
		if (target == null || !person(target) || target == caster) {
			return false;
		}
		sign(level, caster, target, COLOUR);
		int ticks = Math.round(CHARM_TICKS * power);
		if (Charm.charm(target, caster, ticks)) {
			Concentration.hold(caster, "charm", ticks, () -> Charm.free(target));
		} else {
			shrugged(level, target);
		}
		return true;
	}

	private static boolean hold(ServerLevel level, ServerPlayer caster, float power) {
		LivingEntity target = target(caster);
		if (target == null || !person(target) || target == caster) {
			return false;
		}
		sign(level, caster, target, 0xB8A860);
		int ticks = Math.round(HOLD_TICKS * power);
		if (HeldFast.afflict(target, ticks)) {
			Concentration.hold(caster, "hold", ticks, () -> HeldFast.release(target));
			Fx.ring(level, target.position().add(0.0, 0.1, 0.0), 1.0F, 0xB8A860);
		} else {
			shrugged(level, target);
		}
		return true;
	}

	// ---- command ------------------------------------------------------------------------------------------------------------------

	private static boolean command(ServerLevel level, ServerPlayer caster) {
		LivingEntity target = target(caster);
		if (target == null || target == caster || !SpellParts.canSpeak(caster)) {
			return false;
		}
		String word = Choices.get(caster, "command");
		sign(level, caster, target, 0xE8B04A);
		level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.5F);
		Saves.Kind kind = word.equals("flee") ? Saves.Kind.FEAR : Saves.Kind.CHARM;
		if (target instanceof Mob mob && mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MISC || Legendary.shrugs(target)
			|| Saves.resist(target, kind)) {
			shrugged(level, target);
			return true;
		}
		switch (word) {
			case "flee" -> {
				Fear.frighten(target, caster, FLEE_TICKS);
				if (target instanceof Mob mob) {
					mob.setTarget(null);
					Vec3 away = target.position().subtract(caster.position()).multiply(1.0, 0.0, 1.0);
					Vec3 to = target.position().add((away.lengthSqr() < 1.0E-4 ? caster.getLookAngle() : away.normalize()).scale(16.0));
					mob.getNavigation().moveTo(to.x, to.y, to.z, 1.3);
				}
			}
			case "drop" -> drop(level, target);
			default -> {
				HeldFast.startle(target, GROVEL_TICKS);
				level.sendParticles(Fx.mote(0xE8B04A, 0.8F), target.getX(), target.getY(0.2), target.getZ(), 8, 0.4, 0.05, 0.4, 0.01);
			}
		}
		return true;
	}

	/** {@code target} lets go of whatever is in their hand (a mob cannot pick it up again for a moment). */
	private static void drop(ServerLevel level, LivingEntity target) {
		ItemStack held = target.getMainHandItem().copy();
		if (held.isEmpty()) {
			held = target.getOffhandItem().copy();
			target.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		} else {
			target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		if (held.isEmpty()) {
			return;
		}
		ItemEntity item = target instanceof ServerPlayer sp ? sp.drop(held, false, Prediction.SERVER_ONLY) : target.spawnAtLocation(level, held);
		if (item != null) {
			item.setPickUpDelay(PICKUP_DELAY);
		}
		if (target instanceof Mob mob) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.6F);
	}

	// ---- suggestion ---------------------------------------------------------------------------------------------------------------

	private static boolean suggestion(ServerLevel level, ServerPlayer caster) {
		LivingEntity target = target(caster);
		if (target == null || target == caster || target instanceof Player || !(target instanceof Mob) || !SpellParts.canSpeak(caster)) {
			return false;
		}
		sign(level, caster, target, 0xC9B8FF);
		if (Legendary.shrugs(target) || Saves.resist(target, Saves.Kind.CHARM)) {
			shrugged(level, target);
			return true;
		}
		String action = Choices.get(caster, "suggestion");
		if (action.equals("sleep")) {
			if (!HeldFast.sleep(target, SLEEP_TICKS)) {
				shrugged(level, target);
			}
			return true;
		}
		SUGGESTED.put(target, new Suggestion(action, caster.getUUID(), level.getGameTime() + (action.equals("follow") ? FOLLOW_TICKS : SUGGEST_TICKS)));
		level.sendParticles(Fx.rune(0xC9B8FF, 0.8F), target.getX(), target.getEyeY() + 0.3, target.getZ(), 4, 0.2, 0.1, 0.2, 0.0);
		return true;
	}

	/** Whether {@code who} is carrying out a suggestion just now. */
	public static boolean suggested(LivingEntity who) {
		Suggestion s = SUGGESTED.get(who);
		return s != null && who.level().getGameTime() < s.until;
	}

	private static void tick(MinecraftServer server) {
		if (SUGGESTED.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (Iterator<Map.Entry<LivingEntity, Suggestion>> it = SUGGESTED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Suggestion> e = it.next();
			LivingEntity who = e.getKey();
			Suggestion s = e.getValue();
			if (!who.isAlive() || !(who.level() instanceof ServerLevel level) || level.getGameTime() >= s.until || !(who instanceof Mob mob)) {
				it.remove();
				continue;
			}
			// harm ends it: the suggestion was only ever a thought
			if (mob.getTarget() != null || who.getLastHurtByMob() != null && who.tickCount - who.getLastHurtByMobTimestamp() < EVERY * 2) {
				it.remove();
				continue;
			}
			if (HeldFast.locked(who)) {
				continue;
			}
			ServerPlayer caster = level.getServer().getPlayerList().getPlayer(s.caster);
			switch (s.action) {
				case "wait" -> {
					mob.getNavigation().stop();
					Vec3 v = who.getDeltaMovement();
					who.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
				}
				case "leave" -> {
					if (s.goal == null && caster != null) {
						Vec3 away = who.position().subtract(caster.position()).multiply(1.0, 0.0, 1.0);
						Vec3 dir = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().multiply(1.0, 0.0, 1.0) : away.normalize();
						s.goal = who.position().add(dir.scale(LEAVE_DISTANCE));
					}
					if (s.goal != null) {
						walk(mob, BlockPos.containing(s.goal), 1);
					}
				}
				default -> {
					if (caster != null && caster.level() == level && who.distanceTo(caster) > CLOSE) {
						walk(mob, caster.blockPosition(), (int) CLOSE);
					}
				}
			}
		}
	}

	private static void walk(Mob mob, BlockPos to, int close) {
		if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, PACE, close);
		} else {
			mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, PACE);
		}
	}

	/** Words to show for a spell's missing target (a shared line). */
	static Component none() {
		return Component.translatable("message.jaymoskingdoms.scroll_no_target");
	}
}
