package com.jaymospowers.tinkering;

import com.jaymospowers.combat.WeaponKind;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Small on-hit chances by the kind of weapon in the hand (the idea from Hackers 'n Slashers and Minecraft Dungeons; the
 * code and numbers are ours): an axe cleaves into the next foe, a mace or hammer dazes, a spear pierces the one behind, a
 * sickle swipes twice. Kept beside {@link TraitEffects} so the two don't stack into nonsense: one effect at most per
 * blow, a {@link #COOLDOWN} between, none from a blow that is itself an effect, and a forged tool that already cleaves
 * (Sweeping, Area) or pierces (Piercing) keeps its trait instead of the kind's effect.
 */
public final class WeaponProcs {
	// ===== the knobs ================================================================================================
	/** Chance per blow (0 to 1) of each kind's effect. */
	static final float CLEAVE_CHANCE = 0.25F, DAZE_CHANCE = 0.2F, PIERCE_CHANCE = 0.25F, SWIPE_CHANCE = 0.2F;
	/** The effect's damage as a share of the blow that set it off. */
	static final float CLEAVE_SHARE = 0.5F, PIERCE_SHARE = 0.6F, SWIPE_SHARE = 0.5F;
	/** How far (blocks) from the struck one the cleave finds the next foe, and how far behind it the spear pierces. */
	static final double CLEAVE_RANGE = 2.5, PIERCE_RANGE = 3.0;
	/** How long a mace's daze stuns (ticks). */
	static final int DAZE_TICKS = 30;
	/** Ticks between one player's effects. */
	static final int COOLDOWN = 12;
	/** The effects' colours and size. */
	static final int COLOR = 0xE8E0CC, DAZE_COLOR = 0xF2D675;
	static final float SIZE = 0.8F;
	// ================================================================================================================

	private static final Map<UUID, Long> LAST = new HashMap<>();
	/** Set while an effect's own damage lands, so it sets off nothing. */
	private static boolean procing;

	private WeaponProcs() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!procing && !blocked && damageTaken > 0.0F && source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player
				&& entity != player && entity.level() instanceof ServerLevel level) {
				proc(level, player, entity, damageTaken);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	private static void proc(ServerLevel level, ServerPlayer player, LivingEntity target, float taken) {
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < COOLDOWN) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		WeaponKind kind = WeaponKind.of(stack);
		if (kind == null || ToolStats.isBroken(stack)) {
			return;
		}
		ToolStats.Stats stats = ToolStats.stats(stack);
		float roll = level.getRandom().nextFloat();
		boolean done = switch (kind) {
			case AXE -> roll < CLEAVE_CHANCE && (stats == null || stats.level(Trait.SWEEPING) + stats.level(Trait.AREA) == 0)
				&& cleave(level, player, target, taken);
			case MACE, HAMMER -> roll < DAZE_CHANCE && daze(level, target);
			case SPEAR -> roll < PIERCE_CHANCE && (stats == null || stats.level(Trait.PIERCE) == 0) && pierce(level, player, target, taken);
			case CLAW -> roll < SWIPE_CHANCE && swipe(level, player, target, taken);
			default -> false;
		};
		if (done) {
			LAST.put(player.getUUID(), now);
		}
	}

	private static void hurt(ServerLevel level, ServerPlayer player, LivingEntity target, float damage) {
		procing = true;
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, level.damageSources().playerAttack(player), damage);
		} finally {
			procing = false;
		}
	}

	/** The axe carries on into the nearest other foe beside the one struck. */
	private static boolean cleave(ServerLevel level, ServerPlayer player, LivingEntity target, float taken) {
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(CLEAVE_RANGE),
			e -> e != player && e != target && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && player.hasLineOfSight(e));
		LivingEntity next = near.stream().min((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target))).orElse(null);
		if (next == null) {
			return false;
		}
		hurt(level, player, next, taken * CLEAVE_SHARE);
		Vec3 from = target.getBoundingBox().getCenter(), to = next.getBoundingBox().getCenter();
		MagicFx.stroke(level, from, to, SIZE * 0.6F, COLOR);
		return true;
	}

	/** The mace rings the head: the struck one reels. */
	private static boolean daze(ServerLevel level, LivingEntity target) {
		DarktideStatus.stun(target, DAZE_TICKS);
		MagicFx.ring(level, target.getEyePosition(), SIZE, DAZE_COLOR);
		return true;
	}

	/** The spear goes through the one struck into the one behind it. */
	private static boolean pierce(ServerLevel level, ServerPlayer player, LivingEntity target, float taken) {
		Vec3 dir = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (dir.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 line = dir.normalize();
		Vec3 start = target.getBoundingBox().getCenter();
		LivingEntity behind = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(PIERCE_RANGE),
			o -> o != player && o != target && o.isAlive() && !o.isSpectator() && !player.isAlliedTo(o))) {
			Vec3 rel = e.getBoundingBox().getCenter().subtract(start);
			double along = rel.dot(line);
			double off = rel.subtract(line.scale(along)).length();
			if (along > 0.0 && along <= PIERCE_RANGE && off <= 0.8 && along < best) {
				best = along;
				behind = e;
			}
		}
		if (behind == null) {
			return false;
		}
		hurt(level, player, behind, taken * PIERCE_SHARE);
		MagicFx.stroke(level, start, behind.getBoundingBox().getCenter(), SIZE * 0.5F, COLOR);
		return true;
	}

	/** The sickle comes back for a second cut. */
	private static boolean swipe(ServerLevel level, ServerPlayer player, LivingEntity target, float taken) {
		hurt(level, player, target, taken * SWIPE_SHARE);
		Vec3 at = target.getBoundingBox().getCenter();
		MagicFx.slash(level, at, at.add(-0.6, 0.5, 0.0), at.add(0.6, -0.5, 0.0), SIZE * 0.5F, COLOR, 6);
		return true;
	}
}
