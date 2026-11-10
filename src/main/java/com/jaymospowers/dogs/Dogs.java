package com.jaymospowers.dogs;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dogs that learn (Andy's TFC Hardrock picks). A tamed wolf learns from what it does with you ({@link Record}), and each
 * skill comes once it has done enough of it:
 * <ul>
 * <li><b>Hunter</b> ({@link #HUNTER_KILLS} animals killed): bites harder.</li>
 * <li><b>Tracker</b> ({@link #TRACKER_KILLS} animals killed): follows the blood trail of a wounded beast or monster to it
 * ({@code core/blood/BloodTracking}), as wild hunters do.</li>
 * <li><b>Guard</b> ({@link #GUARD_KILLS} monsters killed): tougher, with more health and a thicker coat.</li>
 * <li><b>Pack Dog</b> ({@link #PACK_BLOCKS} blocks walked at your side): carries a saddle pack of 9 slots; sneak and use
 * it with an empty hand.</li>
 * <li><b>Herder</b> ({@link #HERD_SECONDS} seconds among farm animals): while you sneak near it, the farm animals round it
 * walk to you.</li>
 * </ul>
 * Sneak and use your dog with an empty hand to hear what it knows (and open its pack, if it carries one).
 */
public final class Dogs {
	public enum Skill { HUNTER, TRACKER, GUARD, PACK_DOG, HERDER }

	static final int HUNTER_KILLS = 10, TRACKER_KILLS = 25, GUARD_KILLS = 10, PACK_BLOCKS = 2000, HERD_SECONDS = 1200;
	/** A Hunter's extra bite, a Guard's extra health and armour. */
	static final double HUNTER_DAMAGE = 2.0, GUARD_HEALTH = 10.0, GUARD_ARMOR = 4.0;
	/** How near the dog its owner must be for a walk to count, and the farm animals a Herder moves (blocks). */
	static final double WITH_OWNER = 16.0, HERD_RANGE = 10.0, AMONG_ANIMALS = 8.0;
	/** Farm animals near enough to count as herding. */
	static final int HERD_ANIMALS = 3;
	public static final int PACK_SLOTS = 9;

	/** What a dog has done and the skills it has (bit per {@link Skill}). */
	public record Record(int hunts, int guards, int walked, int herded, int skills) {
		static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("hunts", 0).forGetter(Record::hunts),
			Codec.INT.optionalFieldOf("guards", 0).forGetter(Record::guards),
			Codec.INT.optionalFieldOf("walked", 0).forGetter(Record::walked),
			Codec.INT.optionalFieldOf("herded", 0).forGetter(Record::herded),
			Codec.INT.optionalFieldOf("skills", 0).forGetter(Record::skills)
		).apply(i, Record::new));
		static final Record NONE = new Record(0, 0, 0, 0, 0);

		public boolean has(Skill skill) {
			return (skills & 1 << skill.ordinal()) != 0;
		}
	}

	public static final AttachmentType<Record> RECORD = AttachmentRegistry.create(JaymosPowers.id("dog_record"), b -> b.persistent(Record.CODEC));
	public static final AttachmentType<List<ItemStack>> PACK = AttachmentRegistry.create(JaymosPowers.id("dog_pack"),
		b -> b.persistent(ItemStack.OPTIONAL_CODEC.listOf()));

	/** Where each dog stood a second ago, for the walk it counts. Not saved. */
	private static final Map<UUID, Vec3> LAST = new HashMap<>();

	private Dogs() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Wolf dead && dead.level() instanceof ServerLevel level) {
				dropPack(level, dead);
			}
			if (source.getEntity() instanceof Wolf wolf && wolf.isTame() && wolf.level() instanceof ServerLevel) {
				Record r = record(wolf);
				if (entity instanceof Enemy) {
					learn(wolf, new Record(r.hunts, r.guards + 1, r.walked, r.herded, r.skills));
				} else if (entity instanceof Animal) {
					learn(wolf, new Record(r.hunts + 1, r.guards, r.walked, r.herded, r.skills));
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Dogs::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Wolf wolf) || !wolf.isTame() || !wolf.isOwnedBy(player) || !player.isShiftKeyDown()
				|| hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server) {
				Record r = record(wolf);
				server.sendOverlayMessage(knows(r));
				if (r.has(Skill.PACK_DOG)) {
					openPack(server, wolf);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	public static Record record(Entity dog) {
		return dog.getAttachedOrElse(RECORD, Record.NONE);
	}

	/** {@code dog} is a tame Tracker: it follows blood trails ({@code BloodTracking}). */
	public static boolean tracks(TamableAnimal dog) {
		return dog.isTame() && !dog.isOrderedToSit() && record(dog).has(Skill.TRACKER);
	}

	private static Component knows(Record r) {
		MutableComponent skills = Component.empty();
		for (Skill skill : Skill.values()) {
			if (r.has(skill)) {
				if (!skills.getSiblings().isEmpty()) {
					skills.append(", ");
				}
				skills.append(Component.translatable("dog_skill.jaymospowers." + skill.name().toLowerCase(java.util.Locale.ROOT)));
			}
		}
		return skills.getSiblings().isEmpty() ? Component.translatable("message.jaymospowers.dog.nothing")
			: Component.translatable("message.jaymospowers.dog.knows", skills);
	}

	/** Saves {@code r} and teaches any skill it has now earned. */
	static void learn(Wolf wolf, Record r) {
		int skills = r.skills;
		skills |= r.hunts >= HUNTER_KILLS ? bit(Skill.HUNTER) : 0;
		skills |= r.hunts >= TRACKER_KILLS ? bit(Skill.TRACKER) : 0;
		skills |= r.guards >= GUARD_KILLS ? bit(Skill.GUARD) : 0;
		skills |= r.walked >= PACK_BLOCKS ? bit(Skill.PACK_DOG) : 0;
		skills |= r.herded >= HERD_SECONDS ? bit(Skill.HERDER) : 0;
		int fresh = skills & ~r.skills;
		wolf.setAttached(RECORD, new Record(r.hunts, r.guards, r.walked, r.herded, skills));
		if (fresh == 0 || !(wolf.level() instanceof ServerLevel level)) {
			return;
		}
		for (Skill skill : Skill.values()) {
			if ((fresh & bit(skill)) != 0) {
				taught(level, wolf, skill);
			}
		}
	}

	private static int bit(Skill skill) {
		return 1 << skill.ordinal();
	}

	private static void taught(ServerLevel level, Wolf wolf, Skill skill) {
		switch (skill) {
			case HUNTER -> modifier(wolf.getAttribute(Attributes.ATTACK_DAMAGE), "dog_hunter", HUNTER_DAMAGE);
			case GUARD -> {
				modifier(wolf.getAttribute(Attributes.MAX_HEALTH), "dog_guard", GUARD_HEALTH);
				modifier(wolf.getAttribute(Attributes.ARMOR), "dog_guard", GUARD_ARMOR);
				wolf.heal((float)GUARD_HEALTH);
			}
			default -> {
			}
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 10, 0.3, 0.3, 0.3, 0.0);
		level.playSound(null, wolf.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.4F);
		if (wolf.getOwner() instanceof ServerPlayer owner) {
			owner.sendSystemMessage(Component.translatable("message.jaymospowers.dog.learned", wolf.getDisplayName(),
				Component.translatable("dog_skill.jaymospowers." + skill.name().toLowerCase(java.util.Locale.ROOT))).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void modifier(@Nullable AttributeInstance attribute, String id, double amount) {
		if (attribute != null) {
			attribute.addOrReplacePermanentModifier(new AttributeModifier(JaymosPowers.id(id), amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			for (Wolf wolf : level.getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(WITH_OWNER), w -> w.isTame() && w.isOwnedBy(player))) {
				Record r = record(wolf);
				int walked = r.walked, herded = r.herded;
				Vec3 last = LAST.put(wolf.getUUID(), wolf.position());
				if (last != null && !wolf.isOrderedToSit()) {
					walked += (int)Math.min(10.0, last.distanceTo(wolf.position()));
				}
				List<Animal> flock = level.getEntitiesOfClass(Animal.class, wolf.getBoundingBox().inflate(AMONG_ANIMALS),
					a -> !(a instanceof TamableAnimal) && a.isAlive());
				if (flock.size() >= HERD_ANIMALS) {
					herded++;
				}
				if (walked != r.walked || herded != r.herded) {
					learn(wolf, new Record(r.hunts, r.guards, Math.min(walked, PACK_BLOCKS), Math.min(herded, HERD_SECONDS), r.skills));
				}
				if (r.has(Skill.HERDER) && player.isShiftKeyDown() && !wolf.isOrderedToSit()) {
					for (Animal animal : level.getEntitiesOfClass(Animal.class, wolf.getBoundingBox().inflate(HERD_RANGE),
						a -> !(a instanceof TamableAnimal) && a.isAlive() && !a.isLeashed() && a.distanceToSqr(player) > 9.0)) {
						animal.getNavigation().moveTo(player, 1.1);
					}
				}
			}
		}
	}

	private static void openPack(ServerPlayer player, Wolf wolf) {
		SimpleContainer pack = new SimpleContainer(PACK_SLOTS) {
			@Override
			public void setChanged() {
				super.setChanged();
				List<ItemStack> items = new ArrayList<>();
				for (int i = 0; i < getContainerSize(); i++) {
					items.add(getItem(i).copy());
				}
				wolf.setAttached(PACK, items);
			}

			@Override
			public boolean stillValid(net.minecraft.world.entity.player.Player p) {
				return wolf.isAlive() && p.distanceToSqr(wolf) < 64.0;
			}
		};
		List<ItemStack> held = wolf.getAttachedOrElse(PACK, List.of());
		for (int i = 0; i < Math.min(PACK_SLOTS, held.size()); i++) {
			pack.setItem(i, held.get(i).copy());
		}
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, pack, 1),
			Component.translatable("container.jaymospowers.dog_pack", wolf.getDisplayName())));
	}

	/** A dog that dies drops what it carried. */
	public static void dropPack(ServerLevel level, Wolf wolf) {
		for (ItemStack stack : wolf.getAttachedOrElse(PACK, List.<ItemStack>of())) {
			if (!stack.isEmpty()) {
				wolf.spawnAtLocation(level, stack.copy());
			}
		}
		wolf.removeAttached(PACK);
	}
}
