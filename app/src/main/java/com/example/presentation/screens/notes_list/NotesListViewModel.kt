package com.example.presentation.screens.notes_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferencesManager
import com.example.domain.model.Note
import com.example.domain.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    DATE_UPDATED("По дате изменения"),
    DATE_CREATED("По дате создания"),
    TITLE("По названию (А-Я)")
}

data class NotesListUiState(
    val notes: List<Note> = emptyList(),
    val filteredNotes: List<Note> = emptyList(),
    val selectedColor: String? = null,
    val selectedTag: String? = null,
    val selectedFolder: String? = null,
    val sortOrder: SortOrder = SortOrder.DATE_UPDATED,
    val availableTags: List<String> = emptyList(),
    val availableFolders: List<String> = emptyList(),
    val isSelectionMode: Boolean = false,
    val selectedNoteIds: Set<Long> = emptySet(),
    val isGridLayout: Boolean = true
)

class NotesListViewModel(
    private val repository: NoteRepository,
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NotesListUiState(isGridLayout = preferencesManager.isGridLayoutSync())
    )
    val uiState: StateFlow<NotesListUiState> = _uiState.asStateFlow()

    init {
        loadNotes()
        loadFolders()
        observeLayoutMode()
    }

    private fun observeLayoutMode() {
        viewModelScope.launch {
            preferencesManager.isGridLayoutFlow.collect { isGrid ->
                _uiState.update { it.copy(isGridLayout = isGrid) }
            }
        }
    }

    private fun loadNotes() {
        viewModelScope.launch {
            repository.getActiveNotes().collect { notes ->
                val allTags = notes.flatMap { it.tags }.distinct().sorted()
                _uiState.update { current ->
                    current.copy(
                        notes = notes,
                        availableTags = allTags,
                        filteredNotes = sortAndFilterNotes(notes, current.selectedColor, current.selectedTag, current.selectedFolder, current.sortOrder)
                    )
                }
            }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            repository.getAllFolders().collect { folders ->
                _uiState.update { it.copy(availableFolders = folders) }
            }
        }
    }

    private fun sortAndFilterNotes(
        notes: List<Note>,
        color: String?,
        tag: String?,
        folder: String?,
        sortOrder: SortOrder
    ): List<Note> {
        val filtered = notes.filter { note ->
            val matchesColor = color == null || note.colorHex.equals(color, ignoreCase = true)
            val matchesTag = tag == null || note.tags.contains(tag)
            val matchesFolder = folder == null || note.folder == folder
            matchesColor && matchesTag && matchesFolder
        }

        return when (sortOrder) {
            SortOrder.DATE_UPDATED -> filtered.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt })
            SortOrder.DATE_CREATED -> filtered.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.createdAt })
            SortOrder.TITLE -> filtered.sortedWith(compareByDescending<Note> { it.isPinned }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { it.content } })
        }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update {
            it.copy(
                sortOrder = order,
                filteredNotes = sortAndFilterNotes(it.notes, it.selectedColor, it.selectedTag, it.selectedFolder, order)
            )
        }
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                selectedColor = null,
                selectedTag = null,
                selectedFolder = null,
                filteredNotes = sortAndFilterNotes(it.notes, null, null, null, it.sortOrder)
            )
        }
    }

    fun setColorFilter(color: String?) {
        _uiState.update {
            it.copy(
                selectedColor = color,
                filteredNotes = sortAndFilterNotes(it.notes, color, it.selectedTag, it.selectedFolder, it.sortOrder)
            )
        }
    }

    fun setTagFilter(tag: String?) {
        _uiState.update {
            it.copy(
                selectedTag = tag,
                filteredNotes = sortAndFilterNotes(it.notes, it.selectedColor, tag, it.selectedFolder, it.sortOrder)
            )
        }
    }

    fun setFolderFilter(folder: String?) {
        _uiState.update {
            it.copy(
                selectedFolder = folder,
                filteredNotes = sortAndFilterNotes(it.notes, it.selectedColor, it.selectedTag, folder, it.sortOrder)
            )
        }
    }

    fun toggleLayout() {
        val next = !_uiState.value.isGridLayout
        _uiState.update { it.copy(isGridLayout = next) }
        preferencesManager.setGridLayoutSync(next)
        viewModelScope.launch {
            preferencesManager.setGridLayout(next)
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun selectAllNotes() {
        _uiState.update { current ->
            val allIds = current.filteredNotes.map { it.id }.toSet()
            current.copy(selectedNoteIds = allIds, isSelectionMode = allIds.isNotEmpty())
        }
    }

    fun toggleNoteSelection(noteId: Long) {
        _uiState.update { current ->
            val newSelection = current.selectedNoteIds.toMutableSet()
            if (newSelection.contains(noteId)) {
                newSelection.remove(noteId)
            } else {
                newSelection.add(noteId)
            }
            current.copy(
                selectedNoteIds = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedNoteIds = emptySet(), isSelectionMode = false) }
    }

    fun deleteSelectedNotes() {
        viewModelScope.launch {
            val selected = _uiState.value.selectedNoteIds
            val toDelete = _uiState.value.notes.filter { selected.contains(it.id) }
            toDelete.forEach { note ->
                repository.updateNote(note.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
            }
            clearSelection()
        }
    }

    fun archiveSelectedNotes() {
        viewModelScope.launch {
            val selected = _uiState.value.selectedNoteIds
            val toArchive = _uiState.value.notes.filter { selected.contains(it.id) }
            toArchive.forEach { note ->
                repository.updateNote(note.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
            }
            clearSelection()
        }
    }

    fun pinSelectedNotes(pin: Boolean) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedNoteIds
            val toUpdate = _uiState.value.notes.filter { selected.contains(it.id) }
            toUpdate.forEach { note ->
                repository.updateNote(note.copy(isPinned = pin, updatedAt = System.currentTimeMillis()))
            }
            clearSelection()
        }
    }

    fun changeColorForSelected(hex: String) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedNoteIds
            val toUpdate = _uiState.value.notes.filter { selected.contains(it.id) }
            toUpdate.forEach { note ->
                repository.updateNote(note.copy(colorHex = hex, updatedAt = System.currentTimeMillis()))
            }
            clearSelection()
        }
    }

    fun moveSelectedToFolder(folderName: String?) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedNoteIds
            val toUpdate = _uiState.value.notes.filter { selected.contains(it.id) }
            toUpdate.forEach { note ->
                repository.updateNote(note.copy(folder = folderName, updatedAt = System.currentTimeMillis()))
            }
            clearSelection()
        }
    }

    fun lockSelectedNotes(lock: Boolean) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedNoteIds
            val toUpdate = _uiState.value.notes.filter { selected.contains(it.id) }
            toUpdate.forEach { note ->
                repository.updateNote(note.copy(isLocked = lock, updatedAt = System.currentTimeMillis()))
            }
            clearSelection()
        }
    }
}
