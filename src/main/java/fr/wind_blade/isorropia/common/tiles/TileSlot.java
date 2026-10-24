package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisConsumptionContext;
import dev.tc4port.thaumcraft.api.aspect.VisCost;
import dev.tc4port.thaumcraft.registry.TCBlocks;
import dev.tc4port.thaumcraft.registry.TCSounds;
import fr.wind_blade.isorropia.common.blocks.BlocksIS;
import fr.wind_blade.isorropia.common.world.PocketGateways;
import fr.wind_blade.isorropia.common.world.PocketPlane;
import fr.wind_blade.isorropia.common.world.PocketPlaneStore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Thaumic Horizons' keystone receptacle, {@code TileSlot}. A blank planar keystone slotted into it ties the receptacle
 * to one pocket plane; charging it with a wand then cuts a gateway: the receptacle turns the wall the player built
 * into the ornate frame, opens the three-by-three surface in the middle, and asks the plane for one of its four free
 * sides so the two ends are a pair.
 *
 * <p>TH checked the wall against its own metal-device and cosmetic-solid blocks. Those two legacy metadata blocks have
 * no single successor here, so the ring is validated against the closest surviving pair: thaumium block for the
 * corners and arcane stone for the edges, which is what the two looked like.</p>
 */
public class TileSlot extends BlockEntity {
    /** TH charged two hundred and fifty of each primal to cut a gateway. */
    private static final Map<VisChannel, Integer> GATEWAY_COST = Map.of(
            VisChannel.AER, 250, VisChannel.TERRA, 250, VisChannel.IGNIS, 250,
            VisChannel.AQUA, 250, VisChannel.ORDO, 250, VisChannel.PERDITIO, 250);

    private boolean hasKeystone;
    private boolean portalOpen;
    private int planeId = -1;
    private int planeColour = 0xFFFFFF;
    private int which;
    private boolean xAligned;

    public TileSlot(BlockPos pos, BlockState state) {
        super(TilesIS.GATEWAY_SLOT.get(), pos, state);
    }

    public boolean hasKeystone() {
        return this.hasKeystone;
    }

    public boolean portalOpen() {
        return this.portalOpen;
    }

    public int planeId() {
        return this.planeId;
    }

    /** The bound plane's colour, which is what the inserted keystone is tinted with. */
    public int planeColour() {
        return this.planeColour;
    }

    public int which() {
        return this.which;
    }

    public void insertKeystone(int planeId, int colour) {
        this.hasKeystone = true;
        this.planeId = planeId;
        this.planeColour = colour;
        sync();
    }

    /** Takes the keystone back out and reports the plane it was bound to, as TH's did. */
    public int removeKeystone() {
        this.hasKeystone = false;
        sync();
        return this.planeId;
    }

