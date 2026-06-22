package dev.spring.ai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class PineconeConfiguration
{
	@Value("${spring.ai.vectorstore.pinecone.api-key}")
	private String apiKey;

	@Value("${spring.ai.vectorstore.pinecone.index-name}")
	private String indexName;

	@Value("${pdf.ingestion.namespace.pages}")
	private String pagesNamespace;

	@Value("${pdf.ingestion.namespace.sections}")
	private String sectionsNamespace;

	// Stores raw page text chunks — no custom metadata
	@Bean("pagesVectorStore")
	@Primary
	public VectorStore pagesVectorStore(EmbeddingModel embeddingModel)
	{
		return PineconeVectorStore.builder(embeddingModel)
				.apiKey(apiKey)
				.indexName(indexName)
				.namespace(pagesNamespace)
				.build();
	}

	// Stores body-only chunks — each chunk carries printedPageNumber in metadata
	@Bean("sectionsVectorStore")
	public VectorStore sectionsVectorStore(EmbeddingModel embeddingModel)
	{
		return PineconeVectorStore.builder(embeddingModel)
				.apiKey(apiKey)
				.indexName(indexName)
				.namespace(sectionsNamespace)
				.build();
	}
}
