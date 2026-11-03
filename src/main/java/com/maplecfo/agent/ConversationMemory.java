package com.maplecfo.agent;

import com.maplecfo.agent.llm.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * The agent's short-term memory: earlier questions and answers of this
 * session, so follow-ups like "and what about my RRSP?" make sense.
 * Only the last {@code maxTurns} question/answer pairs are kept.
 */
public class ConversationMemory {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final int maxTurns;

    public ConversationMemory(int maxTurns) {
        if (maxTurns < 1) throw new IllegalArgumentException("maxTurns must be at least 1");
        this.maxTurns = maxTurns;
    }

    public synchronized void add(ChatMessage m) {
        if (m.role() != ChatMessage.Role.USER && m.role() != ChatMessage.Role.ASSISTANT) {
            throw new IllegalArgumentException("Memory keeps only user and assistant text");
        }
        messages.add(m);
        compactOlderTurns();
    }

    /** The most recent messages, oldest first. */
    public synchronized List<ChatMessage> recent(int n) {
        return List.copyOf(messages.subList(Math.max(0, messages.size() - n), messages.size()));
    }

    public synchronized List<ChatMessage> all() {
        return List.copyOf(messages);
    }

    /** Drops the oldest turns beyond the limit, always starting the kept part at a user message. */
    public synchronized void compactOlderTurns() {
        while (messages.size() > maxTurns * 2) {
            messages.remove(0);
            while (!messages.isEmpty() && messages.get(0).role() != ChatMessage.Role.USER) {
                messages.remove(0);
            }
        }
    }

    public synchronized void clear() {
        messages.clear();
    }

    public synchronized int size() {
        return messages.size();
    }
}
