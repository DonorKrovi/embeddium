package org.embeddedt.embeddium.gui;

import com.google.common.collect.Multimap;
import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.data.fingerprint.HashedFingerprint;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import me.jellysquid.mods.sodium.client.gui.console.Console;
import me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.embeddium.client.gui.options.OptionIdentifier;
import org.embeddedt.embeddium.gui.frame.AbstractFrame;
import org.embeddedt.embeddium.gui.frame.BasicFrame;
import org.embeddedt.embeddium.gui.frame.ScrollableFrame;
import org.embeddedt.embeddium.gui.frame.components.SearchTextFieldComponent;
import org.embeddedt.embeddium.gui.frame.components.SearchTextFieldModel;
import org.embeddedt.embeddium.gui.frame.tab.Tab;
import org.embeddedt.embeddium.gui.frame.tab.TabFrame;
import org.embeddedt.embeddium.gui.screen.PromptScreen;
import org.embeddedt.embeddium.gui.theme.DefaultColors;
import org.embeddedt.embeddium.render.ShaderModBridge;
import org.embeddedt.embeddium.util.PlatformUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class EmbeddiumVideoOptionsScreen extends Screen {
    private static final ResourceLocation LOGO_LOCATION = new ResourceLocation(SodiumClientMod.MODID, "textures/embeddium/gui/logo_transparent.png");
    private static final int LOGO_SIZE = 256;

    private static final AtomicReference<Component> tabFrameSelectedTab = new AtomicReference<>(null);
    private final AtomicReference<Integer> tabFrameScrollBarOffset = new AtomicReference<>(0);
    private final AtomicReference<Integer> optionPageScrollBarOffset = new AtomicReference<>(0);

    private final Screen prevScreen;
    private final List<OptionPage> pages = new ArrayList<>();
    private AbstractFrame frame;
    private FlatButtonWidget applyButton, closeButton, undoButton;

    private Dim2i logoDim;

    private boolean hasPendingChanges;

    private SearchTextFieldComponent searchTextField;
    private final SearchTextFieldModel searchTextModel;

    private boolean firstInit = true;

    public EmbeddiumVideoOptionsScreen(Screen prev, List<OptionPage> pages) {
        super(Component.literal("Cobble Options"));
        this.prevScreen = prev;
        this.pages.addAll(pages);
        this.searchTextModel = new SearchTextFieldModel(this.pages, this);
        registerTextures();
    }

    private void registerTextures() {
        Minecraft.getInstance().textureManager.register(LOGO_LOCATION, new SimpleTexture(LOGO_LOCATION));
    }

    public void rebuildUI() {
        // Remember if the search bar was previously focused since we'll lose that information after recreating
        // the widget.
        boolean wasSearchFocused = this.searchTextField.isFocused();
        this.rebuildWidgets();
        if(wasSearchFocused) {
            this.setFocused(this.searchTextField);
        }
    }

    @Override
    protected void init() {
        this.frame = this.parentFrameBuilder().build();
        this.addRenderableWidget(this.frame);

        this.setFocused(this.frame);

        if(firstInit) {
            this.setFocused(this.searchTextField);
            firstInit = false;
        }
    }

    private static final float ASPECT_RATIO = 5f / 4f;
    private static final int MINIMUM_WIDTH = 550;

    protected BasicFrame.Builder parentFrameBuilder() {
        BasicFrame.Builder basicFrameBuilder;

        // Apply aspect ratio clamping on wide enough screens
        int newWidth = this.width;
        if (newWidth > MINIMUM_WIDTH && (float) this.width / (float) this.height > ASPECT_RATIO) {
            newWidth = Math.max(MINIMUM_WIDTH, (int) (this.height * ASPECT_RATIO));
        }

        Dim2i basicFrameDim = new Dim2i((this.width - newWidth) / 2, 0, newWidth, this.height);
        Dim2i tabFrameDim = new Dim2i(basicFrameDim.x() + basicFrameDim.width() / 20 / 2, basicFrameDim.y() + basicFrameDim.height() / 4 / 2, basicFrameDim.width() - (basicFrameDim.width() / 20), basicFrameDim.height() / 4 * 3);

        Dim2i undoButtonDim = new Dim2i(tabFrameDim.getLimitX() - 203, tabFrameDim.getLimitY() + 5, 65, 20);
        Dim2i applyButtonDim = new Dim2i(tabFrameDim.getLimitX() - 134, tabFrameDim.getLimitY() + 5, 65, 20);
        Dim2i closeButtonDim = new Dim2i(tabFrameDim.getLimitX() - 65, tabFrameDim.getLimitY() + 5, 65, 20);

        Component donationText = Component.translatable("sodium.options.buttons.donate");
        int donationTextWidth = this.minecraft.font.width(donationText);

        Dim2i donateButtonDim = new Dim2i(tabFrameDim.getLimitX() - 32 - donationTextWidth, tabFrameDim.y() - 26, 10 + donationTextWidth, 20);
        Dim2i hideDonateButtonDim = new Dim2i(tabFrameDim.getLimitX() - 20, tabFrameDim.y() - 26, 20, 20);

        int logoSizeOnScreen = 20;
        this.logoDim = new Dim2i(tabFrameDim.x(), tabFrameDim.getLimitY() + 25 - logoSizeOnScreen, logoSizeOnScreen, logoSizeOnScreen);

        this.undoButton = new FlatButtonWidget(undoButtonDim, Component.translatable("sodium.options.buttons.undo"), this::undoChanges);
        this.applyButton = new FlatButtonWidget(applyButtonDim, Component.translatable("sodium.options.buttons.apply"), this::applyChanges);
        this.closeButton = new FlatButtonWidget(closeButtonDim, Component.translatable("gui.done"), this::onClose);

        if (SodiumClientMod.options().notifications.hasClearedDonationButton) {
            this.setDonationButtonVisibility(false);
        }

        basicFrameBuilder = this.parentBasicFrameBuilder(basicFrameDim, tabFrameDim);

        this.searchTextField = new SearchTextFieldComponent(searchTextFieldDim, this.pages, this.searchTextModel);

        basicFrameBuilder.addChild(dim -> this.searchTextField);

        return basicFrameBuilder;
    }

    private boolean canShowPage(OptionPage page) {
        if(page.getGroups().isEmpty()) {
            return false;
        }

        // Check if any options on this page are visible
        var predicate = searchTextModel.getOptionPredicate();

        for(OptionGroup group : page.getGroups()) {
            for(Option<?> option : group.getOptions()) {
                if(predicate.test(option)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void createShaderPackButton(Multimap<String, Tab<?>> tabs) {
        if(this.searchTextModel.getOptionPredicate().test(null) && ShaderModBridge.isShaderModPresent()) {
            String shaderModId = Stream.of("oculus", "iris").filter(PlatformUtil::modPresent).findFirst().orElse("iris");
            tabs.put(shaderModId, Tab.createBuilder()
                    .setTitle(Component.translatable("options.iris.shaderPackSelection"))
                    .setId(OptionIdentifier.create("iris", "shader_packs"))
                    .setOnSelectFunction(() -> {
                        if(ShaderModBridge.openShaderScreen(this) instanceof Screen screen) {
                            this.minecraft.setScreen(screen);
                        }
                        return false;
                    })
                    .build());
        }
    }

    private AbstractFrame createTabFrame(Dim2i tabFrameDim) {
        // TabFrame will automatically expand its height to fit all tabs, so the scrollable frame can handle it
        return TabFrame.createBuilder()
                .setDimension(tabFrameDim)
                .shouldRenderOutline(false)
                .setTabSectionScrollBarOffset(tabFrameScrollBarOffset)
                .setTabSectionSelectedTab(tabFrameSelectedTab)
                .addTabs(tabs -> this.pages
                        .stream()
                        .filter(this::canShowPage)
                        .forEach(page -> tabs.put(page.getId().getModId(), Tab.createBuilder().from(page, searchTextModel.getOptionPredicate(), optionPageScrollBarOffset)))
                )
                .addTabs(this::createShaderPackButton)
                .onSetTab(() -> {
                    optionPageScrollBarOffset.set(0);
                })
                .build();
    }

    public BasicFrame.Builder parentBasicFrameBuilder(Dim2i parentBasicFrameDim, Dim2i tabFrameDim) {
        return BasicFrame.createBuilder()
                .setDimension(parentBasicFrameDim)
                .shouldRenderOutline(false)
                .addChild(parentDim -> this.createTabFrame(tabFrameDim))
                .addChild(dim -> this.undoButton)
                .addChild(dim -> this.applyButton)
                .addChild(dim -> this.closeButton);
    }

    @Override
    public void renderBackground(GuiGraphics gfx) {
        super.renderBackground(gfx);

        // Render watermarks
        gfx.setColor(ColorARGB.unpackRed(DefaultColors.ELEMENT_ACTIVATED) / 255f, ColorARGB.unpackGreen(DefaultColors.ELEMENT_ACTIVATED) / 255f, ColorARGB.unpackBlue(DefaultColors.ELEMENT_ACTIVATED) / 255f, 0.8F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 1);
        gfx.blit(LOGO_LOCATION, this.logoDim.x(), this.logoDim.y(), this.logoDim.width(), this.logoDim.height(), 0.0F, 0.0F, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        gfx.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void render(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        this.renderBackground(drawContext);
        this.updateControls();
        this.frame.render(drawContext, mouseX, mouseY, delta);
    }

    private void updateControls() {
        boolean hasChanges = this.getAllOptions()
                .anyMatch(Option::hasChanged);

        for (OptionPage page : this.pages) {
            for (Option<?> option : page.getOptions()) {
                if (option.hasChanged()) {
                    hasChanges = true;
                }
            }
        }

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.hasPendingChanges = hasChanges;
    }

    private Stream<Option<?>> getAllOptions() {
        return this.pages.stream()
                .flatMap(s -> s.getOptions().stream());
    }

    private void applyChanges() {
        final HashSet<OptionStorage<?>> dirtyStorages = new HashSet<>();
        final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);

        this.getAllOptions().forEach((option -> {
            if (!option.hasChanged()) {
                return;
            }

            option.applyChanges();

            flags.addAll(option.getFlags());
            dirtyStorages.add(option.getStorage());
        }));

        Minecraft client = Minecraft.getInstance();

        if (client.level != null) {
            if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
                client.levelRenderer.allChanged();
            } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
                client.levelRenderer.needsUpdate();
            }
        }

        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            client.updateMaxMipLevel(client.options.mipmapLevels().get());
            client.delayTextureReload();
        }

        if (flags.contains(OptionFlag.REQUIRES_GAME_RESTART)) {
            Console.instance().logMessage(MessageLevel.WARN,
                    Component.translatable("sodium.console.game_restart"), 10.0);
        }

        for (OptionStorage<?> storage : dirtyStorages) {
            storage.save();
        }
    }

    private void undoChanges() {
        this.getAllOptions()
                .forEach(Option::reset);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_P && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && !(this.searchTextField != null && this.searchTextField.isFocused())) {
            Minecraft.getInstance().setScreen(new VideoSettingsScreen(this.prevScreen, Minecraft.getInstance().options));

            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.hasPendingChanges;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.prevScreen);
    }
}
