package com.cleanroommc.multiblocked.api.recipe;

import com.cleanroommc.multiblocked.api.capability.CapabilityProxy;
import com.cleanroommc.multiblocked.api.capability.ICapabilityProxyHolder;
import com.cleanroommc.multiblocked.api.capability.IO;
import com.cleanroommc.multiblocked.api.capability.MultiblockCapability;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import crafttweaker.annotations.ZenRegister;
import com.cleanroommc.multiblocked.Multiblocked;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.Tuple;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenProperty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ZenClass("mods.multiblocked.recipe.Recipe")
@ZenRegister
public class Recipe {
    @ZenProperty
    public final String uid;
    public final ImmutableMap<MultiblockCapability<?>, ImmutableList<Tuple<Object, Float>>> inputs;
    public final ImmutableMap<MultiblockCapability<?>, ImmutableList<Tuple<Object, Float>>> outputs;
    public final int duration;

    public Recipe(String uid,
                  ImmutableMap<MultiblockCapability<?>, ImmutableList<Tuple<Object, Float>>> inputs,
                  ImmutableMap<MultiblockCapability<?>, ImmutableList<Tuple<Object, Float>>> outputs,
                  int duration) {
        this.uid = uid;
        this.inputs = inputs;
        this.outputs = outputs;
        this.duration = duration;
    }

    /**
     * Does the recipe match the owned proxy.
     *
     * @param capabilityProxies proxies
     * @return result
     */
    public boolean match(ICapabilityProxyHolder holder) {
        if (!holder.hasProxies()) return false;
        if (!match(IO.IN, holder.getCapabilities())) return false;
        return match(IO.OUT, holder.getCapabilities());
    }

    private boolean match(IO io, Table<IO, MultiblockCapability<?>, Long2ObjectOpenHashMap<CapabilityProxy<?>>> capabilityProxies) {
        for (Map.Entry<MultiblockCapability<?>, ImmutableList<Tuple<Object, Float>>> entry : io == IO.IN ? inputs.entrySet() : outputs.entrySet()) {
            Set<CapabilityProxy<?>> used = new HashSet<>();
            List<?> content = entry.getValue().stream().map(Tuple::getFirst).collect(Collectors.toList());
            if (capabilityProxies.contains(io, entry.getKey())) {
                for (CapabilityProxy<?> proxy : capabilityProxies.get(io, entry.getKey()).values()) { // search same io type
                    if (used.contains(proxy)) continue;
                    used.add(proxy);
                    content = proxy.searchingRecipe(io, this, content);
                    if (content == null) break;
                }
            }
            if (content == null) continue;
            if (capabilityProxies.contains(IO.BOTH, entry.getKey())) {
                for (CapabilityProxy<?> proxy : capabilityProxies.get(IO.BOTH, entry.getKey()).values()) { // search both type
                    if (used.contains(proxy)) continue;
                    used.add(proxy);
                    content = proxy.searchingRecipe(io, this, content);
                    if (content == null) break;
                }
            }
            if (content != null) return false;
        }
        return true;
    }

    @SuppressWarnings("ALL")
    public void handleInput(ICapabilityProxyHolder holder) {
        if (!holder.hasProxies()) return;
        Table<IO, MultiblockCapability<?>, Long2ObjectOpenHashMap<CapabilityProxy<?>>> capabilityProxies = holder.getCapabilities();
        for (Map.Entry<MultiblockCapability<?>, ImmutableList<Tuple<Object, Float>>> entry : inputs.entrySet()) {
            Set<CapabilityProxy<?>> used = new HashSet<>();
            List content = new ArrayList<>();
            for (Tuple<Object, Float> tuple : entry.getValue()) {
                if (tuple.getSecond() == 1 || Multiblocked.RNG.nextFloat() < tuple.getSecond()) { // chance input
                    content.add(tuple.getFirst());
                }
            }
            if (content.isEmpty()) continue;
            if (capabilityProxies.contains(IO.IN, entry.getKey())) {
                for (CapabilityProxy<?> proxy : capabilityProxies.get(IO.IN, entry.getKey()).values()) { // search same io type
                    if (used.contains(proxy)) continue;
                    used.add(proxy);
                    content = proxy.handleRecipeInput(this, content);
                    if (content == null) break;
                }
            }
            if (content == null) continue;
            if (capabilityProxies.contains(IO.BOTH, entry.getKey())){
                for (CapabilityProxy<?> proxy : capabilityProxies.get(IO.BOTH, entry.getKey()).values()) { // search both type
                    if (used.contains(proxy)) continue;
                    used.add(proxy);
                    content = proxy.handleRecipeInput(this, content);
                    if (content == null) break;
                }
            }
        }
    }

    @SuppressWarnings("ALL")
    public void handleOutput(ICapabilityProxyHolder holder) {
        if (!holder.hasProxies()) return;
        Table<IO, MultiblockCapability<?>, Long2ObjectOpenHashMap<CapabilityProxy<?>>> capabilityProxies = holder.getCapabilities();
        for (Map.Entry<MultiblockCapability<?>, ImmutableList<Tuple<Object, Float>>> entry : outputs.entrySet()) {
            Set<CapabilityProxy<?>> used = new HashSet<>();
            List content = new ArrayList<>();
            for (Tuple<Object, Float> tuple : entry.getValue()) {
                if (tuple.getSecond() == 1 || Multiblocked.RNG.nextFloat() < tuple.getSecond()) { // chance output
                    content.add(tuple.getFirst());
                }
            }
            if (content.isEmpty()) continue;
            if (capabilityProxies.contains(IO.OUT, entry.getKey())) {
                for (CapabilityProxy<?> proxy : capabilityProxies.get(IO.OUT, entry.getKey()).values()) { // search same io type
                    if (used.contains(proxy)) continue;
                    used.add(proxy);
                    content = proxy.handleRecipeOutput(this, content);
                    if (content == null) break;
                }
            }
            if (content == null) continue;
            if (capabilityProxies.contains(IO.BOTH, entry.getKey())) {
                for (CapabilityProxy<?> proxy : capabilityProxies.get(IO.BOTH, entry.getKey()).values()) { // search both type
                    if (used.contains(proxy)) continue;
                    used.add(proxy);
                    content = proxy.handleRecipeOutput(this, content);
                    if (content == null) break;
                }
            }
        }
    }
}
