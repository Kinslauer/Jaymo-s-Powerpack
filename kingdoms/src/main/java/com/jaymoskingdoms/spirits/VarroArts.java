package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Thralls;
import com.jaymoskingdoms.Worker;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Varro's later arts (Andy, 2026-11-14: "more of the shadow of war combat inspiration for varro, and grimsby"; the ideas
 * only, our own names and looks): the siege-mage stops throwing from the back line and takes the field in hand.
 * <ul>
 * <li><b>Hauling Hand</b> (level {@value #HAUL_LEVEL}): the gauntlet closes on the foe you look at, up to
 * {@value #HAUL_REACH} blocks off, and drags it out of its line to your feet, off its own.</li>
 * <li><b>Javelin Heads</b> ({@value #HEAD_LEVEL}): each cast changes what his javelins and bombards are tipped with: plain,
 * fire, frost, venom, light (light blinds the eye that sees it and burns the undead half again as hard).</li>
 * <li><b>The Marshal's Voice</b> ({@value #DREAD_LEVEL}): he shouts through you. Every lesser foe that hears it turns and
 * runs for {@value #DREAD_TICKS} ticks; the great ones only falter.</li>
 * <li><b>Break</b> ({@value #BREAK_LEVEL}): a foe beaten below {@value #BREAK_HEALTH} of its health, in arm's reach: the
 * hand closes on its head and its will is his. It fights for you for a minute, then is itself again.</li>
 * </ul>
 */
public final class VarroArts {
	static final int HAUL_LEVEL = 9, HEAD_LEVEL = 11, DREAD_LEVEL = 13, BREAK_LEVEL = 15;
	/** Hauling Hand: reach, the hurt of the grip, how long the hauled foe is off its feet, and the heaviest thing it moves (health). */
	static final double HAUL_REACH = 24.0;
	static final float HAUL_DAMAGE = 4.0F, HAUL_MOST = 120.0F;
	static final int HAUL_STAGGER = 30;
	/** The javelin's heads. */
	static final int PLAIN = 0, FIRE = 1, FROST = 2, VENOM = 3, LIGHT = 4, HEADS = 5;
	static final String[] HEAD_NAMES = {"plain", "fire", "frost", "venom", "light"};
	static final int HEAD_TICKS = 100;
	static final float LIGHT_ON_UNDEAD = 1.5F;
	/** The Marshal's Voice: how far it carries, how long they run, and the health above which a foe only falters. */
	static final double DREAD_RANGE = 10.0;
	static final int DREAD_TICKS = 160;
	static final float DREAD_MOST = 60.0F;
	/** Break: reach, the share of health a foe must be under, how long it serves (ticks), the heaviest will he can take (health), how many at once. */
	static final double BREAK_REACH = 4.0;
	static final float BREAK_HEALTH = 0.35F, BREAK_MOST = 100.0F;
	static final int BREAK_TICKS = 20 * 60, BREAK_AT_ONCE = 3;

	static final Move HAUL = new Ambrose.Def("hauling_hand", 14.0F, 140, HAUL_LEVEL, Poses.PULL, Mana.WILL, VarroArts::haul);
	static final Move HEAD = new Ambrose.Def("javelin_head", 0.0F, 10, HEAD_LEVEL, Poses.MARK, Mana.WILL, VarroArts::nextHead);
	static final Move DREAD = new Ambrose.Def("marshals_voice", 22.0F, 20 * 30, DREAD_LEVEL, Poses.WARD, Mana.WILL, VarroArts::dread);
	static final Move BREAK = new Ambrose.Def("break_will", 30.0F, 20 * 45, BREAK_LEVEL, Poses.GRAB, Mana.WILL, VarroArts::breakWill);
	static final List<Move> MOVES = List.of(HAUL, HEAD, DREAD, BREAK);

	private static final Map<UUID, Integer> CHOSEN = new HashMap<>();

	private record Fleeing(PathfinderMob mob, UUID from, long until) {
	}

	private static final Map<UUID, Fleeing> FLEEING = new HashMap<>();

	private VarroArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (FLEEING.isEmpty() || server.getTickCount() % 10 != 0) {
				return;
			}
			for (Iterator<Fleeing> it = FLEEING.values().iterator(); it.hasNext();) {
				Fleeing f = it.next();
				if (!f.mob().isAlive() || f.mob().level().getGameTime() > f.until()) {
					it.remove();
					continue;
				}
				f.mob().setTarget(null);
				Player from = f.mob().level().getPlayerByUUID(f.from());
				if (from != null && f.mob().getNavigation().isDone()) {
					Vec3 away = DefaultRandomPos.getPosAway(f.mob(), 14, 6, from.position());
					if (away != null) {
						f.mob().getNavigation().moveTo(away.x, away.y, away.z, 1.4);
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLEEING.clear();
			CHOSEN.clear();
		});
	}

	// ---- hauling hand --------------------------------------------------------------------------------------------------

	private static boolean haul(ServerPlayer player, SpiritState state, int charge) {
		for (LivingEntity foe : SpiritFoes.cone(player, HAUL_REACH, 8.0, true)) {
			if (player.hasLineOfSight(foe) && haulIn(player, foe, Spirits.damageScale(state))) {
				CastHints.setReach(player, 2.0);
				return true;
			}
		}
		return false;
	}

	/** The gauntlet drags {@code foe} to {@code player}'s feet; false if it is too great a thing to move. */
	static boolean haulIn(ServerPlayer player, LivingEntity foe, float scale) {
		if (foe.getMaxHealth() > HAUL_MOST) {
			return false;
		}
		ServerLevel level = (ServerLevel)player.level();
		Vec3 from = foe.getBoundingBox().getCenter();
		Vec3 look = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : look.normalize();
		Vec3 to = player.position().add(look.scale(1.6 + foe.getBbWidth() * 0.5));
		if (!level.noCollision(foe, foe.getBoundingBox().move(to.subtract(foe.position())))) {
			to = player.position();
		}
		foe.teleportTo(to.x, to.y, to.z);
		foe.setDeltaMovement(Vec3.ZERO);
		foe.fallDistance = 0.0;
		Fx.beam(level, player.getEyePosition().add(0.0, -0.3, 0.0), from, 0.18F, 0xFFB347, 10);
		foe.setInvulnerableTime(0);
		SpiritFoes.strike(level, player, foe, HAUL_DAMAGE * scale);
		Staggered.stagger(player, foe, HAUL_STAGGER);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 0.6F);
		PowersCompat.shake(player, 0.25F);
		return true;
	}

	// ---- javelin heads -------------------------------------------------------------------------------------------------

	static int headOf(LivingEntity owner) {
		return CHOSEN.getOrDefault(owner.getUUID(), PLAIN);
	}

	private static boolean nextHead(ServerPlayer player, SpiritState state, int charge) {
		int head = (headOf(player) + 1) % HEADS;
		CHOSEN.put(player.getUUID(), head);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.javelin_head",
			Component.translatable("spirit.jaymoskingdoms.javelin_head." + HEAD_NAMES[head])).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** How much harder a javelin with {@code head} lands on {@code victim}. */
	static float headTimes(int head, LivingEntity victim) {
		return head == LIGHT && victim.is(EntityTypeTags.UNDEAD) ? LIGHT_ON_UNDEAD : 1.0F;
	}

	/** What a javelin with {@code head} leaves in {@code victim}. */
	static void headHit(ServerLevel level, LivingEntity caster, LivingEntity victim, int head) {
		switch (head) {
			case FIRE -> victim.igniteForTicks(HEAD_TICKS);
			case FROST -> {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HEAD_TICKS, 1), caster);
				victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), victim.getTicksRequiredToFreeze() + HEAD_TICKS));
			}
			case VENOM -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, HEAD_TICKS, 1), caster);
			case LIGHT -> {
				victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, HEAD_TICKS * 2, 0), caster);
				victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HEAD_TICKS, 0), caster);
			}
			default -> {
			}
		}
	}

	// ---- the marshal's voice -------------------------------------------------------------------------------------------

	private static boolean dread(ServerPlayer player, SpiritState state, int charge) {
		return shout(player);
	}

	/** The shout: lesser foes in earshot run, great ones falter. Always goes out. */
	static boolean shout(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		long until = level.getGameTime() + DREAD_TICKS;
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DREAD_RANGE),
			e -> SpiritFoes.foe(player, e, false) && e.distanceToSqr(player) <= DREAD_RANGE * DREAD_RANGE)) {
			if (foe instanceof PathfinderMob mob && foe.getMaxHealth() <= DREAD_MOST) {
				mob.setTarget(null);
				mob.getNavigation().stop();
				FLEEING.put(mob.getUUID(), new Fleeing(mob, player.getUUID(), until));
			} else {
				foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DREAD_TICKS, 0), player);
				foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DREAD_TICKS / 2, 0), player);
			}
		}
		Fx.ring(level, player.position().add(0.0, 0.2, 0.0), (float)DREAD_RANGE, 0xFFB347);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2F, 0.7F);
		PowersCompat.shake(player, 0.5F);
		return true;
	}

	static boolean fleeing(LivingEntity e) {
		return FLEEING.containsKey(e.getUUID());
	}

	// ---- break ---------------------------------------------------------------------------------------------------------

	private static boolean breakWill(ServerPlayer player, SpiritState state, int charge) {
		ServerLevel level = (ServerLevel)player.level();
		int held = 0;
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0), m -> m.entityTags().contains(Thralls.BROKEN_TAG))) {
			Thralls.Tag tag = Thralls.tag(m);
			if (tag != null && tag.master().equals(player.getUUID())) {
				held++;
			}
		}
		if (held >= BREAK_AT_ONCE) {
			return false;
		}
		for (LivingEntity foe : SpiritFoes.cone(player, BREAK_REACH, 45.0, true)) {
			if (foe instanceof Mob mob && !(foe instanceof Worker) && !Thralls.isThrall(foe) && foe.getMaxHealth() <= BREAK_MOST
				&& foe.getHealth() <= foe.getMaxHealth() * BREAK_HEALTH) {
				Thralls.sway(level, mob, player.getUUID(), BREAK_TICKS);
				mob.heal(mob.getMaxHealth() * 0.5F);
				FLEEING.remove(mob.getUUID());
				Vec3 head = mob.getEyePosition();
				Fx.sigil(level, mob.position().add(0.0, 0.05, 0.0), 1.2F, 0xFFB347, 30);
				Mana.sendSeen(level, SpiritJavelin.AMBER, head.x, head.y, head.z, 20, 0.25, 0.25, 0.25, 0.02);
				level.playSound(null, head.x, head.y, head.z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.7F, 1.6F);
				CastHints.setReach(player, Math.max(1.0, mob.distanceTo(player)));
				return true;
			}
		}
		return false;
	}
}
