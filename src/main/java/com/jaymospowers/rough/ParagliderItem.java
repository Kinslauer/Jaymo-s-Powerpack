package com.jaymospowers.rough;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.stamina.Feathers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A paraglider (Andy's TFC Hardrock picks, our own item): a cloth wing on two sticks. Hold right-click in mid-air to
 * glide, the way the aercloud gliders do: a slow sink ({@link #SINK}, sneak to dive), steered by walking. It has no glide
 * time; instead it tires your arms, one half-feather of stamina every {@link #STAMINA_TICKS}, and it folds when you
 * have none left. Over heat it rises: a lit campfire, a fire, lava, magma, a lit furnace or an Advanced Chimneys
 * chimney anywhere in the open {@link #UPDRAFT_HEIGHT} blocks below lifts you ({@link #LIFT}, weaker the higher you are),
 * and riding an updraft costs no stamina. No fall damage while it is open. One durability each time you open it.
 * <p>
 * Players move on their own client, so the steering and the lift happen there ({@code onUseTick}); the server spends
 * the stamina, wears the item and forgets the fall.
 */
public class ParagliderItem extends Item {
	/** Vertical speed gliding and diving (blocks a tick, negative = down), and the push forward a tick. */
	static final double SINK = -0.04, DIVE = -0.18, PUSH = 0.028;
	/** Share of the push sideways and backwards. */
	static final double SIDE = 0.5, BACK = 0.25;
	/** Rise at the heat and at the top of the updraft (blocks a tick), and how high above the heat it reaches. */
	static final double LIFT = 0.2, LIFT_TOP = 0.05;
	static final int UPDRAFT_HEIGHT = 24;
	/** Ticks of gliding a half-feather of stamina pays for. */
	static final int STAMINA_TICKS = 20;
	/** Waits after folding for want of stamina and after letting go (ticks); uses before it wears out. */
	static final int COOLDOWN_TIRED = 60, COOLDOWN_RELEASE = 2, DURABILITY = 160;
	private static final double DRAG = 0.98;
	private static final int OPEN_GRACE = 2;

	public static Item PARAGLIDER;

	public ParagliderItem(Item.Properties properties) {
		super(properties.component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0F)));
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("paraglider"));
		PARAGLIDER = Registry.register(BuiltInRegistries.ITEM, key, new ParagliderItem(new Item.Properties().setId(key).durability(DURABILITY)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(PARAGLIDER));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.onGround() || player.isInWater() || player.isInLava() || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying
			|| player.getCooldowns().isOnCooldown(stack) || !Feathers.canSpend(player, 1)) {
			return InteractionResult.PASS;
		}
		if (!com.jaymospowers.core.wounds.LimbUse.usable(player, com.jaymospowers.core.wounds.LimbUse.BOTH_ARMS)) {
			if (level.isClientSide()) {
				com.jaymospowers.core.wounds.LimbUse.refuse(player, com.jaymospowers.core.wounds.LimbUse.BOTH_ARMS);
			}
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			stack.hurtAndBreak(1, sp, hand);
			if (stack.isEmpty()) {
				return InteractionResult.CONSUME;
			}
		}
		player.startUsingItem(hand);
		level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		if (!(user instanceof Player player)) {
			return;
		}
		int used = this.getUseDuration(stack, user) - remaining;
		user.resetFallDistance();
		boolean down = used > OPEN_GRACE && (player.onGround() || player.isInWater() || player.isInLava());
		if (down || player.isPassenger()) {
			fold(player, stack, COOLDOWN_RELEASE);
			return;
		}
		if (used > 0 && used % STAMINA_TICKS == 0 && player instanceof ServerPlayer sp && lift(level, player.blockPosition()) <= 0.0
			&& !Feathers.spend(sp, 1)) {
			fold(player, stack, COOLDOWN_TIRED);
			return;
		}
		if (level.isClientSide() && player.isLocalPlayer()) {
			if (!Feathers.canSpend(player, 1)) {
				fold(player, stack, COOLDOWN_TIRED);
				return;
			}
			glide(player, used);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
		if (user instanceof Player player) {
			player.getCooldowns().addCooldown(stack, COOLDOWN_RELEASE);
		}
		return true;
	}

	private static void fold(Player player, ItemStack stack, int cooldown) {
		player.stopUsingItem();
		player.getCooldowns().addCooldown(stack, cooldown);
	}

	private static double cachedLift;
	private static int cachedAt = -1;

	/** The steering, the slow fall and the updraft, once a tick, on the gliding player's own client. */
	private static void glide(Player player, int used) {
		if (cachedAt < 0 || used < cachedAt || used - cachedAt >= 4) {
			cachedLift = lift(player.level(), player.blockPosition());
			cachedAt = used;
		}
		Vec3 v = player.getDeltaMovement();
		double gravity = player.getGravity();
		double y;
		if (player.isShiftKeyDown()) {
			y = DIVE / DRAG + gravity;
		} else {
			double floor = SINK / DRAG + gravity;
			y = cachedLift > 0.0 ? Math.max(v.y, cachedLift / DRAG + gravity) : Math.max(v.y, floor);
		}
		Vec3 look = player.getLookAngle();
		double flat = Math.sqrt(look.x * look.x + look.z * look.z);
		double fx = flat < 1.0E-4 ? 0.0 : look.x / flat, fz = flat < 1.0E-4 ? 0.0 : look.z / flat;
		double forward = player.zza > 0.0F ? player.zza : player.zza * BACK;
		double side = player.xxa * SIDE;
		player.setDeltaMovement(v.x + (fx * forward + fz * side) * PUSH, y, v.z + (fz * forward - fx * side) * PUSH);
		if (cachedLift > 0.0 && used % 3 == 0) {
			player.level().addParticle(ParticleTypes.SMOKE, player.getX(), player.getY() - 0.5, player.getZ(), 0.0, 0.05, 0.0);
		}
	}

	/** The updraft at {@code pos}: how fast heat below lifts you (0: none). Looks down the 3x3 columns to the first block. */
	static double lift(Level level, BlockPos pos) {
		int best = Integer.MAX_VALUE;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= UPDRAFT_HEIGHT && dy < best; dy++) {
					at.set(pos.getX() + dx, pos.getY() - dy, pos.getZ() + dz);
					BlockState state = level.getBlockState(at);
					if (state.isAir()) {
						continue;
					}
					if (hot(state)) {
						best = dy;
					}
					break;
				}
			}
		}
		if (best == Integer.MAX_VALUE) {
			return 0.0;
		}
		return LIFT + (LIFT_TOP - LIFT) * best / UPDRAFT_HEIGHT;
	}

	private static final boolean CHIMNEYS = FabricLoader.getInstance().isModLoaded("adchimneys");

	/** Heat that makes an updraft: fire, lava, magma, a lit campfire or furnace, an Advanced Chimneys chimney or vent. */
	static boolean hot(BlockState state) {
		if (state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.getFluidState().is(FluidTags.LAVA)) {
			return true;
		}
		if (state.is(BlockTags.CAMPFIRES)) {
			return state.getValueOrElse(CampfireBlock.LIT, false);
		}
		if (state.getBlock() instanceof AbstractFurnaceBlock) {
			return state.getValueOrElse(AbstractFurnaceBlock.LIT, false);
		}
		return CHIMNEYS && "adchimneys".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
	}
}
