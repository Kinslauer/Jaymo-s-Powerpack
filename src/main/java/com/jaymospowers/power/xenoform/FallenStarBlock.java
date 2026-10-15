package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Callings;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.web.WebPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A fallen star: the glowing core left in the crater where a {@link FallingStars falling star} came down. It lies there
 * until someone mines it (a pickaxe, slowly). Broken, it usually holds raw iron and amethyst, but once in a while a
 * {@link XenoBandItem Xeno-Band}, or black goo with a symbiote in it (the goo waits for a web slinger; a powerless
 * finder is marked for the Web).
 */
public final class FallenStarBlock extends Block {
	public static Block FALLEN_STAR;
	public static Item ITEM;

	// ---- tuning ----
	/** Chance a broken star holds the band, else (checked next) the symbiote's goo. */
	public static final float BAND_CHANCE = 0.03F;
	public static final float SYMBIOTE_CHANCE = 0.07F;
	/** Otherwise: this much raw iron and amethyst, and this much experience. */
	public static final int IRON_MIN = 2, IRON_MAX = 5, AMETHYST_MIN = 1, AMETHYST_MAX = 2, XP = 12;
	public static final int LIGHT = 9;

	private FallenStarBlock(Properties properties) {
		super(properties);
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("fallen_star"));
		FALLEN_STAR = Registry.register(BuiltInRegistries.BLOCK, key, new FallenStarBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_GREEN)
			.strength(5.0F, 1200.0F)
			.requiresCorrectToolForDrops()
			.lightLevel(state -> LIGHT)
			.sound(SoundType.ANCIENT_DEBRIS)
			.pushReaction(PushReaction.IMMOVEABLE)
			.noLootTable()
			.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("fallen_star"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(FALLEN_STAR, new Item.Properties().setId(itemKey)
			.useBlockDescriptionPrefix().rarity(Rarity.RARE).fireResistant()));
	}

	/** Mined (not in creative): what was inside. */
	@Override
	public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
		super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
		RandomSource random = level.getRandom();
		Vec3 centre = Vec3.atCenterOf(pos);
		level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 30, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFF5CF07A), centre.x, centre.y, centre.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
		if (random.nextFloat() < BAND_CHANCE) {
			popResource(level, pos, new ItemStack(XenoBandItem.BAND));
			player.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.star_band").withStyle(ChatFormatting.LIGHT_PURPLE));
			level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.5F);
			return;
		}
		if (random.nextFloat() < SYMBIOTE_CHANCE && WebPower.GOO != null) {
			level.setBlock(pos.below(), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(pos, WebPower.GOO.defaultBlockState(), Block.UPDATE_ALL);
			level.sendParticles(ParticleTypes.SQUID_INK, centre.x, centre.y, centre.z, 25, 0.3, 0.2, 0.3, 0.06);
			level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.5F);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.star_symbiote").withStyle(ChatFormatting.DARK_PURPLE));
			if (Powers.of(player) == null) {
				// it has your scent now: the Web is calling, and the goo waits here for you
				Callings.call(player, "web", Callings.DAY);
			}
			return;
		}
		popResource(level, pos, new ItemStack(Items.RAW_IRON, IRON_MIN + random.nextInt(IRON_MAX - IRON_MIN + 1)));
		popResource(level, pos, new ItemStack(Items.AMETHYST_SHARD, AMETHYST_MIN + random.nextInt(AMETHYST_MAX - AMETHYST_MIN + 1)));
		ExperienceOrb.award(level, centre, XP);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.0 + random.nextDouble() * 0.3,
				pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(), 0.0, 0.03, 0.0);
		}
	}
}
