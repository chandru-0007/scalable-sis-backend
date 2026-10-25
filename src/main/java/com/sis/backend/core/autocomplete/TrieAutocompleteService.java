package com.sis.backend.core.autocomplete;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory Prefix Trie for sub-millisecond Autocomplete suggestions.
 * Indexes course codes and titles for instant course search.
 */
@Service
public class TrieAutocompleteService {

    private final TrieNode root = new TrieNode();
    private final AtomicInteger totalWords = new AtomicInteger(0);

    public static class CourseSuggestion {
        private String code;
        private String title;

        public CourseSuggestion(String code, String title) {
            this.code = code;
            this.title = title;
        }

        public String getCode() {
            return code;
        }

        public String getTitle() {
            return title;
        }
    }

    private static class TrieNode {
        Map<Character, TrieNode> children = new ConcurrentHashMap<>();
        boolean isEndOfWord = false;
        Set<CourseSuggestion> suggestions = Collections.synchronizedSet(new LinkedHashSet<>());
    }

    /**
     * Index a course phrase (both code and title tokens).
     */
    public void insert(String keyword, String code, String title) {
        if (keyword == null || keyword.isBlank()) return;

        String normalized = keyword.trim().toLowerCase();
        TrieNode current = root;

        CourseSuggestion suggestion = new CourseSuggestion(code, title);

        for (char c : normalized.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new TrieNode());
            current.suggestions.add(suggestion);
        }

        current.isEndOfWord = true;
        totalWords.incrementAndGet();
    }

    /**
     * Search prefix matching suggestions.
     */
    public List<CourseSuggestion> search(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }

        String normalized = prefix.trim().toLowerCase();
        TrieNode current = root;

        for (char c : normalized.toCharArray()) {
            current = current.children.get(c);
            if (current == null) {
                return Collections.emptyList();
            }
        }

        List<CourseSuggestion> results = new ArrayList<>(current.suggestions);
        if (results.size() > limit) {
            return results.subList(0, limit);
        }
        return results;
    }

    public int getTotalIndexedTerms() {
        return totalWords.get();
    }
}
