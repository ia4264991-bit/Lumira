CREATE TABLE quiz (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_card_id UUID REFERENCES card(id) ON DELETE CASCADE,
    owner_user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT quiz_exactly_one_owner CHECK (num_nonnulls(owner_card_id, owner_user_id) = 1),
    CONSTRAINT quiz_title_nonblank CHECK (length(btrim(title)) > 0)
);
CREATE INDEX quiz_owner_card_created_idx ON quiz(owner_card_id, created_at DESC) WHERE owner_card_id IS NOT NULL;
CREATE INDEX quiz_owner_user_created_idx ON quiz(owner_user_id, created_at DESC) WHERE owner_user_id IS NOT NULL;

CREATE TABLE quiz_question (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES quiz(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position > 0),
    prompt TEXT NOT NULL CHECK (length(btrim(prompt)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT quiz_question_position_unique UNIQUE (quiz_id, position)
);
CREATE INDEX quiz_question_quiz_idx ON quiz_question(quiz_id, position);

CREATE TABLE quiz_question_option (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES quiz_question(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position > 0),
    option_text TEXT NOT NULL CHECK (length(btrim(option_text)) > 0),
    is_correct BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT quiz_option_position_unique UNIQUE (question_id, position)
);
CREATE UNIQUE INDEX quiz_one_correct_option_idx ON quiz_question_option(question_id) WHERE is_correct = true;
CREATE INDEX quiz_option_question_idx ON quiz_question_option(question_id, position);

CREATE FUNCTION enforce_quiz_question_structure(p_question_id UUID) RETURNS VOID AS $$
DECLARE
    option_count BIGINT;
    correct_count BIGINT;
    min_position INTEGER;
    max_position INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM quiz_question WHERE id = p_question_id) THEN
        RETURN;
    END IF;
    SELECT count(*), count(*) FILTER (WHERE is_correct), min(position), max(position)
      INTO option_count, correct_count, min_position, max_position
      FROM quiz_question_option WHERE question_id = p_question_id;
    IF option_count < 2 OR correct_count <> 1 OR min_position <> 1 OR max_position <> option_count THEN
        RAISE EXCEPTION 'Quiz question must have contiguous options starting at 1, at least two options, and exactly one correct option'
            USING ERRCODE = '23514';
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION enforce_quiz_structure(p_quiz_id UUID) RETURNS VOID AS $$
DECLARE
    question_count BIGINT;
    min_position INTEGER;
    max_position INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM quiz WHERE id = p_quiz_id) THEN
        RETURN;
    END IF;
    SELECT count(*), min(position), max(position)
      INTO question_count, min_position, max_position
      FROM quiz_question WHERE quiz_id = p_quiz_id;
    IF question_count < 1 OR min_position <> 1 OR max_position <> question_count THEN
        RAISE EXCEPTION 'Quiz must have contiguous questions starting at 1 and at least one question'
            USING ERRCODE = '23514';
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION check_quiz_question_structure_trigger() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM enforce_quiz_question_structure(OLD.id);
        PERFORM enforce_quiz_structure(OLD.quiz_id);
        RETURN OLD;
    END IF;
    PERFORM enforce_quiz_question_structure(NEW.id);
    PERFORM enforce_quiz_structure(NEW.quiz_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION check_quiz_option_structure_trigger() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM enforce_quiz_question_structure(OLD.question_id);
        RETURN OLD;
    END IF;
    PERFORM enforce_quiz_question_structure(NEW.question_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER quiz_question_structure_constraint
AFTER INSERT OR UPDATE OR DELETE ON quiz_question
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION check_quiz_question_structure_trigger();

CREATE CONSTRAINT TRIGGER quiz_option_structure_constraint
AFTER INSERT OR UPDATE OR DELETE ON quiz_question_option
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION check_quiz_option_structure_trigger();

ALTER TABLE artifact_share ADD COLUMN quiz_id UUID REFERENCES quiz(id) ON DELETE CASCADE;
ALTER TABLE artifact_share DROP CONSTRAINT artifact_share_exactly_one_artifact;
ALTER TABLE artifact_share ADD CONSTRAINT artifact_share_exactly_one_artifact
    CHECK (num_nonnulls(resource_id, note_id, study_set_id, quiz_id) = 1);
CREATE UNIQUE INDEX artifact_share_quiz_card_unique ON artifact_share(quiz_id, card_id) WHERE quiz_id IS NOT NULL;

CREATE TABLE quiz_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES quiz(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    correct_count INTEGER NOT NULL CHECK (correct_count >= 0),
    total_questions INTEGER NOT NULL CHECK (total_questions > 0 AND correct_count <= total_questions),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX quiz_attempt_user_quiz_submitted_idx ON quiz_attempt(user_id, quiz_id, submitted_at DESC);

CREATE TABLE quiz_attempt_answer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES quiz_attempt(id) ON DELETE CASCADE,
    question_id UUID NOT NULL,
    question_position INTEGER NOT NULL CHECK (question_position > 0),
    prompt_snapshot TEXT NOT NULL,
    selected_option_id UUID NOT NULL,
    selected_option_text TEXT NOT NULL,
    correct_option_id UUID NOT NULL,
    correct_option_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT quiz_attempt_answer_question_unique UNIQUE (attempt_id, question_id)
);

CREATE TABLE quiz_attempt_answer_option (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    answer_id UUID NOT NULL REFERENCES quiz_attempt_answer(id) ON DELETE CASCADE,
    option_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position > 0),
    option_text TEXT NOT NULL,
    is_selected BOOLEAN NOT NULL DEFAULT false,
    is_correct BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT quiz_attempt_answer_option_position_unique UNIQUE (answer_id, position)
);
CREATE UNIQUE INDEX quiz_attempt_one_selected_option_idx ON quiz_attempt_answer_option(answer_id) WHERE is_selected = true;
CREATE UNIQUE INDEX quiz_attempt_one_correct_option_idx ON quiz_attempt_answer_option(answer_id) WHERE is_correct = true;
