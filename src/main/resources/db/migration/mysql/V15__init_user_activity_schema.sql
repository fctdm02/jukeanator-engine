-- Append-only user-activity log (Swing/JFC desktop UI navigation + queue actions, mobile/web queue
-- actions). Shares persistent_identity_seq with every other AbstractPersistentEntity-shaped table
-- (see V12's comment on jukebox_split_period/local_credit_transactions for the same convention).
-- No update/delete path -- UserActivityRepositoryJpaImpl only ever inserts.

CREATE TABLE user_activity (
    persistent_identity INT PRIMARY KEY,
    location_id         INT NULL,
    source               VARCHAR(32) NOT NULL,
    username             VARCHAR(255) NOT NULL,
    activity_type        VARCHAR(64) NOT NULL,
    occurred_at          TIMESTAMP NOT NULL,
    details              JSON NULL
) ENGINE=InnoDB;

CREATE INDEX idx_user_activity_location_occurred_at ON user_activity (location_id, occurred_at);
CREATE INDEX idx_user_activity_activity_type ON user_activity (activity_type);
