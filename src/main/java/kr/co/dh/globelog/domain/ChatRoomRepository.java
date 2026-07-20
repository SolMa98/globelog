package kr.co.dh.globelog.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 같은 두 사람 사이엔 DIRECT 방이 항상 하나만 있어야 하므로, 새로 만들기 전에 항상
    // 먼저 찾아본다(find-or-create). DIRECT 방은 멤버가 정확히 2명이도록 초대 API에서
    // 막아두므로, 이 쿼리가 "두 사람만 있는 방"이라고 가정해도 안전하다.
    @Query("SELECT m1.room FROM ChatRoomMember m1 WHERE m1.room.type = kr.co.dh.globelog.domain.ChatRoomType.DIRECT "
            + "AND m1.user.id = :userAId "
            + "AND EXISTS (SELECT 1 FROM ChatRoomMember m2 WHERE m2.room = m1.room AND m2.user.id = :userBId)")
    Optional<ChatRoom> findDirectRoomBetween(@Param("userAId") Long userAId, @Param("userBId") Long userBId);

    // 개인 채팅방("나와의 채팅")은 사용자당 하나만 존재.
    @Query("SELECT m.room FROM ChatRoomMember m WHERE m.room.type = kr.co.dh.globelog.domain.ChatRoomType.SELF "
            + "AND m.user.id = :userId")
    Optional<ChatRoom> findSelfRoom(@Param("userId") Long userId);
}
