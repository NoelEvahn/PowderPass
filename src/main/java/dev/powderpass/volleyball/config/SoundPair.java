package dev.powderpass.volleyball.config;

/** The vanilla sound everyone can hear and the custom sound for players with the resource pack. */
public record SoundPair(SoundSpec vanilla, SoundSpec pack) {

    public SoundSpec pick(boolean hasPack) {
        return hasPack && pack != null ? pack : vanilla;
    }
}
