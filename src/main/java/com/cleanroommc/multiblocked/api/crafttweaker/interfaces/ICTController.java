package com.cleanroommc.multiblocked.api.crafttweaker.interfaces;

import com.cleanroommc.multiblocked.api.capability.ICapabilityProxyHolder;
import com.cleanroommc.multiblocked.api.recipe.RecipeLogic;
import com.cleanroommc.multiblocked.api.tile.ControllerTileEntity;
import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenClass("mods.multiblocked.tile.Controller")
@ZenRegister
public interface ICTController extends ICTComponent, ICapabilityProxyHolder {
    
    ControllerTileEntity getInner();

    @ZenMethod
    default boolean checkPattern() { 
        return getInner().checkPattern();
    }

    @ZenGetter
    default RecipeLogic recipeLogic() {
        return getInner().getRecipeLogic();
    }

}
