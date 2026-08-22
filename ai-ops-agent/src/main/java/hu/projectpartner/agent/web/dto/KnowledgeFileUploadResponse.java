package hu.projectpartner.agent.web.dto;

public record KnowledgeFileUploadResponse(
        String documentId,
        String fileName,
        long bytes,
        String classification,
        String status
) {
}
