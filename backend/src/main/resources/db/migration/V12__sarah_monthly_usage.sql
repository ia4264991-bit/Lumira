CREATE TABLE sarah_usage (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0 CHECK (request_count >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, period_start)
);
