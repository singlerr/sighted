package com.kgb_8375.sighted;

import com.kgb_8375.sighted.config.ClientConfiguration;
import com.kgb_8375.sighted.ext.ChunkLightProviderExt;
import com.kgb_8375.sighted.mixin.LightEngineAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.LongArrayNBT;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.storage.ChunkLoader;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.lighting.NibbleArrayRepeater;
import net.minecraft.world.lighting.WorldLightManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public class FakeChunkStorage extends ChunkLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<File, FakeChunkStorage> active = new HashMap<>();
    private static final NibbleArray COMPLETELY_DARK = new NibbleArray();
    private static final NibbleArray COMPLETELY_LIT = new NibbleArray();
    static {
        for (int x = 0; x < 16; x++) {
            for(int y = 0; y < 16; y++) {
                for(int z = 0; z < 16; z++) {
                    COMPLETELY_LIT.set(x, y, z, 15);
                }
            }
        }
    }

    public static FakeChunkStorage getFor(File file, BiomeProvider biomeProvider) {
        if(!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("Must be called from main thread.");
        }
        return active.computeIfAbsent(file, f -> new FakeChunkStorage(file, biomeProvider));
    }

    public static void closeAll() {
        for (FakeChunkStorage storage : active.values()) {
            try {
                storage.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close storage", e);
            }
        }
        active.clear();
    }

    private final BiomeProvider biomeProvider;

    private FakeChunkStorage(File file, BiomeProvider biomeProvider) {
        super(file, null, false);
        this.biomeProvider = biomeProvider;
    }

    public void save(ChunkPos pos, CompoundNBT chunk) {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        tag.put("Level", chunk);
        write(pos, tag);
    }

    public @Nullable CompoundNBT loadTag(ChunkPos pos) throws IOException {
        CompoundNBT tag = read(pos);
        if (tag == null) {
            return null;
        }
        return tag.getCompound("Level");
    }

    public CompoundNBT serialize(IChunk chunk, WorldLightManager lightManager) {
        ChunkPos chunkPos = chunk.getPos();
        CompoundNBT level = new CompoundNBT();
        level.putInt("xPos", chunkPos.x);
        level.putInt("zPos", chunkPos.z);

        ChunkSection[] chunkSections = chunk.getSections();
        ListNBT sectionsTag = new ListNBT();

        for (ChunkSection chunkSection : chunkSections) {
            if(chunkSection == null) {
                continue;
            }
            int y = chunkSection.bottomBlockY() >> 4;
            boolean empty = true;

            CompoundNBT sectionTag = new CompoundNBT();
            sectionTag.putByte("Y", (byte) y);

            if (chunkSection != Chunk.EMPTY_SECTION) {
                chunkSection.getStates().write(sectionTag, "Palette", "BlockStates");
                empty = false;
            }

            NibbleArray blockLight = lightManager.getLayerListener(LightType.BLOCK).getDataLayerData(SectionPos.of(chunkPos, y));
            if (blockLight != null && !blockLight.isEmpty()) {
                sectionTag.putByteArray("BlockLight", blockLight.getData());
                empty = false;
            }

            NibbleArray skyLight = lightManager.getLayerListener(LightType.SKY).getDataLayerData(SectionPos.of(chunkPos, y));
            if (skyLight != null && !skyLight.isEmpty()) {
                sectionTag.putByteArray("SkyLight", skyLight.getData());
                empty = false;
            }

            if(!empty) {
                sectionsTag.add(sectionTag);
            }
        }

        level.put("Sections", sectionsTag);

        BiomeContainer biomeContainer = chunk.getBiomes();
        if (biomeContainer != null) {
            level.putIntArray("Biomes", biomeContainer.writeBiomes());
        }

        ListNBT blockEntitiesTag = new ListNBT();
        for(BlockPos pos : chunk.getBlockEntitiesPos()) {
            CompoundNBT blockEntityTag = chunk.getBlockEntityNbt(pos);
            if(blockEntityTag != null) {
                blockEntitiesTag.add(blockEntityTag);
            }
        }
        level.put("TileEntities", blockEntitiesTag);

        CompoundNBT heightmapsTag = new CompoundNBT();
        for(Map.Entry<Heightmap.Type, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getStatus().heightmapsAfter().contains(entry.getKey())) {
                heightmapsTag.put(entry.getKey().name(), new LongArrayNBT(entry.getValue().getRawData()));
            }
        }
        level.put("Heightmaps", heightmapsTag);

        return level;
    }

    // Note: This method is called asynchronously, so any methods called must either be verified to be thread safe (and
    //       must be unlikely to loose that thread safety in the presence of third party mods) or must be delayed
    //       by moving them into the returned supplier which is executed on the main thread.
    //       For performance reasons though: The more stuff we can do async, the better.
    public @Nullable
    Supplier<Chunk> deserialize(ChunkPos pos, CompoundNBT level, World world) {
        ClientConfiguration config = Sighted.getConfig().getClientConfig();

        ChunkPos chunkPos = new ChunkPos(level.getInt("xPos"), level.getInt("zPos"));
        if (!Objects.equals(pos, chunkPos)) {
            LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", pos, pos, chunkPos);
        }

        BiomeContainer biomeContainer;
        if(level.contains("Biomes", 11)) {
            biomeContainer = new BiomeContainer(world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), level.getIntArray("Biomes"));
        } else if (biomeProvider != null) {
            biomeContainer = new BiomeContainer(world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), chunkPos, biomeProvider);
        } else {
            LOGGER.error("Chunk file at {} has neither Biomes key nor biomeSource.", pos);
            return null;
        }
        ListNBT sectionsTag = level.getList("Sections", 10);
        ChunkSection[] chunkSections = new ChunkSection[16];
        NibbleArray[] blockLight = new NibbleArray[chunkSections.length];
        NibbleArray[] skyLight = new NibbleArray[chunkSections.length];

        Arrays.fill(blockLight, COMPLETELY_DARK);

        for(int i = 0; i < sectionsTag.size(); i++) {
            CompoundNBT sectionTag = sectionsTag.getCompound(i);
            int y = sectionTag.getByte("Y");

            if (sectionTag.contains("Palette", 9) && sectionTag.contains("BlockStates", 12)) {
                ChunkSection chunkSection = new ChunkSection(y << 4);
                chunkSection.getStates().read(
                        sectionTag.getList("Palette", 10),
                        sectionTag.getLongArray("BlockStates"));
                chunkSection.recalcBlockCounts();
                if (!chunkSection.isEmpty()) {
                    chunkSections[y] = chunkSection;
                }
            }

            if (sectionTag.contains("BlockLight", 7)) {
                blockLight[y] = new NibbleArray(sectionTag.getByteArray("BlockLight"));
            }

            if (sectionTag.contains("SkyLight", 7)) {
                skyLight[y] = new NibbleArray(sectionTag.getByteArray("SkyLight"));
            }
        }

        // Not all light sections are stored. For block light we simply fall back to a completely dark section.
        // For sky light we need to compute the section based on those above it. We are going top to bottom section.

        // The nearest section data read from storage
        NibbleArray fullSectionAbove = null;
        // The nearest section data computed from the one above (based on its bottom-most layer).
        // May be re-used for multiple sections once computed.
        NibbleArray inferredSection = COMPLETELY_LIT;
        for (int y = skyLight.length - 1; y >= 0; y--) {
            NibbleArray section = skyLight[y];

            // If we found a section, invalidate our inferred section cache and store it for later
            if (section != null) {
                inferredSection = null;
                fullSectionAbove = section;
                continue;
            }

            // If we are missing a section, infer it from the previous full section (the result of that can be re-used)
            if (inferredSection == null) {
                assert fullSectionAbove != null; // we only clear the cache when we set this
                inferredSection = new NibbleArray((new NibbleArrayRepeater(fullSectionAbove, 0).getData()));
            }
            skyLight[y] = inferredSection;
        }

        Chunk chunk = new FakeChunk(world, pos, biomeContainer, chunkSections);

        CompoundNBT heightmapsTag = level.getCompound("Heightmaps");
        EnumSet<Heightmap.Type> missingHeightmapTypes = EnumSet.noneOf(Heightmap.Type.class);

        for (Heightmap.Type type : chunk.getStatus().heightmapsAfter()) {
            String key = type.getSerializedName();
            if (heightmapsTag.contains(key, 12)) {
                chunk.setHeightmap(type, heightmapsTag.getLongArray(key));
            } else {
                missingHeightmapTypes.add(type);
            }
        }

        Heightmap.primeHeightmaps(chunk, missingHeightmapTypes);

        if(!config.noBlockEntities.get()) {
            ListNBT blockEntitiesTag = level.getList("TileEntities", 10);
            for (int i = 0; i < blockEntitiesTag.size(); i++) {
                chunk.setBlockEntityNbt(blockEntitiesTag.getCompound(i));
            }
        }

        return () -> {
            boolean hasSkyLight = world.dimensionType().hasSkyLight();
            AbstractChunkProvider chunkManager = world.getChunkSource();
            LightEngineAccessor lightingProvider = (LightEngineAccessor) chunkManager.getLightEngine();
            ChunkLightProviderExt blockLightProvider = (ChunkLightProviderExt) lightingProvider.getBlockEngine();
            ChunkLightProviderExt skyLightProvider = (ChunkLightProviderExt) lightingProvider.getSkyEngine();

            for (int y = 0; y < chunkSections.length; y++) {
                if (blockLightProvider != null) {
                    blockLightProvider.sighted_addSectionData(SectionPos.of(pos, y).asLong(), blockLight[y]);
                }
                if (skyLightProvider != null && hasSkyLight) {
                    skyLightProvider.sighted_addSectionData(SectionPos.of(pos, y).asLong(), skyLight[y]);
                }
            }

            // MC lazily loads block entities when they are first accessed.
            // It does so in a thread-unsafe way though, so if they are first accessed from e.g. a render thread, this
            // will cause threading issues (afaict thread-unsafe access to a chunk's block entities is still a problem
            // even in vanilla, e.g. if a block entity is removed while it is accessed, but apparently no one at Mojang
            // has run into that so far). To work around this, we force all block entities to be initialized
            // immediately, before any other code gets access to the chunk.
            for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
                chunk.getBlockEntity(blockPos);
            }

            return chunk;
        };
    }
}
