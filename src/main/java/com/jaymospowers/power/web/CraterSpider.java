package com.jaymospowers.power.web;

import com.jaymospowers.core.AwakeningDeeds;
import com.jaymospowers.core.Powers;
import com.jaymospowers.tinkering.Tinkering;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A crater spider: a cave spider shut in a glass jar (glass or glass panes on its four sides and below, open at the lid)
 * that is fed a lump of meteoric iron through the lid (the lump used on it). It stays a cave spider underneath, marked with
 * {@link #TAG} and a name. Its bite wakes a Web Slinger in the making ({@link AwakeningDeeds#wake}); anyone else is only
 * poisoned and sickened ({@link #POISON_TICKS}, {@link #NAUSEA_TICKS}).
 */
public final class CraterSpider {
	public static final String TAG = "jaymospowers_crater_spider";
	public static final int POISON_TICKS = 200;
	public static final int NAUSEA_TICKS = 160;

	private CraterSpider() {
	}

	public static boolean is(Entity entity) {
		return entity instanceof CaveSpider && entity.entityTags().contains(TAG);
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(world instanceof ServerLevel level) || !(entity instanceof CaveSpider spider) || is(spider) || !spider.isAlive()
				|| !player.getItemInHand(hand).is(Tinkering.METEORIC_IRON) || !jarred(level, spider.blockPosition())) {
				return InteractionResult.PASS;
			}
			if (!player.isCreative()) {
				player.getItemInHand(hand).shrink(1);
			}
			spider.addTag(TAG);
			spider.setCustomName(Component.translatable("entity.jaymospowers.crater_spider"));
			spider.setPersistenceRequired();
			level.playSound(null, spider.getX(), spider.getY(), spider.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.0F, 0.6F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spider.getX(), spider.getY() + 0.3, spider.getZ(), 20, 0.2, 0.2, 0.2, 0.1);
			return InteractionResult.SUCCESS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer player) || !player.isAlive() || damageTaken <= 0.0F || !is(source.getEntity())
				|| source.is(DamageTypeTags.IS_PROJECTILE)) {
				return;
			}
			if (Powers.of(player) == null && AwakeningDeeds.wake(player, "web")) {
				return;
			}
			player.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0));
		});
	}

	/** Glass (or a glass pane) on the four sides and below {@code pos}: a jar, open at the lid. */
	private static boolean jarred(ServerLevel level, BlockPos pos) {
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
			if (!glass(level.getBlockState(pos.relative(direction)))) {
				return false;
			}
		}
		return true;
	}

	private static boolean glass(BlockState state) {
		return state.is(BlockTags.IMPERMEABLE) || state.getBlock() instanceof IronBarsBlock && !state.is(Blocks.IRON_BARS);
	}
}
