package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memory.data.MemoryCategory
import com.example.memory.data.MemoryEntity
import com.example.memory.retrieval.ScoredMemory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MemorySortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    IMPORTANCE("Importance"),
    MOST_RECALLED("Most Recalled")
}

@Composable
fun MemoryVaultScreen(
    memories: List<MemoryEntity>,
    onAddMemory: (content: String, category: MemoryCategory, importance: Int, keywords: String) -> Unit,
    onUpdateMemory: (MemoryEntity) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearAllMemories: () -> Unit,
    onSimulateRetrieval: suspend (String) -> List<ScoredMemory>,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<MemoryCategory?>(null) }
    var sortOrder by remember { mutableStateOf(MemorySortOrder.NEWEST) }

    var showAddDialog by remember { mutableStateOf(false) }
    var memoryToEdit by remember { mutableStateOf<MemoryEntity?>(null) }
    var memoryToDelete by remember { mutableStateOf<MemoryEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Retrieval Simulator state
    var isSimulatorExpanded by remember { mutableStateOf(false) }
    var simQuery by remember { mutableStateOf("") }
    var simResults by remember { mutableStateOf<List<ScoredMemory>?>(null) }
    var isSimulating by remember { mutableStateOf(false) }

    // Filtered & Sorted list
    val filteredMemories = remember(memories, searchQuery, selectedCategory, sortOrder) {
        var list = memories

        if (selectedCategory != null) {
            list = list.filter { it.categoryEnum == selectedCategory }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase().trim()
            list = list.filter {
                it.content.lowercase().contains(q) ||
                it.keywords.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }

        when (sortOrder) {
            MemorySortOrder.NEWEST -> list.sortedByDescending { it.timestamp }
            MemorySortOrder.OLDEST -> list.sortedBy { it.timestamp }
            MemorySortOrder.IMPORTANCE -> list.sortedByDescending { it.importance }
            MemorySortOrder.MOST_RECALLED -> list.sortedByDescending { it.accessCount }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0C))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("memory_vault_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color(0xFF00A3FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Memory Vault",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${memories.size} Atomic Memories • Hybrid Vector RAG",
                    color = Color(0xFFA1A1AA),
                    fontSize = 11.5.sp
                )
            }

            // Add Note Button
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_memory_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Note", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search memories, facts, tags...", color = Color(0xFF71717A), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF71717A), modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF71717A), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF141518),
                    unfocusedContainerColor = Color(0xFF141518),
                    focusedBorderColor = Color(0xFF00A3FF),
                    unfocusedBorderColor = Color(0xFF2E3138)
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("memory_search_input")
            )
        }

        // Category Filter Chips & Sort
        val chipScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(chipScrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "All" chip
            FilterChipItem(
                label = "All (${memories.size})",
                isSelected = selectedCategory == null,
                color = Color(0xFF00A3FF),
                onClick = { selectedCategory = null }
            )

            MemoryCategory.entries.forEach { cat ->
                val count = memories.count { it.categoryEnum == cat }
                FilterChipItem(
                    label = "${cat.displayName} ($count)",
                    isSelected = selectedCategory == cat,
                    color = Color(cat.colorHex),
                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat }
                )
            }
        }

        // Sort order selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sort:",
                color = Color(0xFF71717A),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            MemorySortOrder.entries.forEach { order ->
                val isSelected = sortOrder == order
                Text(
                    text = order.label,
                    color = if (isSelected) Color(0xFF00A3FF) else Color(0xFFA1A1AA),
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { sortOrder = order }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            if (memories.isNotEmpty()) {
                Text(
                    text = "Clear All",
                    color = Color(0xFFEF4444).copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showClearAllDialog = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Interactive RAG Retrieval Simulator Panel
        RetrievalSimulatorBanner(
            isExpanded = isSimulatorExpanded,
            query = simQuery,
            results = simResults,
            isSimulating = isSimulating,
            onToggleExpand = { isSimulatorExpanded = !isSimulatorExpanded },
            onQueryChange = { simQuery = it },
            onRunSimulation = {
                if (simQuery.isNotBlank()) {
                    coroutineScope.launch {
                        isSimulating = true
                        simResults = onSimulateRetrieval(simQuery)
                        isSimulating = false
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Memory Notes List
        if (filteredMemories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0xFF00A3FF).copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFF00A3FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedCategory != null) "No matching memories found" else "Your Memory Vault is empty",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try another keyword or reset filters" else "Add notes manually or chat naturally to let the AI learn your preferences, projects, and facts.",
                        color = Color(0xFF71717A),
                        fontSize = 12.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    if (searchQuery.isBlank() && selectedCategory == null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Memory Note", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredMemories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onEdit = { memoryToEdit = memory },
                        onDelete = { memoryToDelete = memory }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        MemoryEditorDialog(
            initialContent = "",
            initialCategory = MemoryCategory.FACT,
            initialImportance = 3,
            initialKeywords = "",
            isEditing = false,
            onDismiss = { showAddDialog = false },
            onConfirm = { content, category, importance, keywords ->
                onAddMemory(content, category, importance, keywords)
                showAddDialog = false
            }
        )
    }

    // Edit Memory Dialog
    if (memoryToEdit != null) {
        val target = memoryToEdit!!
        MemoryEditorDialog(
            initialContent = target.content,
            initialCategory = target.categoryEnum,
            initialImportance = target.importance,
            initialKeywords = target.keywords,
            isEditing = true,
            onDismiss = { memoryToEdit = null },
            onConfirm = { content, category, importance, keywords ->
                onUpdateMemory(
                    target.copy(
                        content = content,
                        category = category.name,
                        importance = importance,
                        keywords = keywords
                    )
                )
                memoryToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (memoryToDelete != null) {
        val target = memoryToDelete!!
        AlertDialog(
            onDismissRequest = { memoryToDelete = null },
            title = { Text("Delete Memory?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This memory will be permanently removed from the Vault:\n\n\"${target.content}\"",
                    color = Color(0xFFA1A1AA),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMemory(target.id)
                        memoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToDelete = null }) {
                    Text("Cancel", color = Color(0xFFA1A1AA))
                }
            },
            containerColor = Color(0xFF18191D)
        )
    }

    // Clear All Confirmation Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Long-Term Memories?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete all ${memories.size} memories in the Vault? The AI companion will forget all personal facts, preferences, and project context.",
                    color = Color(0xFFA1A1AA),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllMemories()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel", color = Color(0xFFA1A1AA))
                }
            },
            containerColor = Color(0xFF18191D)
        )
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) color.copy(alpha = 0.2f) else Color(0xFF141518))
            .border(
                1.dp,
                if (isSelected) color else Color(0xFF27272A),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) color else Color(0xFFA1A1AA),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = memory.categoryEnum
    val catColor = Color(category.colorHex)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val formattedDate = remember(memory.timestamp) { dateFormat.format(Date(memory.timestamp)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121418))
            .border(1.dp, Color(0xFF23252B), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            // Top Row: Category Pill, Source badge, Importance stars, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(catColor.copy(alpha = 0.15f))
                        .border(1.dp, catColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = category.displayName,
                        color = catColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Source indicator
                val isAuto = memory.source == "AUTO_EXTRACTED"
                Text(
                    text = if (isAuto) "✦ Learned" else "User Note",
                    color = if (isAuto) Color(0xFF38BDF8) else Color(0xFF71717A),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.weight(1f))

                // Importance stars
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (i in 1..5) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (i <= memory.importance) Color(0xFFF59E0B) else Color(0xFF3F3F46),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Edit & Delete icons
                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFF71717A),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Content
            Text(
                text = memory.content,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal
            )

            // Keywords if any
            if (memory.keywords.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    memory.keywordList.take(4).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1B1D22))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Created $formattedDate",
                    color = Color(0xFF71717A),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                if (memory.accessCount > 0) {
                    Text(
                        text = "Recalled ${memory.accessCount}x",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Not yet recalled",
                        color = Color(0xFF52525B),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RetrievalSimulatorBanner(
    isExpanded: Boolean,
    query: String,
    results: List<ScoredMemory>?,
    isSimulating: Boolean,
    onToggleExpand: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRunSimulation: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131722))
            .border(1.dp, Color(0xFF00A3FF).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF00A3FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Interactive RAG Retrieval Simulator",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isExpanded) "Hide ▲" else "Test ▼",
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "Test which memories would be recalled into the context window for a given user query based on cosine vector similarity and hybrid BM25 scoring:",
                        color = Color(0xFFA1A1AA),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("e.g., What should I practice today?", color = Color(0xFF52525B), fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0A0C10),
                                unfocusedContainerColor = Color(0xFF0A0C10),
                                focusedBorderColor = Color(0xFF00A3FF),
                                unfocusedBorderColor = Color(0xFF27272A)
                            ),
                            modifier = Modifier.weight(1f).height(46.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onRunSimulation,
                            enabled = query.isNotBlank() && !isSimulating,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            if (isSimulating) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Text("Simulate", color = Color.White, fontSize = 12.5.sp)
                            }
                        }
                    }

                    if (results != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (results.isEmpty()) {
                            Text(
                                text = "No memories exceeded the relevance threshold for this query.",
                                color = Color(0xFFEF4444),
                                fontSize = 11.5.sp
                            )
                        } else {
                            Text(
                                text = "Top ${results.size} Recalled Memories for Prompt Context:",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            results.forEachIndexed { idx, item ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0B0D12))
                                        .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "#${idx + 1} [${item.memory.category}]",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = "Score: ${(item.finalScore * 100).toInt()}% (Vector: ${(item.semanticScore * 100).toInt()}%, Lex: ${(item.keywordScore * 100).toInt()}%)",
                                                color = Color(0xFFF59E0B),
                                                fontSize = 10.5.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.memory.content,
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryEditorDialog(
    initialContent: String,
    initialCategory: MemoryCategory,
    initialImportance: Int,
    initialKeywords: String,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (content: String, category: MemoryCategory, importance: Int, keywords: String) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(initialCategory) }
    var importance by remember { mutableIntStateOf(initialImportance) }
    var keywords by remember { mutableStateOf(initialKeywords) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Memory Note" else "New Memory Note",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Memory Statement (Atomic Fact/Preference):",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("e.g., User is developing an audio Android app using Jetpack Compose.", color = Color(0xFF52525B), fontSize = 12.5.sp) },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF141518),
                        unfocusedContainerColor = Color(0xFF141518),
                        focusedBorderColor = Color(0xFF00A3FF),
                        unfocusedBorderColor = Color(0xFF2E3138)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("memory_content_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Category:",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                val catScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(catScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        val isSel = category == cat
                        val catCol = Color(cat.colorHex)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) catCol.copy(alpha = 0.25f) else Color(0xFF141518))
                                .border(1.dp, if (isSel) catCol else Color(0xFF2E3138), RoundedCornerShape(8.dp))
                                .clickable { category = cat }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.displayName,
                                color = if (isSel) catCol else Color(0xFFA1A1AA),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Importance Level: $importance/5",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        for (i in 1..5) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (i <= importance) Color(0xFFF59E0B) else Color(0xFF3F3F46),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { importance = i }
                            )
                        }
                    }
                }
                Slider(
                    value = importance.toFloat(),
                    onValueChange = { importance = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00A3FF),
                        activeTrackColor = Color(0xFF00A3FF),
                        inactiveTrackColor = Color(0xFF27272A)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Keywords (comma separated):",
                    color = Color(0xFF71717A),
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    placeholder = { Text("e.g. audio, android, compose", color = Color(0xFF52525B), fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF141518),
                        unfocusedContainerColor = Color(0xFF141518),
                        focusedBorderColor = Color(0xFF00A3FF),
                        unfocusedBorderColor = Color(0xFF2E3138)
                    ),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        onConfirm(content.trim(), category, importance, keywords.trim())
                    }
                },
                enabled = content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF))
            ) {
                Text(if (isEditing) "Save Changes" else "Add to Vault", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA1A1AA))
            }
        },
        containerColor = Color(0xFF18191E)
    )
}
