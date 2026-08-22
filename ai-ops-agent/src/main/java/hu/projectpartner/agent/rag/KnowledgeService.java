package hu.projectpartner.agent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private final VectorStore vectorStore;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String ingest(String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Knowledge content must not be blank");
        }

        var safeMetadata = metadata == null ? Map.<String, Object>of() : Map.copyOf(metadata);
        Document document = new Document(content, safeMetadata);

        long start = System.nanoTime();
        vectorStore.add(List.of(document));
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        log.info("event=rag.document.ingested documentId={} chars={} source={} durationMs={}",
                document.getId(),
                content.length(),
                safeMetadata.getOrDefault("source", "unknown"),
                durationMs);

        return document.getId();
    }

    public List<Document> search(String query, int topK) {
        int effectiveTopK = Math.max(1, Math.min(topK, 20));
        long start = System.nanoTime();

        List<Document> result = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(effectiveTopK)
                .build());

        log.info("event=rag.search.completed topK={} resultCount={} durationMs={}",
                effectiveTopK,
                result.size(),
                (System.nanoTime() - start) / 1_000_000);

        return result;
    }
}
