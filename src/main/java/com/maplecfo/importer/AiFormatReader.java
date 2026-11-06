package com.maplecfo.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maplecfo.agent.PromptBuilder;
import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.domain.Transaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * F16 (new in Stage 2): when no saved bank format fits a CSV file, the AI reads its first lines and proposes
 * how to read the columns. The AI only proposes: Java turns the answer into a {@link ColumnMapping} (rejecting
 * anything invalid), test-reads the whole file with it, and the user sees a preview before the format is saved.
 */
public class AiFormatReader {

    static final int SAMPLE_LINES = 8;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** What the AI proposed, and what happened when the whole file was read with it. */
    public record Proposal(ColumnMapping mapping, String explanation, List<Transaction> preview, int rowsRead,
                           List<String> rowsRejected) {

        public Proposal {
            preview = List.copyOf(preview);
            rowsRejected = List.copyOf(rowsRejected);
        }

        /** At least one row was read and at most one row in five was rejected. */
        public boolean looksRight() {
            return rowsRead > 0 && rowsRejected.size() * 4 <= rowsRead;
        }

        /** The same proposal with the format saved under another name. */
        public Proposal named(String name) {
            return new Proposal(mapping.withName(name), explanation, preview, rowsRead, rowsRejected);
        }

        public String describeColumns() {
            ColumnMapping m = mapping;
            String amount = m.usesSingleAmount()
                    ? "amount from '" + m.amountColumn() + "'" + (m.negateAmounts() ? " (sign flipped)" : "")
                    : "money out from '" + m.debitColumn() + "', money in from '" + m.creditColumn() + "'";
            return "date from '" + m.dateColumn() + "' (" + m.dateFormat() + "), description from '"
                    + m.descriptionColumn() + "', " + amount
                    + (m.balanceColumn() != null ? ", balance from '" + m.balanceColumn() + "'" : "")
                    + (m.hasHeader() ? "" : " (no header row)");
        }
    }

    private final LlmClient llm;
    private final PromptBuilder prompts;

    public AiFormatReader(LlmClient llm, PromptBuilder prompts) {
        this.llm = llm;
        this.prompts = prompts;
    }

    /**
     * @param formatName name to save the new format under, e.g. "Tangerine"
     * @throws IllegalStateException    in demo mode (no AI model)
     * @throws IllegalArgumentException when the AI's answer cannot be used for this file
     */
    public Proposal propose(Path file, String formatName) throws IOException {
        if (llm.isMock()) {
            throw new IllegalStateException("Reading a new bank format needs the AI model. Add your API key to .env, "
                    + "or choose the format yourself.");
        }
        List<String> lines = Files.readAllLines(file).stream().filter(l -> !l.isBlank()).limit(SAMPLE_LINES).toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        String reply = llm.chat(List.of(ChatMessage.system(prompts.buildFormatSystemPrompt()),
                ChatMessage.user(prompts.buildFormatPrompt(lines))), List.of()).text();
        JsonNode j = parseJson(reply);
        ColumnMapping mapping;
        try {
            mapping = new ColumnMapping(formatName, j.path("hasHeader").asBoolean(true), text(j, "dateColumn"),
                    text(j, "dateFormat"), text(j, "descriptionColumn"), text(j, "amountColumn"),
                    text(j, "debitColumn"), text(j, "creditColumn"), j.path("negateAmounts").asBoolean(false),
                    text(j, "balanceColumn"));
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new IllegalArgumentException("The AI's suggestion is incomplete (" + e.getMessage()
                    + "). Choose the format yourself.");
        }
        CsvTransactionAdapter reader = new CsvTransactionAdapter(mapping);
        List<Transaction> txs;
        try {
            txs = reader.importFile(file, "preview");
        } catch (MappingMismatchException e) {
            throw new IllegalArgumentException("The AI named columns that are not in this file. Choose the format "
                    + "yourself.");
        }
        return new Proposal(mapping, j.path("explanation").asText(""), txs.subList(0, Math.min(5, txs.size())),
                txs.size(), reader.getRejectedRows());
    }

    /** The JSON object in the reply (the model sometimes wraps it in a code block). */
    static JsonNode parseJson(String reply) {
        int start = reply == null ? -1 : reply.indexOf('{');
        int end = reply == null ? -1 : reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("The AI did not return a format description. Choose the format yourself.");
        }
        try {
            return JSON.readTree(reply.substring(start, end + 1));
        } catch (IOException e) {
            throw new IllegalArgumentException("The AI's format description could not be read. Choose the format yourself.");
        }
    }

    private static String text(JsonNode j, String field) {
        JsonNode v = j.get(field);
        return v == null || v.isNull() || v.asText().isBlank() || v.asText().equalsIgnoreCase("null")
                ? null : v.asText().trim();
    }
}
