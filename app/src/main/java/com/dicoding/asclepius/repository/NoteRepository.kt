package com.dicoding.asclepius.repository

import android.app.Application
import androidx.lifecycle.LiveData
import com.dicoding.asclepius.database.Note
import com.dicoding.asclepius.database.NoteDao
import com.dicoding.asclepius.database.NoteRoomDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NoteRepository(application: Application) {
    private val mNotesDao: NoteDao
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        val db = NoteRoomDatabase.getDatabase(application)
        mNotesDao = db.noteDao()
    }

    fun getAllNotes(): LiveData<List<Note>> = mNotesDao.getAllNotes()

    fun insert(note: Note) {
        executorService.execute { mNotesDao.insert(note) }
    }

    fun deleteByTimestamp(timestamp: String) {
        executorService.execute { mNotesDao.deleteByTimestamp(timestamp) }
    }

    fun getNoteByTimestamp(timestamp: String): LiveData<Note?> {
        return mNotesDao.getNoteByTimestamp(timestamp)
    }

}