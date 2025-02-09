package com.cleanroommc.multiblocked.api.pattern;

import com.cleanroommc.multiblocked.api.capability.IO;
import com.cleanroommc.multiblocked.api.pattern.predicates.SimplePredicate;
import com.cleanroommc.multiblocked.api.pattern.util.BlockInfo;
import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.OperatorType;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;
import stanhebben.zenscript.annotations.ZenOperator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ZenClass("mods.multiblocked.pattern.CTPredicate")
@ZenRegister
public class TraceabilityPredicate {

    public List<SimplePredicate> common = new ArrayList<>();
    public List<SimplePredicate> limited = new ArrayList<>();
    public boolean isCenter;

    public TraceabilityPredicate() {}

    public TraceabilityPredicate(TraceabilityPredicate predicate) {
        common.addAll(predicate.common);
        limited.addAll(predicate.limited);
        isCenter = predicate.isCenter;
    }

    public TraceabilityPredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo[]> candidates) {
        common.add(new SimplePredicate(predicate, candidates));
    }

    public TraceabilityPredicate(SimplePredicate simplePredicate) {
        if (simplePredicate.minCount != -1 || simplePredicate.maxCount != -1) {
            limited.add(simplePredicate);
        } else {
            common.add(simplePredicate);
        }
    }

    /**
     * Mark it as the controller of this multi. Normally you won't call it yourself. Use plz.
     */
    @ZenMethod
    public TraceabilityPredicate setCenter() {
        isCenter = true;
        return this;
    }

    @ZenMethod
    public TraceabilityPredicate sort() {
        limited.sort(Comparator.comparingInt(a -> a.minCount));
        return this;
    }

    /**
     * Add tooltips for candidates. They are shown in JEI Pages.
     */
    @ZenMethod
    public TraceabilityPredicate addTooltips(String... tips) {
        if (tips.length > 0) {
            List<String> tooltips = Arrays.stream(tips).collect(Collectors.toList());
            common.forEach(predicate -> {
                if (predicate.candidates == null) return;
                if (predicate.toolTips == null) {
                    predicate.toolTips = new ArrayList<>();
                }
                predicate.toolTips.addAll(tooltips);
            });
            limited.forEach(predicate -> {
                if (predicate.candidates == null) return;
                if (predicate.toolTips == null) {
                    predicate.toolTips = new ArrayList<>();
                }
                predicate.toolTips.addAll(tooltips);
            });
        }
        return this;
    }

    /**
     * Set the minimum number of candidate blocks.
     */
    @ZenMethod
    public TraceabilityPredicate setMinGlobalLimited(int min) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.minCount = min;
        }
        return this;
    }

    @ZenMethod
    public TraceabilityPredicate setMinGlobalLimited(int min, int previewCount) {
        return this.setMinGlobalLimited(min).setPreviewCount(previewCount);
    }

    /**
     * Set the maximum number of candidate blocks.
     */
    @ZenMethod
    public TraceabilityPredicate setMaxGlobalLimited(int max) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.maxCount = max;
        }
        return this;
    }

    @ZenMethod
    public TraceabilityPredicate setMaxGlobalLimited(int max, int previewCount) {
        return this.setMaxGlobalLimited(max).setPreviewCount(previewCount);
    }

    /**
     * Sets the Minimum and Maximum limit to the passed value
     * @param limit The Maximum and Minimum limit
     */
    @ZenMethod
    public TraceabilityPredicate setExactLimit(int limit) {
        return this.setMinGlobalLimited(limit).setMaxGlobalLimited(limit);
    }

    /**
     * Set the number of it appears in JEI pages. It only affects JEI preview. (The specific number)
     */
    @ZenMethod
    public TraceabilityPredicate setPreviewCount(int count) {
        common.forEach(predicate -> predicate.previewCount = count);
        limited.forEach(predicate -> predicate.previewCount = count);
        return this;
    }

    /**
     * Set renderMask.
     */
    @ZenMethod
    public TraceabilityPredicate disableRenderFormed() {
        common.forEach(predicate -> predicate.disableRenderFormed = true);
        limited.forEach(predicate -> predicate.disableRenderFormed = true);
        return this;
    }

    /**
     * Set io.
     */
    @ZenMethod
    public TraceabilityPredicate setIO(IO io) {
        common.forEach(predicate -> predicate.io = io);
        limited.forEach(predicate -> predicate.io = io);
        return this;
    }

    public boolean test(MultiblockState blockWorldState) {
        blockWorldState.io = IO.BOTH;
        boolean flag = false;
        for (SimplePredicate predicate : limited) {
            if (predicate.testLimited(blockWorldState)) {
                flag = true;
            }
        }
        return flag || common.stream().anyMatch(predicate->predicate.test(blockWorldState));
    }

    @ZenMethod
    @ZenOperator(OperatorType.OR)
    public TraceabilityPredicate or(TraceabilityPredicate other) {
        if (other != null) {
            TraceabilityPredicate newPredicate = new TraceabilityPredicate(this);
            newPredicate.common.addAll(other.common);
            newPredicate.limited.addAll(other.limited);
            return newPredicate;
        }
        return this;
    }

    @ZenMethod
    public boolean isAny() {
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.get(0) == SimplePredicate.ANY;
    }

    @ZenMethod
    public boolean isAir() {
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.get(0) == SimplePredicate.AIR;
    }

    @ZenMethod
    public boolean isSingle() {
        return !isAny() && !isAir() && this.common.size() + this.limited.size() == 1;
    }

    @ZenMethod
    public boolean hasAir() {
        return this.common.contains(SimplePredicate.AIR);
    }

}
