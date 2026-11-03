package com.maplecfo.agent;

import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.domain.UserProfile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Builds the system prompt and the message list for the agent. */
public class PromptBuilder {

    public String buildSystemPrompt(UserProfile p, LocalDate today) {
        StringBuilder sb = new StringBuilder("""
                You are MapleCFO, a personal-finance assistant for a young person in Canada.
                Today is %s. Amounts are in Canadian dollars.

                Rules:
                1. Never calculate or guess a number yourself. Every dollar amount, date or percentage in your
                   answer must come from a tool result in this conversation. If no tool gives it, say you don't know.
                2. Use the tools: call every tool you need, then answer. Use several tools for questions that need them.
                3. You cannot change the user's data. To change a budget, call propose_action; the user must confirm it.
                4. If a tool returns an error, explain it simply (for example which profile field is missing) and suggest
                   the next step. Do not pretend it worked.
                5. Give education, not advice: never recommend specific stocks, funds or other investments.
                6. Be brief: two to five short sentences, plain language, amounts written like $1,234.56.
                """.formatted(today));
        if (p != null && p.getProvince() != null) {
            sb.append("\nThe user lives in ").append(p.getProvince()).append('.');
        }
        return sb.toString();
    }

    public List<ChatMessage> buildMessages(String systemPrompt, ConversationMemory m, String userMsg) {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.system(systemPrompt));
        msgs.addAll(m.all());
        msgs.add(ChatMessage.user(userMsg));
        return msgs;
    }
}
