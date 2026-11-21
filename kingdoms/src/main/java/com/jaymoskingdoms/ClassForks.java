package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jspecify.annotations.Nullable;

/**
 * The crossroads the old class pages got when they became trees (class trees, docs/ClassTrees-Plan.md), each at class
 * level 10:
 * <ul>
 * <li><b>Thief: Assassin or Acrobat.</b> An Assassin's sneak attack on a foe that doesn't know they're there hits far
 * harder ({@link ThiefSneak}). An Acrobat climbs faster (a mixin on the climb), steps up a block at a run without
 * jumping (a vault), and falls further unhurt and lands softer.</li>
 * <li><b>Mage: Evoker or Warder.</b> An Evoker's blasting spells hit harder and never hurt their friends. A Warder's wards
 * (Warding, Sanctuary, Bulwark) last longer, and Warding soaks a step more.</li>
 * <li><b>Ranger: Hunter or Beast Master.</b> A Hunter does more to the favoured foe and reads its trail further
 * ({@link TrailReading}). A Beast Master bonds with a beast they tamed (sneak and the class key, looking at it): it grows
 * with their Ranger level, tougher, harder-biting and a little bigger.</li>
 * </ul>
 */
public final class ClassForks {
	/** Assassin: the sneak attack on the unaware is the blow times this plus this, in place of the Thief's own. */
	static final float ASSASSIN_MULT = 2.5F, ASSASSIN_FLAT = 4.0F;
	/** Acrobat: climbing this many times as fast; a step this much higher at a run; this much more fall unhurt; fall damage cut by this share. */
	public static final double ACROBAT_CLIMB = 1.6;
	static final double ACROBAT_STEP = 0.5, ACROBAT_SAFE_FALL = 4.0, ACROBAT_SOFT_LANDING = 0.5;
	/** Evoker: blasting spells hit this much harder. */
	static final float EVOKER_DAMAGE = 1.3F;
	/** Warder: wards last this many times as long; Warding's absorption a step stronger. */
	static final float WARDER_LONGER = 1.5F;
	static final int WARDER_SOAK = 1;
	/** The wards a Warder's last longer. */
	static final Set<String> WARDS = Set.of("warding", "sanctuary", "bulwark");
	/** Hunter: this share more on the favoured foe; the foe's trail read this many times as far. */
	static final double HUNTER_DAMAGE = 0.25, HUNTER_TRACK = 1.5;
	/** Beast Master: a bonded beast's gains per Ranger level (health, bite, size share). */
	static final double BEAST_HEALTH = 1.0, BEAST_BITE = 0.2, BEAST_SIZE = 0.01;

	public static final String ASSASSIN = "thief_assassin", ACROBAT = "thief_acrobat", EVOKER = "mage_evoker", WARDER = "mage_warder",
		HUNTER = "ranger_hunter", BEASTS = "ranger_beasts";

	/** A Beast Master's bonded beast. Saved, kept through death. */
	static final AttachmentType<UUID> BEAST = AttachmentRegistry.create(JaymosKingdoms.id("ranger_beast"),
		b -> b.persistent(UUIDUtil.CODEC).copyOnDeath());

	private ClassForks() {
	}

