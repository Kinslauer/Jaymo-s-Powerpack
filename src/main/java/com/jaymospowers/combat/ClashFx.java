package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): "when swords clash or when parries occur there should be particle sparks", and "splintering wood
 * from shattering shields". What steel and wood throw off when they meet, for every fight and for the films:
 * <ul>
 * <li>{@link #sparks}: a shower of hot sparks off the point where two blades (or a blade and a metal shield) met. They
 * fly out to the sides of the blow, fall, bounce once and cool from white through orange to a dull red. One packet a
 * shower: the client makes the sparks ({@code client/combat/ClashFxClient}).</li>
 * <li>{@link #splinters}: shards of wood tumbling off a shield, with a few chips of plank.</li>
 * <li>{@link #met}: which of the two a blow on a guard throws, by what the guard and the weapon are made of
 * ({@link #wooden}, {@link ShieldBang#metal}).</li>
 * </ul>
 * Every parry calls it ({@link Guard#parried}, Darktide's blade, Kingdoms' fighters' parries and blade clashes through
 * its {@code PowersCompat}), and so does every blow a raised guard takes ({@link #blocked}, from {@code BlockFxMixin}):
 * small sparks off a blade or a metal shield, chips off a wooden one. A wooden shield that takes too much breaks apart
 * ({@link ShieldShatter}).
 */
public final class ClashFx {
	/** Sparks in a shower: a plain block's, a parry's, a perfect parry's or a clash of two swings. */
	public static final int BLOCK_SPARKS = 6, PARRY_SPARKS = 16, CLASH_SPARKS = 26;
	/** The same showers' size (1 = a spark about two pixels of a block across). */
	public static final float BLOCK_SCALE = 0.8F, PARRY_SCALE = 1.0F, CLASH_SCALE = 1.2F;
	/** The most sparks or shards one packet may ask a client for. */
	public static final int MAX_BURST = 64;
	/** Shards off a wooden shield per point of damage it took (at least one, at most {@link #CHIP_MAX}), and when it breaks. */
	public static final float CHIPS_PER_DAMAGE = 0.5F;
	public static final int CHIP_MAX = 6, SHATTER_SHARDS = 26;
	/** Players further than this (blocks) aren't sent a shower. */
	static final double RANGE = 48.0;
	/** A shield's planks, and the darker wood of its back (0xRRGGBB): the shards' two colours. */
	public static final int WOOD = 0x9A7B4F, DARK_WOOD = 0x6B5232;

	/** Shields that are wood whatever they're called (the vanilla shield, though its rim rings as metal). */
	public static final TagKey<Item> WOODEN_SHIELDS = TagKey.create(Registries.ITEM, JaymosPowers.id("wooden_shields"));
	/** Words in a shield's name that mean it isn't wood (besides the metals {@link ShieldBang#metal} knows). */
	private static final Set<String> HARD = Set.of("diamond", "obsidian", "crystal", "emerald", "amethyst", "grand", "bone",
		"stone", "ender", "dragon", "energy", "tower");

	/**
	 * A shower thrown from one point: {@code count} pieces flying mostly across ({@code dx}, {@code dy}, {@code dz}), the
	 * line of the blow that made them (zero: every way), each about {@code scale}; {@code color} tints shards (0xRRGGBB;
	 * sparks colour themselves by their heat).
	 */
	public record Burst(float dx, float dy, float dz, int count, float scale, int color, boolean wood) implements ParticleOptions {
		public static final MapCodec<Burst> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.FLOAT.optionalFieldOf("dx", 0.0F).forGetter(Burst::dx),
			Codec.FLOAT.optionalFieldOf("dy", 0.0F).forGetter(Burst::dy),
			Codec.FLOAT.optionalFieldOf("dz", 0.0F).forGetter(Burst::dz),
			Codec.INT.optionalFieldOf("count", PARRY_SPARKS).forGetter(Burst::count),
			Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Burst::scale),
			Codec.INT.optionalFieldOf("color", WOOD).forGetter(Burst::color),
			Codec.BOOL.optionalFieldOf("wood", false).forGetter(Burst::wood)).apply(i, Burst::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Burst> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Burst::dx, ByteBufCodecs.FLOAT, Burst::dy, ByteBufCodecs.FLOAT, Burst::dz,
			ByteBufCodecs.VAR_INT, Burst::count, ByteBufCodecs.FLOAT, Burst::scale, ByteBufCodecs.INT, Burst::color,
			ByteBufCodecs.BOOL, Burst::wood, Burst::new);

		@Override
		public ParticleType<?> getType() {
			return wood ? SPLINTERS : SPARKS;
		}
	}

	public static final ParticleType<Burst> SPARKS = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("clash_sparks"),
		FabricParticleTypes.complex(true, Burst.CODEC, Burst.STREAM_CODEC));
	public static final ParticleType<Burst> SPLINTERS = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("shield_splinters"),
		FabricParticleTypes.complex(true, Burst.CODEC, Burst.STREAM_CODEC));

	private ClashFx() {
	}

	/** Loads the class, which registers {@link #SPARKS} and {@link #SPLINTERS}. */
	public static void init() {
	}

	/**
	 * A shower of sparks at {@code at}: {@code count} of them, {@code scale} big, thrown mostly across {@code line} (the
	 * way the blow came; null: every way). Public for Kingdoms (its {@code PowersCompat}) and the films.
	 */
	public static void sparks(ServerLevel level, Vec3 at, @Nullable Vec3 line, int count, float scale) {
		send(level, at, burst(line, count, scale, 0xFFFFFF, false));
	}

	/**
	 * Shards of wood at {@code at}: {@code count} of them in {@code color} (0xRRGGBB, {@link #WOOD}), thrown mostly along
	 * {@code line} (the way the blow went; null: every way), with a few chips of plank among them.
	 */
	public static void splinters(ServerLevel level, Vec3 at, @Nullable Vec3 line, int count, int color) {
		send(level, at, burst(line, count, 1.0F, color, true));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), at.x, at.y, at.z,
			Math.max(1, count / 3), 0.12, 0.12, 0.12, 0.08);
	}

	/**
	 * A blow from {@code attacker} (null: an arrow or the like) met {@code defender}'s guard, held in {@code guard}:
	 * sparks if metal met metal (a blade on a blade, a blade on a metal shield), shards and a few sparks if a blade bit a
	 * wooden shield, shards alone for anything else on wood. {@code sparks} and {@code scale}: the shower a metal meeting
	 * throws ({@link #PARRY_SPARKS} and the like); {@code chips}: the shards a wooden one does.
	 */
	public static void met(ServerLevel level, LivingEntity defender, ItemStack guard, @Nullable LivingEntity attacker, @Nullable Vec3 from, int sparks,
		float scale, int chips) {
		Vec3 eye = defender.getEyePosition();
		Vec3 toward = from != null ? from.subtract(eye) : attacker != null ? attacker.getEyePosition().subtract(eye) : defender.getLookAngle();
		toward = toward.lengthSqr() > 1.0E-4 ? toward.normalize() : defender.getLookAngle();
		Vec3 at = eye.add(toward.scale(0.7)).add(0.0, -0.3, 0.0);
		boolean wood = wooden(guard);
		ItemStack weapon = attacker != null ? attacker.getMainHandItem() : ItemStack.EMPTY;
		boolean steel = attacker == null ? from != null : weapon.has(DataComponents.WEAPON) && !woodenWeapon(weapon);
		if (wood) {
			splinters(level, at, toward.scale(-1.0), chips, (defender.getRandom().nextBoolean() ? WOOD : DARK_WOOD));
			if (steel && attacker != null) {
				sparks(level, at, toward, Math.max(2, sparks / 4), scale * 0.8F);
			}
		} else if (steel || attacker == null) {
			sparks(level, at, toward, sparks, scale);
		} else if (!guard.isEmpty()) {
			// a fist or a claw on steel: a few
			sparks(level, at, toward, Math.max(2, sparks / 3), scale * 0.8F);
		}
	}

	/**
	 * Every blow a raised guard took a share of ({@code LivingEntity.applyItemBlocking}, {@code BlockFxMixin}): a few
	 * sparks or chips off it, and a wooden shield wears toward breaking ({@link ShieldShatter#took}). {@code guard} is
	 * what was held up when the blow came.
	 */
	public static void blocked(LivingEntity defender, ServerLevel level, DamageSource source, ItemStack guard, float taken) {
		if (taken <= 0.0F || guard.isEmpty() || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)) {
			return;
		}
		LivingEntity attacker = source.getDirectEntity() instanceof LivingEntity living && living != defender ? living : null;
		Vec3 from = source.getSourcePosition();
		if (attacker == null && from == null) {
			return;
		}
		int chips = Mth.clamp(Mth.ceil(taken * CHIPS_PER_DAMAGE), 1, CHIP_MAX);
		met(level, defender, guard, attacker, from, BLOCK_SPARKS + Math.min(6, (int)(taken / 3.0F)), BLOCK_SCALE, chips);
		if (wooden(guard)) {
			level.playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.7F,
				0.8F + defender.getRandom().nextFloat() * 0.3F);
			ShieldShatter.took(level, defender, guard, source, taken);
		}
	}

	/**
	 * A shield (it blocks and isn't a weapon) made of wood: the {@link #WOODEN_SHIELDS} tag, or nothing in its name that
	 * says metal or stone.
	 */
	public static boolean wooden(ItemStack stack) {
		if (stack.isEmpty() || !stack.has(DataComponents.BLOCKS_ATTACKS) || stack.has(DataComponents.WEAPON)) {
			return false;
		}
		if (stack.is(WOODEN_SHIELDS)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
		for (String word : path.split("[_./]")) {
			if (HARD.contains(word)) {
				return false;
			}
		}
		return !ShieldBang.metal(stack);
	}

	/** A weapon of wood or bare stick (a wooden sword, a club, a staff): no sparks off it. */
	private static boolean woodenWeapon(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
		for (String word : path.split("[_./]")) {
			if (word.equals("wooden") || word.equals("wood") || word.equals("club") || word.equals("staff") || word.equals("stick")
				|| word.equals("bamboo")) {
				return !ShieldBang.metal(stack);
			}
		}
		return false;
	}

	private static Burst burst(@Nullable Vec3 line, int count, float scale, int color, boolean wood) {
		Vec3 l = line == null || line.lengthSqr() < 1.0E-6 ? Vec3.ZERO : line.normalize();
		return new Burst((float)l.x, (float)l.y, (float)l.z, Mth.clamp(count, 1, MAX_BURST), scale, color, wood);
	}

	private static void send(ServerLevel level, Vec3 at, Burst burst) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(at) <= RANGE * RANGE) {
				level.sendParticles(player, burst, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}
}
