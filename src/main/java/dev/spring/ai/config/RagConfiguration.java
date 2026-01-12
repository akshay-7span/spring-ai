package dev.spring.ai.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration
public class RagConfiguration
{
	/**
	 * Resource pointing to the meetings text file on the classpath.
	 * This file is used as the source input when the persisted vector store does not exist
	 * and a new vector store must be constructed from raw meeting text.
	 */
	@Value("classpath:/meetings/project_kickoff_meeting.txt")
	private Resource resource; // holds the injected Resource instance for reading input text

	@Value("${spring.ai.vectorstore.pinecone.api-key}")
	private String apiKey;

	@Value("${spring.ai.vectorstore.pinecone.index-name}")
	private String indexName;

	@Value("${spring.ai.vectorstore.pinecone.namespace:default}")
	private String namespace;

/**
		 * Create and configure a Pinecone-backed {@link VectorStore} bean.
		 *
		 * @param embeddingModel the embedding model used to convert text into vector embeddings.
		 * @return a configured {@link VectorStore} instance connected to Pinecone using
		 *         the configured {@code apiKey}, {@code indexName} and {@code namespace}.
		 */
		@Bean
		public VectorStore pineconeVectorStore(EmbeddingModel embeddingModel) {
			// Use the injected EmbeddingModel to build a Pinecone-backed VectorStore.
			// Connection and index configuration are read from application properties.
			return PineconeVectorStore.builder(embeddingModel)
					.apiKey(apiKey)
					.indexName(indexName)
					.namespace(namespace)
					.build();
		}


		/**
		 * Load meeting documents from the embedded resource, split them into token-based chunks,
		 * and persist those chunks into the provided {@link VectorStore}.
		 *
		 * This bean runs on startup and returns {@code true} when the ingest operation completes.
		 *
		 * @param vectorStore the {@link VectorStore} used to persist document chunks.
		 * @return {@code true} when documents were read, split and added to the vector store.
		 */
		@Bean
		public Boolean meetingDocuments(VectorStore vectorStore)
		{
			// Create a TextReader that reads the configured classpath resource containing meeting text
			TextReader textReader = new TextReader(resource);

			// Read the raw documents from the resource (may return a list with a single Document)
			List<Document> documents = textReader.read();

			// Split documents into smaller token-based chunks suitable for embedding and retrieval
			TokenTextSplitter splitter = new TokenTextSplitter();
			List<Document> chunks = splitter.split(documents);

			// Add all chunks to the provided VectorStore so they are available for similarity search
			vectorStore.add(chunks);

			// Return a simple indication that the operation completed successfully
			return true;
		}
}
