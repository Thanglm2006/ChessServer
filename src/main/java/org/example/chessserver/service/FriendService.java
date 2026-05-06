package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.FriendDto;
import org.example.chessserver.entity.EloRating;
import org.example.chessserver.entity.Friendship;
import org.example.chessserver.entity.User;
import org.example.chessserver.repository.EloRatingRepository;
import org.example.chessserver.repository.FriendshipRepository;
import org.example.chessserver.repository.UserRepository;
import org.example.chessserver.websocket.ChessWebSocketHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final EloRatingRepository eloRatingRepository;
    
    @Lazy
    private final ChessWebSocketHandler webSocketHandler;

    public void sendFriendRequest(int senderId, int receiverId) {
        if (senderId == receiverId) {
            throw new RuntimeException("You cannot send a friend request to yourself");
        }
        Friendship existing = friendshipRepository.findFriendshipBetween(senderId, receiverId);
        if (existing != null) {
            throw new RuntimeException("Friendship already exists or request is pending");
        }

        User u1 = userRepository.findById(senderId).orElseThrow(() -> new RuntimeException("Sender not found"));
        User u2 = userRepository.findById(receiverId).orElseThrow(() -> new RuntimeException("Receiver not found"));

        Friendship f = new Friendship();
        f.setUser1(u1);
        f.setUser2(u2);
        f.setStatus("PENDING");
        friendshipRepository.save(f);
    }

    public void acceptFriendRequest(int u1, int u2) {
        Friendship existing = friendshipRepository.findFriendshipBetween(u1, u2);
        if (existing == null) {
            throw new RuntimeException("Friend request not found");
        }
        if (!"PENDING".equals(existing.getStatus())) {
            throw new RuntimeException("Friend request is already accepted or invalid");
        }
        existing.setStatus("ACCEPTED");
        friendshipRepository.save(existing);
    }

    public List<FriendDto> getFriendsList(int userId) {
        List<Friendship> friendships = friendshipRepository.findAcceptedFriendships(userId);
        return friendships.stream().map(f -> {
            int friendId = (f.getUser1().getUserId() == userId) ? f.getUser2().getUserId() : f.getUser1().getUserId();
            User friendUser = (f.getUser1().getUserId() == userId) ? f.getUser2() : f.getUser1();
            int rating = eloRatingRepository.findById(friendId).map(EloRating::getRating).orElse(1200);
            String status = webSocketHandler.isUserOnline(friendId) ? "ONLINE" : "OFFLINE";
            
            return FriendDto.builder()
                    .userId(friendId)
                    .username(friendUser.getUsername())
                    .status(status)
                    .rating(rating)
                    .build();
        }).collect(Collectors.toList());
    }

    public List<FriendDto> getPendingRequests(int userId) {
        List<Friendship> friendships = friendshipRepository.findPendingRequests(userId);
        return friendships.stream().map(f -> {
            User sender = f.getUser1();
            int rating = eloRatingRepository.findById(sender.getUserId()).map(EloRating::getRating).orElse(1200);
            return FriendDto.builder()
                    .userId(sender.getUserId())
                    .username(sender.getUsername())
                    .status("PENDING")
                    .rating(rating)
                    .build();
        }).collect(Collectors.toList());
    }
}
