package hu.projectpartner.agent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record KnowledgeIngestRequest(
        @NotBlank @Size(max = 100_000) String content,
        Map<String, Object> metadata
) {}
