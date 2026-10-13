package com.powderpass.backrooms;

import com.destroystokyo.paper.SkinParts;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.MainHand;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.LivingEntityMock;

import java.util.UUID;

/** Test-only stand-in for Paper's Mannequin (MockBukkit has none). Records what the plugin does to it. */
final class MannequinMock extends LivingEntityMock implements Mannequin {
    ResolvableProfile profile;
    Component description = Component.text("NPC");
    MainHand hand = MainHand.RIGHT;
    boolean immovable;
    Pose pose = Pose.STANDING;
    int swings;

    MannequinMock(ServerMock server) {
        super(server, UUID.randomUUID());
    }

    @Override
    public EntityType getType() {
        return EntityType.MANNEQUIN;
    }

    @Override
    public ResolvableProfile getProfile() {
        return profile;
    }

    @Override
    public void setProfile(ResolvableProfile p) {
        profile = p;
    }

    @Override
    public SkinParts.Mutable getSkinParts() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSkinParts(SkinParts parts) {
    }

    @Override
    public boolean isImmovable() {
        return immovable;
    }

    @Override
    public void setImmovable(boolean b) {
        immovable = b;
    }

    @Override
    public Component getDescription() {
        return description;
    }

    @Override
    public void setDescription(Component c) {
        description = c;
    }

    @Override
    public MainHand getMainHand() {
        return hand;
    }

    @Override
    public void setMainHand(MainHand h) {
        hand = h;
    }

    @Override
    public void setPose(Pose p, boolean fixed) {
        pose = p;
    }

    @Override
    public Pose getPose() {
        return pose;
    }

    @Override
    public void swingMainHand() {
        swings++;
    }
}
