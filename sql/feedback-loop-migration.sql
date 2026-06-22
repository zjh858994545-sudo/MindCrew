-- MindCrew feedback loop enhancement

ALTER TABLE `qa_feedback`
  ADD COLUMN `failure_reason` VARCHAR(64) DEFAULT NULL COMMENT '失败原因：RETRIEVAL_MISS/RERANK_WRONG/HALLUCINATION/CITATION_WRONG/ANSWER_INCOMPLETE/OUTDATED_INFO/SECURITY_RISK' AFTER `comment`;

ALTER TABLE `qa_feedback`
  ADD INDEX `idx_failure_reason` (`failure_reason`);
