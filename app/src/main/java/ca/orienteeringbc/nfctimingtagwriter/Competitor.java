package ca.orienteeringbc.nfctimingtagwriter;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by jon on 13/03/18.
 * Contains id # for everyone in WJR system
 */

@Entity (indices = {@Index(value = { "firstName", "lastName", "wjrId" }, unique = true)})
public class Competitor {
    @PrimaryKey
    private int wjrId;

    private String firstName;

    private String lastName;

    public Competitor(int wjrId, String firstName, String lastName) {
        this.wjrId = wjrId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String toString() {
        return lastName + ", " + firstName;
    }

    public int getWjrId() {
        return wjrId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
