import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Java 서식 정리 (코드 > 서식 정리)
 *
 * 하는 일: 줄 앞의 들여쓰기(4칸)를 괄호 깊이에 맞게 고치고, 줄 끝 공백을 없애고, 연속된 빈 줄을 한 줄로 줄인다.
 * 하지 않는 일: 공백이 아닌 글자는 하나도 바꾸지 않는다. 줄바꿈 위치도 바꾸지 않는다.
 * 문자열 안, 주석 안, 텍스트 블록(""") 안의 내용은 건드리지 않는다.
 */
public class JavaFormatterPlugin implements MiniJavaEditor.Plugin {

    @Override
    public String name() {
        return "Java 서식 정리";
    }

    @Override
    public String description() {
        return "들여쓰기(4칸), 줄 끝 공백, 연속된 빈 줄을 정리합니다 (코드 > 서식 정리). 코드 글자는 바꾸지 않습니다";
    }

    @Override
    public void onLoad(MiniJavaEditor.Context ctx) {
        ctx.addMenuItem("코드", "서식 정리 (Java)", () -> run(ctx));
    }

    private static void run(MiniJavaEditor.Context ctx) {
        String source = ctx.text();
        if (source.isBlank()) return;
        String formatted = format(source);
        if (formatted.equals(source)) {
            ctx.showMessage("서식이 이미 정리되어 있습니다.");
            return;
        }
        // 서식 정리는 공백만 바꾸므로, "공백이 아닌 글자가 몇 개 앞에 있었는가"로 커서 위치를 되찾을 수 있다
        int keep = nonWhitespaceBefore(source, ctx.caretPosition());
        ctx.replaceAll(formatted);
        int pos = positionAfterNonWhitespace(formatted, keep);
        ctx.select(pos, pos);
    }

    static int nonWhitespaceBefore(String s, int end) {
        int n = 0;
        for (int i = 0; i < Math.min(end, s.length()); i++) {
            if (!Character.isWhitespace(s.charAt(i))) n++;
        }
        return n;
    }

    static int positionAfterNonWhitespace(String s, int count) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (n == count) return i;
            if (!Character.isWhitespace(s.charAt(i))) n++;
        }
        return s.length();
    }

    // ------------------------------------------------------------------
    // 서식 정리 본체
    //
    // 규칙 (IDE에서 흔히 쓰는 방식):
    //  - 블록({ })의 안쪽은, 그 블록을 연 줄의 들여쓰기에서 4칸 더 들어간다. 닫는 } 는 여는 줄과 같은 칸.
    //  - 배열 초기값({ }) 안쪽은 8칸 더 들어간다.
    //  - 한 문장이 여러 줄로 이어질 때, 이어지는 줄은 문장 시작에서 8칸. 그 줄 안의 연산자(+ && .)로 또 이어지면 8칸 더.
    //  - 중괄호 없는 if/for/while/else 본문은 4칸. 옛 방식 case: 의 본문은 4칸.
    // ------------------------------------------------------------------

    private static final int INDENT = 4;
    private static final int CONTINUATION = 8;
    private static final Pattern CONTROL = Pattern.compile("^(if|for|while|else\\s+if)\\s*\\(.*");

    /** 열려 있는 괄호 하나 ( { [ 와, 그것을 연 줄의 정보 */
    private static final class Open {
        final char c;
        boolean arrayInit;       // { 가 배열 초기값인가
        boolean statementIndent; // ") {" 처럼 문장 머리를 닫는 {: 본문은 이어진 줄이 아니라 문장 시작에 맞춘다
        int indent = -1;         // 이 괄호를 연 줄의 들여쓰기 (그 줄 처리가 끝나면 채운다)
        int lvl;                 // 그 줄의 이어쓰기 단계
        int stmtIndent;          // 그 줄이 속한 문장의 첫 줄 들여쓰기
        boolean opCont;          // 그 줄이 연산자(. + && 등)로 이어진 줄이었는가
        Open stmtRef;            // statementIndent일 때, 문장 머리를 닫은 ( 괄호

        Open(char c) {
            this.c = c;
        }
    }

    /** 줄과 줄 사이에 이어지는 상태 */
    private static final class State {
        final Deque<Open> stack = new ArrayDeque<>();
        int braces = 0;                                    // 열려 있는 { 의 개수
        boolean inBlock;                                   // /* */ 주석 안
        boolean inText;                                    // """ 텍스트 블록 안
        final Set<Integer> caseDepths = new HashSet<>();   // 옛 방식 case: 본문을 한 칸 더 들여쓸 깊이들
        boolean prevContinues;                             // 앞 줄이 연산자로 끝나서 이 줄로 이어지는가
        boolean prevOpCont;                                // 앞 줄이 연산자로 이어진 줄이었는가
        int prevLvl;                                       // 앞 줄의 이어쓰기 단계
        boolean pendingBody;                               // if/for/while 헤더 다음 줄이 중괄호 없는 본문인가
        boolean inControlHeader;                           // if (...) 헤더가 여러 줄에 걸쳐 있는가
        int stmtOffset;                                    // 지금 문장의 첫 줄이 가진 추가 들여쓰기 (본문/case)
        int stmtIndent;                                    // 지금 문장의 첫 줄 들여쓰기
        boolean stmtIsCase;                                // 지금 문장이 case/default 줄로 시작했는가
        int commentIndent;                                 // 마지막 /* 주석이 시작한 줄의 들여쓰기
        Open lastClosedParen;                              // 이 줄에서 가장 최근에 닫힌 ( 
    }

    public static String format(String source) {
        if (source.isBlank()) return source;
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        State st = new State();
        List<String> out = new ArrayList<>();
        int blankRun = 0;

        for (String line : lines) {
            boolean startedInText = st.inText;
            boolean startedInBlock = st.inBlock;
            Deque<Open> before = new ArrayDeque<>(st.stack);
            int bracesBefore = st.braces;
            String code = scan(line, st).strip();

            // 텍스트 블록 안의 줄(닫는 """ 줄 포함)은 그대로 둔다: 여기의 공백은 문자열 내용이다
            if (startedInText) {
                out.add(line);
                blankRun = 0;
                continue;
            }
            String text = line.strip();

            // /* */ 주석 안의 줄: * 로 시작하면 주석 시작 줄에 맞춰 정렬하고, 아니면 그대로 둔다
            if (startedInBlock) {
                if (text.isEmpty()) out.add("");
                else if (text.startsWith("*")) out.add(" ".repeat(st.commentIndent + 1) + text);
                else out.add(rtrim(line));
                continue;
            }

            if (text.isEmpty()) {
                blankRun++;
                if (blankRun <= 1) out.add("");
                continue;
            }
            blankRun = 0;

            // 줄 앞쪽의 닫는 괄호(} ) ])를 먼저 처리한 상태에서 들여쓰기를 정한다
            Deque<Open> after = new ArrayDeque<>(before);
            int depth = bracesBefore;
            Open firstPopped = null;
            for (int k = 0; k < code.length() && isCloser(code.charAt(k)); k++) {
                if (!after.isEmpty() && after.peek().c == openerOf(code.charAt(k))) {
                    Open popped = after.pop();
                    if (firstPopped == null) firstPopped = popped;
                    if (popped.c == '{') depth--;
                }
            }

            boolean commentOnly = code.isEmpty();
            boolean label = !commentOnly && isCaseLabel(code);
            int caseExtra = (!label && st.caseDepths.contains(depth)) ? INDENT : 0;
            int blockBase = blockBase(after);
            Open top = after.peek();
            Open topParen = (top != null && top.c != '{') ? top : null;
            int indent;
            int lvl = 0;
            boolean opCont = false;

            if (commentOnly) {
                if (st.prevContinues) {
                    indent = blockBase + st.stmtOffset + CONTINUATION * (st.prevOpCont ? st.prevLvl : st.prevLvl + 1);
                } else if (topParen != null) {
                    indent = blockBase + st.stmtOffset + CONTINUATION * (topParen.lvl + 1);
                } else {
                    indent = blockBase + caseExtra;
                }
            } else if (firstPopped != null) {
                indent = firstPopped.indent; // 닫는 괄호는 그 괄호를 연 줄과 같은 칸
                // 문장 수준에서 블록이 끝난 줄 (} else { 등). })처럼 앞에서 연 ( 를 닫는 줄은 호출이 이어지는 중이다
                if (topParen == null && (st.lastClosedParen == null || st.lastClosedParen.indent < 0)) {
                    st.stmtIndent = indent;
                    st.stmtOffset = Math.max(0, indent - blockBase);
                }
            } else {
                opCont = startsWithOperator(code) || st.prevContinues;
                if (opCont) lvl = st.prevOpCont ? st.prevLvl : st.prevLvl + 1;
                else if (topParen != null) lvl = topParen.lvl + 1;

                if (lvl > 0) {
                    indent = blockBase + st.stmtOffset + CONTINUATION * lvl;
                } else {
                    int offset = caseExtra + (st.pendingBody && code.charAt(0) != '{' ? INDENT : 0);
                    st.stmtOffset = offset;
                    indent = blockBase + offset;
                    st.stmtIndent = indent;
                    st.stmtIsCase = code.startsWith("case ") || code.startsWith("default");
                }
            }

            if (!commentOnly) {
                // 이 줄에서 새로 열린 괄호에 줄 정보를 적어 둔다
                for (Open o : st.stack) {
                    if (o.indent >= 0) break;
                    o.stmtIndent = st.stmtIndent;
                    o.indent = o.statementIndent ? (o.stmtRef != null ? o.stmtRef.stmtIndent : st.stmtIndent) : indent;
                    o.lvl = lvl;
                    o.opCont = opCont;
                }
                // 다음 줄을 위한 상태 갱신. 앞선 줄에서 연 괄호를 이 줄에서 닫았다면, 이어지는 식은 그 괄호를 연 줄에서 시작한 것이다
                boolean viaClosed = st.lastClosedParen != null && st.lastClosedParen.indent >= 0;
                st.prevLvl = viaClosed ? st.lastClosedParen.lvl : lvl;
                st.prevOpCont = viaClosed ? st.lastClosedParen.opCont : opCont;
                st.prevContinues = endsWithOperator(code);
                updateControl(st, stripLeadingClosers(code), code);
                if (label) st.caseDepths.add(depth);
            }

            out.add(" ".repeat(Math.max(0, indent)) + text);
            if (!startedInBlock && st.inBlock) st.commentIndent = Math.max(0, indent);
        }

        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return String.join("\n", out) + "\n";
    }

    /** 가장 가까이 열려 있는 { 의 안쪽 들여쓰기 (없으면 0) */
    private static int blockBase(Deque<Open> stack) {
        for (Open o : stack) {
            if (o.c == '{') return o.indent + (o.arrayInit ? CONTINUATION : INDENT);
        }
        return 0;
    }

    /**
     * 한 줄을 읽으면서 상태를 갱신하고, 문자열/문자/주석을 뺀 "코드 부분"을 돌려준다.
     * 문자열과 문자는 "" 와 '' 로 줄여서 돌려준다 (괄호나 // 가 들어 있어도 속지 않도록).
     */
    private static String scan(String line, State st) {
        st.lastClosedParen = null;
        StringBuilder code = new StringBuilder();
        int n = line.length();
        int i = 0;
        while (i < n) {
            char c = line.charAt(i);
            if (st.inBlock) {
                int e = line.indexOf("*/", i);
                if (e < 0) {
                    i = n;
                } else {
                    st.inBlock = false;
                    i = e + 2;
                }
                continue;
            }
            if (st.inText) {
                int j = i;
                boolean closed = false;
                while (j < n) {
                    char d = line.charAt(j);
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    if (line.startsWith("\"\"\"", j)) {
                        closed = true;
                        break;
                    }
                    j++;
                }
                if (closed) {
                    st.inText = false;
                    code.append("\"\"");
                    i = j + 3;
                } else {
                    i = n;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') break;
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
                st.inBlock = true;
                i += 2;
                continue;
            }
            if (line.startsWith("\"\"\"", i)) {
                st.inText = true;
                code.append("\"\"");
                i += 3;
                // 여는 """ 뒤의 나머지는 텍스트 블록 안으로 본다 (규칙상 공백뿐이다)
                continue;
            }
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n) {
                    char d = line.charAt(j);
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    if (d == c) break;
                    j++;
                }
                code.append(c).append(c);
                i = Math.min(j + 1, n);
                continue;
            }
            if (c == '{' || c == '(' || c == '[') {
                Open o = new Open(c);
                if (c == '{') {
                    char p = lastSignificant(code);
                    Open parent = st.stack.peek();
                    o.arrayInit = p == '=' || p == ']' || p == ',' || p == '('
                            || (p == '{' && parent != null && parent.arrayInit);
                    boolean arrow = endsWithArrow(code);
                    boolean afterHeader = p == ')' && st.lastClosedParen != null
                            && st.lastClosedParen.indent >= 0 && st.lastClosedParen.lvl == 0;
                    o.statementIndent = !o.arrayInit && (afterHeader || Character.isLetterOrDigit(p) || p == '_'
                            || (p == '>' && !arrow) || (arrow && st.stmtIsCase));
                    if (afterHeader) o.stmtRef = st.lastClosedParen;
                    st.braces++;
                }
                st.stack.push(o);
            } else if ((c == '}' || c == ')' || c == ']') && !st.stack.isEmpty()) {
                Open opened = st.stack.pop();
                if (opened.c == '{') {
                    st.caseDepths.remove(st.braces); // 이 중괄호 안의 case 상태는 끝난다
                    st.braces--;
                } else if (opened.c == '(') {
                    st.lastClosedParen = opened;
                }
            }
            code.append(c);
            i++;
        }
        return code.toString();
    }

    private static boolean endsWithArrow(CharSequence code) {
        int e = code.length();
        while (e > 0 && Character.isWhitespace(code.charAt(e - 1))) e--;
        return e >= 2 && code.charAt(e - 1) == '>' && code.charAt(e - 2) == '-';
    }

    private static char lastSignificant(CharSequence code) {
        for (int i = code.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(code.charAt(i))) return code.charAt(i);
        }
        return 0;
    }

    private static boolean isCloser(char c) {
        return c == '}' || c == ')' || c == ']';
    }

    private static char openerOf(char closer) {
        return closer == '}' ? '{' : closer == ')' ? '(' : '[';
    }

    private static String stripLeadingClosers(String code) {
        int k = 0;
        while (k < code.length() && (isCloser(code.charAt(k)) || code.charAt(k) == ' ')) k++;
        return code.substring(k);
    }

    /** 옛 방식 switch의 "case 값:" 또는 "default:" 줄 (화살표 -> 방식은 제외) */
    private static boolean isCaseLabel(String code) {
        if (!(code.startsWith("case ") || code.startsWith("default"))) return false;
        if (code.contains("->")) return false;
        return code.endsWith(":") || code.endsWith(": {") || code.endsWith(":{");
    }

    private static boolean startsWithOperator(String code) {
        for (String op : new String[]{".", "?", "&&", "||", "==", "!=", "<=", ">=", ": ", "+ ", "- ", "* ", "/ ",
                "% ", "& ", "| ", "^ ", "= ", "instanceof "}) {
            if (code.startsWith(op)) return true;
        }
        return false;
    }

    private static boolean endsWithOperator(String code) {
        if (code.endsWith("++") || code.endsWith("--")) return false;
        if (code.startsWith("case ") && code.endsWith(",")) return true; // case A,\n B ->
        for (String op : new String[]{"&&", "||", "->", "+", "-", "*", "/", "%", "=", "?", "&", "|", "^", "."}) {
            if (code.endsWith(op)) return true;
        }
        return false;
    }

    /** if/for/while/else 헤더가 끝났을 때, 다음 줄이 중괄호 없는 본문인지 기억한다 */
    private static void updateControl(State st, String core, String code) {
        Open top = st.stack.peek();
        boolean topIsParen = top != null && top.c != '{';
        if (!st.inControlHeader && (CONTROL.matcher(core).matches() || core.equals("else") || core.equals("do"))) {
            st.inControlHeader = true;
        }
        if (st.inControlHeader) {
            if (!topIsParen) {
                st.inControlHeader = false;
                char last = code.charAt(code.length() - 1);
                st.pendingBody = last == ')' || core.equals("else") || core.equals("do");
            }
        } else {
            st.pendingBody = false;
        }
    }

    private static String rtrim(String s) {
        int e = s.length();
        while (e > 0 && Character.isWhitespace(s.charAt(e - 1))) e--;
        return s.substring(0, e);
    }
}
