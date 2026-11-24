package com.jaymoskingdoms;

import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Andy (Vanilla+, Spawners+ row): "there needs to be a spell you can learn to harvest souls for the spawners". After the
 * idea of Spawners+ (MIT; nothing of it copied).
 * <ul>
 * <li><b>Reap a Soul</b> (a Death Sorcerer's spell, from a scroll like death's others; forbidden, {@link ForbiddenArts}):
 * the living thing you look at, brought low (under {@link #REAP_SHARE} of its health), dies, and its soul is stopped in an
 * empty soul jar from your pack (a glass bottle and soul sand). Never a boss, a villager, a player or anyone with a name.</li>
 * <li><b>Feeding a spawner:</b> use a full jar on it. A jar of its own kind adds a charge ({@link #MOST_CHARGES} at most):
 * every {@link #PER_RANK} charges it reaches the next rank, and each rank reaches {@link #RANGE_STEP} blocks further, waits
 * {@link #DELAY_CUT} less and brings {@link #COUNT_STEP} more; at the last rank it goes quiet (no smoke, no flame). A jar
 * of another kind changes what it spawns. Either way the jar comes back empty.</li>
 * <li><b>Taking one up:</b> a Silk Touch pickaxe and a full jar of its own kind (used up) bring the cage away whole,
 * keeping its mob and its ranks. The barrows' cages are graves: taking one up there is robbing one, and the Lantern Warden
 * minds it ({@link PatronWrath#graveRobbed}).</li>
 * </ul>
 * The charges live in the spawner's own save ({@code mixin.souls.SoulSpawnerMixin}), so they travel with it and reach the
 * client, which leaves out a quiet one's smoke.
 */
public final class SoulJars {
	/** A soul is reaped only from a thing under this share of its health. */
	static final float REAP_SHARE = 0.3F;
	/** Charges: the most a spawner holds, and how many make a rank. */
	public static final int MOST_CHARGES = 9, PER_RANK = 3;
	/** Each rank: how much further it reaches (blocks), the share its delay is cut by, how many more it brings. */
	public static final int RANGE_STEP = 4, COUNT_STEP = 1;
	public static final float DELAY_CUT = 0.1F;
	/** The rank at which it goes quiet. */
	public static final int QUIET_RANK = 3;
	/** On a full jar: the kind of soul in it. */
	static final String SOUL_KEY = "jaymoskingdoms_soul";
	/** On a spawner taken up: its save, put back when it is set down again ({@code mixin.souls.SoulSpawnerItemMixin}). */
	public static final String CAGE_KEY = "jaymoskingdoms_cage";
	static final int SOUL_VIOLET = 0x7A5BC8;

	public static Item SOUL_JAR, FULL_SOUL_JAR;

	/** What the spawner mixin gives a spawner. */
	public interface Charged {
		int jaymoskingdoms$charges();

		void jaymoskingdoms$setCharges(int charges);

		/** What it spawns, or null when it is empty. */
		@Nullable Identifier jaymoskingdoms$kind();

		/** Each rank's step on its range, delay and count. */
		void jaymoskingdoms$rankUp();
	}

	private SoulJars() {
	}

	static void register() {
		SOUL_JAR = Arcana.item("soul_jar", p -> new Item(p.stacksTo(16)));
		FULL_SOUL_JAR = Arcana.item("full_soul_jar", p -> new FullJar(p.stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.insertAfter(Items.GLASS_BOTTLE, SOUL_JAR);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(FULL_SOUL_JAR) || !(level.getBlockEntity(hit.getBlockPos()) instanceof SpawnerBlockEntity spawner)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				feed(server, sp, spawner, stack);
			}
			return InteractionResult.SUCCESS;
		});
		PlayerBlockBreakEvents.AFTER.register(SoulJars::takenUp);
	}

	/** The kind of soul in a full jar, or null. */
	static @Nullable EntityType<?> soulIn(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (!stack.is(FULL_SOUL_JAR) || data == null) {
			return null;
		}
		return data.copyTag().getString(SOUL_KEY).map(Identifier::tryParse).flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional).orElse(null);
	}

	/** Whether a soul may be reaped from {@code e}: no boss, villager, player or anyone named. */
	static boolean reapable(LivingEntity e) {
		return e instanceof Mob && !(e instanceof AbstractVillager) && !(e instanceof Player) && !e.hasCustomName()
			&& !e.is(ConventionalEntityTypeTags.BOSSES) && !e.is(ConventionalEntityTypeTags.CAPTURING_NOT_SUPPORTED);
	}

	/** Reap a Soul: true if it took. */
	static boolean reap(ServerLevel level, Player player, float power) {
		LivingEntity target = Arcana.sighted(player);
		if (target == null || !reapable(target)) {
			return false;
		}
		if (target.getHealth() > target.getMaxHealth() * Math.min(1.0F, REAP_SHARE * power)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.soul_jar.too_strong"));
			return false;
		}
		int jar = player.getInventory().findSlotMatchingItem(new ItemStack(SOUL_JAR));
		if (jar < 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.soul_jar.none"));
			return false;
		}
		Sorcery.beam(level, player, target.getBoundingBox().getCenter(), Necromancy.VIOLET);
		level.sendParticles(Fx.mote(SOUL_VIOLET, 0.9F), target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		EntityType<?> kind = target.getType();
		target.hurtServer(level, level.damageSources().indirectMagic(player, player), Float.MAX_VALUE);
		player.getInventory().getItem(jar).shrink(1);
		ItemStack full = full(kind);
		if (!player.getInventory().add(full)) {
			player.drop(full, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		return true;
	}

	static ItemStack full(EntityType<?> kind) {
		ItemStack full = new ItemStack(FULL_SOUL_JAR);
		CompoundTag tag = new CompoundTag();
		tag.putString(SOUL_KEY, BuiltInRegistries.ENTITY_TYPE.getKey(kind).toString());
		full.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return full;
	}

	private static void feed(ServerLevel level, ServerPlayer player, SpawnerBlockEntity spawner, ItemStack stack) {
		EntityType<?> soul = soulIn(stack);
		if (soul == null) {
			return;
		}
		BaseSpawner base = spawner.getSpawner();
		Charged charged = (Charged)base;
		Identifier kind = charged.jaymoskingdoms$kind();
		BlockPos pos = spawner.getBlockPos();
		if (kind != null && kind.equals(BuiltInRegistries.ENTITY_TYPE.getKey(soul))) {
			int charges = charged.jaymoskingdoms$charges();
			if (charges >= MOST_CHARGES) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.soul_jar.full_cage"));
				return;
			}
			charged.jaymoskingdoms$setCharges(charges + 1);
			if ((charges + 1) % PER_RANK == 0) {
				charged.jaymoskingdoms$rankUp();
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.soul_jar.rank", (charges + 1) / PER_RANK).withStyle(ChatFormatting.DARK_PURPLE));
			} else {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.soul_jar.charge", charges + 1, MOST_CHARGES));
			}
		} else {
			base.setEntityId(soul, level, level.getRandom(), pos);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.soul_jar.changed", soul.getDescription()));
		}
		spawner.setChanged();
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
		level.sendParticles(Fx.mote(SOUL_VIOLET, 0.8F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
		if (!player.isCreative()) {
			stack.shrink(1);
			ItemStack empty = new ItemStack(SOUL_JAR);
			if (!player.getInventory().add(empty)) {
				player.drop(empty, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
	}

	/** A spawner broken with a Silk Touch pickaxe, by someone carrying a full jar of its kind: the cage comes away whole. */
	private static void takenUp(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (!(level instanceof ServerLevel server) || !(be instanceof SpawnerBlockEntity spawner) || player.isCreative()) {
			return;
		}
		ItemStack tool = player.getMainHandItem();
		if (!tool.is(net.minecraft.tags.ItemTags.PICKAXES)
			|| EnchantmentHelper.getItemEnchantmentLevel(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
			.getOrThrow(Enchantments.SILK_TOUCH), tool) <= 0) {
			return;
		}
		Identifier kind = ((Charged)spawner.getSpawner()).jaymoskingdoms$kind();
		int jar = -1;
		for (int i = 0; i < player.getInventory().getContainerSize() && jar < 0 && kind != null; i++) {
			EntityType<?> soul = soulIn(player.getInventory().getItem(i));
			if (soul != null && kind.equals(BuiltInRegistries.ENTITY_TYPE.getKey(soul))) {
				jar = i;
			}
		}
		if (jar < 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.soul_jar.cage_broke"));
			return;
		}
		player.getInventory().getItem(jar).shrink(1);
		ItemStack cage = new ItemStack(Items.SPAWNER);
		CompoundTag tag = new CompoundTag();
		tag.put(CAGE_KEY, spawner.saveCustomOnly(server.registryAccess()));
		cage.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		Block.popResource(server, pos, cage);
		if (com.jaymoskingdoms.barrow.Barrows.grave(server, pos) && player instanceof ServerPlayer sp) {
			PatronWrath.graveRobbed(sp);
		}
	}

	/** A full jar: named for its soul. */
	static final class FullJar extends Item {
		FullJar(Item.Properties properties) {
			super(properties);
		}

		@Override
		public Component getName(ItemStack stack) {
			EntityType<?> soul = soulIn(stack);
			return soul == null ? super.getName(stack) : Component.translatable("item.jaymoskingdoms.full_soul_jar.of", soul.getDescription());
		}
	}
}
