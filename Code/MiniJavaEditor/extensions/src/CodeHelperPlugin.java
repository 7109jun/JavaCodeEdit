import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 코드 도우미 (코드 메뉴).
 * 주석 켜기/끄기, 줄 복제, 줄 위/아래 이동, import 정리(안 쓰는 import 삭제 + 정렬), TODO/FIXME 찾기.
 */
public class CodeHelperPlugin implements MiniJavaEditor.Plugin {

    private MiniJavaEditor.Context ctx;

    @Override
    public String name() {
        return "코드 도우미";
    }

    @Override
    public String description() {
        return "주석 켜기/끄기, 줄 복제와 이동, import 정리, TODO/FIXME 찾기 (코드 메뉴)";
    }

    @Override
    public void onLoad(MiniJavaEditor.Context context) {
        this.ctx = context;
        ctx.addMenuItem("코드", "주석 켜기/끄기", this::toggleComment);
        ctx.addMenuItem("코드", "줄 복제", this::duplicateLines);
        ctx.addMenuItem("코드", "줄 위로 이동", () -> moveLines(-1));
        ctx.addMenuItem("코드", "줄 아래로 이동", () -> moveLines(1));
        ctx.addMenuItem("코드", "import 정리", this::organizeImportsAction);
        ctx.addMenuItem("코드", "TODO / FIXME 찾기", this::findTodosAction);
    }

    // ------------------------------------------------------------------
    // 줄 단위 도구: 선택 영역(없으면 커서가 있는 줄)이 걸친 줄 전체에 적용한다
    // ------------------------------------------------------------------

    /** 선택 영역이 걸친 줄들의 [시작, 끝) 위치 (끝은 마지막 줄의 줄바꿈 앞) */
    static int[] lineRange(String text, int selStart, int selEnd) {
        int a = Math.max(0, Math.min(selStart, text.length()));
        int b = Math.max(a, Math.min(selEnd, text.length()));
        int start = a == 0 ? 0 : text.lastIndexOf('\n', a - 1) + 1;
        int end = b;
        if (b > a && text.charAt(b - 1) == '\n') end = b - 1; // 다음 줄 맨 앞에서 끝나는 선택은 그 줄을 포함하지 않는다
        int lineEnd = text.indexOf('\n', end);
        if (lineEnd < 0) lineEnd = text.length();
        return new int[]{start, lineEnd};
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return i;
    }

