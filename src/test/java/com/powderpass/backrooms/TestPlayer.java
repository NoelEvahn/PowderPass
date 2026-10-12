package com.powderpass.backrooms;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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

    @Override
    public float getAttackCooldown() {
        return 1f;
    }

    @Override
    public void resetCooldown() {
    }
}
