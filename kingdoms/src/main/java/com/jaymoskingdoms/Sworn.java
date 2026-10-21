package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.138) With MCA, a realm's guards, archers and rulers are its own people: when one is called for, one of its grown
 * villagers is sworn in (gone from the street in a puff) and takes up the post as one of its folk, so the throne, the
 * escort, the gate and the rest work as they always have. They keep who they were: their name ({@link #NAME}, before
 * their title, "Aldric, King of Eldmoor"), their heritage (and a Warrior-born's tail, a Verdant's green), and who they
 * were born ({@link #BORN_AS}: the villager's UUID, which MCA's family tree knows them by).
 * <ul>
 * <li><b>Guards and archers</b> are taken only while the town keeps {@link Economy#MIN_VILLAGERS_KEPT} villagers, those
 * without a trade first (a purist's guards from his own blood first, {@link Purists}). Hired guards are made MCA guards
 * where they stand, as before ({@code McaBridge.enlist}).</li>
 * <li><b>Rulers:</b> a realm's first ruler and each successor is one of its villagers. Each realm remembers its ruling
 * house ({@link #HOUSES}: who its ruler was born), so the heir is his family first (his children, brothers and sisters,
 * by MCA's tree), then his widow, then anyone grown in the town. The new ruler's temper is drawn as any ruler's is
 * ({@link Tempers}), only never a purist of another blood than his own ({@link #rulerId}).</li>
 * <li>With no villager to spare, a new hand comes from elsewhere, as without MCA.</li>
 * </ul>
 */
public final class Sworn {
	/** Tries at a ruler's id whose temper fits his blood ({@link #rulerId}). */
	static final int RULER_ID_TRIES = 256;

	/** The name a sworn folk was born with (MCA's). Saved on the folk. */
	public static final AttachmentType<String> NAME = AttachmentRegistry.create(JaymosKingdoms.id("sworn_name"),
		builder -> builder.persistent(Codec.STRING));
	/** The villager a sworn folk was (their UUID in MCA's family tree). Saved on the folk. */
	public static final AttachmentType<UUID> BORN_AS = AttachmentRegistry.create(JaymosKingdoms.id("sworn_born_as"),
		builder -> builder.persistent(UUIDUtil.CODEC));
	/** Each realm's ruling house (by faction id): the villager its ruler (or late ruler) was. Saved on the Overworld. */
	static final AttachmentType<Map<String, UUID>> HOUSES = AttachmentRegistry.create(JaymosKingdoms.id("ruling_houses"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC)));

	private Sworn() {
	}

	/** Roles a realm swears in from its villagers. */
	static boolean swears(Faction f, int role) {
		return McaCompat.LOADED && f.type().isRealm() && (role == Folk.GUARD || role == Folk.ARCHER || role == Folk.KING || role == Folk.LORD);
	}

	/** The villager {@code f} swears in as its {@code role}, or null (none to spare, not a realm, or no MCA). */
	static @Nullable Villager pick(ServerLevel level, Faction f, int role) {
		if (!swears(f, role)) {
			return null;
		}
		Folk.Role spec = Folk.role(role);
		boolean ruler = spec != null && spec.leader();
		return McaCompat.sworn(level, f, role, ruler ? house(level.getServer(), f.id()) : null);
	}

	/**
	 * {@code villager} becomes {@code folk} (set up in its role, not yet in the world): their name, heritage and birth
	 * carried over, and they're gone from where they stood. A ruler is given his id here, before he's crowned.
	 */
	static void take(ServerLevel level, Folk folk, Villager villager) {
		MinecraftServer server = level.getServer();
		Heritage.Kind kind = VillagerHeritage.of(villager);
		// (a purist realm's own folk are all of its ruler's blood: VillagerHeritage#folkSetup)
		Heritage.Kind proud = Purists.folk(server, folk.factionId());
		if (kind != null && proud == null) {
			String tail = villager.entityTags().contains(VillagerHeritage.TAIL_CUT) ? VillagerHeritage.TAIL_CUT
				: villager.entityTags().contains(VillagerHeritage.TAIL_WRAPPED) ? VillagerHeritage.TAIL_WRAPPED : null;
			Float shade = villager.getAttached(Verdants.SHADE);
			if (shade != null) {
				folk.setAttached(Verdants.SHADE, shade);
			}
			VillagerHeritage.born(folk, kind, tail);
			for (String tag : new String[] {VillagerHeritage.APE_TURNED, VillagerHeritage.APE_CUT}) {
				if (villager.entityTags().contains(tag)) {
					folk.addTag(tag);
				}
			}
		}
		if (folk.leader()) {
			folk.setUUID(rulerId(VillagerHeritage.of(folk)));
		}
		folk.setAttached(NAME, villager.getName().getString());
		folk.setAttached(BORN_AS, villager.getUUID());
		titled(folk);
		level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 1.0, villager.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		JaymosKingdoms.LOGGER.debug("{} swore in {} as its {}", folk.factionId(), villager.getName().getString(), Folk.roleName(folk.role()));
		villager.discard();
	}

	/** After its title was given ({@link Folk#setup}, a change of rank): a sworn folk's own name goes before it. */
	static void titled(Folk folk) {
		String name = folk.getAttached(NAME);
		Component title = folk.getCustomName();
		if (name != null && title != null) {
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.sworn", name, title));
		}
	}

	/**
	 * A new ruler's id: a fresh one whose temper ({@link Tempers#of}) isn't a purist of another blood than {@code blood}
	 * (a purist's blood is drawn from his id, {@link Purists#blood}, and his realm's folk are all made of it).
	 */
	static UUID rulerId(Heritage.@Nullable Kind blood) {
		UUID id = UUID.randomUUID();
		for (int i = 0; i < RULER_ID_TRIES && Tempers.of(id) == Tempers.Temper.PURIST && Purists.blood(id) != blood; i++) {
			id = UUID.randomUUID();
		}
		return id;
	}

	/** {@code f}'s ruling house: who its ruler (or late ruler) was born, if one of its villagers. */
	static @Nullable UUID house(MinecraftServer server, int faction) {
		return Factions.level(server).getAttachedOrElse(HOUSES, Map.<String, UUID>of()).get(Integer.toString(faction));
	}

	/** {@code ruler} took {@code f}'s throne: its house is his (none, if he wasn't one of its villagers). */
	static void crowned(MinecraftServer server, int faction, @Nullable Folk ruler) {
		ServerLevel level = Factions.level(server);
		Map<String, UUID> houses = new HashMap<>(level.getAttachedOrElse(HOUSES, Map.<String, UUID>of()));
		UUID born = ruler == null ? null : ruler.getAttached(BORN_AS);
		if (born == null) {
			houses.remove(Integer.toString(faction));
		} else {
			houses.put(Integer.toString(faction), born);
		}
		level.setAttached(HOUSES, Map.copyOf(houses));
	}
}
