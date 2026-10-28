package com.example.woodsreader.ui.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.woodsreader.data.graph.EntityType
import com.example.woodsreader.data.graph.GraphEntity
import com.example.woodsreader.data.graph.GraphRepository
import com.example.woodsreader.ui.theme.HiddenForest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CandidatesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val graphRepository: GraphRepository
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle["bookId"])

    val searchQuery = MutableStateFlow("")

    val candidates: StateFlow<List<GraphEntity>> = combine(
        graphRepository.candidates(bookId),
        searchQuery
    ) { candidateList, query ->
        if (query.isBlank()) {
            candidateList
        } else {
            candidateList.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val confirmedEntities: StateFlow<List<GraphEntity>> = graphRepository
        .entities(bookId, Int.MAX_VALUE, limit = 150)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun confirmCandidate(candidate: GraphEntity) {
        viewModelScope.launch {
            graphRepository.updateEntity(candidate.copy(confirmed = true, ignored = false))
        }
    }

    fun ignoreCandidate(candidate: GraphEntity) {
        viewModelScope.launch {
            graphRepository.updateEntity(candidate.copy(ignored = true))
        }
    }

    fun changeType(candidate: GraphEntity, newType: EntityType) {
        viewModelScope.launch {
            graphRepository.updateEntity(candidate.copy(type = newType))
        }
    }

    fun mergeInto(sourceId: Long, targetId: Long) {
        viewModelScope.launch {
            graphRepository.mergeInto(sourceId, targetId)
        }
    }

    fun confirmAll() {
        viewModelScope.launch {
            val list = candidates.value
            for (c in list) {
                graphRepository.updateEntity(c.copy(confirmed = true, ignored = false))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatesScreen(
    onNavigateUp: () -> Unit,
    viewModel: CandidatesViewModel = hiltViewModel()
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val confirmedEntities by viewModel.confirmedEntities.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var candidateToChangeType by remember { mutableStateOf<GraphEntity?>(null) }
    var candidateToMerge by remember { mutableStateOf<GraphEntity?>(null) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Filtrar candidatos...", fontSize = 14.sp) },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        viewModel.setSearchQuery("")
                                    } else {
                                        isSearchExpanded = false
                                    }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Revisión de Candidatos",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Badge(
                                containerColor = HiddenForest.Accent.copy(alpha = 0.2f),
                                contentColor = HiddenForest.Accent
                            ) {
                                Text("${candidates.size}")
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás"
                        )
                    }
                },
                actions = {
                    if (!isSearchExpanded) {
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    }
                    if (candidates.isNotEmpty()) {
                        TextButton(onClick = { viewModel.confirmAll() }) {
                            Text("Confirmar todos", fontSize = 12.sp, color = HiddenForest.Accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (candidates.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = HiddenForest.Accent,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "¡Sin candidatos pendientes!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Todas las entidades detectadas han sido revisadas o confirmadas en el grafo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiddenForest.TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = HiddenForest.SurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Swipe,
                                    contentDescription = null,
                                    tint = HiddenForest.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Desliza a la derecha para confirmar, o a la izquierda para ignorar.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HiddenForest.TextSecondary
                                )
                            }
                        }
                    }

                    items(
                        items = candidates,
                        key = { it.id }
                    ) { candidate ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        viewModel.confirmCandidate(candidate)
                                        true
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.ignoreCandidate(candidate)
                                        true
                                    }
                                    SwipeToDismissBoxValue.Settled -> false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val direction = dismissState.dismissDirection
                                val backgroundColor = when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF2E7D32) // Verde confirmación
                                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFC62828) // Rojo ignorar
                                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                                }

                                val alignment = when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(backgroundColor)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = alignment
                                ) {
                                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Confirmar",
                                                tint = Color.White
                                            )
                                            Text(
                                                text = "Confirmar",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Ignorar",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Ignorar",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            },
                            content = {
                                CandidateItemCard(
                                    candidate = candidate,
                                    onConfirm = { viewModel.confirmCandidate(candidate) },
                                    onIgnore = { viewModel.ignoreCandidate(candidate) },
                                    onChangeType = { candidateToChangeType = candidate },
                                    onMerge = { candidateToMerge = candidate }
                                )
                            }
                        )
                    }
                }
            }
        }

        // Diálogo para cambiar tipo
        candidateToChangeType?.let { candidate ->
            ChangeTypeDialog(
                candidate = candidate,
                onDismiss = { candidateToChangeType = null },
                onSelectType = { newType ->
                    viewModel.changeType(candidate, newType)
                    candidateToChangeType = null
                }
            )
        }

        // Diálogo para fusionar con otra entidad
        candidateToMerge?.let { candidate ->
            MergeCandidateDialog(
                candidate = candidate,
                confirmedEntities = confirmedEntities.filter { it.id != candidate.id },
                onDismiss = { candidateToMerge = null },
                onConfirmMerge = { targetId ->
                    viewModel.mergeInto(candidate.id, targetId)
                    candidateToMerge = null
                }
            )
        }
    }
}

