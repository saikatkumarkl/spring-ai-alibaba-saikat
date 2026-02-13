/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.studio.core.rag.splitter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A structure-aware text splitter that preserves document structure boundaries such as
 * tables, code blocks, and section headers. This splitter first extracts structural
 * elements (Markdown/HTML tables, code fences) as atomic units, then splits the
 * remaining prose using a recursive character-based approach with configurable
 * separators.
 *
 * <p>Key features:</p>
 * <ul>
 * <li>Preserves Markdown tables (pipe-delimited) as atomic chunks</li>
 * <li>Preserves HTML tables (&lt;table&gt;...&lt;/table&gt;) as atomic chunks</li>
 * <li>Preserves fenced code blocks (```) as atomic chunks</li>
 * <li>Large tables are split at row boundaries, not mid-row</li>
 * <li>Recursive splitting with hierarchical separators for prose text</li>
 * <li>Configurable chunk size and overlap</li>
 * </ul>
 *
 * @since 1.0.0.3
 */
@Slf4j
public class StructureAwareTextSplitter extends TextSplitter {

	/** Maximum chunk size in characters */
	private final int chunkSize;

	/** Overlap size in characters between consecutive chunks */
	private final int chunkOverlap;

	/** Minimum chunk size — chunks smaller than this are merged with neighbors */
	private final int minChunkSize;

	/** Pattern to detect Markdown tables: lines starting with | */
	private static final Pattern MARKDOWN_TABLE_PATTERN = Pattern.compile(
			"(?m)(^\\|.+\\|\\s*\\n)(^\\|[-:|\\s]+\\|\\s*\\n)((?:^\\|.+\\|\\s*\\n)*)", Pattern.MULTILINE);

	/** Pattern to detect HTML tables */
	private static final Pattern HTML_TABLE_PATTERN = Pattern
		.compile("<table[^>]*>.*?</table>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

	/** Pattern to detect fenced code blocks */
	private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");

	/** Hierarchical separators for recursive splitting — from coarsest to finest */
	private static final String[] SEPARATORS = { "\n\n\n", "\n\n", "\n", ". ", ", ", " ", "" };

	/**
	 * Creates a new StructureAwareTextSplitter.
	 * @param chunkSize Maximum chunk size in characters
	 * @param chunkOverlap Overlap size in characters between chunks
	 */
	public StructureAwareTextSplitter(int chunkSize, int chunkOverlap) {
		this(chunkSize, chunkOverlap, 50);
	}

	/**
	 * Creates a new StructureAwareTextSplitter.
	 * @param chunkSize Maximum chunk size in characters
	 * @param chunkOverlap Overlap size in characters between chunks
	 * @param minChunkSize Minimum chunk size — smaller chunks are merged with neighbors
	 */
	public StructureAwareTextSplitter(int chunkSize, int chunkOverlap, int minChunkSize) {
		this.chunkSize = chunkSize;
		this.chunkOverlap = chunkOverlap;
		this.minChunkSize = minChunkSize;
	}

	@Override
	protected List<String> splitText(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}

		// Phase 1: Extract structural elements and split into segments
		List<TextSegment> segments = extractStructuralSegments(text);

		// Phase 2: Split prose segments, keep structural segments intact (or split at
		// boundaries)
		List<String> chunks = new ArrayList<>();
		for (TextSegment segment : segments) {
			if (segment.isStructural()) {
				// Structural element: keep intact if possible, split at row/element
				// boundaries if too large
				if (segment.text().length() <= chunkSize) {
					chunks.add(segment.text().trim());
				}
				else {
					chunks.addAll(splitStructuralElement(segment));
				}
			}
			else {
				// Prose: use recursive character splitting
				chunks.addAll(recursiveSplit(segment.text(), 0));
			}
		}

		// Phase 3: Merge tiny chunks with neighbors and add overlap
		chunks = mergeSmallChunks(chunks);

		// Filter empty chunks
		return chunks.stream().filter(c -> c != null && !c.isBlank()).toList();
	}

	/**
	 * Extracts structural elements (tables, code blocks) from text and returns an
	 * ordered list of segments where each segment is either structural (to be kept
	 * intact) or prose (to be recursively split).
	 */
	private List<TextSegment> extractStructuralSegments(String text) {
		List<TextSegment> segments = new ArrayList<>();

		// Find all structural element positions
		List<StructuralMatch> matches = new ArrayList<>();
		findMatches(MARKDOWN_TABLE_PATTERN, text, "markdown_table", matches);
		findMatches(HTML_TABLE_PATTERN, text, "html_table", matches);
		findMatches(CODE_BLOCK_PATTERN, text, "code_block", matches);

		// Sort by start position
		matches.sort((a, b) -> Integer.compare(a.start, b.start));

		// Remove overlapping matches (keep the first/longest one)
		matches = removeOverlaps(matches);

		// Build segments from gaps and matches
		int lastEnd = 0;
		for (StructuralMatch match : matches) {
			// Add prose gap before this structural element
			if (match.start > lastEnd) {
				String prose = text.substring(lastEnd, match.start);
				if (!prose.isBlank()) {
					segments.add(new TextSegment(prose, false));
				}
			}
			// Add the structural element
			segments.add(new TextSegment(text.substring(match.start, match.end), true));
			lastEnd = match.end;
		}

		// Add trailing prose
		if (lastEnd < text.length()) {
			String trailing = text.substring(lastEnd);
			if (!trailing.isBlank()) {
				segments.add(new TextSegment(trailing, false));
			}
		}

		// If no structural elements found, treat everything as prose
		if (segments.isEmpty()) {
			segments.add(new TextSegment(text, false));
		}

		return segments;
	}

	private void findMatches(Pattern pattern, String text, String type, List<StructuralMatch> matches) {
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			matches.add(new StructuralMatch(matcher.start(), matcher.end(), type));
		}
	}

	private List<StructuralMatch> removeOverlaps(List<StructuralMatch> matches) {
		List<StructuralMatch> result = new ArrayList<>();
		int lastEnd = -1;
		for (StructuralMatch match : matches) {
			if (match.start >= lastEnd) {
				result.add(match);
				lastEnd = match.end;
			}
		}
		return result;
	}

	/**
	 * Splits a structural element (e.g., a large table) at its internal boundaries. For
	 * Markdown tables: splits at row boundaries. For HTML tables: splits at row
	 * boundaries (&lt;tr&gt;). For code blocks: splits at line boundaries.
	 */
	private List<String> splitStructuralElement(TextSegment segment) {
		String text = segment.text();
		List<String> chunks = new ArrayList<>();

		if (text.contains("<table") || text.contains("<TABLE")) {
			// HTML table: split at <tr> boundaries
			chunks.addAll(splitHtmlTable(text));
		}
		else if (text.startsWith("|")) {
			// Markdown table: split at row boundaries
			chunks.addAll(splitMarkdownTable(text));
		}
		else if (text.startsWith("```")) {
			// Code block: split at line boundaries
			chunks.addAll(splitAtLines(text));
		}
		else {
			// Fallback: treat as prose
			chunks.addAll(recursiveSplit(text, 0));
		}

		return chunks;
	}

	/**
	 * Splits a Markdown table into chunks at row boundaries. Preserves the header row
	 * and separator row in each chunk for context.
	 */
	private List<String> splitMarkdownTable(String tableText) {
		List<String> chunks = new ArrayList<>();
		String[] lines = tableText.split("\n");

		if (lines.length <= 2) {
			chunks.add(tableText.trim());
			return chunks;
		}

		// First two lines are header + separator
		String header = lines[0] + "\n" + lines[1] + "\n";

		StringBuilder currentChunk = new StringBuilder(header);
		for (int i = 2; i < lines.length; i++) {
			String line = lines[i];
			if (line.isBlank()) {
				continue;
			}
			if (currentChunk.length() + line.length() + 1 > chunkSize && currentChunk.length() > header.length()) {
				chunks.add(currentChunk.toString().trim());
				currentChunk = new StringBuilder(header);
			}
			currentChunk.append(line).append("\n");
		}
		if (currentChunk.length() > header.length()) {
			chunks.add(currentChunk.toString().trim());
		}
		return chunks;
	}

	/**
	 * Splits an HTML table into chunks at &lt;tr&gt; row boundaries. Preserves the table
	 * wrapper and any &lt;thead&gt; in each chunk.
	 */
	private List<String> splitHtmlTable(String tableText) {
		List<String> chunks = new ArrayList<>();

		// Extract thead if present
		Pattern theadPattern = Pattern.compile("<thead[^>]*>.*?</thead>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
		Matcher theadMatcher = theadPattern.matcher(tableText);
		String thead = theadMatcher.find() ? theadMatcher.group() : "";

		// Extract all <tr> elements (outside thead)
		String bodyText = tableText;
		if (!thead.isEmpty()) {
			bodyText = bodyText.replace(thead, "");
		}

		Pattern trPattern = Pattern.compile("<tr[^>]*>.*?</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
		Matcher trMatcher = trPattern.matcher(bodyText);
		List<String> rows = new ArrayList<>();
		while (trMatcher.find()) {
			rows.add(trMatcher.group());
		}

		if (rows.isEmpty()) {
			chunks.add(tableText.trim());
			return chunks;
		}

		String tableOpen = "<table>";
		String tableClose = "</table>";
		String prefix = tableOpen + "\n" + thead + "\n<tbody>\n";
		String suffix = "\n</tbody>\n" + tableClose;

		StringBuilder currentChunk = new StringBuilder(prefix);
		for (String row : rows) {
			if (currentChunk.length() + row.length() + suffix.length() > chunkSize
					&& currentChunk.length() > prefix.length()) {
				currentChunk.append(suffix);
				chunks.add(currentChunk.toString().trim());
				currentChunk = new StringBuilder(prefix);
			}
			currentChunk.append(row).append("\n");
		}
		if (currentChunk.length() > prefix.length()) {
			currentChunk.append(suffix);
			chunks.add(currentChunk.toString().trim());
		}

		return chunks;
	}

	/**
	 * Splits text at line boundaries while respecting chunk size.
	 */
	private List<String> splitAtLines(String text) {
		List<String> chunks = new ArrayList<>();
		String[] lines = text.split("\n");
		StringBuilder currentChunk = new StringBuilder();

		for (String line : lines) {
			if (currentChunk.length() + line.length() + 1 > chunkSize && !currentChunk.isEmpty()) {
				chunks.add(currentChunk.toString().trim());
				currentChunk = new StringBuilder();
			}
			currentChunk.append(line).append("\n");
		}
		if (!currentChunk.isEmpty()) {
			chunks.add(currentChunk.toString().trim());
		}
		return chunks;
	}

	/**
	 * Recursively splits text using hierarchical separators. Tries the coarsest separator
	 * first, and falls back to finer separators for chunks that are still too large.
	 */
	private List<String> recursiveSplit(String text, int separatorIndex) {
		if (text.length() <= chunkSize) {
			return List.of(text.trim());
		}

		if (separatorIndex >= SEPARATORS.length) {
			// No more separators — hard cut
			List<String> chunks = new ArrayList<>();
			for (int i = 0; i < text.length(); i += chunkSize - chunkOverlap) {
				int end = Math.min(i + chunkSize, text.length());
				chunks.add(text.substring(i, end).trim());
				if (end == text.length()) {
					break;
				}
			}
			return chunks;
		}

		String separator = SEPARATORS[separatorIndex];
		String[] splits;
		if (separator.isEmpty()) {
			// Character-by-character splitting
			return recursiveSplit(text, separatorIndex + 1);
		}
		else {
			splits = text.split(Pattern.quote(separator), -1);
		}

		if (splits.length <= 1) {
			// This separator doesn't split the text; try next
			return recursiveSplit(text, separatorIndex + 1);
		}

		List<String> chunks = new ArrayList<>();
		StringBuilder currentChunk = new StringBuilder();

		for (int i = 0; i < splits.length; i++) {
			String piece = splits[i];
			String withSep = (i < splits.length - 1) ? piece + separator : piece;

			if (currentChunk.length() + withSep.length() <= chunkSize) {
				currentChunk.append(withSep);
			}
			else {
				// Flush current chunk
				if (!currentChunk.isEmpty()) {
					String flushed = currentChunk.toString().trim();
					if (flushed.length() > chunkSize) {
						// Still too big — recursively split with finer separator
						chunks.addAll(recursiveSplit(flushed, separatorIndex + 1));
					}
					else if (!flushed.isEmpty()) {
						chunks.add(flushed);
					}
				}

				// Start new chunk with overlap from end of previous
				currentChunk = new StringBuilder();
				if (chunkOverlap > 0 && !chunks.isEmpty()) {
					String lastChunk = chunks.get(chunks.size() - 1);
					int overlapStart = Math.max(0, lastChunk.length() - chunkOverlap);
					currentChunk.append(lastChunk.substring(overlapStart));
				}

				// Add current piece
				if (withSep.length() > chunkSize) {
					// Single piece exceeds chunk size — recursively split it
					chunks.addAll(recursiveSplit(withSep, separatorIndex + 1));
				}
				else {
					currentChunk.append(withSep);
				}
			}
		}

		// Flush remaining
		if (!currentChunk.isEmpty()) {
			String flushed = currentChunk.toString().trim();
			if (flushed.length() > chunkSize) {
				chunks.addAll(recursiveSplit(flushed, separatorIndex + 1));
			}
			else if (!flushed.isEmpty()) {
				chunks.add(flushed);
			}
		}

		return chunks;
	}

	/**
	 * Merges chunks smaller than minChunkSize with their neighbors.
	 */
	private List<String> mergeSmallChunks(List<String> chunks) {
		if (chunks.size() <= 1) {
			return chunks;
		}

		List<String> merged = new ArrayList<>();
		StringBuilder buffer = new StringBuilder();

		for (String chunk : chunks) {
			if (buffer.isEmpty()) {
				buffer.append(chunk);
			}
			else if (buffer.length() + chunk.length() + 1 <= chunkSize
					&& (buffer.length() < minChunkSize || chunk.length() < minChunkSize)) {
				buffer.append("\n").append(chunk);
			}
			else {
				merged.add(buffer.toString().trim());
				buffer = new StringBuilder(chunk);
			}
		}
		if (!buffer.isEmpty()) {
			merged.add(buffer.toString().trim());
		}
		return merged;
	}

	/** Internal record for a text segment (either structural or prose) */
	private record TextSegment(String text, boolean isStructural) {
	}

	/** Internal record for a structural element match position */
	private record StructuralMatch(int start, int end, String type) {
	}

}
