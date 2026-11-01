package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Fractures;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wounds, phase 3 (0.14.2): broken limbs. Andy (2026-10-19): "I NEED this worked in with our corpses and cripplings". A
 * limb that reaches zero is <b>broken</b> (an arm or a leg), <b>sprained</b> (a foot) or <b>grounded</b> (a wing), and
 * stays so ({@link #BROKEN}) until it has been <b>set</b> ({@link #SET}: for now a priest, a respawn, a Hermit Bean, a
 * Verdant's healing, Chrono's Bloom or Wind's Zephyr's Breath; phase 4's splint later) and has healed back above
 * {@link #BROKEN_CAP}. Until it's set, healing never lifts it above {@link #BROKEN_CAP} ({@link Wounds}).
 * <ul>
 * <li><b>Arm:</b> weaker, slower blows and slower digging; no shield or bow from that hand; the hand may drop what it
 * held as it breaks ({@link #ARM_DROP_CHANCE}). Both arms: a player can't swing, dig, place or use anything but
 * food, drink and medicine.</li>
 * <li><b>Leg:</b> slower, no sprinting, a lower jump. A player's two legs: crawl only ({@link Crawl}); a mob's every
 * leg: barely moving.</li>
 * <li><b>Foot:</b> a little slower while at zero; a sprain heals by itself (never capped, never needs setting).</li>
 * <li><b>Wings:</b> a flyer comes down and can't take off.</li>
 * </ul>
 * The effects are transient attribute modifiers refreshed every {@link #REFRESH_TICKS}, so they go cleanly with the
 * break. Kitchen's corpses read the scoreboard tags {@code jaymoskitchen.broken.<part>} as they read the missing parts.
 * (0.14.5) A broken leg also tags its owner {@link #CRIPPLED_TAG}, and freerunning asks {@link #legsUsable},
 * {@link #armsUsable} and {@link #gripUsable} (both sides).
 * A severed limb ({@link Dismember#MISSING}) stays broken and at zero, and takes Dismember's own effects instead.
 */
public final class Breaks {
	/** (0.14.2) An unset break never heals past this fraction of its part. */
	public static final float BROKEN_CAP = 0.25F;
	/** (0.14.2) The chance an arm's hand drops what it holds as the arm breaks. */
	public static final float ARM_DROP_CHANCE = 0.25F;
	/** (0.14.2) Per broken arm: attack damage, attack speed and digging speed lost (shares). */
	public static final double ARM_DAMAGE = -0.3, ARM_ATTACK_SPEED = -0.25, ARM_DIG = -0.3;
	/** (0.14.2) A broken leg: speed and jump lost (shares). */
	public static final double LEG_SPEED = -0.3, LEG_JUMP = -0.4;
	/** (0.14.2) A mob with every leg broken (both of a person's, front and hind, a spider's): speed and jump lost. */
	public static final double ALL_LEGS_SPEED = -0.7, ALL_LEGS_JUMP = -1.0;
	/** (0.14.2) Per sprained foot: speed lost. */
	public static final double FOOT_SPEED = -0.1;
	/** (0.14.2) A grounded flyer's fall (blocks per tick, at least), and its flying speed lost. */
	public static final double WING_FALL = 0.12, WING_FLY = -1.0;
	/** (0.14.2) How often the effects are refreshed and healed breaks cleared (ticks). */
	public static final int REFRESH_TICKS = 20;
	/** (0.14.4) A splinted break keeps only this share of its effects (Andy's splint: "lifts most of the debuff"). */
	public static final double SPLINT_KEEP = 0.25;
	/** (0.14.4) Per splint worn: speed lost (the splint's own stiffness). */
	public static final double SPLINT_SPEED = -0.05;
	/** (0.14.2) What Kitchen reads off a body for each broken part (then the part's name). */
	public static final String TAG_PREFIX = "jaymoskitchen.broken.";
	/**
	 * (0.14.5) On anything with a broken leg (any of its legs, splinted or not), put on and taken off with the refresh: for
	 * a later Kingdoms patch, whose hidden blade does 10x on a Crippled foe (Kingdoms never compiles against Powers).
	 */
	public static final String CRIPPLED_TAG = "jaymospowers_crippled";

	/** (0.14.2) The broken parts (a bit per {@link BodyPart#ordinal()}). Saved, synced to everyone (limps, hanging arms), not kept through death. */
	public static final AttachmentType<Integer> BROKEN = AttachmentRegistry.create(JaymosPowers.id("wounds_broken"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** (0.14.2) Of those, the ones that have been set (they heal normally). Saved, server side only. */
	public static final AttachmentType<Integer> SET = AttachmentRegistry.create(JaymosPowers.id("wounds_set"),
		builder -> builder.persistent(Codec.INT));

	/** (0.14.4) The broken parts in a splint (set, most of their effects lifted). Saved, synced to everyone (a splinted leg shows), not kept through death. */
	public static final AttachmentType<Integer> SPLINTED = AttachmentRegistry.create(JaymosPowers.id("wounds_splinted"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final Identifier SPLINT_SPEED_ID = JaymosPowers.id("wounds_splint_speed");
	private static final Identifier ARM_DAMAGE_ID = JaymosPowers.id("wounds_arm_damage");
	private static final Identifier ARM_SPEED_ID = JaymosPowers.id("wounds_arm_attack_speed");
	private static final Identifier ARM_DIG_ID = JaymosPowers.id("wounds_arm_dig");
	private static final Identifier LEG_SPEED_ID = JaymosPowers.id("wounds_leg_speed");
	private static final Identifier LEG_JUMP_ID = JaymosPowers.id("wounds_leg_jump");
	private static final Identifier FOOT_SPEED_ID = JaymosPowers.id("wounds_foot_speed");
	private static final Identifier WING_FLY_ID = JaymosPowers.id("wounds_wing_fly");
	/** Fractures' old permanent limp (before 0.14.2), taken off anything that still carries it. */
	private static final Identifier OLD_LIMP_SPEED_ID = JaymosPowers.id("broken_leg_speed");
	private static final Identifier OLD_LIMP_JUMP_ID = JaymosPowers.id("broken_leg_jump");

	private static final BodyPart[] ARMS = {BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM};
	private static final BodyPart[] LEGS = {BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG, BodyPart.FRONT_LEGS, BodyPart.HIND_LEGS, BodyPart.LEGS};
	private static final BodyPart[] FEET = {BodyPart.LEFT_FOOT, BodyPart.RIGHT_FOOT};
	/** (0.14.2) The feet's bits: sprains, never capped, healing by themselves. */
	private static final int SPRAINS = 1 << BodyPart.LEFT_FOOT.ordinal() | 1 << BodyPart.RIGHT_FOOT.ordinal();

	/** Everyone with a break (or whose break just went), until their effects are gone. Not saved: a reload finds them again. */
	private static final Set<LivingEntity> TRACKED = new HashSet<>();

	private Breaks() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof LivingEntity living)) {
				return;
			}
			// (Fractures' limp before 0.14.2 was a saved modifier: gone with its state)
			remove(living, Attributes.MOVEMENT_SPEED, OLD_LIMP_SPEED_ID);
			remove(living, Attributes.JUMP_STRENGTH, OLD_LIMP_JUMP_ID);
			if (broken(living) != 0) {
				TRACKED.add(living);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (broken(newPlayer) != 0) {
				// (back from the End: the same body, the same breaks)
				TRACKED.add(newPlayer);
				refresh(newPlayer);
			} else {
				clearTags(newPlayer, -1);
				crippled(newPlayer, 0);
				apply(newPlayer, 0, 0);
			}
		});
		// both arms gone: nothing can be swung, dug, placed or used, but food and drink still go down (and phase 4's
		// medicine); one arm: no shield or bow from its hand
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> handsGone(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> handsGone(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> armless(player) && !player.getItemInHand(hand).isEmpty()
			&& !consumable(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> armless(player) && !consumable(player.getItemInHand(hand))
			|| armBroken(player, hand) && twoHanded(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerTickEvents.END_SERVER_TICK.register(Breaks::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> TRACKED.clear());
	}

	// ---- queries (both sides: BROKEN is synced) ---------------------------------------------------------------------------

	/** The broken parts, a bit per {@link BodyPart#ordinal()}. */
	public static int broken(Entity entity) {
		Integer mask = entity.getAttached(BROKEN);
		return mask == null ? 0 : mask;
	}

	/**
	 * The broken parts that have been set (server side). (0.14.2) A sprained foot counts as set from the start: it heals
	 * as any part does, and the sprain goes once it's back above {@link #BROKEN_CAP}; no priest needed.
	 */
	public static int set(Entity entity) {
		Integer mask = entity.getAttached(SET);
		return ((mask == null ? 0 : mask) | SPRAINS) & broken(entity);
	}

	/** The broken parts not yet set: these never heal past {@link #BROKEN_CAP}. */
	public static int unset(Entity entity) {
		return broken(entity) & ~set(entity);
	}

	public static boolean isBroken(Entity entity, BodyPart part) {
		return (broken(entity) & bit(part)) != 0;
	}

	/** (0.14.4) The broken parts in a splint. */
	public static int splinted(Entity entity) {
		return entity.getAttachedOrElse(SPLINTED, 0) & broken(entity);
	}

	/** (0.14.4) Whether a splint can go on {@code part}: an arm or a leg, broken, still there, not splinted yet. */
	static boolean splintable(LivingEntity entity, BodyPart part) {
		boolean limb = part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM;
		for (BodyPart leg : LEGS) {
			limb |= part == leg;
		}
		return limb && isBroken(entity, part) && !missing(entity, part) && (splinted(entity) & bit(part)) == 0;
	}

	/** (0.14.4) A splint on {@code part}: set, and most of its effects lifted while it's worn (it comes off as the bone knits). */
	static boolean splint(LivingEntity entity, BodyPart part) {
		if (!splintable(entity, part)) {
			return false;
		}
		entity.setAttached(SET, set(entity) | bit(part));
		entity.setAttached(SPLINTED, splinted(entity) | bit(part));
		TRACKED.add(entity);
		refresh(entity);
		return true;
	}

	private static void unsplint(LivingEntity entity, int mask) {
		int left = entity.getAttachedOrElse(SPLINTED, 0) & ~mask;
		if (left == 0) {
			if (entity.hasAttached(SPLINTED)) {
				entity.removeAttached(SPLINTED);
			}
		} else {
			entity.setAttached(SPLINTED, left);
		}
	}

	static int bit(BodyPart part) {
		return 1 << part.ordinal();
	}

	/** {@link Dismember}'s bit for a person's part (a foot is its leg's), or 0 for a part Dismember doesn't know. */
	public static int limb(BodyPart part) {
		return switch (part) {
			case HEAD -> Dismember.HEAD;
			case RIGHT_ARM -> Dismember.RIGHT_ARM;
			case LEFT_ARM -> Dismember.LEFT_ARM;
			case RIGHT_LEG, RIGHT_FOOT -> Dismember.RIGHT_LEG;
			case LEFT_LEG, LEFT_FOOT -> Dismember.LEFT_LEG;
			default -> 0;
		};
	}

	/** The body part for {@link Dismember}'s bit (head, an arm or a leg), or null. */
	public static @Nullable BodyPart part(int limb) {
		return switch (limb) {
			case Dismember.HEAD -> BodyPart.HEAD;
			case Dismember.RIGHT_ARM -> BodyPart.RIGHT_ARM;
			case Dismember.LEFT_ARM -> BodyPart.LEFT_ARM;
			case Dismember.RIGHT_LEG -> BodyPart.RIGHT_LEG;
			case Dismember.LEFT_LEG -> BodyPart.LEFT_LEG;
			default -> null;
		};
	}

	/** Whether {@code part} has been cut off (Dismember): a foot goes with its leg. */
	static boolean missing(LivingEntity entity, BodyPart part) {
		int limb = limb(part);
		return limb != 0 && (Dismember.missing(entity) & limb) != 0;
	}

	/** The broken limbs as {@link Dismember} bits (arms and legs, not feet), for drawing them hanging limp. */
	public static int limbs(Entity entity) {
		int mask = broken(entity);
		int limbs = 0;
		for (BodyPart part : new BodyPart[] {BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG}) {
			if ((mask & bit(part)) != 0) {
				limbs |= limb(part);
			}
		}
		return limbs & ~Dismember.missing(entity);
	}

	/**
	 * How full each part of {@code plan} may heal (fractions, in plan order): 0 for a severed one, {@link #BROKEN_CAP} for
	 * an unset break, else 1. Null when nothing is capped (the common case).
	 */
	static float @Nullable [] caps(LivingEntity entity, BodyPlan plan) {
		int unset = unset(entity);
		int gone = Dismember.missing(entity);
		// (0.14.4) an open cut heals only so far, a festering part not at all
		int cut = Bleeding.bleeding(entity);
		int fester = Bleeding.festering(entity);
		if (unset == 0 && gone == 0 && cut == 0 && fester == 0) {
			return null;
		}
		java.util.List<Float> stored = fester == 0 ? null : entity.getAttached(Wounds.PARTS);
		float[] caps = new float[plan.size()];
		boolean any = false;
		for (int i = 0; i < caps.length; i++) {
			BodyPart part = plan.part(i);
			caps[i] = 1.0F;
			if (!part.vital() && missing(entity, part)) {
				caps[i] = 0.0F;
				any = true;
				continue;
			}
			if ((unset & bit(part)) != 0) {
				caps[i] = BROKEN_CAP;
				any = true;
			}
			if ((cut & bit(part)) != 0) {
				caps[i] = Math.min(caps[i], Bleeding.CUT_CAP);
				any = true;
			}
			if ((fester & bit(part)) != 0) {
				float now = stored != null && stored.size() == caps.length ? stored.get(i) : 1.0F;
				caps[i] = Math.min(caps[i], Math.max(0.0F, now));
				any = true;
			}
		}
		return any ? caps : null;
	}

	/** Whether the arm on {@code hand}'s side is broken (or gone). */
	static boolean armBroken(LivingEntity entity, InteractionHand hand) {
		boolean right = (entity.getMainArm() == HumanoidArm.RIGHT) == (hand == InteractionHand.MAIN_HAND);
		BodyPart arm = right ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		return isBroken(entity, arm) || missing(entity, arm);
	}

	/**
	 * (0.18.47) {@link #armless}, and if so said why: Jung (a Z Fighter, both arms broken) held left click and nothing hit
	 * or dug, with no word of it. The click is refused on the client before anything goes to the server, so it's said there.
	 */
	private static boolean handsGone(Player player) {
		if (!armless(player)) {
			return false;
		}
		long now = player.level().getGameTime();
		if (player.level().isClientSide() && (now - armlessSaidAt >= ARMLESS_NOTE_TICKS || now < armlessSaidAt)) {
			armlessSaidAt = now;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.armless").withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	/** (0.18.47) How a refused click is said: at most once this often (ticks), on the client. */
	static final int ARMLESS_NOTE_TICKS = 60;
	private static long armlessSaidAt = Long.MIN_VALUE / 2;

	/**
	 * (0.18.47) The share of attack damage {@code entity}'s broken arms take ({@link #ARM_DAMAGE} per arm; 0 to -0.6): a
	 * blow thrown with the legs (a Z Fighter's kick) divides it back out.
	 */
	public static double armDamageShare(LivingEntity entity) {
		AttributeInstance instance = entity.getAttribute(Attributes.ATTACK_DAMAGE);
		AttributeModifier modifier = instance == null ? null : instance.getModifier(ARM_DAMAGE_ID);
		return modifier == null ? 0.0 : modifier.amount();
	}

	/** A player with both arms broken (or one broken and the other gone), and at least one broken: no hands to use. */
	public static boolean armless(Player player) {
		int mask = broken(player);
		if ((mask & (bit(BodyPart.LEFT_ARM) | bit(BodyPart.RIGHT_ARM))) == 0) {
			return false;
		}
		// (0.14.4) an arm in a splint can still hold a cup or a spoon
		return armBroken(player, InteractionHand.MAIN_HAND) && armBroken(player, InteractionHand.OFF_HAND) && !splintedArm(player, InteractionHand.MAIN_HAND)
			&& !splintedArm(player, InteractionHand.OFF_HAND);
	}

	/** (0.14.4) Whether the arm on {@code hand}'s side is broken but splinted. */
	private static boolean splintedArm(LivingEntity entity, InteractionHand hand) {
		boolean right = (entity.getMainArm() == HumanoidArm.RIGHT) == (hand == InteractionHand.MAIN_HAND);
		BodyPart arm = right ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		return (splinted(entity) & bit(arm)) != 0 && !missing(entity, arm);
	}

	/** (0.14.2) What two broken arms can still get down: food, drink, potions, medicine (anything eaten or drunk). */
	static boolean consumable(ItemStack stack) {
		return stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE);
	}

	/** What a single broken arm can't work: a shield (anything that blocks) or a bow or crossbow. */
	static boolean twoHanded(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(Items.SHIELD) || stack.has(DataComponents.BLOCKS_ATTACKS) || stack.getItem() instanceof ProjectileWeaponItem);
	}

	/** A person whose two legs are both broken (not severed): crawling is all that's left. */
	public static boolean crawlOnly(Entity entity) {
		// (0.14.4) both splinted: a slow hobble, on their feet
		return entity instanceof LivingEntity living && BodyPlan.of(living) == BodyPlan.HUMANOID
			&& isBroken(living, BodyPart.LEFT_LEG) && isBroken(living, BodyPart.RIGHT_LEG)
			&& !missing(living, BodyPart.LEFT_LEG) && !missing(living, BodyPart.RIGHT_LEG)
			&& (splinted(living) & (bit(BodyPart.LEFT_LEG) | bit(BodyPart.RIGHT_LEG))) != (bit(BodyPart.LEFT_LEG) | bit(BodyPart.RIGHT_LEG));
	}

	/**
	 * (0.14.5) Whether {@code entity}'s legs carry it for freerunning: no leg broken (or gone) out of a splint. A splinted
	 * leg holds (its effects mostly lifted); a sprained foot is {@link #gripUsable}'s.
	 */
	public static boolean legsUsable(Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return true;
		}
		int bad = broken(living) & ~splinted(living);
		for (BodyPart part : LEGS) {
			if ((bad & bit(part)) != 0 || missing(living, part)) {
				return false;
			}
		}
		return true;
	}

	/** (0.14.5) Whether {@code entity} has an arm to freerun with: not both broken (or gone), splinted or not. */
	public static boolean armsUsable(Entity entity) {
		return !(entity instanceof LivingEntity living) || !(armBroken(living, InteractionHand.MAIN_HAND) && armBroken(living, InteractionHand.OFF_HAND));
	}

	/**
	 * (0.14.5) Whether {@code entity} can hold on to a wall or a bar (freerunning's climb and bar swing): both arms sound
	 * (none broken or gone, splinted or not) and neither foot sprained.
	 */
	public static boolean gripUsable(Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return true;
		}
		return !armBroken(living, InteractionHand.MAIN_HAND) && !armBroken(living, InteractionHand.OFF_HAND) && (broken(living) & SPRAINS) == 0;
	}

	// ---- breaking, setting, knitting --------------------------------------------------------------------------------------

	/**
	 * Marks {@code part} broken (it has just reached zero): the snap, the message, an arm's hand maybe dropping what it
	 * held, and the effects at once. True if it wasn't broken before. Head and body never break (they down or kill).
	 */
	static boolean mark(LivingEntity entity, BodyPart part, @Nullable Entity by) {
		return mark(entity, part, by, false);
	}

	/**
	 * As {@link #mark(LivingEntity, BodyPart, Entity)}; {@code healing}: (0.14.2) the break comes set (a fall's: it heals by
	 * itself, the limp or crawl lasting only until it's back above {@link #BROKEN_CAP}).
	 */
	static boolean mark(LivingEntity entity, BodyPart part, @Nullable Entity by, boolean healing) {
		BodyPlan plan = BodyPlan.of(entity);
		if (part.vital() || plan == null || plan.index(part) < 0 || !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		int mask = broken(entity);
		if ((mask & bit(part)) != 0) {
			// broken again: a bone that was set comes unset (a sprain or a lost part has nothing to set)
			Integer was = entity.getAttached(SET);
			if (!healing && was != null && (was & bit(part)) != 0 && (SPRAINS & bit(part)) == 0 && !missing(entity, part)) {
				int left = was & ~bit(part);
				if (left == 0) {
					entity.removeAttached(SET);
				} else {
					entity.setAttached(SET, left);
				}
				// (0.14.4) and the splint's no good on a fresh break
				unsplint(entity, bit(part));
			}
			return false;
		}
		entity.setAttached(BROKEN, mask | bit(part));
		unsplint(entity, bit(part));
		Integer set = entity.getAttached(SET);
		if (set != null && (set & bit(part)) != 0) {
			// a fresh break isn't set
			int left = set & ~bit(part);
			if (left == 0) {
				entity.removeAttached(SET);
			} else {
				entity.setAttached(SET, left);
			}
		}
		if (healing) {
			entity.setAttached(SET, set(entity) | bit(part));
		}
		entity.addTag(TAG_PREFIX + name(part));
		TRACKED.add(entity);
		boolean gone = missing(entity, part);
		int limb = limb(part);
		if (!gone) {
			if (limb != 0 && plan == BodyPlan.HUMANOID) {
				Fractures.crack(level, entity, limb);
			} else {
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
			}
			if ((part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM) && entity.getRandom().nextFloat() < ARM_DROP_CHANCE) {
				// (Dismember's: the stack leaves the hand before it's dropped, so nothing is doubled)
				Dismember.emptyHand(level, entity, limb);
			}
			if (entity instanceof Player player) {
				String kind = part == BodyPart.LEFT_FOOT || part == BodyPart.RIGHT_FOOT ? "sprained" : "broken";
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds." + kind, part.displayName()).withStyle(ChatFormatting.RED));
			}
		}
		apply(entity, broken(entity), Dismember.missing(entity));
		return true;
	}

	/** Sets {@code part}'s break: from now on it heals as any part does, and the break goes once it's back above {@link #BROKEN_CAP}. True if it was broken and unset. */
	static boolean setPart(LivingEntity entity, BodyPart part) {
		if ((unset(entity) & bit(part)) == 0) {
			return false;
		}
		entity.setAttached(SET, set(entity) | bit(part));
		refresh(entity);
		return true;
	}

	/** Sets every break. True if any was unset. */
	static boolean mendAll(LivingEntity entity) {
		int unset = unset(entity);
		if (unset == 0) {
			return false;
		}
		entity.setAttached(SET, broken(entity));
		refresh(entity);
		if (entity instanceof Player player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.set").withStyle(ChatFormatting.GREEN));
		}
		return true;
	}

	/** Every break gone at once (full healing, a debug command). */
	public static void clearAll(LivingEntity entity) {
		boolean crawled = crawlOnly(entity);
		int mask = broken(entity);
		entity.removeAttached(BROKEN);
		entity.removeAttached(SET);
		unsplint(entity, -1);
		clearTags(entity, mask);
		crippled(entity, 0);
		apply(entity, 0, Dismember.missing(entity));
		uncrawl(entity, crawled);
	}

	/** A severed part grown back (Dismember's regrowth): set, so it heals from where it is. */
	public static void regrown(LivingEntity entity, int limbs) {
		int mask = 0;
		for (BodyPart part : BodyPart.values()) {
			if (!part.vital() && (limb(part) & limbs) != 0) {
				mask |= bit(part);
			}
		}
		if ((broken(entity) & mask) != 0) {
			entity.setAttached(SET, set(entity) | (broken(entity) & mask));
			refresh(entity);
		}
		// (0.14.4) no stump left to bleed, nothing left festering there
		Bleeding.regrown(entity, mask);
	}

	/**
	 * The refresh: set breaks that have healed past {@link #BROKEN_CAP} go (a severed part's never does), health is held
	 * to what the parts may hold, and the effects are brought in line.
	 */
	static void refresh(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		int mask = broken(entity);
		if (plan == null || mask == 0) {
			if (mask != 0) {
				entity.removeAttached(BROKEN);
				entity.removeAttached(SET);
				clearTags(entity, mask);
			}
			crippled(entity, 0);
			unsplint(entity, -1);
			apply(entity, 0, Dismember.missing(entity));
			return;
		}
		boolean crawled = crawlOnly(entity);
		// (keeps vanilla health no higher than the capped parts hold)
		Wounds.reconcile(entity);
		int set = set(entity);
		int healed = 0;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if ((set & bit(part)) != 0 && !missing(entity, part) && Wounds.fraction(entity, part) > BROKEN_CAP) {
				healed |= bit(part);
			}
		}
		// (a part the plan doesn't have can't heal: it just goes)
		for (BodyPart part : BodyPart.values()) {
			if ((mask & bit(part)) != 0 && plan.index(part) < 0) {
				healed |= bit(part);
			}
		}
		if (healed != 0) {
			mask &= ~healed;
			if (mask == 0) {
				entity.removeAttached(BROKEN);
				entity.removeAttached(SET);
			} else {
				entity.setAttached(BROKEN, mask);
				if ((set & mask) == 0) {
					entity.removeAttached(SET);
				} else {
					entity.setAttached(SET, set & mask);
				}
			}
			clearTags(entity, healed);
			// (0.14.4) the splint comes off as the bone knits
			unsplint(entity, healed);
			if (entity instanceof Player player) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.knit").withStyle(ChatFormatting.GREEN));
			}
		}
		apply(entity, mask, Dismember.missing(entity));
		crippled(entity, mask);
		uncrawl(entity, crawled);
		if (entity instanceof Player player && crawlOnly(player) && !player.hasAttached(Crawl.CRAWLING) && Crawl.canCrawl(player)
			&& !com.jaymospowers.core.Carry.isCarried(player)) {
			// both legs broken: down on the ground
			player.setAttached(Crawl.CRAWLING, true);
		}
	}

	/** A player held in a crawl by their legs who can stand again gets up (unless the downed state holds them). */
	private static void uncrawl(LivingEntity entity, boolean crawled) {
		if (crawled && !crawlOnly(entity) && entity instanceof Player player && !Downed.isDowned(player)) {
			player.removeAttached(Crawl.CRAWLING);
		}
	}

	private static void clearTags(LivingEntity entity, int mask) {
		for (BodyPart part : BodyPart.values()) {
			if ((mask & bit(part)) != 0) {
				entity.removeTag(TAG_PREFIX + name(part));
			}
		}
	}

	/** (0.14.5) {@link #CRIPPLED_TAG} on while any leg in {@code mask} is broken, off once none is. */
	private static void crippled(LivingEntity entity, int mask) {
		boolean leg = false;
		for (BodyPart part : LEGS) {
			leg |= (mask & bit(part)) != 0;
		}
		if (leg) {
			entity.addTag(CRIPPLED_TAG);
		} else {
			entity.removeTag(CRIPPLED_TAG);
		}
	}

	static String name(BodyPart part) {
		return part.name().toLowerCase(java.util.Locale.ROOT);
	}

	// ---- the effects ------------------------------------------------------------------------------------------------------

	/** The modifiers for the breaks in {@code mask} (severed parts, {@code gone}, take Dismember's effects instead). */
	static void apply(LivingEntity entity, int mask, int gone) {
		int legs = 0, legParts = 0, feet = 0, splints = 0;
		// (0.14.4) a splinted break keeps only SPLINT_KEEP of its effects
		double arms = 0.0, legWeight = 0.0;
		int splinted = entity.getAttachedOrElse(SPLINTED, 0) & mask;
		BodyPlan plan = BodyPlan.of(entity);
		for (BodyPart part : ARMS) {
			if ((mask & bit(part)) != 0 && (gone & limb(part)) == 0) {
				boolean splint = (splinted & bit(part)) != 0;
				arms += splint ? SPLINT_KEEP : 1.0;
				splints += splint ? 1 : 0;
			}
		}
		for (BodyPart part : LEGS) {
			if (plan != null && plan.index(part) >= 0) {
				legParts++;
			}
			if ((mask & bit(part)) != 0 && (limb(part) == 0 || (gone & limb(part)) == 0)) {
				legs++;
				boolean splint = (splinted & bit(part)) != 0;
				legWeight = Math.max(legWeight, splint ? SPLINT_KEEP : 1.0);
				splints += splint ? 1 : 0;
			}
		}
		for (BodyPart part : FEET) {
			if ((mask & bit(part)) != 0 && (gone & limb(part)) == 0) {
				feet++;
			}
		}
		boolean allLegs = legs > 0 && legs >= legParts && !(entity instanceof Player);
		boolean down = Downed.isDowned(entity);
		if (down) {
			// (the downed one's pace is the downed state's: no limp on top of it)
			legs = 0;
			feet = 0;
		}
		if (Pain.numb(entity)) {
			// (0.14.4) willow bark, poppy, leeches or drink: the pain's hidden, the slowness and weakness with it
			arms = 0.0;
			legs = 0;
			feet = 0;
		}
		modifier(entity, Attributes.ATTACK_DAMAGE, ARM_DAMAGE_ID, ARM_DAMAGE * arms);
		modifier(entity, Attributes.ATTACK_SPEED, ARM_SPEED_ID, ARM_ATTACK_SPEED * arms);
		modifier(entity, Attributes.BLOCK_BREAK_SPEED, ARM_DIG_ID, ARM_DIG * arms);
		modifier(entity, Attributes.MOVEMENT_SPEED, LEG_SPEED_ID, legs == 0 ? 0.0 : (allLegs ? ALL_LEGS_SPEED : LEG_SPEED) * legWeight);
		modifier(entity, Attributes.JUMP_STRENGTH, LEG_JUMP_ID, legs == 0 ? 0.0 : (allLegs ? ALL_LEGS_JUMP : LEG_JUMP) * legWeight);
		modifier(entity, Attributes.MOVEMENT_SPEED, FOOT_SPEED_ID, FOOT_SPEED * feet);
		modifier(entity, Attributes.MOVEMENT_SPEED, SPLINT_SPEED_ID, down ? 0.0 : SPLINT_SPEED * splints);
		modifier(entity, Attributes.FLYING_SPEED, WING_FLY_ID, (mask & bit(BodyPart.WINGS)) != 0 ? WING_FLY : 0.0);
		if (legs > 0 && entity.isSprinting()) {
			entity.setSprinting(false);
		}
	}

	/** {@code attribute}'s modifier {@code id} at {@code amount} (a share of the total), or gone at 0. */
	private static void modifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			if (instance.getModifier(id) != null) {
				instance.removeModifier(id);
			}
			return;
		}
		AttributeModifier now = instance.getModifier(id);
		if (now == null || now.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void remove(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null && instance.getModifier(id) != null) {
			instance.removeModifier(id);
		}
	}

	private static void tick(MinecraftServer server) {
		if (TRACKED.isEmpty()) {
			return;
		}
		boolean refresh = server.getTickCount() % REFRESH_TICKS == 0;
		// (a copy: refreshing can untrack, and a break can track another)
		for (LivingEntity body : new ArrayList<>(TRACKED)) {
			if (body.isRemoved() || !(body.level() instanceof ServerLevel)) {
				TRACKED.remove(body);
				continue;
			}
			if (!body.isAlive()) {
				continue;
			}
			if (refresh) {
				refresh(body);
				if (broken(body) == 0) {
					TRACKED.remove(body);
					continue;
				}
			}
			int mask = broken(body);
			if (mask == 0) {
				continue;
			}
			// (no sprinting on a broken leg)
			if (body.isSprinting() && (mask & (bit(BodyPart.LEFT_LEG) | bit(BodyPart.RIGHT_LEG) | bit(BodyPart.FRONT_LEGS)
				| bit(BodyPart.HIND_LEGS) | bit(BodyPart.LEGS))) != 0) {
				body.setSprinting(false);
			}
			// a broken arm can't hold a shield up or draw a bow
			if (body.isUsingItem() && armBroken(body, body.getUsedItemHand()) && twoHanded(body.getUseItem())) {
				body.stopUsingItem();
			}
			// grounded: a flyer comes down
			if ((mask & bit(BodyPart.WINGS)) != 0 && body instanceof Mob mob && !mob.onGround()) {
				mob.setNoGravity(false);
				Vec3 motion = mob.getDeltaMovement();
				mob.setDeltaMovement(motion.x * 0.6, Math.min(motion.y, -WING_FALL), motion.z * 0.6);
			}
		}
	}
}
