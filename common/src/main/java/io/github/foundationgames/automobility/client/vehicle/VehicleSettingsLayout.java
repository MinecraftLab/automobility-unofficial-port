package io.github.foundationgames.automobility.client.vehicle;

/** Native settings geometry, including all supported intermediate GUI heights. */
public record VehicleSettingsLayout(int left,int top,int width,int height,boolean small) {
    public static VehicleSettingsLayout resolve(int screenWidth,int screenHeight) {
        int w=Math.max(1,Math.min(400,screenWidth-16));
        int h=Math.max(1,Math.min(244,screenHeight-16));
        return new VehicleSettingsLayout((screenWidth-w)/2,(screenHeight-h)/2,w,h,w<300 || h<224);
    }
    public int dividerY() { return height-48; }
    public int footerY() { return height-30; }
    public int graphicsLinkY() { return height-74; }
    public int previewBottomY() { return height-58; }
}
