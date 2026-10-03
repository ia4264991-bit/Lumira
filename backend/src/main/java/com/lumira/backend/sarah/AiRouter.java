package com.lumira.backend.sarah;

/** Provider-neutral server-side boundary; provider credentials and routing never reach the client. */
public interface AiRouter {
    String answer(SarahPrompt prompt);
}
