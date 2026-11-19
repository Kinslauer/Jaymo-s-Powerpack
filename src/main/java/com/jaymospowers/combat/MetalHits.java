package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterAndroid;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (2026-11-19), after Hackers 'n Slashers (all rights reserved, ideas only): "sparks upon clashing blades or
 * otherwise hitting metal". Clashes, parries and blocked blows already spark ({@link ClashFx}); this is the rest, a blow
 * that lands on metal:
 * <ul>
 * <li>a blade (any hard weapon or tool, or a metal fist like a golem's) or an arrow landing on metal armour where it
 * struck (head, chest, legs or feet by the height of the hit; a horse's or wolf's armour anywhere);</li>
 * <li>the same landing on a metal body ({@link #METAL_BODIES}: golems, mechs, drones, and rebuilt androids);</li>
 * <li>a hard weapon or tool struck against a metal block (an anvil, iron bars, a copper door; by its sound, or
 * {@link #METAL_BLOCKS}), and an arrow striking one ({@code ArrowSparkMixin}).</li>
 * </ul>
 * The showers are ClashFx's, smaller than a parry's; a heavy weapon throws more. Leather, cloth and flesh throw none.
 */
public final class MetalHits {
	/** Sparks off a blow on metal armour, on a metal body, a weapon on a metal block, and an arrow on any of them. */
	public static final int ARMOUR_SPARKS = 8, BODY_SPARKS = 10, BLOCK_SPARKS = 6, ARROW_SPARKS = 4;
	/** Extra sparks a heavy weapon (a great weapon or a polearm) throws, and per point of damage the blow did (at most {@link #DAMAGE_SPARKS_MAX}). */
	public static final int HEAVY_SPARKS = 4, DAMAGE_SPARKS_MAX = 6;
	public static final float SPARKS_PER_DAMAGE = 0.5F;
	/** How big the sparks are (1 = a parry's). */
	public static final float SCALE = 0.75F, BLOCK_SCALE = 0.65F;
	/** The ring of it: steel on plate, and on a block, and the pitch (higher than a clash, a clink rather than a ring). */
	public static final float ARMOUR_VOLUME = 0.4F, BLOCK_VOLUME = 0.3F, PITCH = 1.3F;
	/** Ticks between one player's sparks off blocks (each click starts a dig). */
	static final int BLOCK_COOLDOWN = 5;
	/** Share of a body's height above which a blow is on the head, the chest, and the legs (below: the feet), as wounds reads it. */
	static final double HEAD_FROM = 0.75, CHEST_FROM = 0.4, FEET_BELOW = 0.12;

	/** Bodies made of metal: iron and copper golems, clockwork golems, mechs, drones. */
	public static final TagKey<EntityType<?>> METAL_BODIES = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("metal_bodies"));
	/** Blocks that spark whatever they sound like (most metal ones are known by their sound). */
	public static final TagKey<Block> METAL_BLOCKS = TagKey.create(Registries.BLOCK, JaymosPowers.id("metal_blocks"));
	/** Armour that rings when struck: plate and mail (the tags the armour already has). */
	private static final TagKey<Item> PLATE = TagKey.create(Registries.ITEM, JaymosPowers.id("plate_armor"));
	private static final TagKey<Item> MAIL = TagKey.create(Registries.ITEM, JaymosPowers.id("mail_armor"));
	/** Words in an armour's look (its equipment asset) that mean metal, besides the metals ShieldBang knows. */
	private static final Set<String> ARMOUR_WORDS = Set.of("plate", "mail", "harness", "iron", "steel", "chainmail", "netherite",
		"copper", "gold", "bronze");
	private static final Set<SoundType> METAL_SOUNDS = Set.of(SoundType.METAL, SoundType.ANVIL, SoundType.LANTERN, SoundType.NETHERITE_BLOCK,
		SoundType.CHAIN, SoundType.COPPER, SoundType.COPPER_BULB, SoundType.COPPER_GRATE, SoundType.COPPER_GOLEM_STATUE,
		SoundType.TRIAL_SPAWNER, SoundType.VAULT, SoundType.HEAVY_CORE, SoundType.IRON);

	private static final Map<UUID, Long> LAST_BLOCK = new HashMap<>();

	private MetalHits() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			// (a blow on a raised guard is ClashFx's, from BlockFxMixin)
			if (!blocked && base > 0.0F && victim.level() instanceof ServerLevel level) {
				struck(level, victim, source, base);
			}
		});
		AttackBlockCallback.EVENT.register(MetalHits::hitBlock);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_BLOCK.remove(handler.player.getUUID()));
	}

	/** {@code victim} took a blow of {@code amount}: sparks if a hard thing landed on metal. */
	static void struck(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL)) {
			return;
		}
		Entity direct = source.getDirectEntity();
		Vec3 at;
		Vec3 back;
		int extra;
		boolean shot = direct instanceof AbstractArrow;
		if (direct instanceof AbstractArrow arrow) {
			at = arrow.position();
			Vec3 motion = arrow.getDeltaMovement();
			back = motion.lengthSqr() > 1.0E-6 ? motion.scale(-1.0) : victim.position().subtract(at);
			extra = 0;
		} else if (direct instanceof LivingEntity attacker && attacker == source.getEntity() && attacker != victim) {
			ItemStack weapon = attacker.getMainHandItem();
			if (!hard(weapon) && !metalBody(attacker)) {
				return;
			}
			at = landing(victim, attacker);
			back = attacker.getEyePosition().subtract(at);
			extra = BladeSounds.weight(weapon) == BladeSounds.HEAVY ? HEAVY_SPARKS : 0;
		} else {
			return;
		}
		boolean body = metalBody(victim);
		if (!body && !metal(armourAt(victim, at))) {
			return;
		}
		int sparks = (shot ? ARROW_SPARKS : body ? BODY_SPARKS : ARMOUR_SPARKS) + extra
			+ Math.min(DAMAGE_SPARKS_MAX, Mth.floor(amount * SPARKS_PER_DAMAGE));
		ClashFx.sparks(level, at, back, sparks, SCALE);
		BladeSounds.clash(level, at, ARMOUR_VOLUME, PITCH);
	}

	/** A player started striking a block: a hard weapon or tool on metal sparks (server side; the dig goes on). */
	private static InteractionResult hitBlock(Player player, net.minecraft.world.level.Level world, net.minecraft.world.InteractionHand hand,
		BlockPos pos, Direction face) {
		if (!(world instanceof ServerLevel level) || player.isSpectator() || !hard(player.getMainHandItem()) || !metalBlock(level.getBlockState(pos))) {
			return InteractionResult.PASS;
		}
		long now = level.getGameTime();
		Long last = LAST_BLOCK.get(player.getUUID());
		if (last != null && now - last < BLOCK_COOLDOWN && now >= last) {
			return InteractionResult.PASS;
		}
		LAST_BLOCK.put(player.getUUID(), now);
		Vec3 at = null;
		HitResult aim = player.pick(player.blockInteractionRange() + 1.0, 1.0F, false);
		if (aim instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
			at = hit.getLocation();
		}
		if (at == null) {
			at = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
		}
		int sparks = BLOCK_SPARKS + (BladeSounds.weight(player.getMainHandItem()) == BladeSounds.HEAVY ? HEAVY_SPARKS : 0);
		ClashFx.sparks(level, at, player.getEyePosition().subtract(at), sparks, BLOCK_SCALE);
		BladeSounds.clash(level, at, BLOCK_VOLUME, PITCH);
		return InteractionResult.PASS;
	}

	/** An arrow struck {@code hit} (from {@code ArrowSparkMixin}, before it sticks): a few sparks if the block is metal. */
	public static void arrowOnBlock(AbstractArrow arrow, BlockHitResult hit) {
		if (arrow.level() instanceof ServerLevel level && metalBlock(level.getBlockState(hit.getBlockPos()))) {
			Vec3 motion = arrow.getDeltaMovement();
			ClashFx.sparks(level, hit.getLocation(), motion.lengthSqr() > 1.0E-6 ? motion.scale(-1.0) : null, ARROW_SPARKS, BLOCK_SCALE);
		}
	}

	/** A metal block: it sounds like metal, or it's in {@link #METAL_BLOCKS}. */
	public static boolean metalBlock(BlockState state) {
		return !state.isAir() && (state.is(METAL_BLOCKS) || METAL_SOUNDS.contains(state.getSoundType()));
	}

	/** A body of metal ({@link #METAL_BODIES}, or a player rebuilt as an android). */
	public static boolean metalBody(Entity entity) {
		return entity.is(METAL_BODIES) || entity instanceof Player player && ZFighterAndroid.android(player);
	}

	/** Metal armour: plate or mail, or metal by its name or its look. */
	public static boolean metal(ItemStack armour) {
		if (armour.isEmpty()) {
			return false;
		}
		if (armour.is(PLATE) || armour.is(MAIL) || ShieldBang.metal(armour)) {
			return true;
		}
		Equippable worn = armour.get(DataComponents.EQUIPPABLE);
		if (worn != null && worn.assetId().isPresent()) {
			for (String word : worn.assetId().get().identifier().getPath().toLowerCase(Locale.ROOT).split("[_./]")) {
				if (ARMOUR_WORDS.contains(word)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A weapon or tool that isn't wood (a sword, an axe, a pick, a mace): what strikes sparks. */
	static boolean hard(ItemStack stack) {
		return !stack.isEmpty() && (stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL)) && !ClashFx.woodenWeapon(stack);
	}

	/** Where {@code attacker}'s blow landed on {@code victim}: where a player's aim crosses it, else on the attacker's side at its middle. */
	private static Vec3 landing(LivingEntity victim, LivingEntity attacker) {
		AABB box = victim.getBoundingBox();
		if (attacker instanceof Player) {
			Vec3 eye = attacker.getEyePosition();
			Vec3 point = box.inflate(0.1).clip(eye, eye.add(attacker.getLookAngle().scale(16.0))).orElse(null);
			if (point != null) {
				return point;
			}
		}
		Vec3 toward = attacker.position().subtract(victim.position()).multiply(1.0, 0.0, 1.0);
		toward = toward.lengthSqr() > 1.0E-6 ? toward.normalize() : Vec3.ZERO;
		double y = Mth.clamp(attacker.getY() + attacker.getBbHeight() * 0.6, box.minY + 0.1, box.maxY - 0.1);
		return new Vec3(victim.getX() + toward.x * box.getXsize() * 0.5, y, victim.getZ() + toward.z * box.getZsize() * 0.5);
	}

	/** The armour worn where {@code at} is on {@code victim}: a beast's body armour, else the piece at that height. */
	private static ItemStack armourAt(LivingEntity victim, Vec3 at) {
		ItemStack body = victim.getItemBySlot(EquipmentSlot.BODY);
		if (!body.isEmpty()) {
			return body;
		}
		AABB box = victim.getBoundingBox();
		double share = (at.y - box.minY) / Math.max(0.01, box.getYsize());
		EquipmentSlot slot = share >= HEAD_FROM ? EquipmentSlot.HEAD : share >= CHEST_FROM ? EquipmentSlot.CHEST
			: share >= FEET_BELOW ? EquipmentSlot.LEGS : EquipmentSlot.FEET;
		return victim.getItemBySlot(slot);
	}
}
