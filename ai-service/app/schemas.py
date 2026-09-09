from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class DraftRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    requestId: UUID
    question: str = Field(min_length=1, max_length=2000)
    batchCode: str | None = Field(default=None, pattern=r"^[A-Z0-9-]{1,40}$")

    @field_validator("question")
    @classmethod
    def reject_blank_question(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("question must not be blank")
        return value.strip()


class DraftResult(BaseModel):
    requestId: UUID
    answerDraft: str
    missingFields: list[Literal["batchCode", "batchEvidence"]]
    needsHumanReview: Literal[True] = True
    mode: Literal["mock"] = "mock"
