package app.veneer.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.veneer.apps.AppEntry
import app.veneer.apps.normalizeForSearch
import app.veneer.data.LauncherState
import app.veneer.system.SystemActions
import app.veneer.ui.theme.Vivid
import app.veneer.util.Calculator
import app.veneer.ui.theme.LocalThemeFont
import app.veneer.veneer
import java.text.Collator

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    controller: HomeController,
    actions: LauncherActions,
    state: LauncherState,
    apps: List<AppEntry>,
) {
    val theme = state.theme
    val context = LocalContext.current
    val bg = Color(theme.drawerBg)
    val fg = Color(theme.drawerTextColor)
    val font = LocalThemeFont.current
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()

    fun close() {
        keyboard?.hide()
        controller.closeDrawer()
    }
    BackHandler { close() }

    LaunchedEffect(controller.drawerFocusSearch) {
        if (controller.drawerFocusSearch) runCatching { focus.requestFocus() }
    }

    val overrides = state.overrides
    val visible = remember(apps, overrides, query, showHidden) {
        val q = normalizeForSearch(query.trim())
        val collator = Collator.getInstance()
        apps.asSequence()
            .filter { showHidden || overrides[it.key.id]?.hidden != true }
            .map { it to (overrides[it.key.id]?.label ?: it.label) }
            .filter { (_, label) -> q.isEmpty() || normalizeForSearch(label).contains(q) }
            .sortedWith { a, b ->
                if (q.isNotEmpty()) {
                    // Names that start with the query come first.
                    val sa = normalizeForSearch(a.second).startsWith(q)
                    val sb = normalizeForSearch(b.second).startsWith(q)
                    if (sa != sb) return@sortedWith if (sa) -1 else 1
                }
                collator.compare(a.second, b.second)
            }
            .toList()
    }

    // Pulling down past the top of the list closes the drawer.
    val closeOnPull = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > 180f) {
                        pulled = 0f
                        close()
                    }
                } else if (available.y < 0f) pulled = 0f
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 80f) close() }) { change, dy ->
                        change.consume()
                        total += dy
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(fg.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = fg.copy(alpha = 0.7f))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search ${apps.size} apps", color = fg.copy(alpha = 0.55f), style = TextStyle(fontFamily = font, fontSize = 16.sp))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = font, fontSize = 16.sp, color = fg),
                        cursorBrush = SolidColor(Color(theme.accent)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val first = visible.firstOrNull()?.first
                            if (first != null) {
                                actions.launch(first.key)
                                close()
                            } else if (query.isNotBlank()) {
                                SystemActions.webSearch(context, query)
                            }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = fg.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { query = "" },
                    )
                }
            }
            Box {
                IconButton(onClick = { overflowOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = fg)
                }
                FriendlyMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    MenuRow(if (showHidden) "Don't show hidden apps" else "Show hidden apps", Icons.Filled.Visibility, Vivid.Amber) {
                        showHidden = !showHidden
                        overflowOpen = false
                    }
                    MenuRow("Customize Veneer", Icons.Filled.Palette, Vivid.Pink) {
                        overflowOpen = false
                        close()
                        actions.openCustomize()
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(theme.drawerColumns),
            state = gridState,
            modifier = Modifier
                .weight(1f)
                .nestedScroll(closeOnPull),
        ) {
            val math = if (Calculator.looksLikeMath(query)) Calculator.evaluate(query) else null
            if (math != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { CalculatorResult(query, math, fg, Color(theme.accent)) }
            }
            items(visible, key = { it.first.key.id }) { (entry, label) ->
                DrawerCell(entry, label, controller, actions, state, fg, hidden = overrides[entry.key.id]?.hidden == true)
            }
            if (query.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { SystemActions.webSearch(context, query) }
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color(theme.accent))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Search the web for \"${query.trim()}\"",
                            color = fg,
                            style = TextStyle(fontFamily = font, fontSize = 15.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerCell(
    entry: AppEntry,
    label: String,
    controller: HomeController,
    actions: LauncherActions,
    state: LauncherState,
    fg: Color,
    hidden: Boolean,
) {
    val context = LocalContext.current
    val theme = state.theme
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    actions.launch(entry.key)
                    controller.closeDrawer()
                },
                onLongClick = { menu = true },
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppTile(
            entry, label, state.overrides[entry.key.id]?.icon, theme.iconSizeDp.coerceAtMost(64).let { it.dp },
            showLabel = theme.showDrawerLabels, labelColor = if (hidden) fg.copy(alpha = 0.5f) else fg, pressed = pressed,
        )
        FriendlyMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ShortcutMenuRows(entry.key, controller) {
                menu = false
                controller.closeDrawer()
            }
            MenuRow("Add to home screen", Icons.Filled.AddCircle, Vivid.Green) {
                menu = false
                controller.addToHome(entry)
                controller.closeDrawer()
            }
            MenuRow("Edit icon and name", Icons.Filled.Edit, Vivid.Violet) {
                menu = false
                controller.editingApp = entry.key
            }
            MenuRow(if (hidden) "Unhide" else "Hide from app list", Icons.Filled.VisibilityOff, Vivid.Amber) {
                menu = false
                context.veneer.store.updateOverride(entry.key.id) { it.copy(hidden = !hidden) }
            }
            MenuRow("App info", Icons.Filled.Info, Vivid.Blue) {
                menu = false
                controller.app.apps.openAppInfo(entry.key)
            }
            MenuRow("Uninstall", Icons.Filled.Delete, Vivid.Coral) {
                menu = false
                controller.app.apps.uninstall(entry.key)
            }
        }
    }
}

/** The answer to a sum typed into search. Tap to copy it. */
@Composable
private fun CalculatorResult(query: String, value: Double, fg: Color, accent: Color) {
    val clipboard = LocalContext.current.getSystemService(android.content.ClipboardManager::class.java)
    val text = Calculator.format(value)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(accent.copy(alpha = 0.18f))
            .clickable { clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Result", text)) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(query.trim(), color = fg.copy(alpha = 0.7f), style = TextStyle(fontSize = 14.sp, fontFamily = LocalThemeFont.current))
            Text("= $text", color = fg, style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = LocalThemeFont.current))
        }
        Text("Tap to copy", color = accent, style = TextStyle(fontSize = 12.sp))
    }
}
