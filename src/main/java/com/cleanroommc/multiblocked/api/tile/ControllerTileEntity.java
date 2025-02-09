package com.cleanroommc.multiblocked.api.tile;

import com.cleanroommc.multiblocked.Multiblocked;
import com.cleanroommc.multiblocked.api.capability.ICapabilityProxyHolder;
import com.cleanroommc.multiblocked.api.capability.IO;
import com.cleanroommc.multiblocked.api.capability.MultiblockCapability;
import com.cleanroommc.multiblocked.api.capability.proxy.CapabilityProxy;
import com.cleanroommc.multiblocked.api.crafttweaker.interfaces.ICTController;
import com.cleanroommc.multiblocked.api.definition.ControllerDefinition;
import com.cleanroommc.multiblocked.api.gui.factory.TileEntityUIFactory;
import com.cleanroommc.multiblocked.api.gui.modular.ModularUI;
import com.cleanroommc.multiblocked.api.gui.texture.IGuiTexture;
import com.cleanroommc.multiblocked.api.gui.util.ModularUIBuilder;
import com.cleanroommc.multiblocked.api.gui.widget.imp.controller.IOPageWidget;
import com.cleanroommc.multiblocked.api.gui.widget.imp.controller.RecipePage;
import com.cleanroommc.multiblocked.api.gui.widget.imp.controller.structure.StructurePageWidget;
import com.cleanroommc.multiblocked.api.gui.widget.imp.tab.TabContainer;
import com.cleanroommc.multiblocked.api.pattern.BlockPattern;
import com.cleanroommc.multiblocked.api.pattern.MultiblockState;
import com.cleanroommc.multiblocked.api.recipe.RecipeLogic;
import com.cleanroommc.multiblocked.api.registry.MbdCapabilities;
import com.cleanroommc.multiblocked.api.tile.part.PartTileEntity;
import com.cleanroommc.multiblocked.client.renderer.IRenderer;
import com.cleanroommc.multiblocked.persistence.IAsyncThreadUpdate;
import com.cleanroommc.multiblocked.persistence.MultiblockWorldSavedData;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import crafttweaker.api.minecraft.CraftTweakerMC;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A TileEntity that defies all controller machines.
 *
 * Head of the multiblock.
 */
@Optional.Interface(modid = Multiblocked.MODID_CT, iface = "com.cleanroommc.multiblocked.api.crafttweaker.interfaces.ICTController")
public class ControllerTileEntity extends ComponentTileEntity<ControllerDefinition> implements ICapabilityProxyHolder, ICTController, IAsyncThreadUpdate {
    public MultiblockState state;
    public boolean asyncRecipeSearching = true;
    protected Table<IO, MultiblockCapability<?>, Long2ObjectOpenHashMap<CapabilityProxy<?>>> capabilities;
    private Map<Long, Map<MultiblockCapability<?>, Tuple<IO, EnumFacing>>> settings;
    protected LongOpenHashSet parts;
    protected RecipeLogic recipeLogic;

    public BlockPattern getPattern() {
        if (definition.dynamicPattern != null) {
            try {
                return definition.dynamicPattern.apply(this);
            } catch (Exception exception) {
                definition.dynamicPattern = null;
                Multiblocked.LOGGER.error("definition {} custom logic {} error", definition.location, "dynamicPattern", exception);
            }
        }
        return definition.basePattern;
    }

    @Override
    public ControllerTileEntity getInner() {
        return this;
    }

    public RecipeLogic getRecipeLogic() {
        return recipeLogic;
    }

    public boolean checkPattern() {
        if (state == null) return false;
        return getPattern().checkPatternAt(state, false);
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return definition.allowRotate && facing.getAxis() != EnumFacing.Axis.Y;
    }

    public boolean isFormed() {
        return state != null && state.isFormed();
    }

    @Override
    public void update() {
        super.update();
        if (isFormed()) {
            updateFormed();
        }
    }

