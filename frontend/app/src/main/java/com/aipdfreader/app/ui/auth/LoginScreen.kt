package com.aipdfreader.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.hilt.navigation.compose.hiltViewModel
import com.aipdfreader.app.R
import kotlinx.coroutines.launch

/** Firebase email/password authentication screen. */
@Composable
fun LoginScreen(
    onAuthenticated: (needsProfileSetup: Boolean) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (viewModel.isAlreadyAuthenticated) viewModel.continueAuthenticated(onAuthenticated)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        val tabletLayout = maxWidth >= 700.dp && maxHeight >= 680.dp
        if (tabletLayout) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .widthIn(max = 1120.dp)
                    .fillMaxWidth()
                    .height(620.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp
            ) {
                Row(Modifier.fillMaxSize()) {
                    LoginForm(
                        state = state,
                        viewModel = viewModel,
                        onSubmit = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                            viewModel.submit(onSuccess = onAuthenticated)
                        },
                        modifier = Modifier
                            .weight(0.92f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 52.dp, vertical = 42.dp)
                    )
                    VisionBirdPanel(
                        modifier = Modifier
                            .weight(1.08f)
                            .fillMaxHeight(),
                        compact = false
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VisionBirdPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    compact = true
                )
                Spacer(Modifier.height(22.dp))
                LoginForm(
                    state = state,
                    viewModel = viewModel,
                    onSubmit = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        viewModel.submit(onSuccess = onAuthenticated)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your ideas, gathered in one place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoginForm(
    state: LoginUiState,
    viewModel: LoginViewModel,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val passwordFocusRequester = remember { FocusRequester() }
    val emailIntoView = remember { BringIntoViewRequester() }
    val passwordIntoView = remember { BringIntoViewRequester() }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color(0xFF111214)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = "Vision V logo",
                    modifier = Modifier.padding(4.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "VISION",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Spacer(Modifier.height(34.dp))
        Text(
            if (state.mode == AuthMode.LOGIN) "Welcome back" else "Create your account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.mode == AuthMode.LOGIN) "Sign in to pick up where you left off."
            else "A little space for everything you’re learning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(26.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("Email address") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(emailIntoView)
                .onFocusChanged { if (it.isFocused) scope.launch { emailIntoView.bringIntoView() } }
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .bringIntoViewRequester(passwordIntoView)
                .onFocusChanged { if (it.isFocused) scope.launch { passwordIntoView.bringIntoView() } }
        )

        state.errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onSubmit,
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (state.mode == AuthMode.LOGIN) "Sign in" else "Create account")
        }
        TextButton(
            onClick = viewModel::toggleMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (state.mode == AuthMode.LOGIN) "New to Vision?  Create an account"
                else "Already have an account?  Sign in"
            )
        }
    }
}

/** A quiet, custom-drawn bird-and-leaf motif: a small screen banner on phones, a full hero panel on tablets. */
@Composable
private fun VisionBirdPanel(modifier: Modifier = Modifier, compact: Boolean) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 24.dp else 0.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0B302A), Color(0xFF145545), Color(0xFF0D2926))))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val leaves = listOf(
                Triple(0.08f, 0.22f, -28f), Triple(0.16f, 0.78f, 28f),
                Triple(0.82f, 0.16f, 35f), Triple(0.91f, 0.70f, -34f),
                Triple(0.60f, 0.88f, 18f), Triple(0.40f, 0.10f, -12f)
            )
            leaves.forEachIndexed { index, (x, y, angle) ->
                val center = androidx.compose.ui.geometry.Offset(w * x, h * y)
                val path = Path().apply {
                    moveTo(center.x, center.y)
                    quadraticTo(center.x + w * 0.13f, center.y - h * 0.12f, center.x + w * 0.17f, center.y)
                    quadraticTo(center.x + w * 0.10f, center.y + h * 0.12f, center.x, center.y)
                    close()
                }
                drawPath(path, if (index % 2 == 0) Color(0x553EA878) else Color(0x4448BA83))
                drawLine(Color(0x557BC99A), center, androidx.compose.ui.geometry.Offset(center.x + w * 0.13f, center.y), strokeWidth = 1.2.dp.toPx())
            }
            // Perched bird silhouette, with a bright breast and a small eye.
            val cx = w * if (compact) 0.79f else 0.63f
            val cy = h * if (compact) 0.59f else 0.50f
            val scale = if (compact) h * 0.31f else minOf(w, h) * 0.29f
            val branch = Path().apply {
                moveTo(w * 0.10f, cy + scale * 0.75f)
                cubicTo(w * 0.32f, cy + scale * 0.57f, w * 0.57f, cy + scale * 0.91f, w * 0.91f, cy + scale * 0.70f)
            }
            drawPath(branch, Color(0xFFB68B59), style = Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            val body = Path().apply {
                moveTo(cx - scale * 0.63f, cy + scale * 0.38f)
                cubicTo(cx - scale * 0.84f, cy - scale * 0.08f, cx - scale * 0.40f, cy - scale * 0.70f, cx + scale * 0.07f, cy - scale * 0.58f)
                cubicTo(cx + scale * 0.59f, cy - scale * 0.52f, cx + scale * 0.68f, cy + scale * 0.17f, cx + scale * 0.42f, cy + scale * 0.50f)
                cubicTo(cx + scale * 0.16f, cy + scale * 0.82f, cx - scale * 0.35f, cy + scale * 0.78f, cx - scale * 0.63f, cy + scale * 0.38f)
                close()
            }
            drawPath(body, Color(0xFF48A77D))
            val wing = Path().apply {
                moveTo(cx - scale * 0.49f, cy + scale * 0.12f)
                cubicTo(cx - scale * 0.24f, cy - scale * 0.50f, cx + scale * 0.41f, cy - scale * 0.34f, cx + scale * 0.33f, cy + scale * 0.24f)
                cubicTo(cx + scale * 0.22f, cy + scale * 0.54f, cx - scale * 0.23f, cy + scale * 0.55f, cx - scale * 0.49f, cy + scale * 0.12f)
                close()
            }
            drawPath(wing, Color(0xFF237B61))
            drawCircle(Color(0xFF55B58A), radius = scale * 0.24f, center = androidx.compose.ui.geometry.Offset(cx - scale * 0.02f, cy - scale * 0.48f))
            val beak = Path().apply {
                moveTo(cx + scale * 0.19f, cy - scale * 0.50f)
                lineTo(cx + scale * 0.62f, cy - scale * 0.39f)
                lineTo(cx + scale * 0.21f, cy - scale * 0.30f)
                close()
            }
            drawPath(beak, Color(0xFFE7B45E))
            drawCircle(Color(0xFF102820), radius = scale * 0.035f, center = androidx.compose.ui.geometry.Offset(cx + scale * 0.12f, cy - scale * 0.53f))
        }
        if (!compact) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 34.dp, bottom = 32.dp, end = 30.dp)
            ) {
                Text("A clearer way to learn", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Keep your materials close. Let curiosity take flight.", color = Color(0xFFD0E6DC), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(
                "A clearer way to learn",
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
