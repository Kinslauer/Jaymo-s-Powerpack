package com.jaymospowers.umbrella;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/**
 * Umbrellas and parasols (Reference Mods patch 4, after the Umbrellas mod's idea, our own code and art).
 * <ul>
 * <li>Three things to carry: the sturdy <b>umbrella</b> (oiled canvas), the <b>lace parasol</b> and the Mohknee <b>paper
 * parasol</b> (oiled paper, blossom painting, bamboo ribs). Use one to open or furl it.</li>
 * <li><b>Rain</b>: held open, rain doesn't wet you (so no Soaked, and nothing rusts on you: {@code Wet} asks
 * {@link #sheltered}); an open umbrella in a stand ({@link UmbrellaStandBlock}) keeps the rain off the ground round it.</li>
 * <li><b>Float down</b>: open and sturdy, falling, you drift down; a lace or paper parasol turns inside out and tears.</li>
 * <li><b>Campfire smoke</b>: open and sturdy, over a lit campfire's smoke it lifts you, as high as the smoke goes. Each
 * second of drifting or lifting wears the umbrella; a parasol over the fire scorches.</li>
 * </ul>
 * Jaymo's Kingdoms (which never compiles against this mod) hands the same items to the women of wealthy realms and of
 * Mohknee, and reads them by the item tag {@link #TAG} and the custom model data contract: flag 0 = open, colour 0 = the
 * canopy's, colour 1 = the panel's, string 0 = the parasol's panel style {@code "0".."3"}.
 */
public final class Umbrellas {
	// ---- tuning ----
	/** Durability of each kind. */
	static final int UMBRELLA_USES = 600, PARASOL_USES = 240, PAPER_USES = 160;
	/** Drift: you start drifting once falling faster than this (blocks a tick), and fall no faster than {@link #DRIFT}. */
	static final double FALL_FROM = 0.2, DRIFT = 0.1;
	/** A parasol tears once you have fallen this far with it open (blocks). */
	static final float TEAR_FALL = 2.5F;
	/** Smoke: how high above a campfire (and a signal fire, with hay) it lifts you, the push a tick, and the most rise (blocks a tick). */
	static final int SMOKE_REACH = 8, SIGNAL_REACH = 16;
	static final double LIFT_PUSH = 0.07, LIFT_MAX = 0.3;
	/** Wear: a point of durability this often (ticks) while drifting or lifting; a parasol over a fire loses this many. */
	static final int WEAR_EVERY = 20, SCORCH = 6;
	/** An open stand keeps the rain off this far round it (blocks across, up and down). */
	static final int STAND_REACH = 2, STAND_HEIGHT = 3;

	public enum Kind {
		UMBRELLA("umbrella"), PARASOL("parasol"), PAPER("paper_parasol");

		public final String key;

		Kind(String key) {
			this.key = key;
		}
	}

