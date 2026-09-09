package com.seedassistant.consultation;

import com.seedassistant.draft.DraftModels;
import com.seedassistant.draft.PythonDraftClient;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ConsultationService {
    private final ConsultationMapper mapper;
    private final PythonDraftClient python;
    private final TransactionTemplate transaction;
    private final JsonMapper json = JsonMapper.builder().build();

    public ConsultationService(ConsultationMapper mapper, PythonDraftClient python, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.python = python;
        this.transaction = new TransactionTemplate(manager);
    }

    public Consultation.View create(DraftModels.Request request) {
        String id = UUID.randomUUID().toString();
        return transaction.execute(ignored -> {
            // 保留原始问题；Python 客户端会对交给模拟助手的文本去除首尾空白。
            mapper.insert(id, request.question(), request.batchCode());
            return get(id);
        });
    }

    public Consultation.View get(String id) { return view(require(id)); }

    public Consultation.Page history(int page, int size) {
        var rows = mapper.history(size + 1, (page - 1) * size);
        return new Consultation.Page(rows.stream().limit(size).map(this::view).toList(), page, size, rows.size() > size);
    }

    public Consultation.View generate(String id, long version, String requestId) {
        var row = require(id);
        requireEditable(row, version);
        if (!"PENDING".equals(row.status())) { throw conflict(); }
        // 网络调用不放进数据库事务。失败时原咨询保持原状，可重试或人工接管。
        var result = python.generate(requestId, new DraftModels.Request(row.question(), row.batchCode()));
        return transaction.execute(ignored -> {
            if (mapper.saveDraft(id, version, json.writeValueAsString(result)) != 1) { throw conflict(); }
            return get(id);
        });
    }

    public Consultation.View review(String id, Consultation.Review request) {
        requireEditable(require(id), request.version());
        String status = request.action() == Consultation.ReviewAction.CONFIRM ? "CONFIRMED" : "DRAFT_READY";
        return transaction.execute(ignored -> {
            if (mapper.review(id, request.version(), request.finalAnswer(), status) != 1) { throw conflict(); }
            return get(id);
        });
    }

    private Consultation.Row require(String id) {
        var row = mapper.find(id);
        if (row == null) { throw new ConsultationException(404, "CONSULTATION_NOT_FOUND", "咨询记录不存在"); }
        return row;
    }

    private void requireEditable(Consultation.Row row, long version) {
        if (row.version() != version || "CONFIRMED".equals(row.status())) { throw conflict(); }
    }

    private ConsultationException conflict() {
        return new ConsultationException(409, "CONSULTATION_CONFLICT", "记录已变化或已确认，请重新查询后操作");
    }

    private Consultation.View view(Consultation.Row row) {
        return new Consultation.View(row.id(), row.question(), row.batchCode(), row.status(),
                row.draftResult() == null ? null : json.readValue(row.draftResult(), DraftModels.Result.class),
                row.finalAnswer(), row.version(), row.createdAt().atOffset(ZoneOffset.UTC),
                row.updatedAt().atOffset(ZoneOffset.UTC),
                row.confirmedAt() == null ? null : row.confirmedAt().atOffset(ZoneOffset.UTC));
    }
}
