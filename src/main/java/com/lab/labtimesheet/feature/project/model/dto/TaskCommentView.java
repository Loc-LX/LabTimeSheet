package com.lab.labtimesheet.feature.project.model.dto;

import java.time.Instant;

/**
 * Historical append-only Task comment exposed to authorized Task readers.
 *
 * @param id comment identifier
 * @param taskId owning Task identifier
 * @param authorUserId historical author user identifier
 * @param body normalized comment text
 * @param createdAt persisted creation instant
 */
public record TaskCommentView(long id, long taskId, long authorUserId, String body, Instant createdAt) {}
