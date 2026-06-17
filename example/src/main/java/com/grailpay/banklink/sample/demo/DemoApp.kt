package com.grailpay.banklink.sample.demo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val DefaultBrandPrimary = Color(0xFF006A60)

@Composable
fun DemoApp() {
    val vm: DemoViewModel = viewModel()
    val form by vm.form.collectAsState()
    val responses by vm.responses.collectAsState()
    val isInitializing by vm.isInitializing.collectAsState()
    val activity = LocalActivity.current as ComponentActivity

    val brandPrimary = remember(form.brandingPrimaryColor) {
        parseHexColor(form.brandingPrimaryColor) ?: DefaultBrandPrimary
    }
    val brandedScheme = MaterialTheme.colorScheme.copy(
        primary = brandPrimary,
        onPrimary = if (brandPrimary.luminance() > 0.5f) Color.Black else Color.White,
    )

    MaterialTheme(
        colorScheme = brandedScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        DemoScaffold(form, responses, isInitializing, vm, activity)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoScaffold(
    form: DemoFormState,
    responses: Map<CallbackKind, String>,
    isInitializing: Boolean,
    vm: DemoViewModel,
    activity: ComponentActivity,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var overflowOpen by remember { mutableStateOf(false) }

    val sessionsCount = responses.size
    val tokenReady by remember(form.token) {
        derivedStateOf { form.token.isNotBlank() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(sessionsCount = sessionsCount)
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Grailpay Demo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { /* search not implemented */ },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        Box {
                            IconButton(
                                onClick = { overflowOpen = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear all responses") },
                                    onClick = { vm.clearAllResponses(); overflowOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset form") },
                                    onClick = { vm.clearSavedInputs(); overflowOpen = false },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                BottomActionBar(
                    initializeEnabled = tokenReady && !isInitializing,
                    isInitializing = isInitializing,
                    onReset = { vm.clearSavedInputs() },
                    onInitialize = { vm.launch(activity) },
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { innerPadding ->
            FormBody(
                padding = innerPadding,
                form = form,
                responses = responses,
                vm = vm,
            )
        }
    }
}

@Composable
private fun DrawerContent(sessionsCount: Int) {
    ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
        ) {
            // Brand header
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "G",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Column {
                    Text(
                        "Grailpay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "demo · ${com.grailpay.banklink.sample.BuildConfig.BUILD_TYPE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val itemModifier = Modifier.padding(horizontal = 12.dp)
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Link, contentDescription = null) },
                label = { Text("Link Session") },
                selected = true,
                onClick = {},
                modifier = itemModifier,
                colors = NavigationDrawerItemDefaults.colors(),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                label = { Text("Sessions") },
                badge = {
                    if (sessionsCount > 0) {
                        Badge { Text(sessionsCount.toString()) }
                    }
                },
                selected = false,
                onClick = {},
                modifier = itemModifier,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.AccountTree, contentDescription = null) },
                label = { Text("Linked Accounts") },
                selected = false,
                onClick = {},
                modifier = itemModifier,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Domain, contentDescription = null) },
                label = { Text("Entities") },
                selected = false,
                onClick = {},
                modifier = itemModifier,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Webhook, contentDescription = null) },
                label = { Text("Webhooks") },
                selected = false,
                onClick = {},
                modifier = itemModifier,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "Recent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
            )
            RecentRow("Session · just now", com.grailpay.banklink.sample.BuildConfig.BUILD_TYPE)
            RecentRow("Session · 12m ago", com.grailpay.banklink.sample.BuildConfig.BUILD_TYPE)

            Spacer(Modifier.weight(1f))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            DrawerFooter()
        }
    }
}

@Composable
private fun RecentRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawerFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "You",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Sandbox key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Account options")
        }
    }
}

