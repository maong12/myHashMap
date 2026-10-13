package com;

import java.util.*;

/**
 * MyHashMap 검증 + 실험 코드
 * 실행: javac MyHashMap.java Experiment.java && java Experiment
 */
public class Experiment {

  public static void main(String[] args) {
    correctnessTest();
    collisionExperiment();
    //resizeExperiment();
    resizeExperiment2();
    normalizationExperiment();
  }

  // ───────────────────────────────────────────────
  // 0. 정확성 검증: java.util.HashMap과 무작위 연산 결과 비교
  // ───────────────────────────────────────────────
  static void correctnessTest() {
    MyHashMap<Integer, Integer> mine = new MyHashMap<>();
    Map<Integer, Integer> ref = new HashMap<>();
    Random r = new Random(42);

    for (int i = 0; i < 200_000; i++) {
      int key = r.nextInt(5_000);   // 키 범위를 좁혀 갱신/삭제가 자주 일어나게
      int op = r.nextInt(3);
      Object a, b;
      if (op == 0)      { a = mine.put(key, i); b = ref.put(key, i); }
      else if (op == 1) { a = mine.get(key);    b = ref.get(key); }
      else              { a = mine.remove(key); b = ref.remove(key); }

      if (!Objects.equals(a, b) || mine.size() != ref.size()) {
        throw new AssertionError("불일치 발생: i=" + i + ", op=" + op + ", key=" + key);
      }
    }
    // null 키도 확인
    mine.put(null, 1);
    if (!Objects.equals(mine.get(null), 1)) throw new AssertionError("null 키 실패");

    System.out.println("[0] 정확성 테스트 통과 (무작위 연산 200,000회)");
  }

  // ───────────────────────────────────────────────
  // 1. 충돌 실험: hashCode가 전부 같은 키 vs 정상 키
  // ───────────────────────────────────────────────
  static final class BadKey {
    final int id;
    BadKey(int id) { this.id = id; }
    @Override public int hashCode() { return 42; }   // 일부러 전부 같은 해시
    @Override public boolean equals(Object o) {
      return o instanceof BadKey && ((BadKey) o).id == id;
    }
  }

  static void collisionExperiment() {
    System.out.println("\n[1] 충돌 실험 (N개 저장 후 N번 조회 시간)");
    for (int n : new int[]{1_000, 5_000, 10_000, 20_000}) {
      MyHashMap<Integer, Integer> good = new MyHashMap<>();
      MyHashMap<BadKey, Integer> bad = new MyHashMap<>();
      for (int i = 0; i < n; i++) { good.put(i, i); bad.put(new BadKey(i), i); }

      long t1 = System.nanoTime();
      for (int i = 0; i < n; i++) good.get(i);
      long goodMs = System.nanoTime() - t1;

      long t2 = System.nanoTime();
      for (int i = 0; i < n; i++) bad.get(new BadKey(i));
      long badMs = System.nanoTime() - t2;

      System.out.printf("  N=%,6d | 정상 키: %8.2f ms (최대 체인 %d) | 충돌 키: %8.2f ms (최대 체인 %,d)%n",
          n, goodMs / 1e6, good.maxChainLength(), badMs / 1e6, bad.maxChainLength());
    }
    System.out.println("  → N이 2배일 때 충돌 키 조회 시간이 대략 4배로 늘면 O(n) 조회 × n번 = O(n^2)");
  }

