package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.blood.BloodTrail;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.108, Scan C of {@code docs/ScanGaps-Plan.md}) Howl, the pack hunter: lean and long-legged, grey-brown fur
 * darker down the back, a wolf's muzzle and tall ears, a bushy tail. 26 health, fists of 5, quick. Scanned from a
 * wolf, or Kingdoms' werewolf, feral wolf or coyote ({@code XenoDna}). It isn't the werewolf curse: nothing spreads.
 * <ul>
 * <li>R, Pounce: a leap where you look; the first creature you land on is bitten and knocked down.</li>
 * <li>G, Howl: the wild wolves round you answer, and hunt beside you for {@link #PACK_TICKS} ticks.</li>
 * <li>V, Scent: blood stains near you show (only to you), and every bleeder in reach glows.</li>
 * <li>B, Lockjaw: a bite that holds on; the creature is dragged along, slowed and bled. B again lets go.</li>
 * </ul>
 * At night it hunts harder: Strength and Speed.
 */
public final class Howl extends XenoForm {
	// ---- tuning ----
	public static final double POUNCE_OUT = 1.2, POUNCE_UP = 0.45, POUNCE_HIT = 1.2;
	public static final int POUNCE_TICKS = 20, POUNCE_COOLDOWN = 60, POUNCE_DOWN = 40;
	public static final float POUNCE_DAMAGE = 6.0F;
	public static final double PACK_RADIUS = 32.0, PACK_FOLLOW = 6.0, PACK_SPEED = 1.3;
	public static final int PACK_TICKS = 1200, PACK_COOLDOWN = 1800;
	public static final double SCENT_RANGE = 48.0;
	public static final int SCENT_TICKS = 200, SCENT_COOLDOWN = 400;
	public static final double JAW_REACH = 3.0, JAW_LEASH = 1.8;
	public static final int JAW_TICKS = 80, JAW_COOLDOWN = 160, JAW_EVERY = 20;
	public static final float JAW_DAMAGE = 4.0F, JAW_GNAW = 2.0F;
	/** Kingdoms' wolves that answer the howl as the vanilla wolf does. */
	private static final java.util.Set<Identifier> WILD = java.util.Set.of(Identifier.parse("jaymoskingdoms:feral_wolf"),
		Identifier.parse("jaymoskingdoms:coyote"));

	public Howl() {
		super("howl", 1.0F, 26.0F, 5.0, 0.12, 0.2, 0.0, Items.BONE, 0x8A7A66);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Wolf;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		letGo(player, live);
		live.pack.clear();
		live.packTicks = live.sniff = live.rush = 0;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					Vec3 look = player.getLookAngle();
					player.setDeltaMovement(look.x * POUNCE_OUT, Math.max(POUNCE_UP, look.y * POUNCE_OUT), look.z * POUNCE_OUT);
					player.syncVelocity = true;
					live.rush = POUNCE_TICKS;
					sound(player, growl(), 1.0F, 1.1F);
					cooldown(player, live, R, POUNCE_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					howl(player, live, level);
					cooldown(player, live, G, PACK_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					live.sniff = SCENT_TICKS;
					sound(player, sniffSound(), 0.6F, 1.4F);
					cooldown(player, live, V, SCENT_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (live.latched >= 0) {
					letGo(player, live);
					return;
				}
				if (!ready(player, live, B)) {
					return;
				}
				LivingEntity target = aimed(player, JAW_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.hurtServer(level, player.damageSources().playerAttack(player), JAW_DAMAGE);
				Bleeding.open(target, BodyPart.BODY, true);
				live.latched = target.getId();
				live.latchTicks = JAW_TICKS;
				live.hurt = true;
				sound(player, growl(), 1.2F, 0.8F);
				cooldown(player, live, B, JAW_COOLDOWN);
			}
			default -> {
			}
		}
	}

	/** Every wild wolf in reach comes to hunt beside you. */
	private static void howl(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		live.pack.clear();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(PACK_RADIUS), Howl::wild)) {
			live.pack.add(mob.getId());
			if (mob.getTarget() == player) {
				mob.setTarget(null);
			}
			mob.getNavigation().moveTo(player, PACK_SPEED);
		}
		live.packTicks = live.pack.isEmpty() ? 0 : PACK_TICKS;
		sound(player, howlSound(), 3.0F, 0.55F);
		ring(level, ParticleTypes.CLOUD, player.position().add(0.0, 1.8, 0.0), 1.0, 10);
		if (live.pack.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.howl.no_pack").withStyle(ChatFormatting.GRAY));
		}
	}

	/** A wild wolf: an untamed vanilla wolf, or one of Kingdoms' feral wolves or coyotes. */
	private static boolean wild(Mob mob) {
		if (mob instanceof Wolf wolf) {
			return !wolf.isTame() && mob.isAlive();
		}
		return mob.isAlive() && WILD.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
	}

	private static net.minecraft.sounds.SoundEvent growl() {
		return SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.ANGRY).adultSounds().growlSound().value();
	}

	/** Vanilla has no howl of its own any more: the big wolf's call, pitched down. */
	private static net.minecraft.sounds.SoundEvent howlSound() {
		return SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.BIG).adultSounds().whineSound().value();
	}

	private static net.minecraft.sounds.SoundEvent sniffSound() {
		return SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.CLASSIC).adultSounds().pantSound().value();
	}

	private static void letGo(ServerPlayer player, XenoformPower.Live live) {
		live.latched = -1;
		live.latchTicks = 0;
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// the night is a wolf's
		if (level.isDarkOutside() && player.tickCount % 40 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, false, false));
		}
		if (live.rush > 0) {
			live.rush--;
			player.resetFallDistance();
			for (LivingEntity e : around(player, POUNCE_HIT + 1.0)) {
				if (e.getBoundingBox().inflate(POUNCE_HIT * 0.5).intersects(player.getBoundingBox())) {
					e.hurtServer(level, player.damageSources().playerAttack(player), POUNCE_DAMAGE);
					if (!Tripped.bind(e, player, POUNCE_DOWN)) {
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, POUNCE_DOWN, 3), player);
					}
					live.hurt = true;
					live.rush = 0;
					sound(player, growl(), 1.2F, 0.7F);
					break;
				}
			}
			if (player.onGround() && live.rush < POUNCE_TICKS - 3) {
				live.rush = 0;
			}
		}
		if (live.latched >= 0) {
			jaws(player, live, level);
		}
		if (live.packTicks > 0) {
			pack(player, live, level);
		}
		if (live.sniff > 0) {
			live.sniff--;
			if (live.sniff % 10 == 0) {
				scent(player, level);
			}
		}
	}

	/** Held in the jaws: dragged along, slowed, gnawed. */
	private static void jaws(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		Entity e = level.getEntity(live.latched);
		if (!(e instanceof LivingEntity held) || !held.isAlive() || held.distanceTo(player) > JAW_REACH * 2.0 || --live.latchTicks <= 0) {
			letGo(player, live);
			return;
		}
		held.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 3, false, false), player);
		Vec3 jaw = player.position().add(player.getLookAngle().horizontal().normalize().scale(1.0));
		Vec3 pull = jaw.subtract(held.position());
		if (pull.horizontalDistance() > JAW_LEASH * 0.5) {
			held.setDeltaMovement(pull.x * 0.4, held.getDeltaMovement().y, pull.z * 0.4);
			held.syncVelocity = true;
		}
		if (live.latchTicks % JAW_EVERY == 0) {
			held.setInvulnerableTime(0);
			held.hurtServer(level, player.damageSources().playerAttack(player), JAW_GNAW);
			sound(player, growl(), 0.8F, 0.9F);
		}
	}

	/** The answering wolves: they keep near you and go for what you fight. */
	private static void pack(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		if (--live.packTicks <= 0) {
			live.pack.clear();
			return;
		}
		if (player.tickCount % 10 != 0) {
			return;
		}
		LivingEntity prey = player.getLastHurtMob();
		if (prey == null || !prey.isAlive() || prey.distanceTo(player) > PACK_RADIUS || player.tickCount - player.getLastHurtMobTimestamp() > 200) {
			prey = player.getLastHurtByMob();
		}
		if (prey != null && (!prey.isAlive() || prey.distanceTo(player) > PACK_RADIUS)) {
			prey = null;
		}
		var it = live.pack.iterator();
		while (it.hasNext()) {
			Entity e = level.getEntity(it.next());
			if (!(e instanceof Mob wolf) || !wolf.isAlive()) {
				it.remove();
				continue;
			}
			if (wolf.getTarget() == player || prey != null && wolf.getTarget() != prey) {
				wolf.setTarget(prey);
			}
			if (prey == null && wolf.distanceTo(player) > PACK_FOLLOW) {
				wolf.getNavigation().moveTo(player, PACK_SPEED);
			}
		}
	}

	/** The blood near you shows, to you alone, and every bleeder in reach glows. */
	private static void scent(ServerPlayer player, ServerLevel level) {
		DustParticleOptions red = new DustParticleOptions(0xB01818, 1.2F);
		for (BloodTrail.Mark mark : BloodTrail.near(level, player.position(), SCENT_RANGE, m -> true)) {
			level.sendParticles(player, red, true, false, mark.x, mark.y + 0.15, mark.z, 2, 0.15, 0.05, 0.15, 0.0);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SCENT_RANGE),
			e -> e != player && e.isAlive() && Bleeding.bleeding(e) != 0)) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false), player);
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CLOUD, player.position().add(0.0, 1.0, 0.0), 30, 0.8, 0.05);
		sound(player, howlSound(), 3.0F, 0.6F);
		return true;
	}
}
