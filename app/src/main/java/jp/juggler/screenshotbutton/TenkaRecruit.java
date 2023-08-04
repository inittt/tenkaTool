package jp.juggler.screenshotbutton;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TenkaRecruit {
    private Map<String, TAG> str2tag;
    private Map<TAG, String> tag2str;
    private Set<TAG> sr;
    private Map<TAG, Set<NAME>> CH;

    private enum NAME {
        // SR
        아이카(1), 레오나(2), 피오라(3), 리츠키(4), 미나요미(5), 시즈카(6), 쥬노안(7), 브리트니(8),
        나프라라(9), 토타라(10), 호타루(11), 가벨(12), 프리실라(13), 타노시아(14),

        //NR
        아이린(101), 나나(102), 아이리스(103),도라(104),세바스(105),마를렌(106),유이(107),소라카(108),
        이아(109),사이렌(110), 페트라(111),프레이(112),마누엘리(113),키쿄(114),카에데(115),올라(116),
        콜레트(117),샤린(118),마티나(119),클레어(120),로라(121), 미르노(122),라미아(123),하피(124),
        안나(125),브란(126),노노카(127),징천사(128),복천사(129),삼호(130);


        private int value;
        NAME(int value) {this.value = value;}
        public int getValue() {return value;}
    }

    private enum TAG {
        화속성,수속성,풍속성,광속성,암속성,
        딜러,탱커,힐러,디스럽터,서포터,
        인간,마족,야인,
        작은체형,표준체형,
        빈유,미유,거유,
        병사,정예,리더,
        데미지,보호,방어,회복,방해,지원,쇠약,
        폭발력,생존력, 전투,범위공격,반격
    }

    public TenkaRecruit() {
        str2tag = new HashMap<String, TAG>() {
            {
                put("화속성", TAG.화속성);put("수속성", TAG.수속성);put("풍속성", TAG.풍속성);put("광속성", TAG.광속성);put("암속성", TAG.암속성);
                put("딜러", TAG.딜러);put("탱커", TAG.탱커);put("힐러", TAG.힐러);put("디스럽터", TAG.디스럽터);put("서포터", TAG.서포터);
                put("인간", TAG.인간);put("마족", TAG.마족);put("야인", TAG.야인);put("작은체형", TAG.작은체형);put("표준체형", TAG.표준체형);
                put("빈유", TAG.빈유);put("미유", TAG.미유);put("거유", TAG.거유);put("병사", TAG.병사);put("정예", TAG.정예);put("리더", TAG.리더);
                put("데미지", TAG.데미지);put("보호", TAG.보호);put("방어", TAG.방어);put("회복", TAG.회복);put("방해", TAG.방해);put("지원", TAG.지원);
                put("쇠약", TAG.쇠약);put("폭발력", TAG.폭발력);put("생존력", TAG.생존력);put("전투", TAG.전투);put("범위공격", TAG.범위공격);
                put("반격", TAG.반격);
            }
        };
        tag2str = new HashMap<>();
        for (Map.Entry<String, TAG> entry : str2tag.entrySet()) {
            TAG tag = entry.getValue();
            String str = entry.getKey();
            tag2str.put(tag, str);
        }
        sr = new HashSet<>(Arrays.asList(TAG.정예, TAG.서포터, TAG.지원));
        setCH();
    }
    public String getResult(String txt) {
        String[] cur = txt.split(" ");
        if (cur.length != 5) return "인식 오류";
        TAG[] curTags = new TAG[5];
        String srTag = null;
        for(int i = 0; i < 5; i++) {
            String t = cur[i];
            if (!str2tag.containsKey(t)) return "인식 오류";
            TAG curTag = str2tag.get(t);
            if (curTag == TAG.리더) return "SSR 리더";
            if (sr.contains(curTag)) srTag = t;
            curTags[i] = curTag;
        }
        if (srTag != null) return "SR " + srTag;
        return getConfirmTag(curTags);
    }
    private String getConfirmTag(TAG[] tg) {
        ArrayList<ArrayList<TAG>> combList = new ArrayList<>();
        for(int a = 0; a < 4; a++) for(int b = a+1; b < 5; b++) {
            ArrayList<TAG> c2 = new ArrayList<>(), c3 = new ArrayList<>();
            for(int i = 0; i < 5; i++) {
                if (a == i || b == i) c2.add(tg[i]);
                else c3.add(tg[i]);
            }
            combList.add(c2);
            combList.add(c3);
        }
        for(ArrayList<TAG> tgs : combList) if (isOnlySR(tgs)) {
            StringBuilder sb = new StringBuilder().append("SR ");
            for(TAG t : tgs) sb.append(tag2str.get(t)).append(" ");
            return sb.toString();
        }
        return "NR";
    }
    private boolean isOnlySR(ArrayList<TAG> tgs) {
        ArrayList<Set<NAME>> results = new ArrayList<>();
        for(TAG t : tgs) results.add(CH.get(t));
        Set<NAME> retain = new HashSet<>(results.get(0));
        for(int i = 1; i < results.size(); i++) retain.retainAll(results.get(i));
        if (retain.size() == 0) return false;
        for(NAME n : retain) if (n.getValue() > 100) return false;
        return true;
    }
    private void setCH() {
        CH = new HashMap<>();
        addCH(TAG.화속성, new NAME[] {NAME.미나요미, NAME.아이리스, NAME.유이, NAME.키쿄, NAME.샤린, NAME.라미아, NAME.안나});
        addCH(TAG.수속성, new NAME[] {NAME.레오나, NAME.시즈카, NAME.호타루, NAME.마를렌, NAME.콜레트, NAME.로라, NAME.노노카, NAME.징천사, NAME.복천사});
        addCH(TAG.풍속성, new NAME[] {NAME.리츠키, NAME.나프라라, NAME.가벨, NAME.나나, NAME.도라, NAME.카에데, NAME.올라, NAME.미르노, NAME.하피, NAME.브란});
        addCH(TAG.광속성, new NAME[] {NAME.피오라, NAME.브리트니, NAME.토타라, NAME.타노시아, NAME.아이린, NAME.이아, NAME.페트라, NAME.프레이, NAME.마티나, NAME.클레어, NAME.삼호});
        addCH(TAG.암속성, new NAME[] {NAME.아이카, NAME.쥬노안, NAME.프리실라, NAME.세바스, NAME.소라카, NAME.사이렌, NAME.마누엘리});
        addCH(TAG.딜러, new NAME[] {NAME.리츠키, NAME.미나요미, NAME.쥬노안, NAME.토타라, NAME.가벨, NAME.나나, NAME.아이리스, NAME.유이, NAME.페트라, NAME.마누엘리, NAME.올라, NAME.콜레트, NAME.브란, NAME.노노카, NAME.삼호});
        addCH(TAG.탱커, new NAME[] {NAME.레오나, NAME.나프라라, NAME.도라, NAME.사이렌, NAME.프레이, NAME.샤린, NAME.마티나, NAME.미르노, NAME.안나, NAME.삼호});
        addCH(TAG.힐러, new NAME[] {NAME.피오라, NAME.호타루, NAME.아이린, NAME.마를렌, NAME.이아, NAME.카에데, NAME.클레어, NAME.복천사});
        addCH(TAG.디스럽터, new NAME[] {NAME.시즈카, NAME.브리트니, NAME.프리실라, NAME.세바스, NAME.소라카, NAME.키쿄, NAME.로라, NAME.라미아, NAME.하피});
        addCH(TAG.인간, new NAME[] {NAME.레오나, NAME.피오라, NAME.리츠키, NAME.쥬노안, NAME.브리트니, NAME.토타라, NAME.호타루, NAME.가벨, NAME.아이린, NAME.유이, NAME.이아, NAME.사이렌, NAME.페트라, NAME.키쿄, NAME.카에데, NAME.샤린, NAME.마티나, NAME.클레어, NAME.안나, NAME.브란, NAME.노노카});
        addCH(TAG.마족, new NAME[] {NAME.아이카, NAME.나프라라, NAME.나나, NAME.세바스, NAME.프레이, NAME.마누엘리, NAME.로라, NAME.라미아, NAME.하피});
        addCH(TAG.야인, new NAME[] {NAME.미나요미, NAME.시즈카, NAME.프리실라, NAME.타노시아, NAME.아이리스, NAME.도라, NAME.마를렌, NAME.소라카, NAME.올라, NAME.콜레트, NAME.미르노});
        addCH(TAG.작은체형, new NAME[] {NAME.시즈카, NAME.나나, NAME.아이리스, NAME.유이, NAME.이아, NAME.콜레트, NAME.로라, NAME.삼호});
        addCH(TAG.표준체형, new NAME[] {NAME.아이카, NAME.레오나, NAME.피오라, NAME.리츠키, NAME.미나요미, NAME.쥬노안, NAME.나프라라, NAME.토타라, NAME.호타루, NAME.가벨, NAME.아이린, NAME.도라, NAME.세바스, NAME.마를렌, NAME.소라카, NAME.사이렌, NAME.페트라, NAME.프레이, NAME.마누엘리, NAME.키쿄, NAME.카에데, NAME.올라, NAME.샤린, NAME.마티나, NAME.클레어, NAME.미르노, NAME.라미아, NAME.하피, NAME.안나, NAME.브란, NAME.노노카});
        addCH(TAG.빈유, new NAME[] {NAME.호타루, NAME.나나, NAME.아이리스, NAME.이아, NAME.페트라, NAME.콜레트});
        addCH(TAG.미유, new NAME[] {NAME.아이카, NAME.레오나, NAME.피오라, NAME.리츠키, NAME.미나요미, NAME.시즈카, NAME.브리트니, NAME.토타라, NAME.가벨, NAME.프리실라, NAME.타노시아, NAME.도라, NAME.세바스, NAME.마를렌, NAME.소라카, NAME.사이렌, NAME.프레이, NAME.마누엘리, NAME.키쿄, NAME.카에데, NAME.올라, NAME.샤린, NAME.마티나, NAME.클레어, NAME.로라, NAME.라미아, NAME.하피, NAME.안나, NAME.브란, NAME.노노카, NAME.삼호});
        addCH(TAG.거유, new NAME[] {NAME.쥬노안, NAME.나프라라, NAME.아이린, NAME.유이, NAME.미르노});
        addCH(TAG.병사, new NAME[] {NAME.사이렌, NAME.페트라, NAME.프레이, NAME.마누엘리, NAME.키쿄, NAME.카에데, NAME.올라, NAME.콜레트, NAME.샤린, NAME.마티나, NAME.클레어, NAME.로라, NAME.미르노, NAME.라미아, NAME.하피, NAME.안나, NAME.브란, NAME.노노카, NAME.징천사, NAME.복천사, NAME.삼호});
        addCH(TAG.데미지, new NAME[] {NAME.리츠키, NAME.쥬노안, NAME.토타라, NAME.가벨, NAME.나나, NAME.아이리스, NAME.유이, NAME.페트라, NAME.마누엘리, NAME.올라, NAME.콜레트, NAME.브란, NAME.노노카, NAME.삼호});
        addCH(TAG.보호, new NAME[] {NAME.레오나, NAME.나프라라, NAME.도라, NAME.사이렌, NAME.프레이, NAME.샤린, NAME.마티나, NAME.미르노, NAME.안나});
        addCH(TAG.방어, new NAME[] {NAME.레오나, NAME.나프라라, NAME.도라, NAME.사이렌, NAME.프레이, NAME.샤린, NAME.마티나, NAME.미르노, NAME.안나, NAME.브란});
        addCH(TAG.회복, new NAME[] {NAME.피오라, NAME.나프라라, NAME.호타루, NAME.타노시아, NAME.아이린, NAME.마를렌, NAME.이아, NAME.카에데, NAME.클레어, NAME.로라});
        addCH(TAG.방해, new NAME[] {NAME.시즈카, NAME.세바스, NAME.미르노, NAME.라미아, NAME.하피});
        addCH(TAG.쇠약, new NAME[] {NAME.미나요미, NAME.시즈카, NAME.브리트니, NAME.토타라, NAME.프리실라, NAME.소라카, NAME.키쿄, NAME.로라, NAME.하피});
        addCH(TAG.폭발력, new NAME[] {NAME.리츠키, NAME.브리트니, NAME.토타라, NAME.콜레트, NAME.노노카});
        addCH(TAG.생존력, new NAME[] {NAME.레오나, NAME.나프라라, NAME.도라, NAME.마티나, NAME.로라, NAME.징천사, NAME.삼호});
        addCH(TAG.전투, new NAME[] {NAME.미나요미, NAME.아이리스, NAME.유이});
        addCH(TAG.범위공격, new NAME[] {NAME.리츠키, NAME.브리트니, NAME.아이리스, NAME.페트라, NAME.샤린});
    }
    private void addCH(TAG tag, NAME[] names) {
        CH.put(tag, new HashSet<>());
        for(NAME t : names) CH.get(tag).add(t);
    }
}
