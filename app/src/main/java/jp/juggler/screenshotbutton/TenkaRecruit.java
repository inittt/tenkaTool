package jp.juggler.screenshotbutton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jp.juggler.character.Ch;

public class TenkaRecruit {
    private String[] tagArray = new String[] {
            "화속성","수속성","풍속성","광속성","암속성",
            "딜러","탱커","힐러","디스럽터","서포터",
            "인간","마족","야인",
            "작은체형","표준체형",
            "빈유","미유","거유",
            "병사","정예","리더",
            "데미지","보호","방어","회복","방해","지원","쇠약",
            "폭발력","생존력", "전투","범위공격","반격"
    };
    private Set<Ch> CH_TAG;
    private Map<String, Set<Ch>> TAG_CH;

    public String[] getResult(String txt) {
        String SRTag = null;
        String[] cur = txt.split(" "), curTags = new String[5];

        if (cur.length != 5) return new String[] {"인식 오류", ""};
        for(int i = 0; i < 5; i++) {
            String t = cur[i];
            if (!TAG_CH.containsKey(t)) return new String[] {"인식 오류", ""};
            if ("리더".equals(t)) return new String[] {"SSR 리더", ""};
            if ("정예".equals(t)) return new String[] {"SR(확정) 정예", ""};
            curTags[i] = t;
        }
        return getConfirmTag(curTags);
    }
    private String[] getConfirmTag(String[] tg) {
        ArrayList<ArrayList<String>> combList = new ArrayList<>();
        for(int a = 0; a < 4; a++) for(int b = a+1; b < 5; b++) {
            ArrayList<String> c2 = new ArrayList<>(), c3 = new ArrayList<>();
            for(int i = 0; i < 5; i++) {
                if (a == i || b == i) c2.add(tg[i]);
                else c3.add(tg[i]);
            }
            combList.add(c2);
            combList.add(c3);
        }
        for(int i = 0; i < 5; i++) {
            ArrayList<String> oneList = new ArrayList<>();
            oneList.add(tg[i]);
            combList.add(oneList);
        }
        int percent = 0, listSize = 0;
        String res = "";
        for(ArrayList<String> tgs : combList) {
            int curPercent = srCalc(tgs);
            if (curPercent == 0) continue;

            StringBuilder sb = new StringBuilder();
            for(String t : tgs) sb.append(t).append(" ");
            String curTags = sb.toString();

            boolean isHigh = percent < curPercent;
            boolean isEqualAndLong = percent == curPercent && listSize <= tgs.size();
            if (isHigh || isEqualAndLong) {
                percent = curPercent;
                listSize = tgs.size();
                res = curTags;
            }
        }
        if (percent == 100) return new String[] {"SR(확정) ", res};
        return new String[] {"SR(" + percent + "%) ", res};
    }
    private int srCalc(ArrayList<String> tgs) {
        ArrayList<Set<Ch>> results = new ArrayList<>();
        for(String t : tgs) results.add(TAG_CH.get(t));
        Set<Ch> retain = new HashSet<>(results.get(0));
        for(int i = 1; i < results.size(); i++) retain.retainAll(results.get(i));
        if (retain.size() == 0) return 0;

        int srCnt = 0, cnt = 0;
        for(Ch n : retain) {
            cnt++;
            if (n.isSR) srCnt++;
        }
        return (srCnt * 100) / cnt;
    }
    public TenkaRecruit() {
        CH_TAG = new HashSet<>();
        //SR
        CH_TAG.add(new Ch("아이카", true, "암속성 서포터 마족 표준체형 미유 정예 지원"));
        CH_TAG.add(new Ch("레오나", true, "수속성 탱커 인간 표준체형 미유 정예 보호 방어 생존력"));
        CH_TAG.add(new Ch("피오라", true, "광속성 힐러 인간 표준체형 미유 정예 회복"));
        CH_TAG.add(new Ch("리츠키", true, "풍속성 딜러 인간 표준체형 미유 정예 데미지 폭발력 범위공격"));
        CH_TAG.add(new Ch("미나요미", true, "화속성 딜러 야인 표준체형 미유 정예 쇠약 전투"));
        CH_TAG.add(new Ch("시즈카", true, "수속성 디스럽터 야인 작은체형 미유 정예 방해 쇠약"));
        CH_TAG.add(new Ch("쥬노안", true, "암속성 딜러 인간 표준체형 거유 정예 데미지 지원"));
        CH_TAG.add(new Ch("브리트니", true, "광속성 디스럽터 인간 미유 정예 지원 쇠약 폭발력 범위공격"));
        CH_TAG.add(new Ch("나프라라", true, "풍속성 탱커 마족 표준체형 거유 정예 보호 방어 회복 생존력"));
        CH_TAG.add(new Ch("토타라", true, "광속성 딜러 인간 표준체형 미유 정예 데미지 쇠약 폭발력"));
        CH_TAG.add(new Ch("호타루", true, "수속성 힐러 인간 표준체형 빈유 정예 회복 지원"));
        CH_TAG.add(new Ch("가벨", true, "풍속성 딜러 인간 표준체형 미유 정예 데미지"));
        CH_TAG.add(new Ch("프리실라", true, "암속성 디스럽터 야인 미유 정예 쇠약"));
        CH_TAG.add(new Ch("타노시아", true, "광속성 서포터 야인 미유 정예 회복"));

        //NR
        CH_TAG.add(new Ch("아이린", false, "광속성 힐러 인간 표준체형 거유 회복"));
        CH_TAG.add(new Ch("나나", false, "풍속성 딜러 마족 작은체형 빈유 데미지"));
        CH_TAG.add(new Ch("아이리스", false, "화속성 딜러 야인 작은체형 빈유 데미지 전투 범위공격"));
        CH_TAG.add(new Ch("도라", false, "풍속성 탱커 야인 표준체형 미유 보호 방어 생존력"));
        CH_TAG.add(new Ch("세바스", false, "암속성 디스럽터 마족 표준체형 미유 방해"));
        CH_TAG.add(new Ch("마를렌", false, "수속성 힐러 야인 표준체형 미유 회복"));
        CH_TAG.add(new Ch("유이", false, "화속성 딜러 인간 작은체형 거유 데미지 전투"));
        CH_TAG.add(new Ch("소라카", false, "암속성 디스럽터 야인 표준체형 미유 쇠약"));
        CH_TAG.add(new Ch("이아", false, "광속성 힐러 인간 작은체형 빈유 회복"));
        CH_TAG.add(new Ch("사이렌", false, "암속성 탱커 인간 표준체형 미유 병사 보호 방어"));
        CH_TAG.add(new Ch("페트라", false, "광속성 딜러 인간 표준체형 빈유 병사 데미지 범위공격"));
        CH_TAG.add(new Ch("프레이", false, "광속성 탱커 마족 표준체형 미유 병사 보호 방어"));
        CH_TAG.add(new Ch("마누엘리", false, "암속성 딜러 마족 표준체형 미유 병사 데미지"));
        CH_TAG.add(new Ch("키쿄", false, "화속성 디스럽터 인간 표준체형 미유 병사 쇠약"));
        CH_TAG.add(new Ch("카에데", false, "풍속성 힐러 인간 표준체형 미유 병사 회복"));
        CH_TAG.add(new Ch("올라", false, "풍속성 딜러 야인 표준체형 미유 병사 데미지"));
        CH_TAG.add(new Ch("콜레트", false, "수속성 딜러 야인 작은체형 빈유 병사 데미지 폭발력"));
        CH_TAG.add(new Ch("샤린", false, "화속성 탱커 인간 표준체형 미유 병사 보호 방어 범위공격"));
        CH_TAG.add(new Ch("마티나", false, "광속성 탱커 인간 표준체형 미유 병사 보호 방어 생존력"));
        CH_TAG.add(new Ch("클레어", false, "광속성 힐러 인간 표준체형 미유 병사 회복"));
        CH_TAG.add(new Ch("로라", false, "수속성 디스럽터 마족 작은체형 미유 병사 회복 쇠약 생존력"));
        CH_TAG.add(new Ch("미르노", false, "풍속성 탱커 야인 표준체형 거유 병사 보호 방어 방해"));
        CH_TAG.add(new Ch("라미아", false, "화속성 디스럽터 마족 표준체형 미유 병사 방해 쇠약"));
        CH_TAG.add(new Ch("하피", false, "풍속성 디스럽터 마족 표준체형 미유 병사 방해 쇠약"));
        CH_TAG.add(new Ch("안나", false, "화속성 탱커 인간 표준체형 미유 병사 보호 방어"));
        CH_TAG.add(new Ch("브란", false, "풍속성 딜러 인간 표준체형 미유 병사 데미지 방어"));
        CH_TAG.add(new Ch("노노카", false, "수속성 딜러 인간 표준체형 미유 병사 데미지 폭발력"));
        CH_TAG.add(new Ch("징천사", false, "수속성 탱커 병사 생존력"));
        CH_TAG.add(new Ch("복천사", false, "수속성 힐러 병사"));
        CH_TAG.add(new Ch("3호", false, "광속성 딜러 작은체형 미유 병사 데미지 생존력"));
        CH_TAG.add(new Ch("세실", false, "풍속성 딜러 야인 표준체형 거유 병사 데미지 폭발력"));
        CH_TAG.add(new Ch("무무", false, "암속성 디스럽터 표준체형 미유 병사 보호 방해 생존력"));

        TAG_CH = new HashMap<>();
        for(String s : tagArray) TAG_CH.put(s, new HashSet<>());
        for(Ch c : CH_TAG) for(String tag : c.tags) TAG_CH.get(tag).add(c);
    }
}
