package com.kgb_8375.sighted;

import com.kgb_8375.sighted.config.ClientConfiguration;
import com.kgb_8375.sighted.ext.ChunkLightProviderExt;
import com.kgb_8375.sighted.ext.ClientChunkProviderExt;
import com.kgb_8375.sighted.mixin.BiomeManagerAccessor;
import com.kgb_8375.sighted.mixin.LightEngineAccessor;
import com.mojang.datafixers.util.Pair;
import io.netty.util.concurrent.DefaultThreadFactory;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.Commands;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.concurrent.RecursiveEventLoop;
import net.minecraft.util.datafix.codec.DatapackCodec;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.World;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.storage.*;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class FakeChunkManager {

    private static final String FALLBACK_LEVEL_NAME = "sighted-fallback";
    private static final Minecraft client = Minecraft.getInstance();

    private final ClientWorld world;
    private final ClientChunkProvider clientChunkProvider;
    private final ClientChunkProviderExt clientChunkProviderExt;
    private final FakeChunkStorage storage;
    private final @Nullable FakeChunkStorage fallbackStorage;
    private int ticksSinceLastSave;

    private final Long2ObjectMap<Chunk> fakeChunks = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    private final VisibleChunksTracker chunkTracker = new VisibleChunksTracker();
    private final Long2LongMap toBeUnloaded = new Long2LongOpenHashMap();
    // Contains chunks in order to be unloaded. We keep the chunk and time so we can cross-reference it with
    // [toBeUnloaded] to see if the entry has since been removed / the time reset. This way we do not need
    // to remove entries from the middle of the queue.
    private final Deque<Pair<Long, Long>> unloadQueue = new ArrayDeque<>();

    // There unfortunately is only a synchronous api for loading chunks (even though that one just waits on a
    // CompletableFuture, annoying but oh well), so we call that blocking api from a separate thread pool.
    // The size of the pool must be sufficiently large such that there is always at least one query operation
    // running, as otherwise the storage io worker will start writing chunks which slows everything down to a crawl.
    private static final ExecutorService loadExecutor = Executors.newFixedThreadPool(8, new DefaultThreadFactory("sighted-loading", true));
    private final Long2ObjectMap<LoadingJob> loadingJobs = new Long2ObjectOpenHashMap<>();

    public FakeChunkManager(ClientWorld world, ClientChunkProvider clientChunkProvider) {
        this.world = world;
        this.clientChunkProvider = clientChunkProvider;
        this.clientChunkProviderExt = (ClientChunkProviderExt) clientChunkProvider;

        long seedHash = ((BiomeManagerAccessor) world.getBiomeManager()).getBiomeZoomSeed();
        RegistryKey<World> worldKey = world.dimension();
        ResourceLocation worldId = worldKey.getRegistryName();
        Path storagePath = client.gameDirectory
                .toPath()
                .resolve(".sighted")
                .resolve(getCurrentWorldOrServerName())
                .resolve(seedHash + "")
                .resolve(worldId.getNamespace())
                .resolve(worldId.getPath());

        storage = FakeChunkStorage.getFor(storagePath.toFile(), null);

        FakeChunkStorage fallbackStorage = null;
        SaveFormat levelStorage = client.getLevelSource();
        if (levelStorage.levelExists(FALLBACK_LEVEL_NAME)) {
            try (SaveFormat.LevelSave session = levelStorage.createAccess(FALLBACK_LEVEL_NAME)) {
                File worldDirectory = session.getDimensionPath(worldKey);
                File regionDirectory = new File(worldDirectory, "region");
                fallbackStorage = FakeChunkStorage.getFor(regionDirectory, getBiomeProvider(session));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        this.fallbackStorage = fallbackStorage;
    }

    public Chunk getChunk(int x, int z) {
        return fakeChunks.get(ChunkPos.asLong(x, z));
    }

    public FakeChunkStorage getStorage() {
        return storage;
    }

    public void update(BooleanSupplier shouldKeepTicking) {
        // Once a minute, force chunks to disk
        if (++ticksSinceLastSave > 20 * 60) {
            // flushWorker is blocking, so we run it on the io pool
            Util.ioPool().execute(storage::flushWorker);

            ticksSinceLastSave = 0;
        }

        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        ClientConfiguration config = Sighted.getConfig().getClientConfig();
        long time = Util.getMillis();

        int newCenterX = player.xChunk;
        int newCenterZ = player.zChunk;
        int newViewDistance = client.options.renderDistance;
        chunkTracker.update(newCenterX, newCenterZ, newViewDistance, chunkPos -> {
            // Chunk is now outside view distance, can be unloaded / cancelled
            cancelLoad(chunkPos);
            toBeUnloaded.put(chunkPos, time);
            unloadQueue.add(new Pair<>(chunkPos, time));
        }, chunkPos -> {
            // Chunk is now inside view distance, load it
            int x = ChunkPos.getX(chunkPos);
            int z = ChunkPos.getZ(chunkPos);

            // We want this chunk, so don't unload it if it's still here
            toBeUnloaded.remove(chunkPos);
            // Not removing it from [unloadQueue], we check [toBeUnloaded] when we poll it.

            // If there already is a chunk loaded, there's nothing to do
            if (clientChunkProvider.getChunk(x, z, ChunkStatus.FULL, false) != null) {
                return;
            }

            // All good, load it
            LoadingJob loadingJob = new LoadingJob(x, z);
            loadingJobs.put(chunkPos, loadingJob);
            loadExecutor.execute(loadingJob);
        });

        // Anything remaining in the set is no longer needed and can now be unloaded
        long unloadTime = time - config.unloadDelaySecs.get() * 100L;
        int countSinceLastThrottleCheck = 0;
        while (true) {
            Pair<Long, Long> next = unloadQueue.pollFirst();
            if (next == null) {
                break;
            }
            long chunkPos = next.getFirst();
            long queuedTime = next.getSecond();

            if (queuedTime > unloadTime) {
                // Unload is still being delayed, put the entry back into the queue
                // and be done for this update.
                unloadQueue.addFirst(next);
                break;
            }

            long actualQueuedTime = toBeUnloaded.remove(chunkPos);
            if (actualQueuedTime != queuedTime) {
                // The chunk has either been un-queued or re-queued.
                if (actualQueuedTime != 0) {
                    // If it was re-queued, put it back in the map.
                    toBeUnloaded.put(chunkPos, actualQueuedTime);
                }
                // Either way, skip it for now and go to the next entry.
                continue;
            }

            // This chunk is due for unloading
            unload(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos), false);

            if (countSinceLastThrottleCheck++ > 10) {
                countSinceLastThrottleCheck = 0;
                if (!shouldKeepTicking.getAsBoolean()) {
                    break;
                }
            }
        }

        ObjectIterator<LoadingJob> loadingJobsIter = this.loadingJobs.values().iterator();
        while (loadingJobsIter.hasNext()) {
            LoadingJob loadingJob = loadingJobsIter.next();

            //noinspection OptionalAssignedToNull
            if (loadingJob.result == null) {
                continue; // still loading
            }

            // Done loading
            loadingJobsIter.remove();

            client.getProfiler().push("loadedFakeChunk");
            loadingJob.complete();
            client.getProfiler().pop();

            if (!shouldKeepTicking.getAsBoolean()) {
                break;
            }
        }
    }

    public boolean shouldBeLoaded(int x, int z) {
        return chunkTracker.isInViewDistance(x, z);
    }

    private @Nullable Pair<CompoundNBT, FakeChunkStorage> loadTag(int x, int z) {
        ChunkPos chunkPos = new ChunkPos(x, z);
        CompoundNBT tag;
        try {
            tag = storage.loadTag(chunkPos);
            if (tag != null) {
                return new Pair<>(tag, storage);
            }
            if (fallbackStorage != null) {
                tag = fallbackStorage.loadTag(chunkPos);
                if (tag != null) {
                    return new Pair<>(tag, fallbackStorage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void load(int x, int z, CompoundNBT tag, FakeChunkStorage storage) {
        Supplier<Chunk> chunkSupplier = storage.deserialize(new ChunkPos(x, z), tag, world);
        if (chunkSupplier == null) {
            return;
        }
        load(x, z, chunkSupplier.get());
    }

    protected void load(int x, int z, Chunk chunk) {
        fakeChunks.put(ChunkPos.asLong(x, z), chunk);

        world.onChunkLoaded(x, z);

        for (int i = 0; i < 16; i++) {
            world.setSectionDirtyWithNeighbors(x, i, z);
        }

        clientChunkProviderExt.sighted_onFakeChunkAdded(x, z);
    }

    public boolean unload(int x, int z, boolean willBeReplaced) {
        long chunkPos = ChunkPos.asLong(x, z);
        cancelLoad(chunkPos);
        Chunk chunk = fakeChunks.remove(chunkPos);
        if (chunk != null) {
            LightEngineAccessor lightingEngine = (LightEngineAccessor) clientChunkProvider.getLightEngine();
            ChunkLightProviderExt blockLightEngine = (ChunkLightProviderExt) lightingEngine.getBlockEngine();
            ChunkLightProviderExt skyLightEngine = (ChunkLightProviderExt) lightingEngine.getSkyEngine();
            for (int y = 0; y < chunk.getSections().length; y++) {
                if (blockLightEngine != null) {
                    blockLightEngine.sighted_removeSectionData(SectionPos.asLong(x, y, z));
                }
                if (skyLightEngine != null) {
                    skyLightEngine.sighted_removeSectionData(SectionPos.asLong(x, y, z));
                }
            }

            clientChunkProviderExt.sighted_onFakeChunkRemoved(x, z);

            return true;
        }
        return false;
    }

    private void cancelLoad(long chunkPos) {
        LoadingJob loadingJob = loadingJobs.remove(chunkPos);
        if(loadingJob != null) {
            loadingJob.cancelled = true;
        }
    }

    private static String getCurrentWorldOrServerName() {
        IntegratedServer integratedServer = client.getSingleplayerServer();
        if (integratedServer != null) {
            return integratedServer.getWorldData().getLevelName();
        }

        ServerData serverInfo = client.getCurrentServer();
        if (serverInfo != null) {
            return serverInfo.ip.replace(':','_');
        }

        if (client.isConnectedToRealms()) {
            return "realms";
        }

        return "unknown";
    }

    private static BiomeProvider getBiomeProvider(SaveFormat.LevelSave session) throws ExecutionException, InterruptedException {
        // How difficult could this possibly be? Oh, right, datapacks are a thing
        // Mostly puzzled this together from how MinecraftClient starts the integrated server.
        try (ResourcePackList resourcePackList = new ResourcePackList(
                new ServerPackFinder(),
                new FolderPackFinder(session.getLevelPath(FolderName.DATAPACK_DIR).toFile(), IPackNameDecorator.WORLD)
        )) {
            DatapackCodec datapackCodec = MinecraftServer.configurePackRepository(resourcePackList, Minecraft.loadDataPacks(session), false);
            // We need our own executor, cause the MC one already has lots of packets in it
            Thread thread = Thread.currentThread();
            RecursiveEventLoop<Runnable> executor = new RecursiveEventLoop<Runnable>("") {
                @Override
                protected Runnable wrapRunnable(Runnable runnable) {
                    return runnable;
                }

                @Override
                protected boolean shouldRun(Runnable runnable) {
                    return true;
                }

                @Override
                protected Thread getRunningThread() {
                    return thread;
                }
            };
            CompletableFuture<DataPackRegistries> completableFuture = DataPackRegistries.loadResources(
                    resourcePackList.openAllSelected(),
                    Commands.EnvironmentType.INTEGRATED,
                    2,
                    Util.backgroundExecutor(),
                    executor
            );
            executor.execute(completableFuture::isDone);
            DataPackRegistries dataPackRegistries = completableFuture.get();
            IResourceManager resourceManager = dataPackRegistries.getResourceManager();
            DynamicRegistries.Impl registryTracker = DynamicRegistries.builtin();
            IServerConfiguration serverConfiguration = Minecraft.loadWorldData(session, registryTracker, resourceManager, datapackCodec);
            return serverConfiguration.worldGenSettings().overworld().getBiomeSource();
        }
    }

    public String getDebugString() {
        return "F: " + fakeChunks.size() + "L: " + loadingJobs.size() + "U: " + toBeUnloaded.size();
    }

    private class LoadingJob implements Runnable {
        private final int x;
        private final int z;
        private volatile boolean cancelled;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // Null while loading, empty() if no chunk was found
        private volatile Optional<Supplier<Chunk>> result;

        public LoadingJob(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public void run() {
            if (cancelled) {
                return;
            }
            result = Optional.ofNullable(loadTag(x, z))
                    .map(it -> it.getSecond().deserialize(new ChunkPos(x, z), it.getFirst(), world));
        }

        public void complete() {
            result.ifPresent(it -> load(x, z, it.get()));
        }
    }
}