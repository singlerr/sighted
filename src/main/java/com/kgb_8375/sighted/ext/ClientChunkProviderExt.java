package com.kgb_8375.sighted.ext;

import com.kgb_8375.sighted.FakeChunkManager;
import net.minecraft.world.chunk.listener.IChunkStatusListener;

public interface ClientChunkProviderExt {
    FakeChunkManager sighted_getFakeChunkManager();
    void sighted_onFakeChunkAdded(int x, int z);
    void sighted_onFakeChunkRemoved(int x, int z);
}
