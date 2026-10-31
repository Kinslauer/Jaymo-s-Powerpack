package com.jaymospowers.power.xenoform;

import com.jaymospowers.power.xenoform.forms.Velox;
import com.jaymospowers.power.xenoform.forms.Bulwark;
import com.jaymospowers.power.xenoform.forms.Duplex;
import com.jaymospowers.power.xenoform.forms.Gremlin;
import com.jaymospowers.power.xenoform.forms.Knell;
import com.jaymospowers.power.xenoform.forms.Nadir;
import com.jaymospowers.power.xenoform.forms.Prism;
import com.jaymospowers.power.xenoform.forms.Wisp;
import com.jaymospowers.power.xenoform.forms.Wyrm;
import com.jaymospowers.power.xenoform.forms.Pyre;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Every alien the band knows, in dial order. The first {@link #STARTERS} are the ones a fresh band comes with one of;
 * the rest are scanned from the creatures that carry their DNA. Stage 1 shipped Bulwark, Pyre and Blur (now Velox); stage 2 added Prism, Wisp,
 * Gremlin, Knell (scanned from a Warden) and Wyrm (from the Ender Dragon); stage 3 Nadir, the void alien the voices give; then Duplex (from a copper golem).
 */
public final class XenoForms {
	private static final Map<String, XenoForm> ALL = new LinkedHashMap<>();
	/** How many of the first forms a new band may start with. */
	public static final int STARTERS = 3;

	public static final XenoForm BULWARK = register(new Bulwark());
	public static final XenoForm PYRE = register(new Pyre());
	public static final XenoForm VELOX = register(new Velox());
	public static final XenoForm PRISM = register(new Prism());
	public static final XenoForm WISP = register(new Wisp());
	public static final XenoForm GREMLIN = register(new Gremlin());
	public static final XenoForm KNELL = register(new Knell());
	public static final XenoForm WYRM = register(new Wyrm());
	public static final XenoForm DUPLEX = register(new Duplex());
	/** (0.18.95) Rivet, the living metal (from a clockwork golem). */
	public static final XenoForm RIVET = register(new com.jaymospowers.power.xenoform.forms.Rivet());
	/** (0.18.96, patch 4) Gorger (an armadillo), Slipfin (an axolotl), Polestar (the Magnetic Giant). */
	public static final XenoForm GORGER = register(new com.jaymospowers.power.xenoform.forms.Gorger());
	public static final XenoForm SLIPFIN = register(new com.jaymospowers.power.xenoform.forms.Slipfin());
	public static final XenoForm POLESTAR = register(new com.jaymospowers.power.xenoform.forms.Polestar());
	/** The void alien: no creature carries it; the two voices give it ({@link XenoVoices}). */
	public static final XenoForm NADIR = register(new Nadir());
	/** (0.18.103, patch 5) Gyre (a turtle), Cortex (a guardian), Locust (a goat), Croaker (a frog). */
	public static final XenoForm GYRE = register(new com.jaymospowers.power.xenoform.forms.Gyre());
	public static final XenoForm CORTEX = register(new com.jaymospowers.power.xenoform.forms.Cortex());
	public static final XenoForm LOCUST = register(new com.jaymospowers.power.xenoform.forms.Locust());
	public static final XenoForm CROAKER = register(new com.jaymospowers.power.xenoform.forms.Croaker());
	/** (0.18.107, Scan B) Rime (a stray or snow golem), Marrow (a skeleton), Swathe (a husk). */
	public static final XenoForm RIME = register(new com.jaymospowers.power.xenoform.forms.Rime());
	public static final XenoForm MARROW = register(new com.jaymospowers.power.xenoform.forms.Marrow());
	public static final XenoForm SWATHE = register(new com.jaymospowers.power.xenoform.forms.Swathe());
	/** (0.18.109, Scan C) Howl (a wolf), Prowl (an ocelot), Tusk (a hoglin), Talon (a parrot). */
	public static final XenoForm HOWL = register(new com.jaymospowers.power.xenoform.forms.Howl());
	public static final XenoForm PROWL = register(new com.jaymospowers.power.xenoform.forms.Prowl());
	public static final XenoForm TUSK = register(new com.jaymospowers.power.xenoform.forms.Tusk());
	public static final XenoForm TALON = register(new com.jaymospowers.power.xenoform.forms.Talon());
	/** (0.18.111, Scan D) Murk (a squid), Gnasher (a dolphin), Lumen (an allay), Keen (a bat). */
	public static final XenoForm MURK = register(new com.jaymospowers.power.xenoform.forms.Murk());
	public static final XenoForm GNASHER = register(new com.jaymospowers.power.xenoform.forms.Gnasher());
	public static final XenoForm LUMEN = register(new com.jaymospowers.power.xenoform.forms.Lumen());
	public static final XenoForm KEEN = register(new com.jaymospowers.power.xenoform.forms.Keen());
	/** (0.18.113, Scan E) Hive (a bee), Skitter (a spider), Shade (an enderman), Mirage (an illusioner). */
	public static final XenoForm HIVE = register(new com.jaymospowers.power.xenoform.forms.Hive());
	public static final XenoForm SKITTER = register(new com.jaymospowers.power.xenoform.forms.Skitter());
	public static final XenoForm SHADE = register(new com.jaymospowers.power.xenoform.forms.Shade());
	public static final XenoForm MIRAGE = register(new com.jaymospowers.power.xenoform.forms.Mirage());
	/** (0.18.115, Scan F) Ooze (a slime; Magma Ooze from a magma cube), Fuse (a creeper), Thornwild (a creaking), Billow (a happy ghast). */
	public static final XenoForm OOZE = register(new com.jaymospowers.power.xenoform.forms.Ooze(false));
	public static final XenoForm MAGMA_OOZE = register(new com.jaymospowers.power.xenoform.forms.Ooze(true));
	public static final XenoForm FUSE = register(new com.jaymospowers.power.xenoform.forms.Fuse());
	public static final XenoForm THORNWILD = register(new com.jaymospowers.power.xenoform.forms.Thornwild());
	public static final XenoForm BILLOW = register(new com.jaymospowers.power.xenoform.forms.Billow());
	/** (0.18.96) The stalker's own shape, scanned from its shed skin ({@link XenoStalker}); hidden, but kept once found. */
	public static final XenoForm LURK = register(new com.jaymospowers.power.xenoform.forms.Lurk());
	/** (0.18.103, patch 5) Dud, the one that can't be hurt: never scanned, only a corrupted band's glitch teaches it; hidden. */
	public static final XenoForm DUD = register(new com.jaymospowers.power.xenoform.forms.Dud());
	/** (0.18.107, Scan B) Blight, scanned from the Wither; hidden. */
	public static final XenoForm BLIGHT = register(new com.jaymospowers.power.xenoform.forms.Blight());

	private XenoForms() {
	}

	private static XenoForm register(XenoForm form) {
		ALL.put(form.id, form);
		return form;
	}

	public static @Nullable XenoForm get(@Nullable String id) {
		return id == null ? null : ALL.get(id);
	}

	public static List<XenoForm> all() {
		return List.copyOf(ALL.values());
	}

	/** How many aliens can be scanned from creatures: a band with that many is complete. */
	public static int scannable() {
		return (int)ALL.values().stream().filter(f -> !f.hidden()).count();
	}

	/** The alien whose DNA {@code creature} carries, or null. */
	public static @Nullable XenoForm scanOf(Entity creature) {
		// (0.18.100) the DNA table first: extra carriers by id, and those who carry none
		String listed = XenoDna.lookup(creature);
		if (listed != null) {
			return listed.isEmpty() ? null : get(listed);
		}
		for (XenoForm form : ALL.values()) {
			if (form.scans(creature)) {
				return form;
			}
		}
		return null;
	}
}
