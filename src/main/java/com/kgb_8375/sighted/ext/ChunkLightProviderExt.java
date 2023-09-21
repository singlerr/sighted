package com.kgb_8375.sighted.ext;

import net.minecraft.world.chunk.NibbleArray;

public interface ChunkLightProviderExt {
    void sighted_addSectionData(long pos, NibbleArray data);

    void sighted_removeSectionData(long pos);
}
