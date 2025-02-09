package com.cleanroommc.multiblocked.common.capability.trait;

import com.cleanroommc.multiblocked.api.capability.IO;
import com.cleanroommc.multiblocked.api.capability.trait.MultiCapabilityTrait;
import com.cleanroommc.multiblocked.api.gui.widget.WidgetGroup;
import com.cleanroommc.multiblocked.api.gui.widget.imp.SlotWidget;
import com.cleanroommc.multiblocked.api.tile.ComponentTileEntity;
import com.cleanroommc.multiblocked.common.capability.ItemMultiblockCapability;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemCapabilityTrait extends MultiCapabilityTrait {
    private ItemStackHandler handler;

    public ItemCapabilityTrait() {
        super(ItemMultiblockCapability.CAP);
    }

    @Override
    public void serialize(@Nullable JsonElement jsonElement) {
        super.serialize(jsonElement);
        if (jsonElement == null) {
            jsonElement = new JsonArray();
        }
        JsonArray jsonArray = jsonElement.getAsJsonArray();
        int size = jsonArray.size();
        handler = new ItemStackHandler(size);
    }

    @Override
    public void onDrops(NonNullList<ItemStack> drops, EntityPlayer player) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(handler.getStackInSlot(i));
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        handler.deserializeNBT(compound.getCompoundTag("_"));
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("_", handler.serializeNBT());
    }

    @Override
    public void createUI(ComponentTileEntity<?> component, WidgetGroup group, EntityPlayer player) {
        super.createUI(component, group, player);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                group.addWidget(new SlotWidget(new ProxyItemHandler(handler, guiIO, false), i, x[i], y[i], true, true));
            }
        }
    }

    @Override
    public boolean hasUpdate() {
        return ArrayUtils.contains(autoIO, true);
    }

    @Override
    public void update() {
        for (int i = 0; i < autoIO.length; i++) {
            if (autoIO[i]) {
                if (capabilityIO[i] == IO.IN) {
                    ItemStack already = this.handler.getStackInSlot(i);
                    int need = this.handler.getSlotLimit(i) - already.getCount();
                    if (need > 0) {
                        for (EnumFacing facing : getIOFacing()) {
                            TileEntity te = component.getWorld().getTileEntity(component.getPos().offset(facing));
                            if (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
                                IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
                                if (handler != null) {
                                    for (int j = 0; j < handler.getSlots(); j++) {
                                        ItemStack extracted = handler.extractItem(j, need, true);
                                        if (extracted.isEmpty()) continue;
                                        if (already.isEmpty() || extracted.isItemEqual(already)) {
                                            this.handler.insertItem(i, handler.extractItem(j, need, false).copy(), false);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (capabilityIO[i] == IO.OUT){
                    ItemStack already = this.handler.getStackInSlot(i);
                    if (!already.isEmpty()) {
                        for (EnumFacing facing : getIOFacing()) {
                            TileEntity te = component.getWorld().getTileEntity(component.getPos().offset(facing));
                            if (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
                                IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
                                if (handler != null) {
                                    for (int j = 0; j < handler.getSlots(); j++) {
                                        ItemStack left = handler.insertItem(j, already.copy(), false);
                                        if (left.getCount() == already.getCount()) continue;
                                        this.handler.extractItem(i, already.getCount() - left.getCount(), false);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        super.update();
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(new ProxyItemHandler(handler, capabilityIO, false)) : null;
    }

    @Nullable
    @Override
    public <T> T getInnerCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(new ProxyItemHandler(handler, capabilityIO, true)) : null;
    }

    private class ProxyItemHandler implements IItemHandler, IItemHandlerModifiable {
        public ItemStackHandler proxy;
        public IO[] ios;
        public boolean inner;

        public ProxyItemHandler(ItemStackHandler proxy, IO[] ios, boolean inner) {
            this.proxy = proxy;
            this.ios = ios;
            this.inner = inner;
        }

        @Override
        public int getSlots() {
            return proxy.getSlots();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return proxy.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            IO io = ios[slot];
            if (io == IO.BOTH || (inner ? io == IO.OUT : io == IO.IN)) {
                if (!simulate) markAsDirty();
                return proxy.insertItem(slot, stack, simulate);
            }
            return stack;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IO io = ios[slot];
            if (io == IO.BOTH || (inner ? io == IO.IN : io == IO.OUT)) {
                if (!simulate) markAsDirty();
                return proxy.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return proxy.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return proxy.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            proxy.setStackInSlot(slot, stack);
        }
    }

}
