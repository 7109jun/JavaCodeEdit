Mini Java Editor - 실행용 묶음
==============================

들어 있는 것
  classes\            컴파일된 .class 파일 (Java 21용, 71개)
  src\MiniJavaEditor.java   소스 (파일 하나)
  run.bat             편집기 실행 (더블클릭). 콘솔 창이 뜨지 않습니다.
  run-debug.bat       오류 메시지를 볼 수 있는 실행 (실행이 안 될 때 이것으로 해 보세요)
  setup-jdk.bat       JDK 21을 이 폴더에 자동으로 내려받기 (아래 방법 A)
  run.sh              Linux / macOS용 실행

Java가 들어 있지 않은 이유
  이 묶음을 만든 환경의 Java는 Linux용이고 화면 기능도 빠져 있어서 Windows에서는 쓸 수 없습니다.
  그래서 Java 대신 "압축만 풀면 되는" 방법을 준비했습니다. 설치 프로그램은 필요 없습니다.

1단계. JDK 21 준비하기 (한 번만)
  편집기는 JRE가 아니라 JDK 21이 필요합니다 (오류 검사와 실행에 컴파일러를 씁니다).

  방법 A (자동): setup-jdk.bat 을 더블클릭
     Eclipse Temurin 21 (약 200MB)을 내려받아 이 폴더의 jdk 폴더에 풀어 줍니다.
     내려받은 파일은 공식 SHA-256 값과 비교해서 확인합니다.
     * 이 스크립트는 제가 직접 실행해 보지 못했습니다. 안 되면 방법 B를 쓰세요.

  방법 B (수동): https://adoptium.net 에서
     Temurin 21 (LTS) -> Windows x64 -> JDK -> .zip 을 받아서 압축을 풉니다.
     나오는 폴더(예: jdk-21.0.5+11)를 이 폴더(run.bat이 있는 곳)에 통째로 넣습니다.
     폴더 이름이 jdk 로 시작하면 자동으로 찾습니다.

  이미 JDK 21을 설치했다면 이 단계는 건너뛰어도 됩니다 (JAVA_HOME 또는 PATH에서 찾습니다).

2단계. run.bat 더블클릭
  폴더나 파일을 바로 열고 싶으면: run.bat C:\내폴더  (명령 프롬프트나 PowerShell에서)

기본 사용법
  파일 > 폴더 열기          왼쪽에 탐색기가 생깁니다 (더블클릭으로 파일 열기)
  파일 > 새 Java 클래스     폴더 구조에 맞는 package 줄과 함께 만듭니다
  Ctrl+S / Ctrl+Shift+S     저장 / 모두 저장        Ctrl+W 탭 닫기       Ctrl+Z / Ctrl+Y 실행 취소 / 다시 실행
  Ctrl+Space                자동완성                F6 오류 검사 (글을 멈추면 자동으로도 검사)
  F5                        프로젝트를 컴파일해서 실행   Shift+F5 중지      Ctrl+` 터미널 열기
  F7                        pom.xml의 라이브러리 내려받기 (라이브러리 > 라이브러리 추가로 검색해서 추가)
  확장 > 확장 프로그램 관리  jar 확장 프로그램 켜기/끄기, 개발 키트 만들기

기본 확장 프로그램 (extensions 폴더)
  처음 실행하면 "기본 확장 프로그램을 설치할까요?" 하고 한 번 물어봅니다. 나중에는 확장 > 기본 확장 프로그램 설치.
  - 자바 스니펫 모음   sc, list, map, tryres, readfile, rec, getset, stream 등 30여 개 (자동완성에 나타남)
  - 텍스트 도구        텍스트 메뉴: 줄 정렬, 중복 줄 제거, 공백/빈 줄 정리, 대소문자, 줄 번호, 글 통계
  - Java 서식 정리     코드 > 서식 정리 (들여쓰기 4칸, 줄 끝 공백, 빈 줄. 코드 글자는 바꾸지 않음)
  - 저장 백업          저장할 때마다 내용을 보관 (파일마다 최근 20개). 백업 메뉴에서 열기
  - 코드 도우미        코드 메뉴: 주석 켜기/끄기, 줄 복제/이동, import 정리, TODO 찾기
  소스는 extensions\src 에 있습니다. 고쳐서 나만의 확장 프로그램을 만들 수 있습니다.

문제가 생기면
  - run-debug.bat 으로 실행해서 나오는 메시지를 알려 주세요.
  - "UnsupportedClassVersionError" 가 나오면 Java 버전이 21보다 낮은 것입니다.
  - 오류 검사가 "JDK가 필요합니다" 라고 하면 JRE만 있는 것입니다. JDK 21을 준비하세요.
  - 설정을 처음 상태로 되돌리려면 사용자 폴더의 .minijava-editor 폴더를 지우세요
    (확장 프로그램, 확장 프로그램 설정, 내려받은 라이브러리가 들어 있습니다).

주의
  - 확장 프로그램은 컴퓨터에서 코드를 실행합니다. 직접 만들었거나 믿을 수 있는 것만 켜세요.
  - run.bat / run-debug.bat / setup-jdk 는 Windows에서 직접 실행해 보지 못했습니다.

소스에서 다시 컴파일하기 (JDK 필요)
  javac -encoding UTF-8 -d classes src\MiniJavaEditor.java
