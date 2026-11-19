package com.swapnil.cache.policy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A Least-Recently-Used eviction policy with O(1) insert, access, remove,
 * and victim selection.
 *
 * <p><b>Implementation:</b> a doubly linked list with sentinel head and tail
 * nodes tracks recency order (head = most recently used, tail = least
 * recently used). A {@code HashMap<K, Node<K>>} provides O(1) node lookup
 * by key, so no operation ever requires a linear scan.</p>
 *
 * <p><b>Thread-safety:</b> this class is <b>not</b> internally synchronized.
 * It relies on the owning {@link com.swapnil.cache.Cache} to guard access
 * with its internal lock. Do not share a single instance across multiple
 * caches.</p>
 *
 * @param <K> key type
 */

public final class LRUEvictionPolicy<K> implements EvictionPolicy<K> {
    private final Map<K, Node<K>> nodeMap = new HashMap<>();
    private final Node<K> head = new Node<>(null); //MRU sentinel
    private final Node<K> tail = new Node<>(null); //LRu sentinel
    
    public LRUEvictionPolicy(){
        head.next = tail;
        tail.prev = head;
    }

    @Override 
    public void onAccess(K key){
        Objects.requireNonNull(key,"key");
        Node<K> node = nodeMap.get(key);
        if(node == null){
            throw new IllegalStateException(
                "onAccess called for key not tracked by policy: "+key);
        }
        moveToHead(node);
    }

    @Override
    public void onInsert(K key){
        Objects.requireNonNull(key, "key");
        if(nodeMap.containsKey(key)){
            throw new IllegalStateException(
                "onInsert called for duplicate key: " + key
            );
        }

        Node<K> node = new Node<>(key);
        linkAfterHead(node);
        nodeMap.put(key, node);
    }
    
    @Override 
    public void onRemove(K key){
        Objects.requireNonNull(key, "key");
        Node<K> node = nodeMap.remove(key);
        if(node == null){
            throw new IllegalStateException(
                "onRemove called for key not tracked by policy: " + key
            );
        }

        unlink(node);
    }

    @Override 
    public K selectVictim(){
        Node<K> lru = tail.prev;
        return (lru == head) ? null : lru.key;
    }

    /**
     * Returns the number of keys currently tracked by this policy.
     *
     * <p><b>Package-private:</b> intended for testing and debugging only.
     * External callers should use {@link com.swapnil.cache.Cache#size()}.</p>
     */
    int size(){
        return nodeMap.size();
    }

    @Override 
    public String toString(){
        StringBuilder sb = new StringBuilder("LRUEvictionPolicy{MRU->");
        Node<K> n = head.next;
        while(n != tail){
            sb.append(n.key);
            if(n.next != tail){
                sb.append(" -> ");
            }
            n = n.next;
        }
        return sb.append("<-LRU, size=").append(nodeMap.size())
                  .append('}').toString();
    }

    // ------------------------------------------------------------------
    // Internal linked-list operations — all O(1)
    // ------------------------------------------------------------------

    private void moveToHead(Node<K> node){
        unlink(node);
        linkAfterHead(node);
    }

    private void linkAfterHead(Node<K> node){
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void unlink(Node<K> node){
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }

    /**
     * Internal doubly linked list node.
     *
     * <p>Nested private — nothing outside {@code LRUEvictionPolicy} needs
     * access to nodes. Keeping the node type colocated with the policy
     * keeps the {@code internal} package purely for cache-side concerns.</p>
     */

    private static final class Node<K>{
        public K key;
        Node<K> prev;
        Node<K> next;

        Node(K key){
            this.key = key;
        }
    }
}