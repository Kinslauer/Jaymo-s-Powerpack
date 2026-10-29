package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/**
 * (0.17.45, Andy's NOEA picks) The Scouter: a craftable eyepiece worn on the head (glass pane, redstone, iron). Looking
 * at a living thing while wearing it reads out its name and a power level, worked out here on the server: a fighter's
 * from his health, fists and ki, his trained stats, his form, Limit Break and mastery; anyone else's from max health and
 * attack damage. The basic scouter reads up to {@link #CAP}; a number past {@link #OVERLOAD_RATIO} times that overloads it:
 * the readout glitches, the lens cracks and the scouter breaks. Something hiding its ki ({@link ZFighterKiSense#hidesKi})
 * reads {@code ???}.
 */
public final class ZFighterScouter {
	/** What the basic scouter reads up to, and how many times that it takes to burn it out. */
	public static final int CAP = 9000;
	static final float OVERLOAD_RATIO = 2.0F;
	/** How far off it reads (blocks), and the least ticks between one player's reads. */
	static final double READ_RANGE = 48.0;
	static final int READ_GAP = 4;

	// ---- the power level ----
	static final double HEALTH_WEIGHT = 10.0;
	static final double ATTACK_WEIGHT = 50.0;
	/** A fighter adds this per point of max ki and per stat level trained. */
	static final double KI_WEIGHT = 3.0;
	static final double STAT_WEIGHT = 20.0;
	/**
	 * A fighter's multiplier by form (base, Super, Super 2, Super 3, Super Primal Super, False Super, god, Legend 1-3, Primal
	 * Super). (0.17.190) Andy: Super Primal Super's power level is Primal Super's times 4. False Super's entry is unused: it
	 * reads {@link #falseSuper}.
	 */
	static final double[] FORM_MULTIPLIER = {1.0, 2.5, 4.0, 6.0, 7.0 * 4.0, 4.0, 11.0, 5.0, 8.0, 12.0, 7.0};
	/**
	 * (0.17.193) Andy: False Super comes before Super, so it multiplies by this much, rising to this much as he nears the
	 * real breakthrough: half by his hardship toward {@link ZFighterBattle#SUPER_HARDSHIP}, half by the abilities he has
	 * mastered toward ascending ({@link Mastery#ALL}).
	 */
	static final double FALSE_SUPER_MIN = 1.5, FALSE_SUPER_MAX = 3.0;
	/** Plus this much per Limit Break tier, this much at full mastery of the form he's in, and this much times over in Rage. */
	static final double LIMIT_MULTIPLIER = 0.25;
	static final double MASTERY_MULTIPLIER = 0.5;
	static final double RAGE_MULTIPLIER = 1.5;

	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("scouter"));
	public static Item SCOUTER;

	private static final Map<UUID, Long> LAST_READ = new HashMap<>();

	private ZFighterScouter() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("scouter"));
		SCOUTER = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_IRON)
				.setAsset(ASSET)
				.setAllowedEntities(EntityTypes.PLAYER)
				.build())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(SCOUTER));
	}

	/** Whether {@code entity} has a scouter on its head. */
	public static boolean wearing(LivingEntity entity) {
		return SCOUTER != null && entity.getItemBySlot(EquipmentSlot.HEAD).is(SCOUTER);
	}

	/**
	 * A living thing's power level. A Z Fighter's: (health x 10 + fists x 50 + max ki x 3 + stat levels x 20) times his
	 * form, Limit Break tier, mastery of the form he's in and Rage. Anyone else's: health x 10 + attack damage x 50.
	 */
	public static int powerLevel(LivingEntity entity) {
		double attack = entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? entity.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1.0;
		double level = entity.getMaxHealth() * HEALTH_WEIGHT + attack * ATTACK_WEIGHT;
		if (entity instanceof ServerPlayer player && Powers.has(player, ZFighterPower.INSTANCE)) {
			ZFighterPower.Live live = ZFighterPower.live(player);
			level += ZFighterPower.maxKi(player) * KI_WEIGHT + ZFighterTraining.of(player).totalLevels() * STAT_WEIGHT;
			level *= live.form == ZFighterForms.FALSE_SUPER ? falseSuper(player) : FORM_MULTIPLIER[Mth.clamp(live.form, 0, FORM_MULTIPLIER.length - 1)];
			level *= 1.0 + LIMIT_MULTIPLIER * live.limit;
			level *= 1.0 + MASTERY_MULTIPLIER * ZFighterTraining.mastery(player, live.form);
			if (live.raging()) {
				level *= RAGE_MULTIPLIER;
			}
		}
		return (int)Math.min(Integer.MAX_VALUE / 2, Math.round(level));
	}

	/** False Super's multiplier: {@link #FALSE_SUPER_MIN} to {@link #FALSE_SUPER_MAX} by how close he is to his first Super. */
	static double falseSuper(ServerPlayer player) {
		double hardship = Mth.clamp(ZFighterPower.hardship(player) / (double)ZFighterBattle.SUPER_HARDSHIP, 0.0, 1.0);
		double mastered = Mth.clamp(Mastery.unlocked(player) / (double)Mastery.ALL, 0.0, 1.0);
		return Mth.lerp((hardship + mastered) / 2.0, FALSE_SUPER_MIN, FALSE_SUPER_MAX);
	}

	/** From the wearer: he is looking at entity {@code id}. Reads it back to him, and burns the scouter out on too big a number. */
	static void read(ServerPlayer player, int id) {
		if (!wearing(player) || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST_READ.get(player.getUUID());
		if (last != null && now - last < READ_GAP) {
			return;
		}
		LAST_READ.put(player.getUUID(), now);
		Entity found = level.getEntity(id);
		if (!(found instanceof LivingEntity target) || !target.isAlive() || target == player || target.distanceToSqr(player) > READ_RANGE * READ_RANGE
			|| !player.hasLineOfSight(target)) {
			return;
		}
		if (ZFighterKiSense.hidesKi(target)) {
			ZFighterSensesNet.send(player, new ZFighterSensesNet.Reading(id, -1, ZFighterSensesNet.HIDDEN));
			return;
		}
		int number = powerLevel(target);
		int flags = 0;
		if (number > CAP * OVERLOAD_RATIO) {
			flags = ZFighterSensesNet.OVERLOADED;
			overload(player, level);
		} else if (number > CAP) {
			flags = ZFighterSensesNet.PAST_CAP;
		}
		ZFighterSensesNet.send(player, new ZFighterSensesNet.Reading(id, Math.min(number, CAP + 1), flags));
	}

	/** Too much for the lens: it cracks, sparks and breaks (creative players keep theirs). */
	private static void overload(ServerPlayer player, ServerLevel level) {
		double x = player.getX(), y = player.getEyeY(), z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 1.5F);
		level.playSound(null, x, y, z, SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, x, y, z, 24, 0.3, 0.25, 0.3, 0.3);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, SCOUTER), true, false, x, y, z, 12, 0.25, 0.2, 0.25, 0.1);
		Shakes.send(player, 0.5F);
		if (!player.isCreative()) {
			player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_scouter_overload").withStyle(ChatFormatting.RED));
	}

	static void forget(UUID id) {
		LAST_READ.remove(id);
	}
}
