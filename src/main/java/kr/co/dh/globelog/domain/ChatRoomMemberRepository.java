package kr.co.dh.globelog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    List<ChatRoomMember> findByUserIdOrderByJoinedAtDesc(Long userId);

    List<ChatRoomMember> findByRoomId(Long roomId);

    Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);

    void deleteByRoomIdAndUserId(Long roomId, Long userId);
}
