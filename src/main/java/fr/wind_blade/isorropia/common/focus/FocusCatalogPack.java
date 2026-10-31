package fr.wind_blade.isorropia.common.focus;

import fr.wind_blade.isorropia.Isorropia;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
//? if neoforge {
/*import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.neoforged.neoforge.event.AddPackFindersEvent;
*///?} else {
import net.minecraftforge.event.AddPackFindersEvent;
//?}

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Serves Isorropia's bundled focus profiles as a server data pack. */
public final class FocusCatalogPack implements PackResources {
    private static final String NAMESPACE = Isorropia.MODID;
    private static final String DIRECTORY = "thaumcraft/focus_catalog/";
    private static final String PACK_ID = "isorropia_focus_catalog";
    private static final List<String> FILES = List.of("focus_animation.json", "focus_containment.json",
            "focus_disintegration.json", "focus_illumination.json", "focus_liquefaction.json");
    private static final Map<String, byte[]> CACHE = new HashMap<>();

    //? if neoforge {
    /*private final PackLocationInfo location;

    public FocusCatalogPack(PackLocationInfo location) {
        this.location = location;
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }
    *///?} else {
    private final String packId;

    public FocusCatalogPack(String packId) {
        this.packId = packId;
    }

    @Override
    public String packId() {
        return this.packId;
    }
    //?}

    /** Adds the catalog pack to the server's data repository; the client only needs it for its integrated server. */
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        //? if neoforge {
        /*PackLocationInfo location = new PackLocationInfo(PACK_ID,
                Component.literal("Isorropia focus catalog"), PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return new FocusCatalogPack(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return new FocusCatalogPack(location);
            }
        };
        Pack pack = Pack.readMetaAndCreate(location, resources, PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        *///?} else {
        Pack.ResourcesSupplier resources = id -> new FocusCatalogPack(id);
        Pack.Info info = Pack.readPackInfo(PACK_ID, resources);
        if (info == null) {
            return;
        }
        Pack pack = Pack.create(PACK_ID, Component.literal("Isorropia focus catalog"), true, resources, info,
                PackType.SERVER_DATA, Pack.Position.TOP, true, PackSource.BUILT_IN);
        //?}
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        if (elements.length == 1 && "pack.mcmeta".equals(elements[0])) {
            return () -> new ByteArrayInputStream(packMeta());
        }
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        String file = fileFor(type, location);
        return file == null ? null : () -> new ByteArrayInputStream(bytes(file));
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.SERVER_DATA || !NAMESPACE.equals(namespace) || !covers(path)) {
            return;
        }
        for (String file : FILES) {
            //? if neoforge {
            /*ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, DIRECTORY + file);
            *///?} else {
            ResourceLocation id = new ResourceLocation(NAMESPACE, DIRECTORY + file);
            //?}
            output.accept(id, () -> new ByteArrayInputStream(bytes(file)));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of(NAMESPACE) : Set.of();
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer == PackMetadataSection.TYPE) {
            @SuppressWarnings("unchecked")
            T section = (T) new PackMetadataSection(Component.literal("Isorropia focus catalog"),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
            return section;
        }
        return null;
    }

    @Override
    public void close() {
    }

    @Nullable
    private static String fileFor(PackType type, ResourceLocation location) {
        if (type != PackType.SERVER_DATA || !NAMESPACE.equals(location.getNamespace())) {
            return null;
        }
        String path = location.getPath();
        if (!path.startsWith(DIRECTORY)) {
            return null;
        }
        String file = path.substring(DIRECTORY.length());
        return FILES.contains(file) ? file : null;
    }

    /** Whether a resource listing rooted at {@code path} can contain the catalog. */
    private static boolean covers(String path) {
        return path.isEmpty() || DIRECTORY.startsWith(path) || path.startsWith(DIRECTORY);
    }

    private static byte[] bytes(String file) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(file, FocusCatalogPack::load);
        }
    }

    private static byte[] load(String file) {
        try (InputStream stream = Isorropia.class.getResourceAsStream("/focus_catalog/" + file)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled focus profile /focus_catalog/" + file);
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static byte[] packMeta() {
        String meta = "{\"pack\":{\"description\":\"Isorropia focus catalog\",\"pack_format\":"
                + SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA) + "}}";
        return meta.getBytes(StandardCharsets.UTF_8);
    }
}