    public void updateFormed() {
        if (recipeLogic != null) {
            recipeLogic.update();
        }
        if (definition.updateFormed != null) {
            try {
                definition.updateFormed.apply(this);
            } catch (Exception exception) {
                definition.updateFormed = null;
                Multiblocked.LOGGER.error("definition {} custom logic {} error", definition.location, "updateFormed", exception);
            }
        }
    }

    @Override
    public IRenderer updateCurrentRenderer() {
        if (definition.dynamicRenderer != null) {
            try {
                return definition.dynamicRenderer.apply(this);
            } catch (Exception exception) {
                definition.dynamicRenderer = null;
                Multiblocked.LOGGER.error("definition {} custom logic {} error", definition.location, "dynamicRenderer", exception);
            }
        }
        if (definition.workingRenderer != null && isFormed() && (status.equals("working") || status.equals("suspend"))) {
            return definition.workingRenderer;
        }
        return super.updateCurrentRenderer();
    }

    public Table<IO, MultiblockCapability<?>, Long2ObjectOpenHashMap<CapabilityProxy<?>>> getCapabilities() {
        return capabilities;
    }

    /**
     * Called when its formed, server side only.
     */
    public void onStructureFormed() {
        if (recipeLogic == null) {
            recipeLogic = new RecipeLogic(this);
        }
        if (status.equals("unformed")) {
            setStatus("idle");
        }
        // init capabilities
        Map<Long, EnumMap<IO, Set<MultiblockCapability<?>>>> capabilityMap = state.getMatchContext().get("capabilities");
        if (capabilityMap != null) {
            capabilities = Tables.newCustomTable(new EnumMap<>(IO.class), Object2ObjectOpenHashMap::new);
            for (Map.Entry<Long, EnumMap<IO, Set<MultiblockCapability<?>>>> entry : capabilityMap.entrySet()) {
                TileEntity tileEntity = world.getTileEntity(BlockPos.fromLong(entry.getKey()));
                if (tileEntity != null) {
                    if (settings != null) {
                        Map<MultiblockCapability<?>, Tuple<IO, EnumFacing>> caps = settings.get(entry.getKey());
                        if (caps != null) {
                            for (Map.Entry<MultiblockCapability<?>, Tuple<IO, EnumFacing>> ioEntry : caps.entrySet()) {
                                MultiblockCapability<?> capability = ioEntry.getKey();
                                Tuple<IO, EnumFacing> tuple = ioEntry.getValue();
                                if (tuple == null || capability == null) continue;
                                IO io = tuple.getFirst();
                                EnumFacing facing = tuple.getSecond();
                                if (capability.isBlockHasCapability(io, tileEntity)) {
                                    if (!capabilities.contains(io, capability)) {
                                        capabilities.put(io, capability, new Long2ObjectOpenHashMap<>());
                                    }
                                    CapabilityProxy<?> proxy = capability.createProxy(io, tileEntity);
                                    proxy.facing = facing;
                                    capabilities.get(io, capability).put(entry.getKey().longValue(), proxy);
                                }
                            }
                        }
                    } else {
                        entry.getValue().forEach((io,set)->{
                            for (MultiblockCapability<?> capability : set) {
                                if (capability.isBlockHasCapability(io, tileEntity)) {
                                    if (!capabilities.contains(io, capability)) {
                                        capabilities.put(io, capability, new Long2ObjectOpenHashMap<>());
                                    }
                                    CapabilityProxy<?> proxy = capability.createProxy(io, tileEntity);
                                    capabilities.get(io, capability).put(entry.getKey().longValue(), proxy);
                                }
                            }
                        });
                    }
                }
            }
        }

        settings = null;

        // init parts
        parts = state.getMatchContext().get("parts");
        if (parts != null) {
            for (Long pos : parts) {
                TileEntity tileEntity = world.getTileEntity(BlockPos.fromLong(pos));
                if (tileEntity instanceof PartTileEntity) {
                    ((PartTileEntity<?>) tileEntity).addedToController(this);
                }
            }
        }

        writeCustomData(-1, this::writeState);
        if (definition.structureFormed != null) {
            try {
                definition.structureFormed.apply(this);
            } catch (Exception exception) {
                definition.structureFormed = null;
                Multiblocked.LOGGER.error("definition {} custom logic {} error", definition.location, "structureFormed", exception);
            }
        }
    }
    
