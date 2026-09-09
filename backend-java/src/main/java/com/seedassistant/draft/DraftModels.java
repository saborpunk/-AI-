package com.seedassistant.draft;

import jakarta.validation.constraints.*;
import java.util.List;

public final class DraftModels {
    private DraftModels() { }

    public record Request(
            @NotBlank(message = "问题不能为空") @Size(max = 2000, message = "问题最多2000个字符") String question,
            @Pattern(regexp = "[A-Z0-9-]{1,40}", message = "批次号只能包含1到40位大写字母、数字或短横线") String batchCode) { }

    public record PythonRequest(String requestId, String question, String batchCode) { }

    public record Result(
            @NotBlank String requestId,
            @NotBlank @Size(max = 4000) String answerDraft,
            @NotNull @Size(max = 2) List<@NotNull String> missingFields,
            @NotNull Boolean needsHumanReview,
            @NotBlank String mode) { }

    public record Response(String requestId, Result data) { }
}
