package kr.co.dh.globelog.chat;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ChatPageController {

    @GetMapping("/my/chat")
    public String rooms() {
        return "chat/rooms";
    }

    // 카카오톡/인스타그램처럼 "새 대화 시작"을 모달이 아니라 화면 전환으로 처리한다.
    // /my/chat/{roomId}(Long)와 리터럴 경로가 겹쳐 보여도 Spring MVC는 더 구체적인
    // 리터럴 매핑을 우선하므로 충돌 없이 이 메서드가 "new"를 가로챈다.
    @GetMapping("/my/chat/new")
    public String newRoom() {
        return "chat/new";
    }

    @GetMapping("/my/chat/{roomId}")
    public String room(@PathVariable Long roomId, Model model) {
        model.addAttribute("roomId", roomId);
        return "chat/room";
    }
}
