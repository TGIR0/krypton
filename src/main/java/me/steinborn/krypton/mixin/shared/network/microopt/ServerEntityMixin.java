package me.steinborn.krypton.mixin.shared.network.microopt;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {
    @Unique
    @ModifyExpressionValue(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collections;emptyList()Ljava/util/List;"
            )
    )
    private List<Entity> krypton$useGuavaImmutableListForInitialPassengers(List<Entity> original) {
        return ImmutableList.of();
    }
}
