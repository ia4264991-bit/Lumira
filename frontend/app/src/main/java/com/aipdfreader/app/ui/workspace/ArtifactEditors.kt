package com.aipdfreader.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aipdfreader.app.data.remote.dto.FlashcardDto
import com.aipdfreader.app.data.remote.dto.FlashcardPatchDto
import com.aipdfreader.app.data.remote.dto.FlashcardSetDto
import com.aipdfreader.app.data.remote.dto.FlashcardSetPatchDto
import com.aipdfreader.app.data.remote.dto.FlashcardSetWriteDto
import com.aipdfreader.app.data.remote.dto.FlashcardWriteDto
import com.aipdfreader.app.data.remote.dto.QuizDto
import com.aipdfreader.app.data.remote.dto.QuizOptionPatchDto
import com.aipdfreader.app.data.remote.dto.QuizOptionWriteDto
import com.aipdfreader.app.data.remote.dto.QuizPatchDto
import com.aipdfreader.app.data.remote.dto.QuizQuestionPatchDto
import com.aipdfreader.app.data.remote.dto.QuizQuestionWriteDto
import com.aipdfreader.app.data.remote.dto.QuizWriteDto

internal data class QuizDraftInput(
    val title: String,
    val description: String,
    val questions: List<QuizQuestionDraftInput>
)

internal data class QuizQuestionDraftInput(
    val id: String?,
    val prompt: String,
    val options: List<QuizOptionDraftInput>
)

internal data class QuizOptionDraftInput(
    val id: String?,
    val text: String,
    val correct: Boolean
)

internal fun QuizDraftInput.toCreateRequest() = QuizWriteDto(
    title = title,
    description = description,
    questions = questions.mapIndexed { questionIndex, question ->
        QuizQuestionWriteDto(
            position = questionIndex + 1,
            prompt = question.prompt,
            options = question.options.mapIndexed { optionIndex, option ->
                QuizOptionWriteDto(optionIndex + 1, option.text, option.correct)
            }
        )
    }
)

internal fun QuizDraftInput.toPatchRequest() = QuizPatchDto(
    title = title,
    description = description,
    questions = questions.mapIndexed { questionIndex, question ->
        QuizQuestionPatchDto(
            id = question.id,
            position = questionIndex + 1,
            prompt = question.prompt,
            options = question.options.mapIndexed { optionIndex, option ->
                QuizOptionPatchDto(option.id, optionIndex + 1, option.text, option.correct)
            }
        )
    }
)

private class EditableQuizOption(
    val id: String?,
    initialText: String,
    initialCorrect: Boolean
) {
    var text by mutableStateOf(initialText)
    var correct by mutableStateOf(initialCorrect)
}

private class EditableQuizQuestion(
    val id: String?,
    initialPrompt: String,
    initialOptions: List<EditableQuizOption>
) {
    var prompt by mutableStateOf(initialPrompt)
    val options = mutableStateListOf<EditableQuizOption>().apply { addAll(initialOptions) }
}

private fun newQuizQuestion() = EditableQuizQuestion(
    id = null,
    initialPrompt = "",
    initialOptions = (1..4).map { index -> EditableQuizOption(null, "", index == 1) }
)