  // ───────────────────────────────────────────────
  // 2. 리사이즈 실험: 29만 건 바코드형 문자열, 기본 용량 vs 초기 용량 지정
  // ───────────────────────────────────────────────
  static void resizeExperiment() {
    System.out.println("\n[2] 리사이즈 실험 (바코드형 문자열 290,000건)");
    int n = 290_000;
    List<String> barcodes = new ArrayList<>(n);
    Random r = new Random(7);
    for (int i = 0; i < n; i++) {
      barcodes.add(String.format("880%010d", Math.abs(r.nextLong()) % 10_000_000_000L));
    }

    // JIT 워밍업 (첫 실행이 느리게 나오는 것 방지)
    for (int w = 0; w < 3; w++) { insertAll(new MyHashMap<>(), barcodes); }

    long[] defaultTimes = new long[5], presizedTimes = new long[5];
    MyHashMap<String, Integer> lastDefault = null, lastPresized = null;
    for (int k = 0; k < 5; k++) {

      lastPresized = new MyHashMap<>((int) (n / 0.75f) + 1);
      presizedTimes[k] = insertAll(lastPresized, barcodes);

      lastDefault = new MyHashMap<>();
      defaultTimes[k] = insertAll(lastDefault, barcodes);

    }
    Arrays.sort(defaultTimes);
    Arrays.sort(presizedTimes);

    System.out.printf("  기본 용량(16)  : 중앙값 %6.2f ms | 리사이즈 %2d회 | 최종 용량 %,d | 최대 체인 %d%n",
        defaultTimes[2] / 1e6, lastDefault.getResizeCount(), lastDefault.capacity(), lastDefault.maxChainLength());
    System.out.printf("  초기 용량 지정 : 중앙값 %6.2f ms | 리사이즈 %2d회 | 최종 용량 %,d | 최대 체인 %d%n",
        presizedTimes[2] / 1e6, lastPresized.getResizeCount(), lastPresized.capacity(), lastPresized.maxChainLength());
  }

  // GC영향 빼고 측정
  static void resizeExperiment2() {
    System.out.println("\n[2] 리사이즈 실험 (바코드형 문자열 290,000건)");
    int n = 290_000;
    List<String> barcodes = new ArrayList<>(n);
    Random r = new Random(7);
    for (int i = 0; i < n; i++) {
      barcodes.add(String.format("880%010d", Math.abs(r.nextLong()) % 10_000_000_000L));
    }
    for (int w = 0; w < 3; w++) { insertAll(new MyHashMap<>(), barcodes); }

    long[] defaultTimes = new long[5], presizedTimes = new long[5];
    int defResize = 0, preResize = 0;

    // 초기 용량 지정만 5번
    for (int k = 0; k < 5; k++) {
      System.gc();
      MyHashMap<String, Integer> m = new MyHashMap<>((int) (n / 0.75f) + 1);
      presizedTimes[k] = insertAll(m, barcodes);
      preResize = m.getResizeCount();
    }

    // 기본 용량만 5번 (매번 이전 맵은 버려지고 GC 후 측정)
    for (int k = 0; k < 5; k++) {
      System.gc();
      MyHashMap<String, Integer> m = new MyHashMap<>();
      defaultTimes[k] = insertAll(m, barcodes);
      defResize = m.getResizeCount();
    }

    Arrays.sort(defaultTimes);
    Arrays.sort(presizedTimes);

    System.out.printf("  기본 용량(16)  : 중앙값 %6.2f ms | 리사이즈 %2d회%n", defaultTimes[2] / 1e6, defResize);
    System.out.printf("  초기 용량 지정 : 중앙값 %6.2f ms | 리사이즈 %2d회%n", presizedTimes[2] / 1e6, preResize);
  }

  static long insertAll(MyHashMap<String, Integer> map, List<String> keys) {
    long t = System.nanoTime();
    for (int i = 0; i < keys.size(); i++) map.put(keys.get(i), i);
    return System.nanoTime() - t;
  }

  // ───────────────────────────────────────────────
  // 3. 키 정규화 실험: 같은 상품, 다른 표기
  // ───────────────────────────────────────────────
  static void normalizationExperiment() {
    System.out.println("\n[3] 키 정규화 실험");
    String a = "8801234567890";     // 13자리
    String b = "08801234567890";    // 앞에 0이 붙은 14자리 (같은 상품)

    MyHashMap<String, String> raw = new MyHashMap<>();
    raw.put(a, "타이레놀");
    raw.put(b, "타이레놀");
    System.out.printf("  정규화 전: size=%d, hash(a)=%d, hash(b)=%d → 같은 상품이 다른 버킷에 2건 저장%n",
        raw.size(), MyHashMap.hash(a), MyHashMap.hash(b));

    MyHashMap<String, String> normalized = new MyHashMap<>();
    normalized.put(normalize(a), "타이레놀");
    normalized.put(normalize(b), "타이레놀");
    System.out.printf("  정규화 후: size=%d%n", normalized.size());
  }

  static String normalize(String barcode) {
    String s = barcode.trim();
    while (s.length() > 13 && s.startsWith("0")) s = s.substring(1);
    return s;
  }
}
