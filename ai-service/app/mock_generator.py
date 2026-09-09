from app.schemas import DraftRequest, DraftResult


def generate_draft(request: DraftRequest) -> DraftResult:
    # 本阶段只有发芽率咨询模板；不识别任意问题，也不从买家文字提取事实。
    if request.batchCode is None:
        answer = "【模拟草稿】请先提供种子批次号，商家核对该批次的检测或试种记录后，再说明发芽情况。当前没有可核实的发芽率数据。"
        missing = ["batchCode", "batchEvidence"]
    else:
        answer = f"【模拟草稿】已收到批次号 {request.batchCode}，还需要商家提供并核对该批次的检测或试种记录。当前无法确认发芽率，请勿将宣传话术当作批次检测结论。"
        missing = ["batchEvidence"]
    return DraftResult(requestId=request.requestId, answerDraft=answer, missingFields=missing)
