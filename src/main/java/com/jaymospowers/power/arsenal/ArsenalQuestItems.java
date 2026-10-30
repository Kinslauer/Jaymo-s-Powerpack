package com.jaymospowers.power.arsenal;

import com.jaymospowers.power.wind.SorasDagger;
import com.jaymospowers.power.wind.SorasLineage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.8) Andy: "arsenal has a chance to pull special quest items out of his portals, like sora's stone blade if it isnt
 * already in the world. and it is also possible to pull it from where it is in the world, straight through the portal."
 * <p>
 * Now and then ({@link #CHANCE}) a Reach (not Rummage) goes for a special quest item instead of a weapon. At the moment
 * the weapon would come out, the item is looked for in the world:
 * <ul>
 * <li>a copy is out there in loaded ground: it's taken from where it lies (a dropped item, an item frame, a container
 * in a loaded chunk near a player, a mob's hands, armour or pockets, a minecart, another online player's pack or ender
 * chest), a flash of portal opens at that spot, and it comes out of the player's rift. Further along the item's line is
 * ({@link Entry#progress}), the more often the rift lets it go ({@link #FURTHER_ALONG_ACCEPT});</li>
 * <li>no copy is out (the item's own rule says it isn't in the world yet): a new one comes out of the rift, and from
 * then on it counts as in the world;</li>
 * <li>a copy is out but somewhere that can't be reached (an unloaded chunk, an offline player's pack): the roll gives
 * an ordinary weapon.</li>
 * </ul>
 * Nothing here loads a chunk. The item is never tagged as a summon, so it doesn't dissolve. It's looked for only when
 * the weapon would arrive, so a Reach cut short loses nothing.
 */
public final class ArsenalQuestItems {
	/** The chance a Reach goes for a quest item (about 1 pull in 150), times the entry's {@link Entry#weight}. */
	static final float CHANCE = 1.0F / 150.0F;
	/** Each step further along an item's line (a stage of the dagger past the blade) makes it this much likelier to refuse to come. */
	static final float FURTHER_ALONG_ACCEPT = 0.5F;
	/** How far (chunks) around each online player containers are looked through, and the most block entities looked at. */
	static final int CHUNK_RADIUS = 6;
	static final int BLOCK_ENTITY_CAP = 6000;
	/** The rift's reply at the spot the item leaves: motes, and a thread of them out toward the player (up to this far, blocks). */
	static final int FLASH_MOTES = 30;
	static final double THREAD_RANGE = 48.0;
	static final int THREAD_STEPS = 16;

	/**
	 * One special quest item. {@code isOut}: the item's own rule says it's in the world already (a copy exists
	 * somewhere, loaded or not). {@code create}: a new one. {@code made}: told when a new one (or a taken one) has
	 * reached the player, so the item's own tracking counts it. {@code matches}: whether a stack is the copy.
	 * {@code progress}: 0 for the first form of the item, up for each form further along (a rarer pull).
	 */
	record Entry(String id, String reason, float weight, Function<MinecraftServer, Boolean> isOut,
		Function<MinecraftServer, ItemStack> create, java.util.function.Consumer<MinecraftServer> made,
		BiPredicate<MinecraftServer, ItemStack> matches, ToIntFunction<ItemStack> progress) {
	}

	/** The special quest items. Add a row to add one (an optional mod's item only through a registry lookup in its {@code create}). */
	static final List<Entry> ENTRIES = List.of(
		new Entry("soras_stone_blade",
			"Sora's Dagger: one line is out at a time (SorasLineage); the stone blade is its found first stage, and every later piece carries its mark",
			1.0F,
			SorasLineage::isOut,
			SorasLineage::newBlade,
			SorasLineage::claimed,
			(server, stack) -> {
				int serial = SorasLineage.serial(stack);
				return serial != 0 && serial == SorasLineage.currentSerial(server);
			},
			stack -> stack.getItem() instanceof SorasDagger dagger ? 1 + dagger.stage : 0));

	/** Where a copy was found, and how to take it from there. */
	private record Found(ServerLevel level, Vec3 at, ItemStack stack, Runnable remove) {
	}

	private ArsenalQuestItems() {
	}

	/** Rolled when a Reach opens: the quest item it goes for, or null (nearly always). */
	static @Nullable Entry roll(RandomSource random) {
		float total = 0.0F;
		for (Entry entry : ENTRIES) {
			total += entry.weight();
		}
		if (total <= 0.0F || random.nextFloat() >= CHANCE * total) {
			return null;
		}
		float pick = random.nextFloat() * total;
		for (Entry entry : ENTRIES) {
			pick -= entry.weight();
			if (pick < 0.0F) {
				return entry;
			}
		}
		return ENTRIES.getLast();
	}

	/** What the portal shows coming out while it opens (for show only). */
	static ItemStack preview(Entry entry, MinecraftServer server) {
		return entry.create().apply(server);
	}

	/**
	 * The item for a Reach that went for {@code entry}: a copy taken from where it is, a new one, or empty (it's
	 * somewhere out of reach, or the rift let it be: the caller gives an ordinary weapon).
	 */
	static ItemStack take(ServerPlayer player, Entry entry, Vec3 rift) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return ItemStack.EMPTY;
		}
		Found found = locate(player, entry, server);
		ItemStack out;
		if (found != null) {
			float accept = (float)Math.pow(FURTHER_ALONG_ACCEPT, entry.progress().applyAsInt(found.stack()));
			if (player.getRandom().nextFloat() >= accept) {
				return ItemStack.EMPTY;
			}
			out = found.stack().copy();
			found.remove().run();
			flash(found.level(), found.at(), player.level(), rift);
		} else if (!entry.isOut().apply(server)) {
			out = entry.create().apply(server);
		} else {
			return ItemStack.EMPTY;
		}
		entry.made().accept(server);
		return out;
	}

	// ---------------------------------------------------------------- finding it

	private static @Nullable Found locate(ServerPlayer self, Entry entry, MinecraftServer server) {
		// other players' packs and ender chests (online only)
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other == self) {
				continue;
			}
			Found found = inContainer(other.level(), other.position().add(0.0, 1.0, 0.0), other.getInventory(), entry, server);
			if (found == null) {
				found = inContainer(other.level(), other.position().add(0.0, 1.0, 0.0), other.getEnderChestInventory(), entry, server);
			}
			if (found != null) {
				return found;
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			// everything loaded: dropped items, frames, hands and armour, pockets, minecarts
			for (Entity entity : level.getAllEntities()) {
				if (entity == self || entity instanceof Player || entity.isRemoved()) {
					continue;
				}
				Found found = inEntity(level, entity, entry, server);
				if (found != null) {
					return found;
				}
			}
			// containers in loaded chunks around players (no chunk is loaded to look)
			Found found = inBlocks(level, entry, server);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static @Nullable Found inEntity(ServerLevel level, Entity entity, Entry entry, MinecraftServer server) {
		Vec3 at = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
		if (entity instanceof ItemEntity drop) {
			ItemStack stack = drop.getItem();
			if (matches(entry, server, stack)) {
				// emptied before it goes, so the line doesn't read it as destroyed (SorasLineage)
				return new Found(level, at, stack.copy(), () -> {
					drop.setItem(ItemStack.EMPTY);
					drop.discard();
				});
			}
			return null;
		}
		if (entity instanceof ItemFrame frame) {
			ItemStack stack = frame.getItem();
			return matches(entry, server, stack) ? new Found(level, at, stack.copy(), () -> frame.setItem(ItemStack.EMPTY)) : null;
		}
		if (entity instanceof LivingEntity living) {
			for (EquipmentSlot slot : EquipmentSlot.VALUES) {
				ItemStack stack = living.getItemBySlot(slot);
				if (matches(entry, server, stack)) {
					return new Found(level, at, stack.copy(), () -> living.setItemSlot(slot, ItemStack.EMPTY));
				}
			}
			if (living instanceof InventoryCarrier carrier) {
				Found found = inContainer(level, at, carrier.getInventory(), entry, server);
				if (found != null) {
					return found;
				}
			}
		}
		if (entity instanceof Container container) {
			return inContainer(level, at, container, entry, server);
		}
		return null;
	}

	private static @Nullable Found inBlocks(ServerLevel level, Entry entry, MinecraftServer server) {
		Set<Long> seen = new HashSet<>();
		int looked = 0;
		for (ServerPlayer near : level.players()) {
			ChunkPos centre = near.chunkPosition();
			for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
				for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
					if (!seen.add(ChunkPos.pack(centre.x() + dx, centre.z() + dz))) {
						continue;
					}
					LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
					if (chunk == null) {
						continue;
					}
					for (BlockEntity block : List.copyOf(chunk.getBlockEntities().values())) {
						if (++looked > BLOCK_ENTITY_CAP) {
							return null;
						}
						if (block instanceof Container container) {
							Found found = inContainer(level, Vec3.atCenterOf(block.getBlockPos()), container, entry, server);
							if (found != null) {
								return found;
							}
						}
					}
				}
			}
		}
		return null;
	}

	private static @Nullable Found inContainer(ServerLevel level, Vec3 at, Container container, Entry entry, MinecraftServer server) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (matches(entry, server, stack)) {
				int slot = i;
				return new Found(level, at, stack.copy(), () -> {
					container.setItem(slot, ItemStack.EMPTY);
					container.setChanged();
				});
			}
		}
		return null;
	}

	private static boolean matches(Entry entry, MinecraftServer server, ItemStack stack) {
		return !stack.isEmpty() && entry.matches().test(server, stack);
	}

	// ---------------------------------------------------------------- the rift's reply

	/** The spot the item left opens a flash of portal (and a thread of motes runs from it to the player's rift, in the same world). */
	private static void flash(ServerLevel from, Vec3 at, ServerLevel to, Vec3 rift) {
		from.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, FLASH_MOTES, 0.3, 0.4, 0.3, 0.6);
		from.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, FLASH_MOTES / 2, 0.2, 0.3, 0.2, 0.1);
		from.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
		from.playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.12F, 1.8F);
		if (from == to && at.distanceTo(rift) <= THREAD_RANGE) {
			for (int i = 1; i <= THREAD_STEPS; i++) {
				Vec3 p = at.lerp(rift, i / (double)THREAD_STEPS);
				from.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.2);
			}
		}
	}
}
