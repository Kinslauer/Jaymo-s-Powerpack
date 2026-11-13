package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * What the thing under the wreck leaves (Kingdoms' {@code beasts/WreckThing} drops both by their ids).
 * <p>
 * The clinging coil is a charm-slot bauble, the dried tip of one of its arms on a cord. It does nothing until the wearer
 * would die under water, of drowning or anything else: then it lets go of them for good (it is used up), and they are
 * left alive with a full breath, {@link #BREATH_TICKS} ticks of water breathing and a shove toward the surface.
 * <p>
 * The deep tooth is one of the hooks from its arms, a plain item for now.
 */
public final class ClingingCoil extends BaubleItem {
	/** What the wearer is left with: health, ticks of water breathing and of regeneration, and the shove upward. */
	static final float SAVED_HEALTH = 8.0F;
	static final int BREATH_TICKS = 20 * 30, REGEN_TICKS = 20 * 6;
	static final double LIFT = 0.9;

	public static Item COIL, DEEP_TOOTH;

	private ClingingCoil(Properties properties) {
		super(properties, Baubles.Kind.CHARM, List.of());
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("clinging_coil"));
		COIL = Baubles.add(Registry.register(BuiltInRegistries.ITEM, key, new ClingingCoil(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE))));
		ResourceKey<Item> tooth = ResourceKey.create(Registries.ITEM, JaymosPowers.id("deep_tooth"));
		DEEP_TOOTH = Registry.register(BuiltInRegistries.ITEM, tooth, new Item(new Item.Properties().setId(tooth).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(DEEP_TOOTH));
		// (default phase: before Downed's, which runs last)
		ServerLivingEntityEvents.ALLOW_DEATH.register(ClingingCoil::letGo);
	}

	/** The wearer would die under water: the coil is used up and they live. */
	private static boolean letGo(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| Downed.isDowned(player) || !(player.isUnderWater() || source.is(DamageTypeTags.IS_DROWNING))) {
			return true;
		}
		List<ItemStack> worn = Baubles.worn(player);
		for (ItemStack stack : worn) {
			if (!stack.is(COIL)) {
				continue;
			}
			// (the worn stack itself, so an open bauble screen can't give it back)
			stack.shrink(1);
			Baubles.setWorn(player, worn);
			player.setHealth(Math.min(SAVED_HEALTH, player.getMaxHealth()));
			player.setAirSupply(player.getMaxAirSupply());
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, BREATH_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 1));
			player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 0.0, 0.2).add(0.0, LIFT, 0.0));
			player.needsSync = true;
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, player.getX(), player.getY() + 0.9, player.getZ(), 60, 0.4, 0.8, 0.4, 0.2);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.2F, 0.7F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.clinging_coil.let_go"));
			return false;
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.clinging_coil").withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
