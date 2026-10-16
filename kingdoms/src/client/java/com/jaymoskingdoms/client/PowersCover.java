package com.jaymoskingdoms.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * (0.7.81) What Jaymo's Powers covers on a player just now (its {@code BodyCover}: a costume, the symbiote's suit), read
 * by reflection when Powers is loaded, so a heritage's look doesn't poke through or flicker against it: under a mask or
 * the suit no glowing eyes, fangs or face marks; under a hood or helmet no antennae or head fins; under the suit or a
 * costume no arm fins. Without Powers nothing is ever covered.
 */
final class PowersCover {
	private static final MethodHandle HEAD, FACE, BODY;

	static {
		MethodHandle head = null, face = null, body = null;
		if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			try {
				Class<?> cover = Class.forName("com.jaymospowers.client.BodyCover");
				MethodType type = MethodType.methodType(boolean.class, Object.class);
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				head = lookup.findStatic(cover, "head", type);
				face = lookup.findStatic(cover, "face", type);
				body = lookup.findStatic(cover, "body", type);
			} catch (ReflectiveOperationException | LinkageError e) {
				head = face = body = null;
			}
		}
		HEAD = head;
		FACE = face;
		BODY = body;
	}

	private PowersCover() {
	}

	/** Whether the head and hair are covered (a mask over the whole head, a hood, the symbiote). */
	static boolean head(Object state) {
		return ask(HEAD, state);
	}

	/** Whether the face is covered (a mask that hides who you are, the symbiote). */
	static boolean face(Object state) {
		return ask(FACE, state);
	}

	/** Whether the body and limbs are covered (a costume, the symbiote). */
	static boolean body(Object state) {
		return ask(BODY, state);
	}

	private static boolean ask(MethodHandle handle, Object state) {
		if (handle == null) {
			return false;
		}
		try {
			return (boolean)handle.invokeExact(state);
		} catch (Throwable e) {
			return false;
		}
	}
}
