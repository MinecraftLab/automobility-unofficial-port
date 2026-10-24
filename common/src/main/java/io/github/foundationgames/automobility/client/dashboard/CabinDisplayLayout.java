package io.github.foundationgames.automobility.client.dashboard;

/** Panel model uses +Y down; its reader sees model +X on the left and the visible +Z face. */
public record CabinDisplayLayout(float contentX0, float contentX1, float textY, float textZ, float fontScale) {
    public CabinDisplayLayout {
        if (!Float.isFinite(contentX0) || !Float.isFinite(contentX1) || contentX1 <= contentX0
                || !Float.isFinite(textY) || !Float.isFinite(textZ) || !Float.isFinite(fontScale) || fontScale <= 0)
            throw new IllegalArgumentException("invalid display layout");
    }
    public static CabinDisplayLayout forPanel(float width, float height, boolean cassette, boolean progress) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= .06f || height <= .06f)
            throw new IllegalArgumentException("invalid panel dimensions");
        float contentWidth = (width - .028f) * (cassette ? .67f : 1);
        float scale = Math.min(contentWidth / 104, (height - (progress ? .047f : .04f)) / 40);
        return new CabinDisplayLayout(.014f, .014f + contentWidth, .008f, .0028f, scale);
    }
    /** Native font quads face -Z. Reflect X so text reads left-to-right on this +Z dashboard face. */
    public float scaleX() { return -fontScale; }
    public float scaleY() { return fontScale; }
    /** Font internal depth offsets are font pixels too, not unscaled metres. */
    public float scaleZ() { return fontScale; }
    public float textX() { return contentX1; }
    public float glyphX(float pixel) { return textX() + pixel * scaleX(); }
    public float glyphY(float pixel) { return textY + pixel * scaleY(); }
    public float glyphZ(float pixel) { return textZ + pixel * scaleZ(); }
    public float progressX0(float progress) {
        if (!Float.isFinite(progress) || progress < 0 || progress > 1) throw new IllegalArgumentException("progress");
        return contentX1 - (contentX1 - contentX0) * progress;
    }
    public float logoSize() { return Math.min(.024f, (contentX1 - contentX0) * .16f); }
}
