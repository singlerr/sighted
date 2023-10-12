package com.kgb_8375.sighted.mixin;

import net.minecraft.util.datafix.DataFixer;
import net.minecraft.world.storage.SaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SaveHandler.class)
public interface SaveHandlerAccessor {

    @Accessor
    DataFixer getDataFixer();
}
