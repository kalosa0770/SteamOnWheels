package com.steamonwheels;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface LessonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLesson(Lesson lesson);

    @Query("SELECT * FROM lessons WHERE LOWER(subject) = LOWER(:subjectName) ORDER BY id DESC")
    List<Lesson> getLessonsBySubject(String subjectName);

    @Query("SELECT * FROM lessons ORDER BY id DESC")
    List<Lesson> getAllLessons();
}