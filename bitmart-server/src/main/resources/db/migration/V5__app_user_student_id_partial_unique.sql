-- 注销采用软删（仅给 deleted_at 打标，保留行）。原 student_id 全表唯一约束会使
-- "注销后用同一学号重新注册" 与遗留软删行冲突（违反唯一约束 → 500）。
-- 改为部分唯一索引：仅对未注销（deleted_at IS NULL）的行强制学号唯一，
-- 允许已注销行保留其学号，从而支持同一学号重新注册（生成新的用户 id）。

ALTER TABLE app_user DROP CONSTRAINT app_user_student_id_key;

CREATE UNIQUE INDEX app_user_active_student_id_uk
    ON app_user (student_id)
    WHERE deleted_at IS NULL;
