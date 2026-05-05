# Chess Application Backend

This is the backend server for a real-time multiplayer Chess application. It handles user management, matchmaking, game state synchronization via WebSockets, and move validation.

## Features
- **Real-time Multiplayer**: Powered by Spring Boot WebSockets and Redis.
- **Matchmaking**: Queue-based matchmaking with preparation checks.
- **Game Clock**: Server-side move timing and timeout enforcement.
- **Elo System**: Dynamic rating updates via PostgreSQL stored procedures.
- **Friend System**: Friend requests, lists, and direct game invitations.
- **Game History**: Persistent storage of games in PGN format.

## Prerequisites
- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)

## Tech Stack
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL with [PGroonga](https://pgroonga.github.io/) (for fast text search)
- **Cache/Real-time**: Redis
- **Security**: JWT (JSON Web Tokens) & Google OAuth2

## Setup & Running

### 1. Environment Variables
Create a `.env` file in the root directory (same folder as `compose.yaml`) and provide the necessary credentials:

```env
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/chess_db
SPRING_DATASOURCE_USERNAME=Thanglm2006
SPRING_DATASOURCE_PASSWORD=Thanglm2006

# Redis Configuration
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379

# OAuth Configuration
GOOGLE_CLIENT_ID=your_google_client_id_here
```

### 2. Run with Docker Compose
Navigate to the project root and run:

```bash
docker compose up --build
```

The server will be available at `http://localhost:8080`.

## API Documentation
For detailed instructions on how to interact with the REST APIs and WebSocket events, please refer to:
[**Frontend API Instructions**](frontend_api_instructions.md)

## Project Structure
- `src/main/java/.../controller`: REST API endpoints.
- `src/main/java/.../websocket`: WebSocket message handlers.
- `src/main/java/.../service`: Core business logic (Matchmaking, Elo calculation, etc.).
- `src/main/java/.../repository`: Database access layer.
- `init.sql`: Initial database schema and Elo stored procedures.
