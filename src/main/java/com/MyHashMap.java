package com;

import java.util.Objects;

/**
 * 구현 순서: hash → indexFor → put → get → resize → remove
 */
public class MyHashMap<K, V> {

  static final int DEFAULT_CAPACITY = 16;      // 반드시 2의 거듭제곱
  static final float LOAD_FACTOR = 0.75f;
  static final int MAX_CAPACITY = 1 << 30;

  // 버킷 안의 연결 리스트 노드
  static class Node<K, V> {
    final int hash;   // 매번 다시 계산하지 않도록 저장 (resize 때 재사용)
    final K key;
    V value;
    Node<K, V> next;

    Node(int hash, K key, V value, Node<K, V> next) {
      this.hash = hash;
      this.key = key;
      this.value = value;
      this.next = next;
    }
  }

  private Node<K, V>[] table;
  private int size;
  private int threshold;      // size가 이 값을 넘으면 resize
  private int resizeCount;    // 실험용: 리사이즈 횟수 기록

  public MyHashMap() {
    this(DEFAULT_CAPACITY);
  }

  @SuppressWarnings("unchecked")
  public MyHashMap(int initialCapacity) {
    int cap = tableSizeFor(initialCapacity);
    table = (Node<K, V>[]) new Node[cap];
    threshold = (int) (cap * LOAD_FACTOR);
  }

  /** initialCapacity 이상인 가장 작은 2의 거듭제곱 (예: 20 → 32) */
  static int tableSizeFor(int c) {
    int n = 1;
    while (n < c && n < MAX_CAPACITY) n <<= 1;
    return n;
  }

  /**
   * 1: 해시값 계산
   * - key가 null이면 0
   * - 아니면 h = key.hashCode(); return h ^ (h >>> 16);
   * 왜 섞을까? indexFor는 하위 비트만 쓰기 때문에, 상위 비트 정보도 하위로 내려서 충돌을 줄이려는 것
   */
  static int hash(Object key) {
    if(key != null){
      int h = key.hashCode();
      return h ^ (h >>> 16);
    }
    return 0;
  }

  /**
   * 2: 버킷 인덱스 계산 - 해시값을 배열 인덱스(0 ~ length-1)로 변환
   * - hash & (length - 1)
   * length가 2의 거듭제곱이면 hash % length와 같은 결과지만 더 빠르고, 음수 hash도 안전]
   * 용량을 항상 2의 거듭제곱으로 유지하는 조건하에 사용
   */
  static int indexFor(int hash, int length) {
    // [리뷰] 정답. 수정 없음
    return hash & (length - 1);
  }

  /**
   * 3: 저장
   */
  public V put(K key, V value) {
    int hash = hash(key);
    int index = indexFor(hash, table.length);

    for(Node<K, V> node = table[index]; node != null; node = node.next) {
      if(node.hash==hash && Objects.equals(node.key, key)){
        V oldValue = node.value;
        node.value = value; // value 변경
        return oldValue; // 이전 value 리턴
      }
    }
    table[index] = new Node<>(hash, key, value, table[index]);
    size++;
    if(size > threshold) resize();
    return null;
  }

  /**
   * 4: 조회
   */
  public V get(Object key) {
    int hash = hash(key);
    int index = indexFor(hash, table.length);
    for(Node<K, V> node = table[index]; node != null; node = node.next) {
      if(node.hash==hash && Objects.equals(node.key, key)) return node.value; //node 찾으면 value 반환
    }
    return null;
  }

  public boolean containsKey(Object key) {
    int hash = hash(key);
    int index = indexFor(hash, table.length);
    for(Node<K, V> node = table[index]; node != null; node = node.next) {
      if(node.hash==hash && Objects.equals(node.key, key)) return true; // node 찾으면 true 반환
    }
    return false; // node 못찾을땐 false
  }

  /**
   * 5: 리사이즈
   */
  @SuppressWarnings("unchecked")
  private void resize() {
    Node<K, V>[] oldTable = table;
    int oldCap = oldTable.length;

    if (oldCap >= MAX_CAPACITY) {
      threshold = Integer.MAX_VALUE; //threshold를 int 최댓값으로 올려 리사이즈 조건 안걸리게해 불필요한 호출 방지
      return;
    }
    int newCap = oldCap << 1;

    table = (Node<K, V>[]) new Node[newCap];
    threshold = (int) (newCap * LOAD_FACTOR);
    resizeCount++;
    // next를 먼저 저장 후 옮기기
    for (Node<K, V> node : oldTable) {
      if (node == null) continue;
      Node<K, V> head = node;
      while (head != null) {
        Node<K, V> next = head.next;
        int index = indexFor(head.hash, newCap);
        head.next = table[index];
        table[index] = head;
        head = next;
      }
    }
  }

  /**
   * TODO 6: 삭제
   */
  public V remove(Object key) {
    int hash = hash(key);
    int index = indexFor(hash, table.length);

    Node<K, V> prev = null, node = table[index];

    while (node != null) {
      Node<K, V> next = node.next;
      if (node.hash == hash && Objects.equals(node.key, key)) {
        size--;
        if (prev == null) table[index] = next;
        else prev.next = next;
        return node.value;
      }
      prev = node;
      node = next;
    }
    return null;
  }

  public int size() { return size; }
  public int getResizeCount() { return resizeCount; }
  public int capacity() { return table.length; }

  /** 실험용: 가장 긴 체인 길이 (충돌이 얼마나 몰렸는지 확인) */
  public int maxChainLength() {
    int max = 0;
    for (Node<K, V> head : table) {
      int len = 0;
      for (Node<K, V> n = head; n != null; n = n.next) len++;
      max = Math.max(max, len);
    }
    return max;
  }
}
