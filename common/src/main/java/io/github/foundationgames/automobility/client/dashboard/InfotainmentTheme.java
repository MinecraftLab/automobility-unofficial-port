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
        return theme==null?new Palette(mix(0xe7e9ea,0x171a1f,t),mix(0x24282e,0xf0f2f4,t),rgb)
                :new Palette(mix(theme.daySurface,theme.nightSurface,t),mix(theme.dayText,theme.nightText,t),mix(theme.dayText,theme.accent,.30f+.70f*t));
    }
    private static int mix(int a,int b,float t){int out=0;for(int shift:new int[]{0,8,16})out|=Math.round(((a>>shift)&255)*(1-t)+((b>>shift)&255)*t)<<shift;return out;}
}
