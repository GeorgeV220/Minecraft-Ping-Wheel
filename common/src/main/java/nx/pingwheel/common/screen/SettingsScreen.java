package nx.pingwheel.common.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import nx.pingwheel.common.config.Config;
import nx.pingwheel.common.helper.OptionUtils;

import static nx.pingwheel.common.ClientGlobal.ConfigHandler;
import static nx.pingwheel.common.config.Config.MAX_CHANNEL_LENGTH;

public class SettingsScreen extends Screen {

    private final Config config;

    private Screen parent;
    private OptionListWidget list;
    private TextFieldWidget channelTextField;

    public SettingsScreen() {
        super(Text.translatable("ping-wheel.settings.title"));
        this.config = ConfigHandler.getConfig();
    }

    public SettingsScreen(Screen parent) {
        this();
        this.parent = parent;
    }

    @Override
    public void tick() {
        if (this.channelTextField.isFocused() && this.getFocused() != this.channelTextField) {
            this.setFocused(this.channelTextField);
        }
    }

    @Override
    protected void init() {
        this.list = new OptionListWidget(this.client, this.width, this.height - 64, 32, 25);

        final var pingVolumeOption = getPingVolumeOption();
        final var pingDurationOption = getPingDurationOption();
        this.list.addOptionEntry(pingVolumeOption, pingDurationOption);

        final var pingDistanceOption = getPingDistanceOption();
        final var correctionPeriodOption = getCorrectionPeriodOption();
        this.list.addOptionEntry(pingDistanceOption, correctionPeriodOption);

        final var itemIconsVisibleOption = getItemIconsVisibleOption();
        final var directionIndicatorVisibleOption = getDirectionIndicatorVisibleOption();
        this.list.addOptionEntry(itemIconsVisibleOption, directionIndicatorVisibleOption);

        final var pingSizeOption = getPingSizeOption();
        this.list.addOptionEntry(pingSizeOption, null);

        this.channelTextField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 160, 200, 20, Text.empty());
        this.channelTextField.setMaxLength(MAX_CHANNEL_LENGTH);
        this.channelTextField.setText(config.getChannel());
        this.channelTextField.setChangedListener(config::setChannel);
        this.addSelectableChild(this.channelTextField);

        this.addSelectableChild(this.list);

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, (button) -> close())
                .position(this.width / 2 - 100, this.height - 27)
                .size(200, 20)
                .build());
    }

    @Override
    public void close() {
        ConfigHandler.save();

        if (parent != null && this.client != null) {
            this.client.setScreen(parent);
        } else {
            super.close();
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackgroundTexture(ctx);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.list.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 16777215);

        ctx.drawTextWithShadow(this.textRenderer, Text.translatable("ping-wheel.settings.channel"), this.width / 2 - 100, 148, 10526880);
        this.channelTextField.render(ctx, mouseX, mouseY, delta);

        if (this.channelTextField.isHovered() && !this.channelTextField.isFocused()) {
            ctx.drawOrderedTooltip(this.textRenderer, this.textRenderer.wrapLines(Text.translatable("ping-wheel.settings.channel.tooltip"), 140), mouseX, mouseY);
        }
    }

    private SimpleOption<Integer> getPingVolumeOption() {
        final var pingVolumeKey = "ping-wheel.settings.pingVolume";

        return OptionUtils.ofInt(
                pingVolumeKey,
                0, 100, 1,
                (value) -> {
                    if (value == 0) {
                        return Text.translatable(pingVolumeKey, ScreenTexts.OFF);
                    }

                    return Text.translatable(pingVolumeKey, String.format("%s%%", value));
                },
                config::getPingVolume,
                config::setPingVolume
        );
    }

    private SimpleOption<Integer> getPingDurationOption() {
        final var pingDurationKey = "ping-wheel.settings.pingDuration";

        return OptionUtils.ofInt(
                pingDurationKey,
                1, 60, 1,
                (value) -> Text.translatable(pingDurationKey, String.format("%ss", config.getPingDuration())),
                config::getPingDuration,
                config::setPingDuration
        );
    }

    private SimpleOption<Integer> getPingDistanceOption() {
        final var pingDistanceKey = "ping-wheel.settings.pingDistance";

        return OptionUtils.ofInt(
                pingDistanceKey,
                0, 2048, 16,
                (value) -> {
                    if (value == 0) {
                        return Text.translatable(pingDistanceKey, Text.translatable(pingDistanceKey + ".hidden"));
                    } else if (value == 2048) {
                        return Text.translatable(pingDistanceKey, Text.translatable(pingDistanceKey + ".unlimited"));
                    }

                    return Text.translatable(pingDistanceKey, String.format("%sm", value));
                },
                config::getPingDistance,
                config::setPingDistance
        );
    }

    private SimpleOption<Float> getCorrectionPeriodOption() {
        final var correctionPeriodKey = "ping-wheel.settings.correctionPeriod";

        return OptionUtils.ofFloat(
                correctionPeriodKey,
                0.1f, 5.0f, 0.1f,
                (value) -> Text.translatable(correctionPeriodKey, String.format("%.1fs", value)),
                config::getCorrectionPeriod,
                config::setCorrectionPeriod
        );
    }

    private SimpleOption<Boolean> getItemIconsVisibleOption() {
        return OptionUtils.ofBool(
                "ping-wheel.settings.itemIconVisible",
                config::isItemIconVisible,
                config::setItemIconVisible
        );
    }

    private SimpleOption<Boolean> getDirectionIndicatorVisibleOption() {
        return OptionUtils.ofBool(
                "ping-wheel.settings.directionIndicatorVisible",
                config::isDirectionIndicatorVisible,
                config::setDirectionIndicatorVisible
        );
    }

    private SimpleOption<Integer> getPingSizeOption() {
        final var pingSizeKey = "ping-wheel.settings.pingSize";

        return OptionUtils.ofInt(
                pingSizeKey,
                40, 300, 10,
                (value) -> Text.translatable(pingSizeKey, String.format("%s%%", value)),
                config::getPingSize,
                config::setPingSize
        );
    }
}
