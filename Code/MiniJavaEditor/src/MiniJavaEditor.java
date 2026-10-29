import com.sun.source.util.JavacTask;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Mini Java Editor - 여러 파일을 다루는 Java 편집기
 *
 * 1단계  파일 열기/저장/편집 (여러 파일은 탭으로, 폴더는 왼쪽 탐색기로)
 * 2단계  문법 강조        3단계  자동완성        4단계  오류 표시(javac)
 * 5단계  터미널(PowerShell), F5로 프로젝트 실행
 * 6단계  확장 프로그램(jar)  - 메뉴 "확장"
 * 7단계  Maven Central 라이브러리 - 메뉴 "라이브러리"
 *
 * 실행: java MiniJavaEditor.java [폴더나 파일 ...]
 */
public class MiniJavaEditor extends JFrame {

    // ---------------------------------------------------------------
    // 확장 프로그램 API (6단계)
    // 확장 프로그램은 이 두 인터페이스만 알면 만들 수 있다.
    // "확장 > 확장 프로그램 관리 > 개발 키트 만들기"로 예제와 컴파일용 jar를 받을 수 있다.
    // ---------------------------------------------------------------

    /** 확장 프로그램(jar)이 구현하는 인터페이스. jar의 manifest에 "MiniJava-Plugin-Class: 클래스이름"을 적는다. */
    public interface Plugin {
        /** 목록에 보일 이름 */
        String name();

        /** 한 줄 설명 */
        default String description() { return ""; }

        /** 확장 프로그램이 켜질 때 한 번 호출된다. 여기서 메뉴나 스니펫을 등록한다. */
        void onLoad(Context context);

        /** 확장 프로그램이 꺼질 때 호출된다. 등록한 메뉴/스니펫은 자동으로 정리되므로 보통 할 일이 없다. */
        default void onUnload() { }
    }

    /** 확장 프로그램이 편집기를 다루는 창구 */
    public interface Context {
        /** 편집기 창 (대화상자의 부모로 쓸 수 있다). 없을 수도 있다. */
        JFrame frame();

        /** 지금 보고 있는 탭의 글 전체 (열린 탭이 없으면 빈 글자) */
        String text();

        /** 지금 선택한 글 (선택이 없으면 빈 글자) */
        String selectedText();

        /** 선택한 글을 바꾸거나, 선택이 없으면 커서 위치에 글을 넣는다 */
        void insertText(String text);

        /** 지금 보고 있는 탭의 글 전체를 바꾼다 (Ctrl+Z로 되돌릴 수 있다) */
        void replaceAll(String text);

        /** 지금 보고 있는 파일 (저장하지 않은 새 글이면 null) */
        File currentFile();

        /** 열어 둔 프로젝트 폴더 (없으면 null) */
        File projectRoot();

        /** 파일을 새 탭으로 연다 */
        void openFile(File file);

        /** 안내 대화상자를 띄운다 */
        void showMessage(String message);

        /** 아래쪽 터미널에서 명령을 실행한다 */
        void runInTerminal(String command);

        /** 메뉴에 항목을 추가한다. 같은 이름의 메뉴가 없으면 새로 만든다. */
        void addMenuItem(String menuName, String label, Runnable action);

        /** 자동완성 스니펫을 등록한다. template 안의 $0은 커서가 놓일 자리다. */
        void addSnippet(String name, String description, String template);

        /** 파일이 저장될 때마다 호출될 함수를 등록한다 */
        void onFileSaved(Consumer<File> listener);

        /** 커서 위치 (글 맨 앞이 0) */
        int caretPosition();

        /** 선택 영역의 시작 위치 (선택이 없으면 커서 위치) */
        int selectionStart();

        /** 선택 영역의 끝 위치 (선택이 없으면 커서 위치) */
        int selectionEnd();

        /** 선택 영역을 정한다. start와 end가 같으면 커서만 옮긴다. */
        void select(int start, int end);

        /**
         * Tab을 누르면 불린다 (자동완성 목록이 떠 있지 않을 때). true를 돌려주면 Tab은 여기서 처리된 것이다.
         * 여러 확장 프로그램이 등록하면 등록 순서대로 물어보고, 처음 true를 돌려준 것에서 멈춘다.
         */
        void onTabKey(BooleanSupplier handler);

        /** Esc를 누르면 불린다 (자동완성 목록이 떠 있지 않을 때). true를 돌려주면 Esc는 처리된 것이다. */
        void onEscapeKey(BooleanSupplier handler);
    }

    /** "문제" 목록의 한 줄: 어느 파일의 어떤 문제인가 */
    record FileProblem(String fileName, Object tab, JavaChecker.Problem problem) { }

    private final EditorPanel panel;

    public MiniJavaEditor() {
        this(List.of());
    }

    /** @param args 시작할 때 열 폴더나 파일 */
    public MiniJavaEditor(List<String> args) {
        super("Mini Java Editor");
        panel = new EditorPanel(PluginManager.defaultDirectory());
        panel.setTitleListener(this::setTitle);
        setContentPane(panel);
        setJMenuBar(panel.menuBar());

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (panel.requestClose()) {
                    System.exit(0);
                }
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                panel.hidePopup();
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) { panel.hidePopup(); }

