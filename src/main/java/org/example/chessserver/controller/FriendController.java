package org.example.chessserver.controller;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.FriendDto;
import org.example.chessserver.service.FriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {
    
    private final FriendService friendService;

    @GetMapping("/list")
    public ResponseEntity<List<FriendDto>> getFriends(@RequestParam int userId) {
        return ResponseEntity.ok(friendService.getFriendsList(userId));
    }

    @PostMapping("/request")
    public ResponseEntity<String> sendRequest(@RequestParam int senderId, @RequestParam int receiverId) {
        friendService.sendFriendRequest(senderId, receiverId);
        return ResponseEntity.ok("Friend request sent");
    }

    @PostMapping("/accept")
    public ResponseEntity<String> acceptRequest(@RequestParam int user1, @RequestParam int user2) {
        friendService.acceptFriendRequest(user1, user2);
        return ResponseEntity.ok("Friend request accepted");
    }
}
