package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A settler's kit: what a Farmer and a Smith ({@link Careers.Job#FARMER}, {@link Careers.Job#SMITH}) can do for the
 * folk and beasts about them, and what a Master's name is worth to the town's traders. Tuning is in the constants.
 * <ul>
 * <li><b>Replanting</b> (any Farmer): a grown crop you break is sown again from a seed in its drops, unless it is
 * winter where there are seasons.</li>
 * <li><b>Tending</b> (Journeyman Farmer): an empty hand on a growing crop gives it a few of the nudges a random tick
 * gives (so out-of-season crops still wait), costs a little food, and each crop rests a while after.</li>
 * <li><b>Hearth and forge hands</b> (any Farmer, any Smith): tamed animals hit harder near their farmer, and golems
 * their smith built or keeps hit harder near a smith, more with the rank.</li>
 * <li><b>Hero's prices</b> (Master of any job): a trader of the town they work in sells to them as it would to the
 * town's hero, for as long as the talk lasts.</li>
 * </ul>
 * The field forge is {@link FieldForge}; the lord's call is {@link Rally}.
 */
public final class SettlerKit {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** Replant: the crop is not sown again where it is this season (Kitchen's seasons). */
	static final String NO_SOWING = "winter";
	/** Tend: random ticks tried at most, the share of one that ends in a taller crop is the block's own; the food level it needs; the exhaustion it costs; each crop's rest (ticks). */
	static final int TEND_TICKS = 4, TEND_FOOD_MIN = 7;
	static final float TEND_EXHAUSTION = 1.5F;
	static final long TEND_REST = 1200L;
	/** The most crops remembered resting before the list is swept. */
	static final int TEND_MEMORY = 512;
	/** Tamed beasts near their farmer: how near (blocks), and the blow's share by farmer rank (Apprentice, Journeyman, Master). */
	static final double FARMER_NEAR = 16.0;
	static final float[] BEAST_BLOW = {1.15F, 1.25F, 1.4F};
	/** Golems near a smith: how near (blocks), and the blow's share by smith rank. */
	static final double SMITH_NEAR = 16.0;
	static final float[] GOLEM_BLOW = {1.15F, 1.3F, 1.5F};
	/** Hero's prices: how long the trader's talk is covered (ticks) once a Master speaks to them. */
	static final int HERO_TICKS = 600;

	/** Crop (packed position, with the dimension's name) → the tick it may be tended again. */
	private static final Map<String, Long> RESTING = new HashMap<>();

	private SettlerKit() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RESTING.clear());
		PlayerBlockBreakEvents.AFTER.register(SettlerKit::replant);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)
				|| !sp.getMainHandItem().isEmpty() || sp.isSpectator()) {
				return InteractionResult.PASS;
			}
			return tend(level, sp, hit.getBlockPos()) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && entity instanceof Villager v) {
				heroPrices(sp, v);
			}
			return InteractionResult.PASS;
		});
	}

	// ---- the farmer ----------------------------------------------------------------------------------------------------

	/** A grown crop broken: sown again from a seed in its drops. */
	private static void replant(net.minecraft.world.level.Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level) || sp.isCreative() || !(state.getBlock() instanceof CropBlock crop)
			|| !crop.isMaxAge(state) || Careers.heldRank(sp, Careers.Job.FARMER) < 0 || NO_SOWING.equals(KitchenCompat.season(level))) {
			return;
		}
		BlockState sown = crop.getStateForAge(0);
		if (!level.getBlockState(pos).isAir() || !sown.canSurvive(level, pos)) {
			return;
		}
		for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.5), i -> i.isAlive()
			&& i.getItem().getItem() instanceof BlockItem bi && bi.getBlock() == crop)) {
			drop.getItem().shrink(1);
			if (drop.getItem().isEmpty()) {
				drop.discard();
			}
			level.setBlock(pos, sown, 3);
			level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.7F, 1.0F);
			level.sendParticles(Fx.mote(0x8FD14F, 0.8F), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 4, 0.25, 0.1, 0.25, 0.01);
			return;
		}
	}

	/** An empty hand on a growing crop: a few random ticks, a little hunger, and the crop rests. True if it was handled. */
	private static boolean tend(ServerLevel level, ServerPlayer player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state) || Careers.heldRank(player, Careers.Job.FARMER) < Careers.JOURNEYMAN) {
			return false;
		}
		long now = level.getGameTime();
		String key = level.dimension().identifier() + "/" + pos.asLong();
		Long until = RESTING.get(key);
		if (until != null && until > now) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tend.rest"));
			return true;
		}
		if (player.getFoodData().getFoodLevel() < TEND_FOOD_MIN) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tend.hungry"));
			return true;
		}
		if (RESTING.size() > TEND_MEMORY) {
			RESTING.values().removeIf(t -> t <= now);
		}
		RESTING.put(key, now + TEND_REST);
		player.causeFoodExhaustion(TEND_EXHAUSTION);
		int age = crop.getAge(state);
		for (int i = 0; i < TEND_TICKS; i++) {
			BlockState now_ = level.getBlockState(pos);
			if (!(now_.getBlock() instanceof CropBlock) || ((CropBlock)now_.getBlock()).getAge(now_) > age) {
				break;
			}
			now_.randomTick(level, pos, level.getRandom());
		}
		BlockState after = level.getBlockState(pos);
		boolean grew = after.getBlock() instanceof CropBlock c && c.getAge(after) > age;
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.5F, 1.3F);
		level.sendParticles(Fx.mote(grew ? 0x8FD14F : 0xB9A66B, 0.8F), pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, grew ? 6 : 2, 0.25, 0.2, 0.25, 0.01);
		return true;
	}

	// ---- blows ---------------------------------------------------------------------------------------------------------

	/**
	 * (from {@code LivingEntityHurtMixin}) A tamed animal's blow lands harder near its owner when the owner is a Farmer, and a
	 * built golem's near a Smith.
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		Entity by = source.getEntity();
		if (by instanceof TamableAnimal pet && pet.isTame() && pet.getOwner() instanceof ServerPlayer owner && owner.level() == pet.level()
			&& owner.distanceToSqr(pet) <= FARMER_NEAR * FARMER_NEAR) {
			int rank = Careers.heldRank(owner, Careers.Job.FARMER);
			return rank < 0 ? amount : amount * BEAST_BLOW[Mth.clamp(rank, 0, 2)];
		}
		if (by instanceof IronGolem golem && golem.isPlayerCreated() && golem.level() instanceof ServerLevel level) {
			int best = -1;
			for (ServerPlayer p : level.players()) {
				if (p.isAlive() && p.distanceToSqr(golem) <= SMITH_NEAR * SMITH_NEAR) {
					best = Math.max(best, Careers.heldRank(p, Careers.Job.SMITH));
				}
			}
			return best < 0 ? amount : amount * GOLEM_BLOW[Mth.clamp(best, 0, 2)];
		}
		return amount;
	}

	// ---- trade ---------------------------------------------------------------------------------------------------------

	/**
	 * {@code trader} is spoken to by a Master of some job who works in its town: the town's hero price (the villagers'
	 * own Hero of the Village discount) covers the talk. Quiet and brief, and not given to a Master who is a stranger there.
	 */
	private static void heroPrices(ServerPlayer player, Villager trader) {
		if (!(trader.level() instanceof ServerLevel level) || player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
			return;
		}
		Careers.Career career = Careers.of(player);
		for (Careers.Post post : career.posts()) {
			if (career.rank(post.job()) != Careers.MASTER) {
				continue;
			}
			Faction town = Factions.get(level.getServer(), post.faction());
			if (town != null && !town.fallen() && Careers.inTown(level, town, trader.blockPosition())) {
				player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, HERO_TICKS, 0, false, false, false));
				return;
			}
		}
	}
}
