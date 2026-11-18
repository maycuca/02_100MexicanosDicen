package mx.cienmexicanos.game;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    List<GameResult> findByRoomCode(String roomCode);
}