            @Override
            public void componentResized(ComponentEvent e) { panel.hidePopup(); }
        });

        setSize(1200, 760);
        setLocationRelativeTo(null);
        panel.openArguments(args);
        // 창이 뜬 다음에, 처음 실행이면 기본 확장 프로그램을 설치할지 한 번 물어본다
        SwingUtilities.invokeLater(panel::offerBundledExtensionsOnFirstRun);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 실패하면 기본 모양으로 실행된다
        }
        // Swing은 화면 관련 작업을 별도 스레드에서 시작하는 것이 안전하다
        SwingUtilities.invokeLater(() -> new MiniJavaEditor(List.of(args)).setVisible(true));
    }

    static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static ExecutorService daemonExecutor(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true); // 편집기를 닫을 때 같이 종료되도록
            return t;
        });
    }

    // =================================================================
    // 편집기 화면: 탭(여러 파일) + 탐색기(폴더) + 아래쪽 패널(문제/터미널)
    // 창(JFrame)과 분리해 두어서, 화면 없이도 동작을 테스트할 수 있다.
    // =================================================================

    static class EditorPanel extends JPanel {

        /** 대화상자를 한곳에 모았다. 테스트에서는 가짜로 바꿔 끼운다. */
        interface Dialogs {
            void info(String message);
            void error(String message);
            boolean confirm(String title, String message);
            /** JOptionPane.YES_OPTION(저장) / NO_OPTION(저장 안 함) / CANCEL_OPTION(취소) */
            int confirmSave(String fileName);
            File chooseSaveFile(File startDir, String suggestedName);
            /** extensions가 비어 있으면 모든 파일 */
            File[] chooseFiles(File startDir, String description, String... extensions);
            File chooseFolder(File startDir);
            String askText(String title, String prompt, String initial);
            Object choose(String title, String prompt, Object[] options);
        }

        private static final long MAX_OPEN_BYTES = 5L * 1024 * 1024;
        private static final Set<String> BUILT_IN_MENUS = Set.of("파일", "편집", "검사", "실행", "라이브러리", "확장");
        private static final Color ERROR_COLOR = new Color(0xE53935);
        private static final Color WARNING_COLOR = new Color(0xF9A825);

        // ---- 열려 있는 파일들 ----
        private final JTabbedPane editorTabs = new JTabbedPane();
        private final Map<Component, EditorTab> tabsByComponent = new IdentityHashMap<>();
        private int untitledCounter = 0;
        private File lastDirectory = new File(System.getProperty("user.dir", "."));

        // ---- 프로젝트 탐색기 ----
        private File projectRoot = null;
        private final DefaultMutableTreeNode treeRoot =
                new DefaultMutableTreeNode("폴더를 열어 주세요 (파일 > 폴더 열기)");
        private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
        private final JTree projectTree = new JTree(treeModel);
        private volatile Set<String> projectTypeNames = Set.of();

        // ---- 아래쪽 패널 ----
        private final DefaultListModel<FileProblem> problemModel = new DefaultListModel<>();
        private final JList<FileProblem> problemList = new JList<>(problemModel);
        private final JTabbedPane bottomTabs = new JTabbedPane();
        private final JLabel statusLabel = new JLabel(" ");
        private final TerminalPanel terminal = new TerminalPanel(ShellRunner.Shell.detect());

        // ---- 오류 검사 ----
        private final Timer checkTimer = new Timer(700, e -> runCheck());
        private final ExecutorService checker = daemonExecutor("java-checker");
        private final ExecutorService background = daemonExecutor("background");
        private int docVersion = 0; // 글이나 열린 파일이 바뀔 때마다 1씩 증가. 오래된 검사 결과를 버리는 데 쓴다

        // ---- 자동완성 ----
        private final CompletionPopup popup = new CompletionPopup();

        // ---- 확장 프로그램 / 라이브러리 ----
        private final File dataDir;
        private final PluginManager plugins;
        private final JMenuBar menuBar = new JMenuBar();
        private final Map<String, JMenu> menus = new LinkedHashMap<>();
        private final List<SavedListener> savedListeners = new CopyOnWriteArrayList<>();
        private volatile List<File> libJars = List.of();
        private URLClassLoader libLoader = null;
        private MavenResolver maven;

        Dialogs dialogs = new SwingDialogs();
        String lastMessage = "";   // 마지막으로 보여 준 안내/오류 글 (테스트에서 확인용)
        private Consumer<String> titleListener = t -> { };

        private record SavedListener(Object owner, Consumer<File> fn) { }

        /** 확장 프로그램의 Tab/Esc 처리기. tab이 true면 Tab, 아니면 Esc */
        private record KeyHook(Object owner, BooleanSupplier fn, boolean tab) { }
        private final List<KeyHook> keyHooks = new CopyOnWriteArrayList<>();

        EditorPanel(File dataDir) {
            super(new BorderLayout());
            this.dataDir = dataDir;
            this.maven = MavenResolver.central(new File(dataDir, "repository"));
            this.plugins = new PluginManager(new File(dataDir, "plugins"),
                    new File(dataDir, "plugins.properties"), this::createPluginContext);
            checkTimer.setRepeats(false);
            buildUi();
            buildMenus();
            plugins.scan();
            plugins.loadEnabled();
        }

        // =============================================================
        // 화면 구성
        // =============================================================

        private void buildUi() {
            editorTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            editorTabs.addChangeListener(e -> onTabSelected());

            projectTree.setRootVisible(true);
            projectTree.setShowsRootHandles(true);
            projectTree.addTreeWillExpandListener(new TreeWillExpandListener() {
                @Override
                public void treeWillExpand(TreeExpansionEvent e) {
                    loadChildrenIfNeeded((DefaultMutableTreeNode) e.getPath().getLastPathComponent());
                }

                @Override
                public void treeWillCollapse(TreeExpansionEvent e) { }
            });
            projectTree.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) openSelectedTreeFile();
                }

                @Override
                public void mousePressed(MouseEvent e) { showTreeMenu(e); }

                @Override
                public void mouseReleased(MouseEvent e) { showTreeMenu(e); }
            });
            projectTree.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) openSelectedTreeFile();
                }
            });
            JScrollPane treeScroll = new JScrollPane(projectTree);
            treeScroll.setPreferredSize(new Dimension(230, 0));

            problemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            problemList.setCellRenderer(new ProblemRenderer());
            problemList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            ToolTipManager.sharedInstance().registerComponent(problemList);
            problemList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) goToSelectedProblem();
                }
            });
            problemList.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) goToSelectedProblem();
                }
            });
            bottomTabs.addTab("문제", new JScrollPane(problemList));
            bottomTabs.addTab("터미널", terminal);
            bottomTabs.setPreferredSize(new Dimension(0, 190));
            statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.add(bottomTabs, BorderLayout.CENTER);
            bottom.add(statusLabel, BorderLayout.SOUTH);

            JPanel editorArea = new JPanel(new BorderLayout());
            editorArea.add(editorTabs, BorderLayout.CENTER);
            editorArea.add(bottom, BorderLayout.SOUTH);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, editorArea);
            split.setDividerLocation(230);
            split.setResizeWeight(0);
            add(split, BorderLayout.CENTER);
        }

        private JMenu menu(String name) {
            JMenu m = new JMenu(name);
            menus.put(name, m);
            menuBar.add(m);
            return m;
        }

        private JMenuItem item(String text, Runnable action, KeyStroke key) {
            JMenuItem mi = new JMenuItem(text);
            mi.addActionListener(e -> action.run());
            if (key != null) mi.setAccelerator(key);
            return mi;
        }

        private static KeyStroke ctrl(int keyCode) {
            return KeyStroke.getKeyStroke(keyCode, InputEvent.CTRL_DOWN_MASK);
        }

        private static KeyStroke ctrlShift(int keyCode) {
            return KeyStroke.getKeyStroke(keyCode, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        }

        private void buildMenus() {
            JMenu file = menu("파일");
            file.add(item("새로 만들기", this::newFile, ctrl(KeyEvent.VK_N)));
            file.add(item("파일 열기...", this::openFilesDialog, ctrl(KeyEvent.VK_O)));
            file.add(item("폴더 열기...", this::openFolderDialog, ctrlShift(KeyEvent.VK_O)));
            file.add(item("새 Java 클래스...", this::newJavaClass, null));
            file.addSeparator();
            file.add(item("저장", () -> { EditorTab t = current(); if (t != null) save(t); }, ctrl(KeyEvent.VK_S)));
            file.add(item("다른 이름으로 저장...", () -> { EditorTab t = current(); if (t != null) saveAs(t); }, null));
            file.add(item("모두 저장", this::saveAll, ctrlShift(KeyEvent.VK_S)));
            file.addSeparator();
            file.add(item("탭 닫기", () -> { EditorTab t = current(); if (t != null) closeTab(t); }, ctrl(KeyEvent.VK_W)));

            JMenu edit = menu("편집");
            edit.add(item("실행 취소", () -> { EditorTab t = current(); if (t != null) t.undo(); }, ctrl(KeyEvent.VK_Z)));
            edit.add(item("다시 실행", () -> { EditorTab t = current(); if (t != null) t.redo(); }, ctrl(KeyEvent.VK_Y)));
            edit.addSeparator();
            edit.add(item("자동완성 (Ctrl+Space)", () -> {
                EditorTab t = current();
                if (t != null) t.pane.requestFocusInWindow();
                showCompletion(true);
            }, null));

            JMenu check = menu("검사");
            check.add(item("지금 오류 검사", () -> { checkTimer.stop(); runCheck(); },
                    KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0)));

            JMenu run = menu("실행");
            run.add(item("현재 파일 실행", this::runCurrentFile, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0)));
            run.add(item("실행 중지", terminal::stop,
                    KeyStroke.getKeyStroke(KeyEvent.VK_F5, InputEvent.SHIFT_DOWN_MASK)));
            run.add(item("터미널 열기", this::showTerminal, ctrl(KeyEvent.VK_BACK_QUOTE)));

            JMenu libs = menu("라이브러리");
            libs.add(item("pom.xml 기준으로 내려받기", () -> resolveLibraries(false),
                    KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0)));
            libs.add(item("라이브러리 추가 (Maven Central)...", this::addLibraryFlow, null));
            libs.add(item("내려받은 라이브러리 보기", this::showLibraries, null));

            JMenu ext = menu("확장");
            ext.add(item("확장 프로그램 관리...", this::showPluginManager, null));
            ext.add(item("기본 확장 프로그램 설치...", () -> installBundledExtensions(true), null));
        }

        JMenuBar menuBar() { return menuBar; }

        void setTitleListener(Consumer<String> listener) {
            this.titleListener = listener;
            updateFrameTitle();
        }

        void hidePopup() { popup.hide(); }

        // =============================================================
        // 대화상자 (실제 화면용 구현)
        // =============================================================

        private class SwingDialogs implements Dialogs {
            private boolean headless() { return GraphicsEnvironment.isHeadless(); }

            @Override
            public void info(String m) {
                lastMessage = m;
                if (!headless()) JOptionPane.showMessageDialog(EditorPanel.this, m, "알림", JOptionPane.INFORMATION_MESSAGE);
            }

            @Override
            public void error(String m) {
                lastMessage = m;
                if (!headless()) JOptionPane.showMessageDialog(EditorPanel.this, m, "오류", JOptionPane.ERROR_MESSAGE);
            }

            @Override
            public boolean confirm(String title, String m) {
                if (headless()) return false;
                return JOptionPane.showConfirmDialog(EditorPanel.this, m, title, JOptionPane.YES_NO_OPTION)
                        == JOptionPane.YES_OPTION;
            }

            @Override
            public int confirmSave(String fileName) {
                if (headless()) return JOptionPane.CANCEL_OPTION;
                return JOptionPane.showConfirmDialog(EditorPanel.this,
                        "'" + fileName + "'에 저장하지 않은 변경 사항이 있습니다. 저장할까요?",
                        "확인", JOptionPane.YES_NO_CANCEL_OPTION);
            }

            @Override
            public File chooseSaveFile(File startDir, String suggestedName) {
                if (headless()) return null;
                JFileChooser c = new JFileChooser(startDir);
                c.setSelectedFile(new File(startDir, suggestedName));
                return c.showSaveDialog(EditorPanel.this) == JFileChooser.APPROVE_OPTION ? c.getSelectedFile() : null;
            }

            @Override
            public File[] chooseFiles(File startDir, String description, String... extensions) {
                if (headless()) return new File[0];
                JFileChooser c = new JFileChooser(startDir);
                c.setMultiSelectionEnabled(true);
                if (extensions.length > 0) c.setFileFilter(new FileNameExtensionFilter(description, extensions));
                return c.showOpenDialog(EditorPanel.this) == JFileChooser.APPROVE_OPTION
                        ? c.getSelectedFiles() : new File[0];
            }

            @Override
            public File chooseFolder(File startDir) {
                if (headless()) return null;
                JFileChooser c = new JFileChooser(startDir);
                c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                return c.showOpenDialog(EditorPanel.this) == JFileChooser.APPROVE_OPTION ? c.getSelectedFile() : null;
            }

            @Override
            public String askText(String title, String prompt, String initial) {
                if (headless()) return null;
                Object r = JOptionPane.showInputDialog(EditorPanel.this, prompt, title,
                        JOptionPane.QUESTION_MESSAGE, null, null, initial);
                return r == null ? null : r.toString();
            }

            @Override
            public Object choose(String title, String prompt, Object[] options) {
                if (headless() || options.length == 0) return null;
                return JOptionPane.showInputDialog(EditorPanel.this, prompt, title,
                        JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            }
        }

        // =============================================================
        // 탭: 열린 파일 하나
        // =============================================================

        class EditorTab {
            final CodePane pane = new CodePane();
            final StyledDocument doc = pane.getStyledDocument();
            final JavaHighlighter highlighter = new JavaHighlighter(doc);
            final JScrollPane scroll = new JScrollPane(pane);
            final UndoManager undo = new UndoManager();
            final Timer highlightTimer;
            final JLabel titleLabel = new JLabel();
            final String untitledName;
            File file;
            boolean modified;
            boolean bom;
            boolean programmaticChange; // 프로그램이 글을 바꾸는 중에는 자동완성을 띄우지 않는다
            String lineSeparator = System.lineSeparator();

            EditorTab(File file) {
                this.file = file;
                this.untitledName = (file == null) ? "제목 없음 " + (++untitledCounter) : null;

                pane.setBackground(Color.WHITE);
                pane.setMargin(new Insets(4, 6, 4, 6));
                // JTextPane은 setFont 대신 기본 스타일에 글꼴을 지정해야 안정적이다
                Style defaultStyle = doc.getStyle(StyleContext.DEFAULT_STYLE);
                StyleConstants.setFontFamily(defaultStyle, Font.MONOSPACED);
                StyleConstants.setFontSize(defaultStyle, 14);

                highlightTimer = new Timer(120, e -> applyHighlight());
                highlightTimer.setRepeats(false);
                undo.setLimit(1000);

                // 글자가 추가/삭제될 때만 반응한다. 색깔 변경은 changedUpdate로 오는데 무시해야 무한 반복이 없다.
                doc.addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        changed();
                        if (!programmaticChange && e.getLength() == 1) {
                            SwingUtilities.invokeLater(() -> { if (current() == EditorTab.this) showCompletion(false); });
                        }
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        changed();
                        if (!programmaticChange && popup.isVisible()) {
                            SwingUtilities.invokeLater(() -> { if (current() == EditorTab.this) showCompletion(false); });
                        }
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) { /* 색깔 변경은 무시 */ }
                });
                // 실행 취소 기록: 글자 변경만 담는다 (색칠은 기록하지 않는다)
                doc.addUndoableEditListener(e -> recordEdit(e.getEdit()));

                installKeys();
                pane.tooltipAt = offset -> problemTooltipAt(this, offset);
                refreshTitle();
            }

            boolean isJava() { return file == null || file.getName().endsWith(".java"); }

            String displayName() { return file != null ? file.getName() : untitledName; }

            String text() {
                try {
                    return doc.getText(0, doc.getLength());
                } catch (BadLocationException e) {
                    return "";
                }
            }

            private void changed() {
                modified = true;
                docVersion++;
                refreshTitle();
                highlightTimer.restart();
                checkTimer.restart();
            }

            void refreshTitle() {
                titleLabel.setText((modified ? "*" : "") + displayName());
                titleLabel.setToolTipText(file != null ? file.getPath() : displayName());
                if (current() == this) updateFrameTitle();
            }

            void applyHighlight() {
                if (isJava()) highlighter.highlight(); else highlighter.clear();
            }

            private CompoundEdit typingGroup; // 이어서 입력 중인 글자들의 묶음 (되돌리기 한 번에 함께 되돌린다)
            private int typingEnd;
            private long typingTime;

            private void endTypingGroup() {
                if (typingGroup != null) {
                    typingGroup.end();
                    typingGroup = null;
                }
            }

            /**
             * 되돌리기 기록에 넣는다.
             * - 색칠(CHANGE)은 기록하지 않는다.
             * - 한 글자씩 이어서 입력한 글은 한 묶음으로 만든다 (1초 이상 쉬거나, 위치가 바뀌거나, Enter를 누르면 새 묶음).
             */
            private void recordEdit(javax.swing.undo.UndoableEdit edit) {
                if (edit instanceof AbstractDocument.DefaultDocumentEvent de) {
                    if (de.getType() == DocumentEvent.EventType.CHANGE) return;
                    boolean typed = de.getType() == DocumentEvent.EventType.INSERT && de.getLength() == 1;
                    long now = System.currentTimeMillis();
                    if (typed && typingGroup != null && de.getOffset() == typingEnd && now - typingTime < 1000) {
                        typingGroup.addEdit(de);
                        typingEnd++;
                        typingTime = now;
                        if (isNewline(de)) endTypingGroup();
                        return;
                    }
                    endTypingGroup();
                    if (typed) {
                        typingGroup = new CompoundEdit();
                        typingGroup.addEdit(de);
                        typingEnd = de.getOffset() + 1;
                        typingTime = now;
                        undo.addEdit(typingGroup);
                        if (isNewline(de)) endTypingGroup();
                        return;
                    }
                } else {
                    endTypingGroup();
                }
                undo.addEdit(edit);
            }

            private boolean isNewline(AbstractDocument.DefaultDocumentEvent de) {
                try {
                    return doc.getText(de.getOffset(), 1).equals("\n");
                } catch (BadLocationException e) {
                    return false;
                }
            }

            /** 파일 내용을 불러와서 보여 준다 (실행 취소 기록은 비운다) */
            void load(String content) {
                programmaticChange = true;
                try {
                    pane.setText(content);
                } finally {
                    programmaticChange = false;
                }
                pane.setCaretPosition(0);
                typingGroup = null;
                undo.discardAllEdits();
                modified = false;
                refreshTitle();
                applyHighlight();
            }

            void undo() {
                popup.hide();
                endTypingGroup();
                try {
                    if (undo.canUndo()) undo.undo();
                } catch (CannotUndoException ignored) {
                    // 더 되돌릴 것이 없다
                }
            }

            void redo() {
                popup.hide();
                endTypingGroup();
                try {
                    if (undo.canRedo()) undo.redo();
                } catch (CannotRedoException ignored) {
                    // 더 되돌릴 것이 없다
                }
            }

            private boolean swallowTyped; // 확장 프로그램이 처리한 Tab/Esc 뒤에 따라오는 글자 입력을 막는다

            private void installKeys() {
                pane.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        swallowTyped = false;
                        // 확장 프로그램의 Tab/Esc 처리 (자동완성 목록이 떠 있을 때는 목록이 먼저다)
                        int code = e.getKeyCode();
                        if (!popup.isVisible() && (code == KeyEvent.VK_TAB || code == KeyEvent.VK_ESCAPE)
                                && !e.isShiftDown() && !e.isControlDown() && !e.isAltDown()) {
                            boolean tab = code == KeyEvent.VK_TAB;
                            for (KeyHook k : keyHooks) {
                                if (k.tab() == tab && k.fn().getAsBoolean()) {
                                    swallowTyped = true;
                                    e.consume();
                                    return;
                                }
                            }
                        }
                        // Ctrl+Space: 자동완성 강제 호출
                        if (e.getKeyCode() == KeyEvent.VK_SPACE && e.isControlDown()) {
                            showCompletion(true);
                            e.consume();
                            return;
                        }
                        if (!popup.isVisible()) return;

                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_DOWN -> { popup.move(1); e.consume(); }
                            case KeyEvent.VK_UP -> { popup.move(-1); e.consume(); }
                            case KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> { acceptCompletion(); e.consume(); }
                            case KeyEvent.VK_ESCAPE -> { popup.hide(); e.consume(); }
                            case KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_HOME, KeyEvent.VK_END,
                                 KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN -> popup.hide();
                            default -> { }
                        }
                    }

                    @Override
                    public void keyTyped(KeyEvent e) {
                        // Ctrl+Space가 공백 문자로 입력되는 것을 막는다
                        if (e.isControlDown() && e.getKeyChar() == ' ') e.consume();
                        // 확장 프로그램이 처리한 Tab/Esc가 탭 문자나 특수 글자로 들어가지 않게 한다
                        if (swallowTyped) {
                            e.consume();
                            swallowTyped = false;
                        }
                    }
                });
                pane.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) { popup.hide(); }
                });
                pane.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) { popup.hide(); }
                });

                InputMap im = pane.getInputMap();
                ActionMap am = pane.getActionMap();
                im.put(ctrl(KeyEvent.VK_Z), "mj-undo");
                im.put(ctrl(KeyEvent.VK_Y), "mj-redo");
                im.put(ctrlShift(KeyEvent.VK_Z), "mj-redo");
                am.put("mj-undo", new AbstractAction() {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent e) { undo(); }
                });
                am.put("mj-redo", new AbstractAction() {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent e) { redo(); }
                });
            }
        }

        // ---- 탭 관리 ----

        List<EditorTab> tabs() {
            List<EditorTab> list = new ArrayList<>();
            for (int i = 0; i < editorTabs.getTabCount(); i++) {
                EditorTab t = tabsByComponent.get(editorTabs.getComponentAt(i));
                if (t != null) list.add(t);
            }
            return list;
        }

        EditorTab current() {
            Component c = editorTabs.getSelectedComponent();
            return c == null ? null : tabsByComponent.get(c);
        }

        private void onTabSelected() {
            popup.hide();
            updateFrameTitle();
            EditorTab t = current();
            if (t != null) SwingUtilities.invokeLater(() -> t.pane.requestFocusInWindow()); // 바로 타이핑할 수 있게
        }

        private EditorTab addTab(EditorTab tab) {
            tabsByComponent.put(tab.scroll, tab);
            editorTabs.addTab("", tab.scroll);
            int index = editorTabs.indexOfComponent(tab.scroll);
            editorTabs.setTabComponentAt(index, tabHeader(tab));
            editorTabs.setSelectedIndex(index);
            SwingUtilities.invokeLater(() -> tab.pane.requestFocusInWindow());
            docVersion++;
            checkTimer.restart();
            return tab;
        }

        /** 탭 제목 + 닫기(×) 버튼 */
        private JComponent tabHeader(EditorTab tab) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            p.setOpaque(false);
            JButton close = new JButton("×");
            close.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            close.setContentAreaFilled(false);
            close.setFocusable(false);
            close.addActionListener(e -> closeTab(tab));
            p.add(tab.titleLabel);
            p.add(close);
            // 탭 제목에 툴팁이 있으면 Swing이 마우스 클릭을 가로채서 탭이 안 바뀐다. 직접 처리해 준다.
            // 가운데 버튼(휠 클릭)은 탭 닫기.
            MouseAdapter select = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isMiddleMouseButton(e)) closeTab(tab);
                    else editorTabs.setSelectedComponent(tab.scroll);
                }
            };
            p.addMouseListener(select);
            tab.titleLabel.addMouseListener(select);
            return p;
        }

        EditorTab newFile() {
            return addTab(new EditorTab(null));
        }

        private void openFilesDialog() {
            File start = projectRoot != null ? projectRoot : lastDirectory;
            for (File f : dialogs.chooseFiles(start, "모든 파일")) openPath(f);
        }

        /** 파일을 새 탭으로 연다. 이미 열려 있으면 그 탭으로 이동한다. 열 수 없으면 null. */
        EditorTab openPath(File f) {
            File abs = f.getAbsoluteFile();
            for (EditorTab t : tabs()) {
                if (t.file != null && t.file.getAbsoluteFile().equals(abs)) {
                    editorTabs.setSelectedComponent(t.scroll);
                    return t;
                }
            }
            try {
                if (!f.isFile()) {
                    dialogs.error("파일을 찾을 수 없습니다: " + f);
                    return null;
                }
                if (f.length() > MAX_OPEN_BYTES) {
                    dialogs.error("파일이 너무 큽니다 (5MB 초과): " + f.getName());
                    return null;
                }
                byte[] bytes = Files.readAllBytes(f.toPath());
                for (int i = 0; i < Math.min(bytes.length, 4096); i++) {
                    if (bytes[i] == 0) {
                        dialogs.error("텍스트 파일이 아닌 것 같아 열지 않습니다: " + f.getName());
                        return null;
                    }
                }
                String content;
                try {
                    content = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString();
                } catch (CharacterCodingException e) {
                    dialogs.error("UTF-8이 아닌 파일은 열지 않습니다 (저장할 때 글자가 깨지는 것을 막기 위해): " + f.getName());
                    return null;
                }

                boolean hasBom = content.startsWith("\uFEFF");
                if (hasBom) content = content.substring(1);
                // 줄바꿈 방식(\r\n 또는 \n)을 기억해 두었다가 저장할 때 그대로 돌려준다
                String sep = content.contains("\r\n") ? "\r\n" : "\n";
                content = content.replace("\r\n", "\n").replace("\r", "\n");

                EditorTab tab = new EditorTab(f);
                tab.lineSeparator = sep;
                tab.bom = hasBom;
                addTab(tab);
                tab.load(content);
                lastDirectory = abs.getParentFile();
                if (projectRoot == null) terminal.setWorkingDirectory(abs.getParentFile());
                return tab;
            } catch (IOException e) {
                dialogs.error("파일을 열 수 없습니다: " + e.getMessage());
                return null;
            }
        }

        /** 탭을 닫는다. 저장하지 않은 내용이 있으면 물어본다. 취소하면 false. */
        boolean closeTab(EditorTab tab) {
            if (tab.modified && !confirmSaveBeforeClose(tab)) return false;
            removeTab(tab);
            return true;
        }

        private void removeTab(EditorTab tab) {
            popup.hide();
            tab.highlightTimer.stop();
            editorTabs.remove(tab.scroll);
            tabsByComponent.remove(tab.scroll);
            for (int i = problemModel.size() - 1; i >= 0; i--) {
                if (problemModel.get(i).tab() == tab) problemModel.remove(i);
            }
            docVersion++;
            checkTimer.restart(); // 열린 파일이 바뀌었으니 다시 검사한다
            updateFrameTitle();
        }

        private boolean confirmSaveBeforeClose(EditorTab tab) {
            editorTabs.setSelectedComponent(tab.scroll);
            int answer = dialogs.confirmSave(tab.displayName());
            if (answer == JOptionPane.YES_OPTION) return save(tab);
            return answer == JOptionPane.NO_OPTION;
        }

        /** 창을 닫기 전에 호출한다. 취소하면 false. */
        boolean requestClose() {
            for (EditorTab t : tabs()) {
                if (t.modified && !confirmSaveBeforeClose(t)) return false;
            }
            plugins.shutdown();
            terminal.stop();
            checker.shutdownNow();
            background.shutdownNow();
            closeLibLoader();
            return true;
        }

        /** 시작할 때 받은 폴더나 파일을 연다. 아무것도 안 열리면 빈 탭 하나를 만든다. */
        void openArguments(List<String> args) {
            for (String a : args) {
                File f = new File(a);
                if (f.isDirectory()) openFolder(f);
                else if (f.isFile()) openPath(f);
            }
            if (tabs().isEmpty()) newFile();
        }

        void updateFrameTitle() {
            EditorTab t = current();
            String project = projectRoot == null ? ""
                    : (projectRoot.getName().isEmpty() ? projectRoot.getPath() : projectRoot.getName()) + " - ";
            titleListener.accept((t != null ? t.displayName() + " - " : "") + project + "Mini Java Editor");
        }

        // =============================================================
        // 저장
        // =============================================================

        boolean save(EditorTab tab) {
            return tab.file != null ? writeTo(tab, tab.file) : saveAs(tab);
        }

        boolean saveAs(EditorTab tab) {
            String suggested = tab.file != null ? tab.file.getName()
                    : (tab.isJava() ? JavaChecker.guessFileName(tab.text()) : "새 파일.txt");
            File dir = tab.file != null ? tab.file.getAbsoluteFile().getParentFile()
                    : (projectRoot != null ? projectRoot : lastDirectory);
            File chosen = dialogs.chooseSaveFile(dir, suggested);
            if (chosen == null) return false;
            if (chosen.exists() && !chosen.getAbsoluteFile().equals(tab.file == null ? null : tab.file.getAbsoluteFile())
                    && !dialogs.confirm("덮어쓰기", chosen.getName() + " 파일이 이미 있습니다. 덮어쓸까요?")) {
                return false;
            }
            return writeTo(tab, chosen);
        }

        /** 저장하지 않은 변경이 있는 파일(저장 위치가 있는 것)을 모두 저장한다 */
        void saveAll() {
            for (EditorTab t : tabs()) {
                if (t.modified && t.file != null) writeTo(t, t.file);
            }
        }

        private boolean writeTo(EditorTab tab, File file) {
            try {
                String text = tab.text();
                if (!tab.lineSeparator.equals("\n")) text = text.replace("\n", tab.lineSeparator);
                byte[] body = text.getBytes(StandardCharsets.UTF_8);
                if (tab.bom) {
                    byte[] all = new byte[body.length + 3];
                    all[0] = (byte) 0xEF;
                    all[1] = (byte) 0xBB;
                    all[2] = (byte) 0xBF;
                    System.arraycopy(body, 0, all, 3, body.length);
                    body = all;
                }
                Files.write(file.toPath(), body);
            } catch (IOException e) {
                dialogs.error("저장에 실패했습니다: " + e.getMessage());
                return false;
            }

            File abs = file.getAbsoluteFile();
            // 같은 파일이 다른 탭에도 열려 있으면 그 탭은 닫는다 (한 파일이 두 곳에서 편집되지 않게)
            for (EditorTab other : tabs()) {
                if (other != tab && other.file != null && other.file.getAbsoluteFile().equals(abs)) removeTab(other);
            }

            tab.file = file;
            tab.modified = false;
            tab.refreshTitle();
            tab.applyHighlight();
            lastDirectory = abs.getParentFile();
            docVersion++;
            checkTimer.restart(); // 파일 이름이 정해지면 검사 결과가 달라질 수 있다
            if (projectRoot != null && isInside(abs, projectRoot)) {
                rebuildTree();
                updateProjectTypes();
            } else if (projectRoot == null) {
                terminal.setWorkingDirectory(abs.getParentFile());
            }
            fireFileSaved(file);
            if (file.getName().equals("pom.xml")) {
                statusLabel.setText("pom.xml을 저장했습니다. 라이브러리 > 내려받기(F7)로 반영하세요");
            }
            return true;
        }

        private static boolean isInside(File file, File dir) {
            return file.getAbsoluteFile().toPath().normalize()
                    .startsWith(dir.getAbsoluteFile().toPath().normalize());
        }

        private void fireFileSaved(File file) {
            for (SavedListener l : savedListeners) {
                try {
                    l.fn().accept(file);
                } catch (Exception | LinkageError e) {
                    terminal.notice("[확장 프로그램 오류] 저장 알림 처리 중: " + e);
                }
            }
        }

        // =============================================================
        // 프로젝트 탐색기
        // =============================================================

        /** 탐색기 트리의 항목 */
        private static final class FileNode {
            final File file;
            final boolean root;

            FileNode(File file, boolean root) {
                this.file = file;
                this.root = root;
            }

            @Override
            public String toString() {
                String name = file.getName();
                return name.isEmpty() ? file.getPath() : name;
            }
        }

        File projectRoot() { return projectRoot; }

        private void openFolderDialog() {
            File dir = dialogs.chooseFolder(projectRoot != null ? projectRoot : lastDirectory);
            if (dir != null) openFolder(dir);
        }

        /** 폴더를 프로젝트로 연다 (왼쪽 탐색기에 표시) */
        void openFolder(File dir) {
            projectRoot = dir.getAbsoluteFile();
            lastDirectory = projectRoot;
            terminal.setWorkingDirectory(projectRoot);
            rebuildTree();
            updateProjectTypes();
            updateFrameTitle();
            docVersion++;
            checkTimer.restart();
            File pom = new File(projectRoot, "pom.xml");
            if (pom.isFile()) resolveLibraries(true); // 이미 내려받은 것만 조용히 읽어 온다
            else setLibraries(List.of());
        }

        private void rebuildTree() {
            Set<String> expanded = new HashSet<>();
            Enumeration<TreePath> en = projectTree.getExpandedDescendants(new TreePath(treeRoot.getPath()));
            while (en != null && en.hasMoreElements()) {
                Object o = ((DefaultMutableTreeNode) en.nextElement().getLastPathComponent()).getUserObject();
                if (o instanceof FileNode fn) expanded.add(fn.file.getPath());
            }

            treeRoot.removeAllChildren();
            if (projectRoot == null) {
                treeRoot.setUserObject("폴더를 열어 주세요 (파일 > 폴더 열기)");
            } else {
                treeRoot.setUserObject(new FileNode(projectRoot, true));
                fillChildren(treeRoot, projectRoot);
            }
            treeModel.reload();
            if (projectRoot != null) {
                projectTree.expandPath(new TreePath(treeRoot.getPath()));
                reexpand(treeRoot, expanded);
            }
        }

        private void reexpand(DefaultMutableTreeNode node, Set<String> expanded) {
            for (int i = 0; i < node.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                if (child.getUserObject() instanceof FileNode fn && fn.file.isDirectory()
                        && expanded.contains(fn.file.getPath())) {
                    projectTree.expandPath(new TreePath(child.getPath())); // 펼칠 때 자식이 불러와진다
                    reexpand(child, expanded);
                }
            }
        }

        private void fillChildren(DefaultMutableTreeNode node, File dir) {
            File[] kids = dir.listFiles();
            if (kids == null) return;
            List<File> list = new ArrayList<>(List.of(kids));
            list.sort(Comparator.comparing((File f) -> !f.isDirectory())
                    .thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
            for (File k : list) {
                String name = k.getName();
                if (isIgnoredName(name)) continue;
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(new FileNode(k, false));
                if (k.isDirectory()) child.add(new DefaultMutableTreeNode("…")); // 펼칠 때 불러온다
                node.add(child);
            }
        }

        private static boolean isIgnoredName(String name) {
            return name.startsWith(".") || name.equals("node_modules");
        }

        private void loadChildrenIfNeeded(DefaultMutableTreeNode node) {
            if (node.getChildCount() == 1
                    && ((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject() instanceof String
                    && node.getUserObject() instanceof FileNode fn) {
                node.removeAllChildren();
                fillChildren(node, fn.file);
                treeModel.nodeStructureChanged(node);
            }
        }

        private File selectedTreeFile() {
            TreePath p = projectTree.getSelectionPath();
            if (p == null) return null;
            Object o = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            return o instanceof FileNode fn ? fn.file : null;
        }

        private void openSelectedTreeFile() {
            File f = selectedTreeFile();
            if (f != null && f.isFile()) openPath(f);
        }

        private void showTreeMenu(MouseEvent e) {
            if (!e.isPopupTrigger()) return;
            TreePath p = projectTree.getPathForLocation(e.getX(), e.getY());
            if (p != null) projectTree.setSelectionPath(p);
            JPopupMenu menu = new JPopupMenu();
            menu.add(item("새 Java 클래스...", this::newJavaClass, null));
            menu.add(item("새로 고침", () -> { rebuildTree(); updateProjectTypes(); }, null));
            menu.show(projectTree, e.getX(), e.getY());
        }

        /** 프로젝트의 .java 파일 이름들을 모아 자동완성 후보(클래스 이름)로 쓴다 */
        private void updateProjectTypes() {
            File root = projectRoot;
            if (root == null) {
                projectTypeNames = Set.of();
                return;
            }
            background.submit(() -> {
                Set<String> names = new HashSet<>();
                try (Stream<Path> s = Files.walk(root.toPath(), 8)) {
                    s.filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".java"))
                            .filter(p -> {
                                for (Path part : root.toPath().relativize(p)) {
                                    if (isIgnoredName(part.toString())) return false;
                                }
                                return true;
                            })
                            .limit(5000)
                            .forEach(p -> {
                                String n = p.getFileName().toString();
                                names.add(n.substring(0, n.length() - 5));
                            });
                } catch (IOException | RuntimeException e) {
                    // 폴더를 읽지 못하면 이름 목록 없이 계속한다
                }
                projectTypeNames = names;
            });
        }

        Set<String> projectTypeNames() { return projectTypeNames; }

        /** 지금 열린 파일과 프로젝트 폴더로부터 "소스 루트" 후보를 모은다 */
        List<File> sourceRoots() {
            Set<File> roots = new LinkedHashSet<>();
            if (projectRoot != null) {
                for (String sub : List.of("src/main/java", "src")) {
                    File d = new File(projectRoot, sub);
                    if (d.isDirectory()) roots.add(d);
                }
                roots.add(projectRoot);
            }
            for (EditorTab t : tabs()) {
                if (t.file != null && t.isJava()) {
                    File r = JavaChecker.deriveSourceRoot(t.file, t.text());
                    if (r != null) roots.add(r);
                }
            }
            return new ArrayList<>(roots);
        }

        /** 새 Java 클래스 파일을 만든다 (선택한 폴더에, 폴더 구조에 맞는 package 선언과 함께) */
        void newJavaClass() {
            File sel = selectedTreeFile();
            File dir = sel != null ? (sel.isDirectory() ? sel : sel.getParentFile()) : projectRoot;
            if (dir == null) {
                dialogs.error("먼저 폴더를 여세요 (파일 > 폴더 열기).");
                return;
            }
            String name = dialogs.askText("새 Java 클래스", "클래스 이름 (예: Calculator):", "");
            if (name == null) return;
            name = name.trim().replaceFirst("\\.java$", "");
            if (!isValidClassName(name)) {
                dialogs.error("클래스 이름이 올바르지 않습니다: '" + name + "' (영문/숫자/_ 로 이루어지고, 숫자로 시작할 수 없습니다)");
                return;
            }
            File target = new File(dir, name + ".java");
            if (target.exists()) {
                dialogs.error("이미 있는 파일입니다: " + target.getName());
                return;
            }
            String pkg = JavaChecker.packageForDir(dir, sourceRoots());
            String content = (pkg.isEmpty() ? "" : "package " + pkg + ";\n\n") + "public class " + name + " {\n\n}\n";
            try {
                Files.writeString(target.toPath(), content);
            } catch (IOException e) {
                dialogs.error("파일을 만들 수 없습니다: " + e.getMessage());
                return;
            }
            rebuildTree();
            updateProjectTypes();
            openPath(target);
        }

        static boolean isValidClassName(String name) {
            if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) return false;
            if (!name.chars().allMatch(Character::isJavaIdentifierPart)) return false;
            return !Set.of("class", "interface", "enum", "record", "int", "void", "new", "static", "public",
                    "private", "protected", "final", "abstract", "import", "package", "null", "true", "false",
                    "this", "super", "return", "if", "else", "for", "while", "do", "switch", "case", "try",
                    "catch", "throw", "throws", "boolean", "byte", "char", "short", "long", "float", "double",
                    "extends", "implements", "default", "break", "continue", "goto", "const", "instanceof",
                    "native", "synchronized", "transient", "volatile", "strictfp", "assert", "finally")
                    .contains(name);
        }

        // =============================================================
        // 자동완성 (3단계)
        // =============================================================

        /** 현재 탭의 커서 위치를 기준으로 후보를 계산해서 목록을 띄우거나 닫는다 */
        void showCompletion(boolean forced) {
            EditorTab t = current();
            if (t == null || !t.isJava() || !t.pane.isShowing()) {
                popup.hide();
                return;
            }
            String text = t.text();
            CompletionEngine.Result result = CompletionEngine.complete(text, t.pane.getCaretPosition(), forced,
                    new CompletionEngine.Extras(projectTypeNames, libLoader));
            if (result.items().isEmpty()) {
                popup.hide();
            } else {
                popup.show(result, t.pane);
            }
        }

        /** 선택된 후보를 문서에 넣는다 */
        private void acceptCompletion() {
            EditorTab t = current();
            CompletionEngine.Item item = popup.selected();
            int start = popup.replaceStart();
            popup.hide();
            if (t == null || item == null) return;

            String text = t.text();
            int caret = t.pane.getCaretPosition();
            if (start > caret || caret > text.length()) return;

            CompletionEngine.Expansion expansion =
                    CompletionEngine.expand(item, CompletionEngine.lineIndent(text, start));

            t.programmaticChange = true;
            try {
                t.pane.select(start, caret);
                t.pane.replaceSelection(expansion.text());
                t.pane.setCaretPosition(start + expansion.cursor());
            } finally {
                t.programmaticChange = false;
            }
        }

        /** 자동완성 팝업 (화면 부분). 후보 계산은 CompletionEngine이 한다. */
        private class CompletionPopup {

            private static final int MAX_ROWS = 8;

            private JWindow window; // 처음 보일 때 만든다
            private final DefaultListModel<CompletionEngine.Item> model = new DefaultListModel<>();
            private final JList<CompletionEngine.Item> list = new JList<>(model);
            private int replaceStart = 0;

            CompletionPopup() {
                list.setFocusable(false); // 목록이 키보드 포커스를 가져가면 타이핑이 끊긴다
                list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
                list.setCellRenderer(new ItemRenderer());
                list.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2) acceptCompletion(); // 더블클릭으로 선택
                    }
                });
            }

            private JWindow window() {
                if (window == null) {
                    window = new JWindow(SwingUtilities.getWindowAncestor(EditorPanel.this));
                    window.setFocusableWindowState(false);
                    JScrollPane scroll = new JScrollPane(list);
                    scroll.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                    window.setContentPane(scroll);
                }
                return window;
            }

            boolean isVisible() { return window != null && window.isVisible(); }

            void hide() {
                if (isVisible()) window.setVisible(false);
            }

            int replaceStart() { return replaceStart; }

            CompletionEngine.Item selected() { return list.getSelectedValue(); }

            /** 선택 항목을 위/아래로 옮긴다 (끝에서 반대편으로 이어진다) */
            void move(int delta) {
                int size = model.size();
                if (size == 0) return;
                int next = (list.getSelectedIndex() + delta + size) % size;
                list.setSelectedIndex(next);
                list.ensureIndexIsVisible(next);
            }

            void show(CompletionEngine.Result result, JTextComponent pane) {
                try {
                    Rectangle2D caretRect = pane.modelToView2D(result.replaceStart());
                    if (caretRect == null) { hide(); return; }

                    replaceStart = result.replaceStart();
                    model.clear();
                    result.items().forEach(model::addElement);
                    list.setSelectedIndex(0);
                    list.ensureIndexIsVisible(0);
                    list.setVisibleRowCount(Math.min(MAX_ROWS, model.size()));

                    JWindow w = window();
                    w.pack();
                    Dimension size = w.getSize();
                    if (size.width < 300) {
                        size.width = 300;
                        w.setSize(size);
                    }

                    // 글자 바로 아래에 띄우고, 화면 아래쪽이 모자라면 위로 띄운다
                    Point below = new Point((int) caretRect.getX(), (int) (caretRect.getY() + caretRect.getHeight()));
                    Point above = new Point((int) caretRect.getX(), (int) caretRect.getY() - size.height);
                    SwingUtilities.convertPointToScreen(below, pane);
                    SwingUtilities.convertPointToScreen(above, pane);

                    Rectangle screen = pane.getGraphicsConfiguration().getBounds();
                    Point location = below;
                    if (below.y + size.height > screen.y + screen.height) location = above;
                    if (location.x + size.width > screen.x + screen.width) {
                        location.x = screen.x + screen.width - size.width;
                    }

                    w.setLocation(location);
                    w.setVisible(true);
                } catch (BadLocationException ex) {
                    hide();
                }
            }
        }

        // =============================================================
        // 오류 표시 (4단계): 열려 있는 모든 Java 파일을 함께 검사한다
        // =============================================================

        private record CheckRequest(List<JavaChecker.Unit> units, Map<String, EditorTab> byId,
                                    List<File> roots, List<File> jars, int version) { }

        private CheckRequest buildCheckRequest() {
            List<JavaChecker.Unit> units = new ArrayList<>();
            Map<String, EditorTab> byId = new LinkedHashMap<>();
            Set<String> names = new HashSet<>();

            // 저장된 파일 먼저
            for (EditorTab t : tabs()) {
                if (t.file == null || !t.isJava()) continue;
                String id = t.file.getAbsolutePath();
                units.add(new JavaChecker.Unit(id, t.file.getName(), t.text()));
                byId.put(id, t);
                names.add(t.file.getName());
            }
            // 저장하지 않은 새 글은 public 클래스 이름으로 파일 이름을 추측한다. 이름이 겹치면 검사에서 뺀다.
            for (EditorTab t : tabs()) {
                if (t.file != null || !t.isJava()) continue;
                String text = t.text();
                String name = JavaChecker.guessFileName(text);
                if (!names.add(name)) continue;
                String id = "untitled:" + t.untitledName;
                units.add(new JavaChecker.Unit(id, name, text));
                byId.put(id, t);
            }
            return new CheckRequest(units, byId, sourceRoots(), libJars, docVersion);
        }

        /** 열려 있는 Java 파일들을 검사한다. 오래 걸릴 수 있어서 백그라운드에서 실행한다. */
        void runCheck() {
            if (!JavaChecker.isAvailable()) {
                statusLabel.setText("오류 검사에는 JRE가 아니라 JDK가 필요합니다 (Temurin JDK 21 설치)");
                return;
            }
            CheckRequest req = buildCheckRequest();
            statusLabel.setText("검사 중…");
            checker.submit(() -> {
                Map<String, List<JavaChecker.Problem>> result;
                try {
                    result = JavaChecker.checkAll(req.units(), req.roots(), req.jars());
                } catch (Throwable t) {
                    result = null; // 검사기 내부 문제. 편집기는 계속 동작해야 한다.
                }
                Map<String, List<JavaChecker.Problem>> done = result;
                SwingUtilities.invokeLater(() -> applyProblems(req, done));
            });
        }

        /** 테스트용: runCheck와 같은 일을 기다리면서 한 번에 한다 */
        void checkNow() throws Exception {
            CheckRequest req = buildCheckRequest();
            Map<String, List<JavaChecker.Problem>> result =
                    JavaChecker.checkAll(req.units(), req.roots(), req.jars());
            Runnable apply = () -> applyProblems(req, result);
            if (SwingUtilities.isEventDispatchThread()) apply.run();
            else SwingUtilities.invokeAndWait(apply);
        }

        private void applyProblems(CheckRequest req, Map<String, List<JavaChecker.Problem>> results) {
            // 검사하는 사이에 글이 또 바뀌었으면 이 결과는 버린다. 곧 새로 검사된다.
            if (req.version() != docVersion) return;

            clearAllProblems();
            if (results == null) {
                statusLabel.setText("검사하지 못했습니다");
                return;
            }

            int errors = 0;
            int warnings = 0;
            for (Map.Entry<String, EditorTab> en : req.byId().entrySet()) {
                EditorTab tab = en.getValue();
                if (!tabsByComponent.containsKey(tab.scroll)) continue;
                String text = tab.text();
                for (JavaChecker.Problem p : results.getOrDefault(en.getKey(), List.of())) {
                    problemModel.addElement(new FileProblem(tab.displayName(), tab, p));
                    if (p.error()) errors++; else warnings++;

                    int[] range = JavaChecker.underlineRange(text, p.start(), p.end());
                    if (range != null) {
                        try {
                            tab.pane.getHighlighter().addHighlight(range[0], range[1],
                                    new SquigglePainter(p.error() ? ERROR_COLOR : WARNING_COLOR, p));
                        } catch (BadLocationException ignored) {
                            // 글이 바뀐 경우. 밑줄만 생략한다.
                        }
                    }
                }
                tab.pane.repaint();
            }

            int total = problemModel.size();
            bottomTabs.setTitleAt(0, total == 0 ? "문제" : "문제 (" + total + ")");
            statusLabel.setText(total == 0 ? "문제가 없습니다" : "오류 " + errors + "개 · 경고 " + warnings + "개");
        }

        private void clearAllProblems() {
            for (EditorTab t : tabs()) {
                Highlighter h = t.pane.getHighlighter();
                for (Highlighter.Highlight hl : h.getHighlights()) {
                    if (hl.getPainter() instanceof SquigglePainter) h.removeHighlight(hl);
                }
            }
            problemModel.clear();
            bottomTabs.setTitleAt(0, "문제");
        }

        /** 목록에서 고른 문제가 있는 파일/위치로 이동한다 */
        private void goToSelectedProblem() {
            FileProblem fp = problemList.getSelectedValue();
            if (fp == null || !(fp.tab() instanceof EditorTab tab) || !tabsByComponent.containsKey(tab.scroll)) return;
            editorTabs.setSelectedComponent(tab.scroll);
            String text = tab.text();
            JavaChecker.Problem p = fp.problem();
            int offset = (p.start() >= 0)
                    ? Math.min(p.start(), text.length())
                    : JavaChecker.lineStartOffset(text, p.line());
            tab.pane.requestFocusInWindow();
            tab.pane.setCaretPosition(offset);
            try {
                Rectangle2D r = tab.pane.modelToView2D(offset);
                if (r != null) tab.pane.scrollRectToVisible(r.getBounds());
            } catch (BadLocationException ignored) {
                // 문서가 바뀐 경우
            }
        }

        /** 마우스가 가리키는 글자 위치에 문제가 있으면 설명 글(HTML)을 돌려준다 */
        private String problemTooltipAt(EditorTab tab, int offset) {
            StringBuilder sb = new StringBuilder();
            for (Highlighter.Highlight h : tab.pane.getHighlighter().getHighlights()) {
                if (h.getPainter() instanceof SquigglePainter sp
                        && offset >= h.getStartOffset() && offset <= h.getEndOffset()) {
                    if (sb.length() > 0) sb.append("<hr>");
                    sb.append(ProblemRenderer.tooltipHtml(sp.problem()));
                }
            }
            return sb.length() == 0 ? null : "<html>" + sb + "</html>";
        }

        List<FileProblem> problems() {
            List<FileProblem> list = new ArrayList<>();
            for (int i = 0; i < problemModel.size(); i++) list.add(problemModel.get(i));
            return list;
        }

        String statusText() { return statusLabel.getText(); }

        // =============================================================
        // 터미널과 실행 (5단계): F5 = 프로젝트 전체를 컴파일해서 실행
        // =============================================================

        TerminalPanel terminal() { return terminal; }

        private void showTerminal() {
            bottomTabs.setSelectedComponent(terminal);
            terminal.focusInput();
        }

        /**
         * 지금 파일을 실행한다. 다른 파일을 참조해도 된다.
         * javac로 필요한 파일들을 함께 컴파일하고, 성공하면 java로 실행한다.
         */
        void runCurrentFile() {
            EditorTab t = current();
            if (t == null) return;
            if (terminal.isRunning()) {
                showTerminal();
                terminal.notifyBusy();
                return;
            }
            // 최신 내용으로 실행하도록 먼저 저장한다 (저장한 적이 없으면 저장 위치를 물어본다)
            if (t.file == null || t.modified) {
                if (!save(t)) return;
            }
            File file = t.file;
            if (file == null || !file.getName().endsWith(".java")) {
                dialogs.error("Java 파일(.java)만 실행할 수 있습니다.");
                return;
            }
            String text = t.text();
            if (!ProjectRunner.hasMain(text)) {
                showTerminal();
                terminal.notice("[알림] 이 파일에는 main 메서드가 없어서 실행할 수 없습니다. main이 있는 파일에서 F5를 눌러 주세요.");
                return;
            }
            saveAll(); // 다른 파일의 수정 사항도 반영해서 실행한다

            ProjectRunner.Plan plan = ProjectRunner.plan(file, text, sourceRoots(), libJars,
                    Path.of(System.getProperty("java.io.tmpdir"), "minijava-out"));
            try {
                ProjectRunner.prepare(plan);
            } catch (IOException e) {
                dialogs.error("실행 준비에 실패했습니다: " + e.getMessage());
                return;
            }
            terminal.setWorkingDirectory(projectRoot != null ? projectRoot : file.getAbsoluteFile().getParentFile());
            showTerminal();
            terminal.execute(plan.display(), plan.command(terminal.shell()));
        }

        // =============================================================
        // 라이브러리 (7단계, Maven Central)
        // =============================================================

        List<File> libraries() { return libJars; }

        /** 내려받은 라이브러리를 읽는 클래스로더 (없으면 null) */
        ClassLoader libraryClassLoader() { return libLoader; }

        MavenResolver maven() { return maven; }

        void setMaven(MavenResolver resolver) { this.maven = resolver; }

        private void closeLibLoader() {
            URLClassLoader old = libLoader;
            libLoader = null;
            if (old != null) {
                try {
                    old.close();
                } catch (IOException ignored) {
                    // 닫지 못해도 큰 문제는 아니다
                }
            }
        }

        /** 라이브러리(jar) 목록을 바꾼다: 검사/실행/자동완성에 반영된다 */
        void setLibraries(List<File> jars) {
            libJars = List.copyOf(jars);
            closeLibLoader();
            if (!jars.isEmpty()) {
                List<URL> urls = new ArrayList<>();
                for (File j : jars) {
                    try {
                        urls.add(j.toURI().toURL());
                    } catch (java.net.MalformedURLException ignored) {
                        // 건너뜀
                    }
                }
                // 부모를 platform 로더로 둬서 JDK 클래스는 보이고, 편집기 자신의 클래스는 보이지 않게 한다
                libLoader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
            }
            docVersion++;
            checkTimer.restart();
        }

        /**
         * 이 프로젝트의 pom.xml을 찾는다.
         * 지금 보는 파일이 프로젝트 폴더 안에 있으면 그 파일에서 가장 가까운 pom.xml (여러 모듈 프로젝트에서는 모듈의 pom),
         * 아니면 프로젝트 폴더의 pom.xml. 프로젝트 폴더 밖의 pom.xml은 절대 고르지 않는다.
         * 폴더를 열지 않은 경우에는 지금 파일의 위쪽 폴더들에서 찾는다.
         */
        File findPom() {
            File root = projectRoot;
            EditorTab t = current();
            if (t != null && t.file != null) {
                File d = t.file.getAbsoluteFile().getParentFile();
                for (int i = 0; d != null && i < 8; i++, d = d.getParentFile()) {
                    if (root != null && !isInside(d, root)) break;
                    File p = new File(d, "pom.xml");
                    if (p.isFile()) return p;
                }
            }
            if (root != null) {
                File p = new File(root, "pom.xml");
                if (p.isFile()) return p;
            }
            return null;
        }

        /**
         * pom.xml의 라이브러리를 Maven Central에서 내려받는다.
         * @param offlineOnly true면 이미 내려받은 것만 읽고 네트워크는 쓰지 않는다
         */
        void resolveLibraries(boolean offlineOnly) {
            File pom = findPom();
            if (pom == null) {
                if (!offlineOnly) dialogs.error("pom.xml을 찾을 수 없습니다. 폴더를 열거나 '라이브러리 추가'로 만들어 주세요.");
                return;
            }
            MavenResolver resolver = maven;
            if (!offlineOnly) showTerminal();
            statusLabel.setText("라이브러리를 확인하는 중…");
            background.submit(() -> {
                try {
                    MavenResolver.Result r = resolver.resolve(pom, offlineOnly,
                            msg -> SwingUtilities.invokeLater(() -> terminal.notice("[라이브러리] " + msg)));
                    SwingUtilities.invokeLater(() -> librariesResolved(r, offlineOnly));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("라이브러리 오류");
                        terminal.notice("[라이브러리] 실패: " + e.getMessage());
                    });
                }
            });
        }

        private void librariesResolved(MavenResolver.Result r, boolean quiet) {
            setLibraries(r.jars());
            boolean problems = !r.messages().isEmpty();
            if (!quiet || problems) {
                for (String m : r.messages()) terminal.notice("[라이브러리] " + m);
            }
            statusLabel.setText(quiet && problems
                    ? "일부 라이브러리가 없습니다. 라이브러리 > 내려받기(F7)를 눌러 주세요"
                    : "라이브러리 " + r.jars().size() + "개 준비됨");
        }

        private void showLibraries() {
            showTerminal();
            if (libJars.isEmpty()) {
                terminal.notice("[라이브러리] 내려받은 라이브러리가 없습니다.");
                return;
            }
            terminal.notice("[라이브러리] " + libJars.size() + "개:");
            for (File j : libJars) terminal.notice("  " + j.getName());
        }

        /** 테스트용: 메뉴의 "라이브러리 추가"와 같은 흐름을 시작한다 */
        void addLibraryFlowForTest() { addLibraryFlow(); }

        /** Maven Central에서 라이브러리를 찾아 pom.xml에 추가한다 */
        private void addLibraryFlow() {
            String q = dialogs.askText("라이브러리 추가",
                    "검색어 (예: gson) 또는 좌표 (예: com.google.code.gson:gson:2.11.0)", "");
            if (q == null || q.isBlank()) return;
            q = q.trim();

            Coord direct = Coord.parse(q);
            if (direct != null) {
                if (direct.version() != null) {
                    addDependencyToPom(direct);
                    return;
                }
                chooseVersion(direct.group(), direct.artifact());
                return;
            }

            String query = q;
            MavenResolver resolver = maven;
            statusLabel.setText("검색 중…");
            background.submit(() -> {
                try {
                    List<MavenResolver.SearchHit> hits = resolver.search(query);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(" ");
                        if (hits.isEmpty()) {
                            dialogs.info("검색 결과가 없습니다: " + query);
                            return;
                        }
                        Object picked = dialogs.choose("라이브러리 검색 결과", "추가할 라이브러리를 고르세요:",
                                hits.toArray());
                        if (picked instanceof MavenResolver.SearchHit h) chooseVersion(h.group(), h.artifact());
                    });
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(" ");
                        dialogs.error("검색하지 못했습니다: " + e.getMessage()
                                + "\n좌표(group:artifact:version)를 직접 입력해도 됩니다.");
                    });
                }
            });
        }

        private void chooseVersion(String group, String artifact) {
            MavenResolver resolver = maven;
            statusLabel.setText("버전 확인 중…");
            background.submit(() -> {
                try {
                    List<String> versions = resolver.versions(group, artifact);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(" ");
                        if (versions.isEmpty()) {
                            dialogs.error("버전을 찾을 수 없습니다: " + group + ":" + artifact);
                            return;
                        }
                        Object v = dialogs.choose("버전 선택", group + ":" + artifact + " 의 버전 (최신순):",
                                versions.stream().limit(40).toArray());
                        if (v != null) addDependencyToPom(new Coord(group, artifact, v.toString()));
                    });
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(" ");
                        dialogs.error("버전을 가져오지 못했습니다: " + e.getMessage());
                    });
                }
            });
        }

        /** pom.xml에 의존성을 추가하고(없으면 pom.xml을 만들고) 라이브러리를 내려받는다 */
        void addDependencyToPom(Coord c) {
            File pom = findPom();
            try {
                if (pom == null) {
                    if (projectRoot == null) {
                        dialogs.error("먼저 폴더를 여세요 (파일 > 폴더 열기). 그 폴더에 pom.xml을 만들어 드립니다.");
                        return;
                    }
                    if (!dialogs.confirm("pom.xml 만들기", projectRoot.getName()
                            + " 폴더에 pom.xml이 없습니다. 새로 만들까요?")) return;
                    pom = new File(projectRoot, "pom.xml");
                    Files.writeString(pom.toPath(), PomEditor.newPom(projectRoot.getName()));
                }
                File pomFile = pom;
                EditorTab open = null;
                for (EditorTab t : tabs()) {
                    if (t.file != null && t.file.getAbsoluteFile().equals(pomFile.getAbsoluteFile())) open = t;
                }
                String before = open != null ? open.text() : Files.readString(pomFile.toPath());
                if (open != null && open.modified) {
                    dialogs.error("pom.xml에 저장하지 않은 수정이 있습니다. 먼저 저장하거나, 아래 내용을 직접 추가해 주세요:\n\n"
                            + PomEditor.dependencyXml(c, "    "));
                    return;
                }
                String after = PomEditor.addDependency(before, c);
                Files.writeString(pomFile.toPath(), after);
                if (open != null) open.load(after.replace("\r\n", "\n"));
                rebuildTree();
                terminal.notice("[라이브러리] pom.xml에 추가했습니다: " + c);
                resolveLibraries(false);
            } catch (IllegalStateException | IllegalArgumentException e) {
                dialogs.info(e.getMessage());
            } catch (IOException e) {
                dialogs.error("pom.xml을 쓸 수 없습니다: " + e.getMessage());
            }
        }

        // =============================================================
        // 확장 프로그램 (6단계)
        // =============================================================

        PluginManager plugins() { return plugins; }

        /**
         * 편집기와 함께 배포되는 기본 확장 프로그램 폴더(jar들이 들어 있는 extensions 폴더). 없으면 null.
         * 찾는 순서: 시스템 속성 minijava.extensions → 클래스 폴더(classes)의 옆 → 현재 폴더
         */
        File bundledExtensionsDir() {
            List<File> candidates = new ArrayList<>();
            String prop = System.getProperty("minijava.extensions");
            if (prop != null && !prop.isBlank()) candidates.add(new File(prop));
            try {
                java.security.CodeSource cs = MiniJavaEditor.class.getProtectionDomain().getCodeSource();
                if (cs != null && cs.getLocation() != null) {
                    File where = new File(cs.getLocation().toURI());
                    File base = where.getParentFile(); // classes 폴더(또는 jar)가 있는 곳의 위 폴더
                    if (base != null) candidates.add(new File(base, "extensions"));
                }
            } catch (Exception | LinkageError ignored) {
                // 위치를 알 수 없으면 다음 후보로 넘어간다
            }
            candidates.add(new File(System.getProperty("user.dir", "."), "extensions"));
            for (File d : candidates) {
                File[] jars = d.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
                if (jars != null && jars.length > 0) return d;
            }
            return null;
        }

        /**
         * 기본 확장 프로그램을 설치한다. 이미 같은 파일이 설치되어 있으면 그대로 두고, 꺼져 있는 것만 켠다.
         * @param askBeforeEnabling true면 켜기 전에 목록을 보여 주고 물어본다
         */
        void installBundledExtensions(boolean askBeforeEnabling) {
            File dir = bundledExtensionsDir();
            if (dir == null) {
                dialogs.info("기본 확장 프로그램 폴더(extensions)를 찾을 수 없습니다.\n"
                        + "배포 폴더의 classes 옆에 extensions 폴더(안에 jar 파일들)가 있어야 합니다.");
                return;
            }
            File[] jars = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
            if (jars == null || jars.length == 0) return;
            Arrays.sort(jars, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));

            List<PluginManager.Entry> entries = new ArrayList<>();
            for (File jar : jars) {
                try {
                    File target = new File(plugins.directory(), jar.getName());
                    boolean same = target.isFile() && Files.mismatch(target.toPath(), jar.toPath()) == -1;
                    PluginManager.Entry e;
                    if (same) {
                        plugins.scan();
                        e = plugins.entries().stream().filter(x -> x.id.equals(jar.getName())).findFirst().orElse(null);
                    } else {
                        e = plugins.install(jar);
                    }
                    if (e != null) entries.add(e);
                } catch (IOException ex) {
                    dialogs.error("설치하지 못했습니다: " + jar.getName() + " (" + ex.getMessage() + ")");
                }
            }

            List<PluginManager.Entry> toEnable = entries.stream().filter(e -> !e.loaded).toList();
            if (toEnable.isEmpty()) {
                dialogs.info("기본 확장 프로그램 " + entries.size() + "개가 이미 켜져 있습니다.");
                return;
            }
            if (askBeforeEnabling) {
                String names = toEnable.stream()
                        .map(e -> "  • " + e.name + (e.description.isEmpty() ? "" : " - " + e.description))
                        .collect(Collectors.joining("\n"));
                if (!dialogs.confirm("기본 확장 프로그램 켜기", "다음 기본 확장 프로그램을 켤까요?\n\n" + names
                        + "\n\n확장 프로그램은 이 컴퓨터에서 코드를 실행합니다. 이 편집기와 함께 제공된 것들입니다.")) {
                    return;
                }
            }
            StringBuilder failed = new StringBuilder();
            for (PluginManager.Entry e : toEnable) {
                plugins.setEnabled(e, true);
                if (!e.loaded) failed.append("\n  • ").append(e.name).append(": ").append(e.error);
            }
            if (failed.length() > 0) dialogs.error("켜지 못한 확장 프로그램이 있습니다:" + failed);
        }

        /** 처음 실행했을 때 한 번만, 기본 확장 프로그램을 설치할지 물어본다 */
        void offerBundledExtensionsOnFirstRun() {
            File marker = new File(dataDir, "bundled-extensions-offered");
            File dir = bundledExtensionsDir();
            if (marker.exists() || dir == null) return;
            try {
                Files.createDirectories(dataDir.toPath());
                Files.writeString(marker.toPath(), "offered\n"); // 거절해도 다시 묻지 않는다
            } catch (IOException ignored) {
                // 표시를 남기지 못하면 다음에 또 물어볼 수 있다
            }
            File[] jars = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
            int count = jars == null ? 0 : jars.length;
            if (dialogs.confirm("기본 확장 프로그램",
                    "이 편집기와 함께 제공되는 기본 확장 프로그램 " + count + "개를 설치하고 켤까요?\n"
                            + "(스니펫 모음, 텍스트 도구, 서식 정리, 저장 백업, 코드 도우미)\n\n"
                            + "나중에 '확장 > 기본 확장 프로그램 설치'에서도 할 수 있습니다.")) {
                installBundledExtensions(false);
            }
        }

        private PluginManager.ManagedContext createPluginContext(String pluginId) {
            return new PluginContext(pluginId);
        }

        /** 이 스레드가 화면 스레드가 아니면 화면 스레드에서 실행하고 결과를 기다린다 */
        private static <T> T onEdt(Supplier<T> s) {
            if (SwingUtilities.isEventDispatchThread()) return s.get();
            Object[] box = new Object[1];
            try {
                SwingUtilities.invokeAndWait(() -> box[0] = s.get());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            @SuppressWarnings("unchecked") T t = (T) box[0];
            return t;
        }

        private static void runLater(Runnable r) {
            if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
        }

        /** 확장 프로그램 하나에게 주는 편집기 창구. 등록한 것들을 기억해 두었다가 꺼질 때 정리한다. */
        private class PluginContext implements PluginManager.ManagedContext {
            private final String pluginId;
            private final List<Object[]> items = new ArrayList<>(); // {JMenu, JMenuItem, Boolean 메뉴를 새로 만들었는가}
            private final List<Object> snippets = new ArrayList<>();
            private final List<SavedListener> listeners = new ArrayList<>();

            PluginContext(String pluginId) {
                this.pluginId = pluginId;
            }

            private void report(Throwable t) {
                String msg = "[확장 프로그램 오류] " + pluginId + ": " + t;
                lastMessage = msg;
                terminal.notice(msg);
            }

            @Override
            public JFrame frame() {
                return onEdt(() -> SwingUtilities.getWindowAncestor(EditorPanel.this) instanceof JFrame f ? f : null);
            }

            @Override
            public String text() {
                return onEdt(() -> { EditorTab t = current(); return t == null ? "" : t.text(); });
            }

            @Override
            public String selectedText() {
                return onEdt(() -> {
                    EditorTab t = current();
                    String s = t == null ? null : t.pane.getSelectedText();
                    return s == null ? "" : s;
                });
            }

            @Override
            public void insertText(String text) {
                runLater(() -> { EditorTab t = current(); if (t != null) t.pane.replaceSelection(text); });
            }

            @Override
            public void replaceAll(String text) {
                runLater(() -> {
                    EditorTab t = current();
                    if (t == null) return;
                    try {
                        ((AbstractDocument) t.doc).replace(0, t.doc.getLength(), text, null);
                    } catch (BadLocationException e) {
                        report(e);
                    }
                });
            }

            @Override
            public File currentFile() {
                return onEdt(() -> { EditorTab t = current(); return t == null ? null : t.file; });
            }

            @Override
            public File projectRoot() {
                return onEdt(() -> projectRoot);
            }

            @Override
            public void openFile(File file) {
                runLater(() -> openPath(file));
            }

            @Override
            public void showMessage(String message) {
                runLater(() -> dialogs.info(message));
            }

            @Override
            public void runInTerminal(String command) {
                runLater(() -> { showTerminal(); terminal.execute(command, command); });
            }

            @Override
            public void addMenuItem(String menuName, String label, Runnable action) {
                runLater(() -> {
                    JMenu m = menus.get(menuName);
                    boolean created = false;
                    if (m == null) {
                        m = new JMenu(menuName);
                        menus.put(menuName, m);
                        menuBar.add(m, Math.max(0, menuBar.getMenuCount() - 1)); // "확장" 메뉴 앞에 넣는다
                        created = true;
                    }
                    JMenuItem mi = new JMenuItem(label);
                    mi.addActionListener(e -> {
                        try {
                            action.run();
                        } catch (Exception | LinkageError t) {
                            report(t);
                        }
                    });
                    m.add(mi);
                    items.add(new Object[]{m, mi, created});
                    menuBar.revalidate();
                    menuBar.repaint();
                });
            }

            @Override
            public void addSnippet(String name, String description, String template) {
                snippets.add(CompletionEngine.addSnippet(name, description, template));
            }

            @Override
            public void onFileSaved(Consumer<File> listener) {
                SavedListener l = new SavedListener(this, listener);
                listeners.add(l);
                savedListeners.add(l);
            }

            @Override
            public int caretPosition() {
                return onEdt(() -> { EditorTab t = current(); return t == null ? 0 : t.pane.getCaretPosition(); });
            }

            @Override
            public int selectionStart() {
                return onEdt(() -> { EditorTab t = current(); return t == null ? 0 : t.pane.getSelectionStart(); });
            }

            @Override
            public int selectionEnd() {
                return onEdt(() -> { EditorTab t = current(); return t == null ? 0 : t.pane.getSelectionEnd(); });
            }

            @Override
            public void onTabKey(BooleanSupplier handler) {
                keyHooks.add(new KeyHook(this, handler, true));
            }

            @Override
            public void onEscapeKey(BooleanSupplier handler) {
                keyHooks.add(new KeyHook(this, handler, false));
            }

            @Override
            public void select(int start, int end) {
                runLater(() -> {
                    EditorTab t = current();
                    if (t == null) return;
                    int len = t.doc.getLength();
                    int a = Math.max(0, Math.min(start, len));
                    int b = Math.max(0, Math.min(end, len));
                    t.pane.select(Math.min(a, b), Math.max(a, b));
                });
            }

            @Override
            public void dispose() {
                runLater(() -> {
                    for (Object[] it : items) ((JMenu) it[0]).remove((JMenuItem) it[1]);
                    items.clear();
                    // 확장 프로그램이 만든 메뉴가 비었으면 메뉴 자체도 없앤다 (기본 메뉴는 그대로 둔다)
                    for (JMenu m : new ArrayList<>(menus.values())) {
                        if (m.getItemCount() == 0 && !BUILT_IN_MENUS.contains(m.getText())) {
                            menuBar.remove(m);
                            menus.remove(m.getText());
                        }
                    }
                    menuBar.revalidate();
                    menuBar.repaint();
                });
                for (Object s : snippets) CompletionEngine.removeSnippet(s);
                snippets.clear();
                savedListeners.removeAll(listeners);
                listeners.clear();
                keyHooks.removeIf(k -> k.owner() == this);
            }
        }

        /** "확장 프로그램 관리" 창의 표 (화면 없이도 시험할 수 있게 따로 두었다) */
        class PluginTableModel extends AbstractTableModel {
            private final String[] cols = {"사용", "이름", "설명", "파일", "상태"};

            @Override
            public int getRowCount() { return plugins.entries().size(); }

            @Override
            public int getColumnCount() { return cols.length; }

            @Override
            public String getColumnName(int c) { return cols[c]; }

            @Override
            public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }

            @Override
            public boolean isCellEditable(int r, int c) { return c == 0; }

            @Override
            public Object getValueAt(int r, int c) {
                PluginManager.Entry e = plugins.entries().get(r);
                return switch (c) {
                    case 0 -> e.enabled;
                    case 1 -> e.name;
                    case 2 -> e.description;
                    case 3 -> e.jar.getName();
                    default -> !e.error.isEmpty() ? "오류: " + e.error : (e.loaded ? "실행 중" : (e.enabled ? "꺼짐" : "사용 안 함"));
                };
            }

            @Override
            public void setValueAt(Object v, int r, int c) {
                if (c != 0) return;
                PluginManager.Entry e = plugins.entries().get(r);
                boolean on = (Boolean) v;
                if (on && !dialogs.confirm("확장 프로그램 켜기", "'" + e.name + "' 확장 프로그램은 이 컴퓨터에서 코드를 실행합니다.\n"
                        + "직접 만들었거나 신뢰하는 것만 켜세요. 켤까요?")) return;
                plugins.setEnabled(e, on);
                fireTableDataChanged();
                if (!e.error.isEmpty()) dialogs.error("'" + e.name + "'을(를) 켜지 못했습니다:\n" + e.error);
            }
        }

        /** "확장 > 확장 프로그램 관리" 창 */
        private void showPluginManager() {
            Window owner = SwingUtilities.getWindowAncestor(this);
            JDialog dlg = new JDialog(owner, "확장 프로그램 관리", Dialog.ModalityType.APPLICATION_MODAL);

            PluginTableModel model = new PluginTableModel();
            JTable table = new JTable(model);
            table.getColumnModel().getColumn(0).setMaxWidth(50);

            JButton add = new JButton("확장 프로그램 추가 (jar)...");
            add.addActionListener(e -> {
                File[] picked = dialogs.chooseFiles(lastDirectory, "jar 파일", "jar");
                for (File f : picked) {
                    try {
                        plugins.install(f);
                    } catch (IOException ex) {
                        dialogs.error("추가하지 못했습니다: " + ex.getMessage());
                    }
                }
                model.fireTableDataChanged();
            });
            JButton refresh = new JButton("새로 고침");
            refresh.addActionListener(e -> { plugins.scan(); model.fireTableDataChanged(); });
            JButton bundled = new JButton("기본 확장 프로그램 설치");
            bundled.addActionListener(e -> {
                installBundledExtensions(true);
                model.fireTableDataChanged();
            });
            JButton openDir = new JButton("폴더 열기");
            openDir.addActionListener(e -> {
                File d = plugins.directory();
                d.mkdirs();
                try {
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(d);
                    else dialogs.info("확장 프로그램 폴더: " + d);
                } catch (IOException | UnsupportedOperationException ex) {
                    dialogs.info("확장 프로그램 폴더: " + d);
                }
            });
            JButton kit = new JButton("개발 키트 만들기...");
            kit.addActionListener(e -> {
                File dir = dialogs.chooseFolder(lastDirectory);
                if (dir == null) return;
                try {
                    File out = new File(dir, "minijava-plugin-kit");
                    PluginKit.createKit(out);
                    dialogs.info("개발 키트를 만들었습니다:\n" + out + "\n\nREADME.txt를 먼저 읽어 보세요.");
                } catch (IOException | RuntimeException ex) {
                    dialogs.error("개발 키트를 만들지 못했습니다: " + ex.getMessage());
                }
            });
            JButton close = new JButton("닫기");
            close.addActionListener(e -> dlg.dispose());

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.add(bundled);
            buttons.add(add);
            buttons.add(refresh);
            buttons.add(openDir);
            buttons.add(kit);
            buttons.add(close);
            JLabel note = new JLabel("  확장 프로그램은 이 컴퓨터에서 코드를 실행합니다. 신뢰하는 것만 켜세요.");
            note.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

            dlg.add(note, BorderLayout.NORTH);
            dlg.add(new JScrollPane(table), BorderLayout.CENTER);
            dlg.add(buttons, BorderLayout.SOUTH);
            dlg.setSize(840, 380);
            dlg.setLocationRelativeTo(owner);
            dlg.setVisible(true);
        }
    }

    // =================================================================
    // 프로젝트 실행 계획 (5단계): 컴파일 + 실행 명령 만들기
    // =================================================================

    static class ProjectRunner {

        record Plan(String mainClass, Path outDir, List<String> javacArgs, List<String> javaArgs) {
            String display() { return "java " + mainClass; }

            /** javac로 컴파일하고, 성공했을 때만 java로 실행하는 셸 명령 */
            String command(ShellRunner.Shell shell) {
                String compile = ShellRunner.commandLineFor(shell, javacArgs);
                String run = ShellRunner.commandLineFor(shell, javaArgs);
                return shell.kind() == ShellRunner.Kind.POWERSHELL
                        ? compile + "\nif ($LASTEXITCODE -eq 0) { " + run + " }"
                        : compile + " && " + run;
            }
        }

        private static final Pattern MAIN = Pattern.compile("\\bstatic\\s+void\\s+main\\s*\\(");

        static boolean hasMain(String text) {
            return MAIN.matcher(text).find();
        }

        /** 실행할 클래스 이름 (package 포함) */
        static String mainClassName(File file, String text) {
            String pkg = JavaChecker.packageOf(text);
            String base = file.getName().replaceFirst("\\.java$", "");
            return pkg.isEmpty() ? base : pkg + "." + base;
        }

        static Plan plan(File file, String text, List<File> sourceRoots, List<File> jars, Path outBase) {
            String sep = File.pathSeparator;
            Path outDir = outBase.resolve(Integer.toHexString(file.getAbsolutePath().hashCode()));
            List<String> roots = sourceRoots.stream().filter(File::isDirectory).map(File::getPath).toList();
            List<String> jarPaths = jars.stream().map(File::getPath).toList();

            List<String> javac = new ArrayList<>(javacCommand());
            javac.addAll(List.of("-encoding", "UTF-8"));
            if (!jarPaths.isEmpty()) {
                javac.add("-cp");
                javac.add(String.join(sep, jarPaths));
            }
            if (!roots.isEmpty()) {
                javac.add("-sourcepath");
                javac.add(String.join(sep, roots));
            }
            javac.addAll(List.of("-d", outDir.toString(), file.getAbsolutePath()));

            List<String> classpath = new ArrayList<>();
            classpath.add(outDir.toString());
            classpath.addAll(jarPaths);
            List<String> java = List.of(toolPath("java"),
                    "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                    "-cp", String.join(sep, classpath), mainClassName(file, text));
            return new Plan(mainClassName(file, text), outDir, javac, java);
        }

        /** 이전 실행에서 남은 .class 파일이 섞이지 않도록 출력 폴더를 비우고 새로 만든다 */
        static void prepare(Plan plan) throws IOException {
            Path dir = plan.outDir();
            if (Files.exists(dir)) {
                try (Stream<Path> s = Files.walk(dir)) {
                    for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
                }
            }
            Files.createDirectories(dir);
        }

        /** javac 실행 파일이 있으면 그것을, 없으면 JDK의 컴파일러 모듈을 java로 직접 실행한다 */
        private static List<String> javacCommand() {
            String javac = toolPath("javac");
            if (!javac.equals("javac")) return List.of(javac);
            return List.of(toolPath("java"), "-m", "jdk.compiler/com.sun.tools.javac.Main");
        }

        private static String toolPath(String name) {
            String exe = name + (ShellRunner.isWindows() ? ".exe" : "");
            File f = new File(System.getProperty("java.home"), "bin" + File.separator + exe);
            return f.isFile() ? f.getPath() : name;
        }
    }


    /** 목록의 한 줄: 이름은 굵게, 종류(method, keyword 등)는 회색으로 */
    private static class ItemRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            CompletionEngine.Item item = (CompletionEngine.Item) value;
            String kindColor = isSelected ? "#DDDDDD" : "#888888";
            setText("<html><b>" + escape(item.label()) + "</b> &nbsp; <font color='" + kindColor + "'>"
                    + escape(item.kind()) + "</font></html>");
            return this;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    // ---------------------------------------------------------------
    // 문법 강조 (2단계)
    // 편집기 창과 분리해 두었다. 나중에 테스트하거나 교체하기 쉽다.
    // ---------------------------------------------------------------

    static class JavaHighlighter {

        private static final Set<String> KEYWORDS = Set.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                "class", "const", "continue", "default", "do", "double", "else", "enum",
                "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new",
                "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
                "transient", "try", "void", "volatile", "while",
                // 문맥 키워드와 리터럴
                "var", "record", "sealed", "permits", "yield",
                "true", "false", "null"
        );

        /**
         * 코드를 "토큰" 단위로 쪼개는 정규표현식.
         * 앞에 있는 것이 우선이라서, 주석과 문자열을 먼저 찾아야
         * 그 안에 있는 단어가 키워드로 잘못 칠해지지 않는다.
         * (자동완성도 이 정규표현식으로 "지금 주석/문자열 안인가?"를 판단한다)
         */
        private static final Pattern TOKEN = Pattern.compile(
                "(?<blockcomment>/\\*.*?(?:\\*/|\\z))"
                        + "|(?<linecomment>//[^\\n]*)"
                        + "|(?<textblock>\"\"\".*?(?:\"\"\"|\\z))"
                        + "|(?<string>\"(?:\\\\.|[^\"\\\\\\n])*\"?)"
                        + "|(?<chr>'(?:\\\\.|[^'\\\\\\n])*'?)"
                        + "|(?<annotation>@[A-Za-z_]\\w*)"
                        + "|(?<number>\\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+"
                        + "|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?)[LlFfDd]?\\b)"
                        + "|(?<word>[A-Za-z_$][\\w$]*)",
                Pattern.DOTALL
        );

        private final StyledDocument doc;

        private final AttributeSet plainStyle = colorStyle(new Color(0x1E1E1E), false, false);
        private final AttributeSet keywordStyle = colorStyle(new Color(0x0000FF), true, false);
        private final AttributeSet stringStyle = colorStyle(new Color(0xA31515), false, false);
        private final AttributeSet commentStyle = colorStyle(new Color(0x008000), false, true);
        private final AttributeSet numberStyle = colorStyle(new Color(0x098658), false, false);
        private final AttributeSet annotationStyle = colorStyle(new Color(0x795E26), false, false);
        private final AttributeSet typeStyle = colorStyle(new Color(0x267F99), false, false);

        JavaHighlighter(StyledDocument doc) {
            this.doc = doc;
        }

        /** 문서 전체를 다시 칠한다 */
        void highlight() {
            try {
                // JTextPane.getText() 대신 doc.getText()를 써야 글자 위치(offset)가 정확하다
                String text = doc.getText(0, doc.getLength());

                // 전체를 기본색으로 되돌린 뒤, 토큰마다 색을 입힌다
                doc.setCharacterAttributes(0, text.length(), plainStyle, true);

                Matcher m = TOKEN.matcher(text);
                while (m.find()) {
                    AttributeSet style = styleFor(m);
                    if (style != null) {
                        doc.setCharacterAttributes(m.start(), m.end() - m.start(), style, true);
                    }
                }
            } catch (BadLocationException ex) {
                // 입력 도중 문서가 바뀐 경우. 다음 입력에서 다시 칠해지므로 무시해도 된다.
            }
        }

        /** 색을 모두 지우고 기본색으로 되돌린다 (Java 파일이 아닌 파일용) */
        void clear() {
            doc.setCharacterAttributes(0, doc.getLength(), plainStyle, true);
        }

        private AttributeSet styleFor(Matcher m) {
            if (m.group("blockcomment") != null || m.group("linecomment") != null) return commentStyle;
            if (m.group("textblock") != null || m.group("string") != null || m.group("chr") != null)
                return stringStyle;
            if (m.group("annotation") != null) return annotationStyle;
            if (m.group("number") != null) return numberStyle;

            String word = m.group("word");
            if (word != null) {
                if (KEYWORDS.contains(word)) return keywordStyle;
                // 대문자로 시작하는 단어는 클래스 이름으로 보고 색을 입힌다 (String, List 등)
                if (Character.isUpperCase(word.charAt(0))) return typeStyle;
            }
            return null;
        }

        private static AttributeSet colorStyle(Color color, boolean bold, boolean italic) {
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, color);
            StyleConstants.setBold(attrs, bold);
            StyleConstants.setItalic(attrs, italic);
            return attrs;
        }
    }

    // ---------------------------------------------------------------
    // 자동완성 엔진 (3단계)
    // 화면과 상관없이 "글자 + 커서 위치 → 후보 목록"만 계산한다.
    // ---------------------------------------------------------------

    static class CompletionEngine {

        /** 후보 하나. label은 목록에 보이는 글, insert는 실제로 넣을 글 */
        record Item(String label, String insert, int caretBack, String kind) { }

        /** replaceStart부터 커서까지를 후보 중 하나로 바꾼다 */
        record Result(int replaceStart, List<Item> items) { }

        /** 후보를 넣은 결과: 넣을 글과, 그 글 안에서 커서가 놓일 위치 */
        record Expansion(String text, int cursor) { }

        private record Snippet(String name, String description, String template) { }

        private record Scan(boolean inNonCode, Set<String> words) { }

        private static final Result NONE = new Result(0, List.of());
        private static final int MAX_ITEMS = 60;
        private static final String CURSOR = "$0"; // 스니펫에서 커서가 놓일 자리 표시

        /** 클래스 이름만 보고 찾아볼 패키지들 */
        private static final String[] PACKAGES = {
                "java.lang.", "java.util.", "java.io.", "java.nio.file.", "java.util.stream.",
                "java.util.function.", "java.util.concurrent.", "java.time.", "java.math.",
                "java.nio.charset.", "java.text."
        };

        private static final List<String> COMMON_TYPES = List.of(
                "String", "StringBuilder", "StringBuffer", "Integer", "Long", "Double", "Float",
                "Boolean", "Character", "Byte", "Short", "Math", "System", "Object", "Thread",
                "Runnable", "Exception", "RuntimeException", "IOException",
                "IllegalArgumentException", "IllegalStateException", "NullPointerException",
                "List", "ArrayList", "LinkedList", "Map", "HashMap", "TreeMap", "LinkedHashMap",
                "Set", "HashSet", "TreeSet", "Queue", "Deque", "ArrayDeque", "PriorityQueue",
                "Iterator", "Arrays", "Collections", "Objects", "Optional", "Scanner", "Random",
                "File", "Files", "Path", "Paths", "Stream", "Collectors", "LocalDate",
                "LocalDateTime", "Duration", "Instant", "BigInteger", "BigDecimal",
                "Comparable", "Comparator", "Function", "Consumer", "Supplier", "Predicate",
                "Override", "FunctionalInterface"
        );

        /** 확장 프로그램이 등록한 스니펫 */
        private static final List<Snippet> PLUGIN_SNIPPETS = new CopyOnWriteArrayList<>();

        /** 스니펫을 등록한다. 돌려주는 값은 removeSnippet에 넘겨 등록을 취소할 때 쓴다. */
        static Object addSnippet(String name, String description, String template) {
            Snippet s = new Snippet(name, description, template);
            PLUGIN_SNIPPETS.add(s);
            return s;
        }

        static void removeSnippet(Object token) {
            PLUGIN_SNIPPETS.remove(token);
        }

        /** 후보 계산에 더해 줄 정보: 프로젝트의 다른 클래스 이름들, 외부 라이브러리(jar)를 읽는 클래스로더 */
        record Extras(Set<String> types, ClassLoader libs) {
            static final Extras NONE = new Extras(Set.of(), null);
        }

        private static final Pattern IMPORT = Pattern.compile(
                "(?m)^\\s*import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;");

        private static final List<Snippet> SNIPPETS = List.of(
                new Snippet("sout", "System.out.println()", "System.out.println($0);"),
                new Snippet("main", "main 메서드",
                        "public static void main(String[] args) {\n    $0\n}"),
                new Snippet("fori", "숫자 반복문",
                        "for (int i = 0; i < n; i++) {\n    $0\n}"),
                new Snippet("foreach", "컬렉션 반복문",
                        "for (var item : items) {\n    $0\n}"),
                new Snippet("trycatch", "try-catch",
                        "try {\n    $0\n} catch (Exception e) {\n    e.printStackTrace();\n}")
        );

        /** 변수에 점(.)을 찍었을 때 목록에서 빼는, 거의 쓸 일 없는 Object의 메서드 */
        private static final Set<String> EXCLUDED_METHODS =
                Set.of("wait", "notify", "notifyAll", "getClass");

        private static final Map<String, Optional<Class<?>>> CLASS_CACHE = new HashMap<>();

        /**
         * 후보를 계산한다.
         * @param text   전체 글
         * @param caret  커서 위치
         * @param forced Ctrl+Space로 직접 호출했는가 (true면 한 글자만 쳐도 후보를 보여 준다)
         */
        static Result complete(String text, int caret, boolean forced) {
            return complete(text, caret, forced, Extras.NONE);
        }

        /** 프로젝트의 다른 클래스와 외부 라이브러리까지 반영해서 후보를 계산한다 */
        static Result complete(String text, int caret, boolean forced, Extras extras) {
            if (caret < 0 || caret > text.length()) return NONE;

            // 커서 바로 앞의 단어(prefix)를 찾는다
            int start = caret;
            while (start > 0 && isIdentPart(text.charAt(start - 1))) start--;
            String prefix = text.substring(start, caret);
            if (!prefix.isEmpty() && !isIdentStart(prefix.charAt(0))) return NONE; // 숫자로 시작

            boolean member = start > 0 && text.charAt(start - 1) == '.'; // "Math." 처럼 점 뒤인가?
            if (!member && prefix.length() < (forced ? 1 : 2)) return NONE;

            int end = caret;
            while (end < text.length() && isIdentPart(text.charAt(end))) end++;

            // 주석이나 문자열 안에서는 자동완성을 하지 않는다
            Scan scan = scan(text, caret, start, end);
            if (scan.inNonCode()) return NONE;

            List<Item> items = member
                    ? memberItems(text, start - 1, prefix, extras)
                    : wordItems(prefix, scan.words(), extras.types());
            if (items.isEmpty()) return NONE;

            items.sort(Comparator
                    .comparingInt((Item i) -> i.label().startsWith(prefix) ? 0 : 1) // 대소문자까지 맞는 것 먼저
                    .thenComparingInt(i -> kindRank(i.kind()))
                    .thenComparingInt(i -> i.label().length())
                    .thenComparing(Item::label));

            if (items.size() > MAX_ITEMS) items = new ArrayList<>(items.subList(0, MAX_ITEMS));
            return new Result(start, items);
        }

        /** 후보를 문서에 넣을 형태로 만든다. 스니펫은 현재 줄의 들여쓰기를 이어 받는다. */
        static Expansion expand(Item item, String indent) {
            String insert = item.insert();
            if (!insert.contains(CURSOR)) {
                return new Expansion(insert, insert.length() - item.caretBack());
            }
            String expanded = insert.replace("\n", "\n" + indent);
            int cursor = expanded.indexOf(CURSOR);
            return new Expansion(expanded.replace(CURSOR, ""), cursor);
        }

        /** offset이 있는 줄의 앞쪽 공백(들여쓰기)을 돌려준다 */
        static String lineIndent(String text, int offset) {
            int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
            int i = lineStart;
            while (i < offset && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
            return text.substring(lineStart, i);
        }

        // ----- 일반 단어 후보 -----

        private static List<Item> wordItems(String prefix, Set<String> docWords, Set<String> extraTypes) {
            Map<String, Item> map = new LinkedHashMap<>();
            for (String k : JavaHighlighter.KEYWORDS) addWord(map, prefix, new Item(k, k, 0, "keyword"));
            for (String t : COMMON_TYPES) addWord(map, prefix, new Item(t, t, 0, "type"));
            for (String t : extraTypes) addWord(map, prefix, new Item(t, t, 0, "type"));
            List<Snippet> snippets = new ArrayList<>(SNIPPETS);
            snippets.addAll(PLUGIN_SNIPPETS);
            for (Snippet s : snippets) {
                addWord(map, prefix, new Item(s.name(), s.template(), 0, "snippet · " + s.description()));
            }
            for (String w : docWords) {
                if (w.length() >= 3) addWord(map, prefix, new Item(w, w, 0, "word"));
            }
            return new ArrayList<>(map.values());
        }

        private static void addWord(Map<String, Item> map, String prefix, Item item) {
            String label = item.label();
            if (!startsWithIgnoreCase(label, prefix)) return;
            // 이미 다 쳐서 똑같은 단어는 제안하지 않는다 (스니펫은 예외: sout을 다 쳐도 펼쳐 준다)
            if (label.equals(prefix) && !item.kind().startsWith("snippet")) return;
            map.putIfAbsent(label, item);
        }

        // ----- 점(.) 뒤 멤버 후보 -----

        /**
         * "System.out.", "Math.", "name." 같은 식의 점 뒤에 올 수 있는 멤버를 JDK 클래스에서 읽어 온다.
         * @param dot 점(.)의 위치
         */
        private static List<Item> memberItems(String text, int dot, String prefix, Extras extras) {
            // 점 앞의 "A.b.c" 형태를 거꾸로 읽는다
            List<String> segs = new ArrayList<>();
            int pos = dot;
            while (true) {
                int segEnd = pos;
                int segStart = segEnd;
                while (segStart > 0 && isIdentPart(text.charAt(segStart - 1))) segStart--;
                if (segStart == segEnd) return List.of(); // 점 앞이 이름이 아님: getX(). 또는 "abc". 등
                segs.add(0, text.substring(segStart, segEnd));
                if (segStart > 0 && text.charAt(segStart - 1) == '.') {
                    pos = segStart - 1;
                } else {
                    break;
                }
            }

            String first = segs.get(0);
            if (!isIdentStart(first.charAt(0))) return List.of();

            Class<?> cls;
            boolean isStatic;
            if (Character.isUpperCase(first.charAt(0))) {
                cls = findClass(first, text, extras); // Math. → 클래스의 static 멤버
                isStatic = true;
            } else {
                cls = inferVariableType(text, first, dot, extras); // name. → 변수 선언을 보고 타입 추정
                isStatic = false;
            }
            if (cls == null) return List.of();

            // System.out 처럼 필드를 따라 내려간다
            for (int i = 1; i < segs.size(); i++) {
                Field f = publicField(cls, segs.get(i), isStatic);
                if (f == null) return List.of();
                cls = f.getType();
                isStatic = false;
            }
            return collectMembers(cls, isStatic, prefix);
        }

        private static List<Item> collectMembers(Class<?> cls, boolean staticCtx, String prefix) {
            Map<String, Boolean> methods = new HashMap<>(); // 이름 → 매개변수가 있는 버전이 있는가
            for (Method m : cls.getMethods()) {
                String name = m.getName();
                if (Modifier.isStatic(m.getModifiers()) != staticCtx) continue;
                if (!staticCtx && EXCLUDED_METHODS.contains(name)) continue;
                if (!startsWithIgnoreCase(name, prefix)) continue;
                methods.merge(name, m.getParameterCount() > 0, Boolean::logicalOr);
            }

            List<Item> items = new ArrayList<>();
            methods.forEach((name, hasParams) -> items.add(new Item(
                    name + (hasParams ? "(…)" : "()"),
                    name + "()",
                    hasParams ? 1 : 0, // 매개변수가 있으면 괄호 안에 커서를 둔다
                    "method")));

            Set<String> seen = new HashSet<>();
            for (Field f : cls.getFields()) {
                if (Modifier.isStatic(f.getModifiers()) != staticCtx) continue;
                if (!startsWithIgnoreCase(f.getName(), prefix)) continue;
                if (seen.add(f.getName())) items.add(new Item(f.getName(), f.getName(), 0, "field"));
            }
            return items;
        }

        private static Field publicField(Class<?> cls, String name, boolean staticOnly) {
            try {
                Field f = cls.getField(name);
                if (staticOnly && !Modifier.isStatic(f.getModifiers())) return null;
                return f;
            } catch (NoSuchFieldException | SecurityException e) {
                return null;
            }
        }

        /**
         * 변수 선언(String s = ..., List<String> list ..., var sb = new StringBuilder())을 글에서 찾아
         * 그 변수의 타입을 추정한다. 간단한 추정이라 항상 맞지는 않는다.
         */
        private static Class<?> inferVariableType(String text, String name, int before, Extras extras) {
            String quoted = Pattern.quote(name);
            Pattern declared = Pattern.compile(
                    "\\b([A-Z]\\w*)(?:<[^;(){}=]*?>)?(?:\\[\\])*\\s+" + quoted + "\\b\\s*[=;,):]");
            Pattern viaVar = Pattern.compile(
                    "\\bvar\\s+" + quoted + "\\s*=\\s*new\\s+([A-Z]\\w*)");

            String bestType = null;
            int bestPos = -1;
            boolean bestIsBefore = false;
            for (Pattern p : List.of(declared, viaVar)) {
                Matcher m = p.matcher(text);
                while (m.find()) {
                    boolean isBefore = m.start() < before;
                    boolean better = bestType == null
                            || (isBefore && !bestIsBefore)                       // 앞쪽 선언을 우선
                            || (isBefore && bestIsBefore && m.start() > bestPos) // 앞쪽 중에서는 가장 가까운 것
                            || (!isBefore && !bestIsBefore && m.start() < bestPos);
                    if (better) {
                        bestType = m.group(1);
                        bestPos = m.start();
                        bestIsBefore = isBefore;
                    }
                }
            }
            return bestType == null ? null : findClass(bestType, text, extras);
        }

        /**
         * 클래스 이름으로 클래스를 찾는다.
         * 1) 자주 쓰는 JDK 패키지  2) 이 파일의 import 문 (외부 라이브러리 포함)
         */
        private static Class<?> findClass(String simpleName, String text, Extras extras) {
            Class<?> jdk = findJdkClass(simpleName);
            if (jdk != null) return jdk;

            ClassLoader loader = extras.libs() != null ? extras.libs() : CompletionEngine.class.getClassLoader();
            Matcher m = IMPORT.matcher(text);
            while (m.find()) {
                if (m.group(1) != null) continue; // static import는 건너뜀
                String name = m.group(2);
                boolean wildcard = m.group(3) != null;
                String fqn;
                if (wildcard) {
                    fqn = name + "." + simpleName;
                } else if (name.endsWith("." + simpleName)) {
                    fqn = name;
                } else {
                    continue;
                }
                try {
                    return Class.forName(fqn, false, loader); // false: 불러오기만 하고 실행(초기화)은 하지 않는다
                } catch (ClassNotFoundException | LinkageError e) {
                    // 다음 import를 계속 확인한다
                }
            }
            return null;
        }

        private static synchronized Class<?> findJdkClass(String simpleName) {
            Optional<Class<?>> cached = CLASS_CACHE.get(simpleName);
            if (cached != null) return cached.orElse(null);

            Class<?> found = null;
            for (String pkg : PACKAGES) {
                try {
                    // false: 클래스를 불러오기만 하고 초기화(실행)하지는 않는다
                    found = Class.forName(pkg + simpleName, false, CompletionEngine.class.getClassLoader());
                    break;
                } catch (ClassNotFoundException | LinkageError e) {
                    // 다음 패키지에서 계속 찾는다
                }
            }
            CLASS_CACHE.put(simpleName, Optional.ofNullable(found));
            return found;
        }

        // ----- 글 훑어보기: 단어 모으기 + 주석/문자열 안인지 확인 -----

        private static Scan scan(String text, int caret, int wordStart, int wordEnd) {
            boolean inNonCode = false;
            Set<String> words = new LinkedHashSet<>();

            Matcher m = JavaHighlighter.TOKEN.matcher(text);
            while (m.find()) {
                String word = m.group("word");
                if (word != null) {
                    // 지금 입력 중인 단어 자신은 후보에서 뺀다
                    if (m.start() < wordEnd && m.end() > wordStart) continue;
                    words.add(word);
                } else if (m.group("number") == null && m.group("annotation") == null
                        && containsCaret(m, caret)) {
                    inNonCode = true; // 주석, 문자열, 문자 리터럴 중 하나
                }
            }
            return new Scan(inNonCode, words);
        }

        /** 커서가 이 토큰(주석/문자열) "안쪽"에 있는가? 아직 닫히지 않은 토큰은 끝 위치도 안쪽으로 본다. */
        private static boolean containsCaret(Matcher m, int caret) {
            int s = m.start();
            int e = m.end();
            if (caret > s && caret < e) return true;
            if (caret != e) return false;

            String t = m.group();
            if (m.group("linecomment") != null) return true; // 줄 끝에서 쓰는 중
            if (m.group("blockcomment") != null) return !(t.length() >= 4 && t.endsWith("*/"));
            if (m.group("textblock") != null) return !(t.length() >= 6 && t.endsWith("\"\"\""));
            return !isClosedQuote(t); // 문자열, 문자
        }

        private static boolean isClosedQuote(String token) {
            if (token.length() < 2) return false;
            char quote = token.charAt(0);
            if (token.charAt(token.length() - 1) != quote) return false;
            int backslashes = 0;
            for (int i = token.length() - 2; i > 0 && token.charAt(i) == '\\'; i--) backslashes++;
            return backslashes % 2 == 0; // 홀수 개면 마지막 따옴표는 \" 처럼 이스케이프된 것
        }

        // ----- 작은 도구들 -----

        private static int kindRank(String kind) {
            if (kind.equals("word")) return 0;
            if (kind.startsWith("snippet")) return 1;
            if (kind.equals("type")) return 2;
            if (kind.equals("keyword")) return 3;
            return 4; // method, field
        }

        private static boolean startsWithIgnoreCase(String s, String prefix) {
            return s.regionMatches(true, 0, prefix, 0, prefix.length());
        }

        private static boolean isIdentStart(char c) {
            return Character.isLetter(c) || c == '_' || c == '$';
        }

        private static boolean isIdentPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '$';
        }
    }

    // ---------------------------------------------------------------
    // 오류 표시: 화면 부품들 (4단계)
    // ---------------------------------------------------------------

    /** 가로 줄바꿈이 없고(코드 편집기답게), 오류 밑줄에 마우스를 올리면 설명이 뜨는 편집 영역 */
    static class CodePane extends JTextPane {

        /** 글자 위치 → 툴팁 글 (없으면 null). 편집기가 채워 넣는다. */
        IntFunction<String> tooltipAt = offset -> null;

        CodePane() {
            setStyledDocument(new GroupingDocument()); // 덮어쓰기를 되돌리기 한 번으로 묶는다
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int offset = viewToModel2D(e.getPoint());
            return offset < 0 ? null : tooltipAt.apply(offset);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            Component parent = getParent();
            return parent instanceof JViewport
                    && getUI().getPreferredSize(this).width <= parent.getWidth();
        }
    }

    /**
     * 글을 다른 글로 바꾸는 동작(선택한 글 덮어쓰기, 자동완성 적용 등)은 내부적으로
     * "지우기 + 넣기" 두 단계다. 그대로 두면 Ctrl+Z를 두 번 눌러야 하므로 한 번에 되돌리도록 묶는다.
     */
    static class GroupingDocument extends DefaultStyledDocument {
        private CompoundEdit group; // 바꾸는 도중에만 존재한다

        @Override
        public void replace(int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            boolean outermost = group == null && length > 0 && text != null && !text.isEmpty();
            if (outermost) group = new CompoundEdit();
            try {
                super.replace(offset, length, text, attrs);
            } finally {
                if (outermost) {
                    CompoundEdit done = group;
                    group = null;
                    done.end();
                    fireUndoableEditUpdate(new UndoableEditEvent(this, done));
                }
            }
        }

        @Override
        protected void fireUndoableEditUpdate(UndoableEditEvent e) {
            if (group != null && e.getEdit() instanceof AbstractDocument.DefaultDocumentEvent) {
                group.addEdit(e.getEdit()); // 묶는 중이면 모아 두었다가 한 번에 알린다
                return;
            }
            super.fireUndoableEditUpdate(e);
        }
    }

    /** 글자 아래에 물결 모양 밑줄을 그린다 */
    static class SquigglePainter implements Highlighter.HighlightPainter {
        private final Color color;
        private final JavaChecker.Problem problem;

        SquigglePainter(Color color, JavaChecker.Problem problem) {
            this.color = color;
            this.problem = problem;
        }

        JavaChecker.Problem problem() { return problem; }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle2D r0 = c.modelToView2D(p0);
                Rectangle2D r1 = c.modelToView2D(p1);
                if (r0 == null || r1 == null) return;

                int x0 = (int) r0.getX();
                int x1 = (int) r1.getX();
                if (x1 <= x0) x1 = x0 + 6;
                int y = (int) (r0.getY() + r0.getHeight()) - 3;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(color);
                for (int x = x0; x < x1; x += 4) {
                    g2.drawLine(x, y, x + 2, y + 2);
                    g2.drawLine(x + 2, y + 2, Math.min(x + 4, x1), y);
                }
                g2.dispose();
            } catch (BadLocationException ex) {
                // 글이 바뀌는 중이면 이번 한 번은 그리지 않는다
            }
        }
    }

    /** "문제" 목록의 한 줄: ● 줄 번호, 한국어 설명, 회색 영어 원문 */
    private static class ProblemRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            FileProblem fp = (FileProblem) value;
            JavaChecker.Problem p = fp.problem();

            String icon = p.error()
                    ? "<font color='#E53935'>●</font>"
                    : "<font color='#F9A825'>▲</font>";
            String gray = isSelected ? "#DDDDDD" : "#888888";
            String line = p.line() >= 1 ? String.valueOf(p.line()) : "?";
            String original = p.summary().equals(p.original())
                    ? ""
                    : " &nbsp;<font color='" + gray + "'>" + escapeHtml(p.original()) + "</font>";

            setText("<html>" + icon + " " + escapeHtml(fp.fileName()) + ":" + line + " &nbsp;<b>" + escapeHtml(p.summary()) + "</b>"
                    + original + "</html>");
            setToolTipText("<html>" + tooltipHtml(p) + "</html>");
            return this;
        }

        /** 마우스를 올렸을 때 보이는 설명 (한국어 요약 + javac 원문 전체) */
        static String tooltipHtml(JavaChecker.Problem p) {
            return "<b>" + escapeHtml(p.summary()) + "</b><br><font color='#666666'>"
                    + escapeHtml(p.detail()).replace("\n", "<br>").replace("  ", "&nbsp;&nbsp;")
                    + "</font>";
        }
    }

    // ---------------------------------------------------------------
    // 오류 검사기 (4단계)
    // JDK에 들어 있는 javac를 편집기 안에서 직접 호출한다.
    // 화면과 상관없이 "글 → 문제 목록"만 계산하므로 따로 테스트할 수 있다.
    // ---------------------------------------------------------------

    static class JavaChecker {

        /**
         * 문제 하나.
         * start/end는 글에서의 위치(0부터), 없으면 -1.
         * summary는 한국어 설명, original은 javac 원문의 첫 줄, detail은 원문 전체.
         */
        record Problem(boolean error, long line, long column, int start, int end,
                       String code, String summary, String original, String detail) { }

        private static final int MAX_PROBLEMS = 200;

        private static final Pattern PUBLIC_TYPE = Pattern.compile(
                "(?m)^\\s*public\\s+(?:(?:final|abstract|sealed|non-sealed|strictfp|static)\\s+)*"
                        + "(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][\\w$]*)");

        /** JDK가 설치되어 있어야 한다 (JRE만 있으면 javac가 없다) */
        static boolean isAvailable() {
            return ToolProvider.getSystemJavaCompiler() != null;
        }

        /** 저장하지 않은 글의 파일 이름을 추측한다: public 클래스 이름 + .java */
        static String guessFileName(String text) {
            Matcher m = PUBLIC_TYPE.matcher(text);
            return (m.find() ? m.group(1) : "Main") + ".java";
        }

        /** 검사할 파일 하나. id는 결과를 돌려받을 때 쓰는 이름표(경로 등), fileName은 public 클래스 이름 규칙 검사에 쓰인다 */
        record Unit(String id, String fileName, String text) { }

        /**
         * 여러 파일을 한꺼번에 검사한다. 파일끼리 서로 참조해도 된다.
         * 파일로 저장하지 않고 편집기에 있는 글(저장 전 수정 포함) 그대로 검사한다.
         * @param sourceRoots 열려 있지 않은 다른 .java 파일을 찾을 폴더들 (패키지 폴더가 시작되는 위치)
         * @param classpath   외부 라이브러리 jar들
         * @return 파일 id별 문제 목록. 검사할 수 없으면 null
         */
        static Map<String, List<Problem>> checkAll(List<Unit> units, List<File> sourceRoots, List<File> classpath) {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) return null;

            List<String> options = new ArrayList<>(List.of("-proc:none", "-Xlint:none"));
            List<String> roots = new ArrayList<>();
            for (File r : sourceRoots) {
                if (r != null && r.isDirectory()) roots.add(r.getPath());
            }
            if (!roots.isEmpty()) {
                options.add("-sourcepath");
                options.add(String.join(File.pathSeparator, roots));
            }
            List<String> jars = new ArrayList<>();
            for (File j : classpath) {
                if (j != null && j.isFile()) jars.add(j.getPath());
            }
            if (!jars.isEmpty()) {
                options.add("-classpath");
                options.add(String.join(File.pathSeparator, jars));
            }

            Map<URI, String> idByUri = new HashMap<>();
            List<JavaFileObject> sources = new ArrayList<>();
            for (int i = 0; i < units.size(); i++) {
                Unit u = units.get(i);
                URI uri;
                try {
                    // 파일마다 다른 가짜 경로를 줘서 서로 섞이지 않게 한다
                    uri = new URI("string", null, "/u" + i + "/" + u.fileName(), null);
                } catch (URISyntaxException e) {
                    continue;
                }
                idByUri.put(uri, u.id());
                String text = u.text();
                sources.add(new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return text;
                    }
                });
            }

            Map<String, List<Problem>> result = new LinkedHashMap<>();
            for (Unit u : units) result.put(u.id(), new ArrayList<>());
            if (sources.isEmpty()) return result;

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fm =
                         compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)) {
                JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics, options, null, sources);
                task.parse();   // 문법 검사
                task.analyze(); // 이름, 타입 검사
            } catch (IOException | RuntimeException e) {
                // 검사 도중 문제가 생겨도, 그때까지 모인 진단은 보여 준다
            }

            int total = 0;
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                // 열려 있지 않은 다른 파일(디스크에서 읽은 파일)의 문제는 보여 주지 않는다
                String id = d.getSource() == null ? null : idByUri.get(d.getSource().toUri());
                if (id == null) continue;

                Diagnostic.Kind kind = d.getKind();
                boolean error = kind == Diagnostic.Kind.ERROR;
                if (!error && kind != Diagnostic.Kind.WARNING && kind != Diagnostic.Kind.MANDATORY_WARNING) continue;

                String message = d.getMessage(Locale.ENGLISH);
                String original = message.lines().findFirst().orElse(message).trim();
                result.get(id).add(new Problem(
                        error, d.getLineNumber(), d.getColumnNumber(),
                        (int) d.getStartPosition(), (int) d.getEndPosition(),
                        d.getCode(), explain(d.getCode(), message), original, message));
                if (++total >= MAX_PROBLEMS) break;
            }

            for (List<Problem> list : result.values()) {
                list.sort(Comparator.comparingLong(Problem::line).thenComparingLong(Problem::column));
            }
            return result;
        }

        /**
         * 파일 하나를 검사한다 (checkAll의 간편 버전).
         * @param fileName  이 글의 파일 이름 (public 클래스 이름 규칙 검사에 쓰인다)
         * @param sourceDir 같은 폴더의 다른 .java 파일을 찾을 위치 (저장 전이면 null)
         * @return 문제 목록. 검사할 수 없으면 null
         */
        static List<Problem> check(String text, String fileName, File sourceDir) {
            Map<String, List<Problem>> r = checkAll(List.of(new Unit("one", fileName, text)),
                    sourceDir == null ? List.of() : List.of(sourceDir), List.of());
            return r == null ? null : r.get("one");
        }

        // ----- 여러 파일(프로젝트) 도구 -----

        private static final Pattern PACKAGE_DECL = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

        /** 글에 적힌 package 선언 (없으면 빈 글자) */
        static String packageOf(String text) {
            Matcher m = PACKAGE_DECL.matcher(text);
            return m.find() ? m.group(1) : "";
        }

        /**
         * 파일 위치와 package 선언으로 "소스 루트"(패키지 폴더가 시작되는 곳)를 알아낸다.
         * 예: src/com/acme/Foo.java 에 package com.acme; → src
         * 폴더 구조가 package와 맞지 않으면 파일이 있는 폴더를 그대로 돌려준다.
         */
        static File deriveSourceRoot(File file, String text) {
            File dir = file.getAbsoluteFile().getParentFile();
            String pkg = packageOf(text);
            if (pkg.isEmpty() || dir == null) return dir;
            String[] parts = pkg.split("\\.");
            File cur = dir;
            for (int i = parts.length - 1; i >= 0; i--) {
                if (cur == null || !cur.getName().equals(parts[i])) return dir;
                cur = cur.getParentFile();
            }
            return cur != null ? cur : dir;
        }

        /** 폴더 dir에 새 클래스를 만들 때 쓸 package 이름 (소스 루트 기준, 해당 없으면 빈 글자) */
        static String packageForDir(File dir, List<File> roots) {
            Path d = dir.getAbsoluteFile().toPath().normalize();
            for (File r : roots) {
                Path rp = r.getAbsoluteFile().toPath().normalize();
                if (!d.startsWith(rp)) continue;
                StringBuilder sb = new StringBuilder();
                for (Path seg : rp.relativize(d)) {
                    String name = seg.toString();
                    if (name.isEmpty()) continue;
                    if (!Character.isJavaIdentifierStart(name.charAt(0))
                            || !name.chars().allMatch(Character::isJavaIdentifierPart)) {
                        return "";
                    }
                    if (sb.length() > 0) sb.append('.');
                    sb.append(name);
                }
                return sb.toString();
            }
            return "";
        }

        /**
         * 밑줄을 칠 글자 범위를 정한다.
         * - 한 줄 안으로만 칠한다.
         * - 길이가 0인 위치(예: ';'이 빠진 곳)는 글자 하나에 칠하고, 줄 끝이면 바로 앞 글자에 칠한다.
         * @return {시작, 끝}. 칠할 곳이 없으면 null
         */
        static int[] underlineRange(String text, int start, int end) {
            int len = text.length();
            if (start < 0 || len == 0) return null;

            start = Math.min(start, len);
            end = Math.max(Math.min(end, len), start);

            int lineStart = text.lastIndexOf('\n', start - 1) + 1;
            int lineEnd = text.indexOf('\n', start);
            if (lineEnd < 0) lineEnd = len;
            end = Math.min(end, lineEnd);

            if (end <= start) {
                if (start < lineEnd) {
                    end = start + 1;
                } else if (start > 0) {
                    end = start;
                    start = start - 1;
                    if (start < lineStart && start > 0 && text.charAt(start) != '\n') start = lineStart;
                } else {
                    return null;
                }
            }
            return new int[]{start, end};
        }

        /** line(1부터)번째 줄이 시작하는 글자 위치 */
        static int lineStartOffset(String text, long line) {
            int offset = 0;
            for (long l = 1; l < line; l++) {
                int nl = text.indexOf('\n', offset);
                if (nl < 0) return text.length();
                offset = nl + 1;
            }
            return offset;
        }

        // ----- 오류 코드 → 한국어 설명 -----

        static String explain(String code, String message) {
            String first = message.lines().findFirst().orElse(message).trim();

            return switch (code) {
                case "compiler.err.expected", "compiler.err.expected2", "compiler.err.expected3" ->
                        "빠진 기호가 있어요: " + first.replace(" expected", "");
                case "compiler.err.expected4" ->
                        "여기에는 class, interface, enum, record 선언만 올 수 있어요 (} 가 하나 더 있는지 확인해 보세요)";
                case "compiler.err.cant.resolve", "compiler.err.cant.resolve.args",
                     "compiler.err.cant.resolve.location", "compiler.err.cant.resolve.location.args" -> {
                    Matcher m = Pattern.compile("symbol:\\s+(\\w+)\\s+([^\\n]+)").matcher(message);
                    yield m.find()
                            ? "찾을 수 없는 " + kindName(m.group(1)) + ": " + m.group(2).trim()
                                    + " (오타이거나 import가 빠졌을 수 있어요)"
                            : "이름을 찾을 수 없어요 (오타이거나 import가 빠졌을 수 있어요)";
                }
                case "compiler.err.prob.found.req" -> typeMismatch(message, first);
                case "compiler.err.missing.ret.stmt" ->
                        "return 문이 빠졌어요 (이 메서드는 값을 돌려줘야 해요)";
                case "compiler.err.var.might.not.have.been.initialized" -> {
                    Matcher m = Pattern.compile("variable (\\S+) might").matcher(message);
                    yield "값을 넣기 전에 사용한 변수예요" + (m.find() ? ": " + m.group(1) : "");
                }
                case "compiler.err.already.defined" -> {
                    Matcher m = Pattern.compile("(?:variable|method|class) (\\S+) is already defined").matcher(message);
                    yield "이미 같은 이름이 있어요" + (m.find() ? ": " + m.group(1) : "");
                }
                case "compiler.err.unreported.exception.need.to.catch.or.throw" -> {
                    Matcher m = Pattern.compile("unreported exception (\\S+);").matcher(message);
                    yield "처리하지 않은 예외예요" + (m.find() ? ": " + shortType(m.group(1)) : "")
                            + " (try-catch로 잡거나 throws로 넘기세요)";
                }
                case "compiler.err.unclosed.str.lit" ->
                        "문자열이 닫히지 않았어요 (닫는 \" 가 필요해요)";
                case "compiler.err.premature.eof" ->
                        "파일이 중간에 끝났어요 (닫는 } 가 부족할 수 있어요)";
                case "compiler.err.class.public.should.be.in.file" -> {
                    Matcher m = Pattern.compile("named (\\S+)").matcher(message);
                    yield "public 클래스 이름과 파일 이름이 달라요"
                            + (m.find() ? " (파일 이름이 " + m.group(1) + " 여야 해요)" : "");
                }
                case "compiler.err.non-static.cant.be.ref" ->
                        "static이 아닌 것을 static 안에서 바로 쓸 수 없어요 (객체를 만들어 쓰거나 static으로 바꾸세요)";
                case "compiler.err.not.stmt" ->
                        "문장이 아니에요 (값만 있고 실행할 내용이 없어요)";
                case "compiler.err.illegal.start.of.expr" ->
                        "여기서는 식을 시작할 수 없어요 (빠진 값이 있는지 확인해 보세요)";
                case "compiler.err.unreachable.stmt" ->
                        "실행될 수 없는 코드예요 (앞에서 return 등으로 이미 끝나요)";
                case "compiler.err.does.not.override.abstract" -> {
                    Matcher m = Pattern.compile("abstract method (\\S+) in").matcher(message);
                    yield "추상 메서드를 구현하지 않았어요" + (m.find() ? ": " + m.group(1) : "");
                }
                case "compiler.err.report.access" -> {
                    Matcher m = Pattern.compile("(\\S+) has (\\w+) access in (\\S+)").matcher(message);
                    yield m.find()
                            ? "접근할 수 없어요: " + m.group(1) + "은(는) " + shortType(m.group(3))
                                    + " 안에서 " + m.group(2) + "예요"
                            : "접근할 수 없어요";
                }
                case "compiler.err.else.without.if" ->
                        "짝이 되는 if가 없는 else예요";
                case "compiler.err.operator.cant.be.applied", "compiler.err.operator.cant.be.applied.1" ->
                        "이 타입에는 그 연산자를 쓸 수 없어요";
                case "compiler.err.invalid.meth.decl.ret.type.req" ->
                        "메서드에 반환 타입이 필요해요 (없으면 void)";
                case "compiler.err.type.found.req" ->
                        "여기에는 int 같은 기본 타입을 쓸 수 없어요 (Integer 같은 클래스 타입을 쓰세요)";
                case "compiler.err.cant.apply.symbol", "compiler.err.cant.apply.symbols" ->
                        "메서드나 생성자에 넘긴 값이 맞지 않아요";
                default -> first; // 아직 번역하지 않은 오류는 javac 원문을 그대로 보여 준다
            };
        }

        private static String typeMismatch(String message, String first) {
            Matcher lossy = Pattern.compile("possible lossy conversion from (\\S+) to (\\S+)").matcher(message);
            if (lossy.find()) {
                return "큰 타입을 작은 타입에 바로 넣을 수 없어요: " + lossy.group(1) + " → " + lossy.group(2)
                        + " (필요하면 (" + lossy.group(2) + ")처럼 직접 변환하세요)";
            }
            Matcher conv = Pattern.compile("incompatible types: (.+?) cannot be converted to (.+)").matcher(first);
            if (conv.find()) {
                return "타입이 맞지 않아요: " + shortType(conv.group(1)) + " 값을 "
                        + shortType(conv.group(2)) + "에 넣을 수 없어요";
            }
            return "타입이 맞지 않아요";
        }

        private static String kindName(String kind) {
            return switch (kind) {
                case "variable" -> "변수";
                case "class" -> "클래스";
                case "method" -> "메서드";
                case "constructor" -> "생성자";
                case "interface" -> "인터페이스";
                case "package" -> "패키지";
                default -> kind;
            };
        }

        /** java.lang.String → String 처럼 앞의 패키지 이름을 떼어 읽기 쉽게 만든다 */
        private static String shortType(String s) {
            return s.replaceAll("\\b(?:[a-z]\\w*\\.)+(?=[A-Z])", "");
        }
    }

    // ---------------------------------------------------------------
    // 셸 실행기 (5단계)
    // PowerShell(없으면 sh)로 명령을 실행하고, 출력을 실시간으로 전달한다.
    // 화면과 상관없이 동작하므로 따로 테스트할 수 있다.
    //
    // 명령마다 새 프로세스를 띄운다. 대신 명령이 끝날 때 "현재 폴더"를 알아내서
    // 다음 명령에 이어 주기 때문에 cd가 유지된다.
    // ---------------------------------------------------------------

    static class ShellRunner {

        interface Listener {
            /** 출력이 나올 때마다 호출된다 (백그라운드 스레드에서). */
            void onOutput(String text);

            /** 끝났을 때 한 번 호출된다 (백그라운드 스레드에서). finalDir은 명령이 끝난 뒤의 폴더, 모르면 null. */
            void onFinished(int exitCode, File finalDir);
        }

        enum Kind { POWERSHELL, POSIX }

        record Shell(Kind kind, String executable, String displayName) {

            /** 이 컴퓨터에서 쓸 셸을 찾는다. Windows는 PowerShell, 그 외는 pwsh가 있으면 pwsh, 없으면 sh */
            static Shell detect() {
                if (isWindows()) {
                    String ps = findOnPath("powershell.exe"); // Windows PowerShell 5.1
                    if (ps != null) return new Shell(Kind.POWERSHELL, ps, "PowerShell");
                    String pwsh = findOnPath("pwsh.exe");
                    if (pwsh != null) return new Shell(Kind.POWERSHELL, pwsh, "PowerShell 7");
                    return new Shell(Kind.POWERSHELL, "powershell.exe", "PowerShell");
                }
                String pwsh = findOnPath("pwsh");
                if (pwsh != null) return new Shell(Kind.POWERSHELL, pwsh, "PowerShell 7");
                return new Shell(Kind.POSIX, "/bin/sh", "sh");
            }
        }

        private static final Pattern ANSI =
                Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\][^\u0007]*\u0007");

        private final Shell shell;
        private volatile Process process;

        ShellRunner(Shell shell) {
            this.shell = shell;
        }

        Shell shell() { return shell; }

        boolean isRunning() {
            Process p = process;
            return p != null && p.isAlive();
        }

        /**
         * 명령을 실행한다. 바로 돌아오고, 결과는 listener로 전달된다.
         * @throws IOException 셸을 시작하지 못한 경우 (예: PowerShell을 찾을 수 없음)
         */
        synchronized void start(String command, File dir, Listener listener) throws IOException {
            if (isRunning()) throw new IllegalStateException("이미 실행 중입니다");

            // 명령이 끝난 뒤 현재 폴더를 알려 주는 표식. 화면에는 나오지 않게 걸러 낸다.
            String marker = "<<<CWD:" + UUID.randomUUID().toString().replace("-", "") + ">>>";
            Path script = writeScript(shell, command, marker);

            ProcessBuilder pb = new ProcessBuilder(commandLine(shell, script));
            if (dir != null && dir.isDirectory()) pb.directory(dir);
            pb.redirectErrorStream(true); // 오류 출력도 같은 화면에 순서대로 섞어서 받는다

            Process p;
            try {
                p = pb.start();
            } catch (IOException e) {
                Files.deleteIfExists(script);
                throw e;
            }
            process = p;

            Thread t = new Thread(() -> pump(p, marker, script, listener), "shell-output");
            t.setDaemon(true);
            t.start();
        }

        /** 실행 중인 프로그램의 입력(키보드)으로 한 줄을 보낸다 */
        boolean sendInput(String line) {
            Process p = process;
            if (p == null || !p.isAlive()) return false;
            try {
                OutputStream os = p.getOutputStream();
                os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        /** 실행 중인 명령과 그 명령이 띄운 프로그램까지 모두 끈다 */
        void stop() {
            Process p = process;
            if (p == null) return;
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }

        /** 프로세스 출력을 읽어서 전달하고, 끝나면 마무리한다 (별도 스레드에서 실행) */
        private void pump(Process p, String marker, Path script, Listener listener) {
            StringBuilder pending = new StringBuilder();   // 아직 내보내지 않은 글
            StringBuilder afterMarker = new StringBuilder(); // 표식 뒤에 오는 글 = 끝난 뒤의 폴더
            boolean markerSeen = false;

            try (Reader r = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
                char[] chunk = new char[4096];
                int n;
                while ((n = r.read(chunk)) != -1) {
                    String text = new String(chunk, 0, n);
                    if (markerSeen) {
                        afterMarker.append(text);
                        continue;
                    }
                    pending.append(text);
                    int idx = pending.indexOf(marker);
                    if (idx >= 0) {
                        emit(listener, pending.substring(0, idx));
                        afterMarker.append(pending.substring(idx + marker.length()));
                        pending.setLength(0);
                        markerSeen = true;
                    } else {
                        // 표식이 두 덩어리로 쪼개져 올 수 있어서, 표식의 앞부분처럼 보이는 끝부분만 잠시 보관한다.
                        // (나머지는 바로 내보낸다. 그래야 "이름?" 같은 입력 안내가 즉시 보인다)
                        int emitLen = pending.length() - partialMarkerLength(pending, marker);
                        if (emitLen > 0) {
                            emit(listener, pending.substring(0, emitLen));
                            pending.delete(0, emitLen);
                        }
                    }
                }
                if (!markerSeen && pending.length() > 0) emit(listener, pending.toString());
            } catch (IOException e) {
                // 프로세스가 중지되면서 읽기가 끊긴 경우
            }

            int exit;
            try {
                exit = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
                exit = -1;
            }

            try {
                Files.deleteIfExists(script);
            } catch (IOException ignored) {
                // 임시 파일이 남아도 큰 문제는 아니다
            }
            process = null;
            listener.onFinished(exit, parseDir(afterMarker.toString()));
        }

        /** pending의 끝부분이 표식의 앞부분과 같다면 그 길이를, 아니면 0을 돌려준다 */
        private static int partialMarkerLength(StringBuilder pending, String marker) {
            int max = Math.min(pending.length(), marker.length() - 1);
            for (int k = max; k > 0; k--) {
                if (marker.startsWith(pending.substring(pending.length() - k))) return k;
            }
            return 0;
        }

        private static void emit(Listener listener, String text) {
            String clean = clean(text);
            if (!clean.isEmpty()) listener.onOutput(clean);
        }

        /** 화면에 보이면 곤란한 것(BOM, 색상 코드, \r)을 정리한다 */
        static String clean(String text) {
            String t = ANSI.matcher(text.replace("\uFEFF", "")).replaceAll("");
            return t.replace("\r\n", "\n").replace('\r', '\n');
        }

        private static File parseDir(String afterMarker) {
            int end = 0;
            while (end < afterMarker.length() && afterMarker.charAt(end) != '\n' && afterMarker.charAt(end) != '\r') end++;
            String path = afterMarker.substring(0, end).trim();
            if (path.isEmpty()) return null;
            File f = new File(path);
            return f.isDirectory() ? f : null;
        }

        // ----- 셸에 넘길 스크립트 만들기 -----

        static List<String> commandLine(Shell shell, Path script) {
            List<String> cmd = new ArrayList<>();
            cmd.add(shell.executable());
            if (shell.kind() == Kind.POWERSHELL) {
                cmd.add("-NoLogo");
                cmd.add("-NoProfile");
                if (isWindows()) {
                    cmd.add("-ExecutionPolicy"); // 기본 설정에서는 .ps1 실행이 막혀 있어서 이 실행에만 풀어 준다
                    cmd.add("Bypass");
                }
                cmd.add("-File");
            }
            cmd.add(script.toString());
            return cmd;
        }

        /**
         * 명령을 임시 스크립트 파일로 저장한다.
         * 명령줄 인자로 직접 넘기면 따옴표와 한글이 깨지기 쉬워서 파일을 거친다.
         */
        static Path writeScript(Shell shell, String command, String marker) throws IOException {
            boolean ps = shell.kind() == Kind.POWERSHELL;
            Path file = Files.createTempFile("minijava-", ps ? ".ps1" : ".sh");
            byte[] body = (ps ? powershellScript(command, marker) : posixScript(command, marker))
                    .getBytes(StandardCharsets.UTF_8);
            if (ps) {
                // Windows PowerShell 5.1은 BOM이 있어야 UTF-8로 읽는다 (없으면 한글이 깨진다)
                byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
                byte[] all = new byte[bom.length + body.length];
                System.arraycopy(bom, 0, all, 0, bom.length);
                System.arraycopy(body, 0, all, bom.length, body.length);
                body = all;
            }
            Files.write(file, body);
            return file;
        }

        static String powershellScript(String command, String marker) {
            return "$ProgressPreference = 'SilentlyContinue'\n"
                    // 출력을 UTF-8로 맞춘다. 콘솔이 없는 환경에서 실패해도 계속 진행한다.
                    + "try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false } catch { }\n"
                    + "try { $OutputEncoding = New-Object System.Text.UTF8Encoding $false } catch { }\n"
                    + "try {\n"
                    + command + "\n"
                    + "} finally {\n"
                    + "    Write-Output ('" + marker + "' + (Get-Location).Path)\n"
                    + "}\n"
                    + "if ($null -ne $LASTEXITCODE) { exit $LASTEXITCODE }\n";
        }

        static String posixScript(String command, String marker) {
            return command + "\n"
                    + "__mj_rc=$?\n"
                    + "printf '%s%s\\n' '" + marker + "' \"$(pwd)\"\n"
                    + "exit $__mj_rc\n";
        }

        // ----- 작은 도구들 -----

        /** 셸에서 안전하게 쓰도록 작은따옴표로 감싼다 (경로에 공백이 있어도 된다) */
        static String quote(Shell shell, String s) {
            return shell.kind() == Kind.POWERSHELL
                    ? "'" + s.replace("'", "''") + "'"
                    : "'" + s.replace("'", "'\\''") + "'";
        }

        /** 프로그램과 인자들을 셸 명령 한 줄로 만든다 */
        static String commandLineFor(Shell shell, List<String> args) {
            String joined = args.stream().map(a -> quote(shell, a)).collect(Collectors.joining(" "));
            return shell.kind() == Kind.POWERSHELL ? "& " + joined : joined;
        }

        static boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        }

        private static String findOnPath(String exe) {
            String path = System.getenv("PATH");
            if (path == null) return null;
            for (String dir : path.split(File.pathSeparator)) {
                String d = dir.replace("\"", "").trim();
                if (d.isEmpty()) continue;
                File f = new File(d, exe);
                if (f.isFile()) return f.getPath();
            }
            return null;
        }
    }

    // ---------------------------------------------------------------
    // 터미널 화면 (5단계)
    // 위쪽에 출력, 아래쪽에 입력칸. 기본 모양 그대로 단순하게 만들었다.
    // ---------------------------------------------------------------

    static class TerminalPanel extends JPanel {

        private static final int MAX_CHARS = 200_000; // 출력이 너무 길어지면 오래된 부분부터 지운다

        private final ShellRunner runner;
        private final JTextArea output = new JTextArea();
        private final JTextField input = new JTextField();
        private final JLabel promptLabel = new JLabel();
        private final JButton stopButton = new JButton("중지");
        private final JButton clearButton = new JButton("지우기");
        private final List<String> history = new ArrayList<>();
        private int historyPos = 0;
        private File workingDir = new File(System.getProperty("user.dir", "."));
        private boolean running = false;
        private boolean stoppedByUser = false;

        TerminalPanel(ShellRunner.Shell shell) {
            super(new BorderLayout());
            this.runner = new ShellRunner(shell);

            output.setEditable(false);
            output.setLineWrap(true);
            output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            output.setMargin(new Insets(4, 6, 4, 6));
            // 새 출력이 나오면 맨 아래로 자동 스크롤
            ((DefaultCaret) output.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

            input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            promptLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            promptLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            stopButton.setEnabled(false);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            buttons.add(stopButton);
            buttons.add(clearButton);

            JPanel inputRow = new JPanel(new BorderLayout());
            inputRow.add(promptLabel, BorderLayout.WEST);
            inputRow.add(input, BorderLayout.CENTER);
            inputRow.add(buttons, BorderLayout.EAST);

            add(new JScrollPane(output), BorderLayout.CENTER);
            add(inputRow, BorderLayout.SOUTH);

            input.addActionListener(e -> submit());
            input.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (running) return;
                    if (e.getKeyCode() == KeyEvent.VK_UP && historyPos > 0) {
                        historyPos--;
                        showHistory();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        historyPos = Math.min(historyPos + 1, history.size());
                        showHistory();
                        e.consume();
                    }
                }
            });
            stopButton.addActionListener(e -> stop());
            clearButton.addActionListener(e -> output.setText(""));

            updatePrompt();
            append(shell.displayName() + " 터미널입니다. 명령을 입력하고 Enter를 누르세요.\n"
                    + "실행 중인 프로그램에 입력을 보내려면 같은 입력칸에 입력하세요. (cls: 화면 지우기)\n");
        }

        ShellRunner.Shell shell() { return runner.shell(); }

        boolean isRunning() { return running; }

        File workingDirectory() { return workingDir; }

        String outputText() { return output.getText(); }

        void focusInput() { input.requestFocusInWindow(); }

        /** 명령이 실행 중이 아닐 때만 작업 폴더를 바꾼다 */
        void setWorkingDirectory(File dir) {
            if (!running && dir != null && dir.isDirectory()) {
                workingDir = dir;
                updatePrompt();
            }
        }

        /** 터미널에 안내 글을 한 줄 남긴다 (명령 실행이 아니다) */
        void notice(String text) {
            ensureNewline();
            append(text + "\n");
        }

        void notifyBusy() {
            append("[이미 실행 중입니다. 먼저 '중지'를 누르세요]\n");
        }

        /** 입력칸에서 Enter를 눌렀을 때 */
        private void submit() {
            String line = input.getText();
            input.setText("");

            if (running) { // 실행 중인 프로그램의 입력으로 보낸다
                append(line + "\n");
                runner.sendInput(line);
                return;
            }
            if (line.isBlank()) return;

            if (history.isEmpty() || !history.get(history.size() - 1).equals(line)) history.add(line);
            historyPos = history.size();

            String t = line.trim();
            if (t.equalsIgnoreCase("cls") || t.equalsIgnoreCase("clear") || t.equalsIgnoreCase("clear-host")) {
                output.setText(""); // 리다이렉트된 환경에서는 셸의 clear가 듣지 않아서 직접 처리한다
                return;
            }
            execute(line, line);
        }

        private void showHistory() {
            input.setText(historyPos < history.size() ? history.get(historyPos) : "");
            input.setCaretPosition(input.getText().length());
        }

        /** 명령을 실행한다. display는 화면에 보여 줄 글, command는 실제로 실행할 글 */
        boolean execute(String display, String command) {
            if (running) {
                notifyBusy();
                return false;
            }
            ensureNewline();
            append(prompt() + " " + display + "\n");

            stoppedByUser = false;
            try {
                runner.start(command, workingDir, new ShellRunner.Listener() {
                    @Override
                    public void onOutput(String text) {
                        SwingUtilities.invokeLater(() -> append(text));
                    }

                    @Override
                    public void onFinished(int exitCode, File finalDir) {
                        SwingUtilities.invokeLater(() -> finished(exitCode, finalDir));
                    }
                });
            } catch (IOException e) {
                append("[실행하지 못했습니다: " + e.getMessage() + "]\n");
                if (runner.shell().kind() == ShellRunner.Kind.POWERSHELL) {
                    append("PowerShell을 찾을 수 없는 것 같습니다. PATH에 powershell.exe가 있는지 확인해 주세요.\n");
                }
                return false;
            }
            setRunning(true);
            return true;
        }

        void stop() {
            if (!running) return;
            stoppedByUser = true;
            runner.stop();
        }

        private void finished(int exitCode, File finalDir) {
            ensureNewline();
            append(stoppedByUser ? "[중지됨]\n" : "[종료 코드: " + exitCode + "]\n");
            if (finalDir != null && finalDir.isDirectory()) workingDir = finalDir; // cd가 유지된다
            setRunning(false);
        }

        private void setRunning(boolean value) {
            running = value;
            stopButton.setEnabled(value);
            updatePrompt();
        }

        private String prompt() {
            String dir = shortDir(workingDir);
            return runner.shell().kind() == ShellRunner.Kind.POWERSHELL ? "PS " + dir + ">" : dir + " $";
        }

        private void updatePrompt() {
            promptLabel.setText(running ? "입력>" : prompt());
        }

        private static String shortDir(File dir) {
            String path = dir.getPath();
            return path.length() <= 40 ? path : "…" + path.substring(path.length() - 39);
        }

        private void ensureNewline() {
            int len = output.getDocument().getLength();
            if (len == 0) return;
            try {
                if (!output.getDocument().getText(len - 1, 1).equals("\n")) append("\n");
            } catch (BadLocationException ignored) {
                // 무시
            }
        }

        private void append(String text) {
            output.append(text);
            int excess = output.getDocument().getLength() - MAX_CHARS;
            if (excess > 0) output.replaceRange("", 0, excess + MAX_CHARS / 4);
        }
    }

    // =================================================================
    // 확장 프로그램 관리 (6단계)
    // 플러그인 폴더(~/.minijava-editor/plugins)의 jar를 읽어서 켜고 끈다.
    // =================================================================

    static class PluginManager {

        /** 편집기가 확장 프로그램마다 따로 만들어 주는 창구. 꺼질 때 등록해 둔 것들을 정리한다. */
        interface ManagedContext extends Context {
            void dispose();
        }

        static final String MANIFEST_CLASS = "MiniJava-Plugin-Class";
        static final String MANIFEST_NAME = "MiniJava-Plugin-Name";
        static final String MANIFEST_DESCRIPTION = "MiniJava-Plugin-Description";

        /** 확장 프로그램 jar 하나의 상태 */
        static final class Entry {
            final File jar;
            final String id;
            volatile String name;
            volatile String description = "";
            volatile boolean enabled;
            volatile boolean loaded;
            volatile String error = "";
            Plugin plugin;
            URLClassLoader loader;
            ManagedContext context;

            Entry(File jar) {
                this.jar = jar;
                this.id = jar.getName();
                this.name = jar.getName();
            }
        }

        private final File dir;
        private final File settingsFile;
        private final Function<String, ManagedContext> contextFactory;
        private final Properties settings = new Properties();
        private final List<Entry> entries = new ArrayList<>();

        PluginManager(File dir, File settingsFile, Function<String, ManagedContext> contextFactory) {
            this.dir = dir;
            this.settingsFile = settingsFile;
            this.contextFactory = contextFactory;
            if (settingsFile.isFile()) {
                try (var r = Files.newBufferedReader(settingsFile.toPath(), StandardCharsets.UTF_8)) {
                    settings.load(r);
                } catch (IOException ignored) {
                    // 설정을 못 읽으면 모두 꺼진 상태로 시작한다 (안전한 쪽)
                }
            }
        }

        static File defaultDirectory() {
            return new File(System.getProperty("user.home"), ".minijava-editor");
        }

        File directory() { return dir; }

        synchronized List<Entry> entries() { return new ArrayList<>(entries); }

        private Entry find(String id) {
            for (Entry e : entries) if (e.id.equals(id)) return e;
            return null;
        }

        /** 플러그인 폴더를 다시 읽는다. 새로 생긴 jar는 "꺼진 상태"로 추가된다. */
        synchronized void scan() {
            File[] found = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
            List<File> jars = found == null ? new ArrayList<>() : new ArrayList<>(List.of(found));
            jars.sort(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));

            for (Entry e : new ArrayList<>(entries)) {
                if (!jars.contains(e.jar)) {
                    unload(e);
                    entries.remove(e);
                }
            }
            for (File jar : jars) {
                if (find(jar.getName()) != null) continue;
                Entry e = new Entry(jar);
                e.enabled = "true".equals(settings.getProperty("enabled." + e.id));
                readManifestInfo(e);
                entries.add(e);
            }
        }

        private void readManifestInfo(Entry e) {
            try (JarFile jf = new JarFile(e.jar)) {
                Manifest mf = jf.getManifest();
                if (mf == null) return;
                String n = mf.getMainAttributes().getValue(MANIFEST_NAME);
                String d = mf.getMainAttributes().getValue(MANIFEST_DESCRIPTION);
                if (n != null && !n.isBlank()) e.name = n;
                if (d != null) e.description = d;
            } catch (IOException ex) {
                e.error = "jar를 읽을 수 없습니다: " + ex.getMessage();
            }
        }

        /** 켜져 있도록 저장된 확장 프로그램을 모두 켠다 (편집기를 시작할 때) */
        synchronized void loadEnabled() {
            for (Entry e : entries) {
                if (e.enabled && !e.loaded) load(e);
            }
        }

        /** 켜거나 끈다. 켜지 못하면 error에 이유를 남기고 꺼진 상태로 둔다. */
        synchronized boolean setEnabled(Entry e, boolean on) {
            if (on) {
                e.enabled = true;
                load(e);
                if (!e.loaded) e.enabled = false;
            } else {
                unload(e);
                e.enabled = false;
                e.error = "";
            }
            settings.setProperty("enabled." + e.id, String.valueOf(e.enabled));
            saveSettings();
            return e.loaded == on;
        }

        /**
         * jar를 플러그인 폴더에 복사해 넣는다. 켜지는 않는다.
         * 같은 이름의 jar를 바꿔 넣으면 코드가 달라진 것이므로 다시 꺼진 상태로 돌아간다.
         */
        synchronized Entry install(File jar) throws IOException {
            try (JarFile jf = new JarFile(jar)) {
                Manifest mf = jf.getManifest();
                if (mf == null || mf.getMainAttributes().getValue(MANIFEST_CLASS) == null) {
                    throw new IOException("확장 프로그램 jar가 아닙니다: manifest에 " + MANIFEST_CLASS + " 항목이 없습니다");
                }
            } catch (java.util.zip.ZipException e) {
                throw new IOException("jar 파일이 아닙니다: " + jar.getName());
            }

            Files.createDirectories(dir.toPath());
            File target = new File(dir, jar.getName());
            Entry existing = find(target.getName());
            if (existing != null) {
                unload(existing);
                entries.remove(existing);
                settings.setProperty("enabled." + existing.id, "false");
                saveSettings();
            }
            if (!jar.getAbsoluteFile().equals(target.getAbsoluteFile())) {
                Files.copy(jar.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            scan();
            return find(target.getName());
        }

        synchronized void shutdown() {
            for (Entry e : entries) unload(e);
        }

        private void load(Entry e) {
            e.error = "";
            URLClassLoader loader = null;
            ManagedContext ctx = null;
            try {
                String className;
                try (JarFile jf = new JarFile(e.jar)) {
                    Manifest mf = jf.getManifest();
                    className = mf == null ? null : mf.getMainAttributes().getValue(MANIFEST_CLASS);
                }
                if (className == null || className.isBlank()) {
                    throw new IllegalStateException("manifest에 " + MANIFEST_CLASS + " 항목이 없습니다");
                }
                // 부모를 편집기의 클래스로더로 둔다: 확장 프로그램이 MiniJavaEditor.Plugin을 찾을 수 있다
                loader = new URLClassLoader(new URL[]{e.jar.toURI().toURL()}, MiniJavaEditor.class.getClassLoader());
                Class<?> cls = Class.forName(className.trim(), true, loader);
                if (!Plugin.class.isAssignableFrom(cls)) {
                    throw new IllegalStateException(className + " 은(는) MiniJavaEditor.Plugin을 구현하지 않았습니다");
                }
                Plugin plugin = (Plugin) cls.getDeclaredConstructor().newInstance();
                ctx = contextFactory.apply(e.id);
                plugin.onLoad(ctx);

                e.plugin = plugin;
                e.loader = loader;
                e.context = ctx;
                e.loaded = true;
                e.name = plugin.name();
                e.description = plugin.description();
            } catch (Exception | LinkageError t) {
                // 확장 프로그램의 어떤 오류도 편집기를 멈추게 하지 않는다
                e.error = describe(t);
                e.loaded = false;
                if (ctx != null) {
                    try {
                        ctx.dispose();
                    } catch (Exception | LinkageError ignored) {
                        // 정리 중 오류는 무시
                    }
                }
                if (loader != null) {
                    try {
                        loader.close();
                    } catch (IOException ignored) {
                        // 닫지 못해도 큰 문제는 아니다
                    }
                }
            }
        }

        private void unload(Entry e) {
            if (!e.loaded) return;
            try {
                if (e.plugin != null) e.plugin.onUnload();
            } catch (Exception | LinkageError ex) {
                e.error = "끄는 중 오류: " + describe(ex);
            }
            try {
                if (e.context != null) e.context.dispose();
            } catch (Exception | LinkageError ignored) {
                // 정리 중 오류는 무시
            }
            try {
                if (e.loader != null) e.loader.close();
            } catch (IOException ignored) {
                // 무시
            }
            e.plugin = null;
            e.loader = null;
            e.context = null;
            e.loaded = false;
        }

        private void saveSettings() {
            try {
                Files.createDirectories(settingsFile.getAbsoluteFile().getParentFile().toPath());
                try (var w = Files.newBufferedWriter(settingsFile.toPath(), StandardCharsets.UTF_8)) {
                    settings.store(w, "Mini Java Editor - 확장 프로그램 설정");
                }
            } catch (IOException ignored) {
                // 저장하지 못하면 다음 실행 때 꺼진 상태로 시작한다 (안전한 쪽)
            }
        }

        static String describe(Throwable t) {
            Throwable root = t;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            String m = root.getMessage();
            return root.getClass().getSimpleName() + (m == null ? "" : ": " + m);
        }
    }

    // =================================================================
    // 확장 프로그램 개발 키트 (6단계)
    // 컴파일용 API jar, 예제 소스, 빌드 스크립트, 설명서를 만들어 준다.
    // =================================================================

    static class PluginKit {

        /**
         * 컴파일용 API 껍데기. 편집기 안의 MiniJavaEditor.Plugin / Context와 똑같은 모양이어야 한다.
         * (확장 프로그램은 실행할 때 편집기의 진짜 인터페이스를 사용한다. 모양이 같은지는 테스트로 확인한다.)
         */
        static final String API_SOURCE = """
                import javax.swing.JFrame;
                import java.io.File;
                import java.util.function.Consumer;

                public class MiniJavaEditor {
                    private MiniJavaEditor() { }

                    public interface Plugin {
                        String name();
                        default String description() { return ""; }
                        void onLoad(Context context);
                        default void onUnload() { }
                    }

                    public interface Context {
                        JFrame frame();
                        String text();
                        String selectedText();
                        void insertText(String text);
                        void replaceAll(String text);
                        File currentFile();
                        File projectRoot();
                        void openFile(File file);
                        void showMessage(String message);
                        void runInTerminal(String command);
                        void addMenuItem(String menuName, String label, Runnable action);
                        void addSnippet(String name, String description, String template);
                        void onFileSaved(Consumer<File> listener);
                        int caretPosition();
                        int selectionStart();
                        int selectionEnd();
                        void select(int start, int end);
                        void onTabKey(java.util.function.BooleanSupplier handler);
                        void onEscapeKey(java.util.function.BooleanSupplier handler);
                    }
                }
                """;

        static final String SAMPLE_SOURCE = """
                /**
                 * 예제 확장 프로그램.
                 * - "확장 > Hello 삽입": 커서 위치에 System.out.println("Hello")를 넣는다
                 * - "확장 > 대문자로 바꾸기": 선택한 글을 대문자로 바꾼다
                 * - 자동완성 스니펫 "hello"
                 */
                public class HelloPlugin implements MiniJavaEditor.Plugin {

                    @Override
                    public String name() {
                        return "Hello 예제";
                    }

                    @Override
                    public String description() {
                        return "메뉴와 스니펫을 추가하는 예제입니다";
                    }

                    @Override
                    public void onLoad(MiniJavaEditor.Context context) {
                        context.addMenuItem("확장", "Hello 삽입", () ->
                                context.insertText("System.out.println(\\"Hello\\");"));

                        context.addMenuItem("확장", "대문자로 바꾸기", () -> {
                            String selected = context.selectedText();
                            if (selected.isEmpty()) {
                                context.showMessage("먼저 바꿀 글을 선택하세요.");
                            } else {
                                context.insertText(selected.toUpperCase());
                            }
                        });

                        context.addSnippet("hello", "Hello 출력", "System.out.println(\\"Hello, $0\\");");
                    }
                }
                """;

        static final String MANIFEST_TEXT = "MiniJava-Plugin-Class: HelloPlugin\n";

        static final String BUILD_PS1 = """
                # 확장 프로그램 만들기 (PowerShell). JDK가 필요합니다. 사용법: .\\build.ps1
                $ErrorActionPreference = 'Stop'
                if (Test-Path out) { Remove-Item out -Recurse -Force }
                New-Item -ItemType Directory out | Out-Null
                javac -encoding UTF-8 -cp minijava-api.jar -d out HelloPlugin.java
                jar --create --file hello-plugin.jar --manifest manifest.txt -C out .
                Write-Host "만들었습니다: hello-plugin.jar"
                """;

        static final String BUILD_SH = """
                #!/bin/sh
                # 확장 프로그램 만들기 (sh). JDK가 필요합니다. 사용법: sh build.sh
                set -e
                rm -rf out && mkdir out
                javac -encoding UTF-8 -cp minijava-api.jar -d out HelloPlugin.java
                jar --create --file hello-plugin.jar --manifest manifest.txt -C out .
                echo "만들었습니다: hello-plugin.jar"
                """;

        static final String README = """
                Mini Java Editor 확장 프로그램 개발 키트
                ========================================

                들어 있는 것
                  minijava-api.jar   컴파일할 때만 쓰는 API (편집기가 실행할 때는 자기 것을 씁니다)
                  HelloPlugin.java   예제 소스
                  manifest.txt       jar에 넣을 설정 (MiniJava-Plugin-Class: 클래스이름)
                  build.ps1 / build.sh   jar 만들기 스크립트
                  hello-plugin.jar   미리 만들어 둔 예제 (바로 설치해 볼 수 있습니다)

                바로 써 보기
                  1) 편집기의 "확장 > 확장 프로그램 관리 > 확장 프로그램 추가"에서 hello-plugin.jar를 고릅니다.
                  2) 목록에서 "사용"에 체크합니다 (이 컴퓨터에서 코드를 실행하므로 확인 창이 뜹니다).
                  3) "확장" 메뉴에 "Hello 삽입" 등이 생깁니다.

                직접 만들기
                  1) HelloPlugin.java를 고쳐서 자기 확장 프로그램을 만듭니다.
                     MiniJavaEditor.Plugin을 구현하고, onLoad에서 메뉴와 스니펫을 등록하면 됩니다.
                  2) manifest.txt의 클래스 이름을 맞춥니다.
                  3) build.ps1 (또는 build.sh)을 실행하면 jar가 만들어집니다.
                  4) 위의 "바로 써 보기"처럼 설치합니다.

                할 수 있는 일 (MiniJavaEditor.Context)
                  text(), selectedText()   지금 보는 글, 선택한 글 읽기
                  insertText(), replaceAll()   글 넣기/바꾸기 (Ctrl+Z로 되돌릴 수 있습니다)
                  currentFile(), projectRoot(), openFile()
                  addMenuItem(메뉴, 이름, 동작)   메뉴 항목 추가
                  addSnippet(이름, 설명, 템플릿)   자동완성 스니펫 추가 ($0은 커서 자리)
                  runInTerminal(명령)   터미널에서 명령 실행
                  onFileSaved(함수)   파일이 저장될 때 호출
                  caretPosition(), selectionStart(), selectionEnd(), select(시작, 끝)   커서와 선택 영역
                  onTabKey(처리함수), onEscapeKey(처리함수)   Tab / Esc 키 가로채기 (true를 돌려주면 처리된 것)
                  showMessage(글), frame()

                예제로 볼 만한 것
                  편집기와 함께 제공되는 기본 확장 프로그램(extensions\\src 폴더)의 소스를 열어 보세요.

                주의
                  확장 프로그램은 이 컴퓨터에서 어떤 코드든 실행할 수 있습니다.
                  직접 만들었거나 믿을 수 있는 것만 설치하세요.
                """;

        /** 개발 키트를 dir 폴더에 만든다 (JDK의 컴파일러가 필요하다) */
        static void createKit(File dir) throws IOException {
            if (ToolProvider.getSystemJavaCompiler() == null) {
                throw new IOException("JDK가 필요합니다 (컴파일러를 찾을 수 없습니다)");
            }
            Files.createDirectories(dir.toPath());
            File apiJar = new File(dir, "minijava-api.jar");
            buildApiJar(apiJar);
            Files.writeString(dir.toPath().resolve("HelloPlugin.java"), SAMPLE_SOURCE);
            Files.writeString(dir.toPath().resolve("manifest.txt"), MANIFEST_TEXT);
            Files.writeString(dir.toPath().resolve("build.ps1"), BUILD_PS1);
            Files.writeString(dir.toPath().resolve("build.sh"), BUILD_SH);
            Files.writeString(dir.toPath().resolve("README.txt"), README);
            buildPluginJar(apiJar, "HelloPlugin", SAMPLE_SOURCE, new File(dir, "hello-plugin.jar"));
        }

        /** 컴파일용 API jar를 만든다 */
        static void buildApiJar(File dest) throws IOException {
            Path classes = compile("MiniJavaEditor", API_SOURCE, List.of());
            try {
                writeJar(classes, dest, Map.of());
            } finally {
                deleteTree(classes);
            }
        }

        /** 소스 하나로 확장 프로그램 jar를 만든다. className은 manifest의 MiniJava-Plugin-Class가 된다. */
        static void buildPluginJar(File apiJar, String className, String source, File dest) throws IOException {
            buildPluginJar(apiJar, className, source, dest, Map.of());
        }

        /**
         * 위와 같고, manifest에 이름(MiniJava-Plugin-Name)과 설명(MiniJava-Plugin-Description) 같은 항목을 더 넣는다.
         * 이름과 설명은 확장 프로그램을 켜기 전에도 관리 창 목록에 보인다.
         */
        static void buildPluginJar(File apiJar, String className, String source, File dest,
                                   Map<String, String> extraManifest) throws IOException {
            Path classes = compile(className, source, List.of("-cp", apiJar.getPath()));
            try {
                Map<String, String> attrs = new LinkedHashMap<>();
                attrs.put(PluginManager.MANIFEST_CLASS, className);
                attrs.putAll(extraManifest);
                writeJar(classes, dest, attrs);
            } finally {
                deleteTree(classes);
            }
        }

        private static Path compile(String className, String source, List<String> extraOptions) throws IOException {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new IOException("JDK가 필요합니다 (컴파일러를 찾을 수 없습니다)");

            Path out = Files.createTempDirectory("minijava-kit");
            DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
            JavaFileObject src = new SimpleJavaFileObject(
                    URI.create("string:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            };
            List<String> options = new ArrayList<>(List.of("-d", out.toString(), "-encoding", "UTF-8", "-proc:none"));
            options.addAll(extraOptions);
            try (StandardJavaFileManager fm =
                         compiler.getStandardFileManager(diags, Locale.ENGLISH, StandardCharsets.UTF_8)) {
                Boolean ok = compiler.getTask(null, fm, diags, options, null, List.of(src)).call();
                if (!Boolean.TRUE.equals(ok)) {
                    String why = diags.getDiagnostics().stream()
                            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                            .map(d -> d.getMessage(Locale.ENGLISH).lines().findFirst().orElse(""))
                            .findFirst().orElse("알 수 없는 오류");
                    deleteTree(out);
                    throw new IOException("컴파일하지 못했습니다: " + why);
                }
            }
            return out;
        }

        private static void writeJar(Path classesDir, File dest, Map<String, String> manifestAttributes)
                throws IOException {
            Manifest mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifestAttributes.forEach((k, v) -> mf.getMainAttributes().putValue(k, v));
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(dest.toPath()), mf);
                 Stream<Path> files = Files.walk(classesDir)) {
                for (Path p : files.filter(Files::isRegularFile).sorted().toList()) {
                    String name = classesDir.relativize(p).toString().replace(File.separatorChar, '/');
                    jos.putNextEntry(new JarEntry(name));
                    Files.copy(p, jos);
                    jos.closeEntry();
                }
            }
        }

        private static void deleteTree(Path dir) throws IOException {
            if (!Files.exists(dir)) return;
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }


    // =================================================================
    // Maven Central 연동 (7단계)
    // pom.xml의 라이브러리를 읽어서 Maven Central에서 내려받는다 (필요한 하위 라이브러리까지).
    // =================================================================

    /** Maven 좌표 (group:artifact:version). version이 null일 수 있다. */
    record Coord(String group, String artifact, String version) {
        String key() { return group + ":" + artifact; }

        @Override
        public String toString() { return version == null ? key() : key() + ":" + version; }

        /** "group:artifact" 또는 "group:artifact:version". 이 형식이 아니면 null (검색어로 취급한다) */
        static Coord parse(String s) {
            String[] p = s.trim().split(":", -1);
            if (p.length < 2 || p.length > 3) return null;
            for (String part : p) {
                if (!part.matches("[A-Za-z0-9_.+\\-]+") || part.contains("..")) return null;
            }
            return new Coord(p[0], p[1], p.length == 3 ? p[2] : null);
        }
    }

    /** 아주 작은 JSON 읽기 도구 (Maven Central 검색 결과를 읽는 데만 쓴다) */
    static final class MiniJson {
        private final String s;
        private int i = 0;

        private MiniJson(String s) { this.s = s; }

        /** JSON 글을 Map / List / String / Double / Boolean / null 로 바꾼다 */
        static Object parse(String text) {
            MiniJson p = new MiniJson(text);
            p.skipWs();
            Object v = p.value();
            p.skipWs();
            if (p.i != text.length()) throw p.error("끝에 남는 글이 있습니다");
            return v;
        }

        /** root에서 키(또는 배열 위치)를 차례로 따라간다. 없으면 null. */
        static Object get(Object root, Object... path) {
            Object cur = root;
            for (Object key : path) {
                if (cur instanceof Map<?, ?> m && key instanceof String k) cur = m.get(k);
                else if (cur instanceof List<?> l && key instanceof Integer idx && idx >= 0 && idx < l.size()) cur = l.get(idx);
                else return null;
            }
            return cur;
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON 오류(" + i + "번째 글자): " + msg);
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object value() {
            if (i >= s.length()) throw error("값이 없습니다");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String word, Object value) {
            if (!s.startsWith(word, i)) throw error("알 수 없는 값");
            i += word.length();
            return value;
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') { i++; return map; }
            while (true) {
                skipWs();
                if (i >= s.length() || s.charAt(i) != '"') throw error("키가 필요합니다");
                String key = string();
                skipWs();
                if (i >= s.length() || s.charAt(i) != ':') throw error(": 가 필요합니다");
                i++;
                skipWs();
                map.put(key, value());
                skipWs();
                if (i >= s.length()) throw error("}가 없습니다");
                char c = s.charAt(i++);
                if (c == '}') return map;
                if (c != ',') throw error(", 또는 } 가 필요합니다");
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') { i++; return list; }
            while (true) {
                skipWs();
                list.add(value());
                skipWs();
                if (i >= s.length()) throw error("]가 없습니다");
                char c = s.charAt(i++);
                if (c == ']') return list;
                if (c != ',') throw error(", 또는 ] 가 필요합니다");
            }
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++; // 여는 따옴표
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error("\\u 뒤에 16진수 4자리가 필요합니다");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> sb.append(e); // \" \\ \/
                }
            }
            throw error("문자열이 닫히지 않았습니다");
        }

        private Double number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw error("알 수 없는 글자 '" + s.charAt(i) + "'");
            return Double.valueOf(s.substring(start, i));
        }
    }

    /** Maven 저장소에서 라이브러리를 찾아 내려받는다 */
    static class MavenResolver {

        /** 주소에서 내용을 가져온다. 없으면 FileNotFoundException, 그 외 문제는 IOException. */
        interface Fetcher {
            byte[] fetch(String url, long maxBytes) throws IOException;
        }

        /** 의존성 하나 (pom에 적힌 그대로. version 등이 null일 수 있다) */
        record Dep(String group, String artifact, String version, String scope, boolean optional,
                   String type, String classifier, Set<String> exclusions) {
            String key() { return group + ":" + artifact; }
        }

        /** 해결 결과: 내려받은 jar들(클래스패스 순서), 확정된 좌표들, 알려 줄 경고들 */
        record Result(List<File> jars, List<Coord> resolved, List<String> messages) { }

        record SearchHit(String group, String artifact, String latestVersion) {
            @Override
            public String toString() { return group + ":" + artifact + "   (최신 " + latestVersion + ")"; }
        }

        /** 읽어 온 pom 하나. 값에 ${...}가 남아 있을 수 있다. */
        static final class Pom {
            String group;
            String artifact;
            String version;
            String packaging = "jar";
            String parentGroup;
            String parentArtifact;
            String parentVersion;
            String parentRelativePath;
            Pom parent;
            final Map<String, String> props = new LinkedHashMap<>();
            final List<Dep> deps = new ArrayList<>();
            final List<Dep> managed = new ArrayList<>();
        }

        private static final long MAX_POM_BYTES = 5L * 1024 * 1024;
        private static final long MAX_JAR_BYTES = 300L * 1024 * 1024;
        private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_.+\\-]+");
        private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

        private final File cacheDir;
        private final String repoBase;
        private final String searchUrl;
        private final Fetcher fetcher;
        private final Map<String, Pom> pomCache = new HashMap<>();

        MavenResolver(File cacheDir, String repoBase, String searchUrl, Fetcher fetcher) {
            this.cacheDir = cacheDir;
            this.repoBase = checkUrl(repoBase.endsWith("/") ? repoBase : repoBase + "/");
            this.searchUrl = checkUrl(searchUrl);
            this.fetcher = fetcher;
        }

        /** 기본 설정: 공식 Maven Central */
        static MavenResolver central(File cacheDir) {
            return new MavenResolver(cacheDir, "https://repo.maven.apache.org/maven2/",
                    "https://search.maven.org/solrsearch/select", new HttpFetcher());
        }

        /** 안전을 위해 https만 허용한다 (시험용 내 컴퓨터 주소는 예외) */
        private static String checkUrl(String url) {
            if (url.startsWith("https://") || url.startsWith("http://localhost")
                    || url.startsWith("http://127.0.0.1")) {
                return url;
            }
            throw new IllegalArgumentException("https 주소만 사용할 수 있습니다: " + url);
        }

        /** 실제 인터넷에서 내려받는 구현 */
        static class HttpFetcher implements Fetcher {
            private final HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL) // https -> http 로 내려가는 이동은 막힌다
                    .proxy(ProxySelector.getDefault())
                    .build();

            @Override
            public byte[] fetch(String url, long maxBytes) throws IOException {
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(120))
                            .header("User-Agent", "MiniJavaEditor")
                            .GET().build();
                    HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream in = resp.body()) {
                        int code = resp.statusCode();
                        if (code == 404) throw new FileNotFoundException(url);
                        if (code != 200) throw new IOException("HTTP " + code + ": " + url);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[16 * 1024];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                            if (out.size() > maxBytes) throw new IOException("파일이 너무 큽니다: " + url);
                        }
                        return out.toByteArray();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("중단되었습니다", e);
                } catch (IllegalArgumentException e) {
                    throw new IOException("잘못된 주소입니다: " + url, e);
                }
            }
        }

        // =============================================================
        // pom 읽기
        // =============================================================

        private static Element child(Element parent, String name) {
            if (parent == null) return null;
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && e.getTagName().equals(name)) return e;
            }
            return null;
        }

        private static List<Element> children(Element parent, String name) {
            List<Element> list = new ArrayList<>();
            if (parent == null) return list;
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && e.getTagName().equals(name)) list.add(e);
            }
            return list;
        }

        private static String text(Element parent, String name) {
            Element e = child(parent, name);
            if (e == null) return null;
            String t = e.getTextContent().trim();
            return t.isEmpty() ? null : t;
        }

        static Document parseXml(byte[] xml) throws IOException {
            try {
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // 외부 개체 공격 방지
                f.setXIncludeAware(false);
                f.setExpandEntityReferences(false);
                return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            } catch (Exception e) {
                throw new IOException("XML을 읽을 수 없습니다: " + e.getMessage(), e);
            }
        }

        static Pom parsePom(byte[] xml) throws IOException {
            Element root = parseXml(xml).getDocumentElement();
            if (!"project".equals(root.getTagName())) throw new IOException("pom.xml이 아닙니다 (<project>가 없습니다)");

            Pom pom = new Pom();
            pom.group = text(root, "groupId");
            pom.artifact = text(root, "artifactId");
            pom.version = text(root, "version");
            String packaging = text(root, "packaging");
            if (packaging != null) pom.packaging = packaging;

            Element parent = child(root, "parent");
            if (parent != null) {
                pom.parentGroup = text(parent, "groupId");
                pom.parentArtifact = text(parent, "artifactId");
                pom.parentVersion = text(parent, "version");
                pom.parentRelativePath = text(parent, "relativePath");
            }
            Element props = child(root, "properties");
            if (props != null) {
                for (Node n = props.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n instanceof Element e) pom.props.put(e.getTagName(), e.getTextContent().trim());
                }
            }
            pom.deps.addAll(readDeps(child(root, "dependencies")));
            pom.managed.addAll(readDeps(child(child(root, "dependencyManagement"), "dependencies")));
            return pom;
        }

        private static List<Dep> readDeps(Element depsEl) {
            List<Dep> list = new ArrayList<>();
            for (Element d : children(depsEl, "dependency")) {
                Set<String> ex = new HashSet<>();
                for (Element x : children(child(d, "exclusions"), "exclusion")) {
                    ex.add(Optional.ofNullable(text(x, "groupId")).orElse("*") + ":"
                            + Optional.ofNullable(text(x, "artifactId")).orElse("*"));
                }
                String type = text(d, "type");
                list.add(new Dep(text(d, "groupId"), text(d, "artifactId"), text(d, "version"),
                        text(d, "scope"), "true".equalsIgnoreCase(text(d, "optional")),
                        type == null ? "jar" : type, text(d, "classifier"), ex));
            }
            return list;
        }

        // ----- ${...} 값 채우기 -----

        static String effectiveGroup(Pom p) {
            return p.group != null ? p.group : (p.parent != null ? effectiveGroup(p.parent) : p.parentGroup);
        }

        static String effectiveVersion(Pom p) {
            return p.version != null ? p.version : (p.parent != null ? effectiveVersion(p.parent) : p.parentVersion);
        }

        private static String lookup(Pom pom, String name) {
            switch (name) {
                case "project.version", "pom.version", "version": return effectiveVersion(pom);
                case "project.groupId", "pom.groupId", "groupId": return effectiveGroup(pom);
                case "project.artifactId", "pom.artifactId", "artifactId": return pom.artifact;
                case "project.parent.version", "parent.version": return pom.parentVersion;
                case "project.parent.groupId", "parent.groupId": return pom.parentGroup;
                case "project.parent.artifactId", "parent.artifactId": return pom.parentArtifact;
                default: break;
            }
            for (Pom p = pom; p != null; p = p.parent) {
                String v = p.props.get(name);
                if (v != null) return v;
            }
            return null;
        }

        /** s 안의 ${이름}을 pom의 속성 값으로 바꾼다 (모르는 이름은 그대로 둔다) */
        static String interpolate(String s, Pom pom) {
            if (s == null) return null;
            String cur = s;
            for (int pass = 0; pass < 10 && cur.contains("${"); pass++) {
                Matcher m = PROPERTY.matcher(cur);
                StringBuilder sb = new StringBuilder();
                boolean changed = false;
                while (m.find()) {
                    String v = lookup(pom, m.group(1).trim());
                    if (v != null && !v.equals(m.group())) {
                        m.appendReplacement(sb, Matcher.quoteReplacement(v));
                        changed = true;
                    } else {
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                    }
                }
                m.appendTail(sb);
                cur = sb.toString();
                if (!changed) break;
            }
            return cur;
        }

        private static Dep interpolateDep(Dep d, Pom ctx) {
            return new Dep(interpolate(d.group(), ctx), interpolate(d.artifact(), ctx), interpolate(d.version(), ctx),
                    d.scope(), d.optional(), d.type(), interpolate(d.classifier(), ctx), d.exclusions());
        }

        // ----- 부모 pom, 버전 관리(dependencyManagement) -----

        /** 부모의 의존성을 먼저, 자신의 것을 나중에 (같은 라이브러리는 자신의 것이 우선) */
        private static List<Dep> effectiveDeps(Pom pom) {
            Map<String, Dep> map = new LinkedHashMap<>();
            List<Pom> chain = new ArrayList<>();
            for (Pom p = pom; p != null; p = p.parent) chain.add(0, p);
            for (Pom p : chain) {
                for (Dep d : p.deps) {
                    Dep r = interpolateDep(d, pom);
                    if (r.group() != null && r.artifact() != null) map.put(r.key() + ":" + r.classifier(), r);
                }
            }
            return new ArrayList<>(map.values());
        }

        /** 버전이 적혀 있지 않은 의존성의 버전을 정해 주는 표 (부모와 import한 BOM 포함) */
        private Map<String, String> managedVersions(Pom pom, boolean offline, Consumer<String> log) {
            Map<String, String> result = new HashMap<>();
            collectManaged(pom, pom, result, offline, log, 0);
            return result;
        }

        private void collectManaged(Pom leaf, Pom p, Map<String, String> result, boolean offline,
                                    Consumer<String> log, int depth) {
            if (p.parent != null && depth < 12) collectManaged(leaf, p.parent, result, offline, log, depth + 1);
            for (Dep raw : p.managed) {
                Dep d = interpolateDep(raw, leaf);
                if (d.group() == null || d.artifact() == null || d.version() == null) continue;
                if ("import".equals(d.scope()) && "pom".equals(d.type())) {
                    if (depth > 6 || !isSafe(d.group(), d.artifact(), d.version())) continue;
                    try {
                        Pom bom = loadRemote(new Coord(d.group(), d.artifact(), d.version()), offline, log, 0);
                        Map<String, String> imported = new HashMap<>();
                        collectManaged(bom, bom, imported, offline, log, depth + 3);
                        imported.forEach(result::putIfAbsent); // 직접 적은 것이 import한 것보다 우선
                    } catch (IOException e) {
                        log.accept("BOM을 읽지 못해 건너뜁니다: " + d.group() + ":" + d.artifact() + ":" + d.version());
                    }
                } else {
                    result.put(d.key(), d.version());
                }
            }
        }

        private static boolean isSafe(String... parts) {
            for (String p : parts) {
                // ".."가 들어 있으면 저장소 폴더 밖으로 나갈 수 있으므로 막는다
                if (p == null || !SAFE.matcher(p).matches() || p.contains("..")) return false;
            }
            return true;
        }

        // ----- pom 가져오기 -----

        private static String relPath(Coord c, String classifier, String ext) {
            return c.group().replace('.', '/') + "/" + c.artifact() + "/" + c.version() + "/"
                    + c.artifact() + "-" + c.version() + (classifier == null ? "" : "-" + classifier) + "." + ext;
        }

        private Pom loadRemote(Coord c, boolean offline, Consumer<String> log, int depth) throws IOException {
            if (!isSafe(c.group(), c.artifact(), c.version())) {
                throw new IOException("올바르지 않은 좌표입니다: " + c);
            }
            Pom cached = pomCache.get(c.toString());
            if (cached != null) return cached;

            File f = fetchFile(relPath(c, null, "pom"), offline, log, MAX_POM_BYTES);
            Pom pom = parsePom(Files.readAllBytes(f.toPath()));
            pomCache.put(c.toString(), pom);

            if (pom.parentArtifact != null && depth < 10) {
                String pg = interpolate(pom.parentGroup, pom);
                String pv = interpolate(pom.parentVersion, pom);
                if (pg != null && pv != null) {
                    try {
                        pom.parent = loadRemote(new Coord(pg, pom.parentArtifact, pv), offline, log, depth + 1);
                    } catch (IOException e) {
                        log.accept("부모 pom을 읽지 못했습니다 (" + pg + ":" + pom.parentArtifact + ":" + pv + "): " + e.getMessage());
                    }
                }
            }
            return pom;
        }

        /** 내 프로젝트의 pom.xml 읽기. 부모는 relativePath의 파일이 맞으면 그것을, 아니면 저장소에서 가져온다. */
        Pom loadLocalPom(File pomFile, boolean offline, Consumer<String> log, int depth) throws IOException {
            Pom pom = parsePom(Files.readAllBytes(pomFile.toPath()));
            if (pom.parentArtifact == null || depth >= 10) return pom;

            String rel = pom.parentRelativePath == null ? "../pom.xml" : pom.parentRelativePath;
            File candidate = new File(pomFile.getAbsoluteFile().getParentFile(), rel);
            if (candidate.isDirectory()) candidate = new File(candidate, "pom.xml");
            if (candidate.isFile() && !candidate.getAbsoluteFile().equals(pomFile.getAbsoluteFile())) {
                try {
                    Pom local = loadLocalPom(candidate, offline, log, depth + 1);
                    if (pom.parentArtifact.equals(local.artifact)) {
                        pom.parent = local;
                        return pom;
                    }
                } catch (IOException ignored) {
                    // 저장소에서 가져오는 방법으로 넘어간다
                }
            }
            String pg = interpolate(pom.parentGroup, pom);
            String pv = interpolate(pom.parentVersion, pom);
            if (pg != null && pv != null) {
                try {
                    pom.parent = loadRemote(new Coord(pg, pom.parentArtifact, pv), offline, log, 0);
                } catch (IOException e) {
                    log.accept("부모 pom을 읽지 못했습니다: " + pg + ":" + pom.parentArtifact + ":" + pv);
                }
            }
            return pom;
        }

        // ----- 파일 내려받기 (캐시 + 체크섬 확인) -----

        /**
         * relPath의 파일을 캐시에 받아 둔다 (이미 있으면 그대로 쓴다).
         * 저장소가 알려 주는 SHA-1 체크섬과 맞는지 확인하고, 다르면 저장하지 않는다.
         */
        private File fetchFile(String relPath, boolean offline, Consumer<String> log, long maxBytes) throws IOException {
            File f = new File(cacheDir, relPath);
            if (!f.getCanonicalPath().startsWith(cacheDir.getCanonicalPath() + File.separator)) {
                throw new IOException("올바르지 않은 경로입니다: " + relPath); // 경로 탈출 방지
            }
            if (f.isFile() && f.length() > 0) return f;
            if (offline) throw new FileNotFoundException("아직 내려받지 않았습니다: " + relPath);

            log.accept("내려받는 중: " + relPath);
            byte[] data = fetcher.fetch(repoBase + relPath, maxBytes);

            try {
                String expected = new String(fetcher.fetch(repoBase + relPath + ".sha1", 4096), StandardCharsets.UTF_8)
                        .trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
                if (!expected.equals(actual)) {
                    throw new IOException("체크섬이 일치하지 않습니다 (파일이 손상되었거나 바뀌었을 수 있습니다): " + relPath);
                }
            } catch (FileNotFoundException e) {
                log.accept("체크섬 파일이 없어 확인하지 못했습니다: " + relPath);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("체크섬")) throw e;
                log.accept("체크섬을 확인하지 못했습니다: " + relPath + " (" + e.getMessage() + ")");
            }

            Files.createDirectories(f.getParentFile().toPath());
            Path tmp = Files.createTempFile(f.getParentFile().toPath(), "dl-", ".tmp");
            try {
                Files.write(tmp, data);
                Files.move(tmp, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
            return f;
        }

        // =============================================================
        // 의존성 해결
        // =============================================================

        /** pom.xml을 읽어서 필요한 jar를 모두 준비한다 */
        Result resolve(File pomFile, boolean offlineOnly, Consumer<String> log) throws IOException {
            Pom project = loadLocalPom(pomFile, offlineOnly, log, 0);
            Map<String, String> managed = managedVersions(project, offlineOnly, log);

            List<Dep> roots = new ArrayList<>();
            for (Dep d : effectiveDeps(project)) {
                String scope = d.scope() == null ? "compile" : d.scope();
                if (scope.equals("system") || scope.equals("import")) continue;
                if (!"jar".equals(d.type())) continue;
                roots.add(d);
            }
            return resolveRoots(roots, managed, offlineOnly, log);
        }

        private record Pending(Dep dep, int depth, Set<String> exclusions) { }

        /**
         * 가장 가까운 선언이 이기는 규칙(nearest wins)으로 버전을 정하며 하위 라이브러리까지 따라간다.
         * 하위 라이브러리는 compile/runtime 범위이고 optional이 아닌 것만 가져온다.
         */
        Result resolveRoots(List<Dep> roots, Map<String, String> rootManaged, boolean offline, Consumer<String> log) {
            List<String> messages = new ArrayList<>();
            List<File> jars = new ArrayList<>();
            Map<String, Coord> chosen = new LinkedHashMap<>();
            Deque<Pending> queue = new ArrayDeque<>();
            for (Dep d : roots) queue.add(new Pending(d, 0, d.exclusions()));

            while (!queue.isEmpty()) {
                Pending n = queue.poll();
                Dep d = n.dep();
                if (d.group() == null || d.artifact() == null) continue;
                if (chosen.containsKey(d.key()) || isExcluded(n.exclusions(), d)) continue;

                // 루트 pom의 버전 표가 있으면 하위 라이브러리의 버전도 그것을 따른다
                String version = d.version();
                if (rootManaged.containsKey(d.key()) && (n.depth() > 0 || version == null)) {
                    version = rootManaged.get(d.key());
                }
                if (version == null || version.isBlank()) {
                    messages.add("버전을 알 수 없어 건너뜁니다: " + d.key());
                    continue;
                }
                if (version.startsWith("[") || version.startsWith("(")) {
                    Matcher m = Pattern.compile("[0-9][^,\\])]*").matcher(version);
                    if (!m.find()) {
                        messages.add("버전 범위를 읽을 수 없어 건너뜁니다: " + d.key() + ":" + version);
                        continue;
                    }
                    messages.add("버전 범위는 지원하지 않아 " + m.group() + " 을(를) 사용합니다: " + d.key() + ":" + version);
                    version = m.group();
                }
                if (version.endsWith("-SNAPSHOT")) {
                    messages.add("SNAPSHOT 버전은 지원하지 않아 건너뜁니다: " + d.key() + ":" + version);
                    continue;
                }
                if (!isSafe(d.group(), d.artifact(), version) || (d.classifier() != null && !isSafe(d.classifier()))) {
                    messages.add("올바르지 않은 좌표라 건너뜁니다: " + d.key() + ":" + version);
                    continue;
                }

                Coord coord = new Coord(d.group(), d.artifact(), version);
                chosen.put(d.key(), coord);

                Pom pom = null;
                try {
                    pom = loadRemote(coord, offline, log, 0);
                } catch (IOException e) {
                    messages.add("pom을 읽지 못했습니다 (" + coord + "): " + e.getMessage());
                }

                boolean pomOnly = pom != null && "pom".equals(pom.packaging);
                if (!pomOnly) {
                    try {
                        jars.add(fetchFile(relPath(coord, d.classifier(), "jar"), offline, log, MAX_JAR_BYTES));
                    } catch (IOException e) {
                        messages.add("jar를 준비하지 못했습니다 (" + coord + "): " + e.getMessage());
                    }
                }

                if (pom == null) continue;
                Map<String, String> pomManaged = managedVersions(pom, offline, log);
                for (Dep raw : effectiveDeps(pom)) {
                    String scope = raw.scope() == null ? "compile" : raw.scope();
                    if (!(scope.equals("compile") || scope.equals("runtime"))) continue;
                    if (raw.optional() || !"jar".equals(raw.type())) continue;
                    String v = raw.version() != null ? raw.version() : pomManaged.get(raw.key());
                    Set<String> ex = new HashSet<>(n.exclusions());
                    ex.addAll(raw.exclusions());
                    queue.add(new Pending(new Dep(raw.group(), raw.artifact(), v, raw.scope(), false, raw.type(),
                            raw.classifier(), raw.exclusions()), n.depth() + 1, ex));
                }
            }
            return new Result(jars, new ArrayList<>(chosen.values()), messages);
        }

        private static boolean isExcluded(Set<String> exclusions, Dep d) {
            return exclusions.contains(d.key()) || exclusions.contains(d.group() + ":*")
                    || exclusions.contains("*:" + d.artifact()) || exclusions.contains("*:*");
        }

        // =============================================================
        // 버전 목록, 검색
        // =============================================================

        /** 라이브러리의 버전들 (최신이 먼저) */
        List<String> versions(String group, String artifact) throws IOException {
            if (!isSafe(group, artifact)) throw new IOException("올바르지 않은 좌표입니다: " + group + ":" + artifact);
            byte[] data = fetcher.fetch(repoBase + group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml",
                    MAX_POM_BYTES);
            Element root = parseXml(data).getDocumentElement();
            List<String> versions = new ArrayList<>();
            for (Element v : children(child(child(root, "versioning"), "versions"), "version")) {
                String t = v.getTextContent().trim();
                if (SAFE.matcher(t).matches()) versions.add(t);
            }
            java.util.Collections.reverse(versions);
            return versions;
        }

        /** Maven Central에서 라이브러리를 검색한다 */
        List<SearchHit> search(String query) throws IOException {
            String url = searchUrl + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&rows=25&wt=json";
            String json = new String(fetcher.fetch(url, MAX_POM_BYTES), StandardCharsets.UTF_8);
            Object root;
            try {
                root = MiniJson.parse(json);
            } catch (IllegalArgumentException e) {
                throw new IOException("검색 결과를 읽을 수 없습니다: " + e.getMessage(), e);
            }
            List<SearchHit> hits = new ArrayList<>();
            Object docs = MiniJson.get(root, "response", "docs");
            if (docs instanceof List<?> list) {
                for (Object doc : list) {
                    Object g = MiniJson.get(doc, "g");
                    Object a = MiniJson.get(doc, "a");
                    Object v = MiniJson.get(doc, "latestVersion");
                    if (g instanceof String gs && a instanceof String as && isSafe(gs, as)) {
                        hits.add(new SearchHit(gs, as, v instanceof String vs ? vs : "?"));
                    }
                }
            }
            return hits;
        }
    }

    /** pom.xml 글을 고치는 도구 (원래 글의 줄바꿈과 들여쓰기를 최대한 지킨다) */
    static class PomEditor {

        /** 새 프로젝트용 pom.xml */
        static String newPom(String folderName) {
            String artifact = folderName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.\\-]+", "-")
                    .replaceAll("^-+|-+$", "");
            if (artifact.isEmpty()) artifact = "app";
            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>

                        <groupId>com.example</groupId>
                        <artifactId>%s</artifactId>
                        <version>1.0-SNAPSHOT</version>

                        <properties>
                            <maven.compiler.release>21</maven.compiler.release>
                            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                        </properties>

                        <dependencies>
                        </dependencies>
                    </project>
                    """.formatted(artifact);
        }

        /** <dependency> 블록 글 (마지막 줄바꿈 포함). indent는 <dependency>의 들여쓰기. */
        static String dependencyXml(Coord c, String indent) {
            String in2 = indent + "    ";
            return indent + "<dependency>\n"
                    + in2 + "<groupId>" + c.group() + "</groupId>\n"
                    + in2 + "<artifactId>" + c.artifact() + "</artifactId>\n"
                    + in2 + "<version>" + c.version() + "</version>\n"
                    + indent + "</dependency>\n";
        }

        /** 주석과 CDATA 안의 글을 공백으로 가려서 (글자 위치는 그대로) 태그 검색이 속지 않게 한다 */
        private static String maskComments(String s) {
            StringBuilder sb = new StringBuilder(s);
            Matcher m = Pattern.compile("<!--.*?-->|<!\\[CDATA\\[.*?]]>", Pattern.DOTALL).matcher(s);
            while (m.find()) {
                for (int i = m.start(); i < m.end(); i++) {
                    char c = sb.charAt(i);
                    if (c != '\n' && c != '\r') sb.setCharAt(i, ' ');
                }
            }
            return sb.toString();
        }

        /**
         * 프로젝트의 <dependencies>에 의존성을 추가한 새 pom.xml 글을 돌려준다.
         * @throws IllegalStateException    이미 있는 라이브러리일 때
         * @throws IllegalArgumentException pom.xml이 올바르지 않을 때
         */
        static String addDependency(String pom, Coord c) {
            if (c.version() == null) throw new IllegalArgumentException("버전이 필요합니다");

            // 1) 올바른 pom인지, 이미 있는지 확인
            try {
                Element root = MavenResolver.parseXml(pom.getBytes(StandardCharsets.UTF_8)).getDocumentElement();
                if (!"project".equals(root.getTagName())) throw new IllegalArgumentException("<project>가 없는 pom.xml입니다");
                for (Element d : MavenResolver.children(MavenResolver.child(root, "dependencies"), "dependency")) {
                    if (c.group().equals(MavenResolver.text(d, "groupId"))
                            && c.artifact().equals(MavenResolver.text(d, "artifactId"))) {
                        String v = MavenResolver.text(d, "version");
                        throw new IllegalStateException(c.version().equals(v)
                                ? "이미 추가되어 있습니다: " + c
                                : "이미 다른 버전(" + v + ")이 있습니다. pom.xml에서 직접 바꿔 주세요: " + c.key());
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("pom.xml이 올바른 XML이 아닙니다: " + e.getMessage());
            }

            // 2) 프로젝트 바로 아래의 <dependencies> 위치를 찾는다
            String masked = maskComments(pom);
            Matcher m = Pattern.compile("<(/?)([A-Za-z_][\\w.:-]*)([^<>]*?)(/?)>").matcher(masked);
            List<String> stack = new ArrayList<>();
            int depsClose = -1;
            int projectClose = -1;
            int emptyDepsStart = -1;
            int emptyDepsEnd = -1;
            while (m.find()) {
                boolean closing = !m.group(1).isEmpty();
                boolean selfClosing = !m.group(4).isEmpty();
                String name = m.group(2);
                if (closing) {
                    if (name.equals("dependencies") && stack.equals(List.of("project", "dependencies"))) depsClose = m.start();
                    if (name.equals("project") && stack.equals(List.of("project"))) projectClose = m.start();
                    if (!stack.isEmpty()) stack.remove(stack.size() - 1);
                } else if (selfClosing) {
                    if (name.equals("dependencies") && stack.equals(List.of("project"))) {
                        emptyDepsStart = m.start();
                        emptyDepsEnd = m.end();
                    }
                } else {
                    stack.add(name);
                }
            }

            String nl = pom.contains("\r\n") ? "\r\n" : "\n";
            String result;
            if (depsClose >= 0) {
                int lineStart = pom.lastIndexOf('\n', depsClose - 1) + 1;
                String lead = pom.substring(lineStart, depsClose);
                if (lead.isBlank()) {
                    String unit = lead.contains("\t") ? "\t" : "    ";
                    String block = dependencyXml(c, lead + unit).replace("\n", nl);
                    result = pom.substring(0, lineStart) + block + pom.substring(lineStart);
                } else {
                    String block = dependencyXml(c, "").replace("\n", nl);
                    result = pom.substring(0, depsClose) + nl + block + pom.substring(depsClose);
                }
            } else if (emptyDepsStart >= 0) {
                int ls = pom.lastIndexOf('\n', emptyDepsStart - 1) + 1;
                String lead = pom.substring(ls, emptyDepsStart);
                String ind = lead.isBlank() ? lead : "";
                String unit = ind.contains("\t") ? "\t" : "    ";
                String block = ("<dependencies>\n" + dependencyXml(c, ind + unit) + ind + "</dependencies>").replace("\n", nl);
                result = pom.substring(0, emptyDepsStart) + block + pom.substring(emptyDepsEnd);
            } else if (projectClose >= 0) {
                int lineStart = pom.lastIndexOf('\n', projectClose - 1) + 1;
                String indent = detectChildIndent(pom);
                String block = (indent + "<dependencies>\n" + dependencyXml(c, indent + "    ")
                        + indent + "</dependencies>\n").replace("\n", nl);
                if (pom.substring(lineStart, projectClose).isBlank()) {
                    result = pom.substring(0, lineStart) + block + pom.substring(lineStart);
                } else {
                    result = pom.substring(0, projectClose) + nl + block + pom.substring(projectClose);
                }
            } else {
                throw new IllegalArgumentException("pom.xml에서 <project>의 끝을 찾을 수 없습니다");
            }

            // 3) 고친 결과가 올바른 XML이고 의존성이 들어갔는지 확인한다 (아니면 파일을 건드리지 않는다)
            try {
                Element root = MavenResolver.parseXml(result.getBytes(StandardCharsets.UTF_8)).getDocumentElement();
                boolean found = false;
                for (Element d : MavenResolver.children(MavenResolver.child(root, "dependencies"), "dependency")) {
                    if (c.artifact().equals(MavenResolver.text(d, "artifactId"))) found = true;
                }
                if (!found) throw new IllegalStateException("pom.xml을 자동으로 고치지 못했습니다. 직접 추가해 주세요:\n\n" + dependencyXml(c, "    "));
            } catch (IOException e) {
                throw new IllegalStateException("pom.xml을 자동으로 고치지 못했습니다. 직접 추가해 주세요:\n\n" + dependencyXml(c, "    "));
            }
            return result;
        }

        /** <project> 바로 아래 항목들이 쓰는 들여쓰기 (모르면 공백 4칸) */
        private static String detectChildIndent(String pom) {
            Matcher m = Pattern.compile("<project[^>]*>\\s*?\\r?\\n([ \\t]+)<").matcher(maskComments(pom));
            return m.find() ? m.group(1) : "    ";
        }
    }
}
