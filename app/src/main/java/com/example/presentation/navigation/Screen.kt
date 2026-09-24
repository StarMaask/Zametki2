package com.example.presentation.navigation

sealed class Screen(val route: String) {
    object NotesList : Screen("notes_list")
    object NoteEditor : Screen("note_editor/{noteId}?template={template}") {
        fun createRoute(noteId: Long, templateName: String? = null): String {
            return if (templateName != null) {
                "note_editor/$noteId?template=$templateName"
            } else {
                "note_editor/$noteId"
            }
        }
    }
    object Search : Screen("search")
    object Archive : Screen("archive")
    object Trash : Screen("trash")
    object Settings : Screen("settings")
}
