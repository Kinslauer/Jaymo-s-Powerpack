package com.jaymospowers.boxing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.Armory;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;

/**
 * Gauntlets in the off hand, from Gallant Gauntlets' play (by Joshieman; ideas, and its art as the base of every
 * gauntlet: {@code client/armory/FangGauntletsClient}): a gauntlet (the Fang Gauntlets or boxing's fist gear) in the
 * off hand punches with a right click at someone you're fighting, on its own cooldown beside the main hand's, with its
 * own damage and enchantments. Only while the main hand has no right click of its own (an empty hand, a tool; not a
 * weapon raised in its guard, food, a bow), and only at a monster, a player, or a creature that's after you: a
 * villager or a horse still gets the right click they always did.
 * <p>
 * Also here, the rest of Gallant's ideas: each gauntlet gives the armour of a pair of boots of its metal in either hand
 * ({@link BoxingGear#gauntlet}), and the <b>Crushing</b> enchantment ({@code data/jaymospowers/enchantment/crushing.json},
 * {@link CrushArmour}): a blow wears every piece of armour the one hit has on.
 */
public final class OffhandPunch {
	// ---- tuning ----
	/** A punch reaches no farther than this past the player's own reach (blocks; the client's aim is trusted this far). */
	static final double REACH_SLACK = 1.0;
	/** Hunger a punch costs, as a main-hand blow does. */
	static final float EXHAUSTION = 0.1F;
	/** How hard a full-strength punch knocks back (vanilla's sprinting blow is 0.5). */
	static final float KNOCKBACK = 0.3F;

	/** When each player's off hand last punched (their tick count), for its own cooldown. */
	private static final Map<UUID, Integer> LAST = new WeakHashMap<>();

	private OffhandPunch() {
	}

	/** Client → server: the off hand punches the entity with this id. */
	public record Punch(int target) implements CustomPacketPayload {
		public static final Type<Punch> TYPE = new Type<>(JaymosPowers.id("offhand_punch"));
		public static final StreamCodec<ByteBuf, Punch> CODEC = ByteBufCodecs.VAR_INT.map(Punch::new, Punch::target);

		@Override
		public Type<Punch> type() {
			return TYPE;
		}
	}

	static void init() {
		Registry.register(BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE, JaymosPowers.id("crush_armour"), CrushArmour.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Punch.TYPE, Punch.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Punch.TYPE, (payload, ctx) -> punch(ctx.player(), ctx.player().level().getEntity(payload.target())));
	}

	/** Whether {@code stack} is a gauntlet: worn on the hand, and punches from the off hand. */
	public static boolean isGauntlet(ItemStack stack) {
		return BoxingGear.isGear(stack) || Armory.FANG_GAUNTLETS != null && stack.is(Armory.FANG_GAUNTLETS);
	}

	/** Whether {@code player}'s off hand would punch {@code target} on a right click (both sides ask). */
	public static boolean canPunch(Player player, Entity target) {
		ItemStack main = player.getMainHandItem();
		if (player.isSpectator() || !isGauntlet(player.getOffhandItem()) || main.has(DataComponents.BLOCKS_ATTACKS)
			|| main.getUseAnimation() != ItemUseAnimation.NONE || !(target instanceof LivingEntity) || !target.isAttackable()) {
			return false;
		}
		return target instanceof Enemy || target instanceof Player || target instanceof Mob mob && mob.getTarget() == player;
	}

	private static void punch(ServerPlayer player, Entity target) {
		if (target == null || !canPunch(player, target) || !player.isWithinEntityInteractionRange(target, REACH_SLACK)) {
			return;
		}
		ItemStack gauntlet = player.getOffhandItem();
		// the gauntlet's own numbers, as if it were in the main hand: a bare hand's, plus its damage and speed
		double[] numbers = {player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), player.getAttributeBaseValue(Attributes.ATTACK_SPEED)};
		gauntlet.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
				numbers[0] += modifier.amount();
			} else if (attribute.equals(Attributes.ATTACK_SPEED)) {
				numbers[1] += modifier.amount();
			}
		});
		// its own cooldown, vanilla's way: a blow before it's ready lands weaker
		float cooldown = (float) (20.0 / Math.max(0.1, numbers[1]));
		Integer last = LAST.get(player.getUUID());
		float charge = last == null ? 1.0F : Mth.clamp((player.tickCount - last + 0.5F) / cooldown, 0.0F, 1.0F);
		LAST.put(player.getUUID(), player.tickCount);
		ServerLevel level = player.level();
		// the others see the off arm swing (yours swung already)
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, false);
		DamageSource source = player.damageSources().playerAttack(player);
		float damage = EnchantmentHelper.modifyDamage(level, gauntlet, target, source, (float) numbers[0]) * (0.2F + charge * charge * 0.8F);
		player.causeFoodExhaustion(EXHAUSTION);
		if (!target.hurtServer(level, source, damage)) {
			return;
		}
		if (target instanceof LivingEntity living) {
			living.knockback(KNOCKBACK * charge, Mth.sin(player.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(player.getYRot() * Mth.DEG_TO_RAD), source, damage);
		}
		EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, gauntlet);
		gauntlet.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
	}

	/**
	 * Crushing's effect (Gallant Gauntlets' idea, our code): every piece of armour the one hit wears takes {@code amount}
	 * damage.
	 */
	public record CrushArmour(LevelBasedValue amount) implements EnchantmentEntityEffect {
		public static final MapCodec<CrushArmour> CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(LevelBasedValue.CODEC.fieldOf("amount").forGetter(CrushArmour::amount)).apply(i, CrushArmour::new));

		@Override
		public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
			if (!(entity instanceof LivingEntity victim)) {
				return;
			}
			int damage = Math.round(this.amount.calculate(enchantmentLevel));
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack worn = victim.getItemBySlot(slot);
				if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && worn.isDamageableItem()) {
					worn.hurtAndBreak(damage, victim, slot);
				}
			}
		}

		@Override
		public MapCodec<CrushArmour> codec() {
			return CODEC;
		}
	}
}
