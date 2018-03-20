package ca.orienteeringbc.nfctimingtagwriter;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

/**
 * Created by jon on 19/03/18.
 * Database access layer
 */

@Dao
public interface DaoAccess {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCompetitorList(List<Competitor> competitors);

    @Query("SELECT * FROM Competitor ORDER BY LOWER(lastName)")
    List<Competitor> getCompetitors();

    @Query("DELETE FROM Competitor")
    void deleteAllCompetitors();
}