	static void register() {
		// an Evoker's blasts spare their friends
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(source.getEntity() instanceof ServerPlayer mage
			&& entity != mage && blast(source, mage) && ClassTrees.has(mage, EVOKER) > 0 && friend(mage, entity)));
		ServerTickEvents.END_SERVER_TICK.register(ClassForks::tick);
	}

	/** One of {@code mage}'s spells: magic, a blast, or a fiery shot that isn't an arrow. */
	private static boolean blast(DamageSource source, ServerPlayer mage) {
		Entity direct = source.getDirectEntity();
		return source.is(DamageTypeTags.WITCH_RESISTANT_TO) || source.is(DamageTypeTags.IS_EXPLOSION)
			|| source.is(DamageTypeTags.IS_FIRE) && direct instanceof Projectile && !(direct instanceof AbstractArrow);
	}

	/** Someone an Evoker's blast leaves alone: a player, the townsfolk, their own beasts and golems, unless it's after them. */
	static boolean friend(Player mage, LivingEntity e) {
		if (e instanceof Mob mob && mob.getTarget() == mage) {
			return false;
		}
		return e instanceof Player || e instanceof AbstractVillager || e instanceof Folk || e instanceof IronGolem
			|| e instanceof OwnableEntity pet && pet.getOwnerReference() != null && mage.getUUID().equals(pet.getOwnerReference().getUUID());
	}

	/** What a tree fork does to a blow ({@link TreePerks#dealt}): an Evoker's blasts and a Hunter's favoured foe. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer player) || target == player) {
			return amount;
		}
		if (blast(source, player) && ClassTrees.has(player, EVOKER) > 0) {
			amount *= 1.0F + (EVOKER_DAMAGE - 1.0F) * Math.min(1.0F, ClassTrees.power(player, EVOKER));
		}
		if (ClassTrees.has(player, HUNTER) > 0 && Rangers.isFoe(Rangers.foe(player), target)) {
			amount *= (float) (1.0 + HUNTER_DAMAGE * Math.min(1.0F, ClassTrees.power(player, HUNTER)));
		}
		return amount;
	}

	/** A Warder's ward cast: how much longer it lasts (1 for anyone else, or any other spell). */
	static float wardPower(Player player, String spell) {
		return WARDS.contains(spell) && ClassTrees.has(player, WARDER) > 0 ? WARDER_LONGER : 1.0F;
	}

	/** Warding's absorption step for {@code player}: a Warder's soaks one more. */
	static int wardSoak(Player player) {
		return ClassTrees.has(player, WARDER) > 0 ? WARDER_SOAK : 0;
	}

	/** How many times as far a Hunter reads the favoured foe's trail (1 for anyone else). */
	static double trackReach(Player player) {
		return ClassTrees.has(player, HUNTER) > 0 ? HUNTER_TRACK : 1.0;
	}

	/** Whether {@code player} climbs as an Acrobat (both sides: the client climbs its own player). */
	public static boolean acrobat(Player player) {
		return ClassTrees.has(player, ACROBAT) > 0;
	}

	/**
	 * A Beast Master's sneak and class key on a beast they tamed: it becomes their bonded beast (any other goes back to an
	 * ordinary pet). False when it wasn't that (the key goes on to claim the land).
	 */
	static boolean bond(ServerPlayer player) {
		if (ClassTrees.has(player, BEASTS) == 0 || !(Arcana.sighted(player) instanceof TamableAnimal beast) || !beast.isTame() || !beast.isOwnedBy(player)) {
			return false;
		}
		LivingEntity old = beast(player);
		if (old != null && old != beast) {
			grow(old, 0);
		}
		player.setAttached(BEAST, beast.getUUID());
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ranger.bonded", beast.getDisplayName()));
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 0.8F);
		level.sendParticles(Fx.mote(0x3E8A5A, 1.0F), beast.getX(), beast.getY(0.7), beast.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
		return true;
	}

	/** {@code player}'s bonded beast, if it's in their world and alive. */
	static @Nullable LivingEntity beast(ServerPlayer player) {
		UUID id = player.getAttached(BEAST);
		return id != null && player.level().getEntity(id) instanceof LivingEntity beast && beast.isAlive() ? beast : null;
	}

	/** A bonded beast at Ranger level {@code level} (0 takes the growth off). */
	private static void grow(LivingEntity beast, float level) {
		modifier(beast, Attributes.MAX_HEALTH, BEAST_HEALTH * level);
		modifier(beast, Attributes.ATTACK_DAMAGE, BEAST_BITE * level);
		modifier(beast, Attributes.SCALE, BEAST_SIZE * level);
		if (beast.getHealth() > beast.getMaxHealth()) {
			beast.setHealth(beast.getMaxHealth());
		}
	}

	private static void modifier(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double amount) {
		AttributeInstance instance = e.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		net.minecraft.resources.Identifier key = JaymosKingdoms.id("beast_master");
		if (amount <= 0.0) {
			instance.removeModifier(key);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** An Acrobat's legs (four times a second, for the run's vault), a bonded beast's growth (once a second). */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 5 != 0) {
			return;
		}
		boolean second = server.getTickCount() % 20 == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean acrobat = acrobat(player);
			Skills.modifier(player, Attributes.SAFE_FALL_DISTANCE, "acrobat_fall", acrobat ? ACROBAT_SAFE_FALL : 0.0, AttributeModifier.Operation.ADD_VALUE);
			Skills.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, "acrobat_landing", acrobat ? -ACROBAT_SOFT_LANDING : 0.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			Skills.modifier(player, Attributes.STEP_HEIGHT, "acrobat_vault", acrobat && player.isSprinting() ? ACROBAT_STEP : 0.0,
				AttributeModifier.Operation.ADD_VALUE);
			LivingEntity beast = second && player.getAttached(BEAST) != null ? beast(player) : null;
			if (beast != null) {
				float level = ClassTrees.has(player, BEASTS) > 0
					? ClassTrees.level(player, PlayerClass.RANGER) * Math.max(Classes.SECOND_SHARE, Classes.share(player, PlayerClass.RANGER)) : 0.0F;
				grow(beast, level);
			}
		}
	}
}
