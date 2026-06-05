package dev.spring.ai.config;


import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.ai.vectorstore.SimpleVectorStore;


import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class RagConfiguration
{
/**
	 * Resource pointing to the meetings text file on the classpath.
	 * This file is used as the source input when the persisted vector store does not exist
	 * and a new vector store must be constructed from raw meeting text.
	 */
	@Value("classpath:/meetings/project_kickoff_meeting.txt") // inject the resource located at classpath:/meetings/project_kickoff_meeting.txt
	private Resource resource; // holds the injected Resource instance for reading input text

	/**
	 * Filename used to persist the SimpleVectorStore to disk.
	 * The configured name will be appended to the data directory path returned by getVectorStoreFile().
	 */
	@Value("meeting-vector-store.json") // literal value for the vector store filename (could be externalized to properties)
	private String vectorStoreName; // stores the filename for the persisted vector store

	/**
	 * Creates a SimpleVectorStore bean for the application. Behavior:
	 * - If a persisted vector store file exists on disk, it is loaded to avoid recomputing embeddings.
	 * - If no persisted file exists, the method reads the source text resource, converts it into
	 *   Document objects, splits large documents into smaller chunks appropriate for embedding,
	 *   computes embeddings (via the provided EmbeddingModel) and persists the resulting store to disk.
	 *
	 * The method returns an initialized SimpleVectorStore (loaded or newly built) and is exposed as a Spring @Bean.
	 *
	 * @param embeddingModel EmbeddingModel used by the SimpleVectorStore to compute vector embeddings for documents.
	 * @return initialized SimpleVectorStore instance ready for use by the application.
	 */
	@Bean
	SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) { // define bean that depends on an EmbeddingModel
	    // Create an empty SimpleVectorStore using the provided embedding model (builder pattern).
	    SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build(); // instantiate store with embedding capabilities

	    // Resolve the File on disk where the vector store should be persisted or loaded from.
	    File vectorStoreFile = getVectorStoreFile(); // obtains File reference to src/main/resources/data/{vectorStoreName}

	    // If the persisted vector store already exists, load it to avoid rebuilding embeddings.
	    if (vectorStoreFile.exists()) { // existence check for persisted store
	        System.out.println("Vector Store file exists"); // log presence of persisted store for debugging/visibility
	        simpleVectorStore.load(vectorStoreFile); // load persisted vectors and metadata into the in-memory store
	    } else {
	        // No persisted file found: build the vector store from the textual source resource.
	        System.out.println("Vector Store file not found; building from source resource"); // log build action

	        // Create a TextReader to convert the raw Resource into Document objects.
	        TextReader textReader = new TextReader(resource); // bind reader to injected Resource (the meetings.txt file)

	        // Attach custom metadata to the documents so consumers can trace the origin file.
	        textReader.getCustomMetadata().put("fileName","meetings.txt"); // add provenance metadata for each produced Document

	        // Read documents from the resource. This converts the raw text into one or more Document objects.
	        List<Document> documents = textReader.get(); // perform reading/parsing step

	        // Create a TokenTextSplitter to split large documents into smaller chunks suitable for embedding.
	        TokenTextSplitter textSplitter = new TokenTextSplitter(); // using default splitting strategy (token-based)

	        // Apply the splitter to the documents to produce chunked Document instances.
	        List<Document> splitDocument = textSplitter.apply(documents); // chunking step yields documents optimized for embedding

	        // Add the split documents to the vector store which will compute embeddings using the configured model.
	        simpleVectorStore.add(splitDocument); // compute and store embeddings and associated metadata

	        // Persist the newly created vector store to disk so subsequent restarts can load it instead of rebuilding.
	        simpleVectorStore.save(vectorStoreFile); // serialize and write store to the resolved file location
	    }

	    // Return the loaded or newly built SimpleVectorStore to be managed as a Spring bean.
	    return simpleVectorStore; // bean return
	}

	/**
	 * Constructs the File reference used for persisting the vector store. Implementation notes:
	 * - The method currently constructs a path pointing to `src/main/resources/data/{vectorStoreName}`.
	 * - This approach is convenient during development but for production it is recommended to use a configurable
	 *   external directory (e.g. via application properties) to avoid writing into packaged resources.
	 *
	 * @return File instance pointing to the intended persistence location for the vector store (may not exist yet).
	 */
	private File getVectorStoreFile()
	{
	    Path path = Paths.get( "src", "main", "resources", "data"); // assemble path segments to the project's resources data directory
	    String absolutePath = path.toFile().getAbsolutePath() + "/" + vectorStoreName; // append the configured filename to the directory path
	    return new File(absolutePath); // return File handle for caller to check existence, read, or write
	}
}
