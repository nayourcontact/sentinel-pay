package hu.projectpartner.agent.web;

import hu.projectpartner.agent.rag.KnowledgeService;
import hu.projectpartner.agent.service.AgentService;
import hu.projectpartner.agent.web.dto.ChatRequest;
import hu.projectpartner.agent.web.dto.ChatResponse;
import hu.projectpartner.agent.web.dto.KnowledgeFileUploadResponse;
import hu.projectpartner.agent.web.dto.KnowledgeIngestRequest;
import jakarta.validation.Valid;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class AgentController {

    private static final long MAX_DEMO_FILE_BYTES = 1_000_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".md", ".txt");

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;

    public AgentController(AgentService agentService, KnowledgeService knowledgeService) {
        this.agentService = agentService;
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(request.message());
    }

    @PostMapping("/knowledge")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
        String id = knowledgeService.ingest(request.content(), request.metadata());
        return Map.of("documentId", id);
    }

    @PostMapping(value = "/knowledge/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeFileUploadResponse ingestFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "internal-demo") String classification) throws IOException {

        validateKnowledgeFile(file);

        String fileName = file.getOriginalFilename() == null ? "uploaded-document" : file.getOriginalFilename();
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);

        String documentId = knowledgeService.ingest(content, Map.of(
                "source", fileName,
                "classification", classification,
                "ingestionType", "multipart-file"
        ));

        return new KnowledgeFileUploadResponse(
                documentId,
                fileName,
                file.getSize(),
                classification,
                "INDEXED"
        );
    }

    @GetMapping("/knowledge/search")
    public List<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        return knowledgeService.search(q, topK).stream()
                .map(AgentController::toView)
                .toList();
    }

    private static void validateKnowledgeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Knowledge file must not be empty");
        }
        if (file.getSize() > MAX_DEMO_FILE_BYTES) {
            throw new IllegalArgumentException("Knowledge file is larger than the 1 MB demo limit");
        }

        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean supported = SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
        if (!supported) {
            throw new IllegalArgumentException("Only .md and .txt knowledge files are supported by this demo endpoint");
        }
    }

    private static Map<String, Object> toView(Document document) {
        return Map.of(
                "id", document.getId(),
                "text", document.getText(),
                "metadata", document.getMetadata());
    }
}
