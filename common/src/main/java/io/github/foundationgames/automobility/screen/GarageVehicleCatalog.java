package io.github.foundationgames.automobility.screen;

import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Pure browsing metadata: never changes a frame, prefab, entity, UUID or server action. */
public final class GarageVehicleCatalog {
    private GarageVehicleCatalog() {}
    public record VariantInfo(String familyKey, String titleKey, String colorKey, int colorArgb, int order) {}
    public record Group<T>(String key, String titleKey, List<T> variants) {
        public Group {
            Objects.requireNonNull(key); Objects.requireNonNull(titleKey);
            variants=List.copyOf(variants);
            if(variants.isEmpty()) throw new IllegalArgumentException("A vehicle group needs an original choice");
        }
        public boolean colorGroup() { return !titleKey.isEmpty(); }
        /** Selection wins over remembered browsing state, and both return the original choice. */
        public T representative(Predicate<T> selected, Predicate<T> remembered) {
            Objects.requireNonNull(selected); Objects.requireNonNull(remembered);
            for(var option:variants) if(selected.test(option)) return option;
            for(var option:variants) if(remembered.test(option)) return option;
            return variants.getFirst();
        }
    }
    private static final String STANDARD="family:automobility:standard", TRACTOR="family:automobility:tractor";
    private static final Map<Identifier,VariantInfo> VARIANTS=palette();
    /** Only these exact built-in IDs are colors of one catalog family. No geometry/prefix inference. */
    private static Map<Identifier,VariantInfo> palette() {
        var result=new LinkedHashMap<Identifier,VariantInfo>();
        add(result,"standard_white",STANDARD,"standard","white",0,0xFFF9FFFE);
        add(result,"standard_orange",STANDARD,"standard","orange",1,0xFFF9801D);
        add(result,"standard_magenta",STANDARD,"standard","magenta",2,0xFFC74EBD);
        add(result,"standard_light_blue",STANDARD,"standard","light_blue",3,0xFF3AB3DA);
        add(result,"standard_yellow",STANDARD,"standard","yellow",4,0xFFFED83D);
        add(result,"standard_lime",STANDARD,"standard","lime",5,0xFF80C71F);
        add(result,"standard_pink",STANDARD,"standard","pink",6,0xFFF38BAA);
        add(result,"standard_gray",STANDARD,"standard","gray",7,0xFF474F52);
        add(result,"standard_light_gray",STANDARD,"standard","light_gray",8,0xFF9D9D97);
        add(result,"standard_cyan",STANDARD,"standard","cyan",9,0xFF169C9C);
        add(result,"standard_purple",STANDARD,"standard","purple",10,0xFF8932B8);
        add(result,"standard_blue",STANDARD,"standard","blue",11,0xFF3C44AA);
        add(result,"standard_brown",STANDARD,"standard","brown",12,0xFF835432);
        add(result,"standard_green",STANDARD,"standard","green",13,0xFF5E7C16);
        add(result,"standard_red",STANDARD,"standard","red",14,0xFFB02E26);
        add(result,"standard_black",STANDARD,"standard","black",15,0xFF1D1D21);
        add(result,"red_tractor",TRACTOR,"tractor","red",0,0xFFB02E26);
        add(result,"yellow_tractor",TRACTOR,"tractor","yellow",1,0xFFFED83D);
        add(result,"green_tractor",TRACTOR,"tractor","green",2,0xFF5E7C16);
        add(result,"blue_tractor",TRACTOR,"tractor","blue",3,0xFF3C44AA);
        return Map.copyOf(result);
    }
    private static void add(Map<Identifier,VariantInfo> target,String frame,String family,String title,String color,int order,int argb) {
        target.put(Identifier.parse("automobility:"+frame),new VariantInfo(family,"garage.automobility.picker."+title,"color.minecraft."+color,argb,order));
    }
    public static Optional<VariantInfo> describe(Identifier frameId) {
        return frameId==null?Optional.empty():Optional.ofNullable(VARIANTS.get(frameId));
    }
    /** Input order determines group order; the explicit palette determines color order.
     * Unknown frames and all owned instances remain individual UUID-keyed choices.
     */
    public static <T> List<Group<T>> group(List<T> options,Function<T,Identifier> frameId,Function<T,UUID> identity,boolean catalog) {
        Objects.requireNonNull(options); Objects.requireNonNull(frameId); Objects.requireNonNull(identity);
        var buckets=new LinkedHashMap<String,List<T>>(); var titles=new LinkedHashMap<String,String>();
        var identities=new HashSet<UUID>();
        for(var option:options) {
            var uuid=Objects.requireNonNull(identity.apply(option));
            if(!identities.add(uuid)) throw new IllegalArgumentException("Duplicate vehicle selection UUID");
            var info=catalog?describe(frameId.apply(option)):Optional.<VariantInfo>empty();
            String key=info.map(VariantInfo::familyKey).orElse("instance:"+uuid);
            buckets.computeIfAbsent(key,ignored->new ArrayList<>()).add(option);
            titles.putIfAbsent(key,info.map(VariantInfo::titleKey).orElse(""));
        }
        var result=new ArrayList<Group<T>>();
        for(var entry:buckets.entrySet()) {
            if(!titles.get(entry.getKey()).isEmpty()) entry.getValue().sort(Comparator
                    .comparingInt((T option)->describe(frameId.apply(option)).orElseThrow().order())
                    .thenComparing(option->identity.apply(option).toString()));
            result.add(new Group<>(entry.getKey(),titles.get(entry.getKey()),entry.getValue()));
        }
        return List.copyOf(result);
    }
}
