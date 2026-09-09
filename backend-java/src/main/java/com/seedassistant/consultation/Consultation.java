package com.seedassistant.consultation;

import com.seedassistant.draft.DraftModels;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class Consultation {
    private Consultation() { }

    public record Row(String id, String question, String batchCode, String status, String draftResult,
                      String finalAnswer, long version, LocalDateTime createdAt, LocalDateTime updatedAt,
                      LocalDateTime confirmedAt) { }

    public record View(String id, String question, String batchCode, String status, DraftModels.Result draftResult,
                       String finalAnswer, long version, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                       OffsetDateTime confirmedAt) { }

    public record Version(@NotNull @PositiveOrZero Long version) { }
    public enum ReviewAction { SAVE, CONFIRM }
    public record Review(@NotNull @PositiveOrZero Long version,
                         @NotBlank @Size(max = 4000) String finalAnswer,
                         @NotNull ReviewAction action) { }
    public record Page(List<View> items, int page, int size, boolean hasMore) { }
}
