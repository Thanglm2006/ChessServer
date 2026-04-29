package org.example.chessserver.service;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import org.springframework.stereotype.Service;

@Service
public class ChessGameService {

    public Board loadBoard(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);
        return board;
    }

    public String handleMoveLogic(Board board, String moveStr) {
        try {
            String uci = moveStr.toUpperCase();
            System.out.println("Processing UCI move: " + uci + " for side: " + board.getSideToMove());
            Move move = new Move(uci, board.getSideToMove());
            if (board.legalMoves().contains(move)) {
                board.doMove(move);
                return board.getFen();
            } else {
                System.out.println("Move " + uci + " is illegal in current position");
            }
        } catch (Exception e) {
            System.err.println("Move format error: " + moveStr + " -> " + e.getMessage());
        }
        return null;
    }

    public String getGameStatus(Board board) {
        if (board.isMated()) return "CHECKMATE";
        if (board.isStaleMate()) return "STALEMATE";
        if (board.isDraw()) return "DRAW";
        if (board.isKingAttacked()) return "CHECK";
        return "CONTINUE";
    }
}