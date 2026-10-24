package io.github.foundationgames.automobility.client.dashboard;

/** Full display palettes. Stable RGB values retain the existing cosmetic preference format. */
public enum InfotainmentTheme {
    FLAMINGO("Flamingo",0xf273b1,0xfff1e2,0x241620,0x432334,0xfbe9df),
    MC_GREEN("MC Green",0x75c947,0xdce7d5,0x151c17,0x173020,0xe1efdb),
    NOTCH("Notch",0xffb449,0xe8d5ac,0x211b11,0x382d1b,0xf3dfb5),
    FORMULA("Formula",0xe34b4f,0xe7e9ea,0x171a1f,0x24282e,0xf0f2f4);
    public final String title;
    public final int accent;
    private final int daySurface,nightSurface,dayText,nightText;
    InfotainmentTheme(String title,int accent,int daySurface,int nightSurface,int dayText,int nightText){
        this.title=title;this.accent=accent;this.daySurface=daySurface;this.nightSurface=nightSurface;this.dayText=dayText;this.nightText=nightText;
    }
    public record Palette(int surface,int text,int accent) { }
    public static InfotainmentTheme find(int rgb){
        for(var theme:values())if(theme.accent==rgb)return theme;
        return switch(rgb){case 0xf0d4df->FLAMINGO;case 0x95d48b,0xd1ddcb->MC_GREEN;default->null;};
    }
    public static String name(int rgb){var theme=find(rgb);return theme==null?String.format(java.util.Locale.ROOT,"Custom #%06X",rgb):theme.title;}
    public static int next(int rgb){var theme=find(rgb);return values()[theme==null?0:(theme.ordinal()+1)%values().length].accent;}
    public static Palette palette(int rgb,float night){
        var theme=find(rgb);float t=Math.clamp(night,0,1);
        int surface=theme==null?mix(0xe7e9ea,0x171a1f,t):mix(theme.daySurface,theme.nightSurface,t);
        int text=theme==null?mix(0x24282e,0xf0f2f4,t):mix(theme.dayText,theme.nightText,t);
        int accent=theme==null?rgb:mix(theme.dayText,theme.accent,.30f+.70f*t);
        // Foreground and background cross during twilight. Preserve the smooth surface transition,
        // but select readable ink instead of leaving rain/shade at a permanent grey-on-grey midpoint.
        return new Palette(surface,legibleInk(surface,text),legibleInk(surface,accent));
    }
    /** Apply the saved backlight first, then retain readable glyphs against that final surface. */
    public static Palette dim(Palette palette, float brightness) {
        float value=Float.isFinite(brightness)?Math.clamp(brightness,0,1):0;
        if(value==0)return new Palette(0,0,0);
        int surface=shade(palette.surface(),value);
        return new Palette(surface,dimmedInk(surface,shade(palette.text(),value)),
                dimmedInk(surface,shade(palette.accent(),value)));
    }
    private static int shade(int rgb,float value) {
        return ((int)(((rgb>>16)&255)*value)<<16)|((int)(((rgb>>8)&255)*value)<<8)|(int)((rgb&255)*value);
    }
    private static double contrast(int a,int b) {
        double left=luminance(a),right=luminance(b);
        return (Math.max(left,right)+.05)/(Math.min(left,right)+.05);
    }
    private static int dimmedInk(int surface,int desired) {
        if(contrast(surface,desired)>=4.5)return desired;
        int endpoint=contrast(surface,0)>=contrast(surface,0xffffff)?0:0xffffff;
        // Keep as much of the dimmed theme colour as possible, instead of jumping to full white.
        int low=0,high=256;
        while(high-low>1){int middle=(low+high)/2;
            if(contrast(surface,mix(desired,endpoint,middle/256f))>=4.5)high=middle;else low=middle;
        }
        return mix(desired,endpoint,high/256f);
    }
    private static final double[] LINEAR=linearChannels();
    private static double[] linearChannels(){
        double[] result=new double[256];
        for(int channel=0;channel<256;channel++){
            double value=channel/255.0;
            result[channel]=value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);
        }
        return result;
    }
    private static int legibleInk(int surface,int desired) {
        double background=luminance(surface),ink=luminance(desired);
        if((Math.max(background,ink)+.05)/(Math.min(background,ink)+.05)>=4.5)return desired;
        return (background+.05)/.05>=1.05/(background+.05)?0x000000:0xffffff;
    }
    private static double luminance(int rgb) {
        return .2126*linear((rgb>>16)&255)+.7152*linear((rgb>>8)&255)+.0722*linear(rgb&255);
    }
    private static double linear(int channel) {
        return LINEAR[channel];
    }
    private static int mix(int a,int b,float t){int out=0;for(int shift=0;shift<24;shift+=8)out|=Math.round(((a>>shift)&255)*(1-t)+((b>>shift)&255)*t)<<shift;return out;}
}
