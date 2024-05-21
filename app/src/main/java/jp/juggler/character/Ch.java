package jp.juggler.character;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Ch implements Comparable<Ch>{
    public String name;
    public boolean isSR;
    public Set<String> tags;
    public Ch(String n, boolean isSR, String tgs) {
        this.name = n;
        this.isSR = isSR;
        this.tags = new HashSet<>();
        Collections.addAll(tags, tgs.split(" "));
    }

    @Override
    public int compareTo(Ch o) {
        return o.name.compareTo(this.name);
    }
}
