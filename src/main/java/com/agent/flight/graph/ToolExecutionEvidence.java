package com.agent.flight.graph;

import java.io.Serializable;

public record ToolExecutionEvidence(
        String toolName,
        String arguments,
        String result
) implements Serializable {
}