package sonar.fluxnetworks.client.gui.button;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import sonar.fluxnetworks.client.gui.GuiTabType;
import sonar.fluxnetworks.client.gui.basic.GuiButtonCore;

public class NavigationButton extends GuiButtonCore {

    private final GuiTabType mTab;
    private boolean mSelected = false;

    public NavigationButton(Minecraft mc, int x, int y, GuiTabType tab) {
        super(mc, x, y, 16, 16);
        mTab = tab;
    }

    @Override
    protected void drawButton(PoseStack poseStack, int mouseX, int mouseY, float deltaTicks) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0f);
        RenderSystem.setShaderTexture(0, BUTTONS);

        boolean hovered = isMouseHovered(mouseX, mouseY);
        int state = (mSelected || hovered) ? 1 : 0;
        blit(poseStack, x, y, 16 * mTab.ordinal(), 16 * state, 16, 16);

        if (hovered) {
            drawCenteredString(poseStack, mc.font, mTab.getTranslatedName(), x + width / 2, y - 10, 0xFFFFFFFF);
        }
    }

    public GuiTabType getTab() {
        return mTab;
    }

    public boolean isSelected() {
        return mSelected;
    }

    public void setSelected(boolean selected) {
        mSelected = selected;
    }
}
