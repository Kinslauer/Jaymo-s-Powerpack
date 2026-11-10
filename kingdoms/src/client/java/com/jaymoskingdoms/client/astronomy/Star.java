package com.jaymoskingdoms.client.astronomy;

/**
 * One star from the HYG v4.1 catalog (The Astronomy Nexus, CC BY-SA-4.0): its
 * Hipparcos number, right ascension and declination in degrees, visual
 * magnitude, constellation and traditional name where the catalog records one.
 * The mod ships every catalog star down to magnitude 6.5.
 */
public record Star(int hip, float ra, float dec, float mag, String con, String name) {}
