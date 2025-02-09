package com.cleanroommc.multiblocked.api.pattern.predicates;

import com.cleanroommc.multiblocked.Multiblocked;
import com.cleanroommc.multiblocked.api.capability.IO;
import com.cleanroommc.multiblocked.api.gui.texture.ColorBorderTexture;
import com.cleanroommc.multiblocked.api.gui.texture.ColorRectTexture;
import com.cleanroommc.multiblocked.api.gui.texture.ResourceBorderTexture;
import com.cleanroommc.multiblocked.api.gui.texture.ResourceTexture;
import com.cleanroommc.multiblocked.api.gui.texture.TextTexture;
import com.cleanroommc.multiblocked.api.gui.widget.WidgetGroup;
import com.cleanroommc.multiblocked.api.gui.widget.imp.ImageWidget;
import com.cleanroommc.multiblocked.api.gui.widget.imp.LabelWidget;
import com.cleanroommc.multiblocked.api.gui.widget.imp.SelectorWidget;
import com.cleanroommc.multiblocked.api.gui.widget.imp.SwitchWidget;
import com.cleanroommc.multiblocked.api.gui.widget.imp.TextFieldWidget;
import com.cleanroommc.multiblocked.api.pattern.MultiblockState;
import com.cleanroommc.multiblocked.api.pattern.TraceabilityPredicate;
import com.cleanroommc.multiblocked.api.pattern.error.PatternStringError;
import com.cleanroommc.multiblocked.api.pattern.error.SinglePredicateError;
import com.cleanroommc.multiblocked.api.pattern.util.BlockInfo;
import com.cleanroommc.multiblocked.util.LocalizationUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SimplePredicate {
    public static SimplePredicate ANY = new SimplePredicate("any", x -> true, null);
    public static SimplePredicate AIR = new SimplePredicate("air", blockWorldState -> blockWorldState.getBlockState().getBlock().isAir(blockWorldState.getBlockState(), blockWorldState.getWorld(), blockWorldState.getPos()), null);
    
    public Supplier<BlockInfo[]> candidates;
    public Predicate<MultiblockState> predicate;

    public List<String> toolTips;

    public int minCount = -1;
    public int maxCount = -1;
    public int previewCount = -1;
    public boolean disableRenderFormed = false;
    public IO io = IO.BOTH;
    public String customTips;
    public String nbtParser;
    public boolean isCTParser;
    
    public final String type;

    public SimplePredicate() {
        this("unknown");
    }
    
    public SimplePredicate(String type) {
        this.type = type;
    }

    public SimplePredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo[]> candidates) {
        this();
        this.predicate = predicate;
        this.candidates = candidates;
    }

    public SimplePredicate(String type, Predicate<MultiblockState> predicate, Supplier<BlockInfo[]> candidates) {
        this(type);
        this.predicate = predicate;
        this.candidates = candidates;
    }

    public SimplePredicate buildPredicate() {
        return this;
    }

    @SideOnly(Side.CLIENT)
    public List<String> getToolTips(TraceabilityPredicate predicates) {
        List<String> result = new ArrayList<>();
        if (toolTips != null) {
            toolTips.forEach(tip->result.add(I18n.format(tip)));
        }
        if (customTips != null) {
            result.addAll(Arrays.stream(I18n.format(customTips).split("\n")).collect(Collectors.toList()));
        }
        if (minCount == maxCount && maxCount != -1) {
            result.add(I18n.format("multiblocked.pattern.limited_exact",
                    minCount));
        } else if (minCount != maxCount && minCount != -1 && maxCount != -1) {
            result.add(I18n.format("multiblocked.pattern.limited_within",
                    minCount, maxCount));
        } else {
            if (minCount != -1) {
                result.add(I18n.format("multiblocked.pattern.error.limited.1",
                        minCount));
            }
            if (maxCount != -1) {
                result.add(I18n.format("multiblocked.pattern.error.limited.0",
                        maxCount));
            }
        }
        if (predicates == null) return result;
        if (predicates.isSingle()) {
            result.add(I18n.format("multiblocked.pattern.single"));
        }
        if (predicates.hasAir()) {
            result.add(I18n.format("multiblocked.pattern.replaceable_air"));
        }
        return result;
    }

    public boolean test(MultiblockState blockWorldState) {
        if (predicate.test(blockWorldState)) {
            return checkInnerConditions(blockWorldState);
        }
        return false;
    }

    public boolean testLimited(MultiblockState blockWorldState) {
        if (testGlobal(blockWorldState)) {
            return checkInnerConditions(blockWorldState);
        }
        return false;
    }

    private boolean checkInnerConditions(MultiblockState blockWorldState) {
        if (disableRenderFormed) {
            blockWorldState.getMatchContext().getOrCreate("renderMask", LongOpenHashSet::new).add(blockWorldState.getPos().toLong());
        }
        if (io != IO.BOTH) {
            if (blockWorldState.io == IO.BOTH) {
                blockWorldState.io = io;
            } else if (blockWorldState.io != io) {
                blockWorldState.io = null;
            }
        }
        if (nbtParser != null && !blockWorldState.world.isRemote) {
            TileEntity te = blockWorldState.getTileEntity();
            if (te != null) {
                if (isCTParser && Loader.isModLoaded(Multiblocked.MODID_CT)) {
                    // TODO
                } else {
                    NBTTagCompound nbt = te.serializeNBT();
                    if (Pattern.compile(nbtParser).matcher(nbt.toString()).find()) {
                        return true;
                    }
                }
            }
            blockWorldState.setError(new PatternStringError("The NBT fails to match"));
            return false;
        }
        return true;
    }

    public boolean testGlobal(MultiblockState blockWorldState) {
        if (minCount == -1 && maxCount == -1) return true;
        Integer count = blockWorldState.globalCount.get(this);
        boolean base = predicate.test(blockWorldState);
        count = (count == null ? 0 : count) + (base ? 1 : 0);
        blockWorldState.globalCount.put(this, count);
        if (maxCount == -1 || count <= maxCount) return base;
        blockWorldState.setError(new SinglePredicateError(this, 0));
        return false;
    }

    public List<ItemStack> getCandidates() {
        return candidates == null ? Collections.emptyList() : Arrays.stream(this.candidates.get()).filter(info -> info.getBlockState().getBlock() != Blocks.AIR).map(BlockInfo::getItemStackForm).collect(Collectors.toList());
    }

    public List<WidgetGroup> getConfigWidget(List<WidgetGroup> groups) {
        WidgetGroup group = new WidgetGroup(0, 0, 300, 90);
        groups.add(group);
        group.setClientSideWidget();
        group.addWidget(new LabelWidget(0, 0, () -> LocalizationUtils.format("multiblocked.gui.label.type") + " " + type).setTextColor(-1).setDrop(true));
        TextFieldWidget min, max, preview, nbt, tooltips;

        group.addWidget(min = new TextFieldWidget(55, 15, 30, 15, true, () -> minCount + "", s -> {
            minCount = Integer.parseInt(s);
            if (minCount > maxCount) {
                maxCount = minCount;
            }
        }).setNumbersOnly(0, Integer.MAX_VALUE));
        min.setHoverTooltip("multiblocked.gui.tips.min").setVisible(minCount != -1);
        group.addWidget(new SwitchWidget(0, 15, 50, 15, (cd, r)->{
            min.setVisible(r);
            minCount = r ? 0 : -1;
        }).setPressed(minCount != -1).setHoverBorderTexture(1, -1).setBaseTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("min (N)", -1).setDropShadow(true)).setPressedTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("min (Y)", -1).setDropShadow(true))
                .setHoverTooltip("multiblocked.gui.predicate.min"));

        group.addWidget(max = new TextFieldWidget(55, 33, 30, 15, true, () -> maxCount + "", s -> {
            maxCount = Integer.parseInt(s);
            if (minCount > maxCount) {
                minCount = maxCount;
            }
        }).setNumbersOnly(0, Integer.MAX_VALUE));
        max.setHoverTooltip("multiblocked.gui.tips.max").setVisible(maxCount != -1);
        group.addWidget(new SwitchWidget(0, 33, 50, 15, (cd, r)->{
            max.setVisible(r);
            maxCount = r ? 0 : -1;
        }).setPressed(maxCount != -1).setHoverBorderTexture(1, -1).setBaseTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("max (N)", -1).setDropShadow(true)).setPressedTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("max (Y)", -1).setDropShadow(true))
                .setHoverTooltip("multiblocked.gui.predicate.max"));


        group.addWidget(preview = (TextFieldWidget) new TextFieldWidget(55, 51 , 30, 15, true, () -> previewCount + "", s -> previewCount = Integer.parseInt(s)).setNumbersOnly(0, Integer.MAX_VALUE).setHoverTooltip("multiblocked.gui.predicate.preview"));
        preview.setHoverTooltip("multiblocked.gui.predicate.jei").setVisible(previewCount != -1);
        group.addWidget(new SwitchWidget(0, 51, 50, 15, (cd, r)->{
            preview.setVisible(r);
            previewCount = r ? 0 : -1;
        }).setPressed(previewCount != -1).setHoverBorderTexture(1, -1).setBaseTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("jei (N)", -1).setDropShadow(true)).setPressedTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("jei (Y)", -1).setDropShadow(true))
                .setHoverTooltip("multiblocked.gui.predicate.preview.1"));
        WidgetGroup widgetGroup = new WidgetGroup(0, 70, 100, 15)
                .addWidget(new SwitchWidget(0, 0, 15, 15, (cd, r)->disableRenderFormed = r)
                        .setBaseTexture(new ResourceTexture("multiblocked:textures/gui/boolean.png").getSubTexture(0,0,1,0.5))
                        .setPressedTexture(new ResourceTexture("multiblocked:textures/gui/boolean.png").getSubTexture(0,0.5,1,0.5))
                        .setHoverTexture(new ColorBorderTexture(1, -1))
                        .setPressed(disableRenderFormed)
                        .setHoverTooltip("multiblocked.gui.predicate.disabled"))
                .addWidget(new ImageWidget(2, 2, 11, 11, new ColorBorderTexture(1, -1)))
                .addWidget(new LabelWidget(20, 3, "disableRenderFormed").setTextColor(-1).setDrop(true));
        group.addWidget(widgetGroup);

        group.addWidget(nbt = new TextFieldWidget(155, 15, 100, 15, true, null, s -> nbtParser = s));
        nbt.setCurrentString(nbtParser == null ? "" : nbtParser).setHoverTooltip("nbt parser").setVisible(nbtParser != null);
        group.addWidget(new SwitchWidget(100, 15, 50, 15, (cd, r)->{
            nbt.setVisible(r);
            nbtParser = r ? "" : null;
        }).setPressed(nbtParser != null).setHoverBorderTexture(1, -1)
                .setBaseTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("nbt (N)", -1).setDropShadow(true))
                .setPressedTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("nbt (Y)", -1).setDropShadow(true))
                .setHoverTooltip("multiblocked.gui.predicate.nbt"));

        group.addWidget(tooltips = new TextFieldWidget(155, 33, 100, 15, true, null, s -> customTips = s));
        tooltips.setCurrentString(customTips != null ? customTips : "").setHoverTooltip("multiblocked.gui.predicate.tips").setVisible(customTips != null);
        group.addWidget(new SwitchWidget(100, 33, 50, 15, (cd, r) -> {
            tooltips.setVisible(r);
            customTips = r ? "" : null;
        }).setPressed(customTips != null).setHoverBorderTexture(1, -1)
                .setBaseTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("tips (N)", -1).setDropShadow(true))
                .setPressedTexture(new ResourceTexture("multiblocked:textures/gui/button_common.png"), new TextTexture("tips (Y)", -1).setDropShadow(true))
                .setHoverTooltip("multiblocked.gui.predicate.add_tips"));

        group.addWidget(new SelectorWidget(130, 70, 40, 15, Arrays.asList("IN", "OUT", "BOTH", "NULL"), -1)
                .setValue(io == null ? "NULL" : io.name())
                .setIsUp(true)
                .setOnChanged(io-> this.io = io.equals("NULL") ? null : IO.valueOf(io))
                .setButtonBackground(ResourceBorderTexture.BUTTON_COMMON)
                .setBackground(new ColorRectTexture(0xff333333))
                .setHoverTooltip("multiblocked.gui.tips.io"));
        return groups;
    }

    public JsonObject toJson(JsonObject jsonObject) {
        jsonObject.add("type", new JsonPrimitive(type));
        if (disableRenderFormed) {
            jsonObject.addProperty("disableRenderFormed", true);
        }
        if (minCount > -1) {
            jsonObject.addProperty("minCount", minCount);
        }
        if (maxCount > -1) {
            jsonObject.addProperty("maxCount", maxCount);
        }
        if (previewCount > -1) {
            jsonObject.addProperty("previewCount", previewCount);
        }
        if (io != IO.BOTH) {
            if (io == null) {
                jsonObject.addProperty("io", "null");
            } else {
                jsonObject.addProperty("io", io.name());
            }
        }
        if (nbtParser != null) {
            jsonObject.addProperty("nbtParser", nbtParser);
        }
        if (customTips != null) {
            jsonObject.addProperty("customTips", customTips);
        }
        if (isCTParser) {
            jsonObject.addProperty("isCTParser", true);
        }
        return jsonObject;
    }

    public void fromJson(Gson gson, JsonObject jsonObject) {
        disableRenderFormed = JsonUtils.getBoolean(jsonObject, "disableRenderFormed", disableRenderFormed);
        minCount = JsonUtils.getInt(jsonObject, "minCount", minCount);
        maxCount = JsonUtils.getInt(jsonObject, "maxCount", maxCount);
        previewCount = JsonUtils.getInt(jsonObject, "previewCount", previewCount);
        io = JsonUtils.getString(jsonObject, "io", "").equals("null") ? null : IO.valueOf(JsonUtils.getString(jsonObject, "io", IO.BOTH.name()));
        nbtParser = JsonUtils.getString(jsonObject, "nbtParser", nbtParser);
        customTips = JsonUtils.getString(jsonObject, "customTips", customTips);
        isCTParser = JsonUtils.getBoolean(jsonObject, "isCTParser", isCTParser);
    }
    
}
