package me.steinborn.krypton.mod.shared;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class KryptonMixinPlugin implements IMixinConfigPlugin {
    private static final boolean E4MC_LOADED = FabricLoader.getInstance().isModLoaded("e4mc");
    private static final String E4MC_CONFLICTING_MIXIN =
            "me.steinborn.krypton.mixin.shared.network.pipeline.encryption.ServerLoginPacketListenerImplMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // e4mc and Krypton both redirect the same encryption setup path.
        // Disable Krypton's redirect when e4mc is installed to avoid a
        // @Redirect conflict while leaving the rest of Krypton enabled.
        if (E4MC_LOADED && E4MC_CONFLICTING_MIXIN.equals(mixinClassName)) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
