package jp.juggler.screenshotbutton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import jp.juggler.character.Ch;
import jp.juggler.character.SSRItem;

public class TenkaLeaderRecruit {
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
    private Map<String, Set<String>> TAG_CH;

    public ArrayList<SSRItem> getResult(String txt) {
        ArrayList<SSRItem> res = new ArrayList<>();
        txt = txt.replaceAll("리더", "").replaceAll("  ", " ").trim();
        String[] inputTags = txt.split(" ");
        PriorityQueue<SSRItem> result = calcAll(inputTags);

        Set<String> exist = new HashSet<>();
        while (!result.isEmpty()) {
            SSRItem ssr = result.poll();
            if (exist.contains(ssr.name)) continue;
            ssr.tag = "리더 " + ssr.tag;
            res.add(ssr);
            exist.add(ssr.name);
        }
        return res;
    }

    private PriorityQueue<SSRItem> calcAll(String[] inputTags) {
        PriorityQueue<SSRItem> chAll = new PriorityQueue<>(Collections.reverseOrder());
        for(int i = 0; i < 3; i++) for(int j = i+1; j < 4; j++) {
            String tag1 = inputTags[i], tag2 = inputTags[j];

            Set<String> tag1Set = TAG_CH.get(tag1), tag2Set = TAG_CH.get(tag2);
            if (tag1Set == null || tag2Set == null) continue;
            Set<String> retain = new HashSet<>(tag1Set);
            retain.retainAll(tag2Set);

            int retainSize = retain.size();
            if (retainSize == 0) continue;

            int percent = 100 / retainSize;
            for(String ch : retain) chAll.add(new SSRItem(percent, ch, tag1 + " " + tag2));
        }
        for(int i = 0; i < 4; i++) {
            String tag = inputTags[i];

            Set<String> retain = TAG_CH.get(tag);
            if (retain == null || retain.size() == 0) continue;
            int retainSize = retain.size();

            int percent = 100 / retainSize;
            for(String ch : retain) chAll.add(new SSRItem(percent, ch, tag));
        }
        int percent = 100 / CH_TAG.size();
        chAll.add(new SSRItem(percent, "나머지", ""));
        return chAll;
    }

    public TenkaLeaderRecruit() {
        CH_TAG = new HashSet<>();

        //SSR
        CH_TAG.add(new Ch("바알", false, "화속성 딜러 마족 표준체형 리더 데미지"));
        CH_TAG.add(new Ch("사탄", false, "암속성 탱커 마족 표준체형 거유 리더 방어 생존력 반격"));
        CH_TAG.add(new Ch("이블리스", false, "광속성 딜러 마족 표준체형 리더 데미지 생존력 범위공격"));
        CH_TAG.add(new Ch("살루시아", false, "풍속성 서포터 야인 표준체형 거유 리더 지원 폭발력"));
        CH_TAG.add(new Ch("란", false, "수속성 딜러 야인 작은체형 빈유 리더 데미지 폭발력 전투"));
        CH_TAG.add(new Ch("루루", false, "풍속성 힐러 인간 리더 회복"));
        CH_TAG.add(new Ch("밀레", false, "광속성 딜러 표준체형 리더 지원"));
        CH_TAG.add(new Ch("KS-Ⅷ", false, "암속성 딜러 표준체형 리더 데미지 폭발력 전투"));
        CH_TAG.add(new Ch("울타", false, "풍속성 탱커 인간 표준체형 리더 보호 방어 생존력"));
        CH_TAG.add(new Ch("아야네", false, "광속성 딜러 인간 표준체형 리더 폭발력 데미지"));
        CH_TAG.add(new Ch("무엘라", false, "풍속성 디스럽터 인간 표준체형 리더 지원 쇠약"));
        CH_TAG.add(new Ch("하쿠", false, "풍속성 힐러 야인 리더 회복 지원"));
        CH_TAG.add(new Ch("치즈루", false, "풍속성 딜러 마족 표준체형 리더 데미지 폭발력"));
        CH_TAG.add(new Ch("아르티아", false, "암속성 디스럽터 야인 빈유 리더 쇠약"));
        CH_TAG.add(new Ch("메스미나", false, "화속성 디스럽터 마족 빈유 리더 쇠약"));
        CH_TAG.add(new Ch("라티아", false, "암속성 딜러 마족 표준체형 거유 리더 데미지 폭발력"));
        CH_TAG.add(new Ch("슈텐", false, "화속성 딜러 야인 표준체형 빈유 리더 데미지"));
        CH_TAG.add(new Ch("테키", false, "풍속성 딜러 인간 표준체형 리더 데미지 쇠약"));
        CH_TAG.add(new Ch("모모", false, "수속성 딜러 마족 빈유 리더 데미지 폭발력"));
        CH_TAG.add(new Ch("파야", false, "화속성 힐러 마족 리더 회복 지원"));
        CH_TAG.add(new Ch("카시피나", false, "수속성 탱커 야인 표준체형 거유 리더 보호 방어 반격"));
        CH_TAG.add(new Ch("에피나", false, "암속성 서포터 인간 표준체형 빈유 리더 지원"));
        CH_TAG.add(new Ch("이노리", false, "풍속성 딜러 야인 표준체형 리더 데미지"));
        CH_TAG.add(new Ch("세라프", false, "수속성 힐러 야인 표준체형 리더 보호 회복 지원"));
        CH_TAG.add(new Ch("에밀리", false, "광속성 서포터 인간 표준체형 거유 리더 회복 지원"));
        CH_TAG.add(new Ch("안젤리카", false, "암속성 딜러 리더 데미지 폭발력 전투"));
        CH_TAG.add(new Ch("렌", false, "화속성 힐러 인간 표준체형 리더 회복 보호 지원"));
        CH_TAG.add(new Ch("미루", false, "화속성 딜러 리더 폭발력 생존력"));

        TAG_CH = new HashMap<>();
        for(String s : tagArray) TAG_CH.put(s, new HashSet<>());
        for(Ch c : CH_TAG) for(String tag : c.tags) TAG_CH.get(tag).add(c.name);
    }
}
