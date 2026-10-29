import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude AI 코드 생성.
 *
 * 사용법: 주석 줄(// ... 또는 /* ... *&#47;) 끝에서 Tab → Claude가 코드를 만들어 미리보기로 보여 준다.
 *         한 번 더 Tab → 주석 아래에 코드를 넣는다. Esc 또는 다른 곳을 누르거나 글을 고치면 취소된다.
 */
public class ClaudeAiPlugin implements MiniJavaEditor.Plugin {

    // 주소는 -Dminijava.claude.endpoint=... 로 바꿀 수 있다 (시험용, 또는 회사 게이트웨이용)
    static volatile String endpoint =
            System.getProperty("minijava.claude.endpoint", "https://api.anthropic.com/v1/messages");
    static volatile File configFile =
            new File(System.getProperty("user.home"), ".minijava-editor" + File.separator + "claude-ai.properties");

    static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    static final String[] MODELS = {"claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5"};
    static final int MAX_TOKENS = 8192;            // 생각(thinking)에 쓰는 토큰도 여기에 들어간다
    static final int CONTEXT_BEFORE = 24_000;      // 삽입 위치 앞뒤로 보내는 글자 수
    static final int CONTEXT_AFTER = 8_000;
    static final int MAX_INSTRUCTION = 4_000;

    static final String SYSTEM_PROMPT = """
            You are the code-generation engine inside a Java editor.
            The user writes a comment that describes the code they want. Your reply is inserted on the lines right after that comment, at <<INSERT_HERE>>.
            Rules:
            - Reply with the code only: no markdown fences, no explanation, and do not repeat the comment.
            - The code must fit exactly at <<INSERT_HERE>> in the surrounding file (inside a method body, a class body, or top level) and must not duplicate code that already exists there.
            - Do not add import statements. If you need a class that is not imported yet, use its fully qualified name.
            - Follow the naming and style of the file. Write code comments, if any, in the language of the instruction and keep them short.
            """;

    static final String HELP = """
            Claude AI 코드 생성 사용법

            1) 메뉴 'Claude > API 키 설정...'에서 Anthropic API 키를 한 번 입력합니다.
            2) 만들고 싶은 코드를 주석으로 적습니다.   // 숫자 목록에서 짝수만 골라 제곱하기
               (// 줄이 여러 개 이어지면 하나의 설명으로 합쳐지고, /* ... */ 주석도 됩니다)
            3) 주석 줄의 맨 끝에서 Tab → Claude가 만든 코드가 미리보기로 뜹니다.
            4) 한 번 더 Tab → 주석 아래에 코드가 들어갑니다 (Ctrl+Z로 되돌릴 수 있습니다).
               Esc, 마우스 클릭, 글 입력, 커서 이동 중 아무거나 하면 취소됩니다.

            주의: 코드를 만들 때 현재 파일의 내용이 Anthropic API로 전송됩니다.
            """;

    private enum State { IDLE, LOADING, PREVIEW }

    /** 주석에서 읽어낸 요청: 설명, 코드를 넣을 위치(주석 줄의 끝), 팝업을 붙일 위치, 주석의 들여쓰기 */
    record Target(String instruction, int insertPos, int anchorPos, String indent) { }

    /** Claude의 응답: 글 부분과 끝난 이유 */
    record Reply(String text, String stopReason) { }

    // ------------------------------------------------------------------
    // 화면 스레드(EDT)에서만 만지는 상태
    // ------------------------------------------------------------------

    private MiniJavaEditor.Context ctx;
    private HttpClient http;
    private State state = State.IDLE;
    private int generation;                      // 취소/새 요청마다 올린다. 오래된 응답을 버리는 데 쓴다.
    private CompletableFuture<?> inflight;
    private Target target;
    private String snapshot;                     // 요청할 때의 글 전체. 적용 직전에 같은지 다시 본다.
    private String pendingCode;
    private JTextComponent watched;
    private Document watchedDoc;
    private CaretListener caretListener;
    private DocumentListener docListener;
    private FocusAdapter focusListener;
    private JWindow overlay;
    private javax.swing.Timer overlayTimer;

    @Override
    public String name() {
        return "Claude AI 코드 생성";
    }

    @Override
    public String description() {
        return "주석 줄에서 Tab → Claude가 코드 제안 → Tab으로 적용 (Claude 메뉴)";
    }

    @Override
    public void onLoad(MiniJavaEditor.Context context) {
        this.ctx = context;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .proxy(ProxySelector.getDefault())
                .build();
        ctx.onTabKey(this::onTab);
        ctx.onEscapeKey(this::onEscape);
        ctx.addMenuItem("Claude", "API 키 설정...", this::askApiKey);
        ctx.addMenuItem("Claude", "모델 선택...", this::askModel);
        ctx.addMenuItem("Claude", "사용 방법", () -> ctx.showMessage(HELP));
    }

    @Override
    public void onUnload() {
        if (SwingUtilities.isEventDispatchThread()) cancel(); else SwingUtilities.invokeLater(this::cancel);
        if (http != null) http.shutdownNow();      // 켜고 끌 때마다 통신용 스레드가 남지 않게 한다
    }

    // ------------------------------------------------------------------
    // Tab / Esc
    // ------------------------------------------------------------------

    private boolean onTab() {
        try {
            return switch (state) {
                case PREVIEW -> apply();
                case LOADING -> true;      // 만드는 중에 누른 Tab이 탭 글자로 들어가지 않게 삼킨다
                case IDLE -> start();
            };
        } catch (RuntimeException e) {
            reset();
            System.err.println("[Claude AI] " + e);
            return false;
        }
    }

    private boolean onEscape() {
        if (state == State.IDLE) return false;
        cancel();
        return true;
    }

    /** 주석에서 Tab을 눌렀을 때: 요청을 보낸다. 주석이 아니면 false (평소처럼 Tab). */
    private boolean start() {
        File file = ctx.currentFile();
        if (file != null && !file.getName().toLowerCase(Locale.ROOT).endsWith(".java")) return false;
        if (ctx.selectionStart() != ctx.selectionEnd()) return false;
        String text = ctx.text();
        Target t = findTarget(text, ctx.caretPosition());
        if (t == null) return false;

        hideOverlay();
        JTextComponent tc = focusedTextComponent();
        Config cfg = Config.load();
        String key = cfg.effectiveKey();
        if (key.isEmpty()) {
            showOverlay(tc, t.anchorPos(), "Claude API 키가 없습니다",
                    "메뉴 'Claude > API 키 설정...'에서 키를 입력하세요.\n(환경변수 ANTHROPIC_API_KEY도 사용할 수 있습니다)", true, 6000);
            return true;
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("content-type", "application/json")
                    .header("anthropic-version", "2023-06-01")
                    .header("x-api-key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestBody(cfg, userMessage(fileLabel(file), text, t)), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            showOverlay(tc, t.anchorPos(), "요청을 만들지 못했습니다", "API 키나 주소 형식을 확인하세요: " + e.getMessage(), true, 6000);
            return true;
        }

        target = t;
        snapshot = text;
        state = State.LOADING;
        int gen = ++generation;
        watch(tc);
        showOverlay(tc, t.anchorPos(), "Claude가 코드를 만드는 중...  (Esc: 취소)", null, false, 0);

        CompletableFuture<HttpResponse<String>> send = http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        inflight = send;
        send.thenApply(r -> {
            try {
                return parseReply(r.statusCode(), r.body());
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((reply, err) -> SwingUtilities.invokeLater(() -> onResult(gen, reply, err)));
        return true;
    }

    private void onResult(int gen, Reply reply, Throwable err) {
        if (gen != generation || state != State.LOADING) return;   // 그 사이에 취소되었다
        if (err != null) {
            fail("Claude 오류", describe(err));
            return;
        }
        String code = tidy(reply.text(), target.indent());
        if (code.isEmpty()) {
            fail("Claude가 코드를 돌려주지 않았습니다", "끝난 이유: " + reply.stopReason());
            return;
        }
        pendingCode = code;
        state = State.PREVIEW;
        String title = "Claude 제안   Tab: 적용  ·  Esc: 취소"
                + ("max_tokens".equals(reply.stopReason()) ? "   (길이 제한으로 잘렸을 수 있음)" : "");
        showOverlay(watched, target.anchorPos(), title, code, false, 0);
    }

    /** 두 번째 Tab: 미리보기 코드를 주석 아래에 넣는다 */
    private boolean apply() {
        String code = pendingCode;
        Target t = target;
        String before = snapshot;
        reset();
        if (!ctx.text().equals(before)) return false;   // 안전장치: 글이 달라졌으면 넣지 않는다
        ctx.select(t.insertPos(), t.insertPos());
        ctx.insertText("\n" + code);
        return true;
    }

    private void fail(String title, String message) {
        JTextComponent tc = watched;
        int pos = target.anchorPos();
        reset();
        showOverlay(tc, pos, title, message, true, 6000);
    }

    /** 만드는 중이거나 미리보기가 떠 있을 때의 모든 취소 */
    private void cancel() {
        generation++;
        if (inflight != null) inflight.cancel(true);
        reset();
    }

    private void reset() {
        unwatch();
        hideOverlay();
        state = State.IDLE;
        inflight = null;
        target = null;
        snapshot = null;
        pendingCode = null;
    }

    // ------------------------------------------------------------------
    // 편집 중인 글이 바뀌면(글 입력, 커서 이동, 포커스 이동) 요청/미리보기를 취소한다
    // ------------------------------------------------------------------

    private static JTextComponent focusedTextComponent() {
        Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return c instanceof JTextComponent tc ? tc : null;
    }

    private void watch(JTextComponent tc) {
        if (tc == null) return;
        watched = tc;
        watchedDoc = tc.getDocument();
        caretListener = e -> cancel();
        docListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { cancel(); }
            @Override public void removeUpdate(DocumentEvent e) { cancel(); }
            @Override public void changedUpdate(DocumentEvent e) { /* 글자 색 칠하기 같은 모양 변화는 무시 */ }
        };
        focusListener = new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { cancel(); }
        };
        tc.addCaretListener(caretListener);
        watchedDoc.addDocumentListener(docListener);
        tc.addFocusListener(focusListener);
    }

    private void unwatch() {
        if (watched != null) {
            watched.removeCaretListener(caretListener);
            watched.removeFocusListener(focusListener);
            watchedDoc.removeDocumentListener(docListener);
        }
        watched = null;
        watchedDoc = null;
        caretListener = null;
        docListener = null;
        focusListener = null;
    }

    // ------------------------------------------------------------------
    // 미리보기 팝업 (포커스를 받지 않는 작은 창이라 Tab/Esc는 계속 편집기로 간다)
    // ------------------------------------------------------------------

    private void showOverlay(JTextComponent anchor, int pos, String title, String text, boolean wrap, int autoHideMs) {
        hideOverlay();
        Window owner = anchor != null ? SwingUtilities.getWindowAncestor(anchor) : ctx.frame();
        JWindow w = new JWindow(owner);
        w.setFocusableWindowState(false);

        Color bg = uiColor("ToolTip.background", new Color(255, 255, 225));
        Color fg = uiColor("ToolTip.foreground", Color.BLACK);
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(bg);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(uiColor("controlShadow", Color.GRAY)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JLabel head = new JLabel(title);
        head.setForeground(fg);
        head.setFont(head.getFont().deriveFont(Font.BOLD));
        panel.add(head, BorderLayout.NORTH);

        if (text != null) {
            JTextArea area = new JTextArea(text);
            area.setEditable(false);
            area.setFocusable(false);
            area.setOpaque(false);
            area.setForeground(fg);
            Font f = anchor != null ? anchor.getFont() : null;
            area.setFont(f != null ? f : new Font(Font.MONOSPACED, Font.PLAIN, 13));
            area.setTabSize(4);
            if (wrap) {
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setSize(new Dimension(460, Short.MAX_VALUE));
            }
            Dimension pref = area.getPreferredSize();
            FontMetrics fm = area.getFontMetrics(area.getFont());
            int maxH = fm.getHeight() * 22;
            int maxW = 900;
            JScrollPane sp = new JScrollPane(area);
            sp.setBorder(null);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.setPreferredSize(new Dimension(
                    Math.min(pref.width, maxW) + (pref.height > maxH ? 18 : 0),
                    Math.min(pref.height, maxH) + (pref.width > maxW ? 18 : 0)));
            panel.add(sp, BorderLayout.CENTER);
        }
        w.setContentPane(panel);
        w.pack();
        place(w, anchor, pos, owner);
        w.setVisible(true);
        overlay = w;

        if (autoHideMs > 0) {
            overlayTimer = new javax.swing.Timer(autoHideMs, e -> hideOverlay());
            overlayTimer.setRepeats(false);
            overlayTimer.start();
        }
    }

    /** 주석 줄 바로 아래에 붙인다. 화면 아래쪽이 모자라면 줄 위쪽에 붙인다. */
    private static void place(JWindow w, JTextComponent anchor, int pos, Window owner) {
        if (anchor == null || !anchor.isShowing()) {
            w.setLocationRelativeTo(owner);
            return;
        }
        Rectangle2D r;
        try {
            r = anchor.modelToView2D(pos);
        } catch (BadLocationException e) {
            r = null;
        }
        if (r == null) {
            w.setLocationRelativeTo(owner);
            return;
        }
        Point below = new Point((int) r.getX(), (int) r.getMaxY() + 2);
        Point above = new Point((int) r.getX(), (int) r.getY());
        SwingUtilities.convertPointToScreen(below, anchor);
        SwingUtilities.convertPointToScreen(above, anchor);

        GraphicsConfiguration gc = anchor.getGraphicsConfiguration();
        Rectangle sb = gc != null ? gc.getBounds() : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        Insets in = gc != null ? Toolkit.getDefaultToolkit().getScreenInsets(gc) : new Insets(0, 0, 0, 0);
        int left = sb.x + in.left, top = sb.y + in.top;
        int right = sb.x + sb.width - in.right, bottom = sb.y + sb.height - in.bottom;

        int x = Math.max(left, Math.min(below.x, right - w.getWidth()));
        int y = below.y + w.getHeight() <= bottom ? below.y : Math.max(top, above.y - w.getHeight() - 2);
        w.setLocation(x, y);
    }

    private void hideOverlay() {
        if (overlayTimer != null) {
            overlayTimer.stop();
            overlayTimer = null;
        }
        if (overlay != null) {
            overlay.dispose();
            overlay = null;
        }
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    // ------------------------------------------------------------------
    // 주석 찾기 (화면과 무관한 순수 함수)
    // ------------------------------------------------------------------

    /**
     * 커서가 "주석 줄의 맨 끝"에 있으면 그 주석을 읽어 돌려준다. 아니면 null.
     * - // 줄: 위로 이어진 // 줄들을 하나의 설명으로 합친다. 바로 아래 줄도 // 이면 아직 쓰는 중이므로 null.
     * - 블록 주석: 커서 줄이 *&#47; 로 끝나고, 그 주석이 줄 맨 앞에서 시작할 때만.
     * - 코드 뒤에 붙은 주석(int a; // ...)은 대상이 아니다.
     */
    static Target findTarget(String text, int caret) {
        if (caret < 0 || caret > text.length()) return null;
        int lineStart = caret == 0 ? 0 : text.lastIndexOf('\n', caret - 1) + 1;
        int lineEnd = lineEndOf(text, caret);
        String line = text.substring(lineStart, lineEnd);
        String code = line.stripTrailing();
        if (code.isBlank() || caret < lineStart + code.length()) return null;
        String indent = line.substring(0, line.length() - line.stripLeading().length());
        String trimmed = code.strip();
        int anchor = lineStart + indent.length();
        if (trimmed.startsWith("//")) return lineComment(text, lineStart, lineEnd, anchor, indent);
        if (trimmed.endsWith("*/")) return blockComment(text, lineStart + code.length() - 2, lineEnd, anchor);
        return null;
    }

    private static int lineEndOf(String text, int from) {
        int e = text.indexOf('\n', from);
        return e < 0 ? text.length() : e;
    }

    private static Target lineComment(String text, int lineStart, int lineEnd, int anchor, String indent) {
        if (lineEnd < text.length()) {
            int ns = lineEnd + 1;
            if (text.substring(ns, lineEndOf(text, ns)).strip().startsWith("//")) return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add(slashText(text.substring(lineStart, lineEnd)));
        int s = lineStart;
        while (s > 0 && lines.size() < 50) {
            int ps = text.lastIndexOf('\n', s - 2) + 1;
            String prev = text.substring(ps, s - 1);
            if (!prev.strip().startsWith("//")) break;
            lines.add(0, slashText(prev));
            s = ps;
        }
        return build(String.join("\n", lines), lineEnd, anchor, indent);
    }

    private static String slashText(String line) {
        String t = line.strip().substring(2);
        while (t.startsWith("/")) t = t.substring(1);
        return t.strip();
    }

    private static Target blockComment(String text, int close, int lineEnd, int anchor) {
        int open = close == 0 ? -1 : text.lastIndexOf("/*", close - 1);
        if (open < 0 || text.indexOf("*/", open + 2) != close) return null;
        int openLine = open == 0 ? 0 : text.lastIndexOf('\n', open - 1) + 1;
        String indent = text.substring(openLine, open);
        if (!indent.isBlank()) return null;                      // 코드 뒤에 붙은 블록 주석
        List<String> lines = new ArrayList<>();
        for (String l : text.substring(open + 2, close).split("\n", -1)) {
            String t = l.strip();
            while (t.startsWith("*")) t = t.substring(1);
            lines.add(t.strip());
        }
        return build(String.join("\n", lines), lineEnd, anchor, indent);
    }

    private static Target build(String instruction, int insertPos, int anchor, String indent) {
        String s = instruction.strip();
        if (s.isEmpty()) return null;
        if (s.length() > MAX_INSTRUCTION) s = s.substring(0, MAX_INSTRUCTION);
        return new Target(s, insertPos, anchor, indent);
    }

    // ------------------------------------------------------------------
    // 요청 만들기 / 응답 다듬기 (순수 함수)
    // ------------------------------------------------------------------

    static String fileLabel(File file) {
        return file == null ? "(unsaved).java" : file.getName();
    }

    /** 삽입 위치(<<INSERT_HERE>>)를 표시한 파일 내용. 너무 길면 앞뒤를 줄 단위로 자른다. */
    static String userMessage(String fileName, String text, Target t) {
        int pos = t.insertPos();
        int from = Math.max(0, pos - CONTEXT_BEFORE);
        if (from > 0) {
            int nl = text.indexOf('\n', from);
            if (nl >= 0 && nl < pos) from = nl + 1;
        }
        int to = Math.min(text.length(), pos + CONTEXT_AFTER);
        if (to < text.length()) {
            int nl = text.lastIndexOf('\n', to);
            if (nl > pos) to = nl;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(fileName).append("\n\n<file>\n");
        if (from > 0) sb.append("[... earlier lines omitted ...]\n");
        sb.append(text, from, pos).append("\n<<INSERT_HERE>>").append(text, pos, to);
        if (to < text.length()) sb.append("\n[... later lines omitted ...]");
        sb.append("\n</file>\n\nInstruction (the comment right above <<INSERT_HERE>>):\n")
                .append(t.instruction())
                .append("\n\nReply with only the code to insert at <<INSERT_HERE>>.");
        return sb.toString();
    }

    static String requestBody(Config cfg, String user) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"model\":").append(Json.quote(cfg.model));
        sb.append(",\"max_tokens\":").append(MAX_TOKENS);
        if (!cfg.effort.isEmpty()) {
            sb.append(",\"output_config\":{\"effort\":").append(Json.quote(cfg.effort)).append('}');
        }
        sb.append(",\"system\":").append(Json.quote(SYSTEM_PROMPT));
        sb.append(",\"messages\":[{\"role\":\"user\",\"content\":").append(Json.quote(user)).append("}]}");
        return sb.toString();
    }

    /** 응답(JSON)에서 글 블록만 모은다. thinking 같은 다른 블록은 건너뛴다. 오류 응답이면 IOException. */
    static Reply parseReply(int status, String body) throws IOException {
        Map<?, ?> root;
        try {
            root = Json.parse(body) instanceof Map<?, ?> m ? m : null;
        } catch (IllegalArgumentException e) {
            root = null;
        }
        if (root == null) throw new IOException("HTTP " + status + ": 알 수 없는 응답입니다 " + abbreviate(body));
        if (status / 100 != 2 || "error".equals(root.get("type"))) {
            String msg = root.get("error") instanceof Map<?, ?> err && err.get("message") != null
                    ? String.valueOf(err.get("message")) : abbreviate(body);
            throw new IOException("HTTP " + status + ": " + msg
                    + (status == 401 ? "\n('Claude > API 키 설정...'에서 키를 확인하세요)" : ""));
        }
        StringBuilder text = new StringBuilder();
        if (root.get("content") instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> bm && "text".equals(bm.get("type")) && bm.get("text") instanceof String s) {
                    text.append(s);
                }
            }
        }
        return new Reply(text.toString(), String.valueOf(root.get("stop_reason")));
    }

    private static String abbreviate(String s) {
        String t = s == null ? "" : s.strip();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }

    static String describe(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) t = t.getCause();
        if (t instanceof CancellationException) return "취소되었습니다";
        if (t instanceof HttpTimeoutException) return "응답 시간이 초과되었습니다";
        if (t instanceof ConnectException || t instanceof UnknownHostException || t instanceof UnresolvedAddressException) {
            return "서버에 연결하지 못했습니다. 인터넷 연결이나 프록시를 확인하세요";
        }
        String m = t.getMessage();
        return t instanceof IOException && m != null ? m : t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    private static final Pattern FENCE = Pattern.compile("(?s)```[^\\n]*\\n(.*?)```");

    /** 코드 울타리(```)를 걷어내고, 들여쓰기를 주석 줄에 맞춘다 */
    static String tidy(String reply, String indent) {
        String s = reply.replace("\r\n", "\n").replace('\r', '\n');
        Matcher m = FENCE.matcher(s);
        if (m.find()) {
            s = m.group(1);
        } else if (s.stripLeading().startsWith("```")) {      // 닫는 울타리 없이 잘린 경우
            int nl = s.indexOf('\n');
            s = nl < 0 ? "" : s.substring(nl + 1);
        }
        s = s.replaceFirst("\\A(?:[ \\t]*\\n)+", "").stripTrailing();   // 첫 줄의 들여쓰기는 남긴다
        return s.isEmpty() ? "" : reindent(s, indent);
    }

    /** 공통 들여쓰기를 걷어내고 indent를 앞에 붙인다. 모델이 들여쓰기를 어떻게 하든 결과가 같아진다. */
    static String reindent(String code, String indent) {
        List<String> lines = code.lines().toList();
        int min = Integer.MAX_VALUE;
        for (String l : lines) {
            if (!l.isBlank()) min = Math.min(min, l.length() - l.stripLeading().length());
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            String l = lines.get(i);
            if (!l.isBlank()) sb.append(indent).append(l.substring(min));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 설정 (API 키, 모델)
    // ------------------------------------------------------------------

    static final class Config {
        String apiKey = "";
        String model = DEFAULT_MODEL;
        String effort = "low";      // 비워 두면 effort를 보내지 않는다. 파일을 직접 고쳐서만 바꾼다.

        static Config load() {
            Properties p = new Properties();
            File f = configFile;
            if (f.isFile()) {
                try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                    p.load(r);
                } catch (IOException ignored) {
                    // 못 읽으면 기본값으로 쓴다
                }
            }
            Config c = new Config();
            c.apiKey = p.getProperty("apiKey", "").strip();
            c.model = p.getProperty("model", DEFAULT_MODEL).strip();
            if (c.model.isEmpty()) c.model = DEFAULT_MODEL;
            c.effort = p.getProperty("effort", "low").strip();
            return c;
        }

        /** 파일의 키, 없으면 환경변수 ANTHROPIC_API_KEY */
        String effectiveKey() {
            if (!apiKey.isEmpty()) return apiKey;
            String env = System.getenv("ANTHROPIC_API_KEY");
            return env == null ? "" : env.strip();
        }

        void save() throws IOException {
            Path path = configFile.toPath().toAbsolutePath();
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                try {
                    // 키가 들어 있는 파일이므로 가능하면 나만 읽게 만든다 (Windows는 이 방법을 지원하지 않는다)
                    Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                } catch (UnsupportedOperationException e) {
                    Files.createFile(path);
                }
            }
            Properties p = new Properties();
            p.setProperty("apiKey", apiKey);
            p.setProperty("model", model);
            p.setProperty("effort", effort);
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                p.store(w, "Claude AI code generation extension settings");
            }
        }
    }

    private void askApiKey() {
        Config cfg = Config.load();
        String now = !cfg.apiKey.isEmpty() ? "저장된 키가 있습니다. 새로 입력하면 바꿉니다."
                : !cfg.effectiveKey().isEmpty() ? "환경변수 ANTHROPIC_API_KEY를 쓰는 중입니다."
                : "아직 키가 없습니다.";
        JPasswordField pw = new JPasswordField(36);
        Object[] message = {
                "Anthropic API 키를 입력하세요 (sk-ant-...)", now, pw,
                "· 키는 " + configFile + " 파일에 글자 그대로 저장됩니다.",
                "· 코드를 만들 때 현재 파일의 내용이 Anthropic API로 전송됩니다."};
        if (JOptionPane.showConfirmDialog(ctx.frame(), message, "Claude API 키",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String key = new String(pw.getPassword()).strip();
        if (key.isEmpty()) return;                                  // 비워 두면 그대로 둔다
        cfg.apiKey = key;
        saveOrWarn(cfg);
    }

    private void askModel() {
        Config cfg = Config.load();
        JComboBox<String> box = new JComboBox<>(MODELS);
        box.setEditable(true);
        box.setSelectedItem(cfg.model);
        Object[] message = {
                "사용할 모델 (목록에 없는 모델 ID는 직접 입력)", box,
                "sonnet: 균형(기본) · haiku: 가장 빠름 · opus: 가장 똑똑하지만 느림"};
        if (JOptionPane.showConfirmDialog(ctx.frame(), message, "Claude 모델",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String model = String.valueOf(box.getSelectedItem()).strip();
        if (model.isEmpty()) return;
        cfg.model = model;
        saveOrWarn(cfg);
    }

    private void saveOrWarn(Config cfg) {
        try {
            cfg.save();
        } catch (IOException e) {
            ctx.showMessage("설정을 저장하지 못했습니다: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 아주 작은 JSON 도구 (편집기 안의 MiniJson은 확장 프로그램에서 쓸 수 없어서 따로 둔다)
    // ------------------------------------------------------------------

    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        /** 객체는 Map, 배열은 List, 글은 String, 숫자는 Double, true/false는 Boolean, null은 null */
        static Object parse(String s) {
            Json j = new Json(s);
            j.skipWs();
            Object v = j.value();
            j.skipWs();
            if (j.i != s.length()) throw j.error("끝에 남은 글자가 있습니다");
            return v;
        }

        static String quote(String v) {
            StringBuilder sb = new StringBuilder(v.length() + 2).append('"');
            for (int k = 0; k < v.length(); k++) {
                char c = v.charAt(k);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
                    }
                }
            }
            return sb.append('"').toString();
        }

        private IllegalArgumentException error(String why) {
            return new IllegalArgumentException("JSON " + i + "번째 글자: " + why);
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private boolean at(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        private void expect(char c) {
            if (!at(c)) throw error("'" + c + "'가 필요합니다");
            i++;
        }

        private Object value() {
            if (i >= s.length()) throw error("값이 필요합니다");
            return switch (s.charAt(i)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String word, Object v) {
            if (!s.startsWith(word, i)) throw error("알 수 없는 값입니다");
            i += word.length();
            return v;
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            skipWs();
            if (at('}')) { i++; return m; }
            while (true) {
                skipWs();
                if (!at('"')) throw error("키가 필요합니다");
                String k = string();
                skipWs();
                expect(':');
                skipWs();
                m.put(k, value());
                skipWs();
                if (at(',')) { i++; continue; }
                expect('}');
                return m;
            }
        }

        private List<Object> array() {
            List<Object> a = new ArrayList<>();
            i++;
            skipWs();
            if (at(']')) { i++; return a; }
            while (true) {
                skipWs();
                a.add(value());
                skipWs();
                if (at(',')) { i++; continue; }
                expect(']');
                return a;
            }
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error("\\u 뒤에 16진수 4자리가 필요합니다");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("\\u 뒤에 16진수 4자리가 필요합니다");
                        }
                        i += 4;
                    }
                    default -> throw error("알 수 없는 이스케이프입니다");
                }
            }
            throw error("글이 끝나지 않았습니다");
        }

        private Double number() {
            int start = i;
            while (i < s.length() && "+-.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw error("알 수 없는 값입니다");
            try {
                return Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw error("숫자가 올바르지 않습니다");
            }
        }
    }
}