@Composable
fun CandidateItemCard(
    candidate: GraphEntity,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
    onChangeType: () -> Unit,
    onMerge: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HiddenForest.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(entityColor(candidate.type))
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = entityColor(candidate.type).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = candidate.type.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = entityColor(candidate.type),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "${candidate.mentionCount} menciones",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.TextSecondary
                        )

                        Text(
                            text = "• Cap. ${candidate.firstChapterIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiddenForest.TextLow
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onConfirm) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirmar",
                        tint = HiddenForest.Accent
                    )
                }

                IconButton(onClick = onIgnore) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Ignorar",
                        tint = HiddenForest.TextSecondary
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = HiddenForest.TextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cambiar tipo") },
                            leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onChangeType()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Fusionar con...") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMerge()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Confirmar") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = HiddenForest.Accent) },
                            onClick = {
                                showMenu = false
                                onConfirm()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ignorar") },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onIgnore()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeTypeDialog(
    candidate: GraphEntity,
    onDismiss: () -> Unit,
    onSelectType: (EntityType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HiddenForest.Surface,
        title = {
            Text(
                text = "Cambiar tipo de '${candidate.name}'",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = HiddenForest.Text
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EntityType.entries.forEach { type ->
                    val isSelected = type == candidate.type
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) entityColor(type).copy(alpha = 0.25f) else HiddenForest.SurfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) entityColor(type) else HiddenForest.Outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectType(type) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(entityColor(type))
                            )
                            Text(
                                text = type.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) entityColor(type) else HiddenForest.Text
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = HiddenForest.TextSecondary)
            }
        }
    )
}

@Composable
fun MergeCandidateDialog(
    candidate: GraphEntity,
    confirmedEntities: List<GraphEntity>,
    onDismiss: () -> Unit,
    onConfirmMerge: (targetId: Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTargets = remember(confirmedEntities, searchQuery) {
        if (searchQuery.isBlank()) {
            confirmedEntities
        } else {
            confirmedEntities.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HiddenForest.Surface,
        title = {
            Text(
                text = "Fusionar '${candidate.name}' con...",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = HiddenForest.Text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Text(
                    text = "Los alias, menciones y relaciones se transferirán a la entidad destino, y '${candidate.name}' se agregará como alias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiddenForest.TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar entidad destino...", fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HiddenForest.Accent,
                        focusedLabelColor = HiddenForest.Accent,
                        unfocusedTextColor = HiddenForest.Text,
                        focusedTextColor = HiddenForest.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredTargets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay entidades disponibles para fusionar",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiddenForest.TextLow
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredTargets, key = { it.id }) { target ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = HiddenForest.SurfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, HiddenForest.Outline.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onConfirmMerge(target.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(entityColor(target.type))
                                        )
                                        Text(
                                            text = target.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = HiddenForest.Text
                                        )
                                    }
                                    Text(
                                        text = "${target.mentionCount} menciones",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HiddenForest.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = HiddenForest.TextSecondary)
            }
        }
    )
}