    /** 모든 줄이 // 주석이면 주석을 풀고, 아니면 가장 얕은 들여쓰기 위치에 // 를 붙인다. 빈 줄은 건드리지 않는다. */
    static String toggleComment(String block) {
        String[] lines = block.split("\n", -1);
        boolean any = false;
        boolean allCommented = true;
        int minIndent = Integer.MAX_VALUE;
        for (String l : lines) {
            if (l.isBlank()) continue;
            any = true;
            int ind = indentOf(l);
            minIndent = Math.min(minIndent, ind);
            if (!l.startsWith("//", ind)) allCommented = false;
        }
        if (!any) return block;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (i > 0) sb.append('\n');
            if (l.isBlank()) {
                sb.append(l);
            } else if (allCommented) {
                int ind = indentOf(l);
                String rest = l.substring(ind + 2);
                if (rest.startsWith(" ")) rest = rest.substring(1);
                sb.append(l, 0, ind).append(rest);
            } else {
                sb.append(l, 0, minIndent).append("// ").append(l.substring(minIndent));
            }
        }
        return sb.toString();
    }

    private void toggleComment() {
        String text = ctx.text();
        int[] r = lineRange(text, ctx.selectionStart(), ctx.selectionEnd());
        String block = text.substring(r[0], r[1]);
        String result = toggleComment(block);
        if (result.equals(block)) return;
        ctx.select(r[0], r[1]);
        ctx.insertText(result);
        ctx.select(r[0], r[0] + result.length());
    }

    private void duplicateLines() {
        String text = ctx.text();
        int s = ctx.selectionStart();
        int e = ctx.selectionEnd();
        int[] r = lineRange(text, s, e);
        String block = text.substring(r[0], r[1]);
        ctx.select(r[1], r[1]);
        ctx.insertText("\n" + block);
        int copyStart = r[1] + 1;
        if (s == e) ctx.select(copyStart + (s - r[0]), copyStart + (s - r[0])); // 커서만 있었다면 복제된 줄의 같은 위치로
        else ctx.select(copyStart, copyStart + block.length());                   // 선택이 있었다면 복제된 줄들을 선택
    }

    private void moveLines(int direction) {
        String text = ctx.text();
        int s = ctx.selectionStart();
        int e = ctx.selectionEnd();
        int[] r = lineRange(text, s, e);
        String block = text.substring(r[0], r[1]);
        if (direction < 0) {
            if (r[0] == 0) return;
            int prevStart = text.lastIndexOf('\n', r[0] - 2) + 1;
            String prev = text.substring(prevStart, r[0] - 1);
            ctx.select(prevStart, r[1]);
            ctx.insertText(block + "\n" + prev);
            int shift = prevStart - r[0];
            ctx.select(s + shift, e + shift);
        } else {
            if (r[1] >= text.length()) return;
            int nextEnd = text.indexOf('\n', r[1] + 1);
            if (nextEnd < 0) nextEnd = text.length();
            String next = text.substring(r[1] + 1, nextEnd);
            ctx.select(r[0], nextEnd);
            ctx.insertText(next + "\n" + block);
            int shift = next.length() + 1;
            ctx.select(s + shift, e + shift);
        }
    }

    // ------------------------------------------------------------------
    // import 정리
    // ------------------------------------------------------------------

    record ImportResult(String text, int removed) { }

    private static final Pattern IMPORT =
            Pattern.compile("(?m)^[ \\t]*import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;[ \\t]*(\\r?\\n)?");

    private record Imp(boolean isStatic, String name, boolean wildcard) {
        String line() {
            return "import " + (isStatic ? "static " : "") + name + (wildcard ? ".*" : "") + ";";
        }

        String simple() {
            return name.substring(name.lastIndexOf('.') + 1);
        }
    }

    /** 안 쓰는 import를 지우고, 중복을 없애고, java → javax → 나머지 → static 순서로 정렬한다 */
    static ImportResult organizeImports(String text) {
        Matcher m = IMPORT.matcher(text);
        List<int[]> spans = new ArrayList<>();
        List<Imp> imports = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
            imports.add(new Imp(m.group(1) != null, m.group(2), m.group(3) != null));
        }
        if (spans.isEmpty()) return new ImportResult(text, 0);

        StringBuilder restBuilder = new StringBuilder();
        int last = 0;
        for (int[] sp : spans) {
            restBuilder.append(text, last, sp[0]);
            last = sp[1];
        }
        restBuilder.append(text.substring(last));
        String rest = restBuilder.toString();

        LinkedHashSet<Imp> kept = new LinkedHashSet<>();
        int removed = 0;
        for (Imp imp : imports) {
            boolean used = imp.wildcard() || Pattern.compile("\\b" + Pattern.quote(imp.simple()) + "\\b").matcher(rest).find();
            if (used) kept.add(imp);
            else removed++;
        }

        List<String> java = new ArrayList<>();
        List<String> javax = new ArrayList<>();
        List<String> other = new ArrayList<>();
        List<String> statics = new ArrayList<>();
        for (Imp imp : kept) {
            if (imp.isStatic()) statics.add(imp.line());
            else if (imp.name().startsWith("java.")) java.add(imp.line());
            else if (imp.name().startsWith("javax.")) javax.add(imp.line());
            else other.add(imp.line());
        }
        StringBuilder block = new StringBuilder();
        for (List<String> group : List.of(java, javax, other, statics)) {
            if (group.isEmpty()) continue;
            if (block.length() > 0) block.append('\n');
            group.stream().sorted().forEach(l -> block.append(l).append('\n'));
        }

        StringBuilder result = new StringBuilder();
        last = 0;
        boolean inserted = false;
        int insertAt = spans.get(0)[0];
        for (int[] sp : spans) {
            result.append(text, last, sp[0]);
            if (!inserted) {
                result.append(block);
                inserted = true;
            }
            last = sp[1];
        }
        result.append(text.substring(last));

        // import 자리 주변에서 빈 줄이 3개 이상 겹치면 하나로 줄인다
        String all = result.toString();
        int ws = Math.max(0, insertAt - 2);
        int we = Math.min(all.length(), insertAt + block.length() + 3);
        String mid = all.substring(ws, we).replaceAll("\n{3,}", "\n\n");
        return new ImportResult(all.substring(0, ws) + mid + all.substring(we), removed);
    }

    private void organizeImportsAction() {
        String text = ctx.text();
        ImportResult r = organizeImports(text);
        if (r.text().equals(text)) {
            ctx.showMessage(IMPORT.matcher(text).find() ? "import가 이미 정리되어 있습니다." : "import 문이 없습니다.");
            return;
        }
        int fromEnd = text.length() - ctx.caretPosition(); // import는 글 위쪽에 있으므로 아래쪽 위치는 끝에서부터 잰다
        ctx.replaceAll(r.text());
        int pos = Math.max(0, r.text().length() - fromEnd);
        ctx.select(pos, pos);
        if (r.removed() > 0) ctx.showMessage("사용하지 않는 import " + r.removed() + "개를 지웠습니다.");
    }

    // ------------------------------------------------------------------
    // TODO / FIXME 찾기
    // ------------------------------------------------------------------

    private static final Pattern TODO = Pattern.compile("\\b(TODO|FIXME)\\b");

    /** 프로젝트의 .java 파일에서 TODO/FIXME가 있는 줄을 "상대경로:줄  내용" 형태로 모은다 */
    static List<String> scanProject(File root, File currentFile, String currentText) throws IOException {
        List<String> hits = new ArrayList<>();
        Path rootPath = root.toPath();
        try (Stream<Path> s = Files.walk(rootPath, 8)) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> {
                        for (Path part : rootPath.relativize(p)) {
                            String n = part.toString();
                            if (n.startsWith(".") || n.equals("node_modules")) return false;
                        }
                        return true;
                    })
                    .limit(5000)
                    .sorted()
                    .toList();
            for (Path p : files) {
                List<String> lines;
                if (currentFile != null && p.toAbsolutePath().normalize().equals(currentFile.toPath().toAbsolutePath().normalize())) {
                    lines = currentText.lines().toList(); // 저장하지 않은 수정까지 반영
                } else {
                    try {
                        lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                    } catch (IOException | RuntimeException e) {
                        continue;
                    }
                }
                for (int i = 0; i < lines.size(); i++) {
                    if (TODO.matcher(lines.get(i)).find()) {
                        hits.add(rootPath.relativize(p).toString().replace(File.separatorChar, '/') + ":" + (i + 1) + "  " + lines.get(i).strip());
                    }
                }
            }
        }
        return hits;
    }

    static List<String> scanText(String text) {
        List<String> hits = new ArrayList<>();
        List<String> lines = text.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            if (TODO.matcher(lines.get(i)).find()) hits.add("(현재 글):" + (i + 1) + "  " + lines.get(i).strip());
        }
        return hits;
    }

    private void findTodosAction() {
        try {
            File root = ctx.projectRoot();
            List<String> hits = root != null ? scanProject(root, ctx.currentFile(), ctx.text()) : scanText(ctx.text());
            if (hits.isEmpty()) {
                ctx.showMessage("TODO / FIXME 가 없습니다.");
                return;
            }
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "minijava-reports");
            Files.createDirectories(dir);
            // 같은 이름의 파일이 이미 탭에 열려 있으면 새 내용이 안 보이므로, 시각이 들어간 이름을 쓴다
            Path report = dir.resolve("TODO-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss-SSS")) + ".txt");
            Files.writeString(report, "TODO / FIXME " + hits.size() + "개\n\n" + String.join("\n", hits) + "\n");
            ctx.openFile(report.toFile());
        } catch (IOException e) {
            ctx.showMessage("찾는 중 오류: " + e.getMessage());
        }
    }
}
