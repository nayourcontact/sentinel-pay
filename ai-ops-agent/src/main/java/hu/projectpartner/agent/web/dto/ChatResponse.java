package hu.projectpartner.agent.web.dto;

import java.time.Instant;

public record ChatResponse(
        String requestId,
        String answer,
        String model,
        String architecture,
        Instant timestamp
) {}
