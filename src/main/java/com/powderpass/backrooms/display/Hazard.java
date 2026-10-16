package com.powderpass.backrooms.display;

/** Telegraphed periodic danger zone (steam vents, transformers). */
public record Hazard(String type, float radius, double damage, int period, int telegraph, float ox, float oy, float oz) {
}
