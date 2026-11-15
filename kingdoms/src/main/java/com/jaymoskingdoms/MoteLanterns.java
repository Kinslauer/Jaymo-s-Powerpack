package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * (medieval picks, de-flicker; Andy: "court wizards have developed a spell, that lets a light mote inside a lantern, burning
 * indefinitely") The Everburning Mote. Hand a court wizard a lantern (a burnt-out one will do): they name their price; hand
 * it again while they wait and pay, and they shut a mote of their light in it. The mote lantern burns on nothing, so it
 * never runs down under {@link Lanterns}' fills or goes out, and it never gutters in an abandoned place
 * ({@link GutteringLamps}): its flame is a little mote of light (drawn in its glass, and {@link Fx#mote} drifting in it).
 * Lore (Medieval-Plan): a mote of the court's light shut in a lantern, which never needs oil and never gutters.
 */
public final class MoteLanterns {
	// ---- tuning ----
	/** The Everburning Mote's price (emeralds' worth, in the coin of the wizard's land). */
	static final int PRICE = 16;
	/** After naming the price, the wizard waits this long (ticks) for you to hand it over again and pay. */
	static final int OFFER_HOLDS = 200;
	/** The mote lantern's light. */
	static final int LIGHT = 15;
	/** The mote's colour (pale gold, as its glass is drawn) and size. */
	static final int MOTE_COLOR = 0xFFEEAA;
	static final float MOTE_SIZE = 0.35F;

	public static Block MOTE_LANTERN;
	public static Item MOTE_LANTERN_ITEM;

	/** Wizard UUID | player UUID to when they named the price. */
	private static final Map<String, Long> OFFERED = new HashMap<>();

	private MoteLanterns() {
	}

	/** A lantern with a court wizard's mote of light in it: burns on nothing, never gutters. */
	static final class MoteLantern extends LanternBlock {
		MoteLantern(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			// the mote, hanging in the glass
			double y = pos.getY() + (state.getValue(HANGING) ? 0.38 : 0.32);
			level.addParticle(Fx.mote(MOTE_COLOR, MOTE_SIZE), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.06, y,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.06, 0.0, 0.0, 0.0);
		}
	}

	public static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("mote_lantern"));
		MOTE_LANTERN = Registry.register(BuiltInRegistries.BLOCK, key, new MoteLantern(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).lightLevel(state -> LIGHT).noOcclusion()
			.pushReaction(PushReaction.POPPED)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("mote_lantern"));
		MOTE_LANTERN_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(MOTE_LANTERN, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> OFFERED.clear());
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)
				|| !CourtWizards.is(entity) || Hermits.is(entity)) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getMainHandItem();
			if (!held.is(Items.LANTERN) && !held.is(Lanterns.BURNT_LANTERN_ITEM)) {
				return InteractionResult.PASS;
			}
			sell(level, sp, entity, held);
			return InteractionResult.SUCCESS;
		});
	}

	/** {@code player} hands the wizard {@code lantern}: the price named, or (named a moment ago) paid and the mote put in. */
	private static void sell(ServerLevel level, ServerPlayer player, net.minecraft.world.entity.Entity wizard, ItemStack lantern) {
		Faction f = Factions.get(level.getServer(), ((Worker)wizard).factionId());
		long milli = Money.worth(PRICE);
		long now = level.getGameTime();
		String key = wizard.getUUID() + "|" + player.getUUID();
		Long named = OFFERED.get(key);
		OFFERED.entrySet().removeIf(e -> now - e.getValue() > OFFER_HOLDS);
		if (named == null || now - named > OFFER_HOLDS) {
			OFFERED.put(key, now);
			Speech.tell(level, wizard, player, Component.translatable("message.jaymoskingdoms.mote.offer", Money.text(f, milli)), Speech.GOLD);
			return;
		}
		if (!Money.charge(player, f, milli)) {
			Speech.tell(level, wizard, player, Component.translatable("message.jaymoskingdoms.mote.poor", Money.text(f, milli)), Speech.WHITE);
			return;
		}
		OFFERED.remove(key);
		if (f != null && !player.hasInfiniteMaterials()) {
			Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() + PRICE));
		}
		lantern.consume(1, player);
		ItemStack mote = new ItemStack(MOTE_LANTERN_ITEM);
		if (!player.getInventory().add(mote) && !mote.isEmpty()) {
			player.spawnAtLocation(level, mote);
		}
		Vec3 at = wizard.position().add(0.0, wizard.getBbHeight() * 0.6, 0.0).add(wizard.getLookAngle().scale(0.4));
		Fx.sigil(level, at, 0.5F, MOTE_COLOR, 30);
		level.sendParticles(Fx.mote(MOTE_COLOR, MOTE_SIZE), at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.01);
		level.playSound(null, wizard.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.3F);
		Speech.tell(level, wizard, player, Component.translatable("message.jaymoskingdoms.mote.done"), Speech.GOLD);
	}
}
