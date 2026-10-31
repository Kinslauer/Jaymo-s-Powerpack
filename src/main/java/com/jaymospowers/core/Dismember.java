package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dismemberment: a bladed weapon's blow can take off a head, an arm or a leg (humanoids only: {@link #DISMEMBERABLE}).
 * Any blade does it at {@link #BLADE_CHANCE}; Darktide's katana slash at {@link #KATANA_CHANCE} against humanoid mobs.
 * A player hit always has {@link #PLAYER_CHANCE}, whatever the weapon. Where the blow lands picks the part: a player's
 * aim (the height and side of the target their crosshair crosses), a mob's at random.
 * <ul>
 * <li>The part flies off as a {@link SeveredPart} and the body is drawn without it, to the corpse (Jaymo's Kitchen reads
 * the {@code jaymoskitchen.missing.<part>} tags).</li>
 * <li><b>Head</b>: a player dies on the spot and sees through their fallen head until they respawn. Zombies and
 * skeletons ({@link #HEADLESS_UNDEAD}) fight on without one; a headless skeleton's arrows go wild. Any other mob either
 * drops dead or staggers about blind for a few seconds first ({@link #STAGGER_CHANCE}).</li>
 * <li><b>Arm</b>: whatever that hand held falls, and it holds nothing more.</li>
 * <li><b>Leg</b>: slowed hard, and with both gone barely moving and never jumping.</li>
 * </ul>
 * A player's lost limbs never grow back by themselves, not even by dying (Andy, 0.18.84: a respawn keeps every lost arm
 * and leg, only a lost head comes back): only a Hermit Bean, being Verdant, or a Kingdoms heritage that mends (Bio-built on its own, Rubber-folk with a swallow) regrows them ({@link #regrow}). A blunt blow breaks a limb instead ({@link Fractures}).
 * <p>
 * (0.14.2) Wounds phase 3: no more flat random sever. A part comes off on <b>overkill</b> ({@link #severOnOverkill},
 * called by {@code Wounds}): a blade's blow on a limb that's already broken, or one that takes the head past zero, for more
 * than {@link #SEVER_OVERKILL} of that part's health, at {@code min(1, SEVER_BASE * scale)}. The old chances are the
 * scale: {@link #RATE} times the blade's chance over a plain blade's ({@link #BLADE_CHANCE}; the katana's is four
 * times), skeletons {@link #SKELETON_CHANCE_SCALE} times. A severed limb stays at zero and broken in Wounds.
 */
public final class Dismember {
	public static final int HEAD = 1, RIGHT_ARM = 2, LEFT_ARM = 4, RIGHT_LEG = 8, LEFT_LEG = 16;
	public static final int[] PARTS = {HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG};
	/** Chances per blow: any blade against a humanoid mob, the katana slash, any blow against a player. */
	public static final float BLADE_CHANCE = 0.05F, KATANA_CHANCE = 0.20F, PLAYER_CHANCE = 0.05F;
	/** Skeletons (dry bones, brittle joints) lose parts this many times as often (before {@link #RATE}). */
	public static final float SKELETON_CHANCE_SCALE = 3.0F;
	/** (0.11.101) Every chance to sever, times this (Andy: "way too many dismemberments and decapitations ... 50%"). */
	public static final float RATE = 0.5F;
	/** A blow has to land at least this hard (after armour) to sever anything: no taking heads with a tap. */
	public static final float MIN_DAMAGE = 3.0F;
	/** Share of the body from the top that's the head, and from the feet up that's the legs. */
	public static final double HEAD_SHARE = 0.22, LEG_SHARE = 0.4;
	/** A mob's blow picks the head this often; the rest is arms and legs evenly. */
	public static final float MOB_HEAD_CHANCE = 0.2F;
	/** A beheaded mob (not undead) staggers about first this often, for this long (ticks), else drops at once. */
	public static final float STAGGER_CHANCE = 0.5F;
	public static final int STAGGER_MIN = 40, STAGGER_MAX = 90;
	/** Speed and jump lost per leg, and with both gone. */
	public static final double LEG_SPEED = -0.45, LEGS_SPEED = -0.85, LEG_JUMP = -0.5;
	/** A headless archer's arrows are thrown off by up to this much (degrees). */
	public static final float BLIND_SPREAD = 28.0F;
	/** Blood from a fresh stump: how long (ticks) and how often. */
	public static final int BLEED_TICKS = 200, BLEED_EVERY = 6;
	/** (0.14.2) Overkill: a blow past this share of a part's health (on a broken limb, or past the head's zero) can sever it. */
	public static final float SEVER_OVERKILL = 0.5F;
	/** (0.14.2) Overkill's chance to sever, times the blade's scale ({@link #scale}). */
	public static final float SEVER_BASE = 0.6F;

	/** The parts this creature has lost (a {@link #HEAD}... bitmask). Saved, synced to everyone, not kept through death. */
	/** (0.18.84) Kingdoms' Rubber-folk heritage tag: a blade severs their limbs this many times as often. */
	static final String RUBBER_TAG = "jaymoskingdoms_heritage_rubberfolk";
	static final float RUBBER_SEVER_SCALE = 2.0F;
	public static final AttachmentType<Integer> MISSING = AttachmentRegistry.create(JaymosPowers.id("missing_parts"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Who can lose parts: people and things built like them. */
	public static final TagKey<EntityType<?>> DISMEMBERABLE = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("dismemberable"));
	/** Who fights on without a head. */
	public static final TagKey<EntityType<?>> HEADLESS_UNDEAD = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("headless_undead"));
	/** Weapons that cut (besides swords and axes, and anything named like a blade). */
	public static final TagKey<net.minecraft.world.item.Item> SEVERING = TagKey.create(Registries.ITEM, JaymosPowers.id("severing"));
	public static final ResourceKey<DamageType> DECAPITATION = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("decapitation"));

	private static final String[] BLADE_WORDS = {"sword", "katana", "knife", "dagger", "cleaver", "glaive", "machete", "saber", "sabre",
		"ninjato", "scythe", "kunai", "halberd", "blade", "claymore", "hatchet", "war_axe", "battleaxe", "battle_axe", "scimitar", "rapier",
		"cutlass", "falchion", "tanto", "wakizashi", "naginata"};
	private static final Identifier LEG_SPEED_ID = JaymosPowers.id("missing_leg_speed");
	private static final Identifier LEG_JUMP_ID = JaymosPowers.id("missing_leg_jump");

	/** A beheaded mob staggering about: until when, and which way it's lurching. */
	private static final Map<LivingEntity, long[]> STAGGERING = new HashMap<>();
	/** Fresh stumps bleeding: the creature, until when. */
	private static final Map<LivingEntity, Long> BLEEDING = new HashMap<>();
	/** Players missing an arm (their hand is kept empty). */
	private static final List<ServerPlayer> ONE_ARMED = new ArrayList<>();
	/** Set while a power deals its own cut ({@link #cut}), so the held weapon's roll doesn't roll it again. */
	private static boolean rolling;

	/** (0.14.2) A power's own cut in progress: whom it's aimed at, where, by whom, its scale, and whether it took the part. */
	private static final class Cut {
		final LivingEntity target;
		final LivingEntity attacker;
		final int part;
		final float scale;
		boolean severed;

		Cut(LivingEntity target, LivingEntity attacker, int part, float scale) {
			this.target = target;
			this.attacker = attacker;
			this.part = part;
			this.scale = scale;
		}
	}

	private static @Nullable Cut cutting;

	private Dismember() {
	}

	public static void init() {
		SeveredPart.register();
		// (0.18.82) wooden and iron limbs
		Prosthetics.init();
		Fractures.init();
		BladeBlood.init();
		// (0.14.2) no blade roll of its own any more: Wounds calls severOnOverkill
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile projectile) {
				blindShot(projectile);
			}
			if (entity instanceof LivingEntity living && missing(living) != 0) {
				// back from a reload: the leg modifiers are saved, the rest is ticking state
				track(living);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// (Andy: lost limbs don't grow back on death) the stumps come along; only a lost head is back
			int mask = missing(newPlayer) & ~HEAD;
			for (int part : PARTS) {
				if ((mask & part) != 0) {
					newPlayer.addTag(tag(part));
				} else {
					newPlayer.removeTag(tag(part));
				}
			}
			if (mask == 0) {
				newPlayer.removeAttached(MISSING);
				legs(newPlayer, 0);
				return;
			}
			newPlayer.setAttached(MISSING, mask);
			track(newPlayer);
			legs(newPlayer, mask);
			if (!alive) {
				// a severed limb stays broken and at zero in Wounds, as a born-without one does
				for (int part : PARTS) {
					com.jaymospowers.core.wounds.BodyPart lost = (mask & part) != 0 ? com.jaymospowers.core.wounds.Breaks.part(part) : null;
					if (lost != null) {
						com.jaymospowers.core.wounds.Wounds.breakPart(newPlayer, lost, null);
					}
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Dismember::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STAGGERING.clear();
			BLEEDING.clear();
			ONE_ARMED.clear();
		});
	}

	// ---- queries -------------------------------------------------------------------------------------------------------

	public static int missing(Entity entity) {
		Integer mask = entity.getAttached(MISSING);
		return mask == null ? 0 : mask;
	}

	public static boolean headless(Entity entity) {
		return (missing(entity) & HEAD) != 0;
	}

	public static boolean canLoseParts(LivingEntity entity) {
		return entity instanceof Player || entity.getType().builtInRegistryHolder().is(DISMEMBERABLE);
	}

	/** Whether {@code stack} cuts: swords, axes (not pickaxes), and anything with a blade in its name. */
	public static boolean blade(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(SEVERING)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.contains("pickaxe")) {
			return false;
		}
		for (String word : BLADE_WORDS) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}

	/** The scoreboard tag Jaymo's Kitchen reads off a dying body for each missing part. */
	public static String tag(int part) {
		return "jaymoskitchen.missing." + name(part);
	}

	public static String name(int part) {
		return switch (part) {
			case HEAD -> "head";
			case RIGHT_ARM -> "right_arm";
			case LEFT_ARM -> "left_arm";
			case RIGHT_LEG -> "right_leg";
			default -> "left_leg";
		};
	}

	// ---- where a blow lands ----------------------------------------------------------------------------------------------

	/**
	 * The part a blow from {@code attacker} lands on: for a player, the part their crosshair crosses
	 * ({@link LimbBoxes#aimedPart}; for a body without parts, by height: top {@link #HEAD_SHARE} the head, bottom
	 * {@link #LEG_SHARE} a leg, between an arm, the side it's on picking which); for anything else, at random.
	 */
	public static int aimedPart(LivingEntity attacker, LivingEntity target) {
		if (!(attacker instanceof Player)) {
			var random = target.getRandom();
			if (random.nextFloat() < MOB_HEAD_CHANCE) {
				return HEAD;
			}
			return switch (random.nextInt(4)) {
				case 0 -> RIGHT_ARM;
				case 1 -> LEFT_ARM;
				case 2 -> RIGHT_LEG;
				default -> LEFT_LEG;
			};
		}
		Vec3 eye = attacker.getEyePosition();
		Vec3 look = attacker.getLookAngle();
		Integer limb = LimbBoxes.aimedPart(target, eye, look);
		if (limb != null) {
			return limb;
		}
		AABB box = target.getBoundingBox();
		Vec3 at = box.inflate(0.1).clip(eye, eye.add(look.scale(16.0))).orElse(null);
		if (at == null) {
			// (a sweep that didn't cross it): where the look passes at its distance
			double flat = Math.max(0.01, look.horizontalDistance());
			double dist = Math.sqrt(Math.pow(target.getX() - eye.x, 2) + Math.pow(target.getZ() - eye.z, 2));
			at = new Vec3(target.getX(), eye.y + look.y / flat * dist, target.getZ());
		}
		double share = (at.y - box.minY) / Math.max(0.01, box.getYsize());
		// its right, as it faces
		Vec3 right = Vec3.directionFromRotation(0.0F, target.yBodyRot + 90.0F);
		boolean onRight = at.subtract(target.position()).dot(right) < 0.0;
		if (share >= 1.0 - HEAD_SHARE) {
			return HEAD;
		}
		if (share <= LEG_SHARE) {
			return onRight ? RIGHT_LEG : LEFT_LEG;
		}
		return onRight ? RIGHT_ARM : LEFT_ARM;
	}

	/**
	 * A power's own cut: the blow is dealt by the caller inside {@code blow}, landing on {@code part} (Wounds routes it
	 * there), and it takes the part off on overkill ({@link #severOnOverkill}) at this cut's own scale: {@code mobChance}
	 * against a humanoid mob as against {@link #BLADE_CHANCE} (a player always as {@link #PLAYER_CHANCE}). (0.14.2) Before,
	 * a flat roll after the blow. True if the part came off.
	 */
	public static boolean cut(LivingEntity attacker, LivingEntity target, int part, float mobChance, Runnable blow) {
		float before = target.getHealth();
		Cut cut = new Cut(target, attacker, part, scale(target, mobChance));
		Cut outer = cutting;
		cutting = cut;
		boolean was = rolling;
		rolling = true;
		try {
			blow.run();
		} finally {
			rolling = was;
			cutting = outer;
		}
		BladeBlood.splash(target, before - target.getHealth());
		return cut.severed;
	}

	/** (0.14.2) Where a power's cut on {@code victim} is aimed (Wounds lands the blow there), or null if none is. */
	public static com.jaymospowers.core.wounds.@Nullable BodyPart cutAim(LivingEntity victim) {
		Cut cut = cutting;
		return cut != null && cut.target == victim ? com.jaymospowers.core.wounds.Breaks.part(cut.part) : null;
	}

	/**
	 * (0.14.2) Overkill on {@code part} of {@code target} (Wounds has checked the blow: a broken limb, or the head past
	 * zero, by more than {@link #SEVER_OVERKILL}). A power's cut ({@link #cut}) rolls at its own scale; any other blow only
	 * with a blade in the striking hand, at a plain blade's. Rolls {@code min(1, SEVER_BASE * scale)} and cuts it off.
	 * True if it came off.
	 */
	public static boolean severOnOverkill(LivingEntity target, int part, DamageSource source) {
		if (!(target.level() instanceof ServerLevel) || !canLoseParts(target) || target instanceof Player p && (p.isCreative() || p.isSpectator())
			|| (missing(target) & part) != 0) {
			return false;
		}
		Cut cut = cutting;
		LivingEntity attacker;
		float scale;
		if (cut != null && cut.target == target) {
			attacker = cut.attacker;
			scale = cut.scale;
		} else {
			if (rolling || !(source.getDirectEntity() instanceof LivingEntity striker) || source.getEntity() != striker || striker == target
				|| !blade(striker.getMainHandItem())) {
				return false;
			}
			attacker = striker;
			scale = scale(target, BLADE_CHANCE);
		}
		// (0.18.84) Andy: rubber limbs come off easier (they grow back with a swallow, Kingdoms' RubberFolk)
		if (target.entityTags().contains(RUBBER_TAG)) {
			scale *= RUBBER_SEVER_SCALE;
		}
		// (0.17.183) Will's Grim Hand: likelier (Kingdoms)
		scale *= com.jaymospowers.core.wounds.WillSkill.severScale(attacker, target);
		if (target.getRandom().nextFloat() >= Math.min(1.0F, SEVER_BASE * scale)) {
			return false;
		}
		boolean severed = sever(target, part, attacker);
		if (severed && cut != null && cut.target == target) {
			cut.severed = true;
		}
		return severed;
	}

	/**
	 * (0.14.2) A blow's scale on overkill's chance: the old chance it had ({@code base} against a humanoid mob, raised for
	 * skeletons; {@link #PLAYER_CHANCE} against a player) over a plain blade's, times {@link #RATE}.
	 */
	static float scale(LivingEntity target, float base) {
		float chance = target instanceof Player ? PLAYER_CHANCE : mobChance(target, base);
		return RATE * chance / BLADE_CHANCE;
	}

	/** A humanoid mob's chance to lose a part to a blow ({@code base}), raised for skeletons. */
	static float mobChance(LivingEntity target, float base) {
		return target.getType().builtInRegistryHolder().is(EntityTypeTags.SKELETONS) ? Math.min(1.0F, base * SKELETON_CHANCE_SCALE) : base;
	}

	// ---- severing --------------------------------------------------------------------------------------------------------

	/** Takes {@code part} off {@code target} (if it still has it), with everything that follows. True if it came off. */
	public static boolean sever(LivingEntity target, int part, @Nullable LivingEntity by) {
		return lose(target, part, by, false);
	}

	/**
	 * (0.15.148) Jaymo's Kingdoms (an Archer's headshot, by reflection): {@code target}'s head comes off, and instead of
	 * flying it's nailed to the wall at {@code wall}, facing {@code yaw}. The body is left as any beheading leaves it.
	 * True if the head came off.
	 */
	public static boolean beheadPinned(LivingEntity target, @Nullable LivingEntity by, Vec3 wall, float yaw) {
		if (!canLoseParts(target)) {
			return false;
		}
		SeveredPart.pinNextAt = wall;
		SeveredPart.pinNextYaw = yaw;
		try {
			return lose(target, HEAD, by, false);
		} finally {
			SeveredPart.pinNextAt = null;
		}
	}

	/**
	 * A skeleton's part smashed to pieces by a blunt blow ({@link Fractures}): gone like a severed one, but nothing flies
	 * off and nothing bleeds, just a shower of bone and a bone or two on the ground. True if it went.
	 */
	public static boolean shatter(LivingEntity target, int part, @Nullable LivingEntity by) {
		return lose(target, part, by, true);
	}

	/**
	 * (0.17.1) Jaymo's Kingdoms (a life story's "unarmed" start, by reflection): {@code target} has long been without
	 * {@code part} (an arm or a leg), an old stump healed over: missing like any lost part, but nothing flies off, nothing
	 * bleeds, nothing shows. It grows back the usual ways. True if it went.
	 */
	public static boolean bornWithout(LivingEntity target, int part) {
		if (part == HEAD || !(target.level() instanceof ServerLevel level) || (missing(target) & part) != 0) {
			return false;
		}
		int mask = missing(target) | part;
		target.setAttached(MISSING, mask);
		target.addTag(tag(part));
		track(target);
		com.jaymospowers.core.wounds.BodyPart lost = com.jaymospowers.core.wounds.Breaks.part(part);
		if (lost != null) {
			com.jaymospowers.core.wounds.Wounds.breakPart(target, lost, null);
		}
		if (part == RIGHT_ARM || part == LEFT_ARM) {
			emptyHand(level, target, part);
		} else {
			legs(target, mask);
		}
		return true;
	}

	private static boolean lose(LivingEntity target, int part, @Nullable LivingEntity by, boolean shattered) {
		if (!(target.level() instanceof ServerLevel level) || (missing(target) & part) != 0) {
			return false;
		}
		int mask = missing(target) | part;
		target.setAttached(MISSING, mask);
		target.addTag(tag(part));
		if (shattered) {
			Fractures.crack(level, target, part);
			Vec3 at = stump(target, part);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), at.x, at.y, at.z, 30,
				0.2, 0.2, 0.2, 0.3);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.0F, 0.6F);
			drop(level, target, new ItemStack(net.minecraft.world.item.Items.BONE, 1 + target.getRandom().nextInt(2)));
		} else {
			Vec3 from = by != null ? by.position() : target.position().add(target.getLookAngle().scale(-1.0));
			SeveredPart.spawn(level, target, part, from);
			gore(level, target, part);
			BLEEDING.put(target, level.getGameTime() + BLEED_TICKS);
		}
		track(target);
		if (part != HEAD) {
			// (0.14.2) the part is gone from Wounds too: at zero, broken, never healing until it grows back
			com.jaymospowers.core.wounds.BodyPart lost = com.jaymospowers.core.wounds.Breaks.part(part);
			if (lost != null) {
				com.jaymospowers.core.wounds.Wounds.breakPart(target, lost, by);
				if (!shattered) {
					// (0.14.4) and the stump bleeds until it's seared shut (cautery)
					com.jaymospowers.core.wounds.Bleeding.stump(target, lost);
				}
			}
		}
		switch (part) {
			case HEAD -> {
				ItemStack helmet = target.getItemBySlot(EquipmentSlot.HEAD);
				if (!helmet.isEmpty()) {
					target.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
					drop(level, target, helmet);
				}
				beheaded(level, target, by);
				// (0.17.183) Will's Last Look: the weaker who saw it run (Kingdoms)
				com.jaymospowers.core.wounds.WillSkill.beheaded(by, target);
			}
			case RIGHT_ARM, LEFT_ARM -> emptyHand(level, target, part);
			default -> legs(target, mask);
		}
		return true;
	}

	/**
	 * Lost parts grown back (Andy: a living player only ever regrows them as a Verdant or with a Hermit Bean; dying
	 * doesn't, 0.18.84): every one, or just the first still missing. True if anything grew back.
	 */
	public static boolean regrow(LivingEntity target, boolean all) {
		int mask = missing(target);
		if (mask == 0) {
			return false;
		}
		int before = mask;
		for (int part : PARTS) {
			if ((mask & part) != 0) {
				mask &= ~part;
				target.removeTag(tag(part));
				if (!all) {
					break;
				}
			}
		}
		if (mask == 0) {
			target.removeAttached(MISSING);
		} else {
			target.setAttached(MISSING, mask);
		}
		legs(target, mask);
		// (0.18.82) a wooden or iron limb comes off when the real one is back
		Prosthetics.sync(target);
		// (0.14.2) what grew back heals from where it is
		com.jaymospowers.core.wounds.Breaks.regrown(target, before & ~mask);
		if (target.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 14,
				0.35, 0.5, 0.35, 0.0);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHORUS_FLOWER_GROW, SoundSource.PLAYERS, 1.0F, 1.2F);
		}
		return true;
	}

	private static void beheaded(ServerLevel level, LivingEntity target, @Nullable LivingEntity by) {
		if (!target.isAlive()) {
			// a dead body loses its head: nothing more to it
			return;
		}
		if (target instanceof Player) {
			kill(level, target, by);
			return;
		}
		if (target.getType().builtInRegistryHolder().is(HEADLESS_UNDEAD)) {
			// it never needed it
			return;
		}
		if (target.getRandom().nextFloat() < STAGGER_CHANCE && target instanceof Mob) {
			long until = level.getGameTime() + Mth.nextInt(target.getRandom(), STAGGER_MIN, STAGGER_MAX);
			STAGGERING.put(target, new long[] {until, Float.floatToIntBits(target.getYRot())});
			if (target instanceof Mob mob) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
			return;
		}
		kill(level, target, by);
	}

	private static void kill(ServerLevel level, LivingEntity target, @Nullable LivingEntity by) {
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DECAPITATION), by);
		target.setInvulnerableTime(0);
		target.hurtServer(level, source, Float.MAX_VALUE);
	}

	/** Whatever the lost (or broken) arm's hand held falls. */
	public static void emptyHand(ServerLevel level, LivingEntity target, int part) {
		InteractionHand hand = hand(target, part);
		ItemStack held = target.getItemInHand(hand);
		if (!held.isEmpty()) {
			target.setItemInHand(hand, ItemStack.EMPTY);
			drop(level, target, held);
		}
	}

	/** The hand on the side of {@code part} (an arm). */
	public static InteractionHand hand(LivingEntity target, int part) {
		HumanoidArm side = part == RIGHT_ARM ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
		return target.getMainArm() == side ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
	}

	private static void drop(ServerLevel level, LivingEntity target, ItemStack stack) {
		if (target instanceof ServerPlayer player) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		} else {
			target.spawnAtLocation(level, stack);
		}
	}

	/** Slowed by the legs it's lost (none: back to normal). */
	static void legs(LivingEntity target, int mask) {
		// (0.18.82) a leg with a prosthetic isn't bare: Prosthetics adds its own, smaller penalty
		mask &= ~Prosthetics.fitted(target);
		int legs = ((mask & RIGHT_LEG) != 0 ? 1 : 0) + ((mask & LEFT_LEG) != 0 ? 1 : 0);
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
		if (legs == 0) {
			if (speed != null) {
				speed.removeModifier(LEG_SPEED_ID);
			}
			if (jump != null) {
				jump.removeModifier(LEG_JUMP_ID);
			}
			return;
		}
		if (speed != null) {
			speed.addOrReplacePermanentModifier(new AttributeModifier(LEG_SPEED_ID, legs == 2 ? LEGS_SPEED : LEG_SPEED,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (jump != null) {
			jump.addOrReplacePermanentModifier(new AttributeModifier(LEG_JUMP_ID, legs == 2 ? -1.0 : LEG_JUMP,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		target.setSprinting(false);
	}

	private static void gore(ServerLevel level, LivingEntity target, int part) {
		Vec3 at = stump(target, part);
		var blood = com.jaymospowers.core.blood.BloodParticles.fleck(BladeBlood.color(target), 1.4F);
		level.sendParticles(blood, at.x, at.y, at.z, 30, 0.2, 0.2, 0.2, 0.0);
		level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(BladeBlood.color(target), 2.0F), at.x, at.y, at.z, 24,
			0.15, 0.15, 0.15, 0.25);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.1);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.HOSTILE, 0.8F, 0.6F);
	}

	/** Where a part joins the body (in the world). */
	static Vec3 stump(LivingEntity target, int part) {
		double h = target.getBbHeight();
		Vec3 right = Vec3.directionFromRotation(0.0F, target.yBodyRot + 90.0F).scale(-target.getBbWidth() * 0.6);
		return switch (part) {
			case HEAD -> target.position().add(0.0, h * 0.78, 0.0);
			case RIGHT_ARM -> target.position().add(0.0, h * 0.72, 0.0).add(right);
			case LEFT_ARM -> target.position().add(0.0, h * 0.72, 0.0).subtract(right);
			case RIGHT_LEG -> target.position().add(0.0, h * 0.38, 0.0).add(right.scale(0.4));
			default -> target.position().add(0.0, h * 0.38, 0.0).subtract(right.scale(0.4));
		};
	}

	// ---- every tick ------------------------------------------------------------------------------------------------------

	private static void track(LivingEntity entity) {
		if (entity instanceof ServerPlayer player && (missing(player) & (RIGHT_ARM | LEFT_ARM)) != 0 && !ONE_ARMED.contains(player)) {
			ONE_ARMED.add(player);
		}
	}

	private static void tick(MinecraftServer server) {
		if (!STAGGERING.isEmpty()) {
			for (Iterator<Map.Entry<LivingEntity, long[]>> it = STAGGERING.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<LivingEntity, long[]> e = it.next();
				LivingEntity mob = e.getKey();
				if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
					it.remove();
					continue;
				}
				if (level.getGameTime() >= e.getValue()[0]) {
					it.remove();
					kill(level, mob, null);
				}
			}
		}
		if (!BLEEDING.isEmpty()) {
			for (Iterator<Map.Entry<LivingEntity, Long>> it = BLEEDING.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<LivingEntity, Long> e = it.next();
				LivingEntity body = e.getKey();
				if (body.isRemoved() || !(body.level() instanceof ServerLevel level) || level.getGameTime() >= e.getValue()) {
					it.remove();
					continue;
				}
				if (body.tickCount % BLEED_EVERY == 0) {
					int mask = missing(body);
					for (int part : PARTS) {
						if ((mask & part) != 0) {
							Vec3 at = stump(body, part);
							level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(0x7A0000, 1.1F), at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.0);
							level.sendParticles(ParticleTypes.DRIPPING_LAVA, at.x, at.y, at.z, 1, 0.05, 0.0, 0.05, 0.0);
						}
					}
				}
			}
		}
		if (!ONE_ARMED.isEmpty()) {
			for (Iterator<ServerPlayer> it = ONE_ARMED.iterator(); it.hasNext(); ) {
				ServerPlayer player = it.next();
				int mask = missing(player);
				if (player.isRemoved() || (mask & (RIGHT_ARM | LEFT_ARM)) == 0) {
					it.remove();
					continue;
				}
				if (!player.isAlive()) {
					continue;
				}
				for (int arm : new int[] {RIGHT_ARM, LEFT_ARM}) {
					// (0.18.82) a wooden or iron arm holds things
					if ((mask & arm) != 0 && (Prosthetics.fitted(player) & arm) == 0 && !player.getItemInHand(hand(player, arm)).isEmpty()) {
						// nothing to hold it with
						emptyHand(player.level(), player, arm);
					}
				}
			}
		}
	}

	/**
	 * A beheaded mob staggering: no thinking, no target, lurching forward and veering about. Called in place of its AI
	 * step; false if it isn't staggering.
	 */
	public static boolean stagger(Mob mob) {
		long[] state = STAGGERING.get(mob);
		if (state == null) {
			return false;
		}
		mob.setTarget(null);
		mob.getNavigation().stop();
		float yaw = Float.intBitsToFloat((int) state[1]) + (mob.getRandom().nextFloat() - 0.5F) * 40.0F;
		state[1] = Float.floatToIntBits(yaw);
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setSpeed((float) mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.6F);
		mob.setZza(mob.getRandom().nextFloat() < 0.8F ? 0.7F : 0.0F);
		mob.setXxa((mob.getRandom().nextFloat() - 0.5F) * 0.6F);
		mob.setJumping(false);
		return true;
	}

	/** A headless archer can't see what it shoots at: its arrows fly wide. */
	private static void blindShot(Projectile projectile) {
		if (projectile.tickCount > 0 || !(projectile.getOwner() instanceof Mob shooter) || !headless(shooter)) {
			return;
		}
		Vec3 v = projectile.getDeltaMovement();
		if (v.lengthSqr() < 1.0E-4) {
			return;
		}
		var random = shooter.getRandom();
		float yaw = (random.nextFloat() * 2.0F - 1.0F) * BLIND_SPREAD;
		float pitch = (random.nextFloat() * 2.0F - 1.0F) * BLIND_SPREAD * 0.6F;
		Vec3 turned = v.yRot(yaw * Mth.DEG_TO_RAD).xRot(pitch * Mth.DEG_TO_RAD);
		projectile.setDeltaMovement(turned);
		projectile.syncVelocity = true;
	}
}
