package me.steinborn.krypton.mixin.shared.debugaid;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.netty.util.ResourceLeakDetector;
import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SharedConstants.class)
public class ResourceLeakDetectorDisableConditionalMixin {
    @Unique
    @WrapWithCondition(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/netty/util/ResourceLeakDetector;setLevel(Lio/netty/util/ResourceLeakDetector$Level;)V"
            )
    )
    private static boolean krypton$onlyDisableLeakDetectorWhenPropertyUnset(ResourceLeakDetector.Level level) {
        // If io.netty.leakDetection.level is defined, override the game disabling it by default.
        return System.getProperty("io.netty.leakDetection.level") == null;
    }
}
