package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.137) The wind dragons ({@code docs/Dragons-Plan.md}); for now Notus ({@link NotusDragon}). In the Overworld it
 * comes as a rare wilderness encounter in the badlands ({@code Encounters}, biome tag
 * {@code jaymoskingdoms:has_notus_dragon}); in the Nether, where encounters don't reach, this looks for it over the
 * basalt deltas (the same tag). The rider's breath key sends {@link Breathe}. Ops: {@code /kingdom dragon [tame]}.
 */
public final class Dragons {
	/** The Nether: how often each player there is weighed (ticks), and the chance Notus turns up then (about once in 25 hours in the deltas). */
	static final int NETHER_CHECK_TICKS = 600;
	static final float NETHER_CHANCE = 1.0F / 1500.0F;
	/** How far off it turns up there. */
	static final int NETHER_MIN = 24, NETHER_MAX = 40;

	public static final TagKey<Biome> HAS_NOTUS = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_notus_dragon"));

	public static EntityType<NotusDragon> NOTUS_DRAGON;
	public static Item NOTUS_DRAGON_SPAWN_EGG;

	private Dragons() {
	}

	/** Client → server: the rider holds the breath key, looking along yaw/pitch (sent every other tick while held). */
	public record Breathe(float yaw, float pitch) implements CustomPacketPayload {
		public static final Type<Breathe> TYPE = new Type<>(JaymosKingdoms.id("dragon_breathe"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Breathe> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeFloat(p.yaw);
				buf.writeFloat(p.pitch);
			},
			buf -> new Breathe(buf.readFloat(), buf.readFloat()));

		@Override
		public Type<Breathe> type() {
			return TYPE;
		}
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("notus_dragon"));
		NOTUS_DRAGON = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(NotusDragon::new, MobCategory.CREATURE)
			.sized(3.0F, 2.4F)
			.fireImmune()
			.clientTrackingRange(16)
			.build(key));
		FabricDefaultAttributeRegistry.register(NOTUS_DRAGON, NotusDragon.createAttributes());
		ResourceKey<Item> egg = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("notus_dragon_spawn_egg"));
		NOTUS_DRAGON_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, egg, new SpawnEggItem(new Item.Properties().setId(egg).spawnEgg(NOTUS_DRAGON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.insertAfter(Items.ENDER_DRAGON_SPAWN_EGG,
			NOTUS_DRAGON_SPAWN_EGG));

		PayloadTypeRegistry.serverboundPlay().register(Breathe.TYPE, Breathe.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Breathe.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.getVehicle() instanceof NotusDragon dragon && dragon.isTamed() && dragon.getControllingPassenger() == player) {
				dragon.riderBreath(payload.yaw(), payload.pitch());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Dragons::nether);
		// testing (op): /kingdom dragon [tame]: a wild Notus, or a tamed, saddled one, at your side
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("dragon").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> spawn(c.getSource().getPlayerOrException(), false))
				.then(Commands.literal("tame").executes(c -> spawn(c.getSource().getPlayerOrException(), true))))));
	}

	private static int spawn(ServerPlayer player, boolean tame) {
		NotusDragon dragon = NOTUS_DRAGON.create(player.level(), EntitySpawnReason.COMMAND);
		if (dragon == null) {
			return 0;
		}
		BlockPos at = player.blockPosition().relative(player.getDirection(), 5);
		dragon.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
		dragon.finalizeSpawn(player.level(), player.level().getCurrentDifficultyAt(at), EntitySpawnReason.COMMAND, null);
		player.level().addFreshEntity(dragon);
		if (tame) {
			dragon.tameWithName(player);
			dragon.setPersistenceRequired();
			dragon.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		}
		return 1;
	}

	/** Over the basalt deltas (anywhere tagged {@code has_notus_dragon} in the Nether), now and then Notus finds a player. */
	private static void nether(MinecraftServer server) {
		if (server.getTickCount() % NETHER_CHECK_TICKS != 0) {
			return;
		}
		ServerLevel level = server.getLevel(Level.NETHER);
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !level.getBiome(player.blockPosition()).is(HAS_NOTUS)
				|| random.nextFloat() >= NETHER_CHANCE) {
				continue;
			}
			BlockPos spot = spot(level, player, random);
			if (spot == null) {
				continue;
			}
			NotusDragon dragon = NOTUS_DRAGON.create(level, EntitySpawnReason.EVENT);
			if (dragon == null) {
				continue;
			}
			dragon.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
			if (!level.noCollision(dragon)) {
				continue;
			}
			level.addFreshEntity(dragon);
			dragon.setTarget(player);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.encounter.notus_dragon").withStyle(ChatFormatting.GOLD));
		}
	}

	/** Open air over solid ground some way from the player, at about their height (under the Nether's roof). */
	private static @Nullable BlockPos spot(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = NETHER_MIN + random.nextInt(NETHER_MAX - NETHER_MIN + 1);
			BlockPos at = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY() + random.nextInt(9) - 2,
				player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(at)) {
				continue;
			}
			boolean room = true;
			for (int y = 0; y < 4 && room; y++) {
				room = level.getBlockState(at.above(y)).isAir();
			}
			if (room && level.getBlockState(at.below()).isSolid()) {
				return at;
			}
		}
		return null;
	}
}