    /**
     * Cuts the gateway, TH's {@code makePortal}: validate the wall, spend the vis, convert the wall into the frame and
     * ask the plane for a free side.
     */
    public void makePortal(ItemStack wand, Player player) {
        if (this.level == null || this.level.isClientSide || !this.hasKeystone || this.planeId < 0) {
            return;
        }
        if (!(this.level instanceof ServerLevel server) || !(player instanceof ServerPlayer)) {
            return;
        }
        PocketPlaneStore store = PocketPlaneStore.get(player.getServer());
        int which = PocketGateways.firstAvailablePortal(store, this.planeId);
        if (which == 0) {
            return;
        }
        VisCost cost = VisCost.ofWholeVis(GATEWAY_COST);
        // TH asked the wand whether it could pay before validating the wall, and only charged once the portal was cut.
        // A successful simulate reports affordable, not consumed.
        if (!ThaumcraftApiHelper.consumeVisFromWand(wand, player, cost, VisAction.SIMULATE,
                VisConsumptionContext.CRAFTING).affordable()) {
            return;
        }
        boolean alignedX;
        if (checkPortalX()) {
            alignedX = true;
        } else if (checkPortalZ()) {
            alignedX = false;
        } else {
            return;
        }
        this.xAligned = alignedX;
        int variant = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int across = -2; across <= 2; across++) {
            for (int down = 0; down >= -4; down--) {
                if (across == 0 && down == 0) {
                    continue;
                }
                if (alignedX) {
                    cursor.set(this.worldPosition.getX() + across, this.worldPosition.getY() + down,
                            this.worldPosition.getZ());
                } else {
                    cursor.set(this.worldPosition.getX(), this.worldPosition.getY() + down,
                            this.worldPosition.getZ() + across);
                }
                if (!this.level.getBlockState(cursor).isAir()) {
                    this.level.setBlock(cursor, BlocksIS.GATEWAY.get().defaultBlockState()
                            .setValue(fr.wind_blade.isorropia.common.blocks.BlockGatewayPortal.VARIANT,
                                    Math.min(14, variant))
                            .setValue(fr.wind_blade.isorropia.common.blocks.BlockGatewayPortal.ALIGNED, alignedX),
                            Block.UPDATE_ALL);
                    variant++;
                } else {
                    this.level.setBlock(cursor, BlocksIS.PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        this.which = which;
        this.portalOpen = true;
        ServerLevel pocket = PocketPlane.level(server.getServer());
        if (pocket != null) {
            // The visitor lands in the middle of the plane-side opening, which is two below the receptacle.
            PocketGateways.makePortal(pocket, store, this.planeId, which, this.worldPosition.getX(),
                    this.worldPosition.getY() - 2, this.worldPosition.getZ(), server.dimension());
        }
        ThaumcraftApiHelper.consumeVisFromWand(wand, player, cost, VisAction.EXECUTE, VisConsumptionContext.CRAFTING);
        this.level.playSound(null, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5, TCSounds.WAND.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        sync();
    }

    /** Closes the gateway and puts the wall back, TH's {@code destroyPortal}. */
    public void destroyPortal() {
        if (this.level == null || this.level.isClientSide || !this.portalOpen) {
            return;
        }
        Block corner = TCBlocks.byId("thaumium_block").get();
        Block edge = TCBlocks.byId("arcane_stone").get();
        for (int across = -2; across <= 2; across++) {
            for (int down = 0; down >= -4; down--) {
                if (across == 0 && down == 0) {
                    continue;
                }
                BlockPos cell = this.xAligned
                        ? this.worldPosition.offset(across, down, 0)
                        : this.worldPosition.offset(0, down, across);
                if (isRingCell(across, down)) {
                    this.level.setBlock(cell, (isCornerCell(across, down) ? corner : edge).defaultBlockState(),
                            Block.UPDATE_ALL);
                } else {
                    this.level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL);
                }
            }
        }
        if (this.level instanceof ServerLevel server) {
            ServerLevel pocket = PocketPlane.level(server.getServer());
            if (pocket != null && this.planeId >= 0) {
                PocketGateways.destroyPortal(pocket, PocketPlaneStore.get(server.getServer()), this.planeId, this.which);
            }
        }
        this.level.playSound(null, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5, TCSounds.WAND_FAIL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        this.portalOpen = false;
        sync();
    }

    /** The five-by-five ring's cells: the border, minus the receptacle's own cell. */
    private static boolean isRingCell(int across, int down) {
        if (across == 0 && down == 0) {
            return false;
        }
        return down == 0 || down == -4 || across == -2 || across == 2;
    }

    private static boolean isCornerCell(int across, int down) {
        return (across == -2 || across == 2) && (down == 0 || down == -4);
    }

    /** TH's {@code checkPortalX}: a clear three-by-three in the X-aligned plane, ringed by the right wall. */
    private boolean checkPortalX() {
        for (int across = -1; across <= 1; across++) {
            for (int down = -1; down >= -3; down--) {
                if (!this.level.getBlockState(this.worldPosition.offset(across, down, 0)).isAir()) {
                    return false;
                }
            }
        }
        return checkRing(true);
    }

    private boolean checkPortalZ() {
        for (int across = -1; across <= 1; across++) {
            for (int down = -1; down >= -3; down--) {
                if (!this.level.getBlockState(this.worldPosition.offset(0, down, across)).isAir()) {
                    return false;
                }
            }
        }
        return checkRing(false);
    }

    /** TH's {@code checkBlocksX}/{@code checkBlocksZ}: thaumium at the four corners, arcane stone along the ring. */
    private boolean checkRing(boolean alignedX) {
        Block corner = TCBlocks.byId("thaumium_block").get();
        Block edge = TCBlocks.byId("arcane_stone").get();
        for (int across = -2; across <= 2; across++) {
            for (int down = 0; down >= -4; down--) {
                if (!isRingCell(across, down)) {
                    continue;
                }
                BlockPos cell = alignedX ? this.worldPosition.offset(across, down, 0)
                        : this.worldPosition.offset(0, down, across);
                Block expected = isCornerCell(across, down) ? corner : edge;
                if (!this.level.getBlockState(cell).is(expected)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Pushes the receptacle's state to nearby clients, so the keystone and its colour show up at once. */
    private void sync() {
        setChanged();
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        BlockState updated = getBlockState().setValue(
                fr.wind_blade.isorropia.common.blocks.BlockSlot.FILLED, this.hasKeystone);
        this.level.setBlock(this.worldPosition, updated, Block.UPDATE_CLIENTS);
        // The state change above carries the tag only when FILLED actually flips, so send it regardless.
        TileSync.toWatchers(this);
    }

    //? if forge {
    @Override
    protected void saveAdditional(CompoundTag tag) {
    //?} else {
    /*@Override
        protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        //? if forge {
        super.saveAdditional(tag);        //?} else {
        /*        super.saveAdditional(tag, registries);
        *///?}
        tag.putBoolean("hasKeystone", this.hasKeystone);
        tag.putBoolean("portalOpen", this.portalOpen);
        tag.putInt("plane", this.planeId);
        tag.putInt("planeColour", this.planeColour);
        tag.putInt("which", this.which);
        tag.putBoolean("aligned", this.xAligned);
    }

    //? if forge {
    @Override
    public void load(CompoundTag tag) {
    //?} else {
    /*@Override
        protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        //? if forge {
        super.load(tag);        //?} else {
        /*        super.loadAdditional(tag, registries);
        *///?}
        this.hasKeystone = tag.getBoolean("hasKeystone");
        this.portalOpen = tag.getBoolean("portalOpen");
        this.planeId = tag.getInt("plane");
        this.planeColour = tag.contains("planeColour") ? tag.getInt("planeColour") : 0xFFFFFF;
        this.which = tag.getInt("which");
        this.xAligned = tag.getBoolean("aligned");
    }

    //? if forge {
    @Override
    public CompoundTag getUpdateTag() {
    //?} else {
    /*@Override
        public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        CompoundTag tag = new CompoundTag();
        //? if forge {
        saveAdditional(tag);
        //?} else {
        /*saveAdditional(tag, registries);
        *///?}
        return tag;
    }

    //? if forge {
    @Override
    public void handleUpdateTag(CompoundTag tag) {
    //?} else {
    /*@Override
        public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        //? if forge {
        load(tag);
        //?} else {
        /*loadAdditional(tag, registries);
        *///?}
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
