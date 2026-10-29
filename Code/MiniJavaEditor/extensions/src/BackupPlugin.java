import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.JOptionPane;

/**
 * 저장 백업 (백업 메뉴).
 * 파일을 저장할 때마다 그 시점의 내용을 보관한다 (파일마다 최근 20개, 내용이 같으면 새로 만들지 않는다).
 * 보관 위치: 사용자 폴더의 .minijava-editor/backups (시스템 속성 minijava.backup.dir 로 바꿀 수 있다)
 */
public class BackupPlugin implements MiniJavaEditor.Plugin {

    static final int KEEP = 20;
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private MiniJavaEditor.Context ctx;

    @Override
    public String name() {
        return "저장 백업";
    }

    @Override
    public String description() {
        return "저장할 때마다 그 시점의 내용을 보관합니다 (파일마다 최근 20개). 백업 메뉴에서 이전 내용을 열 수 있습니다";
    }

    @Override
    public void onLoad(MiniJavaEditor.Context context) {
        this.ctx = context;
        ctx.onFileSaved(BackupPlugin::backup);
        ctx.addMenuItem("백업", "이 파일의 백업 목록...", this::pick);
        ctx.addMenuItem("백업", "백업 폴더 열기", this::openFolder);
    }

    static Path backupRoot() {
        String p = System.getProperty("minijava.backup.dir");
        return p != null && !p.isBlank() ? Path.of(p) : Path.of(System.getProperty("user.home"), ".minijava-editor", "backups");
    }

    /** 파일마다 따로 쓰는 폴더: 이름 + 경로의 해시 (같은 이름의 다른 파일이 섞이지 않게) */
    static Path dirFor(File file) {
        try {
            String abs = file.getAbsoluteFile().toPath().normalize().toString();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(abs.getBytes(StandardCharsets.UTF_8));
            String safeName = file.getName().replaceAll("[^A-Za-z0-9가-힣._-]", "_");
            return backupRoot().resolve(safeName + "-" + HexFormat.of().formatHex(digest, 0, 6));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 백업 파일들 (최신이 먼저) */
    static List<Path> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        }
    }

    /** 파일이 저장된 직후에 불린다. 실패해도 저장을 방해하지 않는다. */
    static void backup(File saved) {
        try {
            if (!saved.isFile() || saved.length() > MAX_BYTES) return;
            Path root = backupRoot().toAbsolutePath().normalize();
            if (saved.getAbsoluteFile().toPath().normalize().startsWith(root)) return; // 백업 파일 자체는 백업하지 않는다

            Path dir = dirFor(saved);
            Files.createDirectories(dir);
            List<Path> existing = list(dir);
            if (!existing.isEmpty() && Files.mismatch(existing.get(0), saved.toPath()) == -1) return; // 내용이 같다

            Path target = dir.resolve(LocalDateTime.now().format(STAMP) + "-" + saved.getName());
            Files.copy(saved.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

            List<Path> all = list(dir);
            for (int i = KEEP; i < all.size(); i++) Files.deleteIfExists(all.get(i)); // 오래된 것부터 지운다
        } catch (IOException | RuntimeException e) {
            // 백업이 안 되어도 편집은 계속된다
        }
    }

    /** "20261024-101500-123-Foo.java" -> "2026-10-24 10:15:00" 처럼 보기 좋게 */
    static String describe(Path backup) {
        String n = backup.getFileName().toString();
        try {
            String d = n.substring(0, 8);
            String t = n.substring(9, 15);
            long size = Files.size(backup);
            return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8) + " "
                    + t.substring(0, 2) + ":" + t.substring(2, 4) + ":" + t.substring(4, 6)
                    + "  (" + (size < 1024 ? size + "B" : (size / 1024) + "KB") + ")";
        } catch (RuntimeException | IOException e) {
            return n;
        }
    }

    private void pick() {
        File f = ctx.currentFile();
        if (f == null) {
            ctx.showMessage("먼저 파일로 저장해 주세요. 저장하면 백업이 시작됩니다.");
            return;
        }
        try {
            List<Path> list = list(dirFor(f));
            if (list.isEmpty()) {
                ctx.showMessage("이 파일의 백업이 아직 없습니다.");
                return;
            }
            String[] options = list.stream().map(BackupPlugin::describe).toArray(String[]::new);
            Object choice = JOptionPane.showInputDialog(ctx.frame(), "열 백업을 고르세요 (최신순):", "백업 목록",
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == null) return;
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(choice)) {
                    ctx.openFile(list.get(i).toFile());
                    return;
                }
            }
        } catch (IOException e) {
            ctx.showMessage("백업 목록을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    private void openFolder() {
        Path dir = backupRoot();
        try {
            Files.createDirectories(dir);
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(dir.toFile());
            else ctx.showMessage("백업 폴더: " + dir);
        } catch (IOException | UnsupportedOperationException e) {
            ctx.showMessage("백업 폴더: " + dir);
        }
    }
}
