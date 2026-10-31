package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoDouble;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.112, Scan E) Mirage, the illusionist: lean, in a deep blue hooded robe trimmed in gold, a pale grey face and
 * bright eyes, a shimmer about it. 20 health, fists of 3. Scanned from an illusioner (vanilla's, or the Friends &amp;
 * Foes one) ({@code XenoDna}).
 * <ul>
 * <li>R, Mirror Images: {@link #COPIES} false copies of you step out round you; what was after you goes after one of
 * them. They can't fight and vanish at the first blow ({@link XenoDouble#makeFake}).</li>
 * <li>G, Blind: the creature you aim at is blinded and loses you.</li>
 * <li>V, Unseen: for {@link #UNSEEN_TICKS} ticks you can't be seen, and whatever was after you loses you.</li>
 * <li>B, Swap: you trade places with your copy farthest off (nearest, sneaking).</li>
 * </ul>
 */
public final class Mirage extends XenoForm {
	// ---- tuning ----
	public static final int COPIES = 3, COPY_TICKS = 300, COPY_COOLDOWN = 400;
	public static final double COPY_SPREAD = 2.5, LURE_RANGE = 16.0;
	public static final double BLIND_REACH = 16.0;
	public static final int BLIND_TICKS = 100, BLIND_COOLDOWN = 160;
	public static final int UNSEEN_TICKS = 100, UNSEEN_COOLDOWN = 400;
	public static final double UNSEEN_RANGE = 24.0;
	public static final int SWAP_COOLDOWN = 40;

	public Mirage() {
		super("mirage", 1.0F, 20.0F, 3.0, 0.03, 0.0, 0.0, Items.ENDER_EYE, 0x2E4AA0);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Illusioner;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		vanish(player.level(), live);
	}

	/** Every copy gone. */
	private static void vanish(ServerLevel level, XenoformPower.Live live) {
		for (int id : live.doubles) {
			if (level.getEntity(id) instanceof XenoDouble copy && copy.fake()) {
				copy.pop(level);
			}
		}
		live.doubles.clear();
	}

	/** The copies still standing. */
	private static List<XenoDouble> copies(ServerLevel level, XenoformPower.Live live) {
		List<XenoDouble> out = new ArrayList<>();
		for (var it = live.doubles.iterator(); it.hasNext();) {
			if (level.getEntity(it.next()) instanceof XenoDouble copy && copy.isAlive() && copy.fake()) {
				out.add(copy);
			} else {
				it.remove();
			}
		}
		return out;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && images(player, live, level)) {
					cooldown(player, live, R, COPY_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, BLIND_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0), player);
				target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BLIND_TICKS, 0), player);
				if (target instanceof Mob mob && mob.getTarget() == player) {
					mob.setTarget(null);
				}
				beam(level, player.getEyePosition(), target.getEyePosition(), ParticleTypes.WITCH, 10);
				sound(player, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.2F, 1.0F);
				cooldown(player, live, G, BLIND_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, UNSEEN_TICKS, 0, false, false));
					for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(UNSEEN_RANGE), m -> m.getTarget() == player)) {
						mob.setTarget(null);
					}
					burst(level, ParticleTypes.WITCH, player.position().add(0.0, 1.0, 0.0), 30, 0.5, 0.05);
					sound(player, SoundEvents.ILLUSIONER_CAST_SPELL, 1.2F, 1.2F);
					cooldown(player, live, V, UNSEEN_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && swap(player, live, level)) {
					cooldown(player, live, B, SWAP_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** R: the copies step out round it, and what hunted it hunts them. */
	private boolean images(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		vanish(level, live);
		List<XenoDouble> made = new ArrayList<>();
		for (int i = 0; i < COPIES; i++) {
			double a = player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD + i / (double)COPIES * Math.PI * 2.0;
			Vec3 at = player.position().add(-Math.sin(a) * COPY_SPREAD, 0.0, Math.cos(a) * COPY_SPREAD);
			XenoDouble copy = XenoDouble.TYPE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (copy == null) {
				continue;
			}
			copy.snapTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
			copy.makeFake(player, this.id, this.scale, COPY_TICKS);
			if (level.addFreshEntity(copy)) {
				live.doubles.add(copy.getId());
				made.add(copy);
				level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 1.0, at.z, 10, 0.3, 0.5, 0.3, 0.05);
			}
		}
		if (made.isEmpty()) {
			return false;
		}
		// whatever was after it goes after a copy
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(LURE_RANGE), m -> m.getTarget() == player)) {
			mob.setTarget(made.get(player.getRandom().nextInt(made.size())));
		}
		sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4F, 1.0F);
		return true;
	}

	/** B: trade places with the copy farthest off (nearest, sneaking). */
	private static boolean swap(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		XenoDouble pick = null;
		boolean near = player.isShiftKeyDown();
		double best = near ? Double.MAX_VALUE : -1.0;
		for (XenoDouble copy : copies(level, live)) {
			double d = copy.distanceToSqr(player);
			if (near ? d < best : d > best) {
				best = d;
				pick = copy;
			}
		}
		if (pick == null) {
			return false;
		}
		Vec3 mine = player.position();
		Vec3 theirs = pick.position();
		player.teleportTo(level, theirs.x, theirs.y, theirs.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		pick.teleportTo(mine.x, mine.y, mine.z);
		player.resetFallDistance();
		// what was after it now goes after the copy where it stood
		for (Mob mob : level.getEntitiesOfClass(Mob.class, pick.getBoundingBox().inflate(LURE_RANGE), m -> m.getTarget() == player)) {
			mob.setTarget(pick);
		}
		burst(level, ParticleTypes.WITCH, mine.add(0.0, 1.0, 0.0), 12, 0.3, 0.05);
		burst(level, ParticleTypes.WITCH, theirs.add(0.0, 1.0, 0.0), 12, 0.3, 0.05);
		sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.3F);
		return true;
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		if (player.tickCount % 8 == 0) {
			player.level().sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.3, 0.5, 0.3, 0.0);
		}
		if (!live.doubles.isEmpty() && player.tickCount % 20 == 0) {
			copies(player.level(), live);
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.WITCH, player.position().add(0.0, 1.0, 0.0), 50, 1.0, 0.05);
		sound(player, SoundEvents.ILLUSIONER_CAST_SPELL, 1.6F, 0.7F);
		return true;
	}
}
