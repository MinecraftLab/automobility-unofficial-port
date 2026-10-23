package io.github.foundationgames.automobility.api.drivetrain.energy;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.api.drivetrain.VehicleDrivetrainProfiles;
import java.util.*;

/** Fail-closed discovery result. UNMETERED is only standalone Core's existing liquid-fuel compatibility. */
public final class VehicleEnergyProviders {
    public enum Kind { MANAGED, UNMETERED, BLOCKED }
    public record Resolution(Kind kind, Optional<VehicleEnergyProvider.Binding> binding, String reason) {
        public Resolution {
            Objects.requireNonNull(kind); Objects.requireNonNull(binding); Objects.requireNonNull(reason);
            if ((kind == Kind.MANAGED) != binding.isPresent()) throw new IllegalArgumentException("binding/kind");
        }
    }
    private static List<VehicleEnergyProvider> providers = List.of();
    private static Set<String> loadedMods = Set.of();
    private static boolean installed, discoveryFailed;
    private VehicleEnergyProviders() { }
    public static synchronized void install(Collection<VehicleEnergyProvider> discovered, Set<String> actualLoadedMods, boolean failed) {
        if (installed) throw new IllegalStateException("energy discovery already installed");
        var unique = new HashSet<String>(); var accepted = new ArrayList<VehicleEnergyProvider>();
        var electric=new HashMap<String,VehicleDrivetrainProfiles.ElectricProfile>();
        for (var provider : discovered) {
            try {
                String id = provider.id();
                if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !unique.add(id) || provider.source() == null) failed = true;
                else {
                    accepted.add(provider);
                    for(var row:provider.electricProfiles().entrySet()){
                        if(provider.source()!=EnergyDemand.Source.ELECTRIC||row.getKey()==null||row.getKey().length()>128
                                ||!row.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!row.getKey().startsWith(id.substring(0,id.indexOf(':')+1))
                                ||row.getValue()==null||electric.putIfAbsent(row.getKey(),row.getValue())!=null)failed=true;
                    }
                }
            } catch (RuntimeException | LinkageError error) { failed = true; }
        }
        VehicleDrivetrainProfiles.installDiscoveredElectric(failed?Map.of():electric);
        providers = List.copyOf(accepted); loadedMods = Set.copyOf(actualLoadedMods); discoveryFailed = failed; installed = true;
    }
    public static Resolution resolve(AutomobileEntity car, EnergyDemand.Identity identity, EnergyDemand.Source source) {
        if (!installed || discoveryFailed) return blocked("owner discovery unavailable");
        if (car == null || identity == null || source == null || !car.getUUID().equals(identity.vehicleId()) || car.isRemoved()) return blocked("vehicle identity mismatch");
        var matching = new ArrayList<VehicleEnergyProvider>();
        try {
            for (var provider : providers) if (provider.source() == source && provider.manages(car)) matching.add(provider);
            if (matching.size() > 1) return blocked("multiple real-storage owners");
            String required = source == EnergyDemand.Source.FUEL && loadedMods.contains("pumps_and_pistons") ? "pumps_and_pistons:"
                    : source == EnergyDemand.Source.ELECTRIC && loadedMods.contains("powerworks") ? "powerworks:" : null;
            if (matching.isEmpty()) return required != null || source != EnergyDemand.Source.FUEL
                    ? blocked("required storage provider missing") : new Resolution(Kind.UNMETERED, Optional.empty(), "standalone Core fuel policy");
            var provider = matching.getFirst();
            if (required != null && !provider.id().startsWith(required)) return blocked("wrong installed storage owner");
            if(source==EnergyDemand.Source.ELECTRIC&&(!identity.physicalMotorId().isPresent()||!identity.physicalMotorId().equals(physicalMotorId(car))))return blocked("physical motor identity unavailable or changed");
            var binding = Objects.requireNonNull(provider.bind(car, identity));
            if (binding.capability().source() != source) return blocked("bound source mismatch");
            return new Resolution(Kind.MANAGED, Optional.of(binding), provider.id());
        } catch (RuntimeException | LinkageError error) { return blocked("storage provider failed"); }
    }
    public static Optional<UUID> physicalMotorId(AutomobileEntity car){
        if(!installed||discoveryFailed||car==null||car.isRemoved())return Optional.empty();
        try {
            VehicleEnergyProvider selected=null;
            for(var provider:providers)if(provider.source()==EnergyDemand.Source.ELECTRIC&&provider.manages(car)){
                if(selected!=null)return Optional.empty();selected=provider;
            }
            if(selected==null||loadedMods.contains("powerworks")&&!selected.id().startsWith("powerworks:"))return Optional.empty();
            var identity=selected.physicalMotorId(car);return identity.filter(id->!id.equals(new UUID(0,0)));
        }catch(RuntimeException|LinkageError unavailable){return Optional.empty();}
    }
    private static Resolution blocked(String reason) { return new Resolution(Kind.BLOCKED, Optional.empty(), reason); }
}
