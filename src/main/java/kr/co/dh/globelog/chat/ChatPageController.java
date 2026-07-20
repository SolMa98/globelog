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

    @GetMapping("/my/chat/{roomId}")
    public String room(@PathVariable Long roomId, Model model) {
        model.addAttribute("roomId", roomId);
        return "chat/room";
    }
}
