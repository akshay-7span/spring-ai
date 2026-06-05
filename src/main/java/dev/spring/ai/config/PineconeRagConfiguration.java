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
public class PineconeRagConfiguration
{
	@Value("classpath:/meetings/project_kickoff_meeting.txt")
	private Resource meetingResource;

	@Value("${spring.ai.vectorstore.pinecone.api-key}")
	private String apiKey;

	@Value("${spring.ai.vectorstore.pinecone.index-name}")
	private String indexName;

	@Value("${spring.ai.vectorstore.pinecone.namespace:default}")
	private String namespace;

	@Bean
	public VectorStore pineconeVectorStore(EmbeddingModel embeddingModel)
	{
		return PineconeVectorStore.builder(embeddingModel)
				.apiKey(apiKey)
				.indexName(indexName)
				.namespace(namespace)
				.build();
	}

	@Bean
	public Boolean ingestMeetingDocuments(VectorStore pineconeVectorStore)
	{
		TextReader textReader = new TextReader(meetingResource);
		List<Document> documents = textReader.read();

		TokenTextSplitter splitter = new TokenTextSplitter();
		List<Document> chunks = splitter.split(documents);

		pineconeVectorStore.add(chunks);
		return true;
	}
}