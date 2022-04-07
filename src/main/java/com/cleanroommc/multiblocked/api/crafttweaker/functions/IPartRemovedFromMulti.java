package com.cleanroommc.multiblocked.api.crafttweaker.functions;

import com.cleanroommc.multiblocked.api.crafttweaker.interfaces.ICTController;
import com.cleanroommc.multiblocked.api.crafttweaker.interfaces.ICTPart;
import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;

@FunctionalInterface
@ZenClass("mods.multiblocked.functions.IPartRemovedFromMulti")
@ZenRegister
public interface IPartRemovedFromMulti {
    void apply(ICTPart partTileEntity, ICTController controllerTileEntity);
}