@Composable
private fun FormBody(
    padding: PaddingValues,
    form: DemoFormState,
    responses: Map<CallbackKind, String>,
    vm: DemoViewModel,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionHeader("Connection", icon = Icons.Filled.Link)
        OutlinedField(
            label = "Token",
            value = form.token,
            placeholder = "Bank link token",
            required = true,
            helper = "Provided by your backend session call",
            onChange = { v -> vm.updateForm { it.copy(token = v) } },
        )
        OutlinedField(
            label = "Entity UUID",
            value = form.entityUuid,
            placeholder = "Existing entity UUID",
            onChange = { v -> vm.updateForm { it.copy(entityUuid = v) } },
        )
        EntityTypeSegmented(form.entityType) { choice ->
            vm.updateForm { it.copy(entityType = choice) }
        }
        OutlinedField(
            label = "Client Reference Id",
            value = form.clientReferenceId,
            placeholder = "Client reference Id",
            onChange = { v -> vm.updateForm { it.copy(clientReferenceId = v) } },
        )
        OutlinedField(
            label = "App Launcher URL (Android)",
            value = form.appLauncherUrl,
            placeholder = "https://your-domain.com/bank-link-oauth",
            onChange = { v -> vm.updateForm { it.copy(appLauncherUrl = v) } },
        )

        SectionHeader("Branding", icon = Icons.Filled.Palette)
        OutlinedField(
            label = "Company Name",
            value = form.brandingCompanyName,
            placeholder = "Your company name",
            onChange = { v -> vm.updateForm { it.copy(brandingCompanyName = v) } },
        )
        OutlinedField(
            label = "Logo URL",
            value = form.brandingLogoUrl,
            placeholder = "https://your-company.com/logo.png",
            onChange = { v -> vm.updateForm { it.copy(brandingLogoUrl = v) } },
        )
        PrimaryColorField(form.brandingPrimaryColor) { v ->
            vm.updateForm { it.copy(brandingPrimaryColor = v) }
        }

        SectionHeader("Billing", icon = Icons.Filled.Receipt)
        OutlinedField(
            label = "Merchant UUID",
            value = form.billingMerchantUuid,
            placeholder = "",
            onChange = { v -> vm.updateForm { it.copy(billingMerchantUuid = v) } },
        )
        OutlinedField(
            label = "Processor MID",
            value = form.billingProcessorMid,
            placeholder = "",
            onChange = { v -> vm.updateForm { it.copy(billingProcessorMid = v) } },
        )

        Spacer(Modifier.height(4.dp))
        ResponseSection(responses, vm)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SectionHeader(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlinedField(
    label: String,
    value: String,
    placeholder: String,
    required: Boolean = false,
    helper: String? = null,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label)
                    if (required) {
                        Text(
                            " *",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        )
        if (helper != null) {
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityTypeSegmented(current: EntityTypeChoice, onChange: (EntityTypeChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Entity Type",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(EntityTypeChoice.BUSINESS, EntityTypeChoice.PERSON)
            options.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = choice == current,
                    onClick = { onChange(choice) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {
                        if (choice == current) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    label = {
                        Text(if (choice == EntityTypeChoice.BUSINESS) "Business" else "Individual")
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrimaryColorField(value: String, onChange: (String) -> Unit) {
    val parsed = remember(value) { parseHexColor(value) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Primary Color") },
        placeholder = { Text("#4F46E5") },
        singleLine = true,
        shape = RoundedCornerShape(4.dp),
        trailingIcon = {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(parsed ?: MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(6.dp),
                    ),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    )
}

@Composable
private fun BottomActionBar(
    initializeEnabled: Boolean,
    isInitializing: Boolean,
    onReset: () -> Unit,
    onInitialize: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // navigationBarsPadding() reserves space under the gesture / 3-button bar
        // so the Initialize button isn't covered by system navigation insets on
        // edge-to-edge devices (Android 15+ enables this by default).
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Reset")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onInitialize,
                    enabled = initializeEnabled,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    if (isInitializing) {
                        // Tiny spinner instead of the link icon — matches demo.html's
                        // "Initializing..." label while `await init()` is pending.
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Initializing...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Initialize & Open", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseSection(
    responses: Map<CallbackKind, String>,
    vm: DemoViewModel,
) {
    val timestamps = remember { mutableStateMapOf<CallbackKind, Long>() }
    LaunchedEffect(responses) {
        responses.forEach { (kind, _) ->
            if (kind !in timestamps) timestamps[kind] = System.currentTimeMillis()
        }
        (timestamps.keys - responses.keys).forEach { timestamps.remove(it) }
    }
    val expanded = remember { mutableStateMapOf<CallbackKind, Boolean>() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Response",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (responses.isNotEmpty()) {
                TextButton(onClick = { vm.clearAllResponses() }) { Text("Clear all") }
            }
        }
        // Render all 5 callback tiles upfront — same as demo.html's response panel.
        // Tiles start empty (placeholder appearance); they fill in with JSON as
        // events fire after Initialize is tapped.
        CallbackKind.entries.forEach { kind ->
            val body = responses[kind].orEmpty()
            CallbackTile(
                kind = kind,
                body = body,
                timestamp = timestamps[kind],
                expanded = body.isNotEmpty() && expanded[kind] == true,
                onToggle = { if (body.isNotEmpty()) expanded[kind] = !(expanded[kind] ?: false) },
                onClear = { vm.clearResponse(kind) },
            )
        }
    }
}

@Composable
private fun CallbackTile(
    kind: CallbackKind,
    body: String,
    timestamp: Long?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
) {
    val isError = kind == CallbackKind.ERROR
    val hasData = body.isNotEmpty()
    // Empty placeholder: muted surfaceVariant tint, mirrors demo.html's empty <pre> box.
    // Filled state: themed errorContainer / secondaryContainer like before.
    val container = when {
        !hasData -> MaterialTheme.colorScheme.surfaceVariant
        isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = when {
        !hasData -> MaterialTheme.colorScheme.onSurfaceVariant
        isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LeadingIconBox(
                    icon = when {
                        !hasData -> Icons.Filled.PlayCircleOutline
                        isError -> Icons.Filled.ErrorOutline
                        else -> Icons.Filled.CheckCircle
                    },
                    background = MaterialTheme.colorScheme.surface,
                    tint = onContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        humanLabel(kind),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer,
                    )
                    Text(
                        kind.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = onContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (hasData && timestamp != null) relativeTime(timestamp) else "Awaiting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f),
                )
                if (hasData) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = onContainer,
                        )
                    }
                }
            }
            AnimatedVisibility(visible = hasData && expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    JsonBlock(body)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        CopyButton(body)
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeadingIconBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun JsonBlock(body: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp, max = 280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SelectionContainer {
            Text(
                text = body,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CopyButton(body: String) {
    val context = LocalContext.current
    TextButton(onClick = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("callback", body))
    }) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Copy")
    }
}

private fun humanLabel(kind: CallbackKind): String = when (kind) {
    CallbackKind.BANK_CONNECTED -> "Bank connected"
    CallbackKind.ENTITY_CREATED -> "Entity created"
    CallbackKind.LINKED_DEFAULT_ACCOUNT -> "Linked default account"
    CallbackKind.LINK_EXIT -> "Link exit"
    CallbackKind.ERROR -> "Error"
}

private fun relativeTime(ts: Long): String {
    val delta = (System.currentTimeMillis() - ts) / 1000
    return when {
        delta < 5 -> "just now"
        delta < 60 -> "${delta}s ago"
        delta < 3600 -> "${delta / 60}m ago"
        else -> "${delta / 3600}h ago"
    }
}

private fun parseHexColor(input: String): Color? {
    val trimmed = input.trim().removePrefix("#")
    if (trimmed.length != 6 && trimmed.length != 8) return null
    return runCatching {
        val parsed = trimmed.toLong(16)
        if (trimmed.length == 6) Color(0xFF000000 or parsed)
        else Color(parsed)
    }.getOrNull()
}