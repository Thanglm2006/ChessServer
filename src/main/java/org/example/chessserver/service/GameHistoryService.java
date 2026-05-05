package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.GameHistoryDto;
import org.example.chessserver.entity.Game;
import org.example.chessserver.repository.GameRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameHistoryService {

    private final GameRepository gameRepository;

    public List<GameHistoryDto> getHistoryForUser(int userId, int page, int size) {
        Page<Game> games = gameRepository.findGamesByUserId(userId, PageRequest.of(page, size));
        return games.stream().map(g -> {
            boolean isWhite = g.getWhitePlayer() != null && g.getWhitePlayer().getUserId() == userId;
            int oppId = isWhite ? 
                    (g.getBlackPlayer() != null ? g.getBlackPlayer().getUserId() : -1) : 
                    (g.getWhitePlayer() != null ? g.getWhitePlayer().getUserId() : -1);
            String oppName = isWhite ? 
                    (g.getBlackPlayer() != null ? g.getBlackPlayer().getUsername() : "Unknown") : 
                    (g.getWhitePlayer() != null ? g.getWhitePlayer().getUsername() : "Unknown");
            
            return GameHistoryDto.builder()
                    .gameId(g.getGameId())
                    .opponentId(oppId)
                    .opponentName(oppName)
                    .myColor(isWhite ? "WHITE" : "BLACK")
                    .result(g.getResult())
                    .pgn(g.getPgnData())
                    .playedAt(g.getPlayedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
