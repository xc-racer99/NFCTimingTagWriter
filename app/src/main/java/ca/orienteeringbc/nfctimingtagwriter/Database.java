package ca.orienteeringbc.nfctimingtagwriter;

import android.arch.persistence.room.RoomDatabase;

/**
 * Created by jon on 19/03/18.
 * Wrapper for SQLite database
 */

@android.arch.persistence.room.Database(entities = { Competitor.class }, version = 1, exportSchema = false)
public abstract class Database extends RoomDatabase {
    public abstract DaoAccess daoAccess();
}