    public void onStructureInvalid() {
        recipeLogic = null;
        setStatus("unformed");
        // invalid parts
        if (parts != null) {
            for (Long pos : parts) {
                TileEntity tileEntity = world.getTileEntity(BlockPos.fromLong(pos));
                if (tileEntity instanceof PartTileEntity) {
                    ((PartTileEntity<?>) tileEntity).removedFromController(this);
                }
            }
            parts = null;
        }
        capabilities = null;

        writeCustomData(-1, this::writeState);
        if (definition.structureInvalid != null) {
            try {
                definition.structureInvalid.apply(this);
            } catch (Exception exception) {
                definition.structureInvalid = null;
                Multiblocked.LOGGER.error("definition {} custom logic {} error", definition.location, "structureInvalid", exception);
            }
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        if (dataId == -1) {
            readState(buf);
            scheduleChunkForRenderUpdate();
        } else {
            super.receiveCustomData(dataId, buf);
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        writeState(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        readState(buf);
        scheduleChunkForRenderUpdate();
    }

    protected void writeState(PacketBuffer buffer) {
        buffer.writeBoolean(isFormed());
        if (isFormed()) {
            LongSet disabled = state.getMatchContext().getOrDefault("renderMask", LongSets.EMPTY_SET);
            buffer.writeVarInt(disabled.size());
            for (long blockPos : disabled) {
                buffer.writeLong(blockPos);
            }
        }
    }

    protected void readState(PacketBuffer buffer) {
        if (buffer.readBoolean()) {
            state = new MultiblockState(world, pos);
            int size = buffer.readVarInt();
            if (size > 0) {
                ImmutableList.Builder<BlockPos> listBuilder = new ImmutableList.Builder<>();
                for (int i = size; i > 0; i--) {
                    listBuilder.add(BlockPos.fromLong(buffer.readLong()));
                }
                MultiblockWorldSavedData.addDisableModel(state.controllerPos, listBuilder.build());
            }
        } else {
            if (state != null) {
                MultiblockWorldSavedData.removeDisableModel(state.controllerPos);
            }
            state = null;
        }
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        try {
            super.readFromNBT(compound);
        } catch (Exception e) {
            if (definition == null) {
                MultiblockWorldSavedData mwsd = MultiblockWorldSavedData.getOrCreate(world);
                if (pos != null && mwsd.mapping.containsKey(pos)) {
                    mwsd.removeMapping(mwsd.mapping.get(pos));
                }
                return;
            }
        }
        if (compound.hasKey("ars")) {
            asyncRecipeSearching = compound.getBoolean("ars");
        }
        if (compound.hasKey("recipeLogic")) {
            recipeLogic = new RecipeLogic(this);
            recipeLogic.readFromNBT(compound.getCompoundTag("recipeLogic"));
            status = recipeLogic.getStatus().name;
        }
        if (compound.hasKey("capabilities")) {
            NBTTagList tagList = compound.getTagList("capabilities", Constants.NBT.TAG_COMPOUND);
            settings = new HashMap<>();
            for (NBTBase base : tagList) {
                NBTTagCompound tag = (NBTTagCompound) base;
                settings.computeIfAbsent(tag.getLong("pos"), l->new HashMap<>())
                        .put(MbdCapabilities.get(tag.getString("cap")), 
                                new Tuple<>(IO.VALUES[tag.getInteger("io")], EnumFacing.VALUES[tag.getInteger("facing")]));
            }
        }
        state = MultiblockWorldSavedData.getOrCreate(world).mapping.get(pos);
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (!asyncRecipeSearching) {
            compound.setBoolean("ars", false);
        }
        if (recipeLogic != null) compound.setTag("recipeLogic", recipeLogic.writeToNBT(new NBTTagCompound()));
        if (capabilities != null) {
            NBTTagList tagList = new NBTTagList();
            for (Table.Cell<IO, MultiblockCapability<?>, Long2ObjectOpenHashMap<CapabilityProxy<?>>> cell : capabilities.cellSet()) {
                IO io = cell.getRowKey();
                MultiblockCapability<?> cap = cell.getColumnKey();
                Long2ObjectOpenHashMap<CapabilityProxy<?>> value = cell.getValue();
                if (io != null && cap != null && value != null) {
                    for (Map.Entry<Long, CapabilityProxy<?>> entry : value.entrySet()) {
                        NBTTagCompound tag = new NBTTagCompound();
                        tag.setInteger("io", io.ordinal());
                        tag.setInteger("facing", entry.getValue().facing.getIndex());
                        tag.setString("cap", cap.name);
                        tag.setLong("pos", entry.getKey());
                        tagList.appendTag(tag);
                    }
                }
            }
            compound.setTag("capabilities", tagList);
        }
        return compound;
    }

    @Override
    public boolean onRightClick(EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (definition.onRightClick != null) {
            try {
                if (definition.onRightClick.apply(this, CraftTweakerMC.getIPlayer(player), CraftTweakerMC.getIFacing(facing), hitX, hitY, hitZ)) return true;
            } catch (Exception exception) {
                definition.onRightClick = null;
                Multiblocked.LOGGER.error("definition {} custom logic {} error", definition.location, "onRightClick", exception);
            }
        }
        if (!world.isRemote) {
            if (!isFormed() && definition.catalyst != null) {
                if (state == null) state = new MultiblockState(world, pos);
                ItemStack held = player.getHeldItem(hand);
                if (definition.catalyst.isEmpty() || held.isItemEqual(definition.catalyst)) {
                    if (checkPattern()) { // formed
                        player.swingArm(hand);
                        ITextComponent formedMsg = new TextComponentTranslation(getUnlocalizedName()).appendSibling(new TextComponentTranslation("multiblocked.multiblock.formed"));
                        player.sendStatusMessage(formedMsg, true);
                        if (!player.isCreative() && !definition.catalyst.isEmpty()) {
                            held.shrink(1);
                        }
                        MultiblockWorldSavedData.getOrCreate(world).addMapping(state);
                        if (!needAlwaysUpdate()) {
                            MultiblockWorldSavedData.getOrCreate(world).addLoading(this);
                        }
                        onStructureFormed();
                        return true;
                    }
                }
            }
            if (!player.isSneaking()) {
                if (!world.isRemote && player instanceof EntityPlayerMP) {
                    TileEntityUIFactory.INSTANCE.openUI(this, (EntityPlayerMP) player);
                }
            }
        }
        return true;
    }

    @Override
    public ModularUI createUI(EntityPlayer entityPlayer) {
        TabContainer tabContainer = new TabContainer(0, 0, 200, 232);
        if (!traits.isEmpty()) initTraitUI(tabContainer, entityPlayer);
        if (isFormed()) {
            new RecipePage(this, tabContainer);
            new IOPageWidget(this, tabContainer);
        } else {
            new StructurePageWidget(this.definition, tabContainer);
        }
        return new ModularUIBuilder(IGuiTexture.EMPTY, 196, 256)
                .widget(tabContainer)
                .build(this, entityPlayer);
    }

    @Override
    public void asyncThreadLogic(long periodID) {
        if (!isFormed() && getDefinition().catalyst == null && (getOffset() + periodID) % 4 == 0) {
            if (getPattern().checkPatternAt(new MultiblockState(world, pos), false)) {
                FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                    if (state == null) state = new MultiblockState(world, pos);
                    if (checkPattern()) { // formed
                        MultiblockWorldSavedData.getOrCreate(world).addMapping(state);
                        onStructureFormed();
                    }
                });
            }
        }
        try {
            if (hasProxies()) {
                // should i do lock for proxies?
                for (Long2ObjectOpenHashMap<CapabilityProxy<?>> map : getCapabilities().values()) {
                    if (map != null) {
                        for (CapabilityProxy<?> proxy : map.values()) {
                            if (proxy != null) {
                                proxy.updateChangedState(periodID);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Multiblocked.LOGGER.error("something run while checking proxy changes");
        }
    }
}
