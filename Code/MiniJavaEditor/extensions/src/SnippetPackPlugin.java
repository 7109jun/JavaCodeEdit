import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 자바 스니펫 모음.
 * 코드를 치다가 이름(예: sc, list, tryres)을 두 글자 이상 입력하면 자동완성 목록에 나타나고,
 * 고르면 코드 조각이 펼쳐진다. 템플릿 안의 $0 은 펼친 뒤 커서가 놓일 자리다.
 * 자바 기본 패키지(java.lang) 밖의 클래스는 import 없이 쓸 수 있도록 전체 이름(java.util.Scanner 등)으로 적었다.
 */
public class SnippetPackPlugin implements MiniJavaEditor.Plugin {

    @Override
    public String name() {
        return "자바 스니펫 모음";
    }

    @Override
    public String description() {
        return "sc, list, map, tryres, readfile, rec, getset, stream 등 자주 쓰는 코드 조각 30여 개를 자동완성에 추가합니다";
    }

    /** 이름 -> {설명, 템플릿} */
    static Map<String, String[]> snippets() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("sc", new String[]{"Scanner (키보드 입력)", "java.util.Scanner sc = new java.util.Scanner(System.in);$0"});
        m.put("list", new String[]{"ArrayList 만들기", "java.util.List<String> list = new java.util.ArrayList<>();$0"});
        m.put("map", new String[]{"HashMap 만들기", "java.util.Map<String, Integer> map = new java.util.HashMap<>();$0"});
        m.put("set", new String[]{"HashSet 만들기", "java.util.Set<String> set = new java.util.HashSet<>();$0"});
        m.put("forr", new String[]{"거꾸로 반복문", "for (int i = n - 1; i >= 0; i--) {\n    $0\n}"});
        m.put("fore", new String[]{"Map 항목 반복문", "for (var entry : map.entrySet()) {\n    var key = entry.getKey();\n    var value = entry.getValue();\n    $0\n}"});
        m.put("while", new String[]{"while 반복문", "while ($0) {\n    // 반복할 내용\n}"});
        m.put("ife", new String[]{"if - else", "if ($0) {\n    // 참일 때\n} else {\n    // 거짓일 때\n}"});
        m.put("ifnull", new String[]{"null이면 끝내기", "if ($0 == null) {\n    return;\n}"});
        m.put("sw", new String[]{"switch (화살표 방식)", "switch ($0) {\n    case 1 -> System.out.println(\"하나\");\n    default -> System.out.println(\"그 외\");\n}"});
        m.put("tryres", new String[]{"파일을 한 줄씩 읽기 (try-with-resources)", "try (var reader = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(\"$0\"))) {\n    String line;\n    while ((line = reader.readLine()) != null) {\n        System.out.println(line);\n    }\n} catch (java.io.IOException e) {\n    e.printStackTrace();\n}"});
        m.put("readfile", new String[]{"파일 전체 읽기", "String text = java.nio.file.Files.readString(java.nio.file.Path.of(\"$0\"));"});
        m.put("writefile", new String[]{"파일에 쓰기", "java.nio.file.Files.writeString(java.nio.file.Path.of(\"$0\"), \"내용\");"});
        m.put("lam", new String[]{"람다식", "(x) -> $0"});
        m.put("rec", new String[]{"record", "record $0() { }"});
        m.put("enum", new String[]{"enum", "enum $0 {\n    A, B, C\n}"});
        m.put("iface", new String[]{"interface", "interface $0 {\n    void run();\n}"});
        m.put("cls", new String[]{"class", "public class $0 {\n}"});
        m.put("getset", new String[]{"필드 + getter + setter", "private int value;\n\npublic int getValue() {\n    return value;\n}\n\npublic void setValue(int value) {\n    this.value = value;\n}$0"});
        m.put("tostr", new String[]{"toString 재정의", "@Override\npublic String toString() {\n    return \"$0\";\n}"});
        m.put("eqh", new String[]{"equals + hashCode (value 필드 기준)", "@Override\npublic boolean equals(Object o) {\n    if (this == o) return true;\n    if (o == null || getClass() != o.getClass()) return false;\n    var other = getClass().cast(o);\n    return java.util.Objects.equals(value, other.value);\n}\n\n@Override\npublic int hashCode() {\n    return java.util.Objects.hash(value);\n}$0"});
        m.put("thread", new String[]{"새 스레드", "new Thread(() -> {\n    $0\n}).start();"});
        m.put("stream", new String[]{"스트림 filter + toList", "list.stream()\n        .filter(x -> $0)\n        .toList();"});
        m.put("opt", new String[]{"Optional", "java.util.Optional<String> opt = java.util.Optional.ofNullable($0);"});
        m.put("doc", new String[]{"Javadoc 주석", "/**\n * $0\n */"});
        m.put("todo", new String[]{"TODO 주석", "// TODO: $0"});
        m.put("printf", new String[]{"서식 출력", "System.out.printf(\"%d%n\", $0);"});
        m.put("sleep", new String[]{"잠깐 멈추기", "try {\n    Thread.sleep($0);\n} catch (InterruptedException e) {\n    Thread.currentThread().interrupt();\n}"});
        m.put("now", new String[]{"지금 시각", "java.time.LocalDateTime.now()$0"});
        m.put("rand", new String[]{"난수 (0 이상 n 미만)", "new java.util.Random().nextInt($0)"});
        m.put("test", new String[]{"JUnit 5 테스트", "@org.junit.jupiter.api.Test\nvoid $0() {\n}"});
        return m;
    }

    @Override
    public void onLoad(MiniJavaEditor.Context ctx) {
        snippets().forEach((name, v) -> ctx.addSnippet(name, v[0], v[1]));
    }
}
