package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.459) More ways to heal (Healing-Plan 9 to 11): the town healer's full treatment for emeralds, the infirmary cot
 * ({@link InfirmaryCot}) and the shrine ({@link Shrine}). All of them reach the body's parts through Jaymo's Powers' wounds
 * ({@link PowersCompat}); without Powers they heal the hearts. This class holds what they share: the treatment itself, its
 * price, what makes a place an infirmary, and the blocks' registration.
 */
public final class Healing {
	// ---- the healer's price (emeralds) ----
	/** (0.7.459) A full treatment: a base, then so much for each break, each open cut, each festering part, and for every quarter of health lost. */
	static final int TREATMENT_BASE = 4, PER_BREAK = 5, PER_CUT = 1, PER_FESTER = 3, PER_QUARTER_LOST = 2;
	/** (0.7.459) The most a treatment ever costs. */
	static final int TREATMENT_MAX = 48;

	// ---- the infirmary ----
	/** (0.7.459) A cleric or court wizard this near (blocks), or a brewing stand this near, makes a cot an infirmary. */
	static final double HEALER_NEAR = 16.0;
	static final int STAND_NEAR = 8;
	/** (0.7.459) How long (ticks) you lie on a cot, asleep or resting, before an infirmary treats you. */
	static final int COT_TICKS = 100;

	public static Block COT, SHRINE;
	public static Item COT_ITEM, SHRINE_ITEM;

	/** Ticks each player has lain on a cot, and who has been treated this lying down. */
	private static final Map<UUID, Integer> LYING = new HashMap<>();
	private static final Set<UUID> TREATED = new HashSet<>();

	private Healing() {
	}

	static void register() {
		ResourceKey<Block> cot = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("infirmary_cot"));
		COT = Registry.register(BuiltInRegistries.BLOCK, cot, new InfirmaryCot(BlockBehaviour.Properties.of().setId(cot)
			.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(0.2F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));
		COT_ITEM = blockItem("infirmary_cot", COT);
		ResourceKey<Block> shrine = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("shrine"));
		SHRINE = Registry.register(BuiltInRegistries.BLOCK, shrine, new Shrine(BlockBehaviour.Properties.of().setId(shrine)
			.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops().noOcclusion()
			.lightLevel(state -> Shrine.LIGHT)));
		SHRINE_ITEM = blockItem("shrine", SHRINE);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.insertAfter(Items.SMOKER, COT_ITEM);
			output.insertAfter(COT_ITEM, SHRINE_ITEM);
		});
		// (the shrine's attachment loads with its class)
		JaymosKingdoms.LOGGER.debug("shrine attachment: {}", Shrine.PRAYED);
		ServerTickEvents.END_SERVER_TICK.register(Healing::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LYING.clear();
			TREATED.clear();
		});
		Shrine.register();
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	// ---- the treatment ----------------------------------------------------------------------------------------------

	/** Whether {@code body} has anything a healer could treat: a break, a cut, festering, or lost health. */
	static boolean needsTreatment(LivingEntity body) {
		return PowersCompat.injuries(body).any() || body.getHealth() < body.getMaxHealth() - 0.01F;
	}

	/** What a full treatment of {@code body} costs, in emeralds (see the constants above). */
	static int treatmentPrice(LivingEntity body) {
		PowersCompat.Injuries hurt = PowersCompat.injuries(body);
		float lost = Math.max(0.0F, 1.0F - body.getHealth() / body.getMaxHealth());
		int price = TREATMENT_BASE + PER_BREAK * Integer.bitCount(hurt.broken()) + PER_CUT * Integer.bitCount(hurt.bleeding())
			+ PER_FESTER * Integer.bitCount(hurt.festering()) + PER_QUARTER_LOST * (int)Math.ceil(lost * 4.0F - 0.01F);
		return Math.min(TREATMENT_MAX, price);
	}

	/**
	 * A full treatment: every cut closed, festering cleaned, every break set, then every part healed to its fullest (and so the
	 * breaks knit). A severed limb stays gone (that is a regrowing's work), a downed body is left as it is, and no curse is lifted.
	 */
	static void treat(LivingEntity body) {
		while (PowersCompat.closeCut(body)) {
			// (every cut closed)
		}
		PowersCompat.easeFever(body);
		PowersCompat.setBones(body);
		// two passes: parts heal a share at a time, and what a break held down is let go once it is set
		for (int pass = 0; pass < 2; pass++) {
			if (PowersCompat.healEveryPart(body, body.getMaxHealth()) < 0.0F) {
				body.heal(body.getMaxHealth());
				break;
			}
		}
	}

	// ---- the infirmary ----------------------------------------------------------------------------------------------

	/** Someone who tends the sick near {@code pos}: a village cleric or a court wizard, or null. */
	static @Nullable LivingEntity healerNear(ServerLevel level, BlockPos pos) {
		AABB box = new AABB(pos).inflate(HEALER_NEAR);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e.isAlive() && (e instanceof Villager v && Restoration.cleric(v) || CourtWizards.is(e)))) {
			double d = e.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (d < bestDistance) {
				best = e;
				bestDistance = d;
			}
		}
		return best;
	}

	/** Whether a brewing stand stands within {@link #STAND_NEAR} blocks of {@code pos}. */
	static boolean standNear(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-STAND_NEAR, -STAND_NEAR / 2, -STAND_NEAR), pos.offset(STAND_NEAR, STAND_NEAR / 2, STAND_NEAR))) {
			if (level.getBlockState(p).is(Blocks.BREWING_STAND)) {
				return true;
			}
		}
		return false;
	}

	private static void forget(UUID id) {
		LYING.remove(id);
		TREATED.remove(id);
	}

	/** Players lying on a cot (asleep or resting): after {@link #COT_TICKS}, an infirmary treats them, once a lying down. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			BlockPos bed = player.isSleeping() ? player.getSleepingPos().orElse(null) : null;
			if (bed == null || !(player.level().getBlockState(bed).getBlock() instanceof InfirmaryCot)) {
				if (!LYING.isEmpty() || !TREATED.isEmpty()) {
					forget(id);
				}
				continue;
			}
			int ticks = LYING.merge(id, 1, Integer::sum);
			if (ticks >= COT_TICKS && TREATED.add(id)) {
				infirm(player.level(), player, bed);
			}
		}
	}

	private static void infirm(ServerLevel level, ServerPlayer player, BlockPos bed) {
		LivingEntity healer = healerNear(level, bed);
		if (healer == null && !standNear(level, bed)) {
			return;
		}
		if (!needsTreatment(player)) {
			return;
		}
		treat(player);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.6, player.getZ(), 10, 0.4, 0.3, 0.4, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.2F);
		if (healer != null) {
			Speech.tell(level, healer, player, Component.translatable("talk.jaymoskingdoms.healer.cot"), Speech.GOLD);
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cot.brewing"));
		}
	}
}
