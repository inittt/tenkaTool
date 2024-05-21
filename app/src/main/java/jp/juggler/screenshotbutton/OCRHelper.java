package jp.juggler.screenshotbutton;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class OCRHelper {
    private TextRecognizer textRecognizer;
    private final int SIMILAR = 2;
    private Set<String> corr = new HashSet<>(Arrays.asList(
            "화속성","수속성","풍속성","광속성","암속성",
            "딜러","탱커","힐러","디스럽터","서포터",
            "인간","마족","야인",
            "작은체형","표준체형",
            "빈유","미유","거유",
            "병사","정예","리더",
            "데미지","보호","방어","회복","방해","지원","쇠약",
            "폭발력","생존력","전투하면할수록","범위공격","반격",

            "작은", "표준", "전투하면", "범위",
            "체형", "할수록", "공격"
    ));

    public OCRHelper(Context context) {
        textRecognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
    }
    public String recognizeTime(Bitmap bitmap) {
        String res = null;
        for(int i = 0; i < 10; i++) {
            res = recognizeText(bitmap);
            if (!"".equals(res)) break;
        }
        if (res == null) return null;
        res = res.trim();
        if ("90".equals(res)) return "6";
        if ("60".equals(res)) return "9";
        res = res.replaceAll("[^1-9]", "");
        return res;
    }
    public String recognizeTags(Bitmap bitmap) {
        String res = null;
        for(int i = 0; i < 10; i++) {
            res = recognizeText(bitmap);
            if (!"".equals(res)) break;
        }
        if (res == null) return "";

        res = res.replaceAll("[^가-힣]", " ");
        res = res.replaceAll("\\s+", " ");
        String[] sList = res.split(" ");

        StringBuilder sb = new StringBuilder();
        for(String s : sList) {
            if (s.length() < 2) continue;

            String calcTag = calcText(s);
            if (calcTag == null) continue;
            if ("체형".equals(calcTag) || "할수록".equals(calcTag) || "공격".equals(calcTag)) continue;
            if ("작은".equals(calcTag)) calcTag = "작은체형";
            if ("표준".equals(calcTag)) calcTag = "표준체형";
            if ("범위".equals(calcTag)) calcTag = "범위공격";
            if ("전투하면".equals(calcTag) || "전투하면할수록".equals(calcTag)) calcTag = "전투";
            sb.append(calcTag).append(" ");
        }
        return sb.toString().trim();
    }
    private String recognizeText(Bitmap bitmap) {
        String res = "";
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Task<Text> task = textRecognizer.process(image);
        try {
            Text result = Tasks.await(task);
            Log.i("ocr성공", res = result.getText());
        } catch (ExecutionException | InterruptedException e) {
            Log.i("ocr실패", "");
        }
        return res;
    }
    private String calcText(String curTag) {
        int minSimilar = 50;
        String res = null;
        for(String ss : corr) {
            int sim = similar(curTag, ss);
            if (sim <= SIMILAR && sim < minSimilar) {
                minSimilar = sim;
                res = ss;
            }
        }
        return res;
    }

    private final int HANGEUL_BASE = 0xAC00;    // '가'
    private final int HANGEUL_END = 0xD7AF;
    // 이하 cho, jung, jong은 계산 결과로 나온 자모에 대해 적용
    private final int CHO_BASE = 0x1100;
    private final int JUNG_BASE = 0x1161;
    private final int JONG_BASE = (int)0x11A8 - 1;
    // 이하 ja, mo는 단독으로 입력된 자모에 대해 적용
    private final int JA_BASE = 0x3131;
    private final int MO_BASE = 0x314F;
    private String splitKor(String text) {
        List<Character> list = new ArrayList<>();

        for(char c : text.toCharArray()) {
            if((c <= 10 && c <= 13) || c == 32) {
                list.add(c);
                continue;
            } else if (c >= JA_BASE && c <= JA_BASE + 36) {
                list.add(c);
                continue;
            } else if (c >= MO_BASE && c <= MO_BASE + 58) {
                list.add((char)0);
                continue;
            } else if (c >= HANGEUL_BASE && c <= HANGEUL_END){
                int choInt = (c - HANGEUL_BASE) / 28 / 21;
                int jungInt = ((c - HANGEUL_BASE) / 28) % 21;
                int jongInt = (c - HANGEUL_BASE) % 28;
                char cho = (char) (choInt + CHO_BASE);
                char jung = (char) (jungInt + JUNG_BASE);
                char jong = jongInt != 0 ? (char) (jongInt + JONG_BASE) : 0;

                list.add(cho);
                list.add(jung);
                if (jong != 0) list.add(jong);
            } else {
                list.add(c);
            }

        }
        StringBuilder sb = new StringBuilder();
        for(char c : list) sb.append(c);
        return sb.toString();
    }

    private int calculateDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            throw new IllegalArgumentException("Input strings cannot be null.");
        }

        int m = s1.length();
        int n = s2.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    int insert = dp[i][j - 1] + 1;
                    int delete = dp[i - 1][j] + 1;
                    int replace = dp[i - 1][j - 1] + 1;
                    dp[i][j] = Math.min(insert, Math.min(delete, replace));
                }
            }
        }

        return dp[m][n];
    }

    private int similar(String s1, String s2) {
        s1 = splitKor(s1);
        s2 = splitKor(s2);
        int distance = calculateDistance(s1, s2);
        Log.i("dist", s1 + " " + s2 + " " + distance);
        return distance;
    }
}
