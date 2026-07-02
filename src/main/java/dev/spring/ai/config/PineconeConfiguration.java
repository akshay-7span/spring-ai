package dev.spring.ai.config;

import io.pinecone.clients.Pinecone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the one Pinecone touch point Spring AI does not provide out of the box:
 * a raw {@link Pinecone} client used <em>only</em> to call Pinecone's hosted
 * Inference Rerank API.
 *
 * <p>The {@code VectorStore} used for embedding, storage, and Top-K similarity
 * search is auto-configured by the {@code spring-ai-starter-vector-store-pinecone}
 * starter from the {@code spring.ai.vectorstore.pinecone.*} properties, so it does
 * not need to be declared here.
 */
@Configuration
public class PineconeConfiguration
{
	/**
	 * Raw Pinecone client used solely for the hosted rerank call
	 * ({@code pinecone.getInferenceClient().rerank(...)}), which Spring AI does
	 * not expose.
	 *
	 * @param pineconeApiKey the Pinecone API key (reused from the vector store config)
	 * @return a configured Pinecone client
	 */
	@Bean
	public Pinecone pinecone(@Value("${spring.ai.vectorstore.pinecone.api-key}") String pineconeApiKey)
	{
		return new Pinecone.Builder(pineconeApiKey).build();
	}
}
