package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemLore;

/**
 * (0.17.164) The <b>Dreaded Blade</b>, capellastudio's black sword with its green runes alive (Blockbench Workshop,
 * CC BY 4.0; its two animated textures, the runes drawn at full light). A demon master's own: the first dark lesson
 * that leaves a Z Fighter corrupted ({@link ZFighterCorruption#darkLesson}) ends with the master putting it in his hands,
 * once a lifetime of the player ({@link #TAG}). Never made. A netherite-weight sword that drinks: each blow heals its
 * wielder {@link #DRINK} of its damage and leaves green soul sparks.
 */
public final class DreadedBlade extends Item {
	/** Its heft: netherite's, a little longer-lived. */
	static final ToolMaterial MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2500, 9.0F, 4.0F, 18, ItemTags.NETHERITE_TOOL_MATERIALS);
	/** Damage and speed over the material's (a sword's 3 and -2.4). */
	static final float DAMAGE = 3.0F, SPEED = -2.4F;
	/** The share of each blow's damage that comes back to its wielder as health. */
	static final float DRINK = 0.2F;
	/** On a player who has been given one. */
	static final String TAG = "jaymospowers.dreaded_blade";

	public static Item ITEM;

	DreadedBlade(Item.Properties properties) {
		super(properties);
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("dreaded_blade"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new DreadedBlade(new Item.Properties().setId(key)
			.sword(MATERIAL, DAMAGE, SPEED)
			.rarity(Rarity.EPIC)
			.fireResistant()
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.dreaded_blade.lore").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC))))));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.NETHERITE_SWORD, ITEM));
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (!(attacker.level() instanceof ServerLevel level)) {
			return;
		}
		// (its blow: the wielder's attack damage, this blade's in it)
		attacker.heal((float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * DRINK);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.3, 0.25, 0.02);
	}

	/** After a dark lesson: a newly corrupted student who has never had one is given the master's blade. */
	static void afterLesson(ServerPlayer player, Entity master) {
		if (ITEM == null || !Fate.isCorrupted(player) || player.entityTags().contains(TAG)) {
			return;
		}
		player.addTag(TAG);
		ItemStack blade = new ItemStack(ITEM);
		ServerLevel level = player.level();
		if (!player.getInventory().add(blade)) {
			player.spawnAtLocation(level, blade);
		}
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_demon_master_blade", master.getDisplayName())
			.withStyle(ChatFormatting.DARK_GREEN));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
	}
}
