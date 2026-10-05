package com.aipdfreader.app.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.repository.LearnerProfile
import com.aipdfreader.app.data.repository.LearnerProfileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

private val learningGoals = listOf("Prepare for exams", "Understand my classes", "Build a study habit", "Explore something new")
private val learningInterests = listOf("Science", "Maths", "Languages", "Technology", "Business", "Arts", "Health", "Other")
private val experienceLevels = listOf("New learner", "I know a little", "I’m confident already")
private val studyTargets = listOf(5, 10, 15, 30)

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val profileStore: LearnerProfileStore
) : ViewModel() {
    fun save(name: String, goal: String, interests: List<String>, experience: String, minutes: Int, onSaved: () -> Unit) {
        val currentUid = profileStore.currentUid ?: return
        profileStore.save(LearnerProfile(currentUid, name.trim(), goal, interests, experience, minutes))
        onSaved()
    }

    fun setPhoto(uri: android.net.Uri) = viewModelScope.launch { profileStore.setPhoto(uri) }
}

@Composable
fun ProfileSetupScreen(
    onComplete: () -> Unit,
    viewModel: ProfileSetupViewModel = hiltViewModel()
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    val interests = remember { mutableStateListOf<String>() }
    var experience by remember { mutableStateOf(experienceLevels.first()) }
    var minutes by remember { mutableIntStateOf(10) }
    var error by remember { mutableStateOf<String?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { photoUri = it; viewModel.setPhoto(it) }
    }
    val progress by animateFloatAsState((step + 1) / 5f, tween(420, easing = FastOutSlowInEasing), label = "setup-progress")

    Scaffold { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) IconButton(onClick = { step--; error = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous question")
                } else Spacer(Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text("Make Vision yours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("A few quick questions · ${step + 1} of 5", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    if (name.isBlank()) name = "Learner"
                    if (goal.isBlank()) goal = learningGoals[1]
                    viewModel.save(name, goal, interests.toList(), experience, minutes, onComplete)
                }) { Text("Later") }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedBird()
                    Spacer(Modifier.width(14.dp))
                    Text(
                        listOf("Hi! I’m your Vision bird.", "Let’s set your direction.", "Pick what sparks your curiosity.", "Every starting point is a good one.", "Small steps add up.")[step],
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(240)) { -it / 6 } + fadeOut(tween(180))) using SizeTransform(clip = false)
                    }, label = "onboarding-question"
                ) { page ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            listOf("What should we call you?", "What are you learning for?", "What do you like learning about?", "Where are you starting from?", "What feels like a good daily goal?")[page],
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                        )
                        Text(
                            listOf("Your name will appear on your account.", "We’ll use this to make your study space feel relevant.", "Choose as many as you like. You can change this later.", "This helps Vision meet you where you are.", "Choose a goal that fits your real day.")[page],
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when (page) {
                            0 -> {
                                OutlinedTextField(value = name, onValueChange = { name = it; error = null },
                                    label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                photoUri?.let { uri ->
                                    AsyncImage(model = uri, contentDescription = "Selected profile photo",
                                        modifier = Modifier.size(60.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                }
                                TextButton(onClick = {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                                    Text("  Add a profile photo (optional)")
                                }
                            }
                            1 -> learningGoals.forEach { option ->
                                ChoiceCard(option, goal == option) { goal = option }
                            }
                            2 -> learningInterests.forEach { option ->
                                ChoiceCard(option, option in interests) {
                                    if (option in interests) interests.remove(option) else interests.add(option)
                                }
                            }
                            3 -> experienceLevels.forEach { option -> ChoiceCard(option, experience == option) { experience = option } }
                            4 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                studyTargets.forEach { target ->
                                    FilterChip(selected = minutes == target, onClick = { minutes = target },
                                        label = { Text("$target min") }, leadingIcon = if (minutes == target) {{ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }} else null)
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        keyboard?.hide()
                        if (step == 0 && name.isBlank()) error = "Enter the name you’d like us to use."
                        else if (step == 1 && goal.isBlank()) error = "Choose a learning goal to continue."
                        else if (step < 4) { step++; error = null }
                        else viewModel.save(name, goal.ifBlank { learningGoals[1] }, interests.toList(), experience, minutes, onComplete)
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text(if (step == 4) "Finish setup" else "Continue") }
                Text("You can update your profile any time in Account.", modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChoiceCard(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AnimatedBird() {
    val transition = rememberInfiniteTransition(label = "vision-bird")
    val bob by transition.animateFloat(initialValue = 0f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bird-bob")
    Surface(Modifier.size(76.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val cx = size.width * .52f
            val cy = size.height * .50f + bob.dp.toPx()
            val r = size.minDimension * .34f
            drawLine(Color(0xFF9A7447), androidx.compose.ui.geometry.Offset(size.width * .08f, cy + r),
                androidx.compose.ui.geometry.Offset(size.width * .92f, cy + r), strokeWidth = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color(0xFF48A77D), r, androidx.compose.ui.geometry.Offset(cx, cy))
            drawOval(Color(0xFF237B61), topLeft = androidx.compose.ui.geometry.Offset(cx - r * .35f, cy - r * .15f),
                size = androidx.compose.ui.geometry.Size(r * .95f, r * 1.1f), style = Stroke(width = 2.dp.toPx()))
            val beak = Path().apply { moveTo(cx + r * .65f, cy - r * .28f); lineTo(cx + r * 1.35f, cy - r * .10f); lineTo(cx + r * .63f, cy + r * .02f); close() }
            drawPath(beak, Color(0xFFE7B45E))
            drawCircle(Color(0xFF102820), r * .07f, androidx.compose.ui.geometry.Offset(cx + r * .46f, cy - r * .35f))
        }
    }
}
