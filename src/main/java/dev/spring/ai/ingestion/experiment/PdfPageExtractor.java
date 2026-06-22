package dev.spring.ai.ingestion.experiment;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads PDF files and extracts text using two different strategies:
 *
 * <ul>
 *   <li><b>Flat extraction</b> ({@link #extractPagesText}) — reads each page
 *       as a single block of text. Fast and simple; good for documents where
 *       every line is useful content.</li>
 *   <li><b>Position-based extraction</b> ({@link #extractPageSections}) —
 *       divides each page into three vertical zones (header 10%, body 80%,
 *       footer 10%) and extracts text from each zone independently. Allows
 *       the caller to discard boilerplate before chunking.</li>
 * </ul>
 *
 * <p>Uses Apache PDFBox 3.x internally. Both methods open the PDF with a
 * try-with-resources block so the document is always closed after reading.
 */
@Component
public class PdfPageExtractor
{
	private static final Logger log       = LoggerFactory.getLogger(PdfPageExtractor.class);
	private static final String SEPARATOR = "=".repeat(72);
	private static final String DIVIDER   = "-".repeat(72);

	/**
	 * Extracts the full text of each page as a flat string.
	 *
	 * <p>Uses {@link PDFTextStripper} with {@code setStartPage} / {@code setEndPage}
	 * to process one page at a time. The order of the returned map matches the
	 * physical page order in the PDF (insertion-ordered {@link LinkedHashMap}).
	 *
	 * @param pdfFile the PDF file to read
	 * @return a map of 1-based page number → full page text
	 * @throws IOException if the file cannot be opened or read
	 */
	public Map<Integer, String> extractPagesText(File pdfFile) throws IOException
	{
		// LinkedHashMap preserves page order — important for downstream chunking
		Map<Integer, String> pages = new LinkedHashMap<>();

		try (PDDocument document = Loader.loadPDF(pdfFile))
		{
			int totalPages = document.getNumberOfPages();

			log.info(SEPARATOR);
			log.info("PDF LOADED  : {}", pdfFile.getName());
			log.info("TOTAL PAGES : {}", totalPages);
			log.info(SEPARATOR);

			// PDFTextStripper is reused across all pages.
			// setStartPage/setEndPage narrows it to a single page per iteration.
			PDFTextStripper stripper = new PDFTextStripper();

			for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++)
			{
				stripper.setStartPage(pageNumber);
				stripper.setEndPage(pageNumber);
				String text = stripper.getText(document);
				pages.put(pageNumber, text);

				log.info(DIVIDER);
				log.info("PAGE {} / {}", pageNumber, totalPages);
				log.info(DIVIDER);
				log.info("{}", text.trim());
			}

			log.info(SEPARATOR);
			log.info("EXTRACTION COMPLETE — {} page(s) extracted from '{}'", pages.size(), pdfFile.getName());
			log.info(SEPARATOR);
		}

		return pages;
	}

	/**
	 * Extracts text from each page split into three vertical zones:
	 * header (top 10%), body (middle 80%), and footer (bottom 10%).
	 *
	 * <p>Uses {@link PDFTextStripperByArea}, which works in screen coordinates
	 * (origin at top-left, Y axis grows downward). This is the opposite of
	 * PDF's internal coordinate system (bottom-left origin), so header zone
	 * starts at y=0 (the visual top of the page).
	 *
	 * <p>Zone heights are computed as percentages of the page's {@link PDRectangle}
	 * height, so this works correctly for any page size (A4, Letter, etc.).
	 *
	 * @param pdfFile the PDF file to read
	 * @return one {@link PageSections} per page, in document order
	 * @throws IOException if the file cannot be opened or read
	 */
	public List<PageSections> extractPageSections(File pdfFile) throws IOException
	{
		List<PageSections> result = new ArrayList<>();

		try (PDDocument document = Loader.loadPDF(pdfFile))
		{
			int totalPages = document.getNumberOfPages();

			log.info(SEPARATOR);
			log.info("PDF LOADED     : {}", pdfFile.getName());
			log.info("TOTAL PAGES    : {}", totalPages);
			log.info("STRATEGY       : Position-based (header 10% | body 80% | footer 10%)");
			log.info(SEPARATOR);

			for (int i = 0; i < totalPages; i++)
			{
				int    pageNumber = i + 1;            // convert 0-based index to 1-based page number
				PDPage page       = document.getPage(i);
				PDRectangle mediaBox = page.getMediaBox();

				// Derive zone heights as percentages of the physical page height
				float pageWidth    = mediaBox.getWidth();
				float pageHeight   = mediaBox.getHeight();
				float headerHeight = pageHeight * 0.10f;  // top 10%
				float bodyHeight   = pageHeight * 0.80f;  // middle 80%
				float footerHeight = pageHeight * 0.10f;  // bottom 10%

				// PDFTextStripperByArea uses screen-space coordinates (top-left origin).
				// Each Rectangle2D is defined as (x, y, width, height) where y=0 is the
				// very top of the page — so header starts at y=0, body starts where header
				// ends, and footer starts where body ends.
				PDFTextStripperByArea stripper = new PDFTextStripperByArea();
				stripper.setSortByPosition(true);  // ensures text is read in reading order
				stripper.addRegion("header", new Rectangle2D.Float(0, 0,                          pageWidth, headerHeight));
				stripper.addRegion("body",   new Rectangle2D.Float(0, headerHeight,               pageWidth, bodyHeight));
				stripper.addRegion("footer", new Rectangle2D.Float(0, headerHeight + bodyHeight,  pageWidth, footerHeight));
				stripper.extractRegions(page);

				String header = stripper.getTextForRegion("header").trim();
				String body   = stripper.getTextForRegion("body").trim();
				String footer = stripper.getTextForRegion("footer").trim();

				result.add(new PageSections(pageNumber, header, body, footer));

				log.info(SEPARATOR);
				log.info("PAGE {} / {}  ({}pt x {}pt)", pageNumber, totalPages, (int) pageWidth, (int) pageHeight);
				log.info(DIVIDER);
				log.info("[ HEADER ]");
				log.info("{}", header.isEmpty() ? "(empty)" : header);
				log.info(DIVIDER);
				log.info("[ BODY ]");
				log.info("{}", body.isEmpty() ? "(empty)" : body);
				log.info(DIVIDER);
				log.info("[ FOOTER ]");
				log.info("{}", footer.isEmpty() ? "(empty)" : footer);
				log.info(SEPARATOR);
			}

			log.info("SECTION EXTRACTION COMPLETE — {} page(s) from '{}'", totalPages, pdfFile.getName());
			log.info(SEPARATOR);
		}

		return result;
	}
}
