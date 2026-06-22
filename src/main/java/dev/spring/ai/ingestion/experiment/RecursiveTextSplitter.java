package dev.spring.ai.ingestion.experiment;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a text document into chunks suitable for vector embedding.
 *
 * <h3>Why recursive splitting?</h3>
 * A fixed-size splitter cuts text blindly at a character limit, which can break
 * sentences mid-way and destroy semantic context. A recursive splitter instead
 * tries to split at natural language boundaries first (paragraphs, lines,
 * sentences) and only falls back to splitting on spaces when no better boundary
 * exists within the chunk limit.
 *
 * <h3>Three-phase pipeline</h3>
 * <ol>
 *   <li><b>Split</b> — recursively divide the text using progressively finer
 *       separators until every piece fits within {@value #CEILING_TOKENS} tokens.</li>
 *   <li><b>Merge</b> — greedily combine adjacent small pieces back together
 *       so that chunks are as large as possible without exceeding the ceiling.
 *       This avoids producing many tiny single-sentence chunks.</li>
 *   <li><b>Overlap</b> — prepend the last {@value #OVERLAP_TOKENS} tokens of
 *       chunk N to the beginning of chunk N+1. This ensures that a sentence
 *       spanning a chunk boundary is present in both chunks, so neither chunk
 *       loses context at retrieval time.</li>
 * </ol>
 *
 * <h3>Token estimation</h3>
 * Token count is approximated as {@code wordCount × 1.3}, which is a reliable
 * rule of thumb for English technical text (one word ≈ 1.3 tokens in most
 * LLM tokenizers). No external tokenizer library is required.
 */
@Component
public class RecursiveTextSplitter
{
	/**
	 * Separators tried in order from coarsest to finest.
	 * The splitter moves to the next separator only when the current one does
	 * not produce pieces small enough to fit within {@link #CEILING_TOKENS}.
	 */
	private static final List<String> SEPARATORS = List.of("\n\n", "\n", ". ", " ");

	/** Maximum estimated token count allowed per chunk after merging. */
	private static final int CEILING_TOKENS = 300;

	/**
	 * Number of tokens from the end of chunk N that are prepended to chunk N+1.
	 * Overlap preserves cross-boundary context for the embedding model.
	 */
	private static final int OVERLAP_TOKENS = 50;

	/**
	 * Entry point: splits the input text into overlapping chunks.
	 *
	 * @param text the raw text to split (may be a full page or a body zone)
	 * @return list of text chunks ready to be wrapped in {@link org.springframework.ai.document.Document}
	 */
	public List<String> split(String text)
	{
		// Phase 1: recursive splitting into atomic pieces ≤ CEILING_TOKENS each
		List<String> chunks = splitRecursively(text.trim(), 0);

		// Phase 2: greedy merge — combine adjacent small pieces up to CEILING_TOKENS
		List<String> merged = mergeSmallChunks(chunks);

		// Phase 3: overlap — prepend tail of previous chunk to maintain context
		return applyOverlap(merged);
	}

	// ── Phase 1: Recursive splitting ─────────────────────────────────────────

	/**
	 * Recursively splits {@code text} using the separator at {@code separatorIndex}.
	 *
	 * <p>Base cases:
	 * <ul>
	 *   <li>Text is blank — return empty list.</li>
	 *   <li>Text already fits within the ceiling — return it as-is.</li>
	 *   <li>All separators exhausted — return the text as-is (can't split further).</li>
	 * </ul>
	 *
	 * <p>If the current separator splits the text into pieces, each piece is
	 * checked: pieces within the ceiling are kept; oversized pieces are
	 * recursively split with the next (finer) separator.
	 */
	private List<String> splitRecursively(String text, int separatorIndex)
	{
		if (text.isBlank())
			return List.of();

		// Text fits within the ceiling or no more separators to try — keep as-is
		if (estimateTokens(text) <= CEILING_TOKENS || separatorIndex >= SEPARATORS.size())
			return List.of(text.trim());

		String   separator = SEPARATORS.get(separatorIndex);
		String[] parts     = text.split(Pattern.quote(separator), -1);

		// Current separator not found in text — fall through to the next finer one
		if (parts.length == 1)
			return splitRecursively(text, separatorIndex + 1);

		List<String> result = new ArrayList<>();
		for (String part : parts)
		{
			if (part.isBlank()) continue;
			part = part.trim();

			if (estimateTokens(part) <= CEILING_TOKENS)
				result.add(part);           // piece fits — keep it
			else
				result.addAll(splitRecursively(part, separatorIndex + 1)); // too big — split further
		}
		return result;
	}

	// ── Phase 2: Greedy merge ─────────────────────────────────────────────────

	/**
	 * Combines adjacent small chunks into larger ones without exceeding the ceiling.
	 *
	 * <p>After recursive splitting, many chunks will be much smaller than the
	 * ceiling (e.g. a single sentence). Merging them back up reduces the total
	 * number of vectors and ensures each chunk carries enough context for the
	 * embedding model to work well.
	 *
	 * <p>Chunks are joined with {@code "\n\n"} to preserve a paragraph-like
	 * separation in the merged text.
	 */
	private List<String> mergeSmallChunks(List<String> chunks)
	{
		List<String>  merged  = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		for (String chunk : chunks)
		{
			// Try adding this chunk to the current accumulator
			String candidate = current.isEmpty()
					? chunk
					: current + "\n\n" + chunk;

			if (estimateTokens(candidate) <= CEILING_TOKENS)
			{
				// Merged candidate still fits — keep accumulating
				current = new StringBuilder(candidate);
			}
			else
			{
				// Adding this chunk would exceed the ceiling — flush current and start fresh
				if (!current.isEmpty())
					merged.add(current.toString().trim());
				current = new StringBuilder(chunk);
			}
		}

		// Flush the last accumulated chunk
		if (!current.isEmpty())
			merged.add(current.toString().trim());

		return merged;
	}

	// ── Phase 3: Overlap ──────────────────────────────────────────────────────

	/**
	 * Prepends the tail of chunk N to chunk N+1 to create a sliding window effect.
	 *
	 * <p>Without overlap, a concept that spans the boundary between two chunks
	 * would appear in neither chunk in full — the embedding of each chunk would
	 * miss part of the idea. Overlap ensures the end of each chunk is also
	 * present at the start of the next one.
	 *
	 * <p>The first chunk is kept unchanged. Only chunks from index 1 onward
	 * receive an overlap prefix.
	 */
	private List<String> applyOverlap(List<String> chunks)
	{
		// No overlap needed for a single chunk
		if (chunks.size() <= 1)
			return chunks;

		List<String> result = new ArrayList<>();
		result.add(chunks.get(0)); // first chunk has no predecessor — kept as-is

		for (int i = 1; i < chunks.size(); i++)
		{
			// Extract the last OVERLAP_TOKENS worth of words from the previous chunk
			String tail  = extractTail(chunks.get(i - 1), OVERLAP_TOKENS);
			// Prepend the tail (separated by a newline) so the chunk reads naturally
			String chunk = tail.isEmpty() ? chunks.get(i) : tail + "\n" + chunks.get(i);
			result.add(chunk);
		}
		return result;
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	/**
	 * Returns the last {@code targetTokens} tokens worth of words from {@code text}.
	 * Returns an empty string if the text is shorter than the target
	 * (no point prepending the entire previous chunk as overlap).
	 */
	private String extractTail(String text, int targetTokens)
	{
		String[] words       = text.split("\\s+");
		int      targetWords = (int) Math.ceil(targetTokens / 1.3); // convert token target to word count

		// Previous chunk is shorter than the overlap window — skip (avoid full duplication)
		if (words.length <= targetWords)
			return "";

		return String.join(" ", Arrays.copyOfRange(words, words.length - targetWords, words.length));
	}

	/**
	 * Estimates the token count of {@code text} using the rule: 1 word ≈ 1.3 tokens.
	 * This is a well-known approximation for English text with most LLM tokenizers
	 * (GPT-style BPE). Avoids the overhead of loading a full tokenizer library.
	 */
	private int estimateTokens(String text)
	{
		if (text == null || text.isBlank()) return 0;
		return (int) Math.ceil(text.split("\\s+").length * 1.3);
	}
}
