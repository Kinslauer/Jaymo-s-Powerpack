package com.jaymospowers.alchemy.gear;

import com.jaymospowers.JaymosPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.47) Weapon Forms patch 3: the Clockwork Hornet Hive. Golemancy-adjacent Artifice research ({@link #RESEARCH}), made on
 * the infusion altar (data/jaymospowers/alchemy/infusion/clockwork_hornet_hive.json; no grid recipe). Using the hive looses
 * {@value #COUNT} {@link ClockworkHornet}s for {@value #LIFE_TICKS} ticks that harry the user's lock-on target or the last
 * thing that hurt them.
 */
public final class ClockworkHornets {
	public static final String RESEARCH = "clockwork_hornets";
	/** How many a hive looses, at most how many one owner may have out, how long they last (ticks). */
	public static final int COUNT = 3, MAX_OUT = 9, LIFE_TICKS = 400;
	/** Ticks between a hornet's stings, and the damage of one; how long it backs off after one. */
	public static final int STING_EVERY = 16, RETREAT_TICKS = 6;
	public static final float STING_DAMAGE = 2.0F;
	/** Blocks a tick: chasing, idling round the owner; how far (blocks) it strays before it hurries back, and how far a foe may be to chase. */
	public static final double SPEED_CHASE = 0.42, SPEED_IDLE = 0.3, LEASH = 6.0, CHASE_RANGE = 32.0;
	/** A foe that hurt the owner is chased for this many ticks afterwards. */
	public static final int RECENT_TICKS = 200;
	/** The wait before another hive may be used (ticks). */
	public static final int COOLDOWN = 100;

	public static EntityType<ClockworkHornet> TYPE;
	/** The hive, and the drone's own item (not in any tab: it is only what the drone is drawn as). */
	public static Item HIVE, HORNET;

	private ClockworkHornets() {
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("clockwork_hornet"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<ClockworkHornet>of(ClockworkHornet::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.3F, 0.3F)
			.clientTrackingRange(8)
			.updateInterval(2)
			.build(key));
		ResourceKey<Item> hornet = ResourceKey.create(Registries.ITEM, JaymosPowers.id("clockwork_hornet"));
		HORNET = Registry.register(BuiltInRegistries.ITEM, hornet, new Item(new Item.Properties().setId(hornet)));
		HIVE = com.jaymospowers.alchemy.Alchemy.item("clockwork_hornet_hive", p -> new HiveItem(p.stacksTo(16).rarity(Rarity.UNCOMMON).component(net.minecraft.core.component.DataComponents.LORE,
			new net.minecraft.world.item.component.ItemLore(java.util.List.of(Component.translatable("tooltip.jaymospowers.clockwork_hornet_hive").withStyle(ChatFormatting.GRAY))))));
	}

	/** The hive: use it to loose the hornets. */
	static final class HiveItem extends Item {
		HiveItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer owner)) {
				return InteractionResult.SUCCESS;
			}
			int out = server.getEntitiesOfClass(ClockworkHornet.class, owner.getBoundingBox().inflate(64.0), h -> h.isAlive() && h.ownedBy(owner)).size();
			if (out + COUNT > MAX_OUT) {
				owner.sendOverlayMessage(Component.translatable("message.jaymospowers.clockwork_hornets.enough").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			for (int i = 0; i < COUNT; i++) {
				ClockworkHornet drone = TYPE.create(server, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
				if (drone != null) {
					drone.setOwner(owner);
					Vec3 at = owner.position().add(Math.cos(i * 2.1) * 0.8, owner.getBbHeight(), Math.sin(i * 2.1) * 0.8);
					drone.setPos(at);
					server.addFreshEntity(drone);
				}
			}
			server.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEEHIVE_EXIT, SoundSource.PLAYERS, 1.0F, 1.5F);
			ItemStack held = owner.getItemInHand(hand);
			owner.getCooldowns().addCooldown(held, COOLDOWN);
			held.consume(1, owner);
			return InteractionResult.SUCCESS;
		}
	}
}
