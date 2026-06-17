package com.kaer.service;

import java.util.List;

public interface MemoryNoteStoreService {
    List<String> getNotes(String sessionId);
    void addNote(String sessionId, String content);
    boolean shouldGenerateNote(String sessionId, int intervalTurns);
}
