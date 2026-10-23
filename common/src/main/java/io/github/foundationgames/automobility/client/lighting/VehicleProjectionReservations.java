package io.github.foundationgames.automobility.client.lighting;
import java.util.*;
import java.util.function.Predicate;
/** Moving a wholly unspent tick reservation never creates or resets credit. */
public final class VehicleProjectionReservations {
 private VehicleProjectionReservations(){}
 public static <T> void transferUnused(Map<Integer,T> ledger,List<Integer> wanted,Predicate<T> unused){
  if(ledger.size()>7||wanted.size()>7||new HashSet<>(wanted).size()!=wanted.size())throw new IllegalArgumentException("Bounded unique FULL reservations");
  var donors=new TreeSet<Integer>();for(var entry:ledger.entrySet())if(!wanted.contains(entry.getKey())&&unused.test(entry.getValue()))donors.add(entry.getKey());
  for(int id:wanted)if(!ledger.containsKey(id)&&!donors.isEmpty()){int donor=donors.pollFirst();T credit=ledger.remove(donor);if(credit==null||!unused.test(credit))throw new IllegalStateException("Spent transfer");ledger.put(id,credit);}
 }
}
