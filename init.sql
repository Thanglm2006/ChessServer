
-- 1. Cài đặt Extension tìm kiếm nhanh
CREATE EXTENSION IF NOT EXISTS pgroonga;

-- 2. Bảng Người dùng (Users)
CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL, -- Hash từ Backend (Bcrypt/Argon2)
    country_code VARCHAR(2) DEFAULT 'VN',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index pgroonga để tìm kiếm tên không dấu/có dấu siêu tốc
CREATE INDEX IF NOT EXISTS ix_users_username_pgroonga ON users USING pgroonga (username);

-- 3. Bảng Điểm số ELO (ELO Ratings)
CREATE TABLE IF NOT EXISTS elo_ratings (
    user_id INTEGER PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    rating INTEGER DEFAULT 1200,
    games_played INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    losses INTEGER DEFAULT 0,
    draws INTEGER DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Bảng Trận đấu (Games)
CREATE TABLE IF NOT EXISTS games (
    game_id SERIAL PRIMARY KEY,
    white_player_id INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    black_player_id INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    result VARCHAR(10), -- '1-0', '0-1', '1/2-1/2'
    pgn_data TEXT,      -- Toàn bộ biên bản trận đấu từ WebSocket
    played_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. TRIGGER: Tự động khởi tạo ELO khi User đăng ký
CREATE OR REPLACE FUNCTION fn_init_elo()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO elo_ratings (user_id) VALUES (NEW.user_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_init_elo ON users;
CREATE TRIGGER tr_init_elo
AFTER INSERT ON users
FOR EACH ROW
EXECUTE FUNCTION fn_init_elo();

-- 6. FUNCTION: Xử lý kết thúc trận đấu & Tính ELO động
-- Được gọi bởi Backend sau khi kết thúc WebSocket session
CREATE OR REPLACE FUNCTION process_game_end(
    p_white_id INTEGER,
    p_black_id INTEGER,
    p_result VARCHAR,
    p_pgn TEXT
)
RETURNS VOID AS $$
DECLARE
    -- Thông tin người chơi Trắng
    w_elo_old INTEGER;
    w_games INTEGER;
    k_w INTEGER;

    -- Thông tin người chơi Đen
    b_elo_old INTEGER;
    b_games INTEGER;
    k_b INTEGER;

    -- Biến tính toán
    exp_w FLOAT; -- Kỳ vọng thắng của Trắng
    exp_b FLOAT; -- Kỳ vọng thắng của Đen
    score_w FLOAT; -- Kết quả thực tế Trắng (1, 0, 0.5)
    score_b FLOAT; -- Kết quả thực tế Đen (1, 0, 0.5)
BEGIN
    -- Lấy dữ liệu hiện tại
    SELECT rating, games_played INTO w_elo_old, w_games FROM elo_ratings WHERE user_id = p_white_id;
    SELECT rating, games_played INTO b_elo_old, b_games FROM elo_ratings WHERE user_id = p_black_id;

    -- Xác định K-Factor động (Chuẩn FIDE)
    -- K=40 cho người mới (<30 trận), K=10 cho cao thủ (>2400), K=20 cho số còn lại
    k_w := CASE WHEN w_games < 30 THEN 40 WHEN w_elo_old > 2400 THEN 10 ELSE 20 END;
    k_b := CASE WHEN b_games < 30 THEN 40 WHEN b_elo_old > 2400 THEN 10 ELSE 20 END;

    -- Tính toán xác suất kỳ vọng (Expected Score)
    exp_w := 1 / (1 + 10^((b_elo_old - w_elo_old)::FLOAT / 400));
    exp_b := 1 - exp_w;

    -- Gán kết quả thực tế
    IF p_result = '1-0' THEN score_w := 1; score_b := 0;
    ELSIF p_result = '0-1' THEN score_w := 0; score_b := 1;
    ELSE score_w := 0.5; score_b := 0.5; -- Hòa
    END IF;

    -- Cập nhật bảng Games
    INSERT INTO games (white_player_id, black_player_id, result, pgn_data)
    VALUES (p_white_id, p_black_id, p_result, p_pgn);

    -- Cập nhật ELO và thống kê cho người Trắng
    UPDATE elo_ratings SET
        rating = rating + ROUND(k_w * (score_w - exp_w)),
        games_played = games_played + 1,
        wins = wins + CASE WHEN score_w = 1 THEN 1 ELSE 0 END,
        losses = losses + CASE WHEN score_w = 0 THEN 1 ELSE 0 END,
        draws = draws + CASE WHEN score_w = 0.5 THEN 1 ELSE 0 END,
        last_updated = CURRENT_TIMESTAMP
    WHERE user_id = p_white_id;

    -- Cập nhật ELO và thống kê cho người Đen
    UPDATE elo_ratings SET
        rating = rating + ROUND(k_b * (score_b - exp_b)),
        games_played = games_played + 1,
        wins = wins + CASE WHEN score_b = 1 THEN 1 ELSE 0 END,
        losses = losses + CASE WHEN score_b = 0 THEN 1 ELSE 0 END,
        draws = draws + CASE WHEN score_b = 0.5 THEN 1 ELSE 0 END,
        last_updated = CURRENT_TIMESTAMP
    WHERE user_id = p_black_id;

END;
$$ LANGUAGE plpgsql;

-- 7. VIEW: Bảng xếp hạng cập nhật thời gian thực
CREATE OR REPLACE VIEW leaderboard AS
SELECT
    u.username,
    u.country_code,
    e.rating,
    e.games_played,
    e.wins,
    e.losses,
    ROUND((e.wins::FLOAT / NULLIF(e.games_played, 0) * 100)::NUMERIC, 1) as win_rate
FROM users u
JOIN elo_ratings e ON u.user_id = e.user_id
WHERE e.games_played > 0
ORDER BY e.rating DESC;