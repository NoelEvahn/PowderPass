package com.powderpass.backrooms;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** PlayerMock with the async teleport and attack-cooldown methods Paper provides. */
final class TestPlayer extends PlayerMock {
    TestPlayer(ServerMock server, String name) {
        super(server, name, UUID.nameUUIDFromBytes(name.getBytes()));
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location, PlayerTeleportEvent.TeleportCause cause, TeleportFlag... flags) {
        return CompletableFuture.completedFuture(teleport(location, cause));
    }

    /** Every namespaced sound played to this player: "key@x,y,z". */
    final List<String> customSounds = new ArrayList<>();
    final List<String> stopped = new ArrayList<>();
    int sendViewDistance = -1;
    int packRequests;
    org.bukkit.entity.Entity spectatorTarget;

    @Override
    public org.bukkit.entity.Entity getSpectatorTarget() {
        return spectatorTarget;
    }

    @Override
    public void setSpectatorTarget(org.bukkit.entity.Entity e) {
        spectatorTarget = e;
    }

    @Override
    public void playSound(Location location, String sound, SoundCategory category, float volume, float pitch) {
        customSounds.add(sound + "@" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
    }

    @Override
    public void stopSound(SoundStop stop) {
        stopped.add(String.valueOf(stop.sound()));
    }

    @Override
    public int getSendViewDistance() {
        return sendViewDistance < 0 ? getWorld().getSendViewDistance() : sendViewDistance;
    }

    @Override
    public void setSendViewDistance(int d) {
        sendViewDistance = d;
    }

    @Override
    public void sendResourcePacks(ResourcePackRequest request) {
        packRequests++;
    }

    @Override
    public float getAttackCooldown() {
        return 1f;
    }

    @Override
    public void resetCooldown() {
    }
}
