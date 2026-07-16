package kr.co.dh.globelog.api;

import java.time.LocalDateTime;
import kr.co.dh.globelog.domain.TripComment;

public record TripCommentResponse(
        Long id,
        Long authorId,
        String authorNickname,
        String authorProfileImageUrl,
        String content,
        LocalDateTime createdAt,
        boolean ownedByViewer) {

    public static TripCommentResponse from(TripComment comment, boolean ownedByViewer) {
        var author = comment.getUser();
        return new TripCommentResponse(
                comment.getId(),
                author.getId(),
                author.getNickname(),
                author.getProfileImageUrl(),
                comment.getContent(),
                comment.getCreatedAt(),
                ownedByViewer);
    }
}
