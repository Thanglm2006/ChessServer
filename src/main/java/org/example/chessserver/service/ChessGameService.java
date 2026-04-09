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
            Move move = new Move(moveStr, board.getSideToMove());
            if (board.legalMoves().contains(move)) {
                board.doMove(move);
                return board.getFen();
            }
        } catch (Exception e) {
            System.err.println("Invalid move format: " + moveStr);
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