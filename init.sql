-- 1. Cài đặt Extension tìm kiếm nhanh
CREATE EXTENSION IF NOT EXISTS pgroonga;

-- 2. Bảng Người dùng (Users)
CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL, -- Hash từ Backend (Bcrypt/Argon2)
    country_code VARCHAR(2) DEFAULT 'VN',
    role VARCHAR(20) DEFAULT 'ROLE_USER',
    is_banned BOOLEAN DEFAULT FALSE,
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

-- 7. Bảng Bạn bè (Friendships)
CREATE TABLE IF NOT EXISTS friendships (
    user_id_1 INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    user_id_2 INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id_1, user_id_2)
);

-- 8. VIEW: Bảng xếp hạng cập nhật thời gian thực
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

-- 9. ADMIN & TOURNAMENT TABLES
CREATE TABLE IF NOT EXISTS tournaments (
    tournament_id SERIAL PRIMARY KEY,
    tournament_name VARCHAR(255) NOT NULL,
    description TEXT,
    total_rounds INTEGER NOT NULL,
    time_control VARCHAR(50) NOT NULL,
    registration_start TIMESTAMP WITH TIME ZONE,
    registration_end TIMESTAMP WITH TIME ZONE,
    start_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'REGISTERING',
    created_by INTEGER REFERENCES users(user_id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tournament_participants (
    tournament_id INTEGER REFERENCES tournaments(tournament_id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    initial_rating INTEGER,
    current_score NUMERIC(4,1) DEFAULT 0,
    buchholz NUMERIC(6,2) DEFAULT 0,
    sonneborn_berger NUMERIC(6,2) DEFAULT 0,
    bye_received BOOLEAN DEFAULT FALSE,
    reminder_sent BOOLEAN DEFAULT FALSE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tournament_id, user_id)
);

CREATE TABLE IF NOT EXISTS tournament_rounds (
    round_id SERIAL PRIMARY KEY,
    tournament_id INTEGER REFERENCES tournaments(tournament_id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(tournament_id, round_number)
);

CREATE TABLE IF NOT EXISTS tournament_pairings (
    pairing_id SERIAL PRIMARY KEY,
    round_id INTEGER REFERENCES tournament_rounds(round_id) ON DELETE CASCADE,
    white_player_id INTEGER REFERENCES users(user_id),
    black_player_id INTEGER REFERENCES users(user_id),
    game_id INTEGER REFERENCES games(game_id),
    result VARCHAR(10),
    is_bye BOOLEAN DEFAULT FALSE,
    white_ready BOOLEAN DEFAULT FALSE,
    black_ready BOOLEAN DEFAULT FALSE,
    lobby_started_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS game_moves (
    move_id SERIAL PRIMARY KEY,
    game_id INTEGER REFERENCES games(game_id) ON DELETE CASCADE,
    move_number INTEGER NOT NULL,
    san_move VARCHAR(20),
    fen_after_move TEXT,
    evaluation FLOAT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 10. Database Migration/Patches for existing installations
ALTER TABLE tournament_pairings ADD COLUMN IF NOT EXISTS white_ready BOOLEAN DEFAULT FALSE;
ALTER TABLE tournament_pairings ADD COLUMN IF NOT EXISTS black_ready BOOLEAN DEFAULT FALSE;
ALTER TABLE tournament_pairings ADD COLUMN IF NOT EXISTS lobby_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tournament_pairings ADD COLUMN IF NOT EXISTS is_bye BOOLEAN DEFAULT FALSE;
ALTER TABLE tournament_rounds ADD COLUMN IF NOT EXISTS ended_at TIMESTAMP WITH TIME ZONE;