	/** Every umbrella, parasol and paper parasol (Kingdoms reads this tag). */
	public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, JaymosPowers.id("umbrellas"));

	public static Item UMBRELLA, PARASOL, PAPER_PARASOL;
	public static Block UMBRELLA_STAND;
	public static BlockEntityType<UmbrellaStandEntity> STAND_ENTITY;

	private Umbrellas() {
	}

	public static void init() {
		UMBRELLA = item(Kind.UMBRELLA, UMBRELLA_USES);
		PARASOL = item(Kind.PARASOL, PARASOL_USES);
		PAPER_PARASOL = item(Kind.PAPER, PAPER_USES);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("umbrella_stand"));
		UMBRELLA_STAND = Registry.register(BuiltInRegistries.BLOCK, key, new UmbrellaStandBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.METAL).noOcclusion().setId(key)));
		ResourceKey<Item> standItem = ResourceKey.create(Registries.ITEM, JaymosPowers.id("umbrella_stand"));
		Registry.register(BuiltInRegistries.ITEM, standItem, new BlockItem(UMBRELLA_STAND, new Item.Properties().setId(standItem).useBlockDescriptionPrefix()));
		STAND_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("umbrella_stand"),
			new BlockEntityType<>(UmbrellaStandEntity::new, Set.of(UMBRELLA_STAND)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(UMBRELLA);
			output.accept(PARASOL);
			output.accept(PAPER_PARASOL);
			output.accept(UMBRELLA_STAND);
		});
		ServerTickEvents.END_SERVER_TICK.register(Umbrellas::tick);
	}

	private static Item item(Kind kind, int uses) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(kind.key));
		return Registry.register(BuiltInRegistries.ITEM, key, new UmbrellaItem(new Item.Properties().setId(key).stacksTo(1).durability(uses), kind));
	}

	// ---- the stack's look ----

	public static boolean isOpen(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return data != null && Boolean.TRUE.equals(data.getBoolean(0));
	}

	/** Opens or furls {@code stack}, keeping the colours and style in its model data. */
	public static void setOpen(ItemStack stack, boolean open) {
		CustomModelData data = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.floats(), List.of(open), data.strings(), data.colors()));
	}

	/** The open umbrella {@code who} holds (either hand), or empty. */
	public static ItemStack openIn(LivingEntity who) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			ItemStack stack = who.getItemBySlot(slot);
			if (stack.getItem() instanceof UmbrellaItem && isOpen(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static EquipmentSlot slotOf(LivingEntity who, ItemStack stack) {
		return who.getItemBySlot(EquipmentSlot.MAINHAND) == stack ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
	}

	// ---- rain ----

	/** Rain does not reach {@code body}: it holds an open umbrella, or stands by an open one in a stand. */
	public static boolean sheltered(LivingEntity body) {
		return !openIn(body).isEmpty() || keepsRain(body.level(), body.blockPosition());
	}

	/** An open umbrella in a stand close enough to keep the rain off {@code at}. */
	public static boolean keepsRain(Level level, BlockPos at) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-STAND_REACH, -STAND_HEIGHT, -STAND_REACH), at.offset(STAND_REACH, 1, STAND_REACH))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(UMBRELLA_STAND) && state.getValue(UmbrellaStandBlock.CONTENTS) == UmbrellaStandBlock.Contents.OPEN) {
				return true;
			}
		}
		return false;
	}

	// ---- falling and smoke ----

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack umbrella = openIn(player);
			if (umbrella.isEmpty() || player.isSpectator() || player.isInWater() || player.getAbilities().flying || player.isFallFlying()
				|| player.isPassenger()) {
				continue;
			}
			UmbrellaItem item = (UmbrellaItem)umbrella.getItem();
			ServerLevel level = player.level();
			boolean wear = player.tickCount % WEAR_EVERY == 0;
			if (!player.onGround()) {
				Vec3 v = player.getDeltaMovement();
				if (item.kind != Kind.UMBRELLA) {
					if (player.fallDistance > TEAR_FALL && v.y < -FALL_FROM) {
						tear(level, player, umbrella);
						continue;
					}
				} else if (v.y < -FALL_FROM) {
					// float down: slowed to a drift, so there is no fall to pay for
					player.setDeltaMovement(v.x, -DRIFT, v.z);
					player.fallDistance = 0.0F;
					player.syncVelocity = true;
					if (wear) {
						umbrella.hurtAndBreak(1, player, slotOf(player, umbrella));
					}
					continue;
				}
			}
			int fire = smokeBelow(level, player.blockPosition());
			if (fire >= 0) {
				if (item.kind == Kind.UMBRELLA) {
					Vec3 v = player.getDeltaMovement();
					player.setDeltaMovement(v.x, Math.min(LIFT_MAX, v.y + LIFT_PUSH), v.z);
					player.fallDistance = 0.0F;
					player.syncVelocity = true;
					if (wear) {
						umbrella.hurtAndBreak(1, player, slotOf(player, umbrella));
					}
				} else if (wear) {
					level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 2.0, player.getZ(), 6, 0.3, 0.1, 0.3, 0.02);
					umbrella.hurtAndBreak(SCORCH, player, slotOf(player, umbrella));
				}
			}
		}
	}

	/** A parasol caught open in a fall turns inside out and tears; you fall as before. */
	private static void tear(ServerLevel level, ServerPlayer player, ItemStack umbrella) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.9F, 0.8F);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.8, player.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
		umbrella.shrink(1);
	}

	/**
	 * The height (blocks) above a lit campfire's smoke {@code feet} are, if the smoke reaches them (a solid block between
	 * stops it; a signal fire, with hay under it, climbs higher); -1 for none.
	 */
	static int smokeBelow(Level level, BlockPos feet) {
		for (int dy = 1; dy <= SIGNAL_REACH; dy++) {
			BlockPos pos = feet.below(dy);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
				return dy <= (state.getValue(CampfireBlock.SIGNAL_FIRE) ? SIGNAL_REACH : SMOKE_REACH) ? dy : -1;
			}
			if (!state.getCollisionShape(level, pos).isEmpty()) {
				return -1;
			}
		}
		return -1;
	}
}
