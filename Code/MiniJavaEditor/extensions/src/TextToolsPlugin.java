import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * 텍스트 도구 (텍스트 메뉴).
 * 선택한 글에 적용하고, 선택이 없으면 글 전체에 적용한다.
 * 줄 정렬, 중복 줄 제거, 공백/빈 줄 정리, 대소문자 변환, 줄 번호, 글 통계.
 */
public class TextToolsPlugin implements MiniJavaEditor.Plugin {

    private MiniJavaEditor.Context ctx;

    @Override
    public String name() {
        return "텍스트 도구";
    }

    @Override
    public String description() {
        return "줄 정렬, 중복 줄 제거, 공백/빈 줄 정리, 대소문자 변환, 줄 번호, 글 통계 (텍스트 메뉴). 선택이 없으면 전체에 적용";
    }

    @Override
    public void onLoad(MiniJavaEditor.Context context) {
        this.ctx = context;
        add("줄 정렬 (오름차순)", s -> mapLines(s, lines -> sorted(lines, false)));
        add("줄 정렬 (내림차순)", s -> mapLines(s, lines -> sorted(lines, true)));
        add("줄 순서 뒤집기", s -> mapLines(s, lines -> {
            List<String> r = new ArrayList<>(lines);
            java.util.Collections.reverse(r);
            return r;
        }));
        add("중복 줄 제거 (처음 것만 남김)", s -> mapLines(s, lines -> new ArrayList<>(new LinkedHashSet<>(lines))));
        add("줄 앞뒤 공백 제거", s -> mapLines(s, lines -> lines.stream().map(String::strip).toList()));
        add("연속된 빈 줄을 한 줄로", s -> mapLines(s, TextToolsPlugin::collapseBlankLines));
        add("빈 줄 모두 제거", s -> mapLines(s, lines -> lines.stream().filter(l -> !l.isBlank()).toList()));
        add("대문자로", s -> s.toUpperCase(Locale.ROOT));
        add("소문자로", s -> s.toLowerCase(Locale.ROOT));
        add("단어 첫 글자만 대문자로", TextToolsPlugin::titleCase);
        add("줄 번호 붙이기 (1: 형식)", s -> mapLines(s, lines -> {
            List<String> r = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) r.add((i + 1) + ": " + lines.get(i));
            return r;
        }));
        add("줄 번호 지우기 (1: 형식)", s -> mapLines(s, lines -> lines.stream().map(l -> l.replaceFirst("^\\d+: ", "")).toList()));
        ctx.addMenuItem("텍스트", "글 통계", this::showStats);
    }

    private void add(String label, UnaryOperator<String> f) {
        ctx.addMenuItem("텍스트", label, () -> {
            String selected = ctx.selectedText();
            if (selected.isEmpty()) ctx.replaceAll(f.apply(ctx.text()));
            else ctx.insertText(f.apply(selected));
        });
    }

    /** 글을 줄 목록으로 나눠 바꾼 뒤 다시 합친다 (끝의 줄바꿈 여부는 그대로 지킨다) */
    static String mapLines(String s, UnaryOperator<List<String>> f) {
        boolean endsWithNewline = s.endsWith("\n");
        String body = endsWithNewline ? s.substring(0, s.length() - 1) : s;
        List<String> lines = new ArrayList<>(List.of(body.split("\n", -1)));
        return String.join("\n", f.apply(lines)) + (endsWithNewline ? "\n" : "");
    }

    static List<String> sorted(List<String> lines, boolean descending) {
        Collator collator = Collator.getInstance(Locale.KOREAN); // 가나다 순과 알파벳 순
        List<String> r = new ArrayList<>(lines);
        r.sort(descending ? collator.reversed() : collator);
        return r;
    }

    static List<String> collapseBlankLines(List<String> lines) {
        List<String> r = new ArrayList<>();
        boolean previousBlank = false;
        for (String l : lines) {
            boolean blank = l.isBlank();
            if (blank && previousBlank) continue;
            r.add(l);
            previousBlank = blank;
        }
        return r;
    }

    static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean startOfWord = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(startOfWord ? Character.toTitleCase(c) : c);
            startOfWord = Character.isWhitespace(c);
        }
        return sb.toString();
    }

    private void showStats() {
        String target = ctx.selectedText();
        boolean selection = !target.isEmpty();
        if (!selection) target = ctx.text();
        int lines = target.isEmpty() ? 0 : target.split("\n", -1).length - (target.endsWith("\n") ? 1 : 0);
        int words = target.isBlank() ? 0 : target.trim().split("\\s+").length;
        int chars = target.codePointCount(0, target.length());
        long nonSpace = target.codePoints().filter(cp -> !Character.isWhitespace(cp)).count();
        int bytes = target.getBytes(StandardCharsets.UTF_8).length;
        ctx.showMessage((selection ? "선택한 글" : "전체 글") + "\n줄 " + lines + " · 단어 " + words
                + " · 글자 " + chars + " (공백 제외 " + nonSpace + ") · " + bytes + "바이트(UTF-8)");
    }
}
