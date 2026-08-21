package dev.gathering.core.format;

import java.util.List;

/** What a deck check came to. */
public record ValidationResult(FormatPreset preset, List<ValidationIssue> issues) {

    public ValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean isLegal() {
        return issues.stream().noneMatch(ValidationIssue::isError);
    }

    public List<ValidationIssue> errors() {
        return issues.stream().filter(ValidationIssue::isError).toList();
    }

    public List<ValidationIssue> warnings() {
        return issues.stream().filter(issue -> !issue.isError()).toList();
    }

    public boolean isClean() {
        return issues.isEmpty();
    }
}