@Composable
internal fun QuizEditorDialog(
    initial: QuizDto?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (QuizDraftInput) -> Unit
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    val questions = remember(initial?.id) {
        mutableStateListOf<EditableQuizQuestion>().apply {
            val existing = initial?.questions.orEmpty()
            if (existing.isEmpty()) add(newQuizQuestion())
            else existing.forEach { question ->
                add(EditableQuizQuestion(
                    question.id,
                    question.prompt,
                    question.options.map { option ->
                        EditableQuizOption(option.id, option.text, option.correct == true)
                    }
                ))
            }
        }
    }
    val canSave = title.isNotBlank() && questions.isNotEmpty() && questions.all { question ->
        question.prompt.isNotBlank() && question.options.size >= 2 &&
            question.options.all { it.text.isNotBlank() } &&
            question.options.count { it.correct } == 1
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create quiz" else "Edit quiz") },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Quiz title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                questions.forEachIndexed { questionIndex, question ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Question " + (questionIndex + 1),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (questions.size > 1) {
                                    TextButton(onClick = { questions.remove(question) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = question.prompt,
                                onValueChange = { question.prompt = it },
                                label = { Text("Question") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            Text("Select the correct answer", style = MaterialTheme.typography.labelMedium)
                            question.options.forEachIndexed { optionIndex, option ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = option.correct,
                                        onClick = {
                                            question.options.forEachIndexed { index, candidate ->
                                                candidate.correct = index == optionIndex
                                            }
                                        }
                                    )
                                    OutlinedTextField(
                                        value = option.text,
                                        onValueChange = { option.text = it },
                                        label = { Text("Answer " + (optionIndex + 1)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    if (question.options.size > 2) {
                                        IconButton(onClick = {
                                            question.options.remove(option)
                                            if (question.options.none { it.correct }) {
                                                question.options.first().correct = true
                                            }
                                        }) {
                                            Text("×")
                                        }
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { question.options.add(EditableQuizOption(null, "", false)) },
                                enabled = question.options.size < 6,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Add answer") }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { questions.add(newQuizQuestion()) },
                    enabled = questions.size < 30,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add question") }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        QuizDraftInput(
                            title.trim(),
                            description.trim(),
                            questions.map { question ->
                                QuizQuestionDraftInput(
                                    question.id,
                                    question.prompt.trim(),
                                    question.options.map { option ->
                                        QuizOptionDraftInput(option.id, option.text.trim(), option.correct)
                                    }
                                )
                            }
                        )
                    )
                },
                enabled = canSave && !busy
            ) { Text(if (busy) "Saving…" else "Save quiz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

internal data class FlashcardSetDraftInput(
    val title: String,
    val description: String,
    val cards: List<FlashcardDraftInput>
)

internal data class FlashcardDraftInput(
    val id: String?,
    val front: String,
    val back: String
)

internal fun FlashcardSetDraftInput.toCreateRequest() = FlashcardSetWriteDto(
    title = title,
    description = description,
    cards = cards.mapIndexed { index, card -> FlashcardWriteDto(index + 1, card.front, card.back) }
)

internal fun FlashcardSetDraftInput.toPatchRequest() = FlashcardSetPatchDto(
    title = title,
    description = description,
    cards = cards.mapIndexed { index, card -> FlashcardPatchDto(card.id, index + 1, card.front, card.back) }
)

private class EditableFlashcard(val id: String?, initialFront: String, initialBack: String) {
    var front by mutableStateOf(initialFront)
    var back by mutableStateOf(initialBack)
}

@Composable
internal fun FlashcardSetEditorDialog(
    initial: FlashcardSetDto?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (FlashcardSetDraftInput) -> Unit
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    val cards = remember(initial?.id) {
        mutableStateListOf<EditableFlashcard>().apply {
            val existing = initial?.cards.orEmpty()
            if (existing.isEmpty()) add(EditableFlashcard(null, "", ""))
            else existing.forEach { add(EditableFlashcard(it.id, it.front, it.back)) }
        }
    }
    val canSave = title.isNotBlank() && cards.isNotEmpty() &&
        cards.all { it.front.isNotBlank() && it.back.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create flashcard set" else "Edit flashcard set") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Set title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                cards.forEachIndexed { index, card ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Card " + (index + 1), style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f))
                                if (cards.size > 1) TextButton(onClick = { cards.remove(card) }) { Text("Remove") }
                            }
                            OutlinedTextField(value = card.front, onValueChange = { card.front = it },
                                label = { Text("Front") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            OutlinedTextField(value = card.back, onValueChange = { card.back = it },
                                label = { Text("Back") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { cards.add(EditableFlashcard(null, "", "")) },
                    enabled = cards.size < 200,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add flashcard") }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        FlashcardSetDraftInput(
                            title.trim(),
                            description.trim(),
                            cards.map { FlashcardDraftInput(it.id, it.front.trim(), it.back.trim()) }
                        )
                    )
                },
                enabled = canSave && !busy
            ) { Text(if (busy) "Saving…" else "Save set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun FlashcardProgressDialog(
    set: FlashcardSetDto,
    progress: List<com.aipdfreader.app.data.remote.dto.FlashcardProgressDto>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val cardById = set.cards.associateBy { it.id }
    val gotIt = progress.count { it.outcome == "GOT_IT" }
    val again = progress.count { it.outcome == "AGAIN" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your review history") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(set.title, style = MaterialTheme.typography.titleMedium)
                Text(progress.size.toString() + " reviews · " + gotIt + " got it · " + again + " again")
                if (loading) {
                    Spacer(Modifier.height(4.dp))
                    Text("Loading review history…")
                } else if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                } else if (progress.isEmpty()) {
                    Text("No reviews yet.")
                } else {
                    progress.forEach { item ->
                        val front = cardById[item.flashcardId]?.front ?: "Removed flashcard"
                        Text(
                            (item.outcome.replace('_', ' ')) + " · " + front +
                                (item.reviewedAt?.let { " · " + it } ?: "")
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}