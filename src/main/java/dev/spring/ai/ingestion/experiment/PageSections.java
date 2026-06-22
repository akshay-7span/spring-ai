package dev.spring.ai.ingestion.experiment;

/**
 * Holds the three positional zones extracted from a single PDF page.
 *
 * @param pageNumber  1-based page number
 * @param header      text found in the top 10% of the page
 * @param body        text found in the middle 80% of the page
 * @param footer      text found in the bottom 10% of the page
 */
public record PageSections(
        int pageNumber,
        String header,
        String body,
        String footer
) {}
