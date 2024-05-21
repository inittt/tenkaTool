package jp.juggler.character;

public class SSRItem implements Comparable<SSRItem>{
    public int percent;
    public String name;
    public String tag;

    public SSRItem(int p, String n, String t){
        this.percent = p;
        this.name = n;
        this.tag = t;
    }
    @Override
    public int compareTo(SSRItem o) {
        if (this.percent != o.percent) return this.percent - o.percent;
        else return this.tag.compareTo(o.tag);
    }
